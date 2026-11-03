package com.jogodavelha.room;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;

@Service
@RequiredArgsConstructor
public class RoomExpirationService {
    private final RoomRepository rooms;
    private final RoomCodeAllocator allocator;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${room.waiting.ttl-minutes:10}")
    private long ttlMinutes;

    @Scheduled(fixedDelayString = "${room.waiting.sweep-ms:60000}")
    @Transactional
    public void expireAbandonedRooms() {
        allocator.lockMembership();
        var limit = clock.instant().minus(Duration.ofMinutes(ttlMinutes));
        for (Room room : rooms.findByStatusAndPlayer2IsNullAndCreatedAtBefore(RoomStatus.WAITING, limit)) {
            room.close();
            rooms.save(room);
            events.publishEvent(
                    new RoomClosedEvent(room.getCode(), room.getId(), null, RoomClosedEvent.EXPIRED)
            );
        }
    }
}