package com.jogodavelha.coins;

import java.util.UUID;

public record CoinPackageResponse(
        UUID id,
        Integer coins
) {
}
