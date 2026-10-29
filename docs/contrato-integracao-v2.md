# Contrato de Integração Back-End × Front-End V2

**Versão:** 2.0
**Data de elaboração:** 26/10/2026
**Fonte da verdade:** Implementação na branch `dev`
**Documento anterior preservado:** `docs/contrato-integracao.md`

**BE-013.1:** os trechos de sorteio por round abaixo estão implementados na PR #119,
em revisão. As demais seções mantêm a referência da auditoria da `dev`.

---

## 1. Visão Geral

### 1.1 Objetivo

Este documento descreve o contrato de integração entre o Back-End e o Front-End do Jogo da Velha, baseado na implementação existente na branch `dev`. O documento serve como referência única para desenvolvedores do Front-End implementarem a integração corretamente.

### 1.2 Escopo e Responsabilidades

**Back-End:**
- Fornecer endpoints REST para autenticação, gerenciamento de salas, perfil e inventários
- Fornecer conexão WebSocket/STOMP para comunicação em tempo real
- Publicar eventos de estado da sala e atualizações de jogo
- Validar todas as requisições e garantir consistência do estado

**Front-End:**
- Autenticar usuários via JWT e manter tokens válidos
- Gerenciar conexão WebSocket/STOMP com autenticação
- Assinar tópicos e filas apropriadas
- Tratar erros e estados de forma adequada
- Sincronizar estado da interface com eventos recebidos

### 1.3 Implementação Analisada

- **Branch:** `dev`
- **Data da auditoria:** 26/10/2026
- **Método:** Análise estática do código-fonte, DTOs, controllers, services e configurações

### 1.4 Legenda de Status

- ✅ **Implementado** - Funcionalidade confirmada no código
- 🚧 **Em desenvolvimento** - Código existe mas integração incompleta
- 📝 **Planejado** - Apenas proposto, não implementado
- ❓ **A confirmar** - Evidência insuficiente no código/teste

### 1.5 Documentação Anterior

A versão anterior (`docs/contrato-integracao.md`) foi preservada integralmente sem alterações. Este documento V2 é uma reestruturação completa baseada na auditoria da implementação real, com foco em precisão técnica e rastreabilidade.

---

## 2. Convenções e Padrões

### 2.1 Formato de Datas e Horários

- **Timestamps de erro:** `Instant` ISO-8601 UTC (ex: `2026-10-26T10:30:00Z`)
- **Timestamps de negócio:** `long` em milissegundos epoch para turnos e deadlines
- **Timestamps de histórico:** `Instant` ISO-8601 UTC (ex: `2026-10-25T10:30:00Z`)

### 2.2 Formato de Identificadores

- **UUID de usuário:** String padrão UUID (ex: `123e4567-e89b-12d3-a456-426614174000`)
- **UUID de sala/game:** String padrão UUID
- **Código de sala:** String de 4 dígitos numéricos (ex: `0042`)
- **Código de habilidade:** String em maiúsculas (ex: `BOMBA`, `ESCUDO`)

### 2.3 Convenções de Nomes e Enums

**Enums principais:**
- `RoomStatus`: `WAITING`, `IN_GAME`, `CLOSED`
- `CharacterSymbol`: `X`, `O`, `TRIANGULO`, `QUADRADO`, `ESTRELA`, `CORACAO`
- `RoomPhase`: `WAITING`, `MATCHED`, `DRAFT`, `VERSUS`, `ROUND_DRAW`, `PLAYING`, `ROUND_END`, `RESULT`
- Códigos de habilidade: `BOMBA`, `ESCUDO`, `TORNADO`, `BURACO_NEGRO`, `AMPULHETA`, `CONGELAR`, `CAMPO_DE_FORCA`, `TROCA_RAPIDA`, `REFORCO`, `SILENCIO`

**Slots de jogador:**
- `P1`: Jogador 1 (host da sala)
- `P2`: Jogador 2 (convidado)

### 2.4 Estrutura de Respostas REST

**Resposta de sucesso:** Varia por endpoint, sem envelope padrão unificado

**Resposta de erro (ApiError):**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Dados inválidos",
  "status": 400,
  "timestamp": "2026-10-26T10:30:00Z",
  "path": "/api/auth/register",
  "fields": { "email": "E-mail inválido" },
  "error": "Bad Request"
}
```

O campo `fields` é incluído apenas para erros de validação de campos (`VALIDATION_ERROR`), sendo nulo nos demais casos. O campo `error` contém a frase HTTP (ex: "Bad Request") e está sempre presente, mesmo quando nulo.

### 2.5 Formato de Erros STOMP

Erros STOMP são enviados como `ApiError` para `/user/queue/errors`:

```json
{
  "code": "BAD_REQUEST",
  "message": "Mensagem de erro",
  "status": 400,
  "timestamp": "2026-10-26T10:30:00Z",
  "path": "/app/game/{gameId}/move",
  "fields": null,
  "error": "Bad Request"
}
```

Para erros de habilidade, o campo `code` usa valores específicos do domínio (ex: `SKILL_INVALID_TARGET`). Para erros genéricos, usa `HttpStatus.name()` (ex: `BAD_REQUEST`, `CONFLICT`, `INTERNAL_SERVER_ERROR`). Os campos `fields` e `error` estão sempre presentes, mesmo quando nulos.

### 2.6 Campos Opcionais, Nulos e Ausentes

- **DTOs principais:** Campos nulos são sempre incluídos em `ApiError`; nos demais DTOs listados, são sempre incluídos (`@JsonInclude.Include.ALWAYS`)
  - `RoomDTO`, `SkillDTO`, `CharacterDTO`, `GameDTO`
  - `LoginResponse`, `RegisterResponse`, `BalanceResponse`
  - `ProfileResponse`, `ProfileStatsResponse`, `MatchHistoryResponse`
  - `RoomSnapshot`, `GameState`
- Campos opcionais não presentes no payload são tratados como `null`
- Listas vazias são serializadas como `[]`

### 2.7 Autenticação e Autorização

**REST:**
- Header: `Authorization: Bearer <token>`
- Token JWT contém UUID do usuário no claim `sub`
- Rotas públicas: `/api/auth/**`, `/api/health`, `/api/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`, `/ws/**`
- Demais rotas exigem token válido

**STOMP:**
- Frame CONNECT deve conter header `Authorization: Bearer <token>`
- Token validado por `StompJwtAuthenticationInterceptor`
- Principal autenticado é o UUID do usuário (string)
- Assinaturas de tópicos privados exigem autenticação prévia

---

## 3. Autenticação e Segurança

### 3.1 Obtenção do Token

O token é obtido através do endpoint de login:

```
POST /api/auth/login
```

Corpo da requisição:
```json
{
  "identifier": "usuario@email.com",
  "password": "senha123"
}
```

Resposta de sucesso:
```json
{
  "token": "<jwt_token>",
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "username": "usuario",
  "coinsBalance": 150
}
```

### 3.2 Autenticação REST

Todas as requisições protegidas devem incluir:

```
Authorization: Bearer <jwt_token>
```

O token é validado por `JwtAuthenticationFilter` antes de chegar aos controllers.

### 3.3 Autenticação STOMP

A conexão WebSocket inicia em `/ws`. O frame STOMP `CONNECT` deve incluir:

```
CONNECT
Authorization: Bearer <jwt_token>
```

O interceptor `StompJwtAuthenticationInterceptor` (linha 62-75 de `StompJwtAuthenticationInterceptor.java`) valida o token e extrai o UUID do usuário, configurando-o como `Principal` da sessão.

### 3.4 Identidade do Usuário Autenticado

Após autenticação bem-sucedida:
- O UUID do usuário está disponível como `Principal.getName()` (string)
- Controllers REST obtêm via `Authentication.getPrincipal()` (UUID)
- Controllers STOMP obtêm via `Principal.getName()` (string, convertido para UUID)

### 3.5 Regras de Autorização e Acesso às Salas

**Acesso REST:**
- Usuário só pode entrar em salas onde tem acesso
- `RoomService.joinRoom` valida conflitos de sala/partida ativa
- `RoomService.leaveRoom` valida pertencimento à sala

**Acesso STOMP:**
- Assinatura de `/topic/rooms/{code}` é autorizada apenas para membros da sala (linha 45-58 de `StompJwtAuthenticationInterceptor.java`)
- Validação usa `RoomRepository.findActiveMembership`
- Filas privadas `/user/queue/room` e `/user/queue/errors` exigem principal não-nulo

### 3.6 Configuração CORS

**Origens permitidas:** Configurado via `CORS_ALLOWED_ORIGINS` (padrão: `http://localhost:5173`)

**Métodos permitidos:** `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`

**Headers permitidos:** `Authorization`, `Content-Type`, `Accept`

**Credentials:** Não permitidos

A mesma configuração aplica-se ao endpoint WebSocket `/ws` (linha 35-36 de `WebSocketConfig.java`).

### 3.7 Respostas de Falha de Autenticação

**REST:**
- 401 UNAUTHORIZED: Token ausente, inválido ou expirado
- 403 FORBIDDEN: Acesso negado a recurso protegido
- Payload: `ApiError` com código `UNAUTHORIZED` ou `FORBIDDEN`

**STOMP:**
- CONNECT rejeitado: Conexão encerrada pelo servidor, possivelmente com frame STOMP ERROR
- Erros durante mensagem: `ApiError` enviado para `/user/queue/errors`
- Não há mensagem garantida em `/user/queue/errors` para CONNECT rejeitado (sessão ainda não autenticada)

---

## 4. Contrato REST

### 4.1 Autenticação

#### POST /api/auth/register

**Status:** ✅ Implementado
**Controller:** `AuthController.register` (linha 36-40)
**Autenticação:** Pública

**Objetivo:** Cadastrar um novo usuário no sistema.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "username": "ana",
  "password": "senha123",
  "email": "ana@example.com"
}
```

Validações:
- `username`: obrigatório, 3-15 caracteres
- `password`: obrigatório, 6-72 caracteres
- `email`: obrigatório, até 254 caracteres, formato válido com domínio/TLD de ao menos 2 letras

**Resposta de sucesso (201):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "username": "ana",
  "email": "ana@example.com"
}
```

**Possíveis erros:**
- 400 VALIDATION_ERROR: Dados inválidos (pode incluir `fields`)
- 400 INVALID_REQUEST_BODY: Corpo JSON ilegível
- 409 USERNAME_ALREADY_EXISTS: Username já cadastrado
- 409 EMAIL_ALREADY_EXISTS: E-mail já cadastrado
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `RegisterRequest` (linha 13-33 de `RegisterRequest.java`)
- Response DTO: `RegisterResponse` (linha 5-9 de `RegisterResponse.java`)
- Serviço: `AuthService.register`

---

#### POST /api/auth/login

**Status:** ✅ Implementado
**Controller:** `AuthController.login` (linha 49-52)
**Autenticação:** Pública

**Objetivo:** Autenticar usuário e retornar token JWT.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "identifier": "ana",
  "password": "senha123"
}
```

Validações:
- `identifier`: obrigatório, até 254 caracteres (pode ser e-mail ou username)
- `password`: obrigatório, não vazio

**Resposta de sucesso (200):**
```json
{
  "token": "<jwt_token>",
  "id": "123e4567-e89b-12d3-a456-426614174000",
  "username": "ana",
  "coinsBalance": 150
}
```

**Possíveis erros:**
- 400 VALIDATION_ERROR: Dados inválidos
- 400 INVALID_REQUEST_BODY: Corpo JSON ilegível
- 401 INVALID_CREDENTIALS: Credenciais inválidas
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `LoginRequest` (linha 12-23 de `LoginRequest.java`)
- Response DTO: `LoginResponse` (linha 5-15 de `LoginResponse.java`)
- Serviço: `AuthService.login`
- Token permanece válido até expiração configurada (30 dias por padrão)
- Logout é local no cliente (sem endpoint de revogação)

---

### 4.2 Saúde da API

#### GET /api/health

**Status:** ✅ Implementado
**Controller:** `HealthController.checkHealth` (linha 25-27)
**Autenticação:** Pública

**Objetivo:** Verificar disponibilidade da API.

**Parâmetros de rota/query:** Nenhum

**Headers:** Nenhum específico

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
```json
{
  "status": "UP",
  "message": "API disponível"
}
```

**Possíveis erros:**
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- Endpoint leve para acordar a aplicação após cold start
- DTO: `HealthResponse` (linha 29 de `HealthController.java`)

---

### 4.3 Salas

#### POST /api/rooms

**Status:** ✅ Implementado
**Controller:** `RoomController.createRoom` (linha 29-53)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Criar uma nova sala para o usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (201):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174001",
  "code": "0042",
  "status": "WAITING",
  "player1Id": "123e4567-e89b-12d3-a456-426614174000",
  "player1Username": "ana",
  "player2Id": null,
  "player2Username": null
}
```

**Possíveis erros:**
- 400 ROOM_BAD_REQUEST: Usuário inválido ou requisição inválida
- 409 ROOM_CONFLICT: Jogador já está em sala ou partida ativa
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `RoomDTO` (linha 5-25 de `RoomDTO.java`)
- Serviço: `RoomService.createRoom`
- Usuário autenticado é o criador da sala (player1)
- Código de sala gerado automaticamente (4 dígitos)
- `player2Id` e `player2Username` são nulos na criação

---

#### POST /api/rooms/{code}/join

**Status:** ✅ Implementado
**Controller:** `RoomController.joinRoom` (linha 60-74)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Entrar em uma sala existente pelo código.

**Parâmetros de rota:**
- `code`: String de 4 dígitos (ex: `0042`)

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
```json
{
  "id": "123e4567-e89b-12d3-a456-426614174001",
  "code": "0042",
  "status": "IN_GAME",
  "player1Id": "123e4567-e89b-12d3-a456-426614174000",
  "player1Username": "ana",
  "player2Id": "123e4567-e89b-12d3-a456-426614174002",
  "player2Username": "bia"
}
```

**Possíveis erros:**
- 400 ROOM_BAD_REQUEST: Código inválido ou usuário inválido
- 404 ROOM_NOT_FOUND: Sala inexistente ou encerrada
- 409 ROOM_FULL: Sala já possui dois jogadores
- 409 ROOM_ALREADY_STARTED: Partida já iniciada
- 409 ROOM_CONFLICT: Jogador já está em sala ou partida ativa
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- Serviço: `RoomService.joinRoom`
- Usuário autenticado entra como player2
- Não há anotações Bean Validation no parâmetro `code`
- Sala muda status automaticamente quando player2 entra

---

#### POST /api/rooms/{code}/leave

**Status:** ✅ Implementado
**Controller:** `RoomController.leaveRoom` (linha 81-89)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Sair da sala. O host de sala em estado WAITING fecha a sala.

**Parâmetros de rota:**
- `code`: String de 4 dígitos (ex: `0042`)

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (204):** Sem corpo

**Possíveis erros:**
- 400 ROOM_BAD_REQUEST: Código inválido ou jogador não pertence à sala
- 404 ROOM_NOT_FOUND: Sala inexistente ou já encerrada
- 409 ROOM_ALREADY_STARTED: Partida já iniciada (abandono em partida será tratado como W.O. em outra tarefa)
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- Serviço: `RoomService.leaveRoom`
- Se o usuário for o host e a sala estiver em WAITING, a sala é fechada
- Abandono em partida não é suportado neste endpoint

---

### 4.4 Perfil

#### GET /api/profile

**Status:** ✅ Implementado
**Controller:** `ProfileController.profile` (linha 31-41)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar dados do perfil do usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
```json
{
  "username": "testuser",
  "coinsBalance": 150,
  "avatar": null,
  "preferredCharacter": "X"
}
```

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `ProfileResponse` (linha 3-9 de `ProfileResponse.java`)
- Serviço: `ProfileService.getProfile`
- `avatar`: Campo reservado (sempre null nesta versão)
- `preferredCharacter`: Personagem mais utilizado pelo usuário; null se não houver histórico

---

#### GET /api/profile/stats

**Status:** ✅ Implementado
**Controller:** `ProfileController.stats` (linha 43-53)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar estatísticas do usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
```json
{
  "matchesPlayed": 10,
  "wins": 6,
  "currentWinStreak": 2,
  "longestWinStreak": 4,
  "winRate": 60.0
}
```

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `ProfileStatsResponse` (linha 3-10 de `ProfileStatsResponse.java`)
- Serviço: `ProfileService.getStats`
- `winRate`: Porcentagem (wins / matchesPlayed * 100)
- Empate ou derrota quebram a sequência de vitórias

---

#### PATCH /api/profile/username

**Status:** ✅ Implementado
**Controller:** `ProfileController.updateUsername` (linha 55-69)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Alterar o apelido do usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "username": "newusername"
}
```

Validações:
- `username`: 3-15 caracteres, único (case-insensitive)

**Resposta de sucesso (204):** Sem corpo

**Possíveis erros:**
- 400 VALIDATION_ERROR: Apelido fora do tamanho permitido
- 401 UNAUTHORIZED: Token ausente ou inválido
- 409 USERNAME_ALREADY_EXISTS: Apelido já em uso
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `UpdateUsernameRequest`
- Serviço: `ProfileService.updateUsername`
- Usuário pode enviar o próprio apelido atual sem erro

---

#### GET /api/profile/history

**Status:** ✅ Implementado
**Controller:** `ProfileController.history` (linha 71-81)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar histórico de partidas do usuário autenticado, ordenado da mais recente para a mais antiga.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
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

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `MatchHistoryResponse` (linha 6-13 de `MatchHistoryResponse.java`)
- Serviço: `ProfileService.getHistory`
- `result`: "WIN", "LOSS" ou "DRAW"
- `walkover`: true se encerrado por W.O. (FORFEIT_TIMEOUT)
- `opponentUsername`: "Desconhecido" se não disponível

---

### 4.5 Moedas

#### GET /api/coins/balance

**Status:** ✅ Implementado
**Controller:** `CoinController.getBalance` (linha 19-31)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar saldo atual de moedas do usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
```json
{
  "coinsBalance": 150
}
```

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `BalanceResponse` (linha 3-6 de `BalanceResponse.java`)
- Serviço: `CoinService.getBalance`

---

### 4.6 Habilidades

#### GET /api/skills/inventory

**Status:** ✅ Implementado
**Controller:** `SkillController.inventory` (linha 29-39)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar habilidades associadas ao usuário autenticado (conjunto elegível para Draft).

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
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

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `SkillDTO` (linha 5-26 de `SkillDTO.java`)
- Serviço: `SkillService.getInventory`
- `code`: Identificador de integração da habilidade
- `initialSkill`: true se é habilidade inicial gratuita
- `offensive`: true se é habilidade ofensiva

---

### 4.7 Personagens

#### GET /api/characters/inventory

**Status:** ✅ Implementado
**Controller:** `CharacterController.inventory` (linha 29-39)
**Autenticação:** Bearer Token obrigatório

**Objetivo:** Retornar personagens visuais associados ao usuário autenticado.

**Parâmetros de rota/query:** Nenhum

**Headers:**
- `Authorization: Bearer <token>`

**Body da requisição:** Nenhum

**Resposta de sucesso (200):**
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

**Possíveis erros:**
- 401 UNAUTHORIZED: Token ausente ou inválido
- 500 INTERNAL_ERROR: Erro interno do servidor

**Regras e observações:**
- DTO: `CharacterDTO` (linha 5-20 de `CharacterDTO.java`)
- Serviço: `CharacterService.getInventory`
- `symbol`: `X`, `O`, `TRIANGULO`, `QUADRADO`, `ESTRELA` ou `CORACAO`
- Personagens são cosméticos e não têm poderes
- Endpoint retorna apenas registros existentes em `user_characters`
- Pendência: confirmar se X e O são atribuídos a todo jogador (RN-LOJ-01)

---

## 5. Contrato WebSocket/STOMP

### 5.1 Conexão e Autenticação

**Endpoint de conexão:** `ws://localhost:8080/ws`

**Prefixo de aplicação:** `/app`

**Brokers:**
- `/topic`: Tópicos públicos (broadcast)
- `/queue`: Filas privadas (envio direto ao usuário)

**Autenticação no CONNECT:**
```
CONNECT
Authorization: Bearer <jwt_token>
```

**Validação:**
- Implementado por `StompJwtAuthenticationInterceptor` (linha 36-76)
- Token validado por `JwtService.extractUserId`
- UUID extraído configurado como `Principal` da sessão
- Falha de autenticação rejeita o CONNECT (conexão encerrada)

### 5.2 Destinos de Envio (SEND)

#### SEND /app/rooms/{code}/connect

**Status:** ✅ Implementado
**Controller:** `PlayerConnectionController.connect` (linha 29-52)
**Autenticação:** STOMP Principal obrigatório

**Objetivo:** Conectar usuário à sala e marcar como conectado.

**Parâmetros de rota:**
- `code`: String de 4 dígitos (ex: `0042`)

**Headers:**
- `Content-Type: application/json` (opcional, corpo pode ser vazio)

**Body da requisição:** Opcional (pode ser enviado sem corpo)

**Validações:**
- Principal deve ser não-nulo
- Principal deve ser UUID válido
- SessionId deve estar presente

**Processamento:**
- Registro da sessão em `PlayerSessionRegistry.register`
- Marcação de jogador conectado via `PlayerService.markConnected`
- Publicação de `ROOM_SNAPSHOT` via `RoomSnapshotService.publish` (se disponível)

**Resposta/Eventos resultantes:**
- `ROOM_SNAPSHOT` publicado em `/topic/rooms/{code}` (público)
- `ROOM_SNAPSHOT` enviado para `/user/queue/room` (privado para cada jogador)

**Regras e observações:**
- Identidade vem do `Principal` estabelecido no CONNECT
- **BREAKING:** Não envie `playerId` no corpo (campo ignorado se presente)
- `RoomSnapshotService` é opcional (injetado via `@Autowired(required = false)`)

---

#### SEND /app/rooms/{code}/sync

**Status:** ✅ Implementado
**Controller:** `PlayerConnectionController.sync` (linha 54-69)
**Autenticação:** STOMP Principal obrigatório

**Objetivo:** Solicitar sincronização completa do estado da sala.

**Parâmetros de rota:**
- `code`: String de 4 dígitos (ex: `0042`)

**Headers:** Nenhum específico

**Body da requisição:** Nenhum

**Validações:**
- Principal deve ser não-nulo e UUID válido
- `RoomSnapshotService` deve estar disponível
- Solicitante deve pertencer à sala

**Processamento:**
- Validação de código de sala (4 dígitos)
- Verificação de pertencimento à sala
- Envio de snapshot privado para solicitante

**Resposta/Eventos resultantes:**
- `ROOM_SNAPSHOT` enviado para `/user/queue/room` (privado para solicitante)

**Regras e observações:**
- Usado para reconectar clientes que perderam estado
- Snapshot enviado é completo e individualizado

---

#### SEND /app/game/{gameId}/move

**Status:** ✅ Implementado
**Controller:** `GameplayController.move` (linha 25-32)
**Autenticação:** STOMP Principal obrigatório

**Objetivo:** Realizar uma jogada na partida.

**Parâmetros de rota:**
- `gameId`: UUID da partida

**Headers:**
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "position": 0
}
```

Validações:
- `position`: obrigatório, inteiro entre 0 e 8

**Processamento:**
- Validação de autenticação
- Chamada de `MoveActionService.play`

**Resposta/Eventos resultantes:**
- Não há ACK de mensagem imediato
- Atualizações publicadas via eventos internos (TurnChangedEvent)
- Destino público final alinhado com front está pendente (BE-024)

**Regras e observações:**
- A identidade do jogador vem do `Principal` autenticado e não faz parte do corpo
- DTO interno: `MoveRequest` (linha 19 de `GameplayController.java`)

---

#### SEND /app/game/{gameId}/skill

**Status:** ✅ Implementado
**Controller:** `GameplayController.activateSkill` (linha 34-42)
**Autenticação:** STOMP Principal obrigatório

**Objetivo:** Ativar uma habilidade na partida.

**Parâmetros de rota:**
- `gameId`: UUID da partida

**Headers:**
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "skillCode": "BOMBA",
  "targetPosition": 1,
  "destinationPosition": 2
}
```

Validações:
- `skillCode`: obrigatório, não vazio
- `targetPosition`: opcional (depende da habilidade)
- `destinationPosition`: opcional (depende da habilidade)

**Processamento:**
- Validação de autenticação
- Chamada de `SkillActivationService.activate`

**Resposta/Eventos resultantes:**
- Não há ACK de mensagem imediato
- Erros enviados para `/user/queue/errors`
- Atualizações publicadas via eventos internos

**Regras e observações:**
- A identidade do jogador vem do `Principal` autenticado e não faz parte do corpo
- DTO interno: `SkillRequest` (linha 20 de `GameplayController.java`)
- Posições opcionais conforme habilidade (ex: Tornado precisa de destinationPosition)

---

#### SEND /app/rooms/{code}/draft/abilities

**Status:** ✅ Implementado
**Controller:** `DraftAbilitiesController.confirm` (linha 25-38)
**Autenticação:** STOMP Principal obrigatório

**Objetivo:** Confirmar seleção de duas habilidades iniciais no Draft.

**Parâmetros de rota:**
- `code`: String de 4 dígitos (ex: `0042`)

**Headers:**
- `Content-Type: application/json`

**Body da requisição:**
```json
{
  "abilities": ["BOMBA", "ESCUDO"]
}
```

Validações:
- `abilities`: obrigatório, lista de 2 códigos de habilidade
- Identidade STOMP obrigatória

**Processamento:**
- Validação de autenticação
- Chamada de `DraftAbilitiesService.confirm`

**Resposta/Eventos resultantes:**
- Evento `DraftUpdated` publicado internamente
- `DRAFT_UPDATE` publicado em `/topic/rooms/{code}` via `DraftEventPublisher`
- `ROOM_SNAPSHOT` publicado via `RoomSnapshotEventPublisher`

**Regras e observações:**
- DTO: `Request` (linha 16-17 de `DraftAbilitiesController.java`)
- `@JsonIgnoreProperties(ignoreUnknown = true)` permite campos extras
- Habilidades devem pertencer ao inventário do usuário
- Destino compatível com `DRAFT_ABILITIES` no frontend

#### SEND /app/rooms/{code}/draw/pick — BE-013.1

**Status:** Implementado na PR #119, em revisão.
**Controller:** `RoundSkillDrawController.pick`.
**Autenticação:** `Principal` do JWT validado no CONNECT. Campos extras de
identidade, como `playerId`, são ignorados.

Envie com `content-type: application/json`:

```json
{ "ability": "TORNADO" }
```

O código da sala tem quatro dígitos e o jogador precisa participar dela. A habilidade
deve ser uma das três opções recebidas para aquele round. Só é aceita uma confirmação
por jogador, antes do prazo de 8 segundos.

Erros são privados, em `/user/queue/errors`: `BAD_REQUEST` (400) para código ou
habilidade inválidos; `FORBIDDEN` (403) para não participante; `CONFLICT` (409)
para sorteio ausente, encerrado, expirado ou escolha já confirmada. Não há novos
códigos de erro. O mesmo mapeamento consta no README.

### 5.3 Destinos de Assinatura (SUBSCRIBE)

#### SUBSCRIBE /topic/rooms/{code}

**Status:** ✅ Implementado
**Autorização:** Apenas membros da sala

**Objetivo:** Receber atualizações públicas da sala.

**Validação de autorização:**
- Implementado em `StompJwtAuthenticationInterceptor` (linha 41-58)
- Principal deve ser não-nulo
- Jogador deve ter associação ativa na sala (`findActiveMembership`)

**Eventos recebidos:**
- `ROOM_SNAPSHOT`: Snapshot público da sala (sem campos privados)
- `DRAFT_UPDATE`: Atualizações do Draft

**Regras e observações:**
- Padrão de destino: `/topic/rooms/[0-9]{4}`
- O snapshot público mantém todos os campos de `RoomSnapshot`; `mySlot` vem como `null` e `myDeck` como `[]` (ambos presentes no JSON)

---

#### SUBSCRIBE /user/queue/room

**Status:** ✅ Implementado
**Autorização:** Apenas sessões autenticadas

**Objetivo:** Receber snapshots privados e individualizados da sala.

**Validação de autorização:**
- Principal deve ser não-nulo (linha 43 de `StompJwtAuthenticationInterceptor.java`)

**Eventos recebidos:**
- `ROOM_SNAPSHOT`: Snapshot completo e individualizado (inclui `myDeck`)

**Regras e observações:**
- Enviado para cada jogador individualmente
- Inclui campos específicos do destinatário
- Usado em sincronização e conexão à sala

---

#### SUBSCRIBE /user/queue/errors

**Status:** ✅ Implementado
**Autorização:** Apenas sessões autenticadas

**Objetivo:** Receber mensagens de erro STOMP.

**Validação de autorização:**
- Principal deve ser não-nulo (linha 43 de `StompJwtAuthenticationInterceptor.java`)

**Eventos recebidos:**
- `ApiError`: Mensagens de erro de handlers STOMP

**Regras e observações:**
- Não se aplica ao CONNECT rejeitado (sessão ainda não autenticada)
- Erros enviados via `@SendToUser` em `WebSocketExceptionHandler`

### 5.4 Eventos Publicados pelo Back-End

#### ROOM_SNAPSHOT

**Status:** ✅ Implementado
**Publicador:** `RoomSnapshotService.publish` (linha 58-69)
**Gatilho de publicação:**
- Conexão à sala (`PlayerConnectionController.connect`)
- Sincronização solicitada (`PlayerConnectionController.sync`)
- Entrada de jogador na sala (`PlayerJoinedRoomEvent` via `RoomSnapshotEventPublisher`)
- Atualização de Draft (`DraftUpdated` via `RoomSnapshotEventPublisher`)

**Destinatários:**
- Público: `/topic/rooms/{code}` (snapshot sem campos privados)
- Privado: `/user/queue/room` (snapshot completo individualizado para cada jogador)

**Formato do envelope:**
```json
{
  "type": "ROOM_SNAPSHOT",
  "payload": { /* RoomSnapshot */ },
  "serverTime": 1729934600000
}
```

**Formato do payload (RoomSnapshot):**
```json
{
  "code": "0042",
  "phase": "IN_GAME",
  "players": [
    {
      "slot": "P1",
      "userId": "123e4567-e89b-12d3-a456-426614174000",
      "nickname": "ana",
      "character": "X",
      "roundsWon": 1,
      "consecutiveTimeouts": 0,
      "forceFieldTurnsLeft": 0,
      "silenced": false,
      "connected": true
    },
    {
      "slot": "P2",
      "userId": "123e4567-e89b-12d3-a456-426614174002",
      "nickname": "bia",
      "character": "O",
      "roundsWon": 0,
      "consecutiveTimeouts": 0,
      "forceFieldTurnsLeft": 0,
      "silenced": false,
      "connected": true
    }
  ],
  "mySlot": null,
  "round": 1,
  "suddenDeath": false,
  "board": [
    { "index": 0, "owner": null, "effects": [] },
    { "index": 1, "owner": "P1", "effects": [] },
    { "index": 2, "owner": null, "effects": [] },
    { "index": 3, "owner": null, "effects": [] },
    { "index": 4, "owner": "P2", "effects": [] },
    { "index": 5, "owner": null, "effects": [] },
    { "index": 6, "owner": null, "effects": [] },
    { "index": 7, "owner": null, "effects": [] },
    { "index": 8, "owner": null, "effects": [] }
  ],
  "turn": {
    "player": "P1",
    "startedAt": 1729934600000,
    "durationSeconds": 30
  },
  "myDeck": [],
  "outcome": null,
  "serverTime": 1729934600000,
  "draft": {
    "deadline": 1729934660000,
    "ready": { "P1": true, "P2": false }
  },
  "drawOptions": null,
  "coins": null,
  "rematch": null
}
```

**Momento de publicação:**
- Após persistência das mudanças (transação commitada)
- Após confirmação de Draft
- Após entrada de jogador
- Na abertura do sorteio de cada round, após uma escolha enquanto o adversário
  ainda escolhe, e na liberação do turno após os dois decks estarem definidos (BE-013.1).

**Comportamento esperado do Front-End:**
- Usar `ROOM_SNAPSHOT` como fonte única de verdade para estado da sala
- Sincronizar interface com snapshot recebido
- Considerar `mySlot` para identificar posição do usuário
- Atualizar deck e habilidades com base em `myDeck`

**Diferenças entre snapshot público e privado:**
- Todos os campos de `RoomSnapshot` são serializados, inclusive quando nulos (`@JsonInclude(ALWAYS)`). No snapshot público, `mySlot` é `null` e `myDeck` é `[]`; eles não são omitidos.
- No snapshot privado, `mySlot` indica o slot do destinatário e `myDeck` contém o deck daquele jogador. Os demais campos também aparecem quando nulos.
- Durante `ROUND_DRAW`, o snapshot privado contém `drawOptions:{options,deadline}`
  apenas para quem ainda não confirmou. Após confirmar, `drawOptions` é `null`
  e `myDeck` contém o deck atualizado do próprio jogador: no primeiro round são
  as duas habilidades fixas e a sorteada; nos seguintes reflete a substituição.
  O adversário continua
  recebendo somente as próprias opções e seu prazo original.
- No snapshot público, `drawOptions` é sempre `null`, `mySlot` é `null` e `myDeck`
  é `[]`. A fase compartilhada pode ser `ROUND_DRAW`, mas as opções nunca são públicas.
- Na fase `ROUND_DRAW`, `turn` é `null`: os 8 segundos de escolha não são um turno
  e não alteram os contadores de timeout. O Draft inicial concluído não substitui
  a fase da partida no snapshot.
- Campos variáveis do snapshot privado:

```json
{
  "mySlot": "P1",
  "myDeck": [
    { "ability": "BOMBA", "source": "INITIAL", "usedThisRound": false },
    { "ability": "ESCUDO", "source": "INITIAL", "usedThisRound": false }
  ]
}
```

**Regras e observações:**
- Implementado em `RoomSnapshotService` (linha 50-109)
- Usa `@JsonInclude(JsonInclude.Include.ALWAYS)` para campos sempre presentes
- `RoomPhase`: WAITING, MATCHED, DRAFT, VERSUS, ROUND_DRAW, PLAYING, ROUND_END, RESULT
- `Outcome.kind`: "WIN", "ABSOLUTE_DRAW", "WO"
- `CellEffect.kind`: "ESCUDO", "CONGELAR", "REFORCO"

---

#### DRAFT_UPDATE

**Status:** ✅ Implementado
**Publicador:** `DraftEventPublisher.onDraftUpdated` (linha 30-50)
**Gatilho de publicação:**
- Evento `DraftUpdated` emitido por `DraftService` após CAS no Redis

**Destinatários:**
- Público: `/topic/rooms/{code}`

**Formato do envelope:**
```json
{
  "type": "DRAFT_UPDATE",
  "payload": { /* Progress */ },
  "serverTime": 1729934600000
}
```

**Formato do payload (Progress):**
```json
{
  "deadline": 1729934660000,
  "characters": {
    "P1": "X",
    "P2": "O"
  },
  "ready": {
    "P1": true,
    "P2": false
  }
}
```

**Momento de publicação:**
- Após operação CAS no Redis (Compare-And-Swap)
- Fora do callback que pode ser repetido

**Comportamento esperado do Front-End:**
- Atualizar UI de Draft com personagens selecionados
- Mostrar estado de pronto de cada jogador
- Respeitar deadline para seleção

**Regras e observações:**
- Implementado em `DraftEventPublisher` (linha 17-60)
- Evento também publica `ROOM_SNAPSHOT` via `RoomSnapshotEventPublisher`

#### DRAW_OPTIONS e DECK_UPDATE — BE-013.1

**Status:** Implementados.
**Publicador:** `RoundSkillDrawService`.
**Destino:** somente `/user/queue/room`, usando o UUID autenticado do destinatário.
Nunca são publicados em `/topic/rooms/{code}`.

```json
{
  "type": "DRAW_OPTIONS",
  "payload": { "options": ["TORNADO", "CONGELAR", "SILENCIO"], "deadline": 1793102408000 },
  "serverTime": 1793102400000
}
```

São três códigos distintos do catálogo completo, podendo incluir habilidades não
compradas e excluindo todo o deck atual do próprio jogador. No primeiro round, o
deck tem as duas habilidades iniciais e a escolhida é adicionada. Nos rounds
seguintes, uma das três habilidades atuais é removida aleatoriamente e a escolhida
é adicionada. `deadline` e `serverTime` são epoch millis.

```json
{
  "type": "DECK_UPDATE",
  "payload": {
    "deck": [
      { "ability": "BOMBA", "source": "INITIAL", "usedThisRound": false },
      { "ability": "ESCUDO", "source": "INITIAL", "usedThisRound": false },
      { "ability": "TORNADO", "source": "DRAWN", "usedThisRound": false }
    ]
  },
  "serverTime": 1793102401000
}
```

Cada escolha salva publica um `DECK_UPDATE` ao dono com exatamente três habilidades.
A segunda escolha não reenvia
os decks já publicados. Na expiração, somente quem recebeu uma escolha automática
recebe o evento. Os snapshots privados continuam disponíveis para sincronização.

### 5.5 Mensagens de Erro STOMP

**Destino:** `/user/queue/errors`

**Formato:** `ApiError` (mesmo DTO de erros REST)

**Códigos específicos de habilidade:**
- `SKILL_INVALID_TARGET`: Alvo inválido para habilidade
- `SKILL_INVALID_DESTINATION`: Destino inválido (Tornado, Troca Rápida)
- `SKILL_SILENCED`: Jogador sob Silêncio
- `SKILL_ALREADY_USED`: Habilidade já usada no round
- `SKILL_NOT_IN_DECK`: Habilidade não pertence ao deck do round

**Códigos genéricos:**
- `BAD_REQUEST`: Requisição inválida
- `CONFLICT`: Conflito de estado
- `FORBIDDEN`: Acesso negado
- `INTERNAL_SERVER_ERROR`: Erro interno

**Handler:** `WebSocketExceptionHandler` (linha 14-54)

**Regras e observações:**
- Erros de habilidade usam código específico do domínio
- Erros genéricos usam `HttpStatus.name()`
- Campo `fields` é sempre presente (nulo em erros STOMP)
- Campo `error` (frase HTTP) é sempre presente

### 5.6 Envelopes e Timestamps

**Envelope padrão de eventos:**
```json
{
  "type": "<EVENT_TYPE>",
  "payload": { /* event-specific */ },
  "serverTime": 1729934600000
}
```

**Timestamps:**
- `serverTime`: `long` em milissegundos epoch (tempo do servidor)
- Usado para sincronização de clocks e cálculo de deadlines

### 5.7 Regras de Autorização de Assinaturas

**Implementação:** `StompJwtAuthenticationInterceptor.preSend` (linha 37-59)

**Regras:**
- `/user/queue/errors`: Principal não-nulo
- `/user/queue/room`: Principal não-nulo
- `/topic/rooms/{code}`: Principal não-nulo E associação ativa na sala
- Demais destinos: Negado

**Validação de associação:**
- Usa `RoomRepository.findActiveMembership`
- Verifica `RoomCodeAllocator.ACTIVE`
- Rejeita com `AccessDeniedException` se não pertencer

---

## 6. Fluxos Completos de Integração

### 6.1 Cadastro e Autenticação

1. Front-End envia `POST /api/auth/register` com username, password e email
2. Back-End valida dados e cria usuário em `UserRepository`
3. Back-End retorna 201 com `id`, `username` e `email`
4. Front-End envia `POST /api/auth/login` com identifier (email ou username) e password
5. Back-End valida credenciais e gera JWT
6. Back-End retorna 200 com `token`, `id`, `username` e `coinsBalance`
7. Front-End armazena token localmente
8. Front-End usa token em header `Authorization: Bearer <token>` para requisições subsequentes

**Observações:**
- Logout é local no cliente (sem endpoint de revogação)
- Token permanece válido até expiração (30 dias por padrão)

### 6.2 Criação e Entrada em Sala

1. Front-End envia `POST /api/rooms` com token de autenticação
2. Back-End valida usuário e cria sala com código de 4 dígitos
3. Back-End retorna 201 com `RoomDTO` (id, code, status, player1Id, player1Username)
4. Front-End estabelece conexão WebSocket em `/ws`
5. Front-Envia envia frame CONNECT com `Authorization: Bearer <token>`
6. Back-End valida token via `StompJwtAuthenticationInterceptor`
7. Front-Envia envia `SEND /app/rooms/{code}/connect`
8. Back-End registra sessão e marca jogador conectado
9. Back-End publica `ROOM_SNAPSHOT` em `/topic/rooms/{code}` e `/user/queue/room`
10. Front-End assina `/topic/rooms/{code}` e `/user/queue/room`
11. Front-Envia atualiza UI com snapshot recebido

**Observações:**
- Identidade do jogador vem do JWT, não do corpo da mensagem
- Segundo jogador entra via `POST /api/rooms/{code}/join`

### 6.3 Conexão STOMP e Sincronização Inicial

1. Front-End conecta WebSocket em `ws://localhost:8080/ws`
2. Front-Envia envia CONNECT com `Authorization: Bearer <token>`
3. Back-End valida token e configura Principal
4. Front-Envia assina `/topic/rooms/{code}` e `/user/queue/room`
5. Front-Envia envia `SEND /app/rooms/{code}/connect`
6. Back-End publica `ROOM_SNAPSHOT` público e privado
7. Front-Envia atualiza estado com snapshot

**Observações:**
- Se conexão for perdida, usar `SEND /app/rooms/{code}/sync` para resincronizar
- Snapshot privado inclui deck e campos específicos do usuário

### 6.4 Entrada do Segundo Jogador

1. Jogador 2 envia `POST /api/rooms/{code}/join`
2. Back-End valida e adiciona jogador 2 à sala
3. Back-End retorna 200 com `RoomDTO` completo
4. Back-End emite `PlayerJoinedRoomEvent`
5. `RoomSnapshotEventPublisher` publica `ROOM_SNAPSHOT`
6. Ambos jogadores recebem snapshot atualizado via `/topic/rooms/{code}` e `/user/queue/room`

**Observações:**
- Sala muda status de WAITING para MATCHED automaticamente
- Snapshot atualizado inclui ambos jogadores

### 6.5 Seleção de Habilidades (Draft)

1. Sala entra em fase DRAFT
2. Back-End publica `ROOM_SNAPSHOT` com fase DRAFT
3. Front-Envia mostra UI de seleção de personagens e habilidades
4. Jogador seleciona personagem (fluxo separado, não documentado aqui)
5. Jogador envia `SEND /app/rooms/{code}/draft/abilities` com lista de 2 habilidades
6. Back-End valida habilidades (devem pertencer ao inventário)
7. Back-End atualiza estado de Draft no Redis
8. Back-End emite `DraftUpdated`
9. `DraftEventPublisher` publica `DRAFT_UPDATE` em `/topic/rooms/{code}`
10. `RoomSnapshotEventPublisher` publica `ROOM_SNAPSHOT`
11. Front-Envia atualiza UI com seleções e estados de pronto

**Observações:**
- Habilidades são do inventário do usuário (`GET /api/skills/inventory`)
- Deck do round é gravado no estado Redis
- Até deck configurado, ativações de habilidade falham com `SKILL_NOT_IN_DECK`

### 6.6 Início e Andamento da Partida

1. Ambos jogadores confirmam o Draft inicial. A BE-013.1 abre `ROUND_DRAW`, com
   três opções privadas para cada jogador e prazo de 8 segundos. Quem não escolhe
   recebe uma escolha automática entre suas opções. O tempo não conta para W.O.
2. Depois de salvar os dois decks (duas fixas e uma sorteada), o evento interno
   `RoundDrawCompleted` libera a fase `PLAYING` e o timer do turno. Isso se repete
   a cada round, limpando consumo e efeitos sem zerar os contadores de timeout.
3. Back-End publica `ROOM_SNAPSHOT` com tabuleiro vazio e turno do primeiro jogador
4. Jogador da vez envia `SEND /app/game/{gameId}/move` com posição
5. Back-End valida jogada via `MoveActionService`
6. Back-End atualiza estado do jogo no Redis
7. Back-End emite `TurnChangedEvent` (interna)
8. Atualizações publicadas via broadcast (destino público pendente de alinhamento BE-024)
9. Jogador pode ativar habilidade via `SEND /app/game/{gameId}/skill`
10. Processamento similar, com validações específicas de habilidade

**Observações:**
- Turno tem deadline (30 segundos por padrão)
- Timeout de turno é tratado pelo Back-End
- Habilidades são validadas contra deck do round

### 6.7 Atualizações do Tabuleiro

1. Jogada ou habilidade processada com sucesso
2. Back-End atualiza estado do tabuleiro no Redis
3. Back-End publica `ROOM_SNAPSHOT` com tabuleiro atualizado
4. Snapshot inclui `board` com `Cell` objetos (index, owner, effects)
5. Front-Envia atualiza UI com novo estado do tabuleiro
6. Efeitos de células (ESCUDO, CONGELAR, REFORCO) incluídos

**Observações:**
- `Cell.owner`: null, "P1" ou "P2"
- `Cell.effects`: lista de efeitos ativos com turnos restantes
- Snapshot é fonte única de verdade para estado do tabuleiro

### 6.8 Encerramento de Rodada e Partida

1. Vitória, empate ou timeout detectado
2. Back-End finaliza rodada
3. Back-End publica `ROOM_SNAPSHOT` com fase ROUND_END
4. Back-End calcula recompensas e atualiza placar
5. Se partida não finalizada, inicia nova rodada
6. Se partida finalizada, muda fase para RESULT
7. Back-End publica `ROOM_SNAPSHOT` com `outcome` preenchido
8. Back-End grava resultado em histórico
9. Front-Envia mostra tela de resultado

**Observações:**
- `Outcome.kind`: "WIN", "ABSOLUTE_DRAW" ou "WO"
- `Outcome.winner`: "P1" ou "P2" (null para empate)
- `Outcome.score`: lista de placar [p1, p2]
- Recompensas em moedas enviadas via `coins` no snapshot

### 6.9 Desconexão e Reconexão de Jogadores

1. Conexão WebSocket perdida
2. `PlayerDisconnectionListener` detecta desconexão
3. Back-End marca jogador como desconectado
4. `ROOM_SNAPSHOT` publicado com `connected: false` para o jogador
5. Front-Envia reconecta WebSocket
6. Front-Envia envia CONNECT com token
7. Front-Envia envia `SEND /app/rooms/{code}/sync`
8. Back-Envia envia snapshot privado completo para `/user/queue/room`
9. Front-Envia restaura estado com snapshot recebido

**Observações:**
- Jogador pode continuar jogando mesmo se desconectado (timeout de partida separado)
- Reconexão via `sync` restaura estado completo
- Durante o sorteio do round, o snapshot privado restaura as opções pendentes e
  o prazo original, sem abrir uma nova janela de 8 segundos.
- Após reiniciar o backend, `RoundSkillDrawService` usa `SCAN` para localizar
  estados de partida no Redis. Sorteios pendentes são reagendados pelo prazo salvo;
  os vencidos são resolvidos imediatamente. Se ambas as escolhas já estavam salvas
  mas o turno não havia começado, o evento de conclusão é reemitido para liberar
  o turno. Não depende de callback ou timer da instância anterior.

### 6.10 Tratamento e Recuperação de Erros

1. Erro em requisição REST
2. Back-End retorna `ApiError` com código apropriado
3. Front-Envia mostra mensagem baseada em `code` e `message`
4. Erro em mensagem STOMP
5. Back-Envia envia `ApiError` para `/user/queue/errors`
6. Front-Envia assina `/user/queue/errors` e trata erros
7. Erro de autenticação STOMP
8. Conexão rejeitada, sem mensagem garantida em fila de erros
9. Front-Envia trata falha de conexão e reconecta

**Observações:**
- Para erros REST, usar `code` para decisão de tratamento
- Para erros STOMP, mostrar `message` ao usuário
- Reautenticar e reconectar em caso de erro de autenticação

---

## 7. Estados, Enums e Regras de Negócio

### 7.1 RoomStatus

**Valores:**
- `WAITING`: Sala aguardando segundo jogador
- `IN_GAME`: Partida em andamento
- `CLOSED`: Sala encerrada

**Transições:**
- WAITING → IN_GAME: quando segundo jogador entra e partida é iniciada
- IN_GAME → CLOSED: quando partida é finalizada

**Implementação:** `RoomStatus` enum (linha 3-5 de `RoomStatus.java`)

### 7.2 RoomPhase

**Valores:**
- `WAITING`: Sala aguardando segundo jogador
- `MATCHED`: Sala com dois jogadores, aguardando início
- `DRAFT`: Fase de seleção de personagens e habilidades
- `VERSUS`: Fase de transição para início da partida
- `ROUND_DRAW`: Sorteio de habilidades para o round
- `PLAYING`: Partida em andamento
- `ROUND_END`: Rodada finalizada, aguardando próxima
- `RESULT`: Partida finalizada, mostrando resultado

**Implementação:** `RoomSnapshotService.RoomPhase` (linha 247)

**Derivação:**
- Baseada em `RoomStatus` e `GamePhase` do estado do jogo
- Calculada em `RoomSnapshotService.phase` (linha 216-230)

### 7.3 CharacterSymbol

**Valores:**
- `X`: Símbolo X
- `O`: Símbolo O
- `TRIANGULO`: Triângulo
- `QUADRADO`: Quadrado
- `ESTRELA`: Estrela
- `CORACAO`: Coração

**Implementação:** `CharacterSymbol` enum (linha 3-11 de `CharacterSymbol.java`)

**Uso:**
- Identifica personagem visual do jogador
- Usado em Draft e na partida

### 7.4 Slots de Jogador

**Valores:**
- `P1`: Jogador 1 (host da sala)
- `P2`: Jogador 2 (convidado)

**Implementação:** `RoomSnapshotService.Slot` (linha 246)

**Uso:**
- Identifica posição do jogador na sala
- Usado em snapshots e mensagens STOMP

### 7.5 Códigos de Habilidade

**Valores:**
- `BOMBA`: Remove uma peça adversária
- `ESCUDO`: Protege peça por um turno
- `TORNADO`: Move peça para posição adjacente
- `BURACO_NEGRO`: Remove peça de qualquer posição
- `AMPULHETA`: Aumenta tempo de turno
- `CONGELAR`: Congela peça por um turno
- `CAMPO_DE_FORCA`: Protege peça por múltiplos turnos
- `TROCA_RAPIDA`: Troca peças de posição
- `REFORCO`: Aumenta defesa de peça
- `SILENCIO`: Impede uso de habilidades

**Implementação:** `Skill.code` (propriedade da entidade `Skill`)

**Uso:**
- Identificador de integração de habilidades
- Usado em ativação (`SEND /app/game/{gameId}/skill`)
- Deve pertencer ao inventário do usuário

### 7.6 Erros de Habilidade

**Códigos:**
- `SKILL_INVALID_TARGET`: Alvo inválido (posição, ocupação, proprietário ou bloqueio)
- `SKILL_INVALID_DESTINATION`: Destino inválido (fora do tabuleiro, ocupado, bloqueado ou não adjacente)
- `SKILL_SILENCED`: Jogador sob Silêncio
- `SKILL_ALREADY_USED`: Habilidade já usada no round atual
- `SKILL_NOT_IN_DECK`: Habilidade não pertence ao deck do round

**Resultados válidos (não erros):**
- `NO_VALID_TARGET`: Sem alvo válido (consome habilidade e ação)
- `BLOCKED_BY_FORCE_FIELD`: Bloqueado por Campo de Força (consome habilidade e ação)

**Implementação:** `SkillValidationException` com códigos específicos

**Handler:** `WebSocketExceptionHandler.handleSkillValidation` (linha 16-21)

### 7.7 Regras de Turno

**Duração padrão:** 30 segundos (configurável)

**Timeout:**
- Turno encerrado automaticamente após deadline
- Jogador perde ação do turno
- Consecutive timeouts incrementados
- Múltiplos timeouts podem resultar em W.O.

**Implementação:** `GameState.getTurnDeadlineEpochMillis()` e `GameState.getTurnDurationMillis()`

**Uso:**
- `Turn` object em `ROOM_SNAPSHOT` inclui `startedAt` e `durationSeconds`
- Front-Envia deve mostrar countdown e bloquear ações após timeout

### 7.8 Regras de Recompensa

**Recompensas em moedas:**
- Calculadas ao final da partida
- Baseadas em resultado e performance
- Enviadas via `coins` field em `ROOM_SNAPSHOT` quando partida finalizada

**Implementação:** `RewardResult` (linha 172-176 de `RoomSnapshotService.java`)

**Uso:**
- `Map<Slot, Integer>` com recompensas para P1 e P2
- Null se partida não finalizada

---

## 8. Catálogo de Erros

### 8.1 Formato do Erro

**REST e STOMP:** `ApiError`

```json
{
  "code": "ERROR_CODE",
  "message": "Mensagem descritiva",
  "status": 400,
  "timestamp": "2026-10-26T10:30:00Z",
  "path": "/api/endpoint",
  "fields": { "field": "Mensagem de validação" }
}
```

### 8.2 Códigos de Erro REST

| Código | Status HTTP | Quando ocorre | Handler |
|--------|-------------|---------------|---------|
| `USERNAME_ALREADY_EXISTS` | 409 | Username já cadastrado | `GlobalExceptionHandler.handleUsernameExists` |
| `EMAIL_ALREADY_EXISTS` | 409 | E-mail já cadastrado | `GlobalExceptionHandler.handleEmailExists` |
| `INVALID_CREDENTIALS` | 401 | Credenciais de login inválidas | `GlobalExceptionHandler.handleInvalidCredentials` |
| `VALIDATION_ERROR` | 400 | Bean validation ou constraint violation | `GlobalExceptionHandler.handleConstraintViolation` / `handleMethodArgumentNotValid` |
| `INVALID_REQUEST_BODY` | 400 | Corpo JSON ilegível | `GlobalExceptionHandler.handleHttpMessageNotReadable` |
| `NOT_FOUND` | 404 | Rota ou recurso não encontrado | `GlobalExceptionHandler.handleNoHandlerFoundException` / `handleNoResourceFoundException` |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP não permitido | `GlobalExceptionHandler.handleHttpRequestMethodNotSupported` |
| `BAD_REQUEST` | 400 | Parâmetro inválido ou ausente | `GlobalExceptionHandler.handleTypeMismatch` / `handleMissingServletRequestParameter` |
| `ROOM_BAD_REQUEST` | 400 | Código de sala inválido ou usuário inválido | `GlobalExceptionHandler.handleRoomBadRequest` |
| `ROOM_NOT_FOUND` | 404 | Sala inexistente ou encerrada | `GlobalExceptionHandler.handleRoomNotFound` |
| `ROOM_FULL` | 409 | Sala já possui dois jogadores | `GlobalExceptionHandler.handleRoomFull` |
| `ROOM_ALREADY_STARTED` | 409 | Partida da sala já iniciada | `GlobalExceptionHandler.handleRoomAlreadyStarted` |
| `ROOM_CONFLICT` | 409 | Jogador já em sala ou partida ativa | `GlobalExceptionHandler.handleRoomConflict` |
| `UNAUTHORIZED` | 401 | Rota protegida sem autenticação | `JwtAuthenticationEntryPoint` |
| `FORBIDDEN` | 403 | Acesso negado a recurso protegido | `ApiAccessDeniedHandler` |
| `INTERNAL_ERROR` | 500 | Exceção inesperada ou erro 5xx | `GlobalExceptionHandler.handleUnexpected` |

### 8.3 Códigos de Erro STOMP

| Código | Status HTTP | Quando ocorre | Handler |
|--------|-------------|---------------|---------|
| `SKILL_INVALID_TARGET` | 400 | Alvo inválido para habilidade | `WebSocketExceptionHandler.handleSkillValidation` |
| `SKILL_INVALID_DESTINATION` | 400 | Destino inválido para habilidade | `WebSocketExceptionHandler.handleSkillValidation` |
| `SKILL_SILENCED` | 400 | Jogador sob Silêncio | `WebSocketExceptionHandler.handleSkillValidation` |
| `SKILL_ALREADY_USED` | 400 | Habilidade já usada no round | `WebSocketExceptionHandler.handleSkillValidation` |
| `SKILL_NOT_IN_DECK` | 400 | Habilidade não no deck do round | `WebSocketExceptionHandler.handleSkillValidation` |
| `BAD_REQUEST` | 400 | Requisição inválida genérica | `WebSocketExceptionHandler.handleBadRequest` / `handleMoveValidation` |
| `FORBIDDEN` | 403 | Acesso negado | `WebSocketExceptionHandler.handleAccessDenied` |
| `CONFLICT` | 409 | Conflito de estado | `WebSocketExceptionHandler.handleConflict` |
| `INTERNAL_SERVER_ERROR` | 500 | Erro interno | `WebSocketExceptionHandler.handleUnexpected` |

### 8.4 Condições que Provocam Erros

**USERNAME_ALREADY_EXISTS:**
- Tentativa de cadastro com username já existente (case-insensitive)

**EMAIL_ALREADY_EXISTS:**
- Tentativa de cadastro com e-mail já existente (case-insensitive)

**INVALID_CREDENTIALS:**
- Login com identifier não encontrado
- Login com senha incorreta

**VALIDATION_ERROR:**
- Campo obrigatório ausente
- Campo fora do tamanho permitido
- Formato inválido (e-mail, código de sala)
- Inclui `fields` com detalhes de cada campo inválido

**ROOM_BAD_REQUEST:**
- Código de sala não é 4 dígitos
- Usuário inválido ou não encontrado

**ROOM_NOT_FOUND:**
- Sala não existe no banco
- Sala existe mas está encerrada (CLOSED)

**ROOM_FULL:**
- Sala já tem 2 jogadores
- Tentativa de entrar em sala cheia

**ROOM_ALREADY_STARTED:**
- Tentativa de entrar em sala com partida em andamento
- Tentativa de sair de sala em partida (não suportado)

**ROOM_CONFLICT:**
- Jogador já está em outra sala
- Jogador já está em partida ativa

**SKILL_INVALID_TARGET:**
- Posição fora do tabuleiro
- Posição vazia para habilidade que requer peça
- Peça do próprio jogador para habilidade ofensiva
- Peça bloqueada por efeito

**SKILL_INVALID_DESTINATION:**
- Destino fora do tabuleiro
- Destino ocupado
- Destino bloqueado por Escudo
- Destino não adjacente (para Tornado)

**SKILL_SILENCED:**
- Jogador com contador de Silêncio > 0

**SKILL_ALREADY_USED:**
- Tentativa de usar mesma habilidade duas vezes no round

**SKILL_NOT_IN_DECK:**
- Habilidade não está no deck do round atual
- Deck ainda não configurado (Draft incompleto)

### 8.5 Comportamento Recomendado para Front-End

**Erros REST:**
- Usar `code` para decidir tratamento
- Mostrar `message` ao usuário
- Para `VALIDATION_ERROR` com `fields`, mostrar erros específicos por campo
- Para 401/403, reautenticar ou redirecionar para login
- Para 409, informar conflito e permitir ação alternativa

**Erros STOMP:**
- Assinar `/user/queue/errors`
- Mostrar `message` ao usuário
- Para erros de habilidade, explicar condição específica
- Para `FORBIDDEN`, verificar autenticação e permissões
- Para `INTERNAL_SERVER_ERROR`, mostrar erro genérico e permitir reconexão

**Recuperação:**
- Reautenticar em caso de 401
- Reconectar WebSocket em caso de falha de conexão
- Sincronizar estado via `SEND /app/rooms/{code}/sync` após reconexão
- Evitar loop infinito de reconexões em erros persistentes

---

## 9. Checklist de Integração

### 9.1 Autenticação

- [ ] Implementar cadastro via `POST /api/auth/register`
- [ ] Implementar login via `POST /api/auth/login`
- [ ] Armazenar token JWT localmente
- [ ] Incluir `Authorization: Bearer <token>` em requisições REST protegidas
- [ ] Implementar conexão WebSocket com token no CONNECT
- [ ] Tratar erros 401/403 e reautenticar quando necessário
- [ ] Implementar logout local (remover token)

### 9.2 Chamadas REST

- [ ] Implementar criação de sala via `POST /api/rooms`
- [ ] Implementar entrada em sala via `POST /api/rooms/{code}/join`
- [ ] Implementar saída de sala via `POST /api/rooms/{code}/leave`
- [ ] Implementar consulta de perfil via `GET /api/profile`
- [ ] Implementar consulta de estatísticas via `GET /api/profile/stats`
- [ ] Implementar atualização de username via `PATCH /api/profile/username`
- [ ] Implementar consulta de histórico via `GET /api/profile/history`
- [ ] Implementar consulta de saldo via `GET /api/coins/balance`
- [ ] Implementar consulta de inventário de habilidades via `GET /api/skills/inventory`
- [ ] Implementar consulta de inventário de personagens via `GET /api/characters/inventory`
- [ ] Implementar health check via `GET /api/health`

### 9.3 Conexão STOMP

- [ ] Estabelecer conexão WebSocket em `/ws`
- [ ] Enviar CONNECT com `Authorization: Bearer <token>`
- [ ] Tratar falha de autenticação no CONNECT
- [ ] Assinar `/topic/rooms/{code}` para atualizações públicas
- [ ] Assinar `/user/queue/room` para snapshots privados
- [ ] Assinar `/user/queue/errors` para mensagens de erro
- [ ] Implementar reconexão automática em caso de desconexão

### 9.4 Assinaturas e Sincronização

- [ ] Enviar `SEND /app/rooms/{code}/connect` ao entrar em sala
- [ ] Enviar `SEND /app/rooms/{code}/sync` para resincronizar estado
- [ ] Processar `ROOM_SNAPSHOT` e atualizar UI
- [ ] Processar `DRAFT_UPDATE` e atualizar UI de Draft
- [ ] Usar `mySlot` para identificar posição do usuário
- [ ] Usar `myDeck` para mostrar habilidades disponíveis
- [ ] Atualizar tabuleiro com base em `board` do snapshot

### 9.5 Eventos

- [ ] Tratar `ROOM_SNAPSHOT` como fonte única de verdade
- [ ] Atualizar fase da sala com base em `phase`
- [ ] Atualizar estado de jogadores com base em `players`
- [ ] Atualizar turno com base em `turn`
- [ ] Atualizar resultado com base em `outcome`
- [ ] Mostrar efeitos de células com base em `board[].effects`

### 9.6 Erros

- [ ] Implementar tratamento de erros REST com base em `code`
- [ ] Implementar tratamento de erros STOMP via `/user/queue/errors`
- [ ] Mostrar mensagens de erro ao usuário
- [ ] Implementar validações no cliente para evitar erros óbvios
- [ ] Tratar erros de habilidade específicos (SKILL_*)

### 9.7 Fluxos Críticos

- [ ] Fluxo completo de cadastro e login
- [ ] Fluxo completo de criação e entrada em sala
- [ ] Fluxo completo de conexão STOMP e sincronização
- [ ] Fluxo completo de seleção de habilidades (Draft)
- [ ] Fluxo completo de jogada e ativação de habilidades
- [ ] Fluxo completo de encerramento de partida
- [ ] Fluxo de desconexão e reconexão

### 9.8 Verificações Executadas

Durante a auditoria, as seguintes verificações foram executadas:

- [x] Leitura e análise de todos controllers REST
- [x] Leitura e análise de todos controllers STOMP
- [x] Leitura e análise de DTOs de entrada e saída
- [x] Leitura e análise de configurações de segurança e WebSocket
- [x] Leitura e análise de handlers de erro REST e STOMP
- [x] Leitura e análise de publishers de eventos
- [x] Leitura e análise de enums e estados
- [x] Verificação de rotas públicas em SecurityConfig
- [x] Verificação de interceptador STOMP
- [x] Verificação de serviços de snapshot e draft
- [x] Listagem de testes existentes (não executados)

---

## 10. Histórico e Rastreabilidade

### 10.1 Informações da V2

- **Versão:** 2.0
- **Data de elaboração:** 26/10/2026
- **Autor:** Auditoria de código-fonte (Devin)
- **Branch analisada:** `dev`
- **Metodologia:** Análise estática de controllers, DTOs, services, configs e handlers

### 10.2 Principais Diferenças em Relação à Documentação Anterior

**Estrutura:**
- V1: Organização mista com propostas e implementação
- V2: Separação clara entre implementado e planejado

**Evidências:**
- V1: Algumas afirmações sem referência ao código
- V2: Todas as afirmações com referência a arquivos e linhas específicas

**WebSocket/STOMP:**
- V1: Documentação parcial com alguns destinos pendentes
- V2: Documentação completa com destinos, envelopes, payloads e publishers

**Erros:**
- V1: Catálogo misto de REST e STOMP
- V2: Separação clara entre erros REST e STOMP, com handlers específicos

**Snapshots:**
- V1: Menos detalhes sobre diferenças entre público e privado
- V2: Documentação explícita de campos individuais por usuário

**Fluxos:**
- V1: Fluxos descritos de forma menos detalhada
- V2: Fluxos passo-a-passo com referências a controllers e serviços

### 10.3 Limitações da Auditoria

- **Testes não executados:** Testes foram listados mas não executados
- **Integração real não testada:** Apenas análise estática de código
- **Endpoint de gameplay broadcast:** Destino público final pendente de alinhamento com front (BE-024)
- **Draft de personagens:** Fluxo de seleção de personagens não detalhado (pode estar em outra parte do código)
- **Loja:** Endpoints de compra não implementados (BE-029 planejado)
- **Recuperação de senha:** Não implementado (BE-006.1 planejado)

### 10.4 Pontos Pendentes de Confirmação

- X e O são atribuídos a todo jogador por padrão (RN-LOJ-01)?
- Destino público de atualizações de gameplay (BE-024)
- Fluxo completo de seleção de personagens no Draft
- Detalhes de implementação de W.O. em partida
- Detalhes de implementação de rematch

### 10.5 Compatibilidade com Front-End

- Todos os contratos REST documentados estão implementados
- Todos os destinos de entrada (SEND) e assinatura (SUBSCRIBE) documentados estão implementados
- Os destinos de saída existem, mas os eventos de gameplay publicados ainda não têm contrato público final alinhado (BE-024)
- DTOs documentados correspondem exatamente ao código
- Enums documentados correspondem exatamente ao código
- Erros documentados correspondem aos handlers implementados

**Incompatibilidades identificadas:**
- O contrato público final dos eventos de atualização de gameplay ainda está pendente (BE-024); a compatibilidade integral dos eventos de saída segue em aberto.

**Bloqueadores de integração:**
- Destino e formato públicos finais para eventos de atualização de gameplay pendentes (BE-024), o que pode impactar sincronização de jogadas em tempo real.

---

## 11. Referências

### 11.1 Arquivos do Código-Fonte

**Controllers REST:**
- `AuthController.java` - Endpoints de autenticação
- `RoomController.java` - Endpoints de salas
- `ProfileController.java` - Endpoints de perfil
- `SkillController.java` - Endpoint de inventário de habilidades
- `CharacterController.java` - Endpoint de inventário de personagens
- `CoinController.java` - Endpoint de saldo
- `HealthController.java` - Endpoint de health check

**Controllers STOMP:**
- `PlayerConnectionController.java` - Conexão e sincronização
- `GameplayController.java` - Jogadas e habilidades
- `DraftAbilitiesController.java` - Seleção de habilidades no Draft

**Configurações:**
- `SecurityConfig.java` - Configuração de segurança REST
- `WebSocketConfig.java` - Configuração WebSocket
- `StompJwtAuthenticationInterceptor.java` - Interceptador de autenticação STOMP

**Erro:**
- `GlobalExceptionHandler.java` - Handler de erros REST
- `WebSocketExceptionHandler.java` - Handler de erros STOMP
- `ApiError.java` - DTO de erro

**Serviços:**
- `RoomSnapshotService.java` - Composição e publicação de snapshots
- `DraftEventPublisher.java` - Publicação de eventos de Draft
- `RoomSnapshotEventPublisher.java` - Publicação de snapshots em eventos
- `WebSocketService.java` - Serviço de envio de mensagens STOMP

**DTOs:**
- `RoomDTO.java` - DTO de sala
- `RegisterRequest.java` / `RegisterResponse.java` - DTOs de cadastro
- `LoginRequest.java` / `LoginResponse.java` - DTOs de login
- `SkillDTO.java` - DTO de habilidade
- `CharacterDTO.java` - DTO de personagem
- `ProfileResponse.java` - DTO de perfil
- `ProfileStatsResponse.java` - DTO de estatísticas
- `MatchHistoryResponse.java` - DTO de histórico
- `BalanceResponse.java` - DTO de saldo

**Enums:**
- `RoomStatus.java` - Status de sala
- `CharacterSymbol.java` - Símbolo de personagem

### 11.2 Documentação Anterior

- `docs/contrato-integracao.md` - Versão anterior (preservada)

### 11.3 Swagger/OpenAPI

- `/swagger-ui.html` - Interface Swagger
- `/v3/api-docs` - Especificação OpenAPI

---

**Fim do documento V2**
