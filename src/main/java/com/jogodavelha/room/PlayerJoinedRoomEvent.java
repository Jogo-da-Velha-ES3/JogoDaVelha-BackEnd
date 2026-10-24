package com.jogodavelha.room;

import java.util.UUID;

public record PlayerJoinedRoomEvent(
        String roomCode,
        UUID roomId,
        UUID guestId,
        String guestUsername,
        UUID hostId,
        String hostUsername
) {}
