package com.jogodavelha.shop;

import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ShopCategory {
    CHARACTERS, SKILLS, AVATARS;

    public static ShopCategory parse(String value) {
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ShopException("SHOP_INVALID_REQUEST", "Categoria inválida.", HttpStatus.BAD_REQUEST);
        }
    }
}
