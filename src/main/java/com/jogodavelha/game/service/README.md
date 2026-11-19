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
- Cada turno permite uma habilidade e uma jogada, em qualquer ordem, sob o mesmo deadline. A primeira ação válida só marca seu indicador; não troca o jogador, reduz efeitos nem reagenda o timer.
- Após cada ação que altera o estado, `TurnService` resolve vitória antes de empate. Efeitos são reduzidos uma vez ao completar ambas as ações ou ao expirar o prazo.
- Timeout sem ação incrementa a contagem; timeout após uma ação válida conclui o turno sem contar inatividade. O terceiro timeout consecutivo sem ação encerra a partida por W.O.
- Jogada/habilidade: `endPlayerAction` roda dentro do mutator do `update`; depois do commit, somente uma troca real de turno publica `TurnChangedEvent` e reagenda o timer.
- Quem iniciar a partida ou um round deve chamar `schedule(gameId, deadline)`; ao encerrar, `cancel(gameId)`.
- No startup e periodicamente, `TurnService` reconcilia timers com os estados Redis: restaura o deadline original, processa prazos vencidos, tenta novamente falhas transitórias do scheduler e reprocessa estados terminais cujo ciclo PostgreSQL falhou.

## Notas

Este módulo coordena a lógica de negócio de alto nível do jogo, interagindo com o módulo logic para validações.
