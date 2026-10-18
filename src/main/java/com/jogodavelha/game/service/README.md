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

## Turnos e tempo

- 10 s por turno; 5 s na morte súbita (round 3); 5 s para o jogador sob Ampulheta.
- A expiração é feita no servidor (`TaskScheduler`), sem depender do cliente, e é idempotente:
  o job só age se o prazo (`turnDeadlineEpochMillis`) ainda for o do turno atual.
- Timeout: registra o timeout do jogador, resolve a vitória (`SkillEngine.endAction`) e passa a vez.
- Jogada/habilidade: `endPlayerAction` dentro do mutator do `update`; depois do `update`,
  `publishAndSchedule` publica `TurnChangedEvent` e reagenda o timer.
- Quem iniciar a partida ou um round deve chamar `schedule(gameId, deadline)`; ao encerrar, `cancel(gameId)`.

## Notas

Este módulo coordena a lógica de negócio de alto nível do jogo, interagindo com o módulo logic para validações.