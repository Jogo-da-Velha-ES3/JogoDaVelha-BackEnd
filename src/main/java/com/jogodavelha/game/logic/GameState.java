package com.jogodavelha.game.logic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.jogodavelha.game.draw.RoundDrawState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

/**
 * Modelo que representa o estado ativo de uma partida em Redis.
 * Serializável em JSON e extensível para campos futuros.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class GameState {

    private UUID gameId;
    private String board;
    private UUID player1Id;
    private UUID player2Id;
    private UUID firstPlayerId;
    private UUID currentTurnPlayerId;
    private long turnDurationMillis;
    private long turnDeadlineEpochMillis;
    private int roundNumber;
    private boolean suddenDeath;
    @Builder.Default
    private GamePhase phase = GamePhase.PLAYING;
    private int player1ConsecutiveTimeouts;
    private int player2ConsecutiveTimeouts;
    private long version;

    public static final int BOARD_SIZE = 16;
    public static final String EMPTY_BOARD = "----------------";
    public static final long DEFAULT_TURN_DURATION_MILLIS = 10000; // 10 segundos
    public static final long HOURGLASS_TURN_DURATION_MILLIS = 5000; // 5 segundos (Ampulheta)
    public static final long SUDDEN_DEATH_TURN_DURATION_MILLIS = 5000; // 5 segundos (morte súbita)

    public record ShieldEffect(UUID ownerId, int turns) {}

    @Builder.Default
    private Map<Integer, ShieldEffect> shieldedPositions = new HashMap<>();
    @Builder.Default
    private Map<Integer, Integer> frozenPositions = new HashMap<>();
    @Builder.Default
    private Map<Integer, Integer> reinforcedPositions = new HashMap<>();
    @Builder.Default
    private Map<UUID, Integer> forceFieldTurns = new HashMap<>();
    @Builder.Default
    private Map<UUID, Integer> silencedTurns = new HashMap<>();
    @Builder.Default
    private Map<UUID, Integer> hourglassTurns = new HashMap<>();
    /** Códigos de habilidades já consumidos por jogador no round atual. */
    @Builder.Default
    private Map<UUID, Set<String>> usedSkillsByPlayer = new HashMap<>();
    /** Habilidades disponíveis a cada jogador no deck selecionado para o round atual. */
    @Builder.Default
    private Map<UUID, Set<String>> roundSkillDecks = new HashMap<>();
    /** Habilidades escolhidas no Draft inicial, preservadas durante toda a partida. */
    @Builder.Default
    private Map<UUID, Set<String>> initialSkillsByPlayer = new HashMap<>();
    /** Habilidades já escolhidas pelo jogador, inclusive as que depois saíram do deck. */
    @Builder.Default
    private Map<UUID, Set<String>> chosenDrawSkillsByPlayer = new HashMap<>();
    private RoundDrawState roundDraw;

    /**
     * Calcula o tempo restante do turno atual em milissegundos.
     * Nunca retorna valor negativo.
     *
     * @return tempo restante em milissegundos (0 se expirado)
     */
    public long getRemainingTimeMillis() {
        long now = Instant.now().toEpochMilli();
        long remaining = turnDeadlineEpochMillis - now;
        return Math.max(0, remaining);
    }

    /**
     * Cria um estado inicial para uma nova partida.
     *
     * @param gameId ID da partida
     * @param firstPlayerId ID do jogador que começa
     * @param turnDurationMillis duração do turno em milissegundos
     * @return estado inicial da partida
     */
    public static GameState createInitial(UUID gameId, UUID firstPlayerId, long turnDurationMillis) {
        long now = Instant.now().toEpochMilli();
        return GameState.builder()
                .gameId(gameId)
                .board(EMPTY_BOARD)
                .firstPlayerId(firstPlayerId)
                .currentTurnPlayerId(firstPlayerId)
                .turnDurationMillis(turnDurationMillis)
                .turnDeadlineEpochMillis(now + turnDurationMillis)
                .roundNumber(1)
                .suddenDeath(false)
                .version(0L)
                .build();
    }

    public static GameState createInitial(UUID gameId, UUID player1Id, UUID player2Id,
                                          long turnDurationMillis, SecureRandom random) {
        if (gameId == null || player1Id == null || player2Id == null || player1Id.equals(player2Id)) {
            throw new IllegalArgumentException("A partida precisa de um ID e dois jogadores diferentes.");
        }
        if (turnDurationMillis <= 0) {
            throw new IllegalArgumentException("A duração do turno deve ser positiva.");
        }
        Objects.requireNonNull(random, "Gerador aleatório é obrigatório.");
        UUID firstPlayer = random.nextBoolean() ? player1Id : player2Id;
        GameState state = createInitial(gameId, firstPlayer, turnDurationMillis);
        state.setPlayer1Id(player1Id);
        state.setPlayer2Id(player2Id);
        return state;
    }

    // As marcas são fixas: P1 usa X e P2 usa O, independentemente de quem começa.
    public char getPlayerSymbol(UUID playerId) {
        return isPlayer1(playerId) ? 'X' : 'O';
    }

    public UUID getOpponentId(UUID playerId) {
        return isPlayer1(playerId) ? player2Id : player1Id;
    }

    public int getConsecutiveTimeouts(UUID playerId) {
        return isPlayer1(playerId) ? player1ConsecutiveTimeouts : player2ConsecutiveTimeouts;
    }

    public void recordTimeout(UUID playerId) {
        if (isPlayer1(playerId)) {
            player1ConsecutiveTimeouts++;
        } else {
            player2ConsecutiveTimeouts++;
        }
    }

    public void resetTimeouts(UUID playerId) {
        if (isPlayer1(playerId)) {
            player1ConsecutiveTimeouts = 0;
        } else {
            player2ConsecutiveTimeouts = 0;
        }
    }

    /** Inicia o round sem alterar contadores. O horário deve ser calculado antes do update. */
    public void startRound(int round, long durationMillis, long startedAtMillis) {
        if (round < 1 || round > 3 || durationMillis <= 0) {
            throw new IllegalArgumentException("Round deve estar entre 1 e 3 e ter duração positiva.");
        }
        UUID opponent = getOpponentId(firstPlayerId);
        board = EMPTY_BOARD;
        clearEffects(); // Efeitos não atravessam rounds
        if (usedSkillsByPlayer != null) usedSkillsByPlayer.clear();
        // O deck evolui entre rounds; apenas o consumo das habilidades reinicia.
        roundDraw = null;
        roundNumber = round;
        phase = GamePhase.PLAYING;
        suddenDeath = round == 3;
        currentTurnPlayerId = round % 2 == 1 ? firstPlayerId : opponent;
        turnDurationMillis = durationMillis;
        turnDeadlineEpochMillis = startedAtMillis + durationMillis;
    }

    public boolean hasUsedSkill(UUID playerId, String skillCode) {
        return usedSkillsByPlayer != null
                && usedSkillsByPlayer.getOrDefault(playerId, Set.of()).contains(skillCode);
    }

    public void recordSkillUse(UUID playerId, String skillCode) {
        if (usedSkillsByPlayer == null) {
            usedSkillsByPlayer = new HashMap<>();
        }
        usedSkillsByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).add(skillCode);
    }

    public boolean hasSkillInRoundDeck(UUID playerId, String skillCode) {
        return roundSkillDecks != null
                && roundSkillDecks.getOrDefault(playerId, Set.of()).contains(skillCode);
    }

    /** Define o deck escolhido para um jogador neste round, incluindo habilidades sorteadas. */
    public void setRoundSkillDeck(UUID playerId, Collection<String> skillCodes) {
        if (playerId == null || skillCodes == null || skillCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Informe jogador e códigos válidos para o deck do round.");
        }
        if (roundSkillDecks == null) {
            roundSkillDecks = new HashMap<>();
        }
        roundSkillDecks.put(playerId, new HashSet<>(skillCodes));
    }

    public void setInitialSkills(UUID playerId, Collection<String> skillCodes) {
        if (playerId == null || skillCodes == null || skillCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Informe jogador e habilidades iniciais válidas.");
        }
        if (initialSkillsByPlayer == null) initialSkillsByPlayer = new HashMap<>();
        initialSkillsByPlayer.put(playerId, new HashSet<>(skillCodes));
        recordChosenDrawSkills(playerId, skillCodes);
    }

    public Set<String> getChosenDrawSkills(UUID playerId) {
        return chosenDrawSkillsByPlayer == null ? Set.of()
                : chosenDrawSkillsByPlayer.getOrDefault(playerId, Set.of());
    }

    public void recordChosenDrawSkill(UUID playerId, String skillCode) {
        if (skillCode == null || skillCode.isBlank()) {
            throw new IllegalArgumentException("Informe a habilidade escolhida.");
        }
        recordChosenDrawSkills(playerId, List.of(skillCode));
    }

    private void recordChosenDrawSkills(UUID playerId, Collection<String> skillCodes) {
        if (playerId == null || skillCodes == null || skillCodes.stream().anyMatch(code -> code == null || code.isBlank())) {
            throw new IllegalArgumentException("Informe jogador e habilidades escolhidas válidas.");
        }
        if (chosenDrawSkillsByPlayer == null) chosenDrawSkillsByPlayer = new HashMap<>();
        chosenDrawSkillsByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>()).addAll(skillCodes);
    }

    private boolean isPlayer1(UUID playerId) {
        if (player1Id == null || player2Id == null) {
            throw new IllegalStateException("O estado ainda não possui os dois jogadores.");
        }
        if (player1Id.equals(playerId)) {
            return true;
        }
        if (player2Id.equals(playerId)) {
            return false;
        }
        throw new IllegalArgumentException("Jogador não pertence à partida.");
    }

    public Set<Integer> shieldSet() {
        return new HashSet<>(shieldedPositions.keySet());
    }

    public Set<Integer> frozenSet() {
        return new HashSet<>(frozenPositions.keySet());
    }

    /* Casas que bloqueiam este jogador (escudos criados pelos oponentes) */
    public Set<Integer> blockedFor(UUID playerId) {
        Set<Integer> blocked = new HashSet<>();
        shieldedPositions.forEach((pos, e) -> {
            if (!e.ownerId().equals(playerId))
                blocked.add(pos);
        });

        return blocked;
    }

    /* Fim da ação: decrementa todos os contadores e remove os que chegam a zero */
    public void tickEffects() {
        shieldedPositions.replaceAll((k, e) -> new ShieldEffect(e.ownerId(), e.turns() - 1));
        shieldedPositions.values().removeIf(e -> e.turns() <= 0);
        tick(frozenPositions);
        tick(reinforcedPositions);
        tick(forceFieldTurns);
        tick(silencedTurns);
        tick(hourglassTurns);
    }

    public void clearEffects() {
        shieldedPositions.clear();
        frozenPositions.clear();
        reinforcedPositions.clear();
        forceFieldTurns.clear();
        silencedTurns.clear();
        hourglassTurns.clear();
    }

    /* Duração do turno de quem vai jogar, considerando a AMPULHETA */
    public long effectiveTurnDuration(UUID nextPlayerId, long baseMillis) {
        return hourglassTurns.containsKey(nextPlayerId)
                ? Math.min(baseMillis, HOURGLASS_TURN_DURATION_MILLIS)
                : baseMillis;
    }

    private static <K> void tick(Map<K, Integer> counters) {
        counters.replaceAll((k, v) -> v - 1);
        counters.values().removeIf(v -> v <= 0);
    }
}
