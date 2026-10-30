package com.jogodavelha.coins;

import java.math.BigDecimal;
import java.util.UUID;

public record CoinPackageResponse(
        UUID id,
        Integer coins,
        BigDecimal priceInReais
) {
}
