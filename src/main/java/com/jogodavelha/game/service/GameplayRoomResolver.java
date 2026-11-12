package com.jogodavelha.game.service;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.telemetry.ActionProfiler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class GameplayRoomResolver {
    private final RoomRepository rooms;
    private final GameRepository games;
    private final GameplayRouteCache routes;

    public GameplayRoomResolver(RoomRepository rooms, GameRepository games, GameplayRouteCache routes) {
        this.rooms = rooms;
        this.games = games;
        this.routes = routes;
    }

    public UUID resolveActiveGame(String code, UUID playerId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        if (playerId == null) {
            throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        }

        long cacheStart = System.nanoTime();
        var cached = routes.find(code);
        ActionProfiler.add("route.cache", cacheStart);
        if (cached.isPresent() && cached.get().isMember(playerId)) {
            return cached.get().gameId();
        }
        return resolveFromDatabase(code, playerId);
    }

    /** Caminho completo com validações no PostgreSQL; repovoa o cache de rota. */
    private UUID resolveFromDatabase(String code, UUID playerId) {
        long dbStart = System.nanoTime();
        try {
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
            UUID gameId = game.getId();
            routes.put(code, gameId, game.getPlayer1().getId(),
                    game.getPlayer2() == null ? null : game.getPlayer2().getId());
            return gameId;
        } finally {
            ActionProfiler.add("room.resolveDb", dbStart);
        }
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
