package com.jogodavelha.error;

import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.game.logic.MoveValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(SkillValidationException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleSkillValidation(SkillValidationException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(ex.getCode().name(), ex.getMessage(), HttpStatus.BAD_REQUEST.value(), destination);
    }

    @MessageExceptionHandler(MoveValidationException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleMoveValidation(MoveValidationException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), destination);
    }

    @MessageExceptionHandler({IllegalArgumentException.class})
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleBadRequest(IllegalArgumentException ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(HttpStatus.BAD_REQUEST, ex.getMessage(), destination);
    }

    @MessageExceptionHandler(IllegalStateException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleConflict(IllegalStateException ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(HttpStatus.CONFLICT, ex.getMessage(), destination);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleUnexpected(Exception ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno do servidor", destination);
    }
}
