package com.jogodavelha.game.logic;

public class MoveValidationException extends RuntimeException {
    public enum Code {
        INVALID_ARGUMENTS,
        INVALID_POSITION,
        INVALID_BOARD,
        INVALID_PHASE,
        DRAFT_IN_PROGRESS,
        ROUND_FINISHED,
        GAME_FINISHED,
        NOT_YOUR_TURN,
        TURN_EXPIRED,
        POSITION_OCCUPIED,
        POSITION_BLOCKED
    }

    private final Code code;

    public MoveValidationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
