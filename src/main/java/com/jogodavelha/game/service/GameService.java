package com.jogodavelha.game.service;

import com.jogodavelha.game.*;
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

    private final GameRepository gameRepository;

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

    /**
     * Concede recompensas em moedas aos jogadores.
     * Vencedor: +50, Perdedor: +10, Empate: +20 para cada.
     * Só concede se rewardsGranted for false.
     *
     * @param game A partida finalizada
     */
    private void grantRewards(Game game) {
        if (game.isRewardsGranted()) {
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
