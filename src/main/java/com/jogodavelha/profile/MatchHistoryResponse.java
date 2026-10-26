package com.jogodavelha.profile;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record MatchHistoryResponse(
        UUID gameId,
        String opponentUsername,
        String result,
        boolean walkover,
        Instant finishedAt
) {
}