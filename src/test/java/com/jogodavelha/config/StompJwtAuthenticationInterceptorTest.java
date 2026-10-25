package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.auth.User;
import com.jogodavelha.room.RoomRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StompJwtAuthenticationInterceptorTest {
    private static final String SECRET = "this-is-a-very-long-secret-key-with-at-least-32-bytes";
    private static final Instant NOW = Instant.parse("2026-10-18T12:00:00Z");
    private static final UUID USER_ID = UUID.randomUUID();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private JwtService jwtService;
    private StompJwtAuthenticationInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, 3600000L), clock);
        interceptor = new StompJwtAuthenticationInterceptor(jwtService);
    }

    @Test
    void validConnectAssociatesSubjectAsPrincipal() {
        StompHeaderAccessor accessor = connect("Bearer " + validToken());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(message, channel());
        StompHeaderAccessor authenticatedAccessor = StompHeaderAccessor.wrap(authenticated);
        assertEquals(USER_ID.toString(), authenticatedAccessor.getUser().getName());
    }

    @Test
    void validStompAssociatesSubjectAsPrincipal() {
        StompHeaderAccessor accessor = stomp("Bearer " + validToken());
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> authenticated = interceptor.preSend(message, channel());
        StompHeaderAccessor authenticatedAccessor = StompHeaderAccessor.wrap(authenticated);
        assertEquals(USER_ID.toString(), authenticatedAccessor.getUser().getName());
    }

    @Test
    void subscriptionRequiresMembershipInTheRequestedActiveRoom() {
        RoomRepository rooms = mock(RoomRepository.class);
        interceptor.setRooms(rooms);
        when(rooms.findActiveMembership(eq("0042"), anyList(), eq(USER_ID)))
                .thenReturn(java.util.Optional.empty());
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/rooms/0042");
        accessor.setUser(() -> USER_ID.toString());
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel()));
        verify(rooms).findActiveMembership(eq("0042"), anyList(), eq(USER_ID));
    }

    @Test
    void authenticatedSessionCanSubscribeToItsPrivateErrorsQueue() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/errors");
        accessor.setUser(() -> USER_ID.toString());
        accessor.setLeaveMutable(true);
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertSame(message, interceptor.preSend(message, channel()));
    }

    @Test
    void privateErrorsQueueStillRequiresAuthentication() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/user/queue/errors");
        accessor.setLeaveMutable(true);
        assertRejected(accessor);
    }

    @Test
    void missingMalformedInvalidExpiredAndWronglySignedTokensAreRejected() {
        assertRejected(connect(null));
        assertRejected(connect("Bearer"));
        assertRejected(connect("Bearer "));
        assertRejected(connect("Basic " + validToken()));
        assertRejected(connect("Bearer not-a-jwt"));
        assertRejected(connect("Bearer " + token(NOW.minusSeconds(120), NOW.minusSeconds(60), SECRET)));
        assertRejected(connect("Bearer " + token(NOW, NOW.plusSeconds(60),
                "different-secret-key-with-at-least-32-bytes")));

        assertRejected(stomp(null));
        assertRejected(stomp("Bearer"));
        assertRejected(stomp("Bearer "));
        assertRejected(stomp("Basic " + validToken()));
        assertRejected(stomp("Bearer not-a-jwt"));
        assertRejected(stomp("Bearer " + token(NOW.minusSeconds(120), NOW.minusSeconds(60), SECRET)));
        assertRejected(stomp("Bearer " + token(NOW, NOW.plusSeconds(60),
                "different-secret-key-with-at-least-32-bytes")));
    }

    private void assertRejected(StompHeaderAccessor accessor) {
        Message<?> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        assertThrows(AccessDeniedException.class, () -> interceptor.preSend(message, channel()));
    }

    private String validToken() {
        User user = new User("testuser", "hashed-password", "test@example.com");
        user.setId(USER_ID);
        return jwtService.generateToken(user);
    }

    private String token(Instant issuedAt, Instant expiresAt, String secret) {
        return Jwts.builder().subject(USER_ID.toString()).issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt)).signWith(Keys.hmacShaKeyFor(secret.getBytes())).compact();
    }

    private StompHeaderAccessor connect(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private StompHeaderAccessor stomp(String authorization) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.STOMP);
        if (authorization != null) accessor.addNativeHeader("Authorization", authorization);
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private MessageChannel channel() {
        return new MessageChannel() {
            @Override public boolean send(Message<?> message) { return true; }
            @Override public boolean send(Message<?> message, long timeout) { return true; }
        };
    }
}
