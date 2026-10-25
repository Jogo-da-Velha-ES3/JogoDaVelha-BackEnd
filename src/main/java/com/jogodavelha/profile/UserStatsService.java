package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserStatsService {

    private final UserStatsRepository userStatsRepository;

    @Transactional
    public void registerWin(User user) {
        UserStats stats = userStatsRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Estatísticas não encontradas para o usuário"
                ));

        stats.registerWin();
    }

    @Transactional
    public void registerDraw(User user) {
        UserStats stats = userStatsRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Estatísticas não encontradas para o usuário"
                ));

        stats.registerDraw();
    }

    @Transactional
    public void registerLoss(User user) {
        UserStats stats = userStatsRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Estatísticas não encontradas para o usuário"
                ));

        stats.registerLoss();
    }
}
