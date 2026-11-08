package com.jogodavelha.error;

import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.room.RoomNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
@Slf4j
public class WebSocketExceptionHandler {

    @MessageExceptionHandler(SkillValidationException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleSkillValidation(SkillValidationException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(ex.getCode().name(), ex.getMessage(), HttpStatus.BAD_REQUEST.value(), destination, null, null);
    }

    @MessageExceptionHandler(MoveValidationException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleMoveValidation(MoveValidationException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of(ex.getCode().name(), ex.getMessage(), HttpStatus.BAD_REQUEST.value(), destination, null, null);
    }

    @MessageExceptionHandler({IllegalArgumentException.class})
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleBadRequest(IllegalArgumentException ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of("ROOM_BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST.value(), destination, null, null);
    }

    @MessageExceptionHandler(AccessDeniedException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleAccessDenied(AccessDeniedException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of("FORBIDDEN", "Jogador não autorizado para esta ação.",
                HttpStatus.FORBIDDEN.value(), destination, null, null);
    }

    @MessageExceptionHandler(RoomNotFoundException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleRoomNotFound(RoomNotFoundException ex,
            @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of("ROOM_NOT_FOUND", "Sala não encontrada ou encerrada.",
                HttpStatus.NOT_FOUND.value(), destination, null, null);
    }

    @MessageExceptionHandler(IllegalStateException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleConflict(IllegalStateException ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        return ApiError.of("ROOM_CONFLICT", "A ação não pode ser realizada no estado atual.",
                HttpStatus.CONFLICT.value(), destination, null, null);
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public ApiError handleUnexpected(Exception ex, @Header(SimpMessageHeaderAccessor.DESTINATION_HEADER) String destination) {
        log.error("Erro inesperado em ação STOMP {}", destination, ex);
        return ApiError.of("INTERNAL_ERROR", "Erro interno do servidor",
                HttpStatus.INTERNAL_SERVER_ERROR.value(), destination, null, null);
    }
}
