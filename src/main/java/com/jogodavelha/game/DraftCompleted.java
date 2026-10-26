package com.jogodavelha.game;

import java.util.UUID;

/** BE-037 integration event. Player identities are resolved from the authoritative Room. */
public record DraftCompleted(UUID roomId) {
    public DraftCompleted {
        if (roomId == null) throw new IllegalArgumentException("roomId é obrigatório.");
    }
}
