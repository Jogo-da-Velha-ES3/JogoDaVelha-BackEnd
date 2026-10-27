package com.jogodavelha.game.draw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.config.*;
import com.jogodavelha.error.WebSocketExceptionHandler;
import com.jogodavelha.game.Game;
import com.jogodavelha.game.GameRepository;
import com.jogodavelha.game.logic.GamePhase;
import com.jogodavelha.game.logic.GameState;
import com.jogodavelha.game.logic.GameStateManager;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.skill.Skill;
import com.jogodavelha.skill.SkillCategory;
import com.jogodavelha.skill.SkillRepository;
import com.jogodavelha.websocket.RoomSnapshotService;
import com.jogodavelha.websocket.WebSocketService;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = RoundSkillDrawWebSocketTest.Application.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.flyway.enabled=false",
                "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes", "jwt.expiration=3600000"})
class RoundSkillDrawWebSocketTest {
    private static final UUID P1 = UUID.randomUUID(), P2 = UUID.randomUUID(), GAME = UUID.randomUUID();
    private static final ObjectMapper JSON = new ObjectMapper();
    @LocalServerPort int port;
    @Autowired JwtService jwt;
    @Autowired RoundSkillDrawService draws;
    @Autowired GameStateManager states;
    @Autowired SimpUserRegistry users;
    @Autowired ApplicationContext context;
    private final List<WebSocketStompClient> clients = new ArrayList<>();

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            RoundSkillDrawController.class, WebSocketExceptionHandler.class})
    static class Application {
        @Bean ObjectMapper mapper() { return JSON; }
        @Bean JwtService jwt(JwtProperties properties, Clock clock) { return new JwtService(properties, clock); }
        @Bean RoomRepository rooms() {
            var rooms = mock(RoomRepository.class);
            var room = mock(Room.class);
            when(room.getCode()).thenReturn("0042");
            when(rooms.findActiveMembership(eq("0042"), anyList(), argThat(id -> P1.equals(id) || P2.equals(id))))
                    .thenReturn(Optional.of(room));
            return rooms;
        }
        @Bean GameRepository games(RoomRepository rooms) {
            var games = mock(GameRepository.class);
            var game = mock(Game.class);
            var room = rooms.findActiveMembership("0042", List.of(), P1).orElseThrow();
            when(game.getId()).thenReturn(GAME);
            when(game.getRoom()).thenReturn(room);
            when(games.findById(GAME)).thenReturn(Optional.of(game));
            when(games.findByRoomIdAndStatus(any(), any())).thenReturn(Optional.of(game));
            return games;
        }
        @Bean GameStateManager states() {
            var states = mock(GameStateManager.class);
            var state = GameState.builder().gameId(GAME).player1Id(P1).player2Id(P2)
                    .roundNumber(1).phase(GamePhase.DRAFT).build();
            state.setInitialSkills(P1, List.of("BOMBA", "ESCUDO"));
            state.setInitialSkills(P2, List.of("SILENCIO", "CONGELAR"));
            when(states.find(GAME)).thenReturn(Optional.of(state));
            doAnswer(call -> {
                Function<GameState, GameState> update = call.getArgument(1);
                update.apply(state);
                return null;
            }).when(states).update(eq(GAME), any());
            return states;
        }
        @Bean RoundSkillDrawService draws(GameStateManager states, RoomRepository rooms, GameRepository games,
                                         Clock clock, SimpMessagingTemplate template, ApplicationEventPublisher events) {
            var catalog = mock(SkillRepository.class);
            when(catalog.findAll()).thenReturn(List.of("BOMBA", "ESCUDO", "CONGELAR", "SILENCIO", "TORNADO", "AMPULHETA")
                    .stream().map(code -> new Skill(code, code, SkillCategory.OFFENSE, code, false, true, 50)).toList());
            var scheduler = mock(TaskScheduler.class);
            when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
            return new RoundSkillDrawService(states, catalog, rooms, games, clock, scheduler,
                    new WebSocketService(template), mock(RoomSnapshotService.class), events, new Random(1));
        }
    }

    @AfterEach
    void stop() { clients.forEach(WebSocketStompClient::stop); }

    @Test
    void privateOptionsAndDeckReachOnlyTheirOwnerAndSendUsesTheJwtIdentity() throws Exception {
        UUID strangerId = UUID.randomUUID();
        StompSession first = connect(P1), second = connect(P2), outsider = connect(strangerId);
        var own = subscribe(first, "/user/queue/room", P1);
        var opponent = subscribe(second, "/user/queue/room", P2);
        var stranger = subscribe(outsider, "/user/queue/room", strangerId);
        var publicEvents = subscribe(first, "/topic/rooms/0042", P1);
        draws.beginRound(GAME, 1, P1, P2);

        var firstOptions = receive(own);
        var secondOptions = receive(opponent);
        assertEquals("DRAW_OPTIONS", firstOptions.path("type").asText());
        assertEquals("DRAW_OPTIONS", secondOptions.path("type").asText());
        assertNotEquals(firstOptions.path("payload").path("options"), secondOptions.path("payload").path("options"));
        assertEquals("PHASE_CHANGE", receive(publicEvents).path("type").asText());
        String ability = firstOptions.path("payload").path("options").get(0).asText();
        var headers = new StompHeaders();
        headers.setDestination("/app/rooms/0042/draw/pick");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        first.send(headers, ("{\"ability\":\"" + ability + "\",\"playerId\":\"" + P2 + "\"}").getBytes(StandardCharsets.UTF_8));

        var deck = receive(own);
        assertEquals("DECK_UPDATE", deck.path("type").asText());
        assertEquals(3, deck.path("payload").path("deck").size());
        assertEquals(ability, states.find(GAME).orElseThrow().getRoundDraw().players().get(P1).selected());
        assertNull(states.find(GAME).orElseThrow().getRoundDraw().players().get(P2).selected());
        assertFalse(states.find(GAME).orElseThrow().getRoundDraw().completed());
        assertNull(opponent.poll(200, TimeUnit.MILLISECONDS));
        assertNull(stranger.poll(200, TimeUnit.MILLISECONDS));
        assertNull(publicEvents.poll(200, TimeUnit.MILLISECONDS));
    }

    private StompSession connect(UUID id) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        clients.add(client);
        var headers = new StompHeaders();
        var user = new User("player", "hash", "player@example.test");
        user.setId(id);
        headers.set("Authorization", "Bearer " + jwt.generateToken(user));
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() { }).get(5, TimeUnit.SECONDS);
    }

    private BlockingQueue<JsonNode> subscribe(StompSession session, String destination, UUID playerId) {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            public void handleFrame(StompHeaders headers, Object payload) {
                try { received.add(JSON.readTree((byte[]) payload)); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> users.getUser(playerId.toString()) != null
                && users.getUser(playerId.toString()).getSessions().stream().anyMatch(serverSession -> {
                    String actual = destination.startsWith("/user/")
                            ? destination.substring("/user".length()) + "-user" + serverSession.getId() : destination;
                    var message = MessageBuilder.withPayload(new byte[0]).setHeader("simpMessageType", SimpMessageType.MESSAGE)
                            .setHeader("simpDestination", actual).build();
                    return !context.getBean(SimpleBrokerMessageHandler.class).getSubscriptionRegistry()
                            .findSubscriptions(message).isEmpty();
                }));
        return received;
    }

    private JsonNode receive(BlockingQueue<JsonNode> queue) throws InterruptedException {
        var event = queue.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Evento não entregue pela conexão STOMP.");
        return event;
    }
}
