package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CoinPurchaseServiceTest {
    private final CoinPackageRepository packages = mock(CoinPackageRepository.class);
    private final CoinOrderRepository orders = mock(CoinOrderRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PaymentProvider payments = mock(PaymentProvider.class);
    private final CoinPurchaseService service = new CoinPurchaseService(packages, orders, users, payments);
    private final UUID userId = UUID.randomUUID();
    private final UUID packageId = UUID.randomUUID();
    private User user;
    private CoinPackage coinPackage;

    @BeforeEach
    void setUp() {
        user = new User("player", "hash", "player@test.com");
        user.setId(userId);
        user.setCoinsBalance(100);
        coinPackage = new CoinPackage(1000, new BigDecimal("34.99"));
        when(users.findById(userId)).thenReturn(Optional.of(user));
        when(packages.findById(packageId)).thenReturn(Optional.of(coinPackage));
    }

    @Test
    void purchaseReturnsTheCreditedBalanceAndPersistsOneOrder() {
        doAnswer(invocation -> {
            user.addCoins(coinPackage.getCoins());
            return null;
        }).when(payments).purchase(user, coinPackage);

        assertEquals(1100, service.purchase(userId, packageId));

        verify(orders, times(1)).save(any(CoinOrder.class));
        verify(payments, times(1)).purchase(user, coinPackage);
    }

    @Test
    void unknownPackageDoesNotCreateOrderOrCreditCoins() {
        when(packages.findById(packageId)).thenReturn(Optional.empty());

        assertEquals("Pacote não encontrado", assertThrows(IllegalArgumentException.class,
                () -> service.purchase(userId, packageId)).getMessage());

        verify(orders, never()).save(any());
        verifyNoInteractions(payments);
        assertEquals(100, user.getCoinsBalance());
    }

    @Test
    void unknownUserDoesNotCreateOrderOrCreditCoins() {
        when(users.findById(userId)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.purchase(userId, packageId));

        verifyNoInteractions(orders, payments);
    }
}
