package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "user_stats")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserStats {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            unique = true
    )
    private User user;

    @Column(name = "matches_played", nullable = false)
    private int matchesPlayed = 0;

    @Column(nullable = false)
    private int wins = 0;

    @Column(name = "current_win_streak", nullable = false)
    private int currentWinStreak = 0;

    @Column(name = "longest_win_streak", nullable = false)
    private int longestWinStreak = 0;

    public UserStats(User user) {
        this.user = user;
    }

    public void registerWin() {
        matchesPlayed++;
        wins++;

        currentWinStreak++;

        if (currentWinStreak > longestWinStreak) {
            longestWinStreak = currentWinStreak;
        }
    }

    public void registerDraw() {
        matchesPlayed++;
        currentWinStreak = 0;
    }

    public void registerLoss() {
        matchesPlayed++;
        currentWinStreak = 0;
    }

    public double getWinRate() {
        if (matchesPlayed == 0) {
            return 0.0;
        }

        return (wins * 100.0) / matchesPlayed;
    }
}
