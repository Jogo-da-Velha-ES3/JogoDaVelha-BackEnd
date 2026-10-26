package com.jogodavelha.profile;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record ProfileResponse(
        String username,
        Integer coinsBalance,
        String avatar,
        String preferredCharacter
) {
}
