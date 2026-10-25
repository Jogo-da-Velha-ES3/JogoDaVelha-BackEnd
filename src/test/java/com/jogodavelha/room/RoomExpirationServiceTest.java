package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RoomExpirationServiceTest {
    private static final Instant NOW = Instant.parse("2026-10-24T12:00:00Z");

    private final RoomRepository rooms = mock(RoomRepository.class);
    private final RoomCodeAllocator allocator = mock(RoomCodeAllocator.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final RoomExpirationService service = new RoomExpirationService(rooms, allocator, events, clock);
    private final User owner = player("owner");

    RoomExpirationServiceTest() {
        // ttlMinutes vem de @Value; no teste unitário é injetado manualmente
        ReflectionTestUtils.setField(service, "ttlMinutes", 10L);
    }

    @Test
    void closesWaitingRoomsOlderThanTtlAndPublishesExpired() {
        Room old = new Room("0042", owner);
        when(rooms.findByStatusAndPlayer2IsNullAndCreatedAtBefore(eq(RoomStatus.WAITING), any()))
                .thenReturn(List.of(old));

        service.expireAbandonedRooms();

        assertEquals(RoomStatus.CLOSED, old.getStatus());
        verify(rooms).save(old);
        var captor = ArgumentCaptor.forClass(RoomClosedEvent.class);
        verify(events).publishEvent(captor.capture());
        assertEquals("0042", captor.getValue().roomCode());
        assertEquals(RoomClosedEvent.EXPIRED, captor.getValue().reason());
        assertNull(captor.getValue().closedBy());
    }

    @Test
    void doesNothingWhenNoRoomIsExpired() {
        when(rooms.findByStatusAndPlayer2IsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        service.expireAbandonedRooms();

        verify(rooms, never()).save(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void usesLimitDerivedFromClockAndTtl() {
        when(rooms.findByStatusAndPlayer2IsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        service.expireAbandonedRooms();

        verify(rooms).findByStatusAndPlayer2IsNullAndCreatedAtBefore(
                RoomStatus.WAITING, NOW.minus(Duration.ofMinutes(10)));
    }

    @Test
    void takesMembershipLockBeforeQuerying() {
        when(rooms.findByStatusAndPlayer2IsNullAndCreatedAtBefore(any(), any())).thenReturn(List.of());

        service.expireAbandonedRooms();

        var order = inOrder(allocator, rooms);
        order.verify(allocator).lockMembership();
        order.verify(rooms).findByStatusAndPlayer2IsNullAndCreatedAtBefore(any(), any());
    }

    private static User player(String name) {
        User user = new User(name, "hash", name + "@example.test");
        user.setId(UUID.randomUUID());
        return user;
    }
}