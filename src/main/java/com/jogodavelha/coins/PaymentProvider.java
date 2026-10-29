package com.jogodavelha.coins;

import com.jogodavelha.auth.User;

public interface PaymentProvider {

    void purchase(
            User user,
            CoinPackage coinPackage
    );
}

