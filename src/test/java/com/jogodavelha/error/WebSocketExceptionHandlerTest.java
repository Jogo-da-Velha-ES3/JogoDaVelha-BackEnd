package com.jogodavelha.error;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.game.logic.SkillValidationException;
import com.jogodavelha.game.logic.MoveValidationException;
import com.jogodavelha.room.RoomNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.messaging.handler.annotation.support.AnnotationExceptionHandlerMethodResolver;
import org.springframework.messaging.simp.annotation.SendToUser;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketExceptionHandlerTest {
    private final WebSocketExceptionHandler handler = new WebSocketExceptionHandler();
    private final AnnotationExceptionHandlerMethodResolver resolver =
            new AnnotationExceptionHandlerMethodResolver(WebSocketExceptionHandler.class);

    @ParameterizedTest
    @EnumSource(SkillValidationException.Code.class)
    void preservesSkillCodeInThePrivateErrorResponse(SkillValidationException.Code code) throws Exception {
        var exception = new SkillValidationException(code, "Pedido inválido");
        var method = resolver.resolveMethod(exception);
        assertNotNull(method);
        assertEquals("handleSkillValidation", method.getName());

        var sendToUser = method.getAnnotation(SendToUser.class);
        assertArrayEquals(new String[]{"/queue/errors"}, sendToUser.destinations());
        assertFalse(sendToUser.broadcast());

        var response = (ApiError) method.invoke(handler, exception, "/app/test");
        var json = new ObjectMapper().valueToTree(response);
        assertEquals(code.name(), json.get("code").asText());
        assertEquals("Pedido inválido", json.get("message").asText());
        assertEquals(400, json.get("status").asInt());
        assertEquals("/app/test", json.get("path").asText());
        assertTrue(json.hasNonNull("timestamp"));
        assertTrue(json.has("fields"));
        assertTrue(json.has("error"));

        assertTrue(json.get("fields").isNull());
        assertTrue(json.get("error").isNull());
    }

    @Test
    void mapsGenericBusinessErrorsToExistingCatalogCodes() throws Exception {
        assertEquals("ROOM_BAD_REQUEST", ((ApiError) resolver.resolveMethod(new IllegalArgumentException())
                .invoke(handler, new IllegalArgumentException("inválido"), "/app/test")).code());
        assertEquals("ROOM_CONFLICT", ((ApiError) resolver.resolveMethod(new IllegalStateException())
                .invoke(handler, new IllegalStateException("conflito"), "/app/test")).code());
        var unexpected = (ApiError) resolver.resolveMethod(new Exception())
                .invoke(handler, new Exception("detalhe interno"), "/app/test");
        assertEquals("INTERNAL_ERROR", unexpected.code());
        assertEquals("Erro interno do servidor", unexpected.message());
    }

    @Test
    void preservesSpecificTurnValidationCode() throws Exception {
        var exception = new MoveValidationException(MoveValidationException.Code.TURN_EXPIRED, "Prazo expirado");
        var method = resolver.resolveMethod(exception);
        assertNotNull(method);
        assertEquals("handleMoveValidation", method.getName());
        var response = (ApiError) method.invoke(handler, exception, "/app/game/action");
        assertEquals(MoveValidationException.Code.TURN_EXPIRED.name(), response.code());
        assertEquals(400, response.status());
        assertEquals("Prazo expirado", response.message());
    }

    @Test
    void mapsMissingRoomToTheRestCatalogCodeWithoutLeakingDetails() throws Exception {
        var exception = new RoomNotFoundException("Sala encerrada");
        var method = resolver.resolveMethod(exception);
        assertNotNull(method);
        assertEquals("handleRoomNotFound", method.getName());
        var response = (ApiError) method.invoke(handler, exception, "/app/rooms/0042/move");
        assertEquals("ROOM_NOT_FOUND", response.code());
        assertEquals(404, response.status());
        assertEquals("Sala não encontrada ou encerrada.", response.message());
    }
}
