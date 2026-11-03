package com.jogodavelha.auth;

public record PasswordResetRequestedEvent(String email, String rawToken) {
    @Override
    public String toString() {
        return "PasswordResetRequestedEvent{}";
    }
}
