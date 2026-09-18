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

## Notas

Este módulo segue a arquitetura de domínios definida no projeto, onde cada domínio possui suas próprias classes de Controller, Service, Entity, Repository e DTO conforme necessário.

As entidades e DTOs utilizam anotações do Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor) para geração automática de getters, setters e construtores.