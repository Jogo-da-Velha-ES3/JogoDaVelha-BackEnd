package com.jogodavelha.character;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "characters")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Character {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "symbol", nullable = false, length = 20)
    private CharacterSymbol symbol;

    @Column(name = "price_coins", nullable = false)
    private Integer priceCoins;

    public Character(String name, CharacterSymbol symbol) {
        this.name = name;
        this.symbol = symbol;
    }
}
