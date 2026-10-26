package com.jogodavelha.game;

import java.util.UUID;

/** Adapter contract for BE-013.1's round skill draw and eight-second choice window. */
public interface RoundSkillDraft {
    /** Calls onComplete after its existing eight-second choice window has resolved. */
    void beginRound(UUID gameId, int roundNumber, UUID player1Id, UUID player2Id, Runnable onComplete);
}
