package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoinServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CoinService coinService;

    @Test
    void returnsUserBalance() {

        UUID userId = UUID.randomUUID();

        User user =
                new User("rafael", "hash", "rafael@test.com");

        user.setId(userId);
        user.setCoinsBalance(150);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        Integer balance = coinService.getBalance(userId);

        assertEquals(150, balance);

        verify(userRepository).findById(userId);
    }

    @Test
    void throwsWhenUserDoesNotExist() {

        UUID userId = UUID.randomUUID();

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> coinService.getBalance(userId)
                );

        assertEquals(
                "Usuário não encontrado.",
                exception.getMessage()
        );

        verify(userRepository).findById(userId);
    }
}
