package com.jogodavelha.game.draft;

import com.jogodavelha.auth.UserRepository;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.websocket.WebSocketService;
import com.jogodavelha.game.MatchLifecycleEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class DraftEventPublisher {
    private final WebSocketService websocket;
    private final CharacterRepository characters;
    private final Clock clock;
    private final UserRepository users;
    /** Símbolos dos personagens já resolvidos; o catálogo é estável e evita queries repetidas. */
    private final Map<UUID, CharacterSymbol> symbolCache = new ConcurrentHashMap<>();
    /** Nicknames não mudam durante um Draft; o cache evita uma query por jogador a cada update. */
    private final Map<UUID, String> nicknames = new ConcurrentHashMap<>();

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
        long start = System.nanoTime();
        try {
            publish(event);
        } finally {
            com.jogodavelha.telemetry.ActionProfiler.add("draft.wsPublish", start);
        }
    }

    private void publish(DraftUpdated event) {
        if (event.version() == 0) {
            websocket.sendToTopic("/topic/rooms/" + event.roomCode(),
                    MatchLifecycleEvent.of("PHASE_CHANGE", Map.of("phase", "DRAFT")));
        }
        var selectedIds = event.players().stream().map(DraftUpdated.PlayerStatus::characterId)
                .filter(Objects::nonNull).toList();
        var catalog = symbolCatalog(selectedIds);
        Map<String, CharacterSymbol> selected = new LinkedHashMap<>();
        Map<String, Boolean> ready = new LinkedHashMap<>();
        Map<String, String> nicknames = new LinkedHashMap<>();
        for (int index = 0; index < event.players().size(); index++) {
            var player = event.players().get(index);
            String slot = "P" + (index + 1);
            ready.put(slot, player.ready());
            if (player.characterId() != null) {
                selected.put(
                        slot,
                        Objects.requireNonNull(catalog.get(player.characterId()),
                        "Personagem selecionado não existe no catálogo.")
                );
                String nickname = nickname(player.playerId());
                if (nickname != null) {
                    nicknames.put(slot, nickname);
                }
            }
        }

        websocket.sendToTopic(
                "/topic/rooms/" + event.roomCode(),
                new Message(
                        "DRAFT_UPDATE",
                        new Progress(event.deadlineEpochMillis(), selected, ready, nicknames, event.version()),
                        clock.millis()
                )
        );
    }

    private Map<UUID, CharacterSymbol> symbolCatalog(java.util.List<UUID> selectedIds) {
        var missing = selectedIds.stream().filter(id -> !symbolCache.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            characters.findAllById(missing).forEach(character ->
                    symbolCache.put(character.getId(), character.getSymbol()));
        }
        return symbolCache;
    }

    private String nickname(UUID playerId) {
        String cached = nicknames.get(playerId);
        if (cached != null) {
            return cached;
        }
        try {
            return users.findById(playerId)
                    .map(user -> nicknames.computeIfAbsent(playerId, ignored -> user.getUsername()))
                    .orElse(null);
        } catch (RuntimeException exception) {
            log.warn("Could not enrich DRAFT_UPDATE with nickname: playerId={}", playerId, exception);
            return null;
        }
    }

    public record Message(String type, Progress payload, long serverTime) { }

    public record Progress(
            long deadline,
            Map<String, CharacterSymbol> characters,
            Map<String, Boolean> ready,
            Map<String, String> nicknames,
            /** Cresce a cada mudança do Draft: o front descarta um aviso que chegue fora de ordem. */
            long version
    ) {
        public Progress {
            characters = Map.copyOf(characters);
            ready = Map.copyOf(ready);
            nicknames = Map.copyOf(nicknames);
        }
    }
}
