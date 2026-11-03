package com.jogodavelha.room;

import java.util.UUID;

public record PlayerJoinedRoomEvent(
        String roomCode,
        UUID roomId,
        RoomPlayerInfo guest,
        RoomPlayerInfo host
) {}
