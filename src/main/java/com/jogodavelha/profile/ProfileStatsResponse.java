package com.jogodavelha.profile;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProfileStatsResponse(
        int matchesPlayed,
        int wins,
        int currentWinStreak,
        int longestWinStreak,
        double winRate
) {
}
