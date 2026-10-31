package com.jogodavelha.websocket;

/** Requests a room snapshot after a lifecycle transition has updated its state. */
public record RoomSnapshotRequestedEvent(String roomCode) {
}
