package com.jogodavelha.room;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftService;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.websocket.RoomSnapshotService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

@Service
public class RematchService {
    public static final long RESPONSE_WINDOW_MILLIS = 15_000;
    private final RoomRepository rooms;
    private final GameRepository games;
    private final RoomService roomService;
    private final RematchStateManager states;
    private final DraftService drafts;
    private final DraftStateManager draftStates;
    private final GameStateManager gameStates;
    private final RoomSnapshotService snapshots;
    private final TaskScheduler scheduler;
    private final Clock clock;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    public RematchService(RoomRepository rooms, GameRepository games, RoomService roomService,
                          RematchStateManager states, DraftService drafts, DraftStateManager draftStates,
                          GameStateManager gameStates, RoomSnapshotService snapshots,
                          @Qualifier("turnTaskScheduler") TaskScheduler scheduler, Clock clock) {
        this.rooms = rooms;
        this.games = games;
        this.roomService = roomService;
        this.states = states;
        this.drafts = drafts;
        this.draftStates = draftStates;
        this.gameStates = gameStates;
        this.snapshots = snapshots;
        this.scheduler = scheduler;
        this.clock = clock;
    }

    public void request(String code, UUID playerId) {
        Room room = requireMember(code, playerId);
        Game game = requireFinishedResult(room);
        Optional<RematchState> current = states.find(room.getId());
        if (current.isPresent() && current.get().gameId().equals(game.getId())) {
            if (current.get().status() == RematchState.Status.PENDING
                    && clock.millis() >= current.get().deadlineEpochMillis()) {
                expire(room.getId(), current.get().gameId(), current.get().deadlineEpochMillis());
            }
            throw new IllegalStateException("A negociação de revanche desta partida já foi iniciada ou encerrada.");
        }
        long deadline = clock.millis() + RESPONSE_WINDOW_MILLIS;
        RematchState pending = new RematchState(room.getId(), room.getCode(), game.getId(), playerId,
                deadline, 0, RematchState.Status.PENDING);
        if (!states.begin(pending)) {
            throw new IllegalStateException("Já existe uma negociação de revanche para esta sala.");
        }
        schedule(pending);
        snapshots.publish(code);
    }

    public void accept(String code, UUID playerId) {
        Room room = requireMember(code, playerId);
        RematchState pending = requirePending(room, playerId);
        requireSameFinishedGame(room, pending);
        RematchState accepted = states.transition(pending, RematchState.Status.ACCEPTED)
                .orElseThrow(() -> new IllegalStateException("A negociação de revanche já foi encerrada."));
        cancel(room.getId());
        startAcceptedRematch(room, accepted);
    }

    public void decline(String code, UUID playerId) {
        Room room = requireMember(code, playerId);
        RematchState pending = requirePending(room, playerId);
        requireSameFinishedGame(room, pending);
        states.transition(pending, RematchState.Status.DECLINED)
                .orElseThrow(() -> new IllegalStateException("A negociação de revanche já foi encerrada."));
        cancel(room.getId());
        roomService.closeAfterMatch(code, playerId, RoomClosedEvent.REMATCH_DECLINED);
    }

    public void returnToLobby(String code, UUID playerId) {
        Room room = requireMember(code, playerId);
        Game finished = requireFinishedResult(room);
        RematchState current = states.find(room.getId()).orElse(null);
        if (current != null && current.gameId().equals(finished.getId())) {
            if (current.status() != RematchState.Status.PENDING) {
                throw new IllegalStateException("A negociação de revanche desta partida já foi encerrada.");
            }
            if (clock.millis() >= current.deadlineEpochMillis()) {
                expire(room.getId(), current.gameId(), current.deadlineEpochMillis());
                return;
            }
            if (states.transition(current, RematchState.Status.CANCELLED).isEmpty()) {
                RematchState latest = states.find(room.getId()).orElse(null);
                if (latest != null && latest.status() == RematchState.Status.ACCEPTED) {
                    throw new IllegalStateException("A revanche já foi aceita.");
                }
                return;
            }
            cancel(room.getId());
        }
        roomService.closeAfterMatch(code, playerId, RoomClosedEvent.RETURN_TO_LOBBY);
    }

    public void onPlayerDisconnected(String code, UUID playerId) {
        Room room = rooms.findActiveMembership(code, RoomCodeAllocator.ACTIVE, playerId).orElse(null);
        if (room == null) return;
        RematchState pending = states.find(room.getId())
                .filter(state -> state.status() == RematchState.Status.PENDING).orElse(null);
        if (pending == null) return;
        if (clock.millis() >= pending.deadlineEpochMillis()) {
            expire(room.getId(), pending.gameId(), pending.deadlineEpochMillis());
            return;
        }
        if (states.transition(pending, RematchState.Status.CANCELLED).isEmpty()) return;
        cancel(room.getId());
        roomService.closeAfterMatch(code, playerId, RoomClosedEvent.REMATCH_DISCONNECTED);
    }

    public void expire(UUID roomId, UUID expectedGameId, long expectedDeadline) {
        RematchState pending = states.find(roomId).orElse(null);
        if (pending == null || pending.status() != RematchState.Status.PENDING
                || !pending.gameId().equals(expectedGameId)
                || pending.deadlineEpochMillis() != expectedDeadline) {
            return;
        }
        if (clock.millis() < expectedDeadline) {
            schedule(pending);
            return;
        }
        Optional<RematchState> expired = states.transition(pending, RematchState.Status.EXPIRED);
        if (expired.isEmpty()) return;
        cancel(roomId);
        roomService.closeAfterMatch(pending.roomCode(), null, RoomClosedEvent.REMATCH_EXPIRED);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverPendingRematches() {
        for (UUID roomId : states.findAllRoomIds()) {
            RematchState state = states.find(roomId).orElse(null);
            if (state == null) continue;
            if (state.status() == RematchState.Status.PENDING) {
                if (clock.millis() >= state.deadlineEpochMillis()) {
                    expire(roomId, state.gameId(), state.deadlineEpochMillis());
                } else {
                    schedule(state);
                }
            } else if (state.status() == RematchState.Status.ACCEPTED) {
                rooms.findById(roomId).ifPresent(room -> startAcceptedRematch(room, state));
            }
        }
    }

    private Room requireMember(String code, UUID playerId) {
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        return rooms.findActiveMembership(code, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
    }

    private Game requireFinishedResult(Room room) {
        if (draftStates.find(room.getId()).isPresent()) {
            throw new IllegalStateException("A sala já está em preparação para uma partida.");
        }
        if (games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS).isPresent()) {
            throw new IllegalStateException("A revanche só pode ser negociada após o encerramento da partida.");
        }
        return games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED)
                .orElseThrow(() -> new IllegalStateException("Não há resultado de partida para negociar revanche."));
    }

    private RematchState requirePending(Room room, UUID playerId) {
        RematchState pending = states.find(room.getId())
                .filter(state -> state.status() == RematchState.Status.PENDING)
                .orElseThrow(() -> new IllegalStateException("Não há negociação de revanche pendente."));
        if (clock.millis() >= pending.deadlineEpochMillis()) {
            expire(room.getId(), pending.gameId(), pending.deadlineEpochMillis());
            throw new IllegalStateException("O prazo da revanche expirou.");
        }
        if (pending.requesterId().equals(playerId)) {
            throw new IllegalStateException("Somente o adversário pode responder à solicitação.");
        }
        return pending;
    }

    private void requireSameFinishedGame(Room room, RematchState pending) {
        Game current = requireFinishedResult(room);
        if (!current.getId().equals(pending.gameId())) {
            throw new IllegalStateException("A solicitação pertence a outra partida.");
        }
    }

    private void startAcceptedRematch(Room room, RematchState accepted) {
        if (room.getStatus() != RoomStatus.IN_GAME || room.getPlayer2() == null
                || games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS).isPresent()) return;
        Game previous = games.findById(accepted.gameId()).orElse(null);
        if (previous == null || previous.getStatus() != GameStatus.FINISHED) return;

        String lockToken = UUID.randomUUID().toString();
        if (!states.acquireInitialization(room.getId(), lockToken)) return;
        try {
            DraftState existing = draftStates.find(room.getId()).orElse(null);
            if (existing != null && previous.getFinishedAt() != null
                    && existing.deadlineEpochMillis() > previous.getFinishedAt().toEpochMilli()) {
                drafts.resume(room.getId());
                snapshots.publish(room.getCode());
                return;
            }
            drafts.cancel(room.getId());
            draftStates.delete(room.getId());
            gameStates.delete(previous.getId());
            drafts.start(room.getId(), room.getCode(), room.getPlayer1().getId(), room.getPlayer2().getId());
            snapshots.publish(room.getCode());
        } finally {
            states.releaseInitialization(room.getId(), lockToken);
        }
    }

    private void schedule(RematchState state) {
        ScheduledFuture<?> timer = scheduler.schedule(
                () -> expire(state.roomId(), state.gameId(), state.deadlineEpochMillis()),
                Instant.ofEpochMilli(state.deadlineEpochMillis()));
        ScheduledFuture<?> previous = timers.put(state.roomId(), timer);
        if (previous != null) previous.cancel(false);
    }

    private void cancel(UUID roomId) {
        ScheduledFuture<?> timer = timers.remove(roomId);
        if (timer != null) timer.cancel(false);
    }
}
