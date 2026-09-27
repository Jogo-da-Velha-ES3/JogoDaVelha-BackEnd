package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/** Reserva o código e persiste a sala na mesma transação; não apenas sugere um código. */
@Service
public class RoomCodeAllocator {
    private static final List<RoomStatus> ACTIVE = List.of(RoomStatus.WAITING, RoomStatus.IN_GAME);
    private final RoomRepository rooms;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public RoomCodeAllocator(RoomRepository rooms, JdbcTemplate jdbc) {
        this.rooms = rooms;
        this.jdbc = jdbc;
    }

    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public Room reserve(User player1) {
        // Lock compartilhado entre instâncias, liberado automaticamente ao concluir a transação.
        jdbc.query("SELECT pg_advisory_xact_lock(7007)", rs -> { });
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
