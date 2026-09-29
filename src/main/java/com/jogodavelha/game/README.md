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

## Notas

Este módulo segue a arquitetura de domínios definida no projeto, onde cada domínio possui suas próprias classes de Controller, Service, Entity, Repository e DTO conforme necessário.
