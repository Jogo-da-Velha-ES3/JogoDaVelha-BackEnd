package com.jogodavelha.game;

/** Result of an idempotent round transition. */
public record RoundUpdate(Game game, boolean applied) { }
