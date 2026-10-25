package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.auth.UsernameAlreadyExistsException;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserStatsRepository userStatsRepository;

    @Mock
    private CharacterRepository characterRepository;

    @InjectMocks
    private ProfileService profileService;

    @Test
    void getProfileReturnsUserData() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of());

        ProfileResponse response = profileService.getProfile(userId);

        assertEquals("testuser", response.username());
        assertEquals(100, response.coinsBalance());
        assertNull(response.avatar());
        assertNull(response.preferredCharacter());
    }

    @Test
    void getProfileReturnsFavoriteCharacter() {
        UUID userId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);

        Character character = new Character("X", CharacterSymbol.X, 0);
        character.setId(characterId);

        Game game = mock(Game.class);
        when(game.getPlayer1()).thenReturn(user);
        when(game.getPlayer1Character()).thenReturn(character);
        when(game.getPlayer2()).thenReturn(mock(User.class));
        when(game.getPlayer2Character()).thenReturn(null);
        when(game.getFinishedAt()).thenReturn(Instant.now());

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game));
        when(characterRepository.findById(characterId)).thenReturn(Optional.of(character));

        ProfileResponse response = profileService.getProfile(userId);

        assertEquals("X", response.preferredCharacter());
    }

    @Test
    void getProfileReturnsNullWhenNoGames() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of());

        ProfileResponse response = profileService.getProfile(userId);

        assertNull(response.preferredCharacter());
    }

    @Test
    void getProfileReturnsNullWhenNoCharacterInGames() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);

        Game game = mock(Game.class);
        when(game.getPlayer1()).thenReturn(user);
        when(game.getPlayer1Character()).thenReturn(null);
        when(game.getPlayer2()).thenReturn(mock(User.class));
        when(game.getPlayer2Character()).thenReturn(null);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game));

        ProfileResponse response = profileService.getProfile(userId);

        assertNull(response.preferredCharacter());
    }

    @Test
    void getProfileTieBreakerByMostRecentUsage() {
        UUID userId = UUID.randomUUID();
        UUID character1Id = UUID.randomUUID();
        UUID character2Id = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);

        Character character1 = new Character("X", CharacterSymbol.X, 0);
        character1.setId(character1Id);
        Character character2 = new Character("O", CharacterSymbol.O, 0);
        character2.setId(character2Id);

        Instant now = Instant.now();
        Instant earlier = now.minusSeconds(3600);

        Game game1 = mock(Game.class);
        when(game1.getPlayer1()).thenReturn(user);
        when(game1.getPlayer1Character()).thenReturn(character1);
        when(game1.getPlayer2()).thenReturn(mock(User.class));
        when(game1.getPlayer2Character()).thenReturn(null);
        when(game1.getFinishedAt()).thenReturn(earlier);

        Game game2 = mock(Game.class);
        when(game2.getPlayer1()).thenReturn(user);
        when(game2.getPlayer1Character()).thenReturn(character2);
        when(game2.getPlayer2()).thenReturn(mock(User.class));
        when(game2.getPlayer2Character()).thenReturn(null);
        when(game2.getFinishedAt()).thenReturn(now);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game1, game2));
        when(characterRepository.findById(character2Id)).thenReturn(Optional.of(character2));

        ProfileResponse response = profileService.getProfile(userId);

        assertEquals("O", response.preferredCharacter());
    }

    @Test
    void getStatsReturnsUserStats() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();
        stats.registerWin();
        stats.registerLoss();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        ProfileStatsResponse response = profileService.getStats(userId);

        assertEquals(3, response.matchesPlayed());
        assertEquals(2, response.wins());
        assertEquals(2, response.currentWinStreak());
        assertEquals(2, response.longestWinStreak());
        assertEquals(66.67, response.winRate(), 0.01);
    }

    @Test
    void getStatsCreatesStatsIfNotExists() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ProfileStatsResponse response = profileService.getStats(userId);

        assertEquals(0, response.matchesPlayed());
        assertEquals(0, response.wins());
        assertEquals(0, response.currentWinStreak());
        assertEquals(0, response.longestWinStreak());
        assertEquals(0.0, response.winRate());
    }

    @Test
    void updateUsernameAllowsSameUsername() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> profileService.updateUsername(userId, "testuser"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUsernameAllowsSameUsernameDifferentCase() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        assertDoesNotThrow(() -> profileService.updateUsername(userId, "TESTUSER"));

        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUsernameRejectsDuplicateUsername() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(true);

        UsernameAlreadyExistsException exception = assertThrows(
                UsernameAlreadyExistsException.class,
                () -> profileService.updateUsername(userId, "newuser")
        );

        assertEquals("Username já está em uso", exception.getMessage());
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateUsernameChangesUsername() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.existsByUsernameIgnoreCase("newuser")).thenReturn(false);
        when(userRepository.save(user)).thenReturn(user);

        profileService.updateUsername(userId, "newuser");

        assertEquals("newuser", user.getUsername());
        verify(userRepository).save(user);
    }

    @Test
    void getHistoryReturnsMatchHistory() {
        UUID userId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        User opponent = new User("opponent", "hash", "opp@test.com");
        opponent.setId(opponentId);

        Game game = mock(Game.class);
        when(game.getId()).thenReturn(UUID.randomUUID());
        when(game.getPlayer1()).thenReturn(user);
        when(game.getPlayer2()).thenReturn(opponent);
        when(game.getWinner()).thenReturn(user);
        when(game.getEndReason()).thenReturn(GameEndReason.NORMAL);
        when(game.getFinishedAt()).thenReturn(Instant.now());

        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game));

        List<MatchHistoryResponse> history = profileService.getHistory(userId);

        assertEquals(1, history.size());
        assertEquals("WIN", history.get(0).result());
        assertEquals("opponent", history.get(0).opponentUsername());
        assertFalse(history.get(0).walkover());
    }

    @Test
    void getHistoryIndicatesForfeit() {
        UUID userId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        User opponent = new User("opponent", "hash", "opp@test.com");
        opponent.setId(opponentId);

        Game game = mock(Game.class);
        when(game.getId()).thenReturn(UUID.randomUUID());
        when(game.getPlayer1()).thenReturn(user);
        when(game.getPlayer2()).thenReturn(opponent);
        when(game.getWinner()).thenReturn(user);
        when(game.getEndReason()).thenReturn(GameEndReason.FORFEIT_TIMEOUT);
        when(game.getFinishedAt()).thenReturn(Instant.now());

        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game));

        List<MatchHistoryResponse> history = profileService.getHistory(userId);

        assertEquals(1, history.size());
        assertTrue(history.get(0).walkover());
    }

    @Test
    void getHistoryHandlesDraw() {
        UUID userId = UUID.randomUUID();
        UUID opponentId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);
        User opponent = new User("opponent", "hash", "opp@test.com");
        opponent.setId(opponentId);

        Game game = mock(Game.class);
        when(game.getId()).thenReturn(UUID.randomUUID());
        when(game.getPlayer1()).thenReturn(user);
        when(game.getPlayer2()).thenReturn(opponent);
        when(game.getWinner()).thenReturn(null);
        when(game.getEndReason()).thenReturn(GameEndReason.NORMAL);
        when(game.getFinishedAt()).thenReturn(Instant.now());

        when(gameRepository.findFinishedGamesByPlayerId(userId)).thenReturn(List.of(game));

        List<MatchHistoryResponse> history = profileService.getHistory(userId);

        assertEquals(1, history.size());
        assertEquals("DRAW", history.get(0).result());
    }
}
