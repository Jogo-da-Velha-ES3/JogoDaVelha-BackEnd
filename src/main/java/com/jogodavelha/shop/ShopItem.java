package com.jogodavelha.shop;

import java.util.UUID;

public record ShopItem(UUID id, ShopCategory category, String code, String name, String description,
                       int priceCoins, boolean owned, boolean equipped) { }
