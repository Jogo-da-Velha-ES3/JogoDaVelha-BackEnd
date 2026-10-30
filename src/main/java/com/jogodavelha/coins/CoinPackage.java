package com.jogodavelha.coins;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

import java.util.UUID;

@Entity
@Table(name = "coin_packages")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CoinPackage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Integer coins;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal priceInReais;

    public CoinPackage(
            Integer coins,
            BigDecimal priceInReais
    ) {
        this.coins = coins;
        this.priceInReais = priceInReais;
    }
}
