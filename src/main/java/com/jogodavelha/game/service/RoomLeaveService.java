package com.jogodavelha.game.service;

import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.room.RematchService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomNotFoundException;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RoomService;
import com.jogodavelha.room.RoomStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * Saída da sala pelo STOMP ({@code /app/rooms/{code}/leave}), em qualquer fase. O servidor decide
 * pela fase da sala, e o front manda sempre a mesma intenção:
 * <ul>
 *   <li>Sala de Espera: o host encerra a sala ({@code HOST_LEFT});</li>
 *   <li>MATCH FEITO e Preparação: a sala é encerrada sem partida e sem Coins ({@code PLAYER_LEFT});</li>
 *   <li>partida em andamento: desistência, o outro vence por W.O.;</li>
 *   <li>Resultado: volta ao lobby, como {@code /lobby} ({@code RETURN_TO_LOBBY}).</li>
 * </ul>
 */
@Service
public class RoomLeaveService {
    private final RoomRepository rooms;
    private final GameRepository games;
    private final DraftStateManager drafts;
    private final RoomService roomService;
    private final RematchService rematches;
    private final GameLifecycleService lifecycle;

    public RoomLeaveService(RoomRepository rooms, GameRepository games, DraftStateManager drafts,
                            RoomService roomService, RematchService rematches, GameLifecycleService lifecycle) {
        this.rooms = rooms;
        this.games = games;
        this.drafts = drafts;
        this.roomService = roomService;
        this.rematches = rematches;
        this.lifecycle = lifecycle;
    }

    public void leave(String code, UUID playerId) {
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código da sala deve conter quatro dígitos.");
        }
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        Room room = rooms.findByCodeAndStatusIn(code, RoomCodeAllocator.ACTIVE)
                .orElseThrow(() -> new RoomNotFoundException("Sala não encontrada ou encerrada."));
        boolean member = room.getPlayer1().getId().equals(playerId)
                || room.getPlayer2() != null && room.getPlayer2().getId().equals(playerId);
        if (!member) throw new AccessDeniedException("Jogador não pertence à sala.");

        if (room.getStatus() == RoomStatus.WAITING) {
            roomService.leaveRoom(code, playerId);
            return;
        }
        Optional<Game> active = games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS);
        if (active.isPresent()) {
            lifecycle.forfeitActiveGame(active.get().getId(), playerId);
            return;
        }
        // Sem partida em andamento: Preparação (Draft aberto, ou o MATCH FEITO antes da primeira
        // partida) ou Resultado (já existe partida encerrada e o Draft dela terminou).
        boolean draftOpen = drafts.find(room.getId()).filter(draft -> !draft.completed()).isPresent();
        boolean neverPlayed = games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(room.getId(), GameStatus.FINISHED)
                .isEmpty();
        if (draftOpen || neverPlayed) {
            roomService.closeBeforeMatch(room.getId(), playerId);
            return;
        }
        rematches.returnToLobby(code, playerId);
    }
}
