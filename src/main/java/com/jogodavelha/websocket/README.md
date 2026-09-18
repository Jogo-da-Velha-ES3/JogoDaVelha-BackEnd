# WebSocket Module

Este módulo contém o gateway de comunicação em tempo real do jogo.

## Responsabilidades

- Gerenciamento de conexões WebSocket
- Processamento de mensagens em tempo real
- Broadcast de eventos do jogo para clientes conectados
- Controle de sessões ativas

## Componentes

- `WebSocketHandler`: Processa mensagens e gerencia conexões
- `WebSocketService`: Gerencia sessões e envio de mensagens
- `GameMessage`: DTO para mensagens do jogo

## Notas

Este módulo atua como gateway de comunicação em tempo real e não persiste dados. Todo o estado persistente deve ser gerenciado pelos módulos service e logic.