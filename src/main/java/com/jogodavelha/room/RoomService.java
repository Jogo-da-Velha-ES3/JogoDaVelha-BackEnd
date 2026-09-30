package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(isolation = Isolation.READ_COMMITTED)
public class RoomService {
    private final RoomRepository rooms;
    private final UserRepository users;
    private final RoomCodeAllocator allocator;

    public Room createRoom(UUID playerId) {
        return allocator.reserve(findPlayer(playerId));
    }

    public Room joinRoom(String code, UUID playerId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        User player = findPlayer(playerId);
        allocator.lockMembership();
        Room room = rooms.findByCodeAndStatusIn(code, RoomCodeAllocator.ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("Sala não encontrada ou encerrada."));

        if (room.getStatus() == RoomStatus.IN_GAME) {
            throw new IllegalStateException("A partida desta sala já foi iniciada.");
        }
        allocator.requireAvailablePlayer(player);
        room.join(player);
        return rooms.saveAndFlush(room);
    }

    private User findPlayer(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Informe o ID do jogador.");
        }
        return users.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Jogador não encontrado."));
    }
}
