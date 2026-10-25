package com.jogodavelha.coins;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CoinService {

    private final UserRepository userRepository;

    public Integer getBalance(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Usuário não encontrado.")
                );

        return user.getCoinsBalance();
    }
}
