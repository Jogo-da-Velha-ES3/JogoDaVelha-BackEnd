package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository userRepository;

    public ProfileResponse getProfile(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("Usuário não encontrado"));

        return new ProfileResponse(
                user.getUsername(),
                user.getCoinsBalance(),
                null,
                null
        );
    }
}
