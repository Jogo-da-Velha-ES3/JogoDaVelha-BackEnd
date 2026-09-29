package com.jogodavelha.room;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByCodeAndStatusIn(String code, Collection<RoomStatus> statuses);

    boolean existsByStatusInAndPlayer1IdOrStatusInAndPlayer2Id(
            Collection<RoomStatus> player1Statuses, UUID player1Id,
            Collection<RoomStatus> player2Statuses, UUID player2Id);
}
