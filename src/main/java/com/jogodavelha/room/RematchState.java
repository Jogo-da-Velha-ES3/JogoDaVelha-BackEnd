package com.jogodavelha.room;

import java.util.UUID;

public record RematchState(UUID roomId, String roomCode, UUID gameId, UUID requesterId,
                           long deadlineEpochMillis, long version, Status status) {
    public enum Status { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }

    public RematchState withStatus(Status updatedStatus) {
        return new RematchState(roomId, roomCode, gameId, requesterId, deadlineEpochMillis,
                version + 1, updatedStatus);
    }
}
