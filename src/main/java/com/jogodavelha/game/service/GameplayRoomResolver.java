package com.jogodavelha.game.service;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GameplayRoomResolver {
    private final RoomRepository rooms;
    private final GameRepository games;

    public GameplayRoomResolver(RoomRepository rooms, GameRepository games) {
        this.rooms = rooms;
        this.games = games;
    }

    public UUID resolveActiveGame(String code, UUID playerId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        if (playerId == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }

        Room room = rooms.findByCodeAndStatusIn(code, RoomCodeAllocator.ACTIVE)
                .orElseThrow(() -> new RoomNotFoundException("Sala não encontrada ou encerrada."));
        if (!isRoomMember(room, playerId)) {
            throw new AccessDeniedException("Jogador não pertence à sala.");
        }

        Game game = games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalStateException("Não há partida ativa nesta sala."));
        if (game.getRoom() == null || !room.getId().equals(game.getRoom().getId())
                || !isGamePlayer(game, playerId)) {
            throw new AccessDeniedException("Jogador não pertence à partida desta sala.");
        }
        return game.getId();
    }

    private boolean isRoomMember(Room room, UUID playerId) {
        return room.getPlayer1().getId().equals(playerId)
                || room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId);
    }

    private boolean isGamePlayer(Game game, UUID playerId) {
        return game.getPlayer1().getId().equals(playerId)
                || game.getPlayer2() != null && game.getPlayer2().getId().equals(playerId);
    }
}
