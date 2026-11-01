# Game Module

Este módulo contém os componentes principais relacionados às partidas de Jogo da Velha.

## Estrutura

- `GameController.java` - Endpoints REST para gerenciamento de partidas
- `Game.java` - Entidade de persistência de partidas
- `GameRepository.java` - Repository para acesso a dados de partidas
- `GameDTO.java` - DTO para transferência de dados de partidas
- `service/` - Serviços de gerenciamento de partidas e jogadores
- `logic/` - Validações e regras do jogo

## Responsabilidades

- Gerenciamento de partidas via REST API
- Persistência de dados de partidas
- Coordenação com módulos de serviço e lógica
- Integração com WebSocket para comunicação em tempo real

## Participação em partidas

Um usuário pode ter somente uma participação ativa por vez. Ao reservar uma sala,
`RoomCodeAllocator` verifica se ele já participa de uma partida `IN_PROGRESS` ou
de uma sala nos estados `WAITING` ou `IN_GAME`. Em caso positivo, a reserva é
rejeitada. A verificação e a reserva são executadas na mesma transação, que
serializa as reservas concorrentes.

Essa regra está aplicada ao fluxo de reserva de sala. O módulo ainda não possui
um fluxo implementado para entrar em uma sala existente.

## Presença e reconexão

- A presença (`CONNECTED`/`DISCONNECTED`) fica em chaves Redis separadas do estado da partida.
- As chaves expiram após 300 s por padrão (`GAME_PRESENCE_TTL`) e são renovadas a cada 30 s enquanto houver uma sessão STOMP ativa (`GAME_PRESENCE_REFRESH_INTERVAL`).
- O encerramento da última sessão do jogador atualiza a presença; sessões adicionais mantêm o jogador conectado.
- Heartbeats STOMP auxiliam a detecção de quedas abruptas. Desconectar ou reconectar não altera nem pausa o `turnDeadlineEpochMillis`; apenas o fluxo de timeout controla expiração, contadores e W.O.
- O broker simples e o registro de sessões são locais ao processo; implantação com múltiplas instâncias exige coordenação distribuída, ainda não implementada.

## Notas

Este módulo segue a arquitetura de domínios definida no projeto, onde cada domínio possui suas próprias classes de Controller, Service, Entity, Repository e DTO conforme necessário.
