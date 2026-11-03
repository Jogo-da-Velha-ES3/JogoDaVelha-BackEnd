package com.jogodavelha.profile;

import com.jogodavelha.auth.User;
import com.jogodavelha.avatar.AvatarService;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.jogodavelha.auth.UsernameAlreadyExistsException;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameRepository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProfileService {

    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final UserStatsRepository userStatsRepository;
    private final CharacterRepository characterRepository;
    private final AvatarService avatars;

    public ProfileResponse getProfile(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException("Usuário não encontrado"));

        String favoriteCharacter = getFavoriteCharacter(userId);

        return new ProfileResponse(
                user.getUsername(),
                user.getCoinsBalance(),
                avatars.equippedCode(userId),
                favoriteCharacter
        );
    }

    private String getFavoriteCharacter(UUID userId) {
        List<Game> finishedGames = gameRepository.findFinishedGamesByPlayerId(userId);

        if (finishedGames.isEmpty()) {
            return null;
        }

        Map<UUID, Long> characterUsageCount = finishedGames.stream()
                .flatMap(game -> {
                    List<UUID> characters = new java.util.ArrayList<>();
                    if (game.getPlayer1() != null && userId.equals(game.getPlayer1().getId()) && game.getPlayer1Character() != null) {
                        characters.add(game.getPlayer1Character().getId());
                    }
                    if (game.getPlayer2() != null && userId.equals(game.getPlayer2().getId()) && game.getPlayer2Character() != null) {
                        characters.add(game.getPlayer2Character().getId());
                    }
                    return characters.stream();
                })
                .collect(Collectors.groupingBy(characterId -> characterId, Collectors.counting()));

        if (characterUsageCount.isEmpty()) {
            return null;
        }

        long maxCount = characterUsageCount.values().stream()
                .max(Long::compareTo)
                .orElse(0L);

        List<UUID> mostUsedCharacters = characterUsageCount.entrySet().stream()
                .filter(entry -> entry.getValue() == maxCount)
                .map(Map.Entry::getKey)
                .toList();

        if (mostUsedCharacters.size() == 1) {
            Character character = characterRepository.findById(mostUsedCharacters.get(0)).orElse(null);
            return character != null ? character.getName() : null;
        }

        Map<UUID, Instant> lastUsed = finishedGames.stream()
                .flatMap(game -> {
                    List<Map.Entry<UUID, Instant>> entries = new java.util.ArrayList<>();
                    if (game.getPlayer1() != null && userId.equals(game.getPlayer1().getId()) && game.getPlayer1Character() != null) {
                        entries.add(Map.entry(game.getPlayer1Character().getId(), game.getFinishedAt()));
                    }
                    if (game.getPlayer2() != null && userId.equals(game.getPlayer2().getId()) && game.getPlayer2Character() != null) {
                        entries.add(Map.entry(game.getPlayer2Character().getId(), game.getFinishedAt()));
                    }
                    return entries.stream();
                })
                .filter(entry -> mostUsedCharacters.contains(entry.getKey()))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (existing, replacement) -> existing.isAfter(replacement) ? existing : replacement
                ));

        UUID mostRecentCharacterId = lastUsed.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        if (mostRecentCharacterId != null) {
            Character character = characterRepository.findById(mostRecentCharacterId).orElse(null);
            return character != null ? character.getName() : null;
        }

        return null;
    }

    public ProfileStatsResponse getStats(UUID userId) {

        UserStats stats = userStatsRepository
                .findByUser_Id(userId)
                .orElseGet(() -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() ->
                                    new IllegalStateException("Usuário não encontrado"));
                    return new UserStats(user);
                });

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

        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Usuário não encontrado"
                        ));

        if (user.getUsername().equalsIgnoreCase(normalizedUsername)) {
            return;
        }

        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new UsernameAlreadyExistsException(
                    "Username já está em uso"
            );
        }

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
