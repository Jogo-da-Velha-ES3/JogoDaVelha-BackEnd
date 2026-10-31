package com.jogodavelha.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;

/**
 * Repository responsável pelo acesso aos dados de partidas.
 * Fornece operações de CRUD e consultas personalizadas para a entidade Game.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

    Optional<Game> findByRoomIdAndStatus(UUID roomId, GameStatus status);

    @Query("SELECT g.status AS status, g.room.code AS roomCode FROM Game g WHERE g.id = :id")
    Optional<SnapshotRoute> findSnapshotRouteById(@Param("id") UUID id);

    interface SnapshotRoute {
        GameStatus getStatus();
        String getRoomCode();
    }

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT g FROM Game g WHERE g.id = :id")
    Optional<Game> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT g FROM Game g WHERE g.status = 'IN_PROGRESS' AND (g.player1.id = :userId OR g.player2.id = :userId)")
    Optional<Game> findActiveGameByPlayerId(@Param("userId") UUID userId);

    @Query("""
    SELECT g
    FROM Game g
    WHERE g.status = 'FINISHED'
      AND (g.player1.id = :userId OR g.player2.id = :userId)
    ORDER BY g.finishedAt DESC
""")
    List<Game> findFinishedGamesByPlayerId(
            @Param("userId") UUID userId
    );
}
