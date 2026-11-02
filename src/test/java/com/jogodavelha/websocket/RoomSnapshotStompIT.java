package com.jogodavelha.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.config.CorsProperties;
import com.jogodavelha.config.JwtProperties;
import com.jogodavelha.config.SecurityConfig;
import com.jogodavelha.config.StompJwtAuthenticationInterceptor;
import com.jogodavelha.config.WebSocketConfig;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameEndReason;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.MatchFinishedEvent;
import com.jogodavelha.game.GameStatus;
import com.jogodavelha.game.Score;
import com.jogodavelha.game.draw.RoundDrawState;
import com.jogodavelha.game.draft.DraftStateManager;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.game.logic.GameValidator;
import com.jogodavelha.game.logic.MoveValidator;
import com.jogodavelha.game.service.MoveActionService;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.game.service.TurnService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomPlayerService;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.room.RematchStateManager;
import com.jogodavelha.skill.SkillEngine;
import com.jogodavelha.skill.SkillActivationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = RoomSnapshotStompIT.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes",
                "jwt.expiration=3600000"
        })
class RoomSnapshotStompIT {
    private static final UUID P1 = UUID.randomUUID();
    private static final UUID P2 = UUID.randomUUID();
    private static final UUID GAME_ID = UUID.randomUUID();
    private static final UUID ROOM_ID = UUID.randomUUID();
    private static final String ROOM_CODE = "7391";
    private static final String TOPIC = "/topic/rooms/" + ROOM_CODE;
    private static final long NOW = 1_800_000_000_000L;
    private static final String P1_PRIVATE_OPTION = "P1_PRIVATE_DRAW_OPTION";
    private static final String P2_PRIVATE_OPTION = "P2_PRIVATE_DRAW_OPTION";

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            GameplayController.class, MoveActionService.class, MoveValidator.class, TurnService.class,
            GameValidator.class, SkillEngine.class, RoomSnapshotService.class, RoomSnapshotEventPublisher.class,
            WebSocketService.class, RoomPlayerService.class})
    static class TestApplication {
        @Bean com.jogodavelha.avatar.AvatarService avatars() {
            return mock(com.jogodavelha.avatar.AvatarService.class);
        }
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean JwtService jwtService(JwtProperties properties, Clock clock) { return new JwtService(properties, clock); }
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.ofEpochMilli(NOW), java.time.ZoneOffset.UTC);
        }
        @Bean InMemoryGameStateManager gameStates() { return new InMemoryGameStateManager(); }
        @Bean RoomRepository rooms() { return mock(RoomRepository.class); }
        @Bean GameRepository games() { return mock(GameRepository.class); }
        @Bean DraftStateManager drafts() { return mock(DraftStateManager.class); }
        @Bean RematchStateManager rematches() { return mock(RematchStateManager.class); }
        @Bean CharacterRepository characters() { return mock(CharacterRepository.class); }
        @Bean PlayerService playerService() { return mock(PlayerService.class); }
        @Bean SkillActivationService skillActivationService() { return mock(SkillActivationService.class); }
        @Bean(name = "turnTaskScheduler") TaskScheduler turnTaskScheduler() {
            TaskScheduler scheduler = mock(TaskScheduler.class);
            doReturn(mock(ScheduledFuture.class)).when(scheduler)
                    .schedule(any(Runnable.class), any(Instant.class));
            return scheduler;
        }
    }

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired ObjectMapper objectMapper;
    @Autowired InMemoryGameStateManager gameStates;
    @Autowired RoomRepository rooms;
    @Autowired GameRepository games;
    @Autowired PlayerService playerService;
    @Autowired ApplicationEventPublisher events;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private Room room;
    private Game game;

    @BeforeEach
    void setUp() {
        User player1 = user(P1, "player-one");
        User player2 = user(P2, "player-two");
        room = new Room(ROOM_CODE, player1);
        room.setId(ROOM_ID);
        room.join(player2);

        game = mock(Game.class);
        when(game.getId()).thenReturn(GAME_ID);
        when(game.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(game.getCurrentRound()).thenReturn(1);
        when(game.isSuddenDeath()).thenReturn(false);
        when(game.getScore()).thenReturn(new Score(0, 0));

        GameRepository.SnapshotRoute route = mock(GameRepository.SnapshotRoute.class);
        when(route.getStatus()).thenReturn(GameStatus.IN_PROGRESS);
        when(route.getRoomCode()).thenReturn(ROOM_CODE);
        when(rooms.findActiveMembership(eq(ROOM_CODE), anyList(), any(UUID.class))).thenAnswer(invocation -> {
            UUID playerId = invocation.getArgument(2);
            return playerId.equals(P1) || playerId.equals(P2) ? Optional.of(room) : Optional.empty();
        });
        when(rooms.findByCodeAndStatusIn(eq(ROOM_CODE), anyList())).thenReturn(Optional.of(room));
        when(games.findSnapshotRouteById(GAME_ID)).thenReturn(Optional.of(route));
        when(games.findByRoomIdAndStatus(ROOM_ID, GameStatus.IN_PROGRESS)).thenReturn(Optional.of(game));
        when(playerService.isConnected(eq(ROOM_CODE), any(UUID.class))).thenReturn(true);

        Map<UUID, RoundDrawState.Player> privateDrawOptions = new LinkedHashMap<>();
        privateDrawOptions.put(P1, new RoundDrawState.Player(List.of(P1_PRIVATE_OPTION, "P1_OTHER", "P1_THIRD"), null));
        privateDrawOptions.put(P2, new RoundDrawState.Player(List.of(P2_PRIVATE_OPTION, "P2_OTHER", "P2_THIRD"), null));
        GameState state = GameState.builder()
                .gameId(GAME_ID)
                .board(GameState.EMPTY_BOARD)
                .player1Id(P1)
                .player2Id(P2)
                .firstPlayerId(P1)
                .currentTurnPlayerId(P1)
                .turnDurationMillis(GameState.DEFAULT_TURN_DURATION_MILLIS)
                .turnDeadlineEpochMillis(NOW + GameState.DEFAULT_TURN_DURATION_MILLIS)
                .roundNumber(1)
                .phase(GamePhase.PLAYING)
                // Keep private draw sentinels in state to catch accidental serialization.
                .roundDraw(new RoundDrawState(ROOM_CODE, 1, NOW + 8_000, privateDrawOptions))
                .build();
        gameStates.set(state);
    }

    @AfterEach
    void stopClients() {
        sessions.forEach(session -> {
            if (session.isConnected()) session.disconnect();
        });
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    void bothStompClientsReceiveTheUpdatedMoveSnapshotWithoutPrivateDrawOptions() throws Exception {
        Client p1 = connectAndSubscribe(P1);
        Client p2 = connectAndSubscribe(P2);
        awaitSubscriptions();

        StompHeaders moveHeaders = new StompHeaders();
        moveHeaders.setDestination("/app/game/" + GAME_ID + "/move");
        moveHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        p1.session().send(moveHeaders, objectMapper.writeValueAsBytes(Map.of("position", 0)));

        assertPublicMoveSnapshot(p1.publicSnapshots());
        assertPublicMoveSnapshot(p2.publicSnapshots());
        assertPrivateMoveSnapshot(p1.privateSnapshots(), "P1");
        assertPrivateMoveSnapshot(p2.privateSnapshots(), "P2");

        assertEquals("X---------------", gameStates.find(GAME_ID).orElseThrow().getBoard());
    }

    @Test
    void bothStompClientsReceiveForfeitResultSnapshotWhileRedisStateStillExists() throws Exception {
        Client p1 = connectAndSubscribe(P1);
        Client p2 = connectAndSubscribe(P2);
        awaitSubscriptions();

        when(game.getStatus()).thenReturn(GameStatus.FINISHED);
        when(game.getWinner()).thenReturn(user(P2, "player-two"));
        when(game.getEndReason()).thenReturn(GameEndReason.FORFEIT_TIMEOUT);
        when(games.findTopByRoomIdAndStatusOrderByFinishedAtDesc(ROOM_ID, GameStatus.FINISHED))
                .thenReturn(Optional.of(game));

        events.publishEvent(new MatchFinishedEvent(GAME_ID, ROOM_CODE));

        assertForfeitResultSnapshot(receive(p1.publicSnapshots()), null);
        assertForfeitResultSnapshot(receive(p1.privateSnapshots()), "P1");
        assertForfeitResultSnapshot(receive(p2.publicSnapshots()), null);
        assertForfeitResultSnapshot(receive(p2.privateSnapshots()), "P2");
        assertTrue(gameStates.find(GAME_ID).isPresent());
    }

    private Client connectAndSubscribe(UUID playerId) throws Exception {
        WebSocketStompClient client = new WebSocketStompClient(new StandardWebSocketClient());
        clients.add(client);
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + jwtService.generateToken(user(playerId, "player")));
        StompSession session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(),
                        connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        sessions.add(session);

        BlockingQueue<JsonNode> publicSnapshots = new LinkedBlockingQueue<>();
        BlockingQueue<JsonNode> privateSnapshots = new LinkedBlockingQueue<>();
        session.subscribe(TOPIC, jsonHandler(publicSnapshots));
        session.subscribe("/user/queue/room", jsonHandler(privateSnapshots));
        return new Client(session, publicSnapshots, privateSnapshots);
    }

    private StompFrameHandler jsonHandler(BlockingQueue<JsonNode> received) {
        return new StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    received.add(objectMapper.readTree((byte[]) payload));
                } catch (Exception exception) {
                    throw new AssertionError("Não foi possível ler o snapshot STOMP", exception);
                }
            }
        };
    }

    private void awaitSubscriptions() {
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            SimpleBrokerMessageHandler broker = applicationContext.getBean(SimpleBrokerMessageHandler.class);
            return subscriptionCount(broker) == 2;
        });
    }

    @Autowired org.springframework.context.ApplicationContext applicationContext;

    private int subscriptionCount(SimpleBrokerMessageHandler broker) {
        var message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER,
                        org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, TOPIC)
                .build();
        return broker.getSubscriptionRegistry().findSubscriptions(message).size();
    }

    private void assertPublicMoveSnapshot(BlockingQueue<JsonNode> received) throws InterruptedException {
        JsonNode message = receive(received);
        JsonNode payload = snapshotPayload(message);
        assertTrue(payload.get("mySlot").isNull());
        assertMove(payload);
        assertTrue(payload.get("drawOptions").isNull());
        assertNoPrivateOptions(message);
    }

    private void assertPrivateMoveSnapshot(BlockingQueue<JsonNode> received, String slot) throws InterruptedException {
        JsonNode message = receive(received);
        JsonNode payload = snapshotPayload(message);
        assertEquals(slot, payload.path("mySlot").asText());
        assertMove(payload);
        assertTrue(payload.get("drawOptions").isNull());
        assertNoPrivateOptions(message);
    }

    private JsonNode snapshotPayload(JsonNode message) {
        assertEquals("ROOM_SNAPSHOT", message.path("type").asText());
        return message.path("payload");
    }

    private void assertForfeitResultSnapshot(JsonNode message, String slot) {
        JsonNode payload = snapshotPayload(message);
        if (slot == null) {
            assertTrue(payload.get("mySlot").isNull());
        } else {
            assertEquals(slot, payload.path("mySlot").asText());
        }
        assertEquals("RESULT", payload.path("phase").asText());
        assertEquals("WO", payload.path("outcome").path("kind").asText());
        assertEquals("P2", payload.path("outcome").path("winner").asText());
        assertEquals("FORFEIT_TIMEOUT", payload.path("outcome").path("reason").asText());
        assertTrue(payload.get("turn").isNull());
    }

    private void assertMove(JsonNode payload) {
        assertEquals(ROOM_CODE, payload.path("code").asText());
        assertEquals("PLAYING", payload.path("phase").asText());
        assertEquals("P1", payload.path("board").get(0).path("owner").asText());
        for (int position = 1; position < 16; position++) {
            assertTrue(payload.path("board").get(position).path("owner").isNull());
        }
        JsonNode turn = payload.path("turn");
        assertEquals("P2", turn.path("player").asText());
        assertEquals(NOW, turn.path("startedAt").asLong());
        assertEquals(NOW + GameState.DEFAULT_TURN_DURATION_MILLIS, turn.path("deadline").asLong());
        assertEquals(10, turn.path("durationSeconds").asInt());
    }

    private void assertNoPrivateOptions(JsonNode message) {
        String serialized = message.toString();
        assertFalse(serialized.contains(P1_PRIVATE_OPTION));
        assertFalse(serialized.contains(P2_PRIVATE_OPTION));
    }

    private JsonNode receive(BlockingQueue<JsonNode> received) throws InterruptedException {
        JsonNode message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "Snapshot atualizado não foi entregue por STOMP.");
        return message;
    }

    private static User user(UUID id, String username) {
        User user = new User(username, "hashed-password", username + "@example.com");
        user.setId(id);
        return user;
    }

    private record Client(StompSession session, BlockingQueue<JsonNode> publicSnapshots,
                          BlockingQueue<JsonNode> privateSnapshots) { }

    static class InMemoryGameStateManager extends GameStateManager {
        private GameState state;

        InMemoryGameStateManager() {
            super(null, new ObjectMapper(), 1_800_000, 5);
        }

        void set(GameState state) { this.state = state; }

        @Override public Optional<GameState> find(UUID gameId) {
            return state != null && state.getGameId().equals(gameId) ? Optional.of(state) : Optional.empty();
        }

        @Override public void update(UUID gameId, Function<GameState, GameState> mutator) {
            if (state == null || !state.getGameId().equals(gameId)) throw new IllegalStateException("Estado ausente");
            state = mutator.apply(state);
        }
    }
}
