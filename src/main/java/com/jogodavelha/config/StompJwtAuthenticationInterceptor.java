package com.jogodavelha.config;

import com.jogodavelha.auth.JwtService;
import com.jogodavelha.room.RoomCodeAllocator;
import com.jogodavelha.room.RoomRepository;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

@Component
public class StompJwtAuthenticationInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private RoomRepository rooms;

    public StompJwtAuthenticationInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Autowired(required = false)
    public void setRooms(RoomRepository rooms) {
        this.rooms = rooms;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor existingAccessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (existingAccessor == null || (existingAccessor.getCommand() != StompCommand.CONNECT && existingAccessor.getCommand() != StompCommand.STOMP)) {
            if (existingAccessor == null || existingAccessor.getCommand() != StompCommand.SUBSCRIBE) return message;
            String destination = existingAccessor.getDestination();
            var principal = existingAccessor.getUser();
            if (destination == null || !destination.matches("/topic/rooms/[0-9]{4}") || principal == null) {
                throw new AccessDeniedException("Inscri\u00e7\u00e3o de sala inv\u00e1lida");
            }
            String code = destination.substring("/topic/rooms/".length());
            UUID playerId;
            try {
                playerId = UUID.fromString(principal.getName());
            } catch (IllegalArgumentException exception) {
                throw new AccessDeniedException("Jogador inv\u00e1lido");
            }
            if (rooms == null || rooms.findActiveMembership(code, RoomCodeAllocator.ACTIVE, playerId).isEmpty()) {
                throw new AccessDeniedException("Jogador n\u00e3o pertence \u00e0 sala");
            }
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
