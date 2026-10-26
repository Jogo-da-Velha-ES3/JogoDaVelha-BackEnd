# Contrato de integração com o front

**Propósito:** registrar o comportamento disponível e as propostas de integração entre backend e frontend.
**Público:** time do front.
**Atualizado em:** 25/10/2026.
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

| Método | Caminho | Auth | Sucesso | Status / card |
|---|---|---|---:|---|
| GET | `/api/health` | Pública | 200 | ✅ BE-044 |
| POST | `/api/auth/register` | Pública | 201 | ✅ BE-006 |
| POST | `/api/auth/login` | Pública | 200 | ✅ BE-006 |
| POST | `/api/rooms` | Bearer exigido | 201 | ✅ BE-009.1 | 
| POST | `/api/rooms/{code}/join` | Bearer exigido | 200 | ✅ BE-009.1 |
| POST | `/api/rooms/{code}/leave` | Bearer exigido | 204 | ✅ BE-036 |
| GET | `/api/skills/inventory` | Bearer exigido | 200 | ✅ BE-041 |
| GET | `/api/coins/balance` | Bearer exigido | 200 | ✅ BE-026 |
| GET | `/api/characters/inventory` | Bearer exigido | 200 | ✅ BE-028 |
| GET | `/api/profile` | Bearer exigido | 200 | ✅ BE-027 |
| GET | `/api/profile/stats` | Bearer exigido | 200 | ✅ BE-027 |
| PATCH | `/api/profile/username` | Bearer exigido | 204 | ✅ BE-027 |
| GET | `/api/profile/history` | Bearer exigido | 200 | ✅ BE-027 |

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
Erros:

400 ROOM_BAD_REQUEST — código inválido ou usuário inválido.
404 ROOM_NOT_FOUND — sala inexistente ou encerrada.
409 ROOM_FULL — sala já possui dois jogadores.
409 ROOM_ALREADY_STARTED — partida já iniciada.
409 ROOM_CONFLICT — jogador já está em sala ou partida ativa.
401 UNAUTHORIZED — token ausente ou inválido.
500 INTERNAL_ERROR — erro interno do servidor.

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

### `GET /api/profile`

✅ Disponível na dev — BE-027.

Bearer Token obrigatório.

Retorna o perfil do usuário autenticado, incluindo apelido, saldo de moedas e personagem favorito (baseado no histórico de partidas).

Resposta 200 (OK):

```json
{
  "username": "testuser",
  "coinsBalance": 150,
  "avatar": null,
  "preferredCharacter": "X"
}
```

- `username`: apelido atual do usuário
- `coinsBalance`: saldo atual de moedas
- `avatar`: campo reservado (sempre null nesta versão)
- `preferredCharacter`: nome do personagem mais utilizado pelo usuário; em caso de empate, o usado mais recentemente. Retorna null se o usuário não tiver histórico de partidas ou se nenhuma partida tiver personagem registrado.

Erros:
- 401 UNAUTHORIZED — token ausente ou inválido.
- 500 INTERNAL_ERROR — erro interno do servidor.

### `GET /api/profile/stats`

✅ Disponível na dev — BE-027.

Bearer Token obrigatório.

Retorna as estatísticas do usuário autenticado.

Resposta 200 (OK):

```json
{
  "matchesPlayed": 10,
  "wins": 6,
  "currentWinStreak": 2,
  "longestWinStreak": 4,
  "winRate": 60.0
}
```

- `matchesPlayed`: total de partidas jogadas
- `wins`: total de vitórias
- `currentWinStreak`: sequência atual de vitórias (empate ou derrota quebram a sequência)
- `longestWinStreak`: maior sequência de vitórias já alcançada
- `winRate`: taxa de vitória em porcentagem (wins / matchesPlayed * 100)

Erros:
- 401 UNAUTHORIZED — token ausente ou inválido.
- 500 INTERNAL_ERROR — erro interno do servidor.

### `PATCH /api/profile/username`

✅ Disponível na dev — BE-027.

Bearer Token obrigatório.

Altera o apelido do usuário autenticado. O apelido deve ter entre 3 e 15 caracteres e ser único (case-insensitive). O usuário pode enviar o próprio apelido atual sem causar erro.

Request body:

```json
{
  "username": "newusername"
}
```

Resposta 204 (No Content) — apelido alterado com sucesso.

Erros:
- 400 VALIDATION_ERROR — apelido fora do tamanho permitido (3-15 caracteres).
- 401 UNAUTHORIZED — token ausente ou inválido.
- 409 USERNAME_ALREADY_EXISTS — apelido já está em uso por outro usuário.
- 500 INTERNAL_ERROR — erro interno do servidor.

### `GET /api/profile/history`

✅ Disponível na dev — BE-027.

Bearer Token obrigatório.

Retorna o histórico de partidas do usuário autenticado, ordenado da mais recente para a mais antiga.

Resposta 200 (OK):

```json
[
  {
    "gameId": "123e4567-e89b-12d3-a456-426614174000",
    "opponentUsername": "opponentuser",
    "result": "WIN",
    "walkover": false,
    "finishedAt": "2026-10-25T10:30:00Z"
  }
]
```

- `gameId`: UUID da partida
- `opponentUsername`: apelido do oponente (ou "Desconhecido" se não disponível)
- `result`: resultado da partida ("WIN", "LOSS" ou "DRAW")
- `walkover`: true se a partida foi encerrada por W.O. (FORFEIT_TIMEOUT)
- `finishedAt`: timestamp de encerramento da partida (ISO-8601 UTC)

Erros:
- 401 UNAUTHORIZED — token ausente ou inválido.
- 500 INTERNAL_ERROR — erro interno do servidor.

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
- ✅ Conexão à sala: `SEND /app/room/{code}/connect` sem corpo de identidade (pode ser enviado sem body). **BREAKING (BE-040):** não envie `playerId`; a identidade vem do `Principal` estabelecido no CONNECT. O registro associa sessão, sala e jogador e marca jogador conectado. `PlayerDisconnectionListener` remove associação da sessão e, se conhecida, marca jogador desconectado; sessão desconhecida não produz ação no serviço.
- ✅ Gameplay (BE-023): `SEND /app/game/{gameId}/move` recebe `{ "position": 0 }`; `SEND /app/game/{gameId}/skill` recebe `{ "skillCode": "BOMBA", "targetPosition": 1, "destinationPosition": 2 }` (posições opcionais conforme habilidade). Campos `playerId` enviados pelo cliente são ignorados. A identidade vem do `Principal`. O sucesso não tem ACK de mensagem nesta etapa; turno/resultado de round é publicado como `TurnChangedEvent` interno após persistência, para consumo pela integração de broadcast da BE-024. Destino e payload público final seguem pendentes de alinhamento com o front conforme seção 4.
- Em clientes STOMP.js, passe o token por `connectHeaders`, por exemplo `client.connect({ Authorization: `Bearer ${token}` })` na API legada ou `new Client({ connectHeaders: { Authorization: `Bearer ${token}` } })` na API atual. O cabeçalho é um header nativo STOMP, não um header do handshake HTTP.
- Falha de autenticação encerra/rejeita o CONNECT STOMP; não há sessão anônima e a resposta de falha pode ser um frame STOMP `ERROR`/fechamento, não um `ApiError` REST nem uma mensagem garantida em `/user/queue/errors` (a sessão ainda não foi autenticada).
- ⚠️ O disconnect acima é o comportamento observado; uma nova conexão à sala publica `ROOM_SNAPSHOT` conforme BE-035.2. Heartbeat STOMP permanece fora do escopo implementado.
- Erros são enviados por `@SendToUser` para `/user/queue/errors`, como `ApiError`. Nos handlers genéricos, `code` vem de `HttpStatus.name()` (por exemplo `BAD_REQUEST`, `CONFLICT`, `INTERNAL_SERVER_ERROR`) e existe também `error` com a frase HTTP; isso diverge do catálogo REST. As BE-014.1 e BE-014.2 preservam os quatro códigos de habilidade descritos acima; o ajuste dos demais erros permanece na BE-044.
- A fila `/user/queue/errors` é destinada a sessões autenticadas; não se aplica ao CONNECT rejeitado, que ainda não tem usuário STOMP associado.

### Escolher duas habilidades iniciais — BE-013

🚧 Implementado nesta branch. Destino compatível com `DRAFT_ABILITIES` no frontend
(`src/sockets/destinations.ts` e `src/types/events.ts`, commit `b3a6626`).

Envie `SEND /app/rooms/{code}/draft/abilities`, com `content-type: application/json`:

```json
{ "abilities": ["BOMBA", "ESCUDO"] }
```

O `Principal` do CONNECT identifica o jogador. Campos extras de identidade no corpo,
como `playerId`, são ignorados. O código tem quatro dígitos e o usuário precisa
participar da sala ativa. O personagem já deve estar escolhido e o prazo de 20s
precisa estar aberto; confirmar habilidades não reinicia esse prazo.

São aceitos exatamente dois códigos distintos do conjunto elegível guardado no
início do Draft (gratuitas e compradas). A confirmação salva as escolhas no estado
Redis, marca o jogador como pronto e impede novas alterações. Não há ACK específico:
o resultado chega no `DRAFT_UPDATE` já descrito abaixo, sem expor as habilidades.
Quando ambos ficam prontos, `DraftCompleted` continua sinalizando a futura BE-038.

Erros chegam apenas à sessão autora em `/user/queue/errors`, como `ApiError`,
com `path` igual ao destino da requisição. A fila aceita inscrição autenticada
sem exigir participação em uma sala; a checagem de inscrição no tópico público
continua exigindo participação. Não são criados novos códigos de erro:

| Código | Status no payload | Quando |
|---|---:|---|
| `BAD_REQUEST` | 400 | Código da sala inválido; seleção ausente, quantidade diferente de dois, duplicada, vazia ou não elegível; personagem ainda não escolhido. |
| `FORBIDDEN` | 403 | Usuário não participa da sala ativa. |
| `CONFLICT` | 409 | Draft ausente, concluído, prazo encerrado ou escolhas já confirmadas. |

Não há endpoint REST, escolha de personagem ou sorteio por round nesta entrega.
A BE-011 continua responsável pelo personagem e a BE-013.1 pelo sorteio de 8s e
pela montagem do deck de cada round. A validação conjunta geral da FE-17 segue pendente.

### `PLAYER_JOINED` — `/topic/rooms/{code}`

✅ Disponível na `dev` — BE-035; dados dos jogadores ampliados na BE-035.1.

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
    "hostId": "123e4567-e89b-12d3-a456-426614174000",
    "hostUsername": "ana"
  },
  "serverTime": "2026-10-23T12:00:00Z"
}
```
Campos do `payload`:

| Campo          | Tipo             | Descrição                               |
|----------------|------------------|-----------------------------------------|
| `roomCode`     | string           | Código da sala.                         |
| `guestId`      | string (UUID)    | ID de quem entrou.                      |
| `opponentName` | string           | Username de quem entrou.                |
| `hostId`       | string (UUID)    | ID do host.                             |
| `hostUsername` | string           | Username do host.                       |
| `guestAvatar`  | string, opcional | Avatar de perfil equipado do convidado. |
| `hostAvatar`   | string, opcional | Avatar de perfil equipado do host.      |

⚠️ `guestAvatar` e `hostAvatar`: o avatar é um cosmético do inventário e **não** é o personagem escolhido no Draft. O avatar equipado ainda não existe (o campo `avatar` do perfil está reservado, sempre `null`), então **hoje esses campos nunca são enviados**; valores `null` são omitidos da mensagem. Passarão a aparecer quando a loja/inventário o implementar, sem alteração de contrato. O formato do avatar (código, URL) está pendente.

Compatibilidade: acréscimo de campos, sem quebra; `roomCode`, `guestId`, `opponentName` e `hostId` permanecem iguais. A inscrição no tópico exige identidade STOMP autenticada e participação ativa na sala, validadas pelo `Principal` no interceptor de entrada.

### `ROOM_SNAPSHOT` — `/topic/rooms/{code}`

✅ Implementado na BE-035.2, com payload compatível com `RoomSnapshot` em `src/types/game.ts` e envelope `ServerEnvelope` em `src/types/events.ts` do frontend. Publica ao conectar, após a entrada do segundo jogador, nas alterações do Draft e nas transições de round/partida. O evento complementa os demais eventos existentes.

O payload segue esta estrutura (nomes e formatos iguais aos tipos do frontend):

- `code`: código textual de quatro dígitos.
- `phase`: `WAITING`, `MATCHED`, `DRAFT`, `VERSUS`, `ROUND_DRAW`, `PLAYING`, `ROUND_END` ou `RESULT`, derivado do estado de sala, Draft e partida disponível.
- `players`: lista de `PlayerState`: `slot` (`P1`/`P2`), `userId` (UUID textual), `nickname`, `character` (símbolo textual ou `null`), `roundsWon`, `consecutiveTimeouts`, `forceFieldTurnsLeft`, `silenced` e `connected`.
- `mySlot`: slot do destinatário na cópia privada; no tópico compartilhado, o campo é `null` (sem valor individual).
- `round`, `suddenDeath`, `board`: round atual, flag booleana e 16 células (`index`, `owner` como `P1`/`P2`/`null`, `effects` com `kind` e `turnsLeft`).
- `turn`: `null` ou `{player, startedAt, durationSeconds}`; `startedAt` é epoch millis.
- `myDeck`: lista privada de `{ability, source, usedThisRound}`. É enviado somente ao jogador correspondente. No tópico compartilhado é sempre lista vazia.
- `outcome`: `null`, `{kind:"WIN", winner, score:[p1,p2]}`, `{kind:"ABSOLUTE_DRAW"}` ou `{kind:"WO", winner}`.
- `serverTime`: epoch millis também presente dentro do payload, como exigido pelo tipo `RoomSnapshot`.
- `draft` é opcional e contém `{deadline, ready:{P1,P2}}`; `drawOptions`, `coins` e `rematch` são opcionais. Campos sem fonte atual confiável são omitidos. O backend não persiste opções do sorteio e revanche ainda não está implementada.

O envelope externo também contém `serverTime` como número inteiro de milissegundos desde Unix epoch. O valor do envelope e do payload é o mesmo. No tópico `/topic/rooms/{code}`, `mySlot` é `null`, `myDeck` é `[]` e `drawOptions` é `null`; nenhum valor individual de slot, deck ou opções privadas é publicado. Cada participante recebe em `/user/queue/room` um `ROOM_SNAPSHOT` individualizado com seu `mySlot` e `myDeck`; `drawOptions` segue nulo porque o backend não mantém esse estado. A inscrição nessa fila privada exige sessão autenticada no `CONNECT`; o cliente já a assina e aplica o snapshot pelo mesmo handler de eventos.

Exemplo resumido de snapshot público em sala `WAITING` (os campos privados usam `null`/lista vazia):

```json
{
  "type": "ROOM_SNAPSHOT",
  "payload": {
    "code": "0042",
    "phase": "WAITING",
    "players": [{
      "slot": "P1", "userId": "123e4567-e89b-12d3-a456-426614174000",
      "nickname": "ana", "character": null, "roundsWon": 0,
      "consecutiveTimeouts": 0, "forceFieldTurnsLeft": 0,
      "silenced": false, "connected": true
    }],
    "mySlot": null,
    "round": 1,
    "suddenDeath": false,
    "board": [
      {"index":0,"owner":null,"effects":[]}, {"index":1,"owner":null,"effects":[]},
      {"index":2,"owner":null,"effects":[]}, {"index":3,"owner":null,"effects":[]},
      {"index":4,"owner":null,"effects":[]}, {"index":5,"owner":null,"effects":[]},
      {"index":6,"owner":null,"effects":[]}, {"index":7,"owner":null,"effects":[]},
      {"index":8,"owner":null,"effects":[]}, {"index":9,"owner":null,"effects":[]},
      {"index":10,"owner":null,"effects":[]}, {"index":11,"owner":null,"effects":[]},
      {"index":12,"owner":null,"effects":[]}, {"index":13,"owner":null,"effects":[]},
      {"index":14,"owner":null,"effects":[]}, {"index":15,"owner":null,"effects":[]}
    ],
    "turn": null,
    "myDeck": [],
    "outcome": null,
    "serverTime": 1793026800000,
    "draft": null,
    "drawOptions": null,
    "coins": null,
    "rematch": null
  },
  "serverTime": 1793026800000
}
```

A conexão e a inscrição em `/topic/rooms/{code}` exigem JWT no `CONNECT` e participação ativa validada pelo `Principal`; o handler de conexão também deriva o jogador do `Principal`.

Sincronização sob demanda: envie `SEND /app/rooms/{code}/sync` sem corpo. A identidade do solicitante vem do `Principal` autenticado pelo JWT. O backend valida sala ativa e participação antes de publicar o snapshot atualizado em `/topic/rooms/{code}`; não aceite nem envie `playerId` no payload. Como a resposta usa o tópico compartilhado, todos os participantes inscritos na sala podem receber o snapshot. Códigos inválidos ou sala inexistente/encerrada seguem o handler STOMP existente como `BAD_REQUEST` (400); usuário autenticado que não pertence à sala recebe `FORBIDDEN` (403) em `/user/queue/errors`.

```text
SEND /app/rooms/0042/sync
body: (vazio)
```

A conexão e a inscrição no tópico seguem o mesmo controle: a autenticação ocorre no `CONNECT` por `Authorization: Bearer <jwt>`, e a associação à sala é conferida no backend pelo `Principal`.

### `ROOM_CLOSED` — `/topic/rooms/{code}`

✅ Disponível na `dev` — BE-036.

Quando é enviado: após o commit do fechamento da sala, por saída do host (`HOST_LEFT`) ou expiração de sala `WAITING` sem 2º jogador (`EXPIRED`).
Quem recebe: todos os inscritos no tópico (broadcast).
Envelope: `{ "type", "payload", "serverTime" }`.

### Propostas de destinos e eventos

Os itens marcados 📝 ainda são propostas. Nesta branch, a BE-037 publica
`DRAFT_UPDATE` em `/topic/rooms/{code}`, no envelope `{ "type", "payload", "serverTime" }`.
O tópico no plural consta como confirmado pelo back na documentação do frontend
(`docs/ARCHITECTURE.md`, seção 6). Os demais eventos mantêm o status indicado abaixo.

🚧 **BE-037, implementada nesta branch:** prazo único de 20s, escolhas elegíveis,
bloqueio após confirmar as duas habilidades e auto-escolha ao expirar. Alterar
escolhas não reinicia o prazo; a auto-escolha não conta para W.O. A elegibilidade
é consultada no início e inclui habilidades compradas, sem filtrar `initialSkill`.

`DraftUpdated` é interno e aciona a publicação abaixo após gravar o Redis.
`DraftCompleted` também é interno: leva as escolhas finais à BE-038, sem expor
habilidades no tópico público. Os eventos não têm entrega persistente ou replay.

Exemplo de `DRAFT_UPDATE` recebido pelos inscritos em `/topic/rooms/0042`:

```json
{
  "type": "DRAFT_UPDATE",
  "payload": {
    "deadline": 1792670020000,
    "characters": { "P1": "X", "P2": "O" },
    "ready": { "P1": true, "P2": false }
  },
  "serverTime": 1792670000000
}
```

`deadline` e `serverTime` são timestamps UTC em milissegundos. P1 é o host e P2
o convidado; lados não dependem de quem começa a jogar. `characters` omite lados
que ainda não escolheram e usa `X`, `O`, `TRIANGULO`, `QUADRADO`, `ESTRELA` ou
`CORACAO`, não UUIDs. `ready` sempre contém os dois lados. Inventários e habilidades
escolhidas não são publicados. A inscrição no tópico exige sessão STOMP autenticada e participante da sala ativa correspondente ao código.

O formato corresponde a `src/types/events.ts` e `src/stores/serverEvents.ts` do
[frontend, commit 718864c](https://github.com/Jogo-da-Velha-ES3/JogoDaVelha-FrontEnd/tree/718864c8124b9a5ca84466447875199f9b1dbaf9).
A checagem do consumidor não substitui a validação conjunta da FE-17, ainda aberta.

BE-011 adiciona a escolha de personagem; a seleção de habilidades da BE-013 está descrita acima. BE-038 inicia o Draft e consome
`DraftCompleted` para criar a partida. Na inicialização, a recuperação varre os estados persistidos no Redis e chama `resume(roomId)` para rearmar os prazos ativos. A integração STOMP da BE-037
cobre dois clientes autenticados, conclusão antecipada e timeout, sem banco,
Redis externo ou migrations.

**Eventos de sala e jogo — janela solicitada pelo front até 21/10:**

| Evento | Quando / destinatário | Campos previstos | Card / status |
|---|---|---|---|
| `ROOM_SNAPSHOT` | Estado atual ao conectar e em transições da sala, Draft e partida; participantes | `room`, `draft`, `match`; `rematch` ainda não implementado | BE-035.2; ✅ |
| `PLAYER_JOINED` | Entrada do segundo jogador; sala / host | Nome do adversário (BE-035) | BE-035, 18/10; 📝 |
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
| `DRAFT_UPDATE` | Início e mudanças do Draft; inscritos no tópico da sala | `deadline`, `characters` e `ready` por P1/P2; exemplo acima |
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
| ~~Tópico `/topic/rooms/{code}` sem restrição de inscrição~~ | Inscrição exige participante autenticado; interceptor confere associação ativa com o código pelo `Principal` | BE-040 |
| `guestAvatar`/`hostAvatar` em `PLAYER_JOINED`: formato do avatar e fonte de dados dependem da loja/inventário | Back + front | BE-035.1 |
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
| 23/10/2026 | Evento `PLAYER_JOINED` disponível em `/topic/rooms/{code}` com envelope `{type, payload, serverTime}`. | NOVO | BE-035; PR feat/room-joined-event |
| 25/10/2026 | `PLAYER_JOINED` ganha `hostUsername` e, quando houver avatar equipado, `guestAvatar`/`hostAvatar` (hoje omitidos). Acréscimo compatível. | ALTERADO | BE-035.1 |
| 23/10/2026 | Evento `PLAYER_JOINED` disponível em `/topic/rooms/{code}` com envelope `{type, payload, serverTime}`.                                                                                    | NOVO                          | BE-035; PR feat/room-joined-event           |
| 24/10/2026 | `POST /api/rooms/{code}/leave`, evento `ROOM_CLOSED` e expiração de salas `WAITING` abandonadas.                                                                                          | NOVO                          | BE-036; PR feat/room-leave-expiration       |
| 25/10/2026 | BE-038: `DraftCompleted` do pacote `draft`, personagens persistidos, sala permanece `IN_GAME` no `MATCH_END` (revanche BE-039) e `coinsCredited` reflete o crédito real. | NOVO | BE-038 |
| 25/10/2026 | Seleção de duas habilidades iniciais por STOMP, confirmação no Draft e erros privados. | NOVO | BE-013 |

### Implementação BE-038 (2026-10-25)

A orquestração da partida publica no destino proposto pelo front `/topic/rooms/{code}`,
com o envelope `{type, payload, serverTime}`. `PHASE_CHANGE` em `PLAYING` contém
`gameId`, `phase`, `round`, `score` (`player1`, `player2`), `firstPlayerId`, `board`,
`suddenDeath` e `turnDeadline` (epoch millis). `ROUND_END` contém `gameId`, `round`,
`result` (`PLAYER1_WIN`, `PLAYER2_WIN` ou `DRAW`) e `score`. `MATCH_END` contém
`gameId`, `score`, `winnerId` (string vazia em empate), `forfeit` e `coinsCredited`
(map de UUID do jogador para moedas efetivamente creditadas pelo processamento de
recompensas). `serverTime` é epoch millis.

O estado inicial é sorteado pelo `GameStateManager`; `ROUND_END` usa `TurnChangedEvent`
e registra uma única vez o round indicado. Round 3 aplica 5 segundos por turno. A sala
permanece `IN_GAME` ao fim da partida para permitir a revanche (BE-039); o fechamento
`CLOSED` fica a cargo dos fluxos de saída/recusa. A orquestração consome o evento
interno `com.jogodavelha.game.draft.DraftCompleted` (jogadores, personagens e flag
`automatic`) publicado pelo `DraftService`. Os personagens selecionados são persistidos
em `games.player1_character_id` / `player2_character_id`. A interface `RoundSkillDraft`
é obrigatória: a partida não inicia o turno sem o callback da janela de escolha de 8s
(implementação BE-013.1). O destino `/topic/room/{code}` citado no backlog continua
divergente; foi usado o plural `/topic/rooms/{code}` proposto nesta seção do contrato.
