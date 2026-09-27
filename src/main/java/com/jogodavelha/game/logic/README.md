# Game Logic Module

Este módulo contém a lógica de negócio e as regras do Jogo da Velha.

## Responsabilidades

- Validação de jogadas conforme as regras do jogo
- Gerenciamento do estado das partidas
- Verificação de condições de vitória e empate
- Controle de turnos e transições de estado

## Componentes

- `GameValidator`: Valida as regras do jogo
- `GameStateManager`: Gerencia o estado das partidas
- `MoveValidator`: Valida movimentos específicos

## Integração com WebSocket STOMP

Este módulo se integra com o sistema de WebSocket via STOMP para notificações em tempo real:

### Como Enviar Atualizações de Jogo

Após processar jogadas ou mudanças de estado, use o `WebSocketService` para notificar os clientes:

```java
@Service
public class GameService {
    private final WebSocketService webSocketService;

    public void processMove(String gameId, String playerId, Move move) {
        // Processa a jogada (validação, atualização de estado, etc.)
        // ...

        // Notifica todos os clientes conectados ao jogo
        GameMessage message = new GameMessage(
            "MOVE",
            gameId,
            playerId,
            move
        );
        webSocketService.sendToGameTopic(gameId, message);
    }
}
```

### Tipos de Mensagens Suportados

- `MOVE`: Notificação de uma jogada realizada
- `GAME_UPDATE`: Atualização completa do estado do jogo
- `PLAYER_JOINED`: Novo jogador entrou no jogo
- `PLAYER_LEFT`: Jogador saiu do jogo
- `GAME_OVER`: Notificação de fim de jogo (vitória ou empate)
- `ERROR`: Mensagens de erro para o cliente

### Destinos STOMP

- `/topic/game/{gameId}`: Tópico para broadcast de atualizações do jogo
- `/topic/game-updates`: Tópico geral para atualizações do sistema
- `/queue/notifications`: Fila para notificações individuais

## Notas

Este é o módulo oficial de validação e regras do jogo. Todas as validações devem ocorrer aqui. A comunicação em tempo real é delegada ao módulo WebSocket que utiliza o protocolo STOMP.