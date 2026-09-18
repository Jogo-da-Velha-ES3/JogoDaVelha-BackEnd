package com.jogodavelha.game;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository responsável pelo acesso aos dados de partidas.
 * Fornece operações de CRUD e consultas personalizadas para a entidade Game.
 */
@Repository
public interface GameRepository extends JpaRepository<Game, String> {
    
    // Métodos de consulta personalizados serão adicionados conforme necessário
    // Exemplo: findByPlayer1Id, findByStatus, etc.
}