package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jogodavelha.auth.UsernameAlreadyExistsException;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameRepository;
import java.util.List;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;

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

    public ProfileStatsResponse getStats(UUID userId) {

        UserStats stats = userStatsRepository
                .findByUser_Id(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Estatísticas não encontradas"
                        ));

        return new ProfileStatsResponse(
                stats.getMatchesPlayed(),
                stats.getWins(),
                stats.getCurrentWinStreak(),
                stats.getLongestWinStreak(),
                stats.getWinRate()
        );
    }

    @Transactional
    public void updateUsername(
            UUID userId,
            String username
    ) {

        String normalizedUsername = username.trim();

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(
                    "Username já está em uso"
            );
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Usuário não encontrado"
                        ));

        user.setUsername(normalizedUsername);

        userRepository.save(user);
    }

    public List<MatchHistoryResponse> getHistory(UUID userId) {

        return gameRepository.findFinishedGamesByPlayerId(userId)
                .stream()
                .map(game -> {

                    boolean isPlayer1 =
                            game.getPlayer1().getId().equals(userId);

                    User opponent =
                            isPlayer1
                                    ? game.getPlayer2()
                                    : game.getPlayer1();

                    String result;

                    if (game.getWinner() == null) {
                        result = "DRAW";
                    } else if (game.getWinner().getId().equals(userId)) {
                        result = "WIN";
                    } else {
                        result = "LOSS";
                    }

                    return new MatchHistoryResponse(
                            game.getId(),
                            opponent != null
                                    ? opponent.getUsername()
                                    : "Desconhecido",
                            result,
                            game.getEndReason() == GameEndReason.FORFEIT_TIMEOUT,
                            game.getFinishedAt()
                    );
                })
                .toList();
    }

}
