package com.jogodavelha.coins;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/coins")
@RequiredArgsConstructor
@Tag(name = "Coin", description = "Gerenciamento de saldo e compra de pacotes de moedas")
public class CoinController {

    private final CoinService coinService;
    private final CoinPurchaseService coinPurchaseService;

    @GetMapping("/balance")
    public ResponseEntity<BalanceResponse> getBalance(
            Authentication authentication
    ) {

        UUID userId = (UUID) authentication.getPrincipal();

        return ResponseEntity.ok(
                new BalanceResponse(
                        coinService.getBalance(userId)
                )
        );
    }

    @GetMapping("/packages")
    public ResponseEntity<List<CoinPackageResponse>> packages() {

        List<CoinPackageResponse> response =
                coinPurchaseService.getPackages()
                        .stream()
                        .map(pkg ->
                                new CoinPackageResponse(
                                        pkg.getId(),
                                        pkg.getCoins(),
                                        pkg.getPriceInReais()
                                )
                        )
                        .toList();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/packages/{packageId}/purchase")
    public ResponseEntity<Void> purchase(
            Authentication authentication,
            @PathVariable UUID packageId
    ) {

        UUID userId = (UUID) authentication.getPrincipal();

        coinPurchaseService.purchase(
                userId,
                packageId
        );

        return ResponseEntity.noContent().build();
    }
}
