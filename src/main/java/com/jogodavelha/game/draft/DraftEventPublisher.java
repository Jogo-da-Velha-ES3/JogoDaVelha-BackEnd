package com.jogodavelha.game.draft;

import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.websocket.WebSocketService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class DraftEventPublisher {
    private final WebSocketService websocket;
    private final CharacterRepository characters;
    private final Clock clock;

    public DraftEventPublisher(WebSocketService websocket, CharacterRepository characters, Clock clock) {
        this.websocket = websocket;
        this.characters = characters;
        this.clock = clock;
    }

    // DraftService emite depois do CAS no Redis, fora do callback que pode ser repetido.
    @EventListener
    public void onDraftUpdated(DraftUpdated event) {
        var selectedIds = event.players().stream().map(DraftUpdated.PlayerStatus::characterId)
                .filter(Objects::nonNull).toList();
        var symbols = selectedIds.isEmpty() ? Map.<UUID, CharacterSymbol>of()
                : characters.findAllById(selectedIds).stream()
                        .collect(Collectors.toMap(Character::getId, Character::getSymbol));
        Map<String, CharacterSymbol> selected = new LinkedHashMap<>();
        Map<String, Boolean> ready = new LinkedHashMap<>();
        for (int index = 0; index < event.players().size(); index++) {
            var player = event.players().get(index);
            String slot = "P" + (index + 1);
            ready.put(slot, player.ready());
            if (player.characterId() != null) {
                selected.put(slot, Objects.requireNonNull(symbols.get(player.characterId()),
                        "Personagem selecionado não existe no catálogo."));
            }
        }
        websocket.sendToTopic("/topic/rooms/" + event.roomCode(),
                new Message("DRAFT_UPDATE", new Progress(event.deadlineEpochMillis(), selected, ready), clock.millis()));
    }

    public record Message(String type, Progress payload, long serverTime) { }

    public record Progress(long deadline, Map<String, CharacterSymbol> characters, Map<String, Boolean> ready) {
        public Progress {
            characters = Map.copyOf(characters);
            ready = Map.copyOf(ready);
        }
    }
}
