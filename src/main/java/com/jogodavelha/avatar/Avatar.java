package com.jogodavelha.avatar;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity
@Table(name = "avatars")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Avatar {
    public static final String DEFAULT_CODE = "DEFAULT";

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String code;
    @Column(nullable = false, length = 80)
    private String name;
    @Column(nullable = false, length = 500)
    private String description;
    @Column(name = "price_coins", nullable = false)
    private int priceCoins;

    public Avatar(String code, String name, String description, int priceCoins) {
        if (priceCoins < 0) throw new IllegalArgumentException("Preço não pode ser negativo.");
        this.code = code;
        this.name = name;
        this.description = description;
        this.priceCoins = priceCoins;
    }
}
