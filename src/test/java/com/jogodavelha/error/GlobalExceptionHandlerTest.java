package com.jogodavelha.error;

import com.jogodavelha.auth.EmailAlreadyExistsException;
import com.jogodavelha.auth.InvalidCredentialsException;
import com.jogodavelha.auth.UsernameAlreadyExistsException;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.HttpInputMessage;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsUsernameEmailCredentialsAndRoomExceptions() {
        assertApi(handler.handleUsernameExists(new UsernameAlreadyExistsException("duplicado"), servletRequest()),
                HttpStatus.CONFLICT, "USERNAME_ALREADY_EXISTS");
        assertApi(handler.handleEmailExists(new EmailAlreadyExistsException("duplicado"), servletRequest()),
                HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS");
        assertApi(handler.handleInvalidCredentials(new InvalidCredentialsException("inválidas"), servletRequest()),
                HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS");
        assertApi(handler.handleRoomBadRequest(new IllegalArgumentException("ruim"), servletRequest()),
                HttpStatus.BAD_REQUEST, "ROOM_BAD_REQUEST");
        assertApi(handler.handleRoomConflict(new IllegalStateException("conflito"), servletRequest()),
                HttpStatus.CONFLICT, "ROOM_CONFLICT");
        assertApi(handler.handleConstraintViolation(new ConstraintViolationException("inválido", null), servletRequest()),
                HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    }

    @Test
    void mapsMvcValidationAndMalformedBody() {
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(new Object(), "request");
        binding.addError(new FieldError("request", "email", "E-mail inválido"));
        MethodArgumentNotValidException validation = new MethodArgumentNotValidException(null, binding);
        var validationResponse = handler.handleMethodArgumentNotValid(validation, new HttpHeaders(),
                HttpStatus.BAD_REQUEST, request());
        ApiError validationBody = (ApiError) validationResponse.getBody();
        assertEquals("VALIDATION_ERROR", validationBody.code());
        assertEquals(Map.of("email", "E-mail inválido"), validationBody.fields());

        assertSpring(HttpStatus.BAD_REQUEST, "INVALID_REQUEST_BODY",
                new org.springframework.http.converter.HttpMessageNotReadableException("bad", new MockHttpInputMessage()));
    }

    @Test
    void mapsNotFoundAndMethodNotAllowed() {
        assertSpring(HttpStatus.NOT_FOUND, "NOT_FOUND",
                new NoHandlerFoundException("GET", "/missing", new HttpHeaders()));
        assertSpring(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED",
                new HttpRequestMethodNotSupportedException("PATCH"));
    }

    private void assertSpring(HttpStatus status, String code, Exception exception) {
        ResponseEntity<Object> response = handler.handleExceptionInternal(exception, null, new HttpHeaders(), status, request());
        assertEquals(status.value(), response.getStatusCode().value());
        assertEquals(code, ((ApiError) response.getBody()).code());
    }

    private static void assertApi(org.springframework.http.ResponseEntity<ApiError> response, HttpStatus status, String code) {
        assertEquals(status.value(), response.getStatusCode().value());
        assertEquals(code, response.getBody().code());
        assertEquals(status.value(), response.getBody().status());
    }

    private static WebRequest request() {
        return new ServletWebRequest(servletRequest());
    }

    private static MockHttpServletRequest servletRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/test");
        return request;
    }

    private static final class MockHttpInputMessage implements HttpInputMessage {
        @Override
        public org.springframework.http.HttpHeaders getHeaders() {
            return new HttpHeaders();
        }

        @Override
        public java.io.InputStream getBody() {
            return new java.io.ByteArrayInputStream(new byte[0]);
        }
    }
}
