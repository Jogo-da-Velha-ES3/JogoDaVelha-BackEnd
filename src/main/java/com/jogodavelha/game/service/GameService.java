package com.jogodavelha.game.service;

import com.jogodavelha.coins.CoinTransactionService;
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
    private final CoinTransactionService coinTransactionService;

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
            game.setRewardResult(
                    grantRewards(game)
            );
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
        if (game.getStatus() == GameStatus.FINISHED) {
            game.setRewardResult(
                    grantRewards(game)
            );
        }

        return gameRepository.save(game);
    }

    /**
     * Concede recompensas em moedas aos jogadores.
     * Normal: 50/10/20 para vitória/derrota/empate. W.O.: 30 apenas ao vencedor.
     * Só concede se rewardsGranted for false.
     *
     * @param game A partida finalizada
     */
    private RewardResult grantRewards(Game game) {
        if (game.isRewardsGranted()) {
            return new RewardResult(0, 0);
        }

        if (game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT) {

            creditCoins(
                    game.getWinner(),
                    COINS_FORFEIT_WIN,
                    "MATCH_FORFEIT_WIN"
            );

            game.markRewardsGranted();

            if (game.getWinner().getId().equals(game.getPlayer1().getId())) {
                return new RewardResult(
                        COINS_FORFEIT_WIN,
                        0
                );
            }

            return new RewardResult(
                    0,
                    COINS_FORFEIT_WIN
            );
        }

        GameOutcome outcome = game.getOutcome();

        switch (outcome) {
            case DRAW -> {
                creditCoins(
                        game.getPlayer1(),
                        COINS_DRAW,
                        "MATCH_DRAW"
                );

                creditCoins(
                        game.getPlayer2(),
                        COINS_DRAW,
                        "MATCH_DRAW"
                );

                game.markRewardsGranted();

                return new RewardResult(
                        COINS_DRAW,
                        COINS_DRAW
                );
            }

            case PLAYER1_WIN -> {
                creditCoins(
                        game.getPlayer1(),
                        COINS_WIN,
                        "MATCH_WIN"
                );

                creditCoins(
                        game.getPlayer2(),
                        COINS_LOSS,
                        "MATCH_LOSS"
                );

                game.markRewardsGranted();

                return new RewardResult(
                        COINS_WIN,
                        COINS_LOSS
                );
            }
            case PLAYER2_WIN -> {
                creditCoins(
                        game.getPlayer2(),
                        COINS_WIN,
                        "MATCH_WIN"
                );

                creditCoins(
                        game.getPlayer1(),
                        COINS_LOSS,
                        "MATCH_LOSS"
                );

                game.markRewardsGranted();

                return new RewardResult(
                        COINS_LOSS,
                        COINS_WIN
                );
            }
        }
        throw new IllegalStateException(
                "Resultado da partida não suportado."
        );
    }

    private void creditCoins(
            User user,
            int amount,
            String source
    ) {
        user.addCoins(amount);

        coinTransactionService.registerCredit(
                user,
                amount,
                source
        );
    }
}
