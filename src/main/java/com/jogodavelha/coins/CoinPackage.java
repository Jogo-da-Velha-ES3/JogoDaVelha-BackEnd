package com.jogodavelha.coins;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public CoinPackage(Integer coins) {
        this.coins = coins;
    }
}
