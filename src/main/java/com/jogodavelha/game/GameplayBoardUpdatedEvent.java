package com.jogodavelha.game;

import com.jogodavelha.game.logic.GameState;

import java.util.UUID;

public record GameplayBoardUpdatedEvent(UUID gameId, GameState state) { }
