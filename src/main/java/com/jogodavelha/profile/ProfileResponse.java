package com.jogodavelha.profile;

public record ProfileResponse(
        String username,
        Integer coinsBalance,
        String avatar,
        String preferredCharacter
) {
}
