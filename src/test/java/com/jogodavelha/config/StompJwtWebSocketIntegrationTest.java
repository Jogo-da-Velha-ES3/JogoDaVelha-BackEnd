package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.game.PlayerSessionRegistry;
import com.jogodavelha.game.service.PlayerService;
import com.jogodavelha.websocket.PlayerConnectionController;
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
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
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

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            PlayerConnectionController.class})
    static class TestApplication {
        @Bean com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
            return new com.fasterxml.jackson.databind.ObjectMapper();
        }
        @Bean JwtService jwtService(JwtProperties properties, java.time.Clock clock) {
            return new JwtService(properties, clock);
        }
        @Bean PlayerService playerService() { return mock(PlayerService.class); }
        @Bean PlayerSessionRegistry playerSessionRegistry() { return mock(PlayerSessionRegistry.class); }
    }

    @LocalServerPort int port;
    @Autowired JwtService jwtService;
    @Autowired PlayerService playerService;
    @Autowired PlayerSessionRegistry playerSessionRegistry;
    private WebSocketStompClient stompClient;

    @AfterEach
    void stopClient() {
        if (stompClient != null) stompClient.stop();
    }

    @Test
    void realStompConnectRequiresJwtAndUsesAuthenticatedIdentityForRoomConnection() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        StompHeaders validHeaders = new StompHeaders();
        validHeaders.add("Authorization", "Bearer " + token());
        StompSession session = stompClient.connectAsync(url(), new WebSocketHttpHeaders(), validHeaders,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);

        assertTrue(session.isConnected());
        session.send("/app/room/0042/connect", null);
        verify(playerService, timeout(3000)).markConnected("0042", USER_ID);
        verify(playerSessionRegistry, timeout(3000)).register(anyString(), eq("0042"), eq(USER_ID));
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

    private String token() {
        User user = new User("integration-user", "hashed-password", "integration@example.com");
        user.setId(USER_ID);
        return jwtService.generateToken(user);
    }

    private String url() { return "ws://localhost:" + port + "/ws"; }

    private void assertConnectRejected(WebSocketStompClient client, StompHeaders headers) {
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> client.connectAsync(url(), new WebSocketHttpHeaders(), headers,
                                new StompSessionHandlerAdapter() {})
                        .get(5, TimeUnit.SECONDS));
        assertNotNull(failure.getCause());
    }

}
