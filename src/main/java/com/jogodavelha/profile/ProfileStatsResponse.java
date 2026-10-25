package com.jogodavelha.profile;

public record ProfileStatsResponse(
        int matchesPlayed,
        int wins,
        int currentWinStreak,
        int longestWinStreak,
        double winRate
) {
}
