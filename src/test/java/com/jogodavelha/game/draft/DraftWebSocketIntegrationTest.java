package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.config.*;
import com.jogodavelha.websocket.WebSocketService;
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
import org.springframework.context.annotation.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest(classes = DraftWebSocketIntegrationTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.profiles.active=test", "spring.flyway.enabled=false",
                "jwt.secret=this-is-a-very-long-secret-key-with-at-least-32-bytes", "jwt.expiration=3600000"})
class DraftWebSocketIntegrationTest {
    private static final UUID P1 = UUID.randomUUID(), P2 = UUID.randomUUID();
    private static final UUID X = new UUID(0, 1), O = new UUID(0, 2);
    private static final String TOPIC = "/topic/rooms/0042";
    @LocalServerPort int port;
    @Autowired DraftService drafts;
    @Autowired JwtService jwt;
    @Autowired MutableClock clock;
    @Autowired SimpleBrokerMessageHandler broker;
    @Autowired Completions completions;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            DraftService.class, DraftStateManager.class, DraftEventPublisher.class, WebSocketService.class})
    static class TestApplication {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean JwtService jwtService(JwtProperties properties, MutableClock clock) { return new JwtService(properties, clock); }
        @Bean Completions completions() { return new Completions(); }
        @Bean TaskScheduler scheduler() {
            var scheduler = mock(TaskScheduler.class);
            when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenReturn(mock(ScheduledFuture.class));
            return scheduler;
        }
        @Bean DraftEligibilityProvider eligibility() {
            return player -> new DraftEligibilityProvider.EligibleItems(List.of(X, O), List.of("BOMBA", "ESCUDO", "AMPULHETA"));
        }
        @Bean CharacterRepository characters() {
            var repository = mock(CharacterRepository.class);
            when(repository.findAllById(any())).thenAnswer(invocation -> {
                Iterable<UUID> ids = invocation.getArgument(0);
                List<Character> result = new ArrayList<>();
                for (UUID id : ids) {
                    Character character = new Character(id.equals(X) ? "X" : "O",
                            id.equals(X) ? CharacterSymbol.X : CharacterSymbol.O, 0);
                    character.setId(id);
                    result.add(character);
                }
                return result;
            });
            return repository;
        }
        @Bean @SuppressWarnings("unchecked") RedisTemplate<String, Object> redis(ObjectMapper mapper) {
            var redis = mock(RedisTemplate.class);
            var values = mock(ValueOperations.class);
            Map<String, String> stored = new ConcurrentHashMap<>();
            when(redis.opsForValue()).thenReturn(values);
            when(values.get(anyString())).thenAnswer(invocation -> stored.get(invocation.getArgument(0)));
            when(values.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                    .thenAnswer(invocation -> stored.putIfAbsent(invocation.getArgument(0), invocation.getArgument(1)) == null);
            when(redis.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                    .thenAnswer(invocation -> {
                        String key = ((List<String>) invocation.getArgument(1)).getFirst();
                        long version = Long.parseLong(invocation.getArgument(2));
                        String before = stored.get(key);
                        if (before == null || mapper.readTree(before).path("version").asLong() != version) return 0L;
                        return stored.replace(key, before, invocation.getArgument(3)) ? 1L : 0L;
                    });
            return redis;
        }
    }

    static class MutableClock extends Clock {
        final AtomicLong now = new AtomicLong(System.currentTimeMillis());
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(now.get()); }
    }

    static class Completions {
        final Queue<DraftCompleted> received = new ConcurrentLinkedQueue<>();
        @EventListener public void completed(DraftCompleted event) { received.add(event); }
    }

    @BeforeEach void resetClock() {
        clock.now.set(System.currentTimeMillis());
        completions.received.clear();
    }

    @AfterEach void stopClients() {
        sessions.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
        clients.forEach(WebSocketStompClient::stop);
    }

    @Test
    void bothAuthenticatedPlayersReceiveProgressAndEarlyCompletion() throws Exception {
        var first = subscribe(P1);
        var second = subscribe(P2);
        awaitSubscriptions();
        UUID room = UUID.randomUUID();
        var state = drafts.start(room, "0042", P1, P2);
        drafts.updatePlayer(room, P1, player -> player.withCharacter(X));
        drafts.updatePlayer(room, P1, player -> player.withSkills(List.of("BOMBA", "ESCUDO")));
        drafts.updatePlayer(room, P2, player -> player.withCharacter(O));
        drafts.updatePlayer(room, P2, player -> player.withSkills(List.of("BOMBA", "AMPULHETA")));

        JsonNode last = null;
        for (int index = 0; index < 5; index++) {
            last = receive(first);
            assertEquals(last, receive(second));
            assertEquals("DRAFT_UPDATE", last.path("type").asText());
            assertEquals(state.deadlineEpochMillis(), last.path("payload").path("deadline").asLong());
            assertEquals(3, last.path("payload").size());
            assertFalse(last.toString().contains("BOMBA"));
            assertFalse(last.toString().contains("eligible"));
        }
        assertEquals("X", last.path("payload").path("characters").path("P1").asText());
        assertEquals("O", last.path("payload").path("characters").path("P2").asText());
        assertTrue(last.path("payload").path("ready").path("P1").asBoolean());
        assertTrue(last.path("payload").path("ready").path("P2").asBoolean());
        assertEquals(1, completions.received.size());
        assertFalse(completions.received.element().automatic());
    }

    @Test
    void timeoutPublishesAutomaticCompletionOnlyOnce() throws Exception {
        var first = subscribe(P1);
        var second = subscribe(P2);
        awaitSubscriptions();
        UUID room = UUID.randomUUID();
        var state = drafts.start(room, "0042", P1, P2);
        receive(first);
        receive(second);
        clock.now.set(state.deadlineEpochMillis());
        drafts.expire(room, state.deadlineEpochMillis());
        var result = receive(first);
        assertEquals(result, receive(second));
        var ready = result.path("payload").path("ready");
        assertTrue(ready.path("P1").asBoolean());
        assertTrue(ready.path("P2").asBoolean());
        var characters = result.path("payload").path("characters");
        assertNotEquals(characters.path("P1"), characters.path("P2"));
        assertEquals(1, completions.received.size());
        assertTrue(completions.received.element().automatic());
        drafts.expire(room, state.deadlineEpochMillis());
        assertNull(first.poll(200, TimeUnit.MILLISECONDS));
        assertNull(second.poll(200, TimeUnit.MILLISECONDS));
        assertEquals(1, completions.received.size());
    }

    private BlockingQueue<JsonNode> subscribe(UUID playerId) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        clients.add(client);
        client.setMessageConverter(new MappingJackson2MessageConverter());
        User user = new User("draft-user", "hashed", "draft@example.com");
        user.setId(playerId);
        var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwt.generateToken(user));
        var session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {}).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe(TOPIC, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return JsonNode.class; }
            public void handleFrame(StompHeaders headers, Object payload) {
                received.add((JsonNode) payload);
            }
        });
        return received;
    }

    private void awaitSubscriptions() {
        var message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, TOPIC).build();
        await().atMost(Duration.ofSeconds(5)).until(() -> broker.getSubscriptionRegistry().findSubscriptions(message).size() == 2);
    }

    private JsonNode receive(BlockingQueue<JsonNode> events) throws InterruptedException {
        var event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Evento DRAFT_UPDATE não foi entregue por STOMP.");
        return event;
    }
}
