# Game Service Module

Este módulo contém os serviços de gerenciamento de partidas e jogadores.

## Responsabilidades

- Gerenciamento de partidas (criação, entrada/saída)
- Controle de jogadores nas partidas
- Gerenciamento de turnos
- Coordenação entre diferentes componentes do jogo

## Componentes

- `GameService`: Gerencia partidas
- `PlayerService`: Gerencia jogadores
- `TurnService`: Controla turnos

## Notas

Este módulo coordena a lógica de negócio de alto nível do jogo, interagindo com o módulo logic para validações.