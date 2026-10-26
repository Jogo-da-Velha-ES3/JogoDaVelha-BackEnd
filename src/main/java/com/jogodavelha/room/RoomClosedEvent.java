package com.jogodavelha.room;

import java.util.UUID;

/** Publicado quando uma sala é fechada (saída do host ou expiração). O RoomEventPublisher envia ROOM_CLOSED. */
public record RoomClosedEvent(String roomCode, UUID roomId, UUID closedBy, String reason) {

    public static final String HOST_LEFT = "HOST_LEFT";
    public static final String EXPIRED = "EXPIRED";
}