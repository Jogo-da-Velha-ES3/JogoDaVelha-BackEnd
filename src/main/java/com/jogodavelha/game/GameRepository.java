package com.jogodavelha.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository responsável pelo acesso aos dados de partidas.
 * Fornece operações de CRUD e consultas personalizadas para a entidade Game.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

    @Query("SELECT g FROM Game g WHERE g.status = 'IN_PROGRESS' AND (g.player1.id = :userId OR g.player2.id = :userId)")
    Optional<Game> findActiveGameByPlayerId(@Param("userId") UUID userId);
}
