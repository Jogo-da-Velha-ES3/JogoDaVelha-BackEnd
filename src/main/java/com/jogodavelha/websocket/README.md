# WebSocket Module

Este módulo contém o gateway de comunicação em tempo real do jogo utilizando o protocolo STOMP sobre WebSocket.

## Abordagem STOMP

Este projeto utiliza a abordagem STOMP (Simple Text Oriented Messaging Protocol) para comunicação WebSocket, fornecida pelo Spring Boot através do `spring-boot-starter-websocket`. Esta abordagem oferece:

- **Protocolo de mensageria estruturado**: STOMP define um formato de mensagem padronizado com frames (CONNECT, SUBSCRIBE, SEND, MESSAGE)
- **Suporte nativo do Spring**: Integração completa com Spring Messaging e SimpMessagingTemplate
- **Destinos flexíveis**: Suporte a tópicos (broadcast) e filas (point-to-point)
- **Serialização automática**: Conversão automática de objetos Java para JSON e vice-versa

## Configuração

A configuração STOMP é definida em `WebSocketConfig`:

- **Endpoint**: `/ws` - Endpoint WebSocket para conexão STOMP
- **Broker**: `/topic` - Prefixo para tópicos de broadcast
- **Application Prefix**: `/app` - Prefixo para mensagens enviadas para handlers do servidor

## Responsabilidades

- Envio de mensagens em tempo real para clientes conectados
- Broadcast de eventos do jogo para múltiplos inscritos
- Comunicação direta com usuários específicos
- Gerenciamento de destinos de mensagens STOMP

## Componentes

- `WebSocketConfig`: Configuração do STOMP endpoint e message broker
- `WebSocketService`: Service para envio de mensagens via SimpMessagingTemplate
- `GameMessage`: DTO para mensagens do jogo serializadas em JSON

## Padrões de Uso

### Broadcast para Tópico
```java
webSocketService.sendToTopic("/topic/game-updates", gameMessage);
```

### Enviar para Tópico Específico de Jogo
```java
webSocketService.sendToGameTopic(gameId, gameMessage);
// Envia para /topic/game/{gameId}
```

### Enviar para Usuário Específico
```java
webSocketService.sendToUser(username, "/queue/notifications", notification);
```

## Cliente STOMP (Exemplo)

```javascript
const socket = new SockJS('/ws');
const stompClient = Stomp.over(socket);

stompClient.connect({}, function (frame) {
    // Subscribe to game updates
    stompClient.subscribe('/topic/game/' + gameId, function (message) {
        const gameMessage = JSON.parse(message.body);
        // Handle game update
    });

    // Send move to server
    stompClient.send('/app/game/move', {}, JSON.stringify({
        gameId: gameId,
        row: row,
        col: col
    }));
});
```

## Notas

Este módulo atua como gateway de comunicação em tempo real e não persiste dados. Todo o estado persistente deve ser gerenciado pelos módulos service e logic.