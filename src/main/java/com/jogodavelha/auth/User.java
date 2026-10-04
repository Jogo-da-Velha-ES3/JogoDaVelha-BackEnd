package com.jogodavelha.auth;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Entidade que representa um usuário no sistema.
 * Armazena informações de autenticação e perfil do usuário.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30, unique = true)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 255, unique = true)
    private String email;

    @Column(name = "coins_balance", nullable = false)
    private Integer coinsBalance = 0;

    public User(String username, String passwordHash, String email) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.email = email;
    }

    /**
     * Adiciona moedas ao saldo do usuário.
     *
     * @param amount Quantidade de moedas a adicionar (deve ser positivo)
     * @throws IllegalArgumentException se amount for negativo
     */
    public void addCoins(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Valor de moedas não pode ser negativo");
        }
        this.coinsBalance += amount;
    }
}
