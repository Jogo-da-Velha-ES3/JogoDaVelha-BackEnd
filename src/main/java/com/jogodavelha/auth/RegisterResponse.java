package com.jogodavelha.auth;

import java.util.UUID;

public record RegisterResponse(UUID id, String username, String email) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
