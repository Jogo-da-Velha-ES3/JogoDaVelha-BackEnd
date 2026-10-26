package com.jogodavelha.auth;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RegisterResponse(UUID id, String username, String email) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(user.getId(), user.getUsername(), user.getEmail());
    }
}
