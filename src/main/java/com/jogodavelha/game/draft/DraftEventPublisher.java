package com.jogodavelha.game.draft;

import com.jogodavelha.auth.UserRepository;
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
    private final UserRepository users;

    public DraftEventPublisher(
            WebSocketService websocket,
            CharacterRepository characters,
            Clock clock,
            UserRepository users
    ) {
        this.websocket = websocket;
        this.characters = characters;
        this.clock = clock;
        this.users = users;
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

        Map<String, String> nicknames = new LinkedHashMap<>();
        for (int index = 0; index < event.players().size(); index++) {
            var player = event.players().get(index);
            String slot = "P" + (index + 1);
            ready.put(slot, player.ready());
            if (player.characterId() != null) {
                selected.put(
                        slot,
                        Objects.requireNonNull(symbols.get(player.characterId()),
                        "Personagem selecionado não existe no catálogo.")
                );
                users.findById(player.playerId()).ifPresent(
                        user -> nicknames.put(slot, user.getUsername())
                );
            }
        }

        websocket.sendToTopic(
                "/topic/rooms/" + event.roomCode(),
                new Message(
                        "DRAFT_UPDATE",
                        new Progress(event.deadlineEpochMillis(), selected, ready, nicknames),
                        clock.millis()
                )
        );
    }

    public record Message(String type, Progress payload, long serverTime) { }

    public record Progress(
            long deadline,
            Map<String, CharacterSymbol> characters,
            Map<String, Boolean> ready,
            Map<String, String> nicknames
    ) {
        public Progress {
            characters = Map.copyOf(characters);
            ready = Map.copyOf(ready);
            nicknames = Map.copyOf(nicknames);
        }
    }
}
