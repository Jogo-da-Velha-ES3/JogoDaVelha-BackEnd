package com.jogodavelha.game.logic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Modelo que representa o estado ativo de uma partida em Redis.
 * Serializável em JSON e extensível para campos futuros.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
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
    private int player1ConsecutiveTimeouts;
    private int player2ConsecutiveTimeouts;
    private long version;

    public static final int BOARD_SIZE = 16;
    public static final String EMPTY_BOARD = "----------------";
    public static final long DEFAULT_TURN_DURATION_MILLIS = 10000; // 10 segundos

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
        roundNumber = round;
        suddenDeath = round == 3;
        currentTurnPlayerId = round % 2 == 1 ? firstPlayerId : opponent;
        turnDurationMillis = durationMillis;
        turnDeadlineEpochMillis = startedAtMillis + durationMillis;
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
}
