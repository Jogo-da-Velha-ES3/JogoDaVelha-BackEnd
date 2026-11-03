package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserStatsServiceTest {

    @Mock
    private UserStatsRepository userStatsRepository;

    @InjectMocks
    private UserStatsService userStatsService;

    @Test
    void registerWinUpdatesExistingStats() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerWin(user);

        assertEquals(2, stats.getMatchesPlayed());
        assertEquals(2, stats.getWins());
        assertEquals(2, stats.getCurrentWinStreak());
        assertEquals(2, stats.getLongestWinStreak());
        verify(userStatsRepository, never()).save(any());
    }

    @Test
    void registerWinCreatesStatsIfNotExists() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userStatsService.registerWin(user);

        verify(userStatsRepository).save(any(UserStats.class));
    }

    @Test
    void registerDrawUpdatesExistingStats() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerDraw(user);

        assertEquals(2, stats.getMatchesPlayed());
        assertEquals(1, stats.getWins());
        assertEquals(0, stats.getCurrentWinStreak());
        assertEquals(1, stats.getLongestWinStreak());
    }

    @Test
    void registerDrawCreatesStatsIfNotExists() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userStatsService.registerDraw(user);

        verify(userStatsRepository).save(any(UserStats.class));
    }

    @Test
    void registerLossUpdatesExistingStats() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerLoss(user);

        assertEquals(2, stats.getMatchesPlayed());
        assertEquals(1, stats.getWins());
        assertEquals(0, stats.getCurrentWinStreak());
        assertEquals(1, stats.getLongestWinStreak());
    }

    @Test
    void registerLossCreatesStatsIfNotExists() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.empty());
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(invocation -> invocation.getArgument(0));

        userStatsService.registerLoss(user);

        verify(userStatsRepository).save(any(UserStats.class));
    }

    @Test
    void winStreakResetOnDraw() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();
        stats.registerWin();
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerDraw(user);

        assertEquals(0, stats.getCurrentWinStreak());
        assertEquals(3, stats.getLongestWinStreak());
    }

    @Test
    void winStreakResetOnLoss() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();
        stats.registerWin();
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerLoss(user);

        assertEquals(0, stats.getCurrentWinStreak());
        assertEquals(3, stats.getLongestWinStreak());
    }

    @Test
    void winStreakIncreasesOnWin() {
        UUID userId = UUID.randomUUID();
        User user = new User("testuser", "hash", "test@test.com");
        user.setId(userId);

        UserStats stats = new UserStats(user);
        stats.registerWin();

        when(userStatsRepository.findByUser_Id(userId)).thenReturn(Optional.of(stats));

        userStatsService.registerWin(user);

        assertEquals(2, stats.getCurrentWinStreak());
        assertEquals(2, stats.getLongestWinStreak());
    }
}
