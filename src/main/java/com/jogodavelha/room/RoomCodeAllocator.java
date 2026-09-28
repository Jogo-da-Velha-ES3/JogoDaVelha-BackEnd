package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.game.GameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class RoomCodeAllocator {
    private static final List<RoomStatus> ACTIVE = List.of(RoomStatus.WAITING, RoomStatus.IN_GAME);
    private final RoomRepository rooms;
    private final GameRepository gameRepository;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Room reserve(User player1) {
        // Serializa as reservas entre instâncias até o fim da transação.
        jdbc.query("SELECT pg_advisory_xact_lock(7007)", rs -> { });

        // Verifica se o jogador já está em uma partida ativa
        gameRepository.findActiveGameByPlayerId(player1.getId()).ifPresent(game -> {
            throw new IllegalStateException("Jogador já está em uma partida ativa");
        });

        // Uma sala aguardando também reserva a participação do jogador.
        if (rooms.existsByStatusInAndPlayer1IdOrStatusInAndPlayer2Id(
                ACTIVE, player1.getId(), ACTIVE, player1.getId())) {
            throw new IllegalStateException("Jogador já está em uma sala ativa");
        }

        int start = random.nextInt(10_000);
        for (int offset = 0; offset < 10_000; offset++) {
            String code = String.format(Locale.ROOT, "%04d", (start + offset) % 10_000);
            if (!rooms.existsByCodeAndStatusIn(code, ACTIVE)) {
                return rooms.saveAndFlush(new Room(code, player1));
            }
        }
        throw new IllegalStateException("Não há códigos de sala disponíveis.");
    }
}
