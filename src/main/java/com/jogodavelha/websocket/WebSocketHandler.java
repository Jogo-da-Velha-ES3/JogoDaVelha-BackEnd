package com.jogodavelha.websocket;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.CloseStatus;

/**
 * Handler responsável pelo processamento de mensagens WebSocket.
 * Gerencia conexões, mensagens e desconexões de clientes em tempo real.
 */
@Component
public class WebSocketHandler implements org.springframework.web.socket.WebSocketHandler {
    
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        // Lógica quando uma conexão é estabelecida
    }
    
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        // Lógica de processamento de mensagens
    }
    
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        // Lógica de tratamento de erros de transporte
    }
    
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
        // Lógica quando uma conexão é fechada
    }
    
    @Override
    public boolean supportsPartialMessages() {
        return false;
    }
}