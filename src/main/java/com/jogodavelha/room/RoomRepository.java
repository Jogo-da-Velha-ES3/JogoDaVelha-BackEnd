package com.jogodavelha.room;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    boolean existsByCodeAndStatusIn(String code, Collection<RoomStatus> statuses);

    Optional<Room> findByCodeAndStatusIn(String code, Collection<RoomStatus> statuses);

    @Query("select r from Room r where r.code = :code and r.status in :statuses "
            + "and (r.player1.id = :playerId or r.player2.id = :playerId)")
    Optional<Room> findActiveMembership(@Param("code") String code,
            @Param("statuses") Collection<RoomStatus> statuses, @Param("playerId") UUID playerId);

    boolean existsByStatusInAndPlayer1IdOrStatusInAndPlayer2Id(
            Collection<RoomStatus> player1Statuses, UUID player1Id,
            Collection<RoomStatus> player2Statuses, UUID player2Id);
}
