package com.jogodavelha.websocket;

import com.jogodavelha.telemetry.ActionProfiler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.converter.SmartMessageConverter;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

/**
 * Service responsável pelo gerenciamento de mensagens STOMP WebSocket.
 * Utiliza SimpMessagingTemplate para enviar mensagens para clientes conectados.
 */
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private SimpUserRegistry userRegistry;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Autowired(required = false)
    public void setUserRegistry(SimpUserRegistry userRegistry) {
        this.userRegistry = userRegistry;
    }

    /**
     * Envia uma mensagem para um tópico específico (broadcast para todos os inscritos).
     *
     * @param topic O tópico de destino (ex: "/topic/game-updates")
     * @param message A mensagem a ser enviada
     */
    public void sendToTopic(String topic, Object message) {
        long start = System.nanoTime();
        try {
            messagingTemplate.convertAndSend(topic, message);
        } finally {
            // Mede só a enfileiragem no broker, não a entrega ao cliente.
            ActionProfiler.add("ws.send", start);
        }
    }

    /**
     * Envia uma mensagem para um tópico específico relacionado a um jogo.
     *
     * @param gameId O ID do jogo
     * @param message A mensagem a ser enviada
     */
    public void sendToGameTopic(String gameId, Object message) {
        sendToTopic("/topic/game/" + gameId, message);
    }

    /**
     * Envia uma mensagem para um usuário específico.
     *
     * @param user O nome de usuário do destinatário
     * @param destination O destino da mensagem (ex: "/queue/notifications")
     * @param message A mensagem a ser enviada
     */
    public void sendToUser(String user, String destination, Object message) {
        long start = System.nanoTime();
        try {
            SimpUser simpUser = userRegistry == null ? null : userRegistry.getUser(user);
            if (simpUser == null || simpUser.getSessions().size() <= 1) {
                messagingTemplate.convertAndSendToUser(user, destination, message);
                return;
            }
            String subscribeDestination = "/user/" + user + destination;
            for (SimpSession session : simpUser.getSessions()) {
                SimpMessageHeaderAccessor accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                accessor.setDestination(destination + "-user" + session.getId());
                accessor.setLeaveMutable(true);
                accessor.addNativeHeader(SimpMessageHeaderAccessor.ORIGINAL_DESTINATION, subscribeDestination);
                Message<?> outbound = MessageBuilder.createMessage(message, accessor.getMessageHeaders());
                var converter = messagingTemplate.getMessageConverter();
                Message<?> converted = converter instanceof SmartMessageConverter smart
                        ? smart.toMessage(message, outbound.getHeaders(), null)
                        : converter.toMessage(message, outbound.getHeaders());
                messagingTemplate.send(accessor.getDestination(), converted != null ? converted : outbound);
            }
        } finally {
            ActionProfiler.add("ws.sendUser", start);
        }
    }
}
