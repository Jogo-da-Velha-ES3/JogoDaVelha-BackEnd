package com.jogodavelha.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository responsável pelo acesso aos dados de partidas.
 * Fornece operações de CRUD e consultas personalizadas para a entidade Game.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, UUID> {

    // Métodos de consulta personalizados serão adicionados conforme necessário
    // Exemplo: findByPlayer1Id, findByStatus, etc.
}