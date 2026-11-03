package com.jogodavelha.game.draft;

import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class DraftAbilitiesService {
    private final RoomRepository rooms;
    private final DraftService drafts;

    public DraftAbilitiesService(RoomRepository rooms, DraftService drafts) {
        this.rooms = rooms;
        this.drafts = drafts;
    }

    public void confirm(String roomCode, UUID playerId, List<String> requested) {
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        if (roomCode == null || !roomCode.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        if (requested == null || requested.size() != 2
                || requested.stream().anyMatch(code -> code == null || code.isBlank())
                || requested.get(0).equals(requested.get(1))) {
            throw new IllegalArgumentException("Escolha exatamente duas habilidades distintas.");
        }
        List<String> abilities = List.copyOf(requested);
        var room = rooms.findActiveMembership(roomCode, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
        try {
            drafts.updatePlayer(room.getId(), playerId, player -> player.withSkills(abilities));
        } catch (NoSuchElementException exception) {
            throw new IllegalStateException("O Draft desta sala não está disponível.", exception);
        }
    }
}
