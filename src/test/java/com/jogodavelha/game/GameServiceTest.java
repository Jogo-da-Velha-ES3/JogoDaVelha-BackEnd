package com.jogodavelha.game;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.service.GameService;
import com.jogodavelha.room.Room;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameServiceTest {

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private GameService gameService;

    private UUID gameId;
    private Game game;
    private User player1;
    private User player2;
    private Room room;

    @BeforeEach
    void setUp() {
        gameId = UUID.randomUUID();
        player1 = new User("player1", "pass", "p1@test.com");
        player1.setId(UUID.randomUUID());
        player2 = new User("player2", "pass", "p2@test.com");
        player2.setId(UUID.randomUUID());
        room = new Room("1234", player1);
        room.setId(UUID.randomUUID());
        game = new Game(room, player1);
        game.setPlayer2(player2);
    }

    @Test
    void testRegisterRoundResultGameNotFoundThrowsException() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.empty());

        assertThrows(GameNotFoundException.class, () -> {
            gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);
        });

        verify(gameRepository).findById(gameId);
        verify(gameRepository, never()).save(any());
    }

    @Test
    void testRegisterRoundResultGameInProgress() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        Game result = gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);

        assertNotNull(result);
        assertEquals(2, result.getCurrentRound());
        assertEquals(new Score(1, 0), result.getScore());
        assertEquals(GameStatus.IN_PROGRESS, result.getStatus());
        assertFalse(result.isRewardsGranted());

        verify(gameRepository).findById(gameId);
        verify(gameRepository).save(game);
    }

    @Test
    void testRegisterRoundResultGameFinishedPlayer1Wins() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);
        Game result = gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);

        assertNotNull(result);
        assertEquals(GameStatus.FINISHED, result.getStatus());
        assertEquals(player1, result.getWinner());
        assertEquals(new Score(2, 0), result.getScore());
        assertTrue(result.isRewardsGranted());

        // Verifica pagamento de moedas
        assertEquals(GameService.COINS_WIN, player1.getCoinsBalance());
        assertEquals(GameService.COINS_LOSS, player2.getCoinsBalance());

        verify(gameRepository, times(2)).findById(gameId);
        verify(gameRepository, times(2)).save(game);
    }

    @Test
    void testRegisterRoundResultGameFinishedPlayer2Wins() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameService.registerRoundResult(gameId, RoundResult.PLAYER2_WIN);
        Game result = gameService.registerRoundResult(gameId, RoundResult.PLAYER2_WIN);

        assertNotNull(result);
        assertEquals(GameStatus.FINISHED, result.getStatus());
        assertEquals(player2, result.getWinner());
        assertEquals(new Score(0, 2), result.getScore());
        assertTrue(result.isRewardsGranted());

        // Verifica pagamento de moedas
        assertEquals(GameService.COINS_LOSS, player1.getCoinsBalance());
        assertEquals(GameService.COINS_WIN, player2.getCoinsBalance());

        verify(gameRepository, times(2)).findById(gameId);
        verify(gameRepository, times(2)).save(game);
    }

    @Test
    void testRegisterRoundResultGameFinishedDraw0x0() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameService.registerRoundResult(gameId, RoundResult.DRAW);
        gameService.registerRoundResult(gameId, RoundResult.DRAW);
        Game result = gameService.registerRoundResult(gameId, RoundResult.DRAW);

        assertNotNull(result);
        assertEquals(GameStatus.FINISHED, result.getStatus());
        assertNull(result.getWinner());
        assertEquals(new Score(0, 0), result.getScore());
        assertTrue(result.isRewardsGranted());

        // Verifica pagamento de moedas
        assertEquals(GameService.COINS_DRAW, player1.getCoinsBalance());
        assertEquals(GameService.COINS_DRAW, player2.getCoinsBalance());

        verify(gameRepository, times(3)).findById(gameId);
        verify(gameRepository, times(3)).save(game);
    }

    @Test
    void testRegisterRoundResultGameFinishedDraw1x1() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);
        gameService.registerRoundResult(gameId, RoundResult.PLAYER2_WIN);
        Game result = gameService.registerRoundResult(gameId, RoundResult.DRAW);

        assertNotNull(result);
        assertEquals(GameStatus.FINISHED, result.getStatus());
        assertNull(result.getWinner());
        assertEquals(new Score(1, 1), result.getScore());
        assertTrue(result.isRewardsGranted());

        // Verifica pagamento de moedas
        assertEquals(GameService.COINS_DRAW, player1.getCoinsBalance());
        assertEquals(GameService.COINS_DRAW, player2.getCoinsBalance());

        verify(gameRepository, times(3)).findById(gameId);
        verify(gameRepository, times(3)).save(game);
    }

    @Test
    void testNoCoinsPaidWhileGameInProgress() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);

        assertEquals(0, player1.getCoinsBalance());
        assertEquals(0, player2.getCoinsBalance());

        verify(gameRepository).findById(gameId);
        verify(gameRepository).save(game);
    }

    @Test
    void testCallingServiceTwiceForFinishedGameDoesNotPayTwice() {
        when(gameRepository.findById(gameId)).thenReturn(Optional.of(game));
        when(gameRepository.save(any(Game.class))).thenReturn(game);

        // Finaliza o jogo
        gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);
        gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);

        int coinsAfterFirstCall = player1.getCoinsBalance();

        // Tenta chamar novamente para o jogo já finalizado
        assertThrows(IllegalStateException.class, () -> {
            gameService.registerRoundResult(gameId, RoundResult.PLAYER1_WIN);
        });

        // Verifica que o saldo não mudou
        assertEquals(coinsAfterFirstCall, player1.getCoinsBalance());
        assertEquals(GameService.COINS_LOSS, player2.getCoinsBalance());

        verify(gameRepository, times(3)).findById(gameId);
        verify(gameRepository, times(2)).save(game);
    }

    @Test
    void testConstants() {
        assertEquals(50, GameService.COINS_WIN);
        assertEquals(20, GameService.COINS_DRAW);
        assertEquals(10, GameService.COINS_LOSS);
    }
}
