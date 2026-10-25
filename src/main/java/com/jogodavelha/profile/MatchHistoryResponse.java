package com.jogodavelha.profile;

import java.time.Instant;
import java.util.UUID;

public record MatchHistoryResponse(
        UUID gameId,
        String opponentUsername,
        String result,
        boolean walkover,
        Instant finishedAt
) {
}