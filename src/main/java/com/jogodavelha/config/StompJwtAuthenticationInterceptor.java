package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class StompJwtAuthenticationInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    public StompJwtAuthenticationInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor existingAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (existingAccessor == null || existingAccessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }
        StompHeaderAccessor accessor = existingAccessor;

        List<String> authorizationHeaders = accessor.getNativeHeader(AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            throw new AccessDeniedException("STOMP CONNECT requires a valid Bearer token");
        }

        String authorization = authorizationHeaders.getFirst();
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new AccessDeniedException("STOMP CONNECT requires a valid Bearer token");
        }

        UUID userId = jwtService.extractUserId(authorization.substring(BEARER_PREFIX.length()))
                .orElseThrow(() -> new AccessDeniedException("STOMP CONNECT requires a valid Bearer token"));
        accessor.setUser(() -> userId.toString());
        return message;
    }
}
