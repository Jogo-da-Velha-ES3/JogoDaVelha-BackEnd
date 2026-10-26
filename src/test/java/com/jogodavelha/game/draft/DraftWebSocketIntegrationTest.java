package com.jogodavelha.game.draft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.character.Character;
import com.jogodavelha.character.CharacterRepository;
import com.jogodavelha.character.CharacterSymbol;
import com.jogodavelha.config.*;
import com.jogodavelha.error.WebSocketExceptionHandler;
import com.jogodavelha.room.Room;
import com.jogodavelha.room.RoomRepository;
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
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
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
    @Autowired DraftStateManager draftStates;
    @Autowired RoomRepository rooms;
    @Autowired SimpUserRegistry userRegistry;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("jwtService") JwtService jwt;
    @Autowired MutableClock clock;
    @Autowired org.springframework.context.ApplicationContext applicationContext;
    @Autowired Completions completions;
    @Autowired SimpMessagingTemplate simpMessagingTemplate;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("webSocketServiceSpy") WebSocketService webSocketService;
    private final List<WebSocketStompClient> clients = new ArrayList<>();
    private final List<StompSession> sessions = new ArrayList<>();
    private final List<String> connectionErrors = new ArrayList<>();
    private final List<String> transportErrors = new ArrayList<>();
    private final UUID selectionRoomId = UUID.randomUUID();

    @Configuration
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class,
            DataRedisAutoConfiguration.class, DataRedisRepositoriesAutoConfiguration.class})
    @EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
    @Import({WebSocketConfig.class, SecurityConfig.class, StompJwtAuthenticationInterceptor.class,
            DraftService.class, DraftStateManager.class, DraftEventPublisher.class,
            DraftAbilitiesController.class, DraftAbilitiesService.class, WebSocketExceptionHandler.class})
    static class TestApplication {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper(); }
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
        @Bean JwtService jwtService(JwtProperties properties, MutableClock clock) { return new JwtService(properties, clock); }
        @Bean Completions completions() { return new Completions(); }
        @Bean RoomRepository rooms() {
            var repository = mock(RoomRepository.class);
            when(repository.findActiveMembership(eq("0042"), anyList(), any(UUID.class)))
                    .thenReturn(Optional.of(mock(Room.class)));
            return repository;
        }
        @Bean @Primary TaskScheduler turnTaskScheduler() {
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
        @Bean WebSocketService webSocketServiceSpy(SimpMessagingTemplate template) {
            return spy(new WebSocketService(template));
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
        connectionErrors.clear();
        transportErrors.clear();
        // Verificação 1: Confirmar que o bean SimpMessagingTemplate existe no contexto
        assertNotNull(simpMessagingTemplate, "SimpMessagingTemplate não foi injetado - infraestrutura WebSocket não está configurada");
        // Resetar o spy do WebSocketService
        reset(webSocketService);
        reset(rooms);
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(selectionRoomId);
        when(rooms.findActiveMembership(eq("0042"), anyList(), any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID player = invocation.getArgument(2);
                    return player.equals(P1) || player.equals(P2) ? Optional.of(room) : Optional.empty();
                });
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

        // Verificação 2: Verificar se o DraftEventPublisher realmente envia
        verify(webSocketService, timeout(5000).atLeastOnce())
                .sendToTopic(eq("/topic/rooms/0042"), any());

        // Verificação 3: Observar o canal de saída do broker
        var broker = applicationContext.getBean(SimpleBrokerMessageHandler.class);
        var message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, TOPIC).build();
        var subscriptions = broker.getSubscriptionRegistry().findSubscriptions(message);
        System.out.println("Número de inscrições encontradas: " + subscriptions.size());
        assertEquals(2, subscriptions.size(), "Broker deve ter 2 inscrições");

        // Verificar se há erros de conexão ou transporte
        assertTrue(connectionErrors.isEmpty(), "Erros de conexão STOMP: " + connectionErrors);
        assertTrue(transportErrors.isEmpty(), "Erros de transporte: " + transportErrors);

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

    @Test
    void stompSelectionPersistsSkillsFromPrincipalAndNotifiesBothPlayers() throws Exception {
        var first = subscribe(P1);
        var second = subscribe(P2);
        awaitSubscriptions();
        drafts.start(selectionRoomId, "0042", P1, P2);
        drafts.updatePlayer(selectionRoomId, P1, player -> player.withCharacter(X));
        drafts.updatePlayer(selectionRoomId, P2, player -> player.withCharacter(O));
        for (int index = 0; index < 3; index++) {
            receive(first);
            receive(second);
        }

        sendAbilities(sessions.get(0), "{\"abilities\":[\"BOMBA\",\"ESCUDO\"],\"playerId\":\"" + P2 + "\"}");
        var progress = receive(first);
        assertEquals(progress, receive(second));
        assertTrue(progress.path("payload").path("ready").path("P1").asBoolean());
        assertFalse(progress.path("payload").path("ready").path("P2").asBoolean());
        var stored = draftStates.find(selectionRoomId).orElseThrow();
        assertEquals(List.of("BOMBA", "ESCUDO"), stored.player1().initialSkills());
        assertTrue(stored.player2().initialSkills().isEmpty());
        assertFalse(progress.toString().contains("BOMBA"));

        sendAbilities(sessions.get(1), "{\"abilities\":[\"AMPULHETA\",\"ESCUDO\"]}");
        var completed = receive(first);
        assertEquals(completed, receive(second));
        assertTrue(completed.path("payload").path("ready").path("P2").asBoolean());
        assertTrue(draftStates.find(selectionRoomId).orElseThrow().completed());
        assertEquals(1, completions.received.size());
    }

    @Test
    void invalidSelectionsReturnPrivateErrorsWithoutChangingDraft() throws Exception {
        var first = subscribe(P1);
        var second = subscribe(P2);
        awaitSubscriptions();
        var errors = subscribeErrors(sessions.get(0), P1);
        var opponentErrors = subscribeErrors(sessions.get(1), P2);
        drafts.start(selectionRoomId, "0042", P1, P2);
        receive(first);
        receive(second);
        assertSelectionError(errors, "{\"abilities\":[\"BOMBA\",\"ESCUDO\"]}", "BAD_REQUEST");

        drafts.updatePlayer(selectionRoomId, P1, player -> player.withCharacter(X));
        receive(first);
        receive(second);
        for (String json : List.of("{\"abilities\":[\"BOMBA\"]}",
                "{\"abilities\":[\"BOMBA\",\"BOMBA\"]}", "{\"abilities\":null}",
                "{\"abilities\":[\"BOMBA\",\"SILENCIO\"]}")) {
            assertSelectionError(errors, json, "BAD_REQUEST");
        }
        clock.now.set(draftStates.find(selectionRoomId).orElseThrow().deadlineEpochMillis());
        assertSelectionError(errors, "{\"abilities\":[\"BOMBA\",\"ESCUDO\"]}", "CONFLICT");
        when(rooms.findActiveMembership(eq("0042"), anyList(), eq(P1))).thenReturn(Optional.empty());
        assertSelectionError(errors, "{\"abilities\":[\"BOMBA\",\"ESCUDO\"]}", "FORBIDDEN");
        assertNull(first.poll(200, TimeUnit.MILLISECONDS));
        assertNull(second.poll(200, TimeUnit.MILLISECONDS));
        assertNull(opponentErrors.poll(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void selectionBeforeDraftStartsReturnsPrivateConflict() throws Exception {
        var publicEvents = subscribe(P1);
        var errors = subscribeErrors(sessions.get(0), P1);

        sendAbilities(sessions.get(0), "{\"abilities\":[\"BOMBA\",\"ESCUDO\"]}");

        assertEquals("CONFLICT", receive(errors).path("code").asText());
        assertTrue(draftStates.find(selectionRoomId).isEmpty());
        assertNull(publicEvents.poll(200, TimeUnit.MILLISECONDS));
    }

    private void assertSelectionError(BlockingQueue<JsonNode> errors, String json, String code) throws Exception {
        DraftState before = draftStates.find(selectionRoomId).orElseThrow();
        sendAbilities(sessions.get(0), json);
        JsonNode error = receive(errors);
        assertEquals(code, error.path("code").asText());
        assertEquals("/app/rooms/0042/draft/abilities", error.path("path").asText());
        assertEquals(before, draftStates.find(selectionRoomId).orElseThrow());
    }

    private void sendAbilities(StompSession session, String json) {
        var headers = new StompHeaders();
        headers.setDestination("/app/rooms/0042/draft/abilities");
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        session.send(headers, json.getBytes(StandardCharsets.UTF_8));
    }

    private BlockingQueue<JsonNode> subscribeErrors(StompSession session, UUID playerId) {
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe("/user/queue/errors", new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            public void handleFrame(StompHeaders headers, Object payload) {
                try { received.add(new ObjectMapper().readTree((byte[]) payload)); }
                catch (Exception exception) { throw new AssertionError(exception); }
            }
        });
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            var user = userRegistry.getUser(playerId.toString());
            if (user == null) return false;
            return user.getSessions().stream().anyMatch(serverSession -> {
                var message = MessageBuilder.withPayload(new byte[0])
                        .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                        .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, "/queue/errors-user" + serverSession.getId()).build();
                return !applicationContext.getBean(SimpleBrokerMessageHandler.class)
                        .getSubscriptionRegistry().findSubscriptions(message).isEmpty();
            });
        });
        return received;
    }

    private BlockingQueue<JsonNode> subscribe(UUID playerId) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        clients.add(client);
        // Não usar conversor personalizado - deixar o padrão receber byte[]
        User user = new User("draft-user", "hashed", "draft@example.com");
        user.setId(playerId);
        var headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + jwt.generateToken(user));

        // Verificação 4: Capturar erros no cliente STOMP
        var handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                System.out.println("Cliente conectado com sucesso: " + playerId);
            }

            @Override
            public void handleException(StompSession session, StompCommand command, StompHeaders headers, byte[] payload, Throwable exception) {
                String error = "STOMP Exception - Command: " + command + ", Error: " + exception.getMessage();
                connectionErrors.add(error);
                System.err.println(error);
                exception.printStackTrace();
            }

            @Override
            public void handleTransportError(StompSession session, Throwable exception) {
                String error = "Transport Error - Error: " + exception.getMessage();
                transportErrors.add(error);
                System.err.println(error);
                exception.printStackTrace();
            }
        };

        var session = client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                handler).get(5, TimeUnit.SECONDS);
        sessions.add(session);
        BlockingQueue<JsonNode> received = new LinkedBlockingQueue<>();
        session.subscribe(TOPIC, new StompFrameHandler() {
            public Type getPayloadType(StompHeaders headers) { return byte[].class; }
            public void handleFrame(StompHeaders headers, Object payload) {
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    received.add(mapper.readTree((byte[]) payload));
                } catch (Exception e) {
                    System.err.println("Erro ao deserializar mensagem: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        });
        return received;
    }

    private void awaitSubscriptions() {
        var message = MessageBuilder.withPayload(new byte[0])
                .setHeader(SimpMessageHeaderAccessor.MESSAGE_TYPE_HEADER, org.springframework.messaging.simp.SimpMessageType.MESSAGE)
                .setHeader(SimpMessageHeaderAccessor.DESTINATION_HEADER, TOPIC).build();
        await().atMost(Duration.ofSeconds(5)).until(() -> applicationContext.getBean(SimpleBrokerMessageHandler.class)
                .getSubscriptionRegistry().findSubscriptions(message).size() == 2);
    }

    private JsonNode receive(BlockingQueue<JsonNode> events) throws InterruptedException {
        var event = events.poll(5, TimeUnit.SECONDS);
        assertNotNull(event, "Evento DRAFT_UPDATE não foi entregue por STOMP.");
        return event;
    }
}
