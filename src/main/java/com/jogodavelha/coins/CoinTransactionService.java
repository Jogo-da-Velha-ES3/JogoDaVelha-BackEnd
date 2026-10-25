package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CoinTransactionService {

    private final CoinTransactionRepository repository;

    public void registerCredit(
            User user,
            int amount,
            String source
    ) {
        repository.save(
                new CoinTransaction(
                        user,
                        CoinTransactionType.CREDIT,
                        amount,
                        user.getCoinsBalance(),
                        source
                )
        );
    }
}