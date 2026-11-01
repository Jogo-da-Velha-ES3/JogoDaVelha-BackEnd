package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.game.PlayerConnectionService;
import com.jogodavelha.game.PlayerDisconnectionListener;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import com.jogodavelha.websocket.PlayerConnectionController;
import com.jogodavelha.websocket.PlayerPresenceEventPublisher;
import com.jogodavelha.websocket.RoomSnapshotService;
import com.jogodavelha.websocket.WebSocketService;
import com.jogodavelha.error.WebSocketExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Type;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = StompJwtWebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes",
                "jwt.expiration=3600000",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
        })
class StompJwtWebSocketIntegrationTest {
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OTHER_USER_ID = UUID.randomUUID();

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class,
            StompJwtAuthenticationInterceptor.class, PlayerConnectionController.class,
            PlayerConnectionService.class, PlayerSessionRegistry.class, PlayerDisconnectionListener.class,
            PlayerPresenceEventPublisher.class, WebSocketService.class, WebSocketExceptionHandler.class})
    static class TestApplication {
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
        @Bean JwtService jwtService(JwtProperties properties, java.time.Clock clock) {
            return new JwtService(properties, clock);
        }
        @Bean PlayerService playerService() { return mock(PlayerService.class); }
        @Bean RoomRepository roomRepository() { return mock(RoomRepository.class); }
        @Bean RoomSnapshotService roomSnapshotService() { return mock(RoomSnapshotService.class); }
    }

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired PlayerService playerService;
    @Autowired RoomRepository rooms;
    @Autowired RoomSnapshotService snapshots;
    @Autowired PlayerPresenceEventPublisher presenceEvents;
    @Autowired SimpUserRegistry users;
    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUpRooms() {
        reset(rooms, playerService, snapshots);
        Room room = new Room("0042", user(USER_ID, "integration-user"));
        room.join(user(OTHER_USER_ID, "other-user"));
        when(rooms.findActiveMembership(eq("0042"), eq(RoomCodeAllocator.ACTIVE), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID playerId = invocation.getArgument(2);
                    return USER_ID.equals(playerId) || OTHER_USER_ID.equals(playerId)
                            ? Optional.of(room) : Optional.empty();
                });
    }

    @AfterEach
    void stopClient() {
        if (stompClient != null) stompClient.stop();
    }

    @Test
    void realStompConnectRequiresJwtAndUsesAuthenticatedIdentityForRoomConnection() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders validHeaders = new StompHeaders();
        validHeaders.add("Authorization", "Bearer " + token(USER_ID));
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), validHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        assertTrue(session.isConnected());
        BlockingQueue<com.fasterxml.jackson.databind.JsonNode> errors = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/errors", jsonHandler(errors));
        session.send("/app/rooms/0042/connect", new byte[0]);
        verify(rooms, timeout(3000)).findActiveMembership(eq("0042"), eq(RoomCodeAllocator.ACTIVE), eq(USER_ID));
        var error = errors.poll(250, TimeUnit.MILLISECONDS);
        assertNull(error, error == null ? "" : error.toString());
        verify(playerService, timeout(3000)).markConnected("0042", USER_ID);
        verify(snapshots, timeout(3000)).publish("0042");
        session.disconnect();

        WebSocketStompClient unauthenticatedClient = new WebSocketStompClient(new StandardWebSocketClient());
        try {
            assertConnectRejected(unauthenticatedClient, new StompHeaders());
            StompHeaders invalidHeaders = new StompHeaders();
            invalidHeaders.add("Authorization", "Bearer invalid-token");
            assertConnectRejected(unauthenticatedClient, invalidHeaders);
        } finally {
            unauthenticatedClient.stop();
        }
    }

    @Test
    void presenceEventsAreDeliveredOnlyToTheAuthenticatedOpponentQueue() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        Client p1 = connectClient(USER_ID);
        Client p2 = connectClient(OTHER_USER_ID);
        Client p2Sibling = connectClient(OTHER_USER_ID);
        await().atMost(5, TimeUnit.SECONDS).until(() ->
                hasQueueSubscription(USER_ID, 1) && hasQueueSubscription(OTHER_USER_ID, 2));

        presenceEvents.disconnected(new PlayerSessionRegistry.Entry("0042", OTHER_USER_ID, "P2", USER_ID));

        var delivered = receive(p1.messages());
        assertEquals("PLAYER_DISCONNECTED", delivered.path("type").asText());
        assertEquals("P2", delivered.path("payload").path("slot").asText());
        assertEquals("0042", delivered.path("payload").path("roomCode").asText());
        assertTrue(delivered.path("serverTime").asLong() > 0);
        assertNull(p2.messages().poll(250, TimeUnit.MILLISECONDS));
        assertNull(p2Sibling.messages().poll(250, TimeUnit.MILLISECONDS));

        presenceEvents.reconnected(new PlayerSessionRegistry.Entry("0042", USER_ID, "P1", OTHER_USER_ID));

        var reverse = receive(p2.messages());
        assertEquals("PLAYER_RECONNECTED", reverse.path("type").asText());
        assertEquals("P1", reverse.path("payload").path("slot").asText());
        assertNull(p1.messages().poll(250, TimeUnit.MILLISECONDS));

        p1.session().disconnect();
        p2.session().disconnect();
        p2Sibling.session().disconnect();
    }

    private Client connectClient(UUID userId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token(userId));
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), headers,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
        BlockingQueue<com.fasterxml.jackson.databind.JsonNode> messages = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/room", jsonHandler(messages));
        return new Client(session, messages);
    }

    private org.springframework.messaging.simp.stomp.StompFrameHandler jsonHandler(
            BlockingQueue<com.fasterxml.jackson.databind.JsonNode> messages) {
        return new org.springframework.messaging.simp.stomp.StompFrameHandler() {
            @Override public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            @Override public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    messages.add(new com.fasterxml.jackson.databind.ObjectMapper().readTree((byte[]) payload));
                } catch (Exception exception) {
                    throw new AssertionError("Não foi possível ler o evento STOMP", exception);
                }
            }
        };
    }

    private boolean hasQueueSubscription(UUID userId, int minimumSessions) {
        var user = users.getUser(userId.toString());
        return user != null && user.getSessions().stream()
                .filter(session -> !session.getSubscriptions().isEmpty())
                .count() >= minimumSessions;
    }

    private com.fasterxml.jackson.databind.JsonNode receive(BlockingQueue<com.fasterxml.jackson.databind.JsonNode> messages)
            throws InterruptedException {
        com.fasterxml.jackson.databind.JsonNode message = messages.poll(5, TimeUnit.SECONDS);
        assertNotNull(message, "O evento STOMP não foi entregue ao destinatário.");
        return message;
    }

    private String token(UUID userId) {
        return jwtService.generateToken(user(userId,
                userId.equals(USER_ID) ? "integration-user" : "other-user"));
    }

    private static User user(UUID id, String username) {
        User user = new User(username, "hashed-password", username + "@example.com");
        user.setId(id);
        return user;
    }

    private String url() { return "ws://localhost:" + port + "/ws"; }

    private record Client(StompSession session,
                          BlockingQueue<com.fasterxml.jackson.databind.JsonNode> messages) {}

    private void assertConnectRejected(WebSocketStompClient client, StompHeaders headers) {
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> client.connectAsync(url(), new WebSocketHttpHeaders(), headers,
                                new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));
        assertNotNull(failure.getCause());
    }

}
