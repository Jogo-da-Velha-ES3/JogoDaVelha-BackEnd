package com.jogodavelha.coins;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoinControllerTest {

    @Test
    void returnsUserBalance() {

        CoinService coinService = mock(CoinService.class);
        CoinController controller = new CoinController(coinService);

        UUID userId = UUID.randomUUID();

        Authentication authentication = mock(Authentication.class);

        when(authentication.getPrincipal())
                .thenReturn(userId);

        when(coinService.getBalance(userId))
                .thenReturn(150);

        ResponseEntity<BalanceResponse> response =
                controller.getBalance(authentication);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(150, response.getBody().coinsBalance());

        verify(coinService).getBalance(userId);
    }
}
