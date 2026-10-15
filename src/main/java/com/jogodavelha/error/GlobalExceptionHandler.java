package com.jogodavelha.error;

import com.jogodavelha.auth.EmailAlreadyExistsException;
import com.jogodavelha.auth.InvalidCredentialsException;
import com.jogodavelha.auth.UsernameAlreadyExistsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice(basePackages = "com.jogodavelha")
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(UsernameAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleUsernameExists(UsernameAlreadyExistsException ex, HttpServletRequest request) {
        return build("USERNAME_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleEmailExists(EmailAlreadyExistsException ex, HttpServletRequest request) {
        return build("EMAIL_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ApiError> handleInvalidCredentials(InvalidCredentialsException ex, HttpServletRequest request) {
        return build("INVALID_CREDENTIALS", ex.getMessage(), HttpStatus.UNAUTHORIZED, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiError> handleRoomBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        // TODO(BE-009.1): replace with typed room exceptions and codes such as ROOM_NOT_FOUND.
        return build("ROOM_BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiError> handleRoomConflict(IllegalStateException ex, HttpServletRequest request) {
        // TODO(BE-009.1): replace with typed room exceptions and codes ROOM_FULL/ROOM_ALREADY_STARTED.
        return build("ROOM_CONFLICT", ex.getMessage(), HttpStatus.CONFLICT, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return build("VALIDATION_ERROR", "Dados inválidos", HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}", request.getRequestURI(), ex);
        return build("INTERNAL_ERROR", "Erro interno do servidor", HttpStatus.INTERNAL_SERVER_ERROR, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ApiError response = ApiError.of("VALIDATION_ERROR", "Dados inválidos", status.value(), path(request), fields);
        return ResponseEntity.status(status).headers(headers).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ApiError response = ApiError.of("INVALID_REQUEST_BODY", "Corpo da requisição inválido",
                status.value(), path(request));
        return ResponseEntity.status(status).headers(headers).body(response);
    }

    @Override
    protected ResponseEntity<Object> handleNoHandlerFoundException(
            NoHandlerFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return springError("NOT_FOUND", "Recurso não encontrado", ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return springError("NOT_FOUND", "Recurso não encontrado", ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return springError("METHOD_NOT_ALLOWED", "Método HTTP não permitido", ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleTypeMismatch(
            org.springframework.beans.TypeMismatchException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return springError("BAD_REQUEST", "Parâmetro inválido", ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            org.springframework.web.bind.MissingServletRequestParameterException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return springError("BAD_REQUEST", "Parâmetro obrigatório ausente", ex, headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        String code = status.value() == 404 ? "NOT_FOUND"
                : status.value() == 405 ? "METHOD_NOT_ALLOWED" : "BAD_REQUEST";
        String message = status.value() == 404 ? "Recurso não encontrado"
                : status.value() == 405 ? "Método HTTP não permitido" : "Requisição inválida";
        return springError(code, message, ex, headers, status, request);
    }

    private ResponseEntity<Object> springError(String code, String message, Exception ex,
                                                HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.is5xxServerError()) {
            log.error("Erro HTTP inesperado em {}", path(request), ex);
            code = "INTERNAL_ERROR";
            message = "Erro interno do servidor";
        }
        return ResponseEntity.status(status).headers(headers)
                .body(ApiError.of(code, message, status.value(), path(request)));
    }

    private ResponseEntity<ApiError> build(String code, String message, HttpStatus status, HttpServletRequest request) {
        return ResponseEntity.status(status)
                .body(ApiError.of(code, message, status.value(), request.getRequestURI()));
    }

    private static String path(WebRequest request) {
        return ((ServletWebRequest) request).getRequest().getRequestURI();
    }
}
