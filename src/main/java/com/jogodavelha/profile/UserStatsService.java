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
        UserStats stats = getOrCreateStats(user);
        stats.registerWin();
    }

    @Transactional
    public void registerDraw(User user) {
        UserStats stats = getOrCreateStats(user);
        stats.registerDraw();
    }

    @Transactional
    public void registerLoss(User user) {
        UserStats stats = getOrCreateStats(user);
        stats.registerLoss();
    }

    private UserStats getOrCreateStats(User user) {
        return userStatsRepository
                .findByUser_Id(user.getId())
                .orElseGet(() -> {
                    UserStats newStats = new UserStats(user);
                    return userStatsRepository.save(newStats);
                });
    }
}
