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
| `serverTime`   | number (epoch millis) | Instante de publicação definido pelo servidor, em milissegundos desde Unix epoch |

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
  "serverTime": 1792929600000
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

> O tópico é broadcast entre participantes, mas cada inscrição exige sessão autenticada e participação ativa na sala correspondente, conferidas pelo interceptor STOMP usando o `Principal`. O cliente do host continua filtrando `PLAYER_JOINED` por `hostId`.

#### `ROOM_SNAPSHOT`

Publicado em `/topic/rooms/{code}` ao conectar, após a entrada do segundo jogador, nas atualizações do Draft e nas transições de round/partida. O evento usa `{type, payload, serverTime}` e `serverTime` é epoch millis. O payload segue o `RoomSnapshot` do frontend (`code`, `phase`, `players`, `mySlot`, `round`, `suddenDeath`, `board`, `turn`, `myDeck`, `outcome` e campos opcionais do Draft/resultado). No tópico compartilhado `mySlot` é `null`, `myDeck` é `[]` e `drawOptions` é `null`.

Cada jogador também recebe o mesmo `ROOM_SNAPSHOT` individualizado em `/user/queue/room`, com seu `mySlot` e `myDeck`. Durante `ROUND_DRAW`, opções e prazo pendentes são recuperados do estado Redis somente no snapshot privado correspondente.

Para sincronizar sob demanda, envie `SEND /app/rooms/{code}/sync` sem corpo. O backend deriva a identidade do `Principal`, valida participação ativa e responde somente ao solicitante em `/user/queue/room`. Não há broadcast durante sync, preservando os dados privados do outro jogador.

#### `ROOM_CLOSED`

Enviado quando a sala é fechada: o host saiu enquanto ela estava `WAITING` (`POST /rooms/{code}/leave`) ou ela expirou por inatividade. É publicado só depois do commit da transação: saídas rejeitadas (sala inexistente, jogador fora da sala, partida já iniciada) não geram mensagem.

| Campo        | Tipo              | Descrição                                     |
|--------------|-------------------|-----------------------------------------------|
| `type`       | string            | Sempre `ROOM_CLOSED`                          |
| `payload`    | objeto            | Dados do fechamento                           |
| `serverTime` | number (epoch millis) | Instante de publicação definido pelo servidor, em milissegundos desde Unix epoch |

Campos de `payload`:

| Campo      | Tipo          | Descrição                                                       |
|------------|---------------|-----------------------------------------------------------------|
| `roomCode` | string        | Código da sala                                                  |
| `closedBy` | string (UUID) | ID de quem saiu. É `null` quando a sala expira                  |
| `reason`   | string        | `HOST_LEFT` (saída voluntária) ou `EXPIRED` (expiração)         |

Exemplo:

```json
{
  "type": "ROOM_CLOSED",
  "payload": {
    "roomCode": "0042",
    "closedBy": "3f2b8c1e-0000-0000-0000-000000000001",
    "reason": "HOST_LEFT"
  },
  "serverTime": 1792843200000
}
```

Tratamento no cliente:

```javascript
if (event.type === 'ROOM_CLOSED') {
    const { roomCode, closedBy, reason } = event.payload;
    // encerrar a tela da sala e voltar ao lobby;
    // reason === 'EXPIRED' -> avisar que a sala expirou por inatividade
}
```
## Notas

Este módulo atua como gateway de comunicação em tempo real e não persiste dados. Todo o estado persistente deve ser gerenciado pelos módulos service e logic.
