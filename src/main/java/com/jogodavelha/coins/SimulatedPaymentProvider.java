package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SimulatedPaymentProvider
        implements PaymentProvider {

    private final UserRepository userRepository;
    private final CoinTransactionService coinTransactionService;

    @Value("${payments.simulated.enabled:true}")
    private boolean enabled;

    @Override
    @Transactional
    public void purchase(
            User user,
            CoinPackage coinPackage
    ) {

        if (!enabled) {
            throw new IllegalStateException(
                    "Compras simuladas estão desabilitadas"
            );
        }

        user.addCoins(
                coinPackage.getCoins()
        );

        userRepository.save(user);

        coinTransactionService.registerCredit(
                user,
                coinPackage.getCoins(),
                "COIN_PURCHASE"
        );
    }
}
