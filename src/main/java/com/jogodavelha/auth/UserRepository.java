package com.jogodavelha.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository responsável pelo acesso aos dados de usuários.
 * Fornece operações de CRUD e consultas personalizadas para a entidade User.
 */
@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    // Métodos de consulta personalizados serão adicionados conforme necessário
    // Exemplo: findByUsername, findByEmail, etc.
}