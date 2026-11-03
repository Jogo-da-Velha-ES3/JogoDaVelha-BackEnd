package com.jogodavelha.room;

import java.util.UUID;

/** Publicado quando uma sala é encerrada. O RoomEventPublisher envia ROOM_CLOSED. */
public record RoomClosedEvent(String roomCode, UUID roomId, UUID closedBy, String reason) {

    public static final String HOST_LEFT = "HOST_LEFT";
    public static final String EXPIRED = "EXPIRED";
    public static final String REMATCH_DECLINED = "REMATCH_DECLINED";
    public static final String REMATCH_EXPIRED = "REMATCH_EXPIRED";
    public static final String RETURN_TO_LOBBY = "RETURN_TO_LOBBY";
    public static final String REMATCH_DISCONNECTED = "REMATCH_DISCONNECTED";
}