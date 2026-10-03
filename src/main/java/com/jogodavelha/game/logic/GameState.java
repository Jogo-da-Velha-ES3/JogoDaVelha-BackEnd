package com.jogodavelha.game.logic;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    private UUID firstPlayerId;
    private UUID currentTurnPlayerId;
    private long turnDurationMillis;
    private long turnDeadlineEpochMillis;
    private int roundNumber;
    private boolean suddenDeath;
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
}
