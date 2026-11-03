package com.jogodavelha.coins;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CoinPackageRepository
        extends JpaRepository<CoinPackage, UUID> {
}
