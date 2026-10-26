package com.jogodavelha.websocket;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomPlayerInfo;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Composes the read-only, public-to-room state used by ROOM_SNAPSHOT. */
@Service
@RequiredArgsConstructor
public class RoomSnapshotService {
    private final RoomRepository rooms;
    private final GameRepository games;
    private final DraftStateManager drafts;
    private final GameStateManager gameStates;
    private final RoomPlayerService players;
    private final WebSocketService websocket;

    public Snapshot compose(String code) {
        Room room = rooms.findByCodeAndStatusIn(code, List.of(RoomStatus.WAITING, RoomStatus.IN_GAME))
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada ou encerrada."));
        RoomBlock roomBlock = new RoomBlock(room.getCode(), room.getStatus().name(),
                players.host(room), room.getPlayer2() == null ? null : players.info(room.getPlayer2()));

        DraftBlock draft = drafts.find(room.getId()).map(RoomSnapshotService::draftBlock).orElse(null);
        Game game = games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)
                .or(() -> games.findByRoomIdAndStatus(room.getId(), GameStatus.FINISHED))
                .orElse(null);
        MatchBlock match = game == null ? null : matchBlock(game, gameStates.find(game.getId()));
        return new Snapshot(roomBlock, draft, match, null);
    }

    public void publish(String code) {
        websocket.sendToTopic("/topic/rooms/" + code,
                new RoomEventPublisher.EventEnvelope<>("ROOM_SNAPSHOT", compose(code), Instant.now()));
    }

    public void sync(String code, UUID requesterId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        if (requesterId == null) {
            throw new AccessDeniedException("Identidade autenticada obrigatória.");
        }
        Room room = rooms.findByCodeAndStatusIn(code, List.of(RoomStatus.WAITING, RoomStatus.IN_GAME))
                .orElseThrow(() -> new RoomNotFoundException("Sala não encontrada ou encerrada."));
        boolean participant = room.getPlayer1().getId().equals(requesterId)
                || (room.getPlayer2() != null && room.getPlayer2().getId().equals(requesterId));
        if (!participant) {
            throw new AccessDeniedException("Jogador não pertence à sala.");
        }
        publish(code);
    }

    private static DraftBlock draftBlock(DraftState state) {
        return new DraftBlock(state.deadlineEpochMillis(), state.version(),
                List.of(draftPlayer(state.player1()), draftPlayer(state.player2())));
    }

    private static DraftPlayer draftPlayer(DraftState.Player player) {
        return new DraftPlayer(player.playerId(), player.stage().name(), player.characterId(), player.ready());
    }

    private static MatchBlock matchBlock(Game game, Optional<GameState> redisState) {
        GameState state = redisState.orElse(null);
        return new MatchBlock(game.getId(), game.getStatus().name(), game.getCurrentRound(), game.getScore(),
                game.isSuddenDeath(), state == null ? null : state.getBoard(),
                game.getWinner() == null ? null : game.getWinner().getId(),
                state == null ? null : state.getPhase().name(),
                state == null ? null : state.getCurrentTurnPlayerId(),
                state == null ? null : state.getTurnDeadlineEpochMillis());
    }

    public record Snapshot(RoomBlock room, DraftBlock draft, MatchBlock match, RematchBlock rematch) { }
    public record RoomBlock(String code, String status, RoomPlayerInfo player1, RoomPlayerInfo player2) { }
    public record DraftBlock(long deadline, long version, List<DraftPlayer> players) { }
    public record DraftPlayer(UUID playerId, String stage, UUID characterId, boolean ready) { }
    public record MatchBlock(UUID gameId, String status, int round, com.jogodavelha.game.Score score,
                             boolean suddenDeath, String board, UUID winnerId, String phase,
                             UUID currentTurnPlayerId, Long turnDeadline) { }
    public record RematchBlock(String status) { }
}
