package com.jogodavelha.room;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RoomDTO(
        UUID id,
        String code,
        RoomStatus status,
        UUID player1Id,
        String player1Username,
        UUID player2Id,
        String player2Username
) {
    public static RoomDTO from(Room room) {
        return new RoomDTO(
                room.getId(),
                room.getCode(),
                room.getStatus(),
                room.getPlayer1().getId(),
                room.getPlayer1().getUsername(),
                room.getPlayer2() != null ? room.getPlayer2().getId() : null,
                room.getPlayer2() != null ? room.getPlayer2().getUsername() : null
        );
    }
}
