package com.jogodavelha.game.logic;

import java.util.UUID;

/**
 * Exceção lançada quando há conflito de concorrência ao tentar atualizar o estado de uma partida.
 * Ocorre quando a versão do estado foi modificada por outra operação antes da conclusão da atualização.
 */
public class ConcurrentStateModificationException extends RuntimeException {

    public ConcurrentStateModificationException(UUID gameId, int maxRetries) {
        super("Conflito de concorrência ao atualizar estado da partida " + gameId + 
              ". Máximo de tentativas (" + maxRetries + ") excedido.");
    }

    public ConcurrentStateModificationException(String message) {
        super(message);
    }
}
