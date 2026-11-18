package com.jogodavelha.websocket;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSocketServiceTest {

    private final SimpMessagingTemplate messagingTemplate = mock(SimpMessagingTemplate.class);
    private final SimpUserRegistry userRegistry = mock(SimpUserRegistry.class);
    private final MessageConverter messageConverter = mock(MessageConverter.class);
    private WebSocketService service;

    @BeforeEach
    void setUp() {
        service = new WebSocketService(messagingTemplate);
        service.setUserRegistry(userRegistry);
        when(messagingTemplate.getMessageConverter()).thenReturn(messageConverter);
        when(messageConverter.toMessage(any(), any())).thenAnswer(invocation ->
                MessageBuilder.createMessage("converted-payload", invocation.getArgument(1)));
    }

    @Test
    void sendToUserWithoutRegistryEntryUsesNativePath() {
        when(userRegistry.getUser("ghost")).thenReturn(null);

        service.sendToUser("ghost", "/queue/room", "payload");

        verify(messagingTemplate).convertAndSendToUser("ghost", "/queue/room", "payload");
        verify(messagingTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendToUserWithZeroSessionsUsesNativePath() {
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of());
        when(userRegistry.getUser("player")).thenReturn(user);

        service.sendToUser("player", "/queue/room", "payload");

        verify(messagingTemplate).convertAndSendToUser("player", "/queue/room", "payload");
        verify(messagingTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendToUserWithSingleSessionUsesNativePath() {
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of(mock(SimpSession.class)));
        when(userRegistry.getUser("player")).thenReturn(user);

        service.sendToUser("player", "/queue/room", "payload");

        verify(messagingTemplate).convertAndSendToUser("player", "/queue/room", "payload");
        verify(messagingTemplate, never()).send(anyString(), any());
    }

    @Test
    void sendToUserWithMultipleSessionsDeliversToEachSessionExactlyOnce() {
        SimpSession sessionA = mock(SimpSession.class);
        SimpSession sessionB = mock(SimpSession.class);
        when(sessionA.getId()).thenReturn("session-a");
        when(sessionB.getId()).thenReturn("session-b");
        SimpUser user = mock(SimpUser.class);
        when(user.getSessions()).thenReturn(Set.of(sessionA, sessionB));
        when(userRegistry.getUser("player")).thenReturn(user);

        service.sendToUser("player", "/queue/room", "payload");

        verify(messagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());

        ArgumentCaptor<String> destinations = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Message<?>> messages = ArgumentCaptor.forClass(Message.class);
        verify(messagingTemplate, times(2)).send(destinations.capture(), messages.capture());

        assertEquals(Set.of("/queue/room-usersession-a", "/queue/room-usersession-b"),
                Set.copyOf(destinations.getAllValues()));

        Message<?> first = messages.getAllValues().get(0);
        Message<?> second = messages.getAllValues().get(1);
        assertNotSame(first, second, "Cada sessão deve receber uma mensagem própria.");
        assertEquals("converted-payload", first.getPayload());
        assertEquals("converted-payload", second.getPayload());
        for (Message<?> delivered : List.of(first, second)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nativeHeaders =
                    (Map<String, Object>) delivered.getHeaders().get("nativeHeaders");
            assertEquals(List.of("/user/player/queue/room"),
                    nativeHeaders.get(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION));
        }
    }
}
