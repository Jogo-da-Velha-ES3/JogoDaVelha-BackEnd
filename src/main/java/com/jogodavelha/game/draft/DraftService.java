package com.jogodavelha.game.draft;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.UnaryOperator;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;

@Service
public class DraftService {
    private final DraftStateManager states;
    private final DraftEligibilityProvider eligibility;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final ApplicationEventPublisher events;
    private final RandomGenerator random;
    private final ConcurrentHashMap<UUID, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    @Autowired
    public DraftService(DraftStateManager states, DraftEligibilityProvider eligibility, Clock clock,
                        @Qualifier("turnTaskScheduler") TaskScheduler scheduler, ApplicationEventPublisher events) {
        this(states, eligibility, clock, scheduler, events, new SecureRandom());
    }

    public DraftService(DraftStateManager states, DraftEligibilityProvider eligibility, Clock clock,
                        TaskScheduler scheduler, ApplicationEventPublisher events, RandomGenerator random) {
        this.states = states;
        this.eligibility = eligibility;
        this.clock = clock;
        this.scheduler = scheduler;
        this.events = events;
        this.random = random;
    }

    public DraftState start(UUID roomId, String roomCode, UUID player1, UUID player2) {
        var first = DraftState.Player.initial(player1, eligibility.forPlayer(player1));
        var second = DraftState.Player.initial(player2, eligibility.forPlayer(player2));
        DraftState state = new DraftState(roomId, roomCode, clock.millis() + DraftState.DURATION_MILLIS,
                0, first, second);
        states.create(state);
        schedule(state);
        events.publishEvent(DraftUpdated.from(state));
        return state;
    }

    // Integração das BE-011/BE-013. Callback puro: pode ser reexecutado em conflito de versão.
    public void updatePlayer(UUID roomId, UUID playerId, UnaryOperator<DraftState.Player> selection) {
        states.update(roomId, current -> {
            if (current.completed() || clock.millis() >= current.deadlineEpochMillis()) {
                throw new IllegalStateException("Prazo encerrado ou Draft já concluído.");
            }
            DraftState.Player before = current.player(playerId);
            if (before.ready()) throw new IllegalStateException("Escolhas do jogador já estão confirmadas.");
            DraftState.Player after = selection.apply(before);
            if (!before.playerId().equals(after.playerId())
                    || !before.eligibleCharacters().equals(after.eligibleCharacters())
                    || !before.eligibleSkills().equals(after.eligibleSkills())) {
                throw new IllegalArgumentException("Seleção não pode alterar jogador ou inventário elegível.");
            }
            return current.withPlayer(after);
        }).ifPresent(change -> publish(change.after(), false));
    }

    public void resume(UUID roomId) {
        states.find(roomId).ifPresent(state -> {
            if (state.completed()) cancel(roomId);
            else schedule(state);
        });
    }

    public void expire(UUID roomId, long expectedDeadline) {
        var existing = states.find(roomId);
        if (existing.isEmpty() || existing.get().completed()) {
            cancel(roomId);
            return;
        }
        DraftState state = existing.get();
        if (state.deadlineEpochMillis() != expectedDeadline) return;
        if (clock.millis() < expectedDeadline) {
            schedule(state);
            return;
        }
        List<Double> draws = IntStream.range(0, 6).mapToObj(ignored -> random.nextDouble()).toList();
        Optional<DraftStateManager.Change> change;
        try {
            change = states.update(roomId, current -> current.completed() || current.deadlineEpochMillis() != expectedDeadline
                    ? current : current.autoComplete(draws));
        } catch (NoSuchElementException exception) {
            cancel(roomId); // A sala pode ter sido removida enquanto o timer aguardava.
            return;
        }
        change.ifPresent(updated -> publish(updated.after(), true));
    }

    public void cancel(UUID roomId) {
        ScheduledFuture<?> timer = timers.remove(roomId);
        if (timer != null) timer.cancel(false);
    }

    private void schedule(DraftState state) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> expire(state.roomId(), state.deadlineEpochMillis()),
                Instant.ofEpochMilli(state.deadlineEpochMillis()));
        ScheduledFuture<?> previous = timers.put(state.roomId(), timer);
        if (previous != null) previous.cancel(false);
    }

    private void publish(DraftState state, boolean automatic) {
        if (state.completed()) cancel(state.roomId());
        try {
            events.publishEvent(DraftUpdated.from(state));
        } finally {
            // Uma falha no broadcast não pode impedir a sinalização para a BE-038.
            if (state.completed()) events.publishEvent(DraftCompleted.from(state, automatic));
        }
    }

    public void selectCharacter(UUID roomId, UUID playerId, UUID characterId) {
        states.update(roomId, current -> {
            if (current.completed() || clock.millis() >= current.deadlineEpochMillis()) {
                throw new IllegalStateException("Prazo encerrado ou Draft já concluído.");
            }
            DraftState.Player me = current.player(playerId);
            if (me.ready()) throw new IllegalStateException("Escolhas do jogador já estão confirmadas.");
            if (!me.eligibleCharacters().contains(characterId)) {
                throw new IllegalArgumentException("Você não possui este personagem.");
            }
            if (characterId.equals(current.opponentOf(playerId).characterId())) {
                throw new IllegalStateException("Personagem já reservado pelo oponente.");
            }
            return current.withPlayer(me.withCharacter(characterId));
        }).ifPresent(change -> publish(change.after(), false));
    }
}
