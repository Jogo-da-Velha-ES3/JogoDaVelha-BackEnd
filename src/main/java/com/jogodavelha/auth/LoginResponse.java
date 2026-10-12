package com.jogodavelha.auth;

import java.util.UUID;

public record LoginResponse(String token, UUID id, String username, Integer coinsBalance) {
    public static LoginResponse from(LoginResult loginResult) {
        // User user = loginResult.user();
        return new LoginResponse(
                loginResult.accessToken(),
                loginResult.user().getId(),
                loginResult.user().getUsername(),
                loginResult.user().getCoinsBalance()
        );
    }
}
