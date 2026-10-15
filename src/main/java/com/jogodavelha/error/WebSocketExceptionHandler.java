package com.jogodavelha.error;

import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

@ControllerAdvice
public class WebSocketExceptionHandler {

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