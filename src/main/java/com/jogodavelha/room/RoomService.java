package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher events;

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
                .orElseThrow(() ->
                        new RoomNotFoundException("Sala não encontrada ou encerrada."));

        allocator.requireAvailablePlayer(player);
        room.join(player);

        Room saved = rooms.saveAndFlush(room);
        events.publishEvent(new PlayerJoinedRoomEvent(
                saved.getCode(), saved.getId(),
                player.getId(), player.getUsername(),
                saved.getPlayer1().getId(), saved.getPlayer1().getUsername())
        );
        return saved;
    }

    private User findPlayer(UUID playerId) {
        if (playerId == null) {
            throw new IllegalArgumentException("Informe o ID do jogador.");
        }
        return users.findById(playerId)
                .orElseThrow(() -> new IllegalArgumentException("Jogador não encontrado."));
    }
}
