package com.jogodavelha.game.service;

import java.util.UUID;

/**
 * Fim de uma pausa do ciclo da partida. Publicado pelo timer e tratado em uma transação, porque
 * as etapas seguintes leem a sala e os jogadores da partida.
 */
public record LifecyclePauseElapsed(UUID gameId, int round, Step step) {
    public enum Step {
        /** Tela de Confronto (VS) antes do round 1. */
        VERSUS,
        /** Anúncio do fim do round antes do próximo round ou do resultado. */
        ROUND_END,
        /** Apresentação do round antes do primeiro TURN_START. */
        PRESENTATION
    }
}
