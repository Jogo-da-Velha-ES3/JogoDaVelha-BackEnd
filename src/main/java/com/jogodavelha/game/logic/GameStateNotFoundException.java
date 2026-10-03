package com.jogodavelha.game.logic;

import java.util.UUID;

/**
 * Exceção lançada quando o estado de uma partida não é encontrado no Redis.
 */
public class GameStateNotFoundException extends RuntimeException {

    public GameStateNotFoundException(UUID gameId) {
        super("Estado da partida não encontrado: " + gameId);
    }

    public GameStateNotFoundException(String message) {
        super(message);
    }
}
