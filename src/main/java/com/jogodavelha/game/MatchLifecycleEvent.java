package com.jogodavelha.game;

import java.util.Map;

/** Contract envelope for room topic events described by contrato-integracao.md. */
public record MatchLifecycleEvent(String type, Map<String, Object> payload, long serverTime) {
    public static MatchLifecycleEvent of(String type, Map<String, Object> payload) {
        return new MatchLifecycleEvent(type, payload, System.currentTimeMillis());
    }
}
