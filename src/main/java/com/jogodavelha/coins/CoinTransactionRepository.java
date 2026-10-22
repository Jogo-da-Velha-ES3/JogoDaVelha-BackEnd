package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CoinTransactionRepository
        extends JpaRepository<CoinTransaction, UUID> {

    List<CoinTransaction> findByUserOrderByCreatedAtDesc(User user);

    long countByUser(User user);
}
