package com.jogodavelha.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service responsável pelo gerenciamento de mensagens STOMP WebSocket.
 * Utiliza SimpMessagingTemplate para enviar mensagens para clientes conectados.
 */
@Service
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Envia uma mensagem para um tópico específico (broadcast para todos os inscritos).
     *
     * @param topic O tópico de destino (ex: "/topic/game-updates")
     * @param message A mensagem a ser enviada
     */
    public void sendToTopic(String topic, Object message) {
        messagingTemplate.convertAndSend(topic, message);
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
        messagingTemplate.convertAndSendToUser(user, destination, message);
    }
}