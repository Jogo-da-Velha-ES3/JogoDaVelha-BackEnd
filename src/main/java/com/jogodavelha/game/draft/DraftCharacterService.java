package com.jogodavelha.game.draft;

import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;
import java.util.Locale;
import java.util.UUID;

@Service
public class DraftCharacterService {
    private final RoomRepository rooms;
    private final DraftService drafts;
    private final CharacterRepository characters;

    public DraftCharacterService(RoomRepository rooms, DraftService drafts, CharacterRepository characters) {
        this.rooms = rooms;
        this.drafts = drafts;
        this.characters = characters;
    }

    public void select(String roomCode, UUID playerId, String characterCode) {
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        if (roomCode == null || !roomCode.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        CharacterSymbol symbol;
        try {
            symbol = CharacterSymbol.valueOf(characterCode == null ? "" : characterCode.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Informe um personagem válido.");
        }
        UUID characterId = characters.findBySymbol(symbol)
                .orElseThrow(() -> new IllegalArgumentException("Personagem não encontrado."))
                .getId();
        var room = rooms.findActiveMembership(roomCode, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
        try {
            drafts.selectCharacter(room.getId(), playerId, characterId);
        } catch (NoSuchElementException e) {
            throw new IllegalStateException("O Draft desta sala não está disponível.", e);
        }
    }
}
