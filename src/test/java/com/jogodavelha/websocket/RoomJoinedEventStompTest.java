package com.jogodavelha.websocket;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.config.*;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.room.PlayerJoinedRoomEvent;
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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@SpringBootTest(classes = RoomJoinedEventStompTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes",
                "jwt.expiration=3600000",
                "spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
        })
class RoomJoinedEventStompTest {
    private static final UUID HOST_ID = UUID.randomUUID();
    private static final UUID GUEST_ID = UUID.randomUUID();

    @Configuration
    @EnableTransactionManagement
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            WebSocketService.class, RoomEventPublisher.class})
    static class TestApplication {
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
        @Bean JwtService jwtService(JwtProperties properties, java.time.Clock clock) {
            return new JwtService(properties, clock);
        }
        @Bean PlayerService playerService() { return mock(PlayerService.class); }
        @Bean PlayerSessionRegistry playerSessionRegistry() { return mock(PlayerSessionRegistry.class); }
        @Bean PlatformTransactionManager transactionManager() {
            return new org.springframework.transaction.support.AbstractPlatformTransactionManager() {
                protected Object doGetTransaction() { return new Object(); }
                protected void doBegin(Object t, org.springframework.transaction.TransactionDefinition d) {}
                protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus s) {}
                protected void doRollback(org.springframework.transaction.support.DefaultTransactionStatus s) {}
            };
        }
    }

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired org.springframework.context.ApplicationEventPublisher events;
    @Autowired PlatformTransactionManager transactionManager;
    private WebSocketStompClient stompClient;

    @AfterEach
    void stopClient() {
        if (stompClient != null) stompClient.stop();
    }

    @Test
    void hostReceivesPlayerJoinedAfterCommit() throws Exception {
        BlockingQueue<Map<String, Object>> received = subscribe("0042");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> events.publishEvent(event("0042")));

        Map<String, Object> message = received.poll(5, TimeUnit.SECONDS);
        assertNotNull(message);
        assertEquals("PLAYER_JOINED", message.get("type"));
        assertNotNull(message.get("serverTime"));
        Map<String, Object> payload = (Map<String, Object>) message.get("payload");
        assertNotNull(payload);
        assertEquals("0042", payload.get("roomCode"));
        assertEquals("guest-user", payload.get("opponentName"));
        assertEquals(GUEST_ID.toString(), payload.get("guestId"));
        assertEquals(HOST_ID.toString(), payload.get("hostId"));
    }

    @Test
    void noMessageWhenTransactionRollsBack() throws Exception {
        BlockingQueue<Map<String, Object>> received = subscribe("0043");

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            events.publishEvent(event("0043"));
            status.setRollbackOnly();
        });

        assertNull(received.poll(1500, TimeUnit.MILLISECONDS));
    }

    private PlayerJoinedRoomEvent event(String code) {
        return new PlayerJoinedRoomEvent(code, UUID.randomUUID(), GUEST_ID, "guest-user", HOST_ID, "host-user");
    }

    @SuppressWarnings("unchecked")
    private BlockingQueue<Map<String, Object>> subscribe(String code) throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        stompClient.setDefaultHeartbeat(new long[]{0, 0}); // opcional
        StompHeaders connectHeaders = new StompHeaders();
        connectHeaders.add("Authorization", "Bearer " + token());
        StompSession session = stompClient.connectAsync("ws://localhost:" + port + "/ws",
                        new WebSocketHttpHeaders(), connectHeaders, new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe("/topic/rooms/" + code, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return Map.class; }
            public void handleFrame(StompHeaders headers, Object payload) { queue.add((Map<String, Object>) payload); }
        });
        Thread.sleep(500); // dá tempo de o broker registrar a inscrição
        return queue;
    }

    private String token() {
        User user = new User("host-user", "hash", "host@example.com");
        user.setId(HOST_ID);
        return jwtService.generateToken(user);
    }
}
