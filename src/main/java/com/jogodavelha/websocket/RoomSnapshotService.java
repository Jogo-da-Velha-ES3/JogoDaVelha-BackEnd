package com.jogodavelha.websocket;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameOutcome;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.RewardResult;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/** Composes the frontend RoomSnapshot and keeps player-specific fields off the shared room topic. */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RoomSnapshotService {
    private static final long SNAPSHOT_LATENCY_WARNING_NANOS = 200_000_000L;
    private static final List<RoomStatus> ACTIVE_ROOM_STATUSES = List.of(RoomStatus.WAITING, RoomStatus.IN_GAME);
    private final RoomRepository rooms;
    private final GameRepository games;
    private final DraftStateManager drafts;
    private final GameStateManager gameStates;
    private final RoomPlayerService roomPlayers;
    private final PlayerService playerService;
    private final CharacterRepository characters;
    private final WebSocketService websocket;

    public RoomSnapshot compose(String code, UUID viewerId) {
        Room room = activeRoom(code);
        Slot mySlot = slot(room, viewerId);
        if (mySlot == null) throw new AccessDeniedException("Jogador não pertence à sala.");
        return compose(room, mySlot, true, System.currentTimeMillis());
    }

    /** Broadcasts a safe room view, then sends a complete, individualized snapshot to each member. */
    public void publish(String code) {
        long startedAt = System.nanoTime();
        Room room = activeRoom(code);
        long serverTime = System.currentTimeMillis();
        State state = readState(room);
        RoomSnapshot publicSnapshot = snapshot(room, null, false, state, serverTime);
        websocket.sendToTopic("/topic/rooms/" + code,
                new SnapshotEnvelope("ROOM_SNAPSHOT", publicSnapshot, serverTime));
        sendPrivate(room, room.getPlayer1().getId(), Slot.P1, state, serverTime);
        if (room.getPlayer2() != null) {
            sendPrivate(room, room.getPlayer2().getId(), Slot.P2, state, serverTime);
        }
        long elapsedNanos = System.nanoTime() - startedAt;
        if (elapsedNanos > SNAPSHOT_LATENCY_WARNING_NANOS) {
            log.warn("ROOM_SNAPSHOT composition and server dispatch exceeded 200 ms: roomCode={}, elapsedMs={}",
                    code, elapsedNanos / 1_000_000.0);
        } else {
            log.debug("ROOM_SNAPSHOT composed and dispatched: roomCode={}, elapsedMs={}",
                    code, elapsedNanos / 1_000_000.0);
        }
    }

    public void sync(String code, UUID requesterId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        if (requesterId == null) throw new AccessDeniedException("Identidade autenticada obrigatória.");
        Room room = activeRoom(code);
        Slot requesterSlot = slot(room, requesterId);
        if (requesterSlot == null) throw new AccessDeniedException("Jogador não pertence à sala.");

        long serverTime = System.currentTimeMillis();
        State state = readState(room);
        websocket.sendToUser(requesterId.toString(), "/queue/room",
                new SnapshotEnvelope("ROOM_SNAPSHOT", snapshot(room, requesterSlot, true, state, serverTime), serverTime));
    }

    private RoomSnapshot compose(Room room, Slot mySlot, boolean includePrivate, long serverTime) {
        return snapshot(room, mySlot, includePrivate, readState(room), serverTime);
    }

    private RoomSnapshot snapshot(Room room, Slot mySlot, boolean includePrivate, State state, long serverTime) {
        MatchSnapshot match = match(room, state);
        List<PlayerState> playerStates = players(room, state, match);
        DraftSnapshot draft = state.draftOptional().filter(value -> state.game() == null)
                .map(value -> new DraftSnapshot(value.deadlineEpochMillis(),
                Map.of(Slot.P1, value.player1().ready(), Slot.P2, value.player2().ready()))).orElse(null);
        RoomPhase phase = phase(room, state);
        List<DeckSlot> deck = includePrivate && mySlot != null ? myDeck(state.gameStateOptional().orElse(null),
                mySlot == Slot.P1 ? room.getPlayer1().getId() : room.getPlayer2().getId()) : List.of();
        Turn turn = match.turn();

        return new RoomSnapshot(state.game() == null ? null : state.game().getId(), room.getCode(), phase, playerStates,
                includePrivate ? mySlot : null,
                match.round(), match.suddenDeath(), board(state.gameStateOptional().orElse(null)), turn,
                deck, match.outcome(), serverTime, draft,
                includePrivate && mySlot != null ? drawOptions(state.gameState(),
                        mySlot == Slot.P1 ? room.getPlayer1().getId() : room.getPlayer2().getId()) : null,
                match.coins(), null);
    }

    private void sendPrivate(Room room, UUID userId, Slot slot, State state, long serverTime) {
        websocket.sendToUser(userId.toString(), "/queue/room",
                new SnapshotEnvelope("ROOM_SNAPSHOT", snapshot(room, slot, true, state, serverTime), serverTime));
    }

    private State readState(Room room) {
        DraftState draft = drafts.find(room.getId()).orElse(null);
        Game game = games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)
                .or(() -> games.findByRoomIdAndStatus(room.getId(), GameStatus.FINISHED)).orElse(null);
        GameState gameState = game == null ? null : gameStates.find(game.getId()).orElse(null);
        return new State(draft, game, gameState);
    }

    private List<PlayerState> players(Room room, State state, MatchSnapshot match) {
        Map<UUID, String> draftCharacters = state.draftOptional().map(draft -> {
            List<UUID> selected = java.util.stream.Stream.of(draft.player1(), draft.player2())
                    .map(DraftState.Player::characterId).filter(id -> id != null).toList();
            Map<UUID, String> symbols = characters.findAllById(selected).stream()
                    .collect(Collectors.toMap(Character::getId, character -> character.getSymbol().name()));
            return java.util.stream.Stream.of(draft.player1(), draft.player2())
                    .filter(player -> player.characterId() != null)
                    .collect(Collectors.toMap(DraftState.Player::playerId,
                            player -> symbols.get(player.characterId())));
        }).orElse(Map.of());
        Game game = state.game();
        GameState gameState = state.gameStateOptional().orElse(null);
        List<PlayerState> result = new ArrayList<>();
        result.add(player(room, room.getPlayer1().getId(), Slot.P1,
                game == null || game.getPlayer1Character() == null ? null : game.getPlayer1Character().getSymbol().name(),
                draftCharacters, game, gameState, match));
        if (room.getPlayer2() != null) {
            result.add(player(room, room.getPlayer2().getId(), Slot.P2,
                    game == null || game.getPlayer2Character() == null ? null : game.getPlayer2Character().getSymbol().name(),
                    draftCharacters, game, gameState, match));
        }
        return List.copyOf(result);
    }

    private PlayerState player(Room room, UUID id, Slot slot, String persistedCharacter,
                               Map<UUID, String> draftCharacters, Game game, GameState state, MatchSnapshot match) {
        String character = persistedCharacter;
        if (character == null && game == null) {
            character = draftCharacters.get(id);
        }
        int wins = game == null ? 0 : slot == Slot.P1 ? game.getScore().player1() : game.getScore().player2();
        int timeouts = state == null ? 0 : state.getConsecutiveTimeouts(id);
        int forceField = state == null ? 0 : state.getForceFieldTurns().getOrDefault(id, 0);
        boolean silenced = state != null && state.getSilencedTurns().getOrDefault(id, 0) > 0;
        return new PlayerState(slot, id.toString(), slot == Slot.P1 ? roomPlayers.host(room).username()
                : roomPlayers.info(room.getPlayer2()).username(), character, wins, timeouts, forceField, silenced,
                playerService.isConnected(room.getCode(), id));
    }

    private MatchSnapshot match(Room room, State state) {
        Game game = state.game();
        GameState gs = state.gameStateOptional().orElse(null);
        if (game == null) return new MatchSnapshot(1, false, null, null, null);
        Slot turnSlot = gs == null || gs.getCurrentTurnPlayerId() == null ? null
                : gs.getCurrentTurnPlayerId().equals(room.getPlayer1().getId()) ? Slot.P1 : Slot.P2;
        Turn turn = gs == null || gs.getPhase() != GamePhase.PLAYING || turnSlot == null ? null
                : new Turn(turnSlot, gs.getTurnDeadlineEpochMillis() - gs.getTurnDurationMillis(),
                gs.getTurnDeadlineEpochMillis(), (int) (gs.getTurnDurationMillis() / 1000));
        return new MatchSnapshot(game.getCurrentRound(), game.isSuddenDeath(), turn,
                outcome(game, room), creditedCoins(game));
    }

    private Map<Slot, Integer> creditedCoins(Game game) {
        RewardResult rewards = game.getRewardResult();
        if (rewards == null) return null;
        return Map.of(Slot.P1, rewards.player1Reward(), Slot.P2, rewards.player2Reward());
    }

    private Outcome outcome(Game game, Room room) {
        if (game.getStatus() != GameStatus.FINISHED) return null;
        Slot winner = game.getWinner() == null ? null
                : game.getWinner().getId().equals(room.getPlayer1().getId()) ? Slot.P1 : Slot.P2;
        List<Integer> score = List.of(game.getScore().player1(), game.getScore().player2());
        if (game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT) return Outcome.wo(winner, score, game.getEndReason().name());
        if (game.getOutcome() == GameOutcome.DRAW) return Outcome.draw(score, game.getEndReason().name());
        return Outcome.win(winner, score, game.getEndReason().name());
    }

    private List<Cell> board(GameState state) {
        if (state == null || state.getBoard() == null) return emptyBoard();
        return IntStream.range(0, GameState.BOARD_SIZE).mapToObj(index -> {
            char mark = state.getBoard().charAt(index);
            Slot owner = mark == '-' ? null : mark == 'X' ? Slot.P1 : Slot.P2;
            List<CellEffect> effects = new ArrayList<>();
            var shield = state.getShieldedPositions().get(index);
            if (shield != null) effects.add(new CellEffect("ESCUDO", shield.turns()));
            Integer frozen = state.getFrozenPositions().get(index);
            if (frozen != null) effects.add(new CellEffect("CONGELAR", frozen));
            Integer reinforced = state.getReinforcedPositions().get(index);
            if (reinforced != null) effects.add(new CellEffect("REFORCO", reinforced));
            return new Cell(index, owner, List.copyOf(effects));
        }).toList();
    }

    private static List<Cell> emptyBoard() {
        return IntStream.range(0, GameState.BOARD_SIZE).mapToObj(index -> new Cell(index, null, List.of())).toList();
    }

    private List<DeckSlot> myDeck(GameState state, UUID playerId) {
        if (state == null) return List.of();
        Set<String> initial = state.getInitialSkillsByPlayer().getOrDefault(playerId, Set.of());
        Set<String> deck = state.getRoundSkillDecks().getOrDefault(playerId, initial);
        return deck.stream().sorted().map(ability -> new DeckSlot(ability, initial.contains(ability) ? "INITIAL" : "DRAWN",
                state.hasUsedSkill(playerId, ability))).toList();
    }

    private RoomPhase phase(Room room, State state) {
        if (room.getStatus() == RoomStatus.WAITING) return RoomPhase.WAITING;
        GameState gameState = state.gameStateOptional().orElse(null);
        if (gameState != null) {
            return switch (gameState.getPhase()) {
                case DRAFT -> gameState.getRoundDraw() != null ? RoomPhase.ROUND_DRAW : RoomPhase.DRAFT;
                case PLAYING -> RoomPhase.PLAYING;
                case ROUND_FINISHED -> RoomPhase.ROUND_END;
                case FINISHED -> RoomPhase.RESULT;
            };
        }
        if (state.draft() != null && state.game() == null) return RoomPhase.DRAFT;
        return state.game() != null && state.game().getStatus() == GameStatus.FINISHED
                ? RoomPhase.RESULT : RoomPhase.MATCHED;
    }

    private DrawOptions drawOptions(GameState state, UUID viewerId) {
        if (state == null || state.getPhase() != GamePhase.DRAFT || state.getRoundDraw() == null) return null;
        var player = state.getRoundDraw().players().get(viewerId);
        return player == null || player.selected() != null ? null
                : new DrawOptions(player.options(), state.getRoundDraw().deadline());
    }

    private Room activeRoom(String code) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        return rooms.findByCodeAndStatusIn(code, ACTIVE_ROOM_STATUSES)
                .orElseThrow(() -> new RoomNotFoundException("Sala não encontrada ou encerrada."));
    }

    private Slot slot(Room room, UUID userId) {
        if (room.getPlayer1().getId().equals(userId)) return Slot.P1;
        if (room.getPlayer2() != null && room.getPlayer2().getId().equals(userId)) return Slot.P2;
        return null;
    }

    public enum Slot { P1, P2 }
    public enum RoomPhase { WAITING, MATCHED, DRAFT, VERSUS, ROUND_DRAW, PLAYING, ROUND_END, RESULT }
    public record SnapshotEnvelope(String type, RoomSnapshot payload, long serverTime) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record RoomSnapshot(UUID gameId, String code, RoomPhase phase, List<PlayerState> players, Slot mySlot,
                               int round, boolean suddenDeath, List<Cell> board, Turn turn,
                               List<DeckSlot> myDeck, Outcome outcome, long serverTime,
                               DraftSnapshot draft, DrawOptions drawOptions, Map<Slot, Integer> coins,
                               Rematch rematch) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record PlayerState(Slot slot, String userId, String nickname, String character, int roundsWon,
                              int consecutiveTimeouts, int forceFieldTurnsLeft, boolean silenced, boolean connected) { }
    @JsonInclude(JsonInclude.Include.ALWAYS)
    public record Cell(int index, Slot owner, List<CellEffect> effects) { }
    public record CellEffect(String kind, int turnsLeft) { }
    public record Turn(Slot player, long startedAt, long deadline, int durationSeconds) { }
    public record DeckSlot(String ability, String source, boolean usedThisRound) { }
    public record Outcome(String kind, Slot winner, List<Integer> score, String reason) {
        static Outcome win(Slot winner, List<Integer> score, String reason) { return new Outcome("WIN", winner, score, reason); }
        static Outcome draw(List<Integer> score, String reason) { return new Outcome("ABSOLUTE_DRAW", null, score, reason); }
        static Outcome wo(Slot winner, List<Integer> score, String reason) { return new Outcome("WO", winner, score, reason); }
    }
    public record DraftSnapshot(long deadline, Map<Slot, Boolean> ready) { }
    public record DrawOptions(List<String> options, long deadline) { }
    public record Rematch(Slot by, long deadline) { }
    private record State(DraftState draft, Game game, GameState gameState) {
        Optional<GameState> gameStateOptional() { return Optional.ofNullable(gameState); }
        Optional<DraftState> draftOptional() { return Optional.ofNullable(draft); }
    }
    private record MatchSnapshot(int round, boolean suddenDeath, Turn turn, Outcome outcome, Map<Slot, Integer> coins) { }
}
