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
- **Broker**: `/topic` (broadcast) e `/queue` (mensagens por usuário, ex.: `/user/queue/errors`)
- **Application Prefix**: `/app` - Prefixo para mensagens enviadas para handlers do servidor

## Responsabilidades

- Envio de mensagens em tempo real para clientes conectados
- Broadcast de eventos do jogo para múltiplos inscritos
- Comunicação direta com usuários específicos
- Gerenciamento de destinos de mensagens STOMP
- Eventos por sala (`/topic/rooms/{code}`) e por partida (`/topic/game/{gameId}`)

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

## Tópicos

### `/topic/rooms/{code}`

Eventos de uma sala, identificada pelo código de 4 dígitos. O cliente se inscreve depois de criar a sala (host) ou entrar nela.

#### `PLAYER_JOINED`

Enviado quando o 2º jogador entra na sala, para o host exibir o "MATCH FEITO!". É publicado só depois do commit da transação de entrada: tentativas rejeitadas (sala inexistente, cheia, já iniciada, jogador indisponível) não geram mensagem.

| Campo          | Tipo          | Descrição                                         |
|----------------|---------------|---------------------------------------------------|
| `type`         | string        | Sempre `PLAYER_JOINED`                            |
| `payload`      | objeto        | Dados do jogador que entrou e da sala             |
| `serverTime`   | string (ISO-8601) | Horário de publicação definido pelo servidor |

Campos de `payload`:

| Campo          | Tipo              | Descrição                                         |
|----------------|-------------------|---------------------------------------------------|
| `roomCode`     | string            | Código da sala                                    |
| `guestId`      | string (UUID)     | ID de quem entrou                                 |
| `opponentName` | string            | Username de quem entrou (o adversário do host)    |
| `hostId`       | string (UUID)     | ID do host, para o cliente decidir se deve reagir |
| `hostUsername` | string            | Username do host                                  |
| `guestAvatar`  | string, opcional  | Avatar equipado do convidado (hoje não enviado)   |
| `hostAvatar`   | string, opcional  | Avatar equipado do host (hoje não enviado)        |

> O avatar é um cosmético do inventário e não é o personagem escolhido no ‘Draft’. O avatar equipado ainda não existe, então `guestAvatar` e `hostAvatar` não são enviados por enquanto (valores nulos são omitidos).

Exemplo:

```json
{
  "type": "PLAYER_JOINED",
  "payload": {
    "roomCode": "0042",
    "guestId": "3f2b8c1e-0000-0000-0000-000000000002",
    "opponentName": "guest",
    "hostId": "3f2b8c1e-0000-0000-0000-000000000001",
    "hostUsername": "host"
  },
  "serverTime": "2026-10-25T12:00:00Z"
}
```

Exemplo de inscrição (cliente STOMP):

```javascript
stompClient.subscribe('/topic/rooms/' + roomCode, function (message) {
    const event = JSON.parse(message.body);
    if (event.type === 'PLAYER_JOINED') {
        const { roomCode, guestId, opponentName, hostId } = event.payload;
        // mostrar "MATCH FEITO!" com event.opponentName
    }
});
```

> Atenção: o tópico é de broadcast e, por ora, sem restrição de inscrição. Qualquer cliente que souber o código da sala recebe o evento, inclusive o jogador que entrou; o cliente do host deve filtrar por `hostId`. A restrição por `Principal` depende da BE-040.

## Notas

Este módulo atua como gateway de comunicação em tempo real e não persiste dados. Todo o estado persistente deve ser gerenciado pelos módulos service e logic.
