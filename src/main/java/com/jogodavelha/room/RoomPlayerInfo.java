package com.jogodavelha.room;

import java.util.UUID;

public record RoomPlayerInfo(
        UUID id,
        String username,
        String avatar
) {
}
