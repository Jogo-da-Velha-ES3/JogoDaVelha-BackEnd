package com.jogodavelha.game.draft;

import com.jogodavelha.room.PlayerJoinedRoomEvent;
import com.jogodavelha.room.RoomPlayerInfo;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RoomDraftInitializerTest {
    @Test
    void startsExistingDraftFlowAfterSecondPlayerJoins() {
        DraftService drafts = mock(DraftService.class);
        RoomDraftInitializer initializer = new RoomDraftInitializer(drafts);
        UUID roomId = UUID.randomUUID();
        UUID guestId = UUID.randomUUID();
        UUID hostId = UUID.randomUUID();
        PlayerJoinedRoomEvent event = new PlayerJoinedRoomEvent("0042", roomId,
                new RoomPlayerInfo(guestId, "guest", null), new RoomPlayerInfo(hostId, "host", null));

        initializer.onPlayerJoined(event);

        verify(drafts).start(roomId, "0042", hostId, guestId);
    }
}
