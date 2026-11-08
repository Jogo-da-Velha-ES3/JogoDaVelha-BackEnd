package com.jogodavelha.game.draft;

import com.jogodavelha.auth.User;
import com.jogodavelha.room.PlayerJoinedRoomEvent;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomPlayerInfo;
import com.jogodavelha.room.RoomRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoomDraftInitializerTest {
    private final DraftService drafts = mock(DraftService.class);
    private final RoomRepository rooms = mock(RoomRepository.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final Clock clock = mock(Clock.class);
    private final RoomDraftInitializer initializer = new RoomDraftInitializer(drafts, rooms, scheduler, clock);
    private final UUID roomId = UUID.randomUUID();
    private final User host = user("host");
    private final User guest = user("guest");
    private final PlayerJoinedRoomEvent event = new PlayerJoinedRoomEvent("0042", roomId,
            new RoomPlayerInfo(guest.getId(), "guest", null), new RoomPlayerInfo(host.getId(), "host", null));

    @Test
    void startsTheDraftOnlyAfterTheMatchFoundPause() {
        Room room = new Room("0042", host);
        room.join(guest);
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));

        Runnable start = scheduledStart();
        verify(drafts, never()).start(any(), any(), any(), any());
        start.run();

        verify(drafts).start(roomId, "0042", host.getId(), guest.getId());
    }

    @Test
    void roomClosedDuringTheMatchFoundPauseNeverOpensTheDraft() {
        Room room = new Room("0042", host);
        room.join(guest);
        room.close();
        when(rooms.findById(roomId)).thenReturn(Optional.of(room));

        scheduledStart().run();

        verify(drafts, never()).start(any(), any(), any(), any());
    }

    private Runnable scheduledStart() {
        when(clock.millis()).thenReturn(10_000L);
        initializer.onPlayerJoined(event);
        ArgumentCaptor<Runnable> start = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).schedule(start.capture(), eq(Instant.ofEpochMilli(13_000)));
        return start.getValue();
    }

    private static User user(String name) {
        User user = new User(name, "hash", name + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }
}
