package com.jogodavelha.room;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByCodeAndStatusIn(String code, Collection<RoomStatus> statuses);

    Optional<Room> findByCodeAndStatusIn(String code, Collection<RoomStatus> statuses);

    boolean existsByStatusInAndPlayer1IdOrStatusInAndPlayer2Id(
            Collection<RoomStatus> player1Statuses, UUID player1Id,
            Collection<RoomStatus> player2Statuses, UUID player2Id);

    List<Room> findByStatusAndPlayer2IsNullAndCreatedAtBefore(RoomStatus status, Instant limit);
}
