package com.jogodavelha.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
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
