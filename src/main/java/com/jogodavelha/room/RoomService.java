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
        validateCode(code);
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

    public Room leaveRoom(String code, UUID playerId) {
        validateCode(code); // mesma checagem de 4 dígitos do join
        findPlayer(playerId);
        allocator.lockMembership();
        Room room = rooms.findByCodeAndStatusIn(code, RoomCodeAllocator.ACTIVE).orElseThrow(
                () -> new RoomNotFoundException("Sala não encontrada ou encerrada.")
        );

        boolean isHost = room.getPlayer1().getId().equals(playerId);
        boolean isGuest = room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId);
        if (!isHost && !isGuest) {
            throw new IllegalArgumentException("Jogador não pertence à sala."); // vira 400
        }
        if (room.getStatus() == RoomStatus.IN_GAME) {
            throw new RoomAlreadyStartedException("A partida já foi iniciada; a saída é tratada como W.O.");
        }
        room.close();
        Room saved = rooms.saveAndFlush(room);
        events.publishEvent(new RoomClosedEvent(saved.getCode(), saved.getId(), playerId, "HOST_LEFT"));
        return saved;
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
    }
}
