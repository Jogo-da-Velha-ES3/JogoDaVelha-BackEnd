package com.jogodavelha.coins;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record BalanceResponse(
        Integer coinsBalance
) {
}
