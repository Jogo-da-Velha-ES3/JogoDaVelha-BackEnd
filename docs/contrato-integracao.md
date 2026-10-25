# Contrato de integração com o front

**Propósito:** registrar o comportamento disponível e as propostas de integração entre backend e frontend.
**Público:** time do front.
**Atualizado em:** 24/10/2026.
**Fonte da verdade:** o contrato REST implementado está no Swagger (`/swagger-ui.html` e `/v3/api-docs`). Este documento é a visão voltada ao front e também acompanha o STOMP.

## Legenda de status

- ✅ Disponível na `dev`
- 🚧 Em desenvolvimento (card e data prevista)
- 📝 Planejado / proposta (pode mudar)
- ⚠️ Temporário (existe, mas vai mudar, com o card que muda)
- ❓ a confirmar: falta evidência no código/teste ou decisão do time.

## 1. Convenções gerais

### Endereços e formatos

- Local: `http://localhost:8080` (porta padrão `SERVER_PORT`; Docker publica `${SERVER_PORT}:8080`). Produção: **a definir**; Render ainda não publicado (BE-033, previsão 21/10/2026; deploy BE-033.1, 29–30/10/2026).
- O prefixo `/api` é aplicado por `ApiPrefixConfig` a todos os `@RestController`; ele só se aplica a REST. STOMP usa `/ws`, sem prefixo `/api`.
- Requisições e respostas JSON usam UTF-8. IDs de usuário/sala são UUID; código de sala atualmente é string de quatro dígitos. Datas de erro são `Instant` ISO-8601 UTC. `null` é omitido da serialização.
- Datas de negócio em futuras respostas: ❓ a confirmar; não há contrato implementado que estabeleça formato geral além do timestamp de erro.

### Autenticação e CORS

- Rotas REST protegidas recebem `Authorization: Bearer <token>`. O token vem do login; seu `sub` identifica o UUID do usuário. Validade configurada por `JWT_EXPIRATION` em milissegundos; recomendado `2592000000` (30 dias). Logout é local no cliente: não há endpoint nem revogação.
- Rotas públicas exatas do `SecurityConfig`: `/api/auth/**`, `/api/health`, `/api/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/ws/**`.
- `CORS_ALLOWED_ORIGINS` configura padrões de origem; o default no `application.yml` é `http://localhost:5173`. O `.env.example` e o Compose fornecem também origens Vercel e `https://*.vercel.app` quando usados com o exemplo. Métodos permitidos: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`; headers: `Authorization`, `Content-Type`, `Accept`; credentials não permitidos. WebSocket `/ws` usa a mesma lista de padrões.

### Erro REST (`ApiError`)

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dados inválidos",
  "status": 400,
  "timestamp": "2026-10-15T12:34:56Z",
  "path": "/api/auth/register",
  "fields": { "email": "E-mail inválido" }
}
```

O front deve decidir pelo `code` e apresentar `message`. `fields` só é preenchido para `VALIDATION_ERROR` de validação de campos; outros campos nulos são omitidos. Catálogo observado em `GlobalExceptionHandler`, `JwtAuthenticationEntryPoint` e `ApiAccessDeniedHandler`:

| Status | HTTP | Quando ocorre |
|---|---:|---|
| ✅ `USERNAME_ALREADY_EXISTS` | 409 | Username já cadastrado. |
| ✅ `EMAIL_ALREADY_EXISTS` | 409 | E-mail já cadastrado. |
| ✅ `INVALID_CREDENTIALS` | 401 | Credenciais de login inválidas. |
| ✅ `VALIDATION_ERROR` | 400 | Bean validation ou constraint violation; erro de corpo inclui `fields`. |
| ✅ `INVALID_REQUEST_BODY` | 400 | Corpo JSON ilegível, pelo handler específico. |
| ✅ `NOT_FOUND` | 404 | Rota ou recurso HTTP não encontrado. |
| ✅ `METHOD_NOT_ALLOWED` | 405 | Método HTTP não permitido. |
| ✅ `BAD_REQUEST` | 400 | Parâmetro HTTP obrigatório ausente ou inválido. |
| ✅ ROOM_BAD_REQUEST | 400 | Código da sala inválido ou usuário inválido. |
| ✅ ROOM_NOT_FOUND | 404 | Sala inexistente ou encerrada. | 
| ✅ ROOM_FULL | 409 | Sala já possui dois jogadores. | 
| ✅ ROOM_ALREADY_STARTED | 409 | A partida da sala já foi iniciada. | 
| ✅ ROOM_CONFLICT | 409 | Jogador já está em uma sala ou partida ativa. |
| ✅ `UNAUTHORIZED` | 401 | Rota protegida sem autenticação ou token ausente/inválido. |
| ✅ `FORBIDDEN` | 403 | Acesso negado a recurso protegido. |
| ✅ `INTERNAL_ERROR` | 500 | Exceção inesperada ou erro HTTP 5xx. |

### Erros de habilidade no STOMP

O handler de erro preserva os códigos abaixo em `ApiError`,
enviado ao autor em `/user/queue/errors`, sem `fields` ou `error`.
Não há endpoint REST nem handler público de ativação. O serviço de domínio de ativação
implementa consumo por round nesta branch, mas a exposição STOMP continua pertencendo à BE-023.

| Código | `status` no payload | Uso |
|---|---:|---|
| `SKILL_INVALID_TARGET` | 400 | Jogador ausente ou alvo inválido para a habilidade (posição, ocupação, proprietário ou bloqueio). |
| `SKILL_INVALID_DESTINATION` | 400 | Destino do Tornado ou Troca Rápida ausente quando há opção, fora do tabuleiro, ocupado, bloqueado por Escudo ou não adjacente. |
| `SKILL_SILENCED` | 400 | Jogador sob Silêncio tenta ativar uma habilidade. |
| `SKILL_ALREADY_USED` | 400 | Jogador tenta reutilizar o mesmo código de habilidade no round atual. |
| `SKILL_NOT_IN_DECK` | 400 | Habilidade não pertence ao deck selecionado pelo jogador para o round atual. |

O catálogo de códigos acima corresponde às habilidades da BE-014.1 e BE-014.2 já
implementadas. `NO_VALID_TARGET` e `BLOCKED_BY_FORCE_FIELD` são resultados válidos,
consomem a habilidade e a ação, e não são erros. Ativação válida grava o código estável
`Skill.code`, encerra a ação via `TurnService` e publica a mudança somente após persistir.
Rejeições de fase, jogador da vez ou prazo usam `BAD_REQUEST` (400) no erro STOMP,
com a mensagem da validação existente; reutilização usa `SKILL_ALREADY_USED` (400).
Habilidades são aceitas somente quando fazem parte do deck corrente persistido no estado
Redis, que é definido pelo fluxo de Draft; isso inclui a habilidade sorteada e selecionada.
O fluxo de Draft deve gravar o deck dos dois jogadores antes de iniciar as ações do round.
Até essa integração ser concluída por BE-037/BE-013.1, ativações sem deck configurado
falham de forma segura com `SKILL_NOT_IN_DECK`.

Pedidos inválidos não devem consumir a habilidade ou encerrar o turno: o chamador
precisa validar antes do consumo e não prosseguir com o fim da ação após a exceção.
O motor rejeita sem alterar o estado; a integração com o consumo do deck fica na
BE-015. Os testes locais cobrem seleção do handler, payload e anotação de envio
privado, não entrega por um broker real.

### IDs, enums e habilidades

- `RoomStatus`: `WAITING`, `IN_GAME`, `CLOSED`.
- UUIDs identificam usuários e salas; `code` de sala, no estado atual, é textual com quatro dígitos.
- Nos objetos de habilidade, `code` é o identificador de integração e `name` é somente exibição: `BOMBA`, `ESCUDO`, `TORNADO`, `BURACO_NEGRO`, `AMPULHETA`, `CONGELAR`, `CAMPO_DE_FORCA`, `TROCA_RAPIDA`, `REFORCO`, `SILENCIO`.

## 2. REST

### Resumo

| Método | Caminho                     | Auth           | Sucesso | Status / card |
|--------|-----------------------------|----------------|--------:|---------------|
| GET    | `/api/health`               | Pública        | 200     | ✅ BE-044     |
| POST   | `/api/auth/register`        | Pública        | 201     | ✅ BE-006     |
| POST   | `/api/auth/login`           | Pública        | 200     | ✅ BE-006     |
| POST   | `/api/rooms`                | Bearer exigido | 201     | ✅ BE-009.1   | 
| POST   | `/api/rooms/{code}/join`    | Bearer exigido | 200     | ✅ BE-009.1   |
| POST   | `/api/rooms/{code}/leave`   | Bearer exigido | 204     | ✅ BE-036     |
| GET    | `/api/skills/inventory`     | Bearer exigido | 200     | ✅ BE-041     |
| GET    | `/api/coins/balance`        | Bearer exigido | 200     | ✅ BE-026     | 
| GET    | `/api/characters/inventory` | Bearer exigido | 200     | ✅ BE-028     |

### `GET /api/health`

✅ Disponível na `dev` — BE-044.

Endpoint leve para confirmar disponibilidade. Sem auth. Sem parâmetros ou corpo. Resposta 200:

```json
{"status":"UP","message":"API disponível"}
```

Erros: nenhum erro de domínio documentado; erros HTTP genéricos seguem o catálogo `ApiError`.

### `GET /api/coins/balance`

✅ Disponível na dev — BE-026.

Bearer Token obrigatório.

Retorna o saldo atual de moedas do usuário autenticado.

Resposta 200 (OK):

```json
{"coinsBalance": 150}
```
Erros:

401 UNAUTHORIZED — token ausente ou inválido.
500 INTERNAL_ERROR — erro interno do servidor.

### `POST /api/auth/register`

✅ Disponível na `dev` — BE-006.

Cadastro público. Corpo: `username` string obrigatória, não vazia, 3–15 caracteres; `password` string obrigatória, 6–72 caracteres; `email` string obrigatória, até 254 caracteres, e-mail válido e padrão com domínio/TLD de ao menos duas letras.

```json
{"username":"ana","password":"senha123","email":"ana@example.com"}
```

Sucesso 201 (o DTO declara `player2Id` e `player2Username`, mas Jackson omite seus valores `null`):

```json
{"id":"123e4567-e89b-12d3-a456-426614174000","username":"ana","email":"ana@example.com"}
```

Erros: 400 `VALIDATION_ERROR` (pode conter `fields`); 400 `INVALID_REQUEST_BODY`; 409 `USERNAME_ALREADY_EXISTS`; 409 `EMAIL_ALREADY_EXISTS`; 500 `INTERNAL_ERROR`.

### `POST /api/auth/login`

✅ Disponível na `dev` — BE-006.

Login público. `identifier` string obrigatória, não vazia, até 254 caracteres (e-mail ou username); `password` string obrigatória, não vazia.

```json
{"identifier":"ana","password":"senha123"}
```

Sucesso 200:

```json
{"token":"<jwt>","id":"123e4567-e89b-12d3-a456-426614174000","username":"ana","coinsBalance":0}
```

Erros: 400 `VALIDATION_ERROR`; 400 `INVALID_REQUEST_BODY`; 401 `INVALID_CREDENTIALS`; 500 `INTERNAL_ERROR`.

### `POST /api/rooms`

✅ Disponível na dev — BE-009.1.

Bearer Token obrigatório.

O usuário autenticado pelo JWT é utilizado como criador da sala.

Sem corpo de requisição.

```http
POST /api/rooms
Authorization: Bearer <jwt>
```

Sucesso 201:

```json
{"id":"123e4567-e89b-12d3-a456-426614174001","code":"0042","status":"WAITING","player1Id":"123e4567-e89b-12d3-a456-426614174000","player1Username":"ana"}
```

Erros:

400 ROOM_BAD_REQUEST — usuário inválido ou requisição inválida.
409 ROOM_CONFLICT — jogador já está em sala ou partida ativa.
401 UNAUTHORIZED — token ausente ou inválido.
500 INTERNAL_ERROR — erro interno do servidor.

### `POST /api/rooms/{code}/join`

✅ Disponível na dev — BE-009.1.

Bearer Token obrigatório.

O usuário autenticado pelo JWT entra na sala identificada pelo código informado.

Sem corpo de requisição.

```http
POST /api/rooms/0042/join
Authorization: Bearer <jwt>
```    
Erros:

400 ROOM_BAD_REQUEST — código inválido ou usuário inválido.
404 ROOM_NOT_FOUND — sala inexistente ou encerrada.
409 ROOM_FULL — sala já possui dois jogadores.
409 ROOM_ALREADY_STARTED — partida já iniciada.
409 ROOM_CONFLICT — jogador já está em sala ou partida ativa.
401 UNAUTHORIZED — token ausente ou inválido.
500 INTERNAL_ERROR — erro interno do servidor.

Sucesso 200:
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174001",
  "code": "0042",
  "status": "WAITING",
  "player1Id": "123e4567-e89b-12d3-a456-426614174000",
  "player1Username": "ana",
  "player2Id": "123e4567-e89b-12d3-a456-426614174002",
  "player2Username": "bia"
}
```

Não há anotações Bean Validation nos parâmetros.

### `POST /api/rooms/{code}/leave`

✅ Disponível na `dev` — BE-036.

Bearer Token obrigatório. O usuário autenticado sai da sala; o host de sala `WAITING` fecha a sala.
Sem corpo. Sucesso: 204, sem corpo.

Erros:

- 400 ROOM_BAD_REQUEST — código inválido ou jogador não pertence à sala.
- 404 ROOM_NOT_FOUND — sala inexistente ou já encerrada.
- 409 ROOM_ALREADY_STARTED — partida já iniciada (abandono em partida será tratado como W.O. em outra tarefa).
- 401 UNAUTHORIZED — token ausente ou inválido.
- 500 INTERNAL_ERROR — erro interno.

### `GET /api/skills/inventory`

✅ Disponível na `dev` — BE-041.

Bearer obrigatório; o usuário é identificado pelo JWT. Sem parâmetros nem corpo.
Retorna as habilidades associadas ao usuário autenticado, também usadas como conjunto
elegível para o Draft.

Sucesso 200:
```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174020",
    "name": "Bomba",
    "code": "BOMBA",
    "description": "Remove uma peça adversária",
    "initialSkill": false,
    "offensive": true,
    "priceCoins": 100
  }
]
```

Erros: 401 `UNAUTHORIZED`; 500 `INTERNAL_ERROR`.

### `GET /api/characters/inventory`

✅ Disponível na `dev` — BE-028.

Retorna os personagens visuais associados ao usuário autenticado. Personagens são
cosméticos e não têm poderes. Bearer obrigatório; usuário identificado pelo JWT;
sem parâmetros nem corpo.

Sucesso 200:

```json
[
  {
    "id": "123e4567-e89b-12d3-a456-426614174010",
    "name": "X",
    "symbol": "X",
    "priceCoins": 0
  }
]
```

`symbol` pode ser `X`, `O`, `TRIANGULO`, `QUADRADO`, `ESTRELA` ou `CORACAO`.
Erros: 401 `UNAUTHORIZED`; 500 `INTERNAL_ERROR`.

Pendência: confirmar se X e O são atribuídos a todo jogador (RN-LOJ-01). O endpoint
retorna apenas registros existentes em `user_characters`; compra e equipar estão na BE-029.
### Propostas REST

Payload não definido adiante permanece **payload a definir**; nada abaixo está implementado.

| Proposta | Responsável previsto | Prazo do backlog | Status |
|---|---|---|---|
| `GET /api/me` — perfil, coins e inventário; compõe perfil, saldo e inventários | Rafael (BE-027/026/028/041) | Até 21/10/2026 | 📝 BE-027, BE-026, BE-028, BE-041; rota agregada proposta pelo front, payload a definir. |
| Loja — catálogo e compra | Lorenzo (BE-029) | Até 28/10/2026 | 📝 BE-029; métodos, caminhos e payloads a definir. |
| Saída de sala | Felipe | 20/10/2026 | 📝 BE-036; backlog chama a ação `leave`, payload a definir. |
| Recuperação de senha | Rafael (sugerido) | Sem prazo (reserva fora do plano base) | 📝 BE-006.1; payload a definir. |

## 3. WebSocket / STOMP

### Conexão e operação atual

- Endpoint `/ws`, WebSocket STOMP puro; `WebSocketConfig` não configura SockJS. Prefixo de aplicação `/app`; brokers simples `/topic` e `/queue`; destinos privados usam `/user/queue/...`.
- ✅ `/ws/**` permanece público no handshake HTTP para permitir o upgrade WebSocket. Cada frame STOMP `CONNECT` precisa conter `Authorization: Bearer <jwt>`; o backend valida assinatura e expiração por `JwtService` e rejeita o CONNECT ausente/malformado/inválido. O `sub` UUID validado é o nome do `Principal` associado à sessão e usado nos handlers.
- ✅ Único handler implementado: `SEND /app/room/{code}/connect` sem corpo de identidade (pode ser enviado sem body). **BREAKING (BE-040):** não envie `playerId`; a identidade vem do `Principal` estabelecido no CONNECT. O registro associa sessão, sala e jogador e marca jogador conectado. `PlayerDisconnectionListener` remove associação da sessão e, se conhecida, marca jogador desconectado; sessão desconhecida não produz ação no serviço.
- ✅ Gameplay (BE-023): `SEND /app/game/{gameId}/move` recebe `{ "position": 0 }`; `SEND /app/game/{gameId}/skill` recebe `{ "skillCode": "BOMBA", "targetPosition": 1, "destinationPosition": 2 }` (posições opcionais conforme habilidade). Campos `playerId` enviados pelo cliente são ignorados. A identidade vem do `Principal`. O sucesso não tem ACK de mensagem nesta etapa; turno/resultado de round é publicado como `TurnChangedEvent` interno após persistência, para consumo pela integração de broadcast da BE-024. Destino e payload público final seguem pendentes de alinhamento com o front conforme seção 4.
- Em clientes STOMP.js, passe o token por `connectHeaders`, por exemplo `client.connect({ Authorization: `Bearer ${token}` })` na API legada ou `new Client({ connectHeaders: { Authorization: `Bearer ${token}` } })` na API atual. O cabeçalho é um header nativo STOMP, não um header do handshake HTTP.
- Falha de autenticação encerra/rejeita o CONNECT STOMP; não há sessão anônima e a resposta de falha pode ser um frame STOMP `ERROR`/fechamento, não um `ApiError` REST nem uma mensagem garantida em `/user/queue/errors` (a sessão ainda não foi autenticada).
- ⚠️ O disconnect acima é o comportamento observado; reconexão/snapshot e heartbeat constam em BE-025 (24–27/10).
- Erros são enviados por `@SendToUser` para `/user/queue/errors`, como `ApiError`. Nos handlers genéricos, `code` vem de `HttpStatus.name()` (por exemplo `BAD_REQUEST`, `CONFLICT`, `INTERNAL_SERVER_ERROR`) e existe também `error` com a frase HTTP; isso diverge do catálogo REST. As BE-014.1 e BE-014.2 preservam os quatro códigos de habilidade descritos acima; o ajuste dos demais erros permanece na BE-044.
- A fila `/user/queue/errors` é destinada a sessões autenticadas; não se aplica ao CONNECT rejeitado, que ainda não tem usuário STOMP associado.

### `PLAYER_JOINED` — `/topic/rooms/{code}`

✅ Disponível na `dev` — BE-035.

Quando é enviado: após o commit da entrada do 2º jogador (`POST /api/rooms/{code}/join`). Entradas rejeitadas não geram mensagem.
Quem recebe: todos os inscritos no tópico (broadcast). O cliente do host deve filtrar por `hostId`.
Envelope: `{ "type", "payload", "serverTime" }`. `serverTime` é o instante ISO-8601 definido pelo servidor; os dados do evento ficam em `payload`.

```json
{
  "type": "PLAYER_JOINED",
  "payload": {
    "roomCode": "0042",
    "guestId": "123e4567-e89b-12d3-a456-426614174002",
    "opponentName": "bia",
    "hostId": "123e4567-e89b-12d3-a456-426614174000"
  },
  "serverTime": "2026-10-23T12:00:00Z"
}
```

Compatibilidade: o tópico não restringe a inscrição; qualquer cliente que souber o código da sala recebe o evento (a restrição por `Principal` fica para depois).

### `ROOM_CLOSED` — `/topic/rooms/{code}`

✅ Disponível na `dev` — BE-036.

Quando é enviado: após o commit do fechamento da sala, por saída do host (`HOST_LEFT`) ou expiração de sala `WAITING` sem 2º jogador (`EXPIRED`).
Quem recebe: todos os inscritos no tópico (broadcast).
Envelope: `{ "type", "payload", "serverTime" }`.

### Propostas de destinos e eventos

Todos os itens abaixo são 📝 e não possuem implementação atual. Envelope proposto: `{ "type", "payload", "serverTime" }`. Destino proposto pelo front: `/topic/rooms/{code}`. O backlog para evento de entrada usa `/topic/room/{code}` (singular); resolver a divergência antes da implementação.

**Eventos de sala e jogo — janela solicitada pelo front até 21/10:**

| Evento | Quando / destinatário | Campos previstos | Card / status |
|---|---|---|---|
| `ROOM_SNAPSHOT` | Estado completo ao conectar/reconectar; jogador da sala | payload a definir (BE-025 descreve snapshot com estado e prazo do turno) | BE-025, 27/10; 📝 |
| `PHASE_CHANGE` | Mudança de fase do jogo; sala | payload a definir | BE-023/BE-024; 20/10 e 24/10; 📝 |
| `TURN_START` | Começo de turno; sala | Prazo do turno como timestamp do servidor (BE-024); demais campos a definir | BE-020/BE-024; 15/10 e 24/10; 📝 |
| `BOARD_UPDATE` | Após ação válida; ambos na sala | Prazo como timestamp do servidor, round, placar, flag de morte súbita, estado de cada casa (vazia/P1/P2/bloqueada/congelada/reforçada) e motivo de W.O. ao fim da partida | BE-024, 24/10; 📝 |
| `ROUND_END` | Encerramento de round; sala | payload a definir | BE-023/BE-038; 20/10 e 24/10; 📝 |
| `MATCH_END` | Fim da partida; sala | Placar final, indicação de W.O. e coins creditadas conforme o evento de fim de partida previsto | BE-038 (que nomeia `GAME_OVER`), 24/10; 📝 |
| `ERROR` | Erro associado à ação; somente autor via `/user/queue/errors` segundo handler atual | `ApiError`; formato STOMP atual diverge conforme seção 4 | BE-044, concluído na base; proposta de envelope ainda 📝 |

Envio de jogada solicitado pelo front em `/move`; destino final completo e payload a definir (BE-023, até 20/10). Backlog menciona handlers de jogar/ativar habilidade, sem fixar o destino público final.

**Preparação e habilidades — janela solicitada pelo front até 23/10:**

| Evento | Quando / destinatário | Campos previstos | Card / status |
|---|---|---|---|
| `DRAFT_UPDATE` | Progresso/prontidão do draft; jogadores da sala | Etapas (personagem, habilidades iniciais), pronto do oponente e timer geral de 20s são descritos no BE-037; formato payload a definir | BE-037, 19/10; 📝 |
| `DRAW_OPTIONS` | Sorteio de três opções; somente dono | Três opções e janela de escolha de 8s (BE-013.1) | BE-013.1, 24/10; 📝 |
| `DECK_UPDATE` | Atualização do deck; jogador dono | payload a definir; privado do jogador conforme pedido do front | BE-023/BE-024; 📝 |
| `ABILITY_RESOLVED` | Resolução de habilidade; sala | `result`: `APPLIED`, `NO_VALID_TARGET` ou `BLOCKED_BY_FORCE_FIELD`; demais campos a definir | BE-023, 20/10; 📝 |
| `TURN_TIMEOUT` | Expiração do turno; sala | Timeout e contador previsto em BE-021; payload a definir | BE-020/BE-021; 15/10 e 25/10; 📝 |

📝 **Alvo, destino e resultado de habilidades:** na futura mensagem de ativação,
`targetPosition` indica a peça adversária nas quatro ofensivas, a casa vazia em
Escudo ou a peça própria em Reforço e Troca Rápida. `destinationPosition` indica
o destino do Tornado ou da Troca Rápida. Campo de Força, Ampulheta e Silêncio não
precisam de posição: o motor determina o jogador afetado a partir de quem ativou.
As posições vão de 0 a 15 em ordem de linha: 0–3 na primeira linha, 4–7 na segunda,
8–11 na terceira e 12–15 na quarta. Tornado e Troca Rápida só permitem vizinhança
ortogonal, sem diagonais nem passagem entre bordas. Os três deslocamentos
desconsideram destinos bloqueados por Escudo para quem usa a habilidade.

📝 Em `ABILITY_RESOLVED`, `result: NO_VALID_TARGET` representa habilidade consumida
sem efeito por Reforço ou ausência de destino permitido, com encerramento do turno.
Não é código de erro e não vai ao catálogo de erros. Sem vizinho permitido,
Tornado e Troca Rápida aceitam ausência de destino para retornar esse resultado; se houver opção,
omitir o destino é pedido inválido. Destino informado inválido continua sendo erro.
`APPLIED` indica efeito aplicado. Pedido inválido recebe erro privado, sem consumir
a habilidade ou encerrar o turno. Consumo e encerramento dependem da BE-015;
publicação do evento, da BE-023/BE-024. Nenhum destino novo é definido aqui.

✅ **BE-014.2 (disponível na `dev`):** os efeitos defensivos e de tempo estão implementados no motor local, sem handler público de ativação. Contadores começam em 2 e diminuem após a
conferência de vitória em cada ação, inclusive a de ativação. Silêncio rejeita
habilidades com `SKILL_SILENCED`, sem impedir jogadas de peça. Ampulheta limita
somente o próximo turno do oponente a 5s. Troca Rápida preserva congelamento e
Reforço da peça movida.

📝 `result: BLOCKED_BY_FORCE_FIELD` indica que Campo de Força anulou a primeira
ofensiva válida recebida (Bomba, Tornado, Buraco Negro, Congelar, Ampulheta ou
Silêncio), sem refletir. A proteção é consumida, e o uso da habilidade deve ser
consumido na BE-015. Esse é um resultado, não erro. O motor rejeita Silêncio e
entradas inválidas antes de consumir a proteção; para pedidos válidos, Campo de
Força tem prioridade sobre Reforço ou ausência de destino permitido. Esse novo
resultado é uma extensão proposta para o evento, pendente de revisão com o front
antes de sua publicação na BE-023/BE-024.

Adicional do backlog não incluído na lista inicial do front:

| Evento | Quando / destinatário | Campos previstos | Card / status |
|---|---|---|---|
| `ROOM_CLOSED` | Sala encerrada/expirada; oponente | payload a definir | BE-036, 20/10; ✅ |

## 4. Pendências e decisões em aberto

| Item | Quem decide | Card |
|---|---|---|
| Códigos de erro STOMP (`BAD_REQUEST`/`CONFLICT` etc.) versus códigos REST e campo `error` adicional | Back + front | BE-044 |
| ~~Remoção de `playerId` do payload STOMP e uso do JWT/`Principal`~~ | Implementado (BE-040); breaking, cliente deve enviar `Authorization` no CONNECT e remover `playerId` do corpo | BE-040 |
| Remoção de `playerId` query nos endpoints de sala e uso de JWT — breaking | Back + front; comunicar antes do merge | BE-009.1 |
| OpenAPI e README podem mostrar origens Vercel configuradas, mas o default isolado da aplicação é somente `http://localhost:5173` | Back/infra + front | BE-034 |
| Erro de autenticação ocorre antes de haver usuário STOMP; não é roteado por `/user/queue/errors` | Frame `ERROR`/fechamento do CONNECT; verificar comportamento observado no cliente/broker usado em produção | BE-040 |
| Envelope STOMP comum, conteúdo de eventos e destino final `/move` | Back + front | BE-023/024/035/037/038 |
| URL pública de produção indisponível | Back/infra | BE-033/033.1 |
| Formato de data geral além do timestamp ISO UTC de erro | Back + front | — |
| Tópico `/topic/rooms/{code}` sem restrição de inscrição; restringir por Principal | Back | BE-035 |
| Expiração de salas WAITING: TTL de 10 min (room.waiting.ttl-minutes) e varredura de 60 s; valores a confirmar com o time | Back | BE-036 |
| Saída em sala IN_GAME (W.O.) fora do escopo da BE-036 | Back | BE-036 |

## 5. Como manter este contrato

### Checklist do autor do PR

- [ ] Atualizei este documento na mesma PR de qualquer criação, alteração ou remoção de endpoint REST, destino ou evento STOMP.
- [ ] Conferi controller/handler, DTO, validações, segurança, status, erros e testes; não derivei o contrato apenas do Swagger.
- [ ] Marquei o estado conforme branch e backlog, incluindo card e data quando estiver em andamento/proposta.
- [ ] Avisei o front antes do merge sobre qualquer quebra de compatibilidade e registrei `BREAKING` no changelog.
- [ ] Acrescentei todo código REST/STOMP novo ao catálogo e registrei divergências/itens incertos.

### Checklist do revisor

- [ ] Conferi que o contrato corresponde ao código e aos testes, inclusive auth, validação e erros.
- [ ] Confirmei status, exemplos, campos omitidos e destinos; nenhum campo foi inventado.
- [ ] Confirmei aviso prévio ao front e entrada `BREAKING` quando aplicável.
- [ ] Não aprovo PR de endpoint/destino/evento sem atualização do contrato na mesma PR.

### Modelo copiável: ficha de endpoint REST

```md
### `MÉTODO /api/caminho`

STATUS — card; previsão (se aplicável).
Descrição:
Auth:
Path/query:
Corpo (tipos e restrições):
Sucesso HTTP:
Exemplo de resposta:
Erros HTTP + `code`:
Compatibilidade/pendências:
```

### Modelo copiável: ficha de evento/destino STOMP

```md
### `EVENTO` — `DESTINO`

STATUS — card; previsão (se aplicável).
Quando é enviado:
Quem recebe:
Envelope:
Campos definidos no backlog:
Payload a definir:
Erros/compatibilidade:
```

## 6. Changelog

| Data       | Mudança                                                                                                                                                                                   | Tipo                          | Card/PR                                     |
|------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------------------|---------------------------------------------|
| 21/10/2026 | Documentado o inventário REST de personagens; atualizado o status BE-014.2 e esclarecida a disponibilidade REST de habilidades.                                                           | NOVO                          | BE-028, BE-014.2                            |
| 15/10/2026 | Criação do contrato com o estado atual da dev. CORS, erro único, prefixo `/api` e códigos de habilidade entram como mudanças já disponíveis; rotas REST que ganharam `/api` são breaking. | NOVO; BREAKING (prefixo REST) | BE-034, BE-044; PR docs/contrato-integracao |
| 23/10/2026 | Evento `PLAYER_JOINED` disponível em `/topic/rooms/{code}` com envelope `{type, payload, serverTime}`.                                                                                    | NOVO                          | BE-035; PR feat/room-joined-event           |
| 24/10/2026 | `POST /api/rooms/{code}/leave`, evento `ROOM_CLOSED` e expiração de salas `WAITING` abandonadas.                                                                                          | NOVO                          | BE-036; PR feat/room-leave-expiration       |