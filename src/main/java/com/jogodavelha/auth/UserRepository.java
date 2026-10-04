package com.jogodavelha.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository responsável pelo acesso aos dados de usuários.
 * Fornece operações de CRUD e consultas personalizadas para a entidade User.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Verifica se existe um usuário com o username fornecido (case-insensitive).
     *
     * @param username o username a verificar
     * @return true se o username já existe, false caso contrário
     */
    boolean existsByUsernameIgnoreCase(String username);

    /**
     * Verifica se existe um usuário com o e-mail fornecido.
     *
     * @param email o e-mail a verificar
     * @return true se o e-mail já existe, false caso contrário
     */
    boolean existsByEmail(String email);

    /**
     * Busca um usuário pelo e-mail.
     *
     * @param email o e-mail do usuário
     * @return Optional contendo o usuário encontrado
     */
    Optional<User> findByEmail(String email);
}
