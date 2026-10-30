package com.jogodavelha.game.draw;

import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.MatchLifecycleEvent;
import com.jogodavelha.game.RoundSkillDraft;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameStateNotFoundException;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.skill.Skill;
import com.jogodavelha.skill.SkillRepository;
import com.jogodavelha.websocket.RoomSnapshotService;
import com.jogodavelha.websocket.WebSocketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.random.RandomGenerator;

@Service
public class RoundSkillDrawService implements RoundSkillDraft {
    private record RoundKey(UUID gameId, int round) { }

    private final GameStateManager states;
    private final SkillRepository skills;
    private final RoomRepository rooms;
    private final GameRepository games;
    private final Clock clock;
    private final TaskScheduler scheduler;
    private final WebSocketService websocket;
    private final RoomSnapshotService snapshots;
    private final ApplicationEventPublisher events;
    private final RandomGenerator random;
    private final Map<RoundKey, ScheduledFuture<?>> timers = new ConcurrentHashMap<>();

    @Autowired
    public RoundSkillDrawService(GameStateManager states, SkillRepository skills, RoomRepository rooms,
                                GameRepository games, Clock clock,
                                @Qualifier("turnTaskScheduler") TaskScheduler scheduler,
                                WebSocketService websocket, RoomSnapshotService snapshots, ApplicationEventPublisher events) {
        this(states, skills, rooms, games, clock, scheduler, websocket, snapshots, events, new SecureRandom());
    }

    public RoundSkillDrawService(GameStateManager states, SkillRepository skills, RoomRepository rooms,
                                GameRepository games, Clock clock, TaskScheduler scheduler,
                                WebSocketService websocket, RoomSnapshotService snapshots,
                                ApplicationEventPublisher events, RandomGenerator random) {
        this.states = states;
        this.skills = skills;
        this.rooms = rooms;
        this.games = games;
        this.clock = clock;
        this.scheduler = scheduler;
        this.websocket = websocket;
        this.snapshots = snapshots;
        this.events = events;
        this.random = random;
    }

    @Override
    public void beginRound(UUID gameId, int round, UUID player1, UUID player2) {
        GameState before = states.find(gameId).orElseThrow(() -> new GameStateNotFoundException(gameId));
        if (before.getRoundNumber() != round || before.getPhase() != GamePhase.DRAFT) return;
        if (!player1.equals(before.getPlayer1Id()) || !player2.equals(before.getPlayer2Id())) {
            throw new IllegalArgumentException("Jogadores diferentes dos participantes da partida.");
        }
        RoundKey key = new RoundKey(gameId, round);
        if (before.getRoundDraw() == null) {
            var game = games.findById(gameId).orElseThrow();
            List<String> catalog = skills.findAll().stream().map(Skill::getCode).distinct().sorted().toList();
            RoundDrawState draw = new RoundDrawState(game.getRoom().getCode(), round,
                    clock.millis() + RoundDrawState.DURATION_MILLIS, Map.of(
                    player1, new RoundDrawState.Player(options(before, player1, catalog), null),
                    player2, new RoundDrawState.Player(options(before, player2, catalog), null)));
            // O sorteio ocorre uma vez, antes do callback que o Redis pode repetir.
            states.update(gameId, current -> {
                if (current.getRoundNumber() == round && current.getPhase() == GamePhase.DRAFT
                        && current.getRoundDraw() == null) {
                    current.setRoundDraw(draw);
                }
                return current;
            });
        }
        GameState state = states.find(gameId).orElseThrow();
        RoundDrawState draw = state.getRoundDraw();
        if (draw == null || draw.round() != round || state.getPhase() != GamePhase.DRAFT) {
            return;
        }
        if (draw.completed()) {
            complete(key);
            return;
        }
        if (clock.millis() >= draw.deadline()) {
            expire(gameId, round, draw.deadline());
            return;
        }
        schedule(key, draw.deadline());
        websocket.sendToTopic("/topic/rooms/" + draw.roomCode(),
                event("PHASE_CHANGE", Map.of("phase", "ROUND_DRAW")));
        snapshots.publish(draw.roomCode());
        draw.players().forEach((playerId, player) -> {
            if (player.selected() == null) websocket.sendToUser(playerId.toString(), "/queue/room",
                    event("DRAW_OPTIONS", Map.of("options", player.options(), "deadline", draw.deadline())));
        });
    }

    @EventListener(ApplicationReadyEvent.class)
    public void resumePendingRounds() {
        states.findAllGameIds().forEach(this::resume);
    }

    public void resume(UUID gameId) {
        GameState state = states.find(gameId).orElse(null);
        if (state == null || state.getPhase() != GamePhase.DRAFT || state.getRoundDraw() == null) return;
        RoundDrawState draw = state.getRoundDraw();
        RoundKey key = new RoundKey(gameId, draw.round());
        if (draw.completed()) complete(key);
        else if (clock.millis() >= draw.deadline()) expire(gameId, draw.round(), draw.deadline());
        else schedule(key, draw.deadline());
    }

    public void pick(String code, UUID playerId, String ability) {
        if (playerId == null) throw new AccessDeniedException("Autenticação STOMP obrigatória.");
        if (code == null || !code.matches("[0-9]{4}")) {
            throw new IllegalArgumentException("O código deve conter quatro dígitos.");
        }
        if (ability == null || ability.isBlank()) throw new IllegalArgumentException("Informe a habilidade sorteada.");
        var room = rooms.findActiveMembership(code, RoomCodeAllocator.ACTIVE, playerId)
                .orElseThrow(() -> new AccessDeniedException("Jogador não pertence à sala."));
        var game = games.findByRoomIdAndStatus(room.getId(), GameStatus.IN_PROGRESS)
                .orElseThrow(() -> new IllegalStateException("Não há sorteio ativo nesta sala."));
        UUID gameId = game.getId();
        GameState before = states.find(gameId).orElseThrow(() -> new IllegalStateException("Sorteio indisponível."));
        RoundDrawState expected = before.getRoundDraw();
        if (expected == null) throw new IllegalStateException("Não há sorteio ativo nesta sala.");
        Set<String> deckBefore = currentDeck(before, playerId);
        String replaced = replacement(before, playerId, expected.round(), deckBefore);
        states.update(gameId, current -> {
            RoundDrawState draw = current.getRoundDraw();
            // Reconfere o prazo em cada tentativa, como o Draft inicial.
            if (current.getPhase() != GamePhase.DRAFT || draw == null || draw.round() != expected.round()
                    || clock.millis() >= draw.deadline()) throw new IllegalStateException("Prazo do sorteio encerrado.");
            current.setRoundDraw(draw.select(playerId, ability));
            applyDeck(current, playerId, expected.round(), ability, replaced);
            current.recordChosenDrawSkill(playerId, ability);
            return current;
        });
        try {
            publishDeck(gameId, expected.round(), playerId);
        } finally {
            if (!complete(new RoundKey(gameId, expected.round()))) snapshots.publish(code);
        }
    }

    public void expire(UUID gameId, int round, long deadline) {
        RoundKey key = new RoundKey(gameId, round);
        GameState state = states.find(gameId).orElse(null);
        if (!pending(state, round, deadline)) {
            cancel(key);
            return;
        }
        if (clock.millis() < deadline) {
            schedule(key, deadline);
            return;
        }
        Map<UUID, String> automatic = new HashMap<>();
        Map<UUID, String> replacements = new HashMap<>();
        state.getRoundDraw().players().forEach((player, draw) -> {
            if (draw.selected() == null) {
                automatic.put(player, draw.options().get(random.nextInt(draw.options().size())));
                replacements.put(player, replacement(state, player, round, currentDeck(state, player)));
            }
        });
        Set<UUID> selected;
        try {
            selected = states.updateWithResult(gameId, current -> {
                Set<UUID> changed = new HashSet<>();
                if (!pending(current, round, deadline)) return new GameStateManager.Update<>(current, changed);
                for (var choice : automatic.entrySet()) {
                    if (current.getRoundDraw().players().get(choice.getKey()).selected() == null) {
                        current.setRoundDraw(current.getRoundDraw().select(choice.getKey(), choice.getValue()));
                        applyDeck(current, choice.getKey(), round, choice.getValue(), replacements.get(choice.getKey()));
                        current.recordChosenDrawSkill(choice.getKey(), choice.getValue());
                        changed.add(choice.getKey());
                    }
                }
                return new GameStateManager.Update<>(current, Set.copyOf(changed));
            });
        } catch (GameStateNotFoundException ended) {
            cancel(key);
            return;
        }
        try {
            selected.forEach(player -> publishDeck(gameId, round, player));
        } finally {
            complete(key);
        }
    }

    private boolean pending(GameState state, int round, long deadline) {
        return state != null && state.getPhase() == GamePhase.DRAFT && state.getRoundNumber() == round
                && state.getRoundDraw() != null && state.getRoundDraw().deadline() == deadline;
    }

    private List<String> options(GameState state, UUID playerId, List<String> catalog) {
        Set<String> current = currentDeck(state, playerId);
        if (current.size() != (state.getRoundNumber() == 1 ? 2 : 3)) {
            throw new IllegalStateException("O deck atual do jogador possui tamanho inválido.");
        }
        Set<String> excluded = new HashSet<>(current);
        excluded.addAll(state.getChosenDrawSkills(playerId));
        List<String> available = new ArrayList<>(catalog.stream().filter(code -> !excluded.contains(code)).toList());
        if (available.size() < 3) throw new IllegalStateException("Catálogo insuficiente para sortear três opções.");
        for (int i = 0; i < 3; i++) Collections.swap(available, i, i + random.nextInt(available.size() - i));
        return List.copyOf(available.subList(0, 3));
    }

    private Set<String> currentDeck(GameState state, UUID playerId) {
        Set<String> current = state.getRoundSkillDecks().get(playerId);
        if (current == null) current = state.getInitialSkillsByPlayer().get(playerId);
        if (current == null) throw new IllegalStateException("Deck do jogador indisponível.");
        return new HashSet<>(current);
    }

    private String replacement(GameState state, UUID playerId, int round, Set<String> deck) {
        if (round == 1) return null;
        if (deck.size() != 3) throw new IllegalStateException("O deck atual deve conter três habilidades.");
        List<String> candidates = deck.stream().sorted().toList();
        return candidates.get(random.nextInt(candidates.size()));
    }

    private void applyDeck(GameState state, UUID playerId, int round, String selected, String replaced) {
        Set<String> deck = currentDeck(state, playerId);
        if (round > 1) {
            if (replaced == null || !deck.remove(replaced)) throw new IllegalStateException("Habilidade de substituição indisponível.");
        }
        if (!deck.add(selected)) throw new IllegalStateException("A habilidade sorteada já está no deck.");
        if (deck.size() != 3) throw new IllegalStateException("O deck deve conter exatamente três habilidades.");
        state.setRoundSkillDeck(playerId, deck);
    }

    private void publishDeck(UUID gameId, int round, UUID playerId) {
        states.find(gameId).ifPresent(state -> {
            if (state.getRoundNumber() != round || !state.getRoundSkillDecks().containsKey(playerId)) return;
            var fixed = state.getInitialSkillsByPlayer().get(playerId);
            var deck = state.getRoundSkillDecks().get(playerId).stream().sorted().map(ability ->
                    new RoomSnapshotService.DeckSlot(ability, fixed.contains(ability) ? "INITIAL" : "DRAWN",
                            state.hasUsedSkill(playerId, ability))).toList();
            websocket.sendToUser(playerId.toString(), "/queue/room", event("DECK_UPDATE", Map.of("deck", deck)));
        });
    }

    private boolean complete(RoundKey key) {
        GameState state = states.find(key.gameId()).orElse(null);
        if (state == null || state.getPhase() != GamePhase.DRAFT || state.getRoundDraw() == null
                || state.getRoundDraw().round() != key.round() || !state.getRoundDraw().completed()) return false;
        cancel(key);
        events.publishEvent(new RoundDrawCompleted(key.gameId(), key.round()));
        return true;
    }

    private MatchLifecycleEvent event(String type, Map<String, ?> payload) {
        return new MatchLifecycleEvent(type, Map.copyOf(payload), clock.millis());
    }

    private void schedule(RoundKey key, long deadline) {
        ScheduledFuture<?> timer = scheduler.schedule(() -> expire(key.gameId(), key.round(), deadline),
                Instant.ofEpochMilli(deadline));
        ScheduledFuture<?> previous = timers.put(key, timer);
        if (previous != null) previous.cancel(false);
    }

    private void cancel(RoundKey key) {
        ScheduledFuture<?> timer = timers.remove(key);
        if (timer != null) timer.cancel(false);
    }
}
