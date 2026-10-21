package com.jogodavelha.game.service;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.*;
import com.jogodavelha.room.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Serviço responsável pela orquestração de partidas.
 * Gerencia o registro de resultados de rounds e o pagamento de recompensas.
 */
@Service
@RequiredArgsConstructor
public class GameService {

    public static final int COINS_WIN = 50;
    public static final int COINS_DRAW = 20;
    public static final int COINS_LOSS = 10;
    public static final int COINS_FORFEIT_WIN = 30;

    private final GameRepository gameRepository;

    @Transactional
    public Game startGame(Room room) {
        if (room == null || !room.isReadyToStart()) {
            throw new IllegalArgumentException("Sala sem dois jogadores prontos para iniciar.");
        }
        return gameRepository.findByRoomId(room.getId()).orElseGet(() -> {
            Game game = new Game(room, room.getPlayer1());
            game.setPlayer2(room.getPlayer2());
            return gameRepository.saveAndFlush(game);
        });
    }

    /**
     * Registra o resultado de um round em uma partida.
     * Se a partida terminar após este round, concede as recompensas em moedas.
     *
     * @param gameId ID da partida
     * @param result Resultado do round
     * @return A partida atualizada
     * @throws GameNotFoundException se a partida não existir
     */
    @Transactional
    public Game registerRoundResult(UUID gameId, RoundResult result) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));

        game.registerRoundResult(result);

        if (game.getStatus() == GameStatus.FINISHED) {
            grantRewards(game);
        }

        return gameRepository.save(game);
    }

    /** Registers a round result once; delayed duplicate callbacks are ignored. */
    @Transactional
    public Game registerRoundResult(UUID gameId, int expectedRound, RoundResult result) {
        return applyRoundResult(gameId, expectedRound, result).game();
    }

    @Transactional
    public RoundUpdate applyRoundResult(UUID gameId, int expectedRound, RoundResult result) {
        Game game = gameRepository.findByIdForUpdate(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        if (game.getCurrentRound() != expectedRound || game.getStatus() == GameStatus.FINISHED) {
            return new RoundUpdate(game, false);
        }
        game.registerRoundResult(result);
        if (game.getStatus() == GameStatus.FINISHED) grantRewards(game);
        return new RoundUpdate(gameRepository.save(game), true);
    }

    @Transactional
    public Game forfeit(UUID gameId, User loser) {
        Game game = gameRepository.findById(gameId)
                .orElseThrow(() -> new GameNotFoundException(gameId));
        game.forfeit(loser);
        grantRewards(game);
        return gameRepository.save(game);
    }

    /**
     * Concede recompensas em moedas aos jogadores.
     * Normal: 50/10/20 para vitória/derrota/empate. W.O.: 30 apenas ao vencedor.
     * Só concede se rewardsGranted for false.
     *
     * @param game A partida finalizada
     */
    private void grantRewards(Game game) {
        if (game.isRewardsGranted()) {
            return;
        }

        if (game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT) {
            game.getWinner().addCoins(COINS_FORFEIT_WIN);
            game.markRewardsGranted();
            return;
        }

        GameOutcome outcome = game.getOutcome();

        switch (outcome) {
            case DRAW -> {
                game.getPlayer1().addCoins(COINS_DRAW);
                game.getPlayer2().addCoins(COINS_DRAW);
            }
            case PLAYER1_WIN -> {
                game.getPlayer1().addCoins(COINS_WIN);
                game.getPlayer2().addCoins(COINS_LOSS);
            }
            case PLAYER2_WIN -> {
                game.getPlayer2().addCoins(COINS_WIN);
                game.getPlayer1().addCoins(COINS_LOSS);
            }
        }

        game.markRewardsGranted();
    }
}
