package com.jogodavelha.shop;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ShopException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public ShopException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }
}
