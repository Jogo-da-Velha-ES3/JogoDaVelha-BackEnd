package com.jogodavelha.character;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
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
