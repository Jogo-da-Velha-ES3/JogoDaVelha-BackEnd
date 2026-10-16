package com.jogodavelha.character;

import java.util.UUID;

public record CharacterDTO(
        UUID id,
        String name,
        CharacterSymbol symbol,
        Integer priceCoins
) {

    public static CharacterDTO from(Character character) {
        return new CharacterDTO(
                character.getId(),
                character.getName(),
                character.getSymbol(),
                character.getPriceCoins()
        );
    }
}
