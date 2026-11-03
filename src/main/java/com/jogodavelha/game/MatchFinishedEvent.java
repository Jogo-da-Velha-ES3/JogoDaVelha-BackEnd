package com.jogodavelha.game;

import java.util.UUID;

/** Signals that the final match snapshot should be published before transient state is removed. */
public record MatchFinishedEvent(UUID gameId, String roomCode) {
}
