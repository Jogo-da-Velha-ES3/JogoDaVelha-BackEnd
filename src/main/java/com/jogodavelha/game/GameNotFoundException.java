package com.jogodavelha.game;

import java.util.UUID;

/**
 * Exceção lançada quando uma partida não é encontrada.
 */
public class GameNotFoundException extends RuntimeException {

    public GameNotFoundException(UUID gameId) {
        super("Partida não encontrada: " + gameId);
    }

    public GameNotFoundException(String message) {
        super(message);
    }
}
