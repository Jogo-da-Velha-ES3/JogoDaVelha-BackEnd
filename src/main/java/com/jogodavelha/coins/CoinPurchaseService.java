package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CoinPurchaseService {

    private final CoinPackageRepository coinPackageRepository;
    private final CoinOrderRepository coinOrderRepository;
    private final UserRepository userRepository;
    private final PaymentProvider paymentProvider;

    public List<CoinPackage> getPackages() {
        return coinPackageRepository.findAll();
    }

    @Transactional
    public void purchase(
            UUID userId,
            UUID packageId
    ) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Usuário não encontrado"
                        ));

        CoinPackage coinPackage =
                coinPackageRepository.findById(packageId)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Pacote não encontrado"
                                ));

        coinOrderRepository.save(
                new CoinOrder(
                        user,
                        coinPackage,
                        PaymentMode.SIMULATED
                )
        );

        paymentProvider.purchase(
                user,
                coinPackage
        );
    }
}
