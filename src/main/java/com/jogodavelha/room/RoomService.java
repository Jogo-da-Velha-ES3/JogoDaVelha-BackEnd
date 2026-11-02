package com.jogodavelha.room;

import com.jogodavelha.auth.User;
import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
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
    private final RoomPlayerService roomPlayers;
    private final GameRepository games;

    public Room createRoom(UUID playerId) {
        return allocator.reserve(findPlayer(playerId));
    }

    public Room joinRoom(String code, UUID playerId) {
        validateCode(code);
        User player = findPlayer(playerId);
        allocator.lockMembership();
        Room room = rooms.findByCodeAndStatusIn(code, RoomCodeAllocator.ACTIVE).orElseThrow(
                () -> new RoomNotFoundException("Sala não encontrada ou encerrada.")
        );

        allocator.requireAvailablePlayer(player);
        room.join(player);

        Room saved = rooms.saveAndFlush(room);
        events.publishEvent(new PlayerJoinedRoomEvent(
                saved.getCode(), saved.getId(),
                roomPlayers.info(player),
                roomPlayers.host(saved))
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

    public void closeAfterMatch(String code, UUID playerId, String reason) {
        validateCode(code);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Informe o motivo do encerramento.");
        allocator.lockMembership();
        Room room = rooms.findByCode(code).orElseThrow(
                () -> new RoomNotFoundException("Sala não encontrada ou encerrada."));
        boolean member = playerId == null || room.getPlayer1().getId().equals(playerId)
                || room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId);
        if (!member) throw new IllegalArgumentException("Jogador não pertence à sala.");
        if (room.getStatus() == RoomStatus.CLOSED) return;
        if (room.getStatus() != RoomStatus.IN_GAME || room.getPlayer2() == null
                || games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS).isPresent()
                || games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED).isEmpty()) {
            throw new RoomAlreadyStartedException("A sala só pode ser encerrada na tela de resultado.");
        }
        room.close();
        Room saved = rooms.saveAndFlush(room);
        events.publishEvent(new RoomClosedEvent(saved.getCode(), saved.getId(), playerId, reason));
    }

    private void validateCode(String code) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
    }
}
