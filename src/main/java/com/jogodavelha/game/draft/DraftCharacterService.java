package com.jogodavelha.game.draft;

import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DraftCharacterService {
    private final RoomRepository rooms;
    private final DraftService drafts;

    public DraftCharacterService(RoomRepository rooms, DraftService drafts) {
        this.rooms = rooms;
        this.drafts = drafts;
    }

    public void select(String roomCode, UUID playerId, String characterId) {
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        if (roomCode == null || !roomCode.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        UUID character;
        try {
            character = UUID.fromString(characterId);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Informe um personagem válido.");
        }
        var room = rooms.findActiveMembership(roomCode, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
        try {
            drafts.selectCharacter(room.getId(), playerId, character);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("O Draft desta sala não está disponível.", e);
        }
    }
}
