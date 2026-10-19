package com.jogodavelha.game.logic;

public class SkillValidationException extends RuntimeException {
    public enum Code {
        SKILL_INVALID_TARGET,
        SKILL_INVALID_DESTINATION,
        SKILL_SILENCED
    }

    private final Code code;

    public SkillValidationException(Code code, String message) {
        super(message);
        this.code = code;
    }

    public Code getCode() {
        return code;
    }
}
