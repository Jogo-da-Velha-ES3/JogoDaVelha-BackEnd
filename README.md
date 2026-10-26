# JogoDaVelha-BackEnd
Backend de um projeto acadêmico de Engenharia de Software III — Jogo da Velha inovador multiplayer

## 📋 Visão Geral

Backend de jogo da velha multiplayer por turnos usando Spring Boot + Maven, seguindo uma arquitetura modular com PostgreSQL para dados persistentes e Redis para gerenciamento de estado rápido/temporário.

## 📌LEIA ANTES: Regras Gerais do Back-End

**Branch e Git**

- Nunca commitar direto na main/dev — criar branch própria
(ex: feat/nome-da-tarefa ; refactor/nome-da-tarefa ; fix/nome-da-tarefa)

- Atualizar sua branch com a dev regularmente para evitar divergência (git pull origin dev)

- Merge pra dev só via Pull Request, com outra pessoa revisando

**Commits e PR**

- Commits pequenos e com mensagem clara (ex: "fix: corrige validação de login")

- PR focada em uma tarefa só, com descrição rápida do que mudou

**Qualidade**

- Não subir código quebrando o que já funciona (testar antes localmente)

- Não commitar senha, token ou arquivo .env (seguir .env.example)

**Documentação**

- Atualizar o README quando criar/mudar algo importante (endpoint, setup, etc.)

- Endpoints documentados no Swagger (anotações OpenAPI aplicadas).

**Contrato de integração com o front** (`docs/contrato-integracao.md`)

- Todo PR que cria, altera ou remove endpoint REST ou destino/evento STOMP deve atualizar o contrato na mesma PR; o revisor não aprova sem isso.
- Ao mergear, o item passa a ✅; enquanto estiver em andamento, fica 🚧/📝 com o card.
- Mudança que quebra compatibilidade (rota, campo, `code` de erro, destino, nome de evento, remoção) é avisada ao front antes do merge e entra no changelog como `BREAKING`.
- Todo `code` de erro novo entra no catálogo do contrato.
- Checkbox para a descrição da PR: `- [ ] Contrato de integração atualizado (se criar/alterar endpoint ou evento)`.

#### Guia Rápido de Prefixos de branches (Padrão de Mercado)
Para manter o repositório limpo e seguro, a convenção internacional utiliza estas abreviações e palavras cheias:
- feat/ (Abreviação de Feature): Para novas funcionalidades.
- fix/ (Abreviação de Bugfix): Para correção de erros.
- refactor/ (Palavra cheia): Para melhorias de código sem alterar comportamento.
- docs/ (Abreviação de Documentation): Para alterações em README, wikis ou comentários.
- chore/ (Palavra cheia): Para tarefas repetitivas ou configurações (ex: atualizar o .gitignore).


## 🏗️ Arquitetura

O projeto está organizado em módulos principais sob o pacote `com.jogodavelha`:

- **`auth/`** — Autenticação, cadastro, permissões e geração/validação de JWT
- **`game/`** — Gerenciamento de partidas, lógica do jogo e serviços
  - **`game/service/`** — Gerenciamento de partidas, entrada/saída de jogadores, controle de turnos
  - **`game/logic/`** — Regras do jogo, validações e transições de estado (camada de validação oficial)
- **`websocket/`** — Gateway de conexões em tempo real (sem persistência)
- **`health/`** — Health check e monitoramento da API
- **`config/`** — Configurações de Security, WebSocket e Redis

## 🛠️ Stack Tecnológico

- **Framework**: Spring Boot 4.1.0
- **Linguagem**: Java 25
- **Build Tool**: Maven
- **Banco de Dados**: PostgreSQL (dados persistentes)
- **Cache/Estado**: Redis (estado temporário da partida, turnos, locks)
- **Segurança**: Spring Security + JWT
- **Tempo Real**: WebSocket (STOMP)
- **Validação**: Spring Boot Validation
- **Documentação**: SpringDoc OpenAPI (Swagger UI)
- **Utilitário**: Lombok

## 📦 Dependências e Decisões Técnicas

### Biblioteca JWT: JJWT (io.jsonwebtoken:jjwt)

**Escolha**: JJWT (v0.12.3) em vez de alternativas

**Justificativa**:
- Biblioteca madura e bem mantida com suporte comprehensive a JWT
- API clara para assinatura, parsing e validação de JWTs
- Forte suporte da comunidade e documentação
- Suporta assinatura e verificação out of the box
- Implementação JSON Web Token (JWT) com suporte a JWS e JWE

### Cliente Redis: Lettuce (Padrão)

**Escolha**: Lettuce (integrado ao Spring Boot starter)

**Justificativa**:
- Cliente padrão e recomendado para Spring Data Redis
- Baseado em Netty, assíncrono e não-bloqueante
- Melhor performance para cenários de alta concorrência
- Sem dependência adicional necessária (Jedis requereria dependência extra)
- Excelente integração com auto-configuração do Spring Boot

### Documentação de API: SpringDoc OpenAPI

**Escolha**: SpringDoc OpenAPI (v3.1.0)

**Justificativa**:
- Biblioteca oficial e recomendada para Spring Boot
- Suporte completo a OpenAPI 3 e Swagger UI
- Integração automática com Spring Boot sem configuração complexa
- Suporte nativo a Spring Security e JWT
- Ativa e mantida pela comunidade SpringDoc

### Estratégia de Profile de Desenvolvimento

**Abordagem**: Usar PostgreSQL e Redis fornecidos pelo Docker Compose

O profile de desenvolvimento (`application-dev.yml`) usa PostgreSQL e Redis. Ao executar via Docker Compose, o backend utiliza os serviços `postgres` e `redis` da mesma rede Docker.

## 🚀 Configuração e Execução

### Configuração do Ambiente

Para rodar o projeto, você precisa configurar as variáveis de ambiente. O projeto utiliza um arquivo `.env` para configuração local.

1. Copie o arquivo de exemplo:
```bash
cp .env.example .env
```

2. Edite o arquivo `.env` com seus valores de desenvolvimento. As variáveis necessárias são:
- `DB_USER` — Usuário do banco de dados PostgreSQL
- `DB_PASSWORD` — Senha do banco de dados PostgreSQL
- `DB_NAME` — Nome do banco de dados PostgreSQL
- `DB_PORT` — Porta do PostgreSQL (padrão: 5432)
- `REDIS_PORT` — Porta do Redis (padrão: 6379)
- `JWT_SECRET` — Chave secreta para assinatura JWT (mínimo 32 bytes, gere com `openssl rand -base64 32`)
- `JWT_EXPIRATION` — Tempo de expiração do token JWT em milissegundos (recomendado: 2592000000 = 30 dias para manter o usuário autenticado entre acessos)
- `SERVER_PORT` — Porta da aplicação (padrão: 8080)
- `CORS_ALLOWED_ORIGINS` — Origens CORS separadas por vírgula; aceita padrões com `*`. Padrão local: `http://localhost:5173`. Para o front, inclua `https://jogodavelha-frontend.vercel.app`, `https://jogodavelha-frontend-dev.vercel.app` e `https://*.vercel.app`.

**⚠️ NOVAS VARIÁVEIS (adicionar ao .env existente):**
- `GAME_STATE_TTL` — TTL em milissegundos para estado da partida (padrão: 1800000 = 30 minutos)
- `GAME_STATE_UPDATE_MAX_RETRIES` — Máximo de tentativas de retry em conflitos (padrão: 5)

### Como Rodar o Projeto

A forma recomendada de rodar o projeto é usando Docker Compose. Ele cria um ambiente completo com o backend, PostgreSQL e Redis, sem exigir Java, Maven ou os bancos instalados diretamente na máquina.

#### Via Docker Compose

**Pré-requisitos:**
- Docker Desktop instalado e em execução
- Arquivo `.env` criado na raiz do projeto

**Primeira execução:**

1. Crie o arquivo de ambiente a partir do exemplo:

```powershell
Copy-Item .env.example .env
```

2. Preencha o `.env` com os valores do PostgreSQL, Redis, JWT e da porta da API. O Docker Compose usa esse arquivo para substituir as variáveis presentes no `docker-compose.yml`.

3. Construa a imagem e inicie todos os serviços:

```bash
docker compose up --build
```

O comando inicia três serviços:

- `api`: compila e executa a aplicação Spring Boot usando o profile `dev`.
- `postgres`: executa o PostgreSQL 16 e armazena os dados no volume Docker `postgres-data`.
- `redis`: executa o Redis para dados temporários e estado das partidas.

O backend aguarda os healthchecks do PostgreSQL e do Redis antes de iniciar. A API fica disponível em `http://localhost:8080` (ou na porta definida em `SERVER_PORT`).

#### Via IntelliJ IDEA

Para executar o Docker Compose pelo botão **Run** do IntelliJ:

1. Abra **Run > Edit Configurations (três pontos ao lado do botão RUN)**.
2. Clique em **+ > Docker**.
3. Em **Compose files**, selecione o arquivo `docker-compose.yml`.
4. Em **Services**, selecione `postgres`, `redis` e `api`, ou deixe todos os serviços selecionados.
5. Na opção **Modify Options**, selecione **Build** e depois selecione **Always**. Isso corresponde ao comando `docker compose up --build` e reconstrói as imagens a cada execução.
6. Clique em **Apply** e depois em **Run**.

Antes de executar, confirme que o Docker Desktop está em execução e que o arquivo `.env` existe na raiz do projeto. Para acelerar execuções sem alterações no `Dockerfile`, a opção **Only missing images** pode ser usada no lugar de **Always**.

**Verificar os serviços em execução:**

```bash
docker compose ps
```

**Acompanhar os logs do backend:**

```bash
docker compose logs -f backend
```

Para acessar os logs de um serviço específico, substitua `api` por `postgres` ou `redis`.

**Acessar a documentação da API:**

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- WebSocket: `ws://localhost:8080/ws`

Para a tela de carregamento, use `GET /api/health`. Esse endpoint não acessa PostgreSQL
ou Redis e pode ser chamado para acordar a aplicação após um cold start. Quando a API
estiver pronta, ele retorna HTTP 200 com `{"status":"UP","message":"API disponível"}`.

**Como atualizar o backend:**

O código-fonte é copiado para a imagem durante o build. Por isso, `docker compose up` sem `--build` pode reutilizar uma imagem antiga e não refletir alterações na API. Depois de alterar o código, execute:

```bash
docker compose up --build
```

**Regra de atualização do Docker:** o front-end deve sempre consumir a imagem gerada a partir da branch dev (via git pull na dev + docker compose up --build, ou via imagem publicada no Docker Hub a partir da dev). 
Desenvolvedores de back-end podem rodar docker compose up --build livremente em suas próprias branches de feature para testes locais — isso não deve ser publicado nem repassado ao front-end até ser mergeado na dev.

Para atualizar o código a partir da branch de desenvolvimento:

```bash
git pull origin dev
docker compose up --build
```

O Docker reaproveita as camadas que não mudaram, incluindo o download das dependências Maven, tornando os rebuilds seguintes mais rápidos.

**Para parar o ambiente:**

```bash
docker compose down
```

Esse comando remove os containers, mas preserva o volume `postgres-data`. Portanto, os dados do PostgreSQL não são apagados.

Para parar e também apagar os dados persistidos, use somente quando isso for intencional:

```bash
docker compose down -v
```
<!--
#### Via Maven (Desenvolvimento Local)

O profile `dev` usa PostgreSQL e Redis. Ao executar o backend diretamente na máquina, esses serviços precisam estar instalados e em execução localmente, ou disponíveis em containers com as portas publicadas para `localhost`.

**Pré-requisitos:**

- Java 25 ou superior
- Maven 3.6+ (ou usar Maven wrapper)
- PostgreSQL e Redis instalados e rodando

O arquivo `.env` não é carregado automaticamente pelo Maven. Configure as variáveis no ambiente do terminal ou na configuração de execução da IDE. Exemplo no PowerShell:

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/jogodavelha"
$env:DB_USER = "postgres"
$env:DB_PASSWORD = "postgres"
$env:REDIS_HOST = "localhost"
$env:REDIS_PORT = "6379"
```

Depois, inicie a aplicação:

```bash
# Maven instalado
mvn spring-boot:run

# Maven Wrapper no Windows
.\mvnw.cmd spring-boot:run
```

A aplicação iniciará em `http://localhost:8080`. Os endpoints de documentação e WebSocket são os mesmos da execução via Docker.

Não execute o backend local e o serviço `backend` do Docker Compose simultaneamente na mesma porta. Se quiser executar o código local usando os bancos do Compose, mantenha apenas `postgres` e `redis` em execução e inicie o backend pelo Maven.
 -->

## 📁 Estrutura do Projeto

```
com.jogodavelha
├── auth/                   # Autenticação e autorização
├── config/                 # Classes de configuração
├── game/
│   ├── service/            # Serviços de gerenciamento de jogo
│   └── logic/              # Regras e validações do jogo
├── health/                 # Verificar disponibilidade da API
├── websocket/              # Comunicação em tempo real
└── JogoDaVelhaApplication  # Classe principal da aplicação
```

### Organização os domínios

Cada domínio do sistema deve possuir sua própria organização, deve ser criado um pacote próprio para ela dentro de `com.jogodavelha`.

As classes relacionadas ao domínio devem ficar dentro do mesmo pacote, seguindo a separação de responsabilidades entre Controller, Service, Entity/Model, Repository e DTO quando forem necessários.

Por exemplo, para adicionar o domínio de **personagens**:

```text
com.jogodavelha
├── auth/
├── config/
├── game/
│   ├── service/ 
│   └── logic/ 
├── websocket/
├── character/
│   ├── CharacterController.java
│   ├── CharacterService.java
│   ├── Character.java
│   ├── CharacterRepository.java
│   └── CharacterDTO.java
└── JogoDaVelhaApplication.java
```

Nem todo domínio precisa obrigatoriamente possuir todas essas classes. Devem ser criadas apenas as que forem necessárias.

Por exemplo:

* `CharacterController` → recebe e responde às requisições HTTP relacionadas aos personagens.
* `CharacterService` → contém a lógica do domínio.
* `Character` → representa a entidade/modelo de um personagem.
* `CharacterRepository` → responsável pelo acesso aos dados dos personagens.
* `CharacterDTO` → define os dados utilizados na comunicação entre API e cliente.

**Regra geral:** novas funcionalidades devem ser organizadas por domínio, mantendo juntas as classes que pertencem à mesma responsabilidade e evitando criar pacotes globais separados como `controllers/`, `services/`, `repositories/` e `dtos/` para todo o projeto.

```

Isso deixa a ideia bem mais clara: character é um domínio e dentro dela ficam as diferentes responsabilidades daquele domínio, em vez de espalhar `CharacterController`, `CharacterService`, etc. por vários pacotes globais.
```


## 🔧 Configuração

### Variáveis de Ambiente

- `DB_URL` — URL de conexão PostgreSQL (usado internamente pelo Docker Compose)
- `DB_USER` — Usuário do banco de dados
- `DB_PASSWORD` — Senha do banco de dados
- `DB_NAME` — Nome do banco de dados
- `DB_PORT` — Porta do PostgreSQL (padrão: 5432)
- `REDIS_HOST` — Host do servidor Redis (usado internamente pelo Docker Compose)
- `REDIS_PORT` — Porta do servidor Redis (padrão: 6379)
- `JWT_SECRET` — Chave secreta para assinatura JWT (mínimo 32 bytes, gere com `openssl rand -base64 32`)
- `JWT_EXPIRATION` — Tempo de expiração do token JWT em milissegundos (ex: 86400000 = 24 horas, 2592000000 = 30 dias)
- `SERVER_PORT` — Porta da aplicação (padrão: 8080)
- `SPRING_PROFILES_ACTIVE` — Profile ativo (padrão: dev)

**⚠️ NOVAS VARIÁVEIS (BE-017):**
- `GAME_STATE_TTL` — TTL em milissegundos para estado da partida (padrão: 1800000)
- `GAME_STATE_UPDATE_MAX_RETRIES` — Máximo de tentativas de retry em conflitos (padrão: 5)

### Arquivos de Configuração

- `application.yml` — Configuração principal com padrões de variáveis de ambiente
- `application-dev.yml` — Profile de desenvolvimento (PostgreSQL + Redis via docker)
- `application-prod.yml` — Profile de produção (PostgreSQL + Redis)

## 🗄️ Migrations com Flyway

O projeto utiliza **Flyway** para controlar a evolução do schema PostgreSQL por meio de migrations versionadas.

O **Flyway é a fonte de verdade do schema do banco de dados**. Alterações estruturais no PostgreSQL devem ser realizadas por meio de migrations, garantindo que a estrutura do banco possa ser reproduzida de forma consistente, controlada e rastreável em diferentes ambientes.

### O que é Flyway

Flyway é uma ferramenta open-source de migrations de banco de dados que gerencia automaticamente a execução de scripts SQL versionados.

Ele:

* Detecta quais migrations já foram executadas no banco
* Executa apenas as migrations pendentes
* Mantém um histórico das migrations executadas na tabela `flyway_schema_history`
* Executa as migrations na ordem correta
* Permite reproduzir a evolução do schema de forma consistente entre ambientes

### Onde ficam as migrations

As migrations ficam no diretório padrão:

```text
src/main/resources/db/migration/
```

### Padrão de nomenclatura

O projeto utiliza o seguinte padrão:

```text
V<número>__<descrição>.sql
```

Exemplos:

```text
V1__create_users.sql
V2__create_rooms.sql
V3__create_games.sql
V4__add_status_to_games.sql
```

Regras:

* O número identifica a versão da migration e deve ser único.
* Novas migrations devem utilizar uma versão maior que as migrations já existentes.
* A descrição deve ser curta, clara e utilizar `snake_case`.
* Utilize `__` (dois underscores) para separar o número da descrição.
* Migrations já executadas **não devem ser editadas**.
* Alterações posteriores devem ser realizadas por meio de uma nova migration.

### Regras de desenvolvimento

1. **Toda alteração estrutural no PostgreSQL deve ser feita por migration.** Não altere tabelas, colunas, índices ou constraints manualmente como parte do fluxo normal de desenvolvimento.

2. **Não execute migrations manualmente.** O arquivo SQL deve ser colocado em `src/main/resources/db/migration/` e executado pelo Flyway.

3. **Migrations já executadas não devem ser alteradas.** Alterar uma migration depois de executada pode causar divergência entre ambientes.

4. **Não reutilize versões de migrations.** Cada migration deve possuir uma versão única.

5. **Cada migration deve representar uma mudança lógica coerente.** Alterações relacionadas podem ser agrupadas em uma única migration.

6. **Evite fragmentar alterações relacionadas sem necessidade.** Não crie várias migrations para mudanças que fazem parte de uma única alteração lógica.

7. **Migrations não devem conter lógica de negócio da aplicação.** Elas devem conter somente as alterações necessárias no banco de dados.

8. **Dados persistentes necessários à evolução do schema podem ser incluídos em migrations quando apropriado.** Dados temporários, dados de teste e regras de negócio não devem ser armazenados por migrations.

9. **SQL deve ser compatível com PostgreSQL.**

10. **Teste a migration localmente antes de abrir um Pull Request.**

11. **O nome da migration deve deixar claro o que ela altera.**

12. **Não commite credenciais ou dados sensíveis em migrations.**

### Alterações manuais no banco

Alterações estruturais devem ser realizadas exclusivamente pelo Flyway.

Por exemplo, não faça diretamente no banco:

```sql
ALTER TABLE users ADD COLUMN username VARCHAR(100);
```

Em vez disso, crie uma nova migration:

```text
V5__add_username_to_users.sql
```

contendo:

```sql
ALTER TABLE users ADD COLUMN username VARCHAR(100);
```

Ao iniciar a aplicação, o Flyway detectará a migration pendente, executará o SQL e registrará sua execução na tabela:

```text
flyway_schema_history
```

#### E se alguém alterar o banco manualmente?

O Flyway **não detecta nem registra automaticamente alterações realizadas manualmente** no PostgreSQL.

Por exemplo:

```text
Dev altera o banco manualmente
        ↓
PostgreSQL é alterado
        ↓
Flyway não registra a alteração
        ↓
A migration correspondente continua pendente
        ↓
Flyway pode falhar ao tentar executá-la
```

Portanto, não se deve tentar "corrigir" o histórico do Flyway simplesmente adicionando manualmente um registro em `flyway_schema_history`.

Se uma alteração manual já tiver sido realizada, o estado do banco deve ser corrigido de acordo com a estratégia definida pela equipe antes de prosseguir.

### Separação de responsabilidades

* **PostgreSQL**: responsável pelos dados persistentes, como usuários, partidas e histórico.
* **Redis**: utilizado para estado temporário e de alta velocidade das partidas, como jogos em andamento, turnos e locks.

As migrations são utilizadas somente para o PostgreSQL. Não são criadas migrations para estruturas ou estado temporário do Redis.

## 🔐 Autenticação e Cadastro

### Cadastro de Usuário

O módulo `auth/` implementa o cadastro de usuários com as seguintes validações e regras:

**Regras de Validação (RegisterRequest):**
- **username**: Obrigatório, entre 3 e 15 caracteres (após trim), aceita qualquer caractere (incluindo emojis e caracteres especiais)
- **email**: Obrigatório, formato válido com TLD de pelo menos 2 letras (ex: `user@example.com`), máximo 254 caracteres, normalizado para minúsculas
- **password**: Obrigatório, entre 6 e 72 caracteres (limite do BCrypt)

**Segurança:**
- Senhas são hasheadas com BCrypt antes de persistir no banco
- A senha nunca é armazenada em texto plano, logada ou retornada em respostas
- Username é case-insensitive (ex: "Victor" e "victor" são considerados duplicados)
- E-mail é case-insensitive e normalizado para minúsculas

**Exceções de Domínio:**
- `UsernameAlreadyExistsException`: Lançada quando o username já está em uso (será mapeada para HTTP 409 na BE-006)
- `EmailAlreadyExistsException`: Lançada quando o e-mail já está em uso (será mapeada para HTTP 409 na BE-006)

**Estrutura da Tabela `users`:**
- `id`: UUID (PRIMARY KEY)
- `username`: VARCHAR(30) NOT NULL (índice único case-insensitive via `lower(username)`)
- `email`: VARCHAR(255) NOT NULL (único)
- `password_hash`: VARCHAR(255) NOT NULL (hash BCrypt)

### Login e Geração de JWT

O módulo `auth/` implementa autenticação via login com geração de tokens JWT.

**Formato do Login (LoginRequest):**
- **identifier**: Obrigatório, pode ser e-mail ou username (máximo 254 caracteres)
- **password**: Obrigatório (validação apenas de presença, sem restrição de tamanho no login)

**Regras de Autenticação:**
- O identifier é trimado e testado primeiro como e-mail (busca case-insensitive em minúsculas)
- Se não encontrar por e-mail, busca por username (case-insensitive via `lower(username)`)
- Não é decidido "é e-mail ou username" por presença de "@" ou regex, pois username aceita qualquer caractere
- E-mail tem prioridade sobre username quando o identifier coincide com ambos
- Senha incorreta e usuário inexistente lançam a mesma exceção genérica para evitar enumeração
- BCrypt dummy é executado quando usuário não existe para reduzir timing attacks

**Geração do Token JWT:**
- Algoritmo: HS256
- Subject: ID do usuário (UUID em String)
- Claims mínimas: apenas subject, issuedAt e expiration (sem e-mail, username ou senha)
- Expiração: configurável via `JWT_EXPIRATION` (em milissegundos)
- Validação de token em requisições realizada pelo JwtAuthenticationFilter

**Configuração JWT (Variáveis de Ambiente):**
- `JWT_SECRET`: Chave secreta para assinatura JWT (mínimo 32 bytes para HS256)
  - A aplicação falha ao iniciar se o secret for ausente ou menor que 32 bytes (fail fast)
  - Para desenvolvimento, gere um secret com: `openssl rand -base64 32`
  - Nunca use o valor do .env.example em produção
- `JWT_EXPIRATION`: Tempo de expiração do token em milissegundos
  - Exemplos:
  - 3600000 = 1 hora
  - 86400000 = 24 horas
  - 2592000000 = 30 dias (recomendado para manter o usuário autenticado entre acessos)

**Exceções de Domínio:**
- `InvalidCredentialsException`: Lançada quando as credenciais são inválidas (será mapeada para HTTP 401 na BE-006)
  - Mensagem genérica "Credenciais inválidas" para usuário inexistente ou senha incorreta
  - Não indica se o usuário existe para evitar enumeração
- `coins_balance`: INTEGER NOT NULL DEFAULT 0 (CHECK >= 0)
- `created_at`: TIMESTAMPTZ NOT NULL DEFAULT now()

**Migrations:**
- `V1__create_users.sql`: Criação inicial da tabela users
- `V10__add_case_insensitive_username_index.sql`: Adiciona índice único case-insensitive em username

## 🎮 Estado da Partida em Redis

### ⚠️ Atualização Necessária: Novas Variáveis de Ambiente

A tarefa BE-017 adicionou novas variáveis de ambiente para configuração do estado da partida em Redis. Se você já tem um arquivo `.env`, adicione as seguintes variáveis:

```bash
# Game State Configuration (NOVAS VARIÁVEIS - BE-017)
GAME_STATE_TTL=
GAME_STATE_UPDATE_MAX_RETRIES=
```

Ou copie o arquivo atualizado de exemplo e preencha os valores:
```bash
cp .env.example .env
```

O Redis é utilizado para armazenar o estado ativo das partidas em andamento, proporcionando acesso rápido e suporte a alta concorrência.

### GameStateManager

O `GameStateManager` é o único ponto de acesso ao Redis para estado de partida. Ele gerencia o estado do round em curso com as seguintes responsabilidades:

- **Criação**: Cria o estado inicial da partida com grid vazio, turno definido e prazo calculado
- **Leitura**: Busca o estado atual de uma partida
- **Atualização**: Atualiza o estado de forma atômica com controle de versão (compare-and-set)
- **Remoção**: Remove o estado ao fim da partida

### Modelo GameState

O modelo `GameState` representa o estado ativo da partida com os seguintes campos:

- `gameId` (UUID): Identificador da partida
- `board` (String[16]): Grid 4x4 representado como string (ex: `"----------------"`)
- `player1Id` / `player2Id` (UUID): Jogadores da partida; P1 usa X e P2 usa O
- `firstPlayerId` (UUID): ID do jogador que começa a partida
- `currentTurnPlayerId` (UUID): ID do jogador com o turno atual
- `turnDurationMillis` (long): Duração do turno em milissegundos
- `turnDeadlineEpochMillis` (long): Prazo do turno em epoch millis
- `roundNumber` (int): Número do round atual (cópia de leitura rápida)
- `suddenDeath` (boolean): Flag de morte súbita (cópia de leitura rápida)
- `phase` (GamePhase): DRAFT, PLAYING, ROUND_FINISHED ou FINISHED
- `player1ConsecutiveTimeouts` / `player2ConsecutiveTimeouts` (int): Timeouts consecutivos de cada jogador
- `version` (long): Versão para controle otimista de concorrência

O modelo é serializável em JSON e tolerante a campos novos e ausentes, permitindo extensibilidade futura sem quebrar estados já gravados.

Estados antigos sem `phase` são lidos como PLAYING. `startRound` também define
PLAYING. As mudanças para Draft e encerramento serão feitas pelos fluxos das
BE-037 e BE-038; o campo sozinho não executa essas transições.

Na BE-017.1, use `create(gameId, player1Id, player2Id, turnDurationMillis)` para criar
o estado completo. O primeiro jogador é sorteado uma vez com `SecureRandom`, que
pode ser passado ao construtor do manager nos testes. `firstPlayerId` guarda quem
começou o round 1; os rounds seguintes alternam a partir dele, sem trocar as marcas.
`getPlayerSymbol` e `getOpponentId` consultam esse mapeamento.

`startRound(round, durationMillis, startedAtMillis)` limpa o tabuleiro, define o
primeiro turno e o prazo e ativa morte súbita no round 3. Não zera os contadores
de timeout nem altera o placar, que continua na entidade `Game`. Use `recordTimeout`
e `resetTimeouts` para atualizar o contador de um jogador. A decisão de W.O. fica
na BE-021. A criação antiga com apenas um jogador continua disponível por
compatibilidade, mas não permite os helpers que precisam dos dois participantes.

### Controle de Concorrência

O `GameStateManager` utiliza controle otimista de concorrência com o seguinte fluxo:

1. Lê o estado atual
2. Aplica o mutator (função de modificação)
3. Tenta gravar de forma atômica se a versão não mudou (compare-and-set via script Lua)
4. Em conflito, relê e tenta novamente (máximo de tentativas configurável)
5. Ao estourar as tentativas, lança `ConcurrentStateModificationException`

O mutator pode executar mais de uma vez. Não faça broadcast, sorteio ou persistência
dentro dele. Calcule o horário e qualquer sorteio antes de chamar `update`.

### Configuração

As seguintes propriedades configuram o comportamento do `GameStateManager`:

- `game.state.ttl`: TTL em milissegundos para partidas abandonadas (padrão: 1800000 = 30 minutos)
- `game.state.update.max-retries`: Número máximo de tentativas de retry em conflitos (padrão: 5)

Essas propriedades podem ser configuradas via variáveis de ambiente:

- `GAME_STATE_TTL`: TTL para estado da partida
- `GAME_STATE_UPDATE_MAX_RETRIES`: Máximo de tentativas de retry

### Chaves Redis

As chaves seguem o padrão: `game:state:{gameId}`

Exemplo: `game:state:550e8400-e29b-41d4-a716-446655440000`

### Segurança

O Redis **não** deve ficar exposto publicamente. No docker-compose.yml, o serviço Redis tem a porta publicada apenas para `127.0.0.1` (localhost), permitindo acesso local para desenvolvimento mas não exposto para fora da máquina. Host e credenciais são configurados via variáveis de ambiente, considerando serviços gerenciados como Upstash para produção (BE-033).

### Testes

Os testes de integração do `GameStateManager` utilizam Testcontainers Redis para garantir que o comportamento seja testado em um ambiente real de Redis. Para rodar os testes de integração, é necessário ter o Docker Desktop em execução:

```bash
# Rodar apenas testes unitários (não requer Docker)
mvn test -Dtest=GameStateTest

# Rodar testes de integração (requer Docker Desktop em execução)
mvn test -Dtest=GameStateManagerIT
```

Os testes incluem:
- Criação e leitura de estado
- Atualização com sucesso
- Conflito de concorrência com retry
- Estouro de tentativas de retry
- TTL configurado e renovado
- Expiração de TTL
- Testes de concorrência com 2 e 10 threads simultâneas
- Desserialização tolerante a campos novos e ausentes

### Fluxo de desenvolvimento

Quando uma alteração no banco for necessária:

```text
Alteração necessária no banco
        ↓
Criar nova migration
        ↓
Colocar em src/main/resources/db/migration/
        ↓
Executar/testar a aplicação
        ↓
Flyway detecta a migration pendente
        ↓
Flyway executa a migration
        ↓
Flyway registra a execução
        ↓
Commit + Pull Request
```

Quando o backend inicia, o Flyway:

1. Verifica o histórico em `flyway_schema_history`.
2. Localiza as migrations disponíveis em `db/migration`.
3. Identifica as migrations que ainda não foram executadas.
4. Executa as migrations pendentes na ordem correta.
5. Registra as migrations executadas no histórico.

### Desenvolvimento em equipe

Para evitar conflitos entre desenvolvedores:

* As versões das migrations devem ser coordenadas pela equipe.
* Uma migration já publicada ou executada não deve ter sua versão reutilizada.
* Antes de criar uma migration, verifique as migrations existentes na branch atual.
* Migrations devem passar por code review normalmente.
* Se duas migrations entrarem em conflito durante o desenvolvimento, a equipe deve resolver o conflito antes do merge.
* Uma migration já executada em algum ambiente compartilhado não deve ser renomeada ou alterada.

Exemplo:

```text
V5__create_characters.sql
V6__add_match_result.sql
V7__create_match_history.sql
```

### Relação entre Flyway e Hibernate

O projeto utiliza o **Flyway para alterar o schema** e o **Hibernate/JPA para mapear as Entities e validar o schema existente**.

A configuração do Hibernate utiliza:

```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
```

O `validate` significa que o Hibernate:

* Não cria tabelas
* Não altera tabelas
* Não remove tabelas
* Não executa migrations
* Apenas verifica se o schema existente é compatível com as Entities mapeadas

A responsabilidade de alterar o schema pertence exclusivamente ao Flyway.

A arquitetura segue o princípio:

```text
Flyway
  ↓
Cria e altera o schema
  ↓
PostgreSQL
  ↑
Hibernate/JPA
  ↓
Mapeia Entities e valida o schema
```

### Entities e migrations

Uma tabela pode existir no PostgreSQL sem que uma Entity correspondente tenha sido implementada.

Por exemplo:

```text
Migration
V1__create_users.sql
        ↓
PostgreSQL
users
```

A aplicação pode iniciar mesmo que `User.java` ainda não exista.

Por outro lado, quando uma Entity for implementada, o schema correspondente deverá existir e ser compatível com ela.

Exemplo:

```text
User.java
    ↕
users
    ↑
V1__create_users.sql
```

Com `ddl-auto: validate`, caso a Entity espere uma tabela, coluna ou estrutura que não exista no banco, o Hibernate deverá acusar a inconsistência durante a inicialização.

Isso permite que **Entities e migrations sejam implementadas em tarefas separadas**, desde que o schema e as Entities estejam consistentes quando forem utilizados juntos.

### Modelo de Dados

#### Entidade Game (Partidas)

A entidade `Game` representa uma partida de Jogo da Velha com suporte a rounds, morte súbita e recompensas em moedas.

**Campos principais:**
- `id`: UUID identificador da partida
- `room`: Sala (Room) associada à partida
- `player1`: Jogador 1 (User, obrigatório)
- `player2`: Jogador 2 (User, opcional)
- `board`: String de 16 caracteres representando o tabuleiro 4x4 ('X', 'O' ou '-' para vazio)
- `currentRound`: Round atual (1-3)
- `victoriesPlayer1`: Contagem de vitórias do jogador 1
- `victoriesPlayer2`: Contagem de vitórias do jogador 2
- `suddenDeath`: Flag indicando modo morte súbita (ativo apenas no round 3)
- `status`: Status da partida (IN_PROGRESS, FINISHED)
- `winner`: Vencedor da partida (null em caso de empate)
- `rewardsGranted`: Flag indicando se recompensas já foram concedidas
- `endReason`: Motivo do encerramento (`NORMAL` ou `FORFEIT_TIMEOUT`)
- `startedAt` / `finishedAt`: Instantes de início e término da partida

**Divisão de Responsabilidades:**
- **Entidade Game**: Estado e regras puras da partida (rounds, placar, morte súbita, quem venceu). Sem dependências de Spring e sem alterar outras entidades.
- **GameService**: Orquestração (buscar no repositório, salvar, @Transactional), pagamento de moedas e idempotência do pagamento.

**Regras de Rounds (implementadas na entidade Game):**
- Sempre se jogam pelo menos 2 rounds
- Ao final do round 2, se um jogador tem mais vitórias, ele vence a partida
- Se as vitórias são iguais (1x1 ou 0x0), ocorre o 3º round em modo morte súbita
- Ao final do 3º round, quem vencer ganha a partida; se empatar, a partida termina empatada
- Empate não soma vitória para ninguém, mas conta como round jogado
- `Game.forfeit(loser)` encerra a partida inteira por timeout. O outro jogador
  vence, mesmo se estiver atrás no placar; o placar e o round atual são preservados.

**Regras de Moedas (implementadas no GameService):**
- Vencedor: +50 moedas
- Perdedor: +10 moedas
- Empate: +20 moedas para cada jogador
- W.O. por timeout: +30 moedas ao vencedor e nenhuma ao perdedor
- Recompensas são concedidas apenas uma vez por partida (flag `rewardsGranted`)
- O pagamento ocorre dentro da mesma transação que salva Game e User

**Constantes da entidade Game:**
- `SUDDEN_DEATH_TURN_SECONDS = 5`: Tempo por turno no modo morte súbita
- `MAX_ROUNDS = 3`: Máximo de rounds por partida
- `EMPTY_BOARD = "----------------"`: Tabuleiro vazio

**Constantes do GameService:**
- `COINS_WIN = 50`: Moedas para vencedor
- `COINS_DRAW = 20`: Moedas para empate
- `COINS_LOSS = 10`: Moedas para perdedor
- `COINS_FORFEIT_WIN = 30`: Moedas para vencedor por W.O.

A BE-016.1 adiciona `GameService.forfeit(gameId, loser)` para encerrar e pagar
as recompensas na mesma transação. Uma nova tentativa de encerrar uma partida
finalizada é rejeitada, sem novo pagamento; `rewardsGranted` continua protegendo
o pagamento de uma partida já recompensada.

`startedAt` é preenchido na criação e `finishedAt` no encerramento, inclusive no
empate e no W.O. A migration V11 adiciona esses campos sem alterar migrations
anteriores. As datas de partidas antigas ficam `NULL`, pois não há registro para
recuperá-las. Não use essas datas desconhecidas para inferir a ordem do histórico.

A V11 foi validada com Flyway em PostgreSQL descartável, tanto em schema vazio
quanto na atualização de V10 com partidas existentes. O mapeamento JPA também
foi validado, sem usar o banco do projeto.

### Princípio geral

A responsabilidade de cada tecnologia deve permanecer bem definida:

```text
Flyway
→ Evolução e versionamento do schema

PostgreSQL
→ Persistência dos dados

Hibernate/JPA
→ Mapeamento das Entities e validação do schema

Redis
→ Estado temporário das partidas
```

**O Hibernate não deve competir com o Flyway pela responsabilidade de gerenciar o schema.**

Qualquer alteração estrutural no PostgreSQL deve passar pelo processo de migration e ser versionada no Git.

## 📚 Documentação da API

O projeto utiliza **SpringDoc OpenAPI** para geração automática de documentação da API com Swagger UI.

### Acesso à Documentação

- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **OpenAPI JSON**: `http://localhost:8080/v3/api-docs`

Contrato de integração: [`docs/contrato-integracao.md`](docs/contrato-integracao.md). Swagger = detalhe do REST; contrato = visão para o front e destinos/eventos STOMP.

### Configuração de Segurança

A documentação já está configurada com suporte a autenticação JWT:
- Botão "Authorize" disponível na interface do Swagger
- Esquema de segurança do tipo `bearer` JWT configurado globalmente
- Para testar endpoints protegidos, insira o token no formato: `Bearer {token}`

### Convenção de Documentação

**A partir de agora, todo endpoint novo deve ser documentado com anotações OpenAPI** antes do PR. As anotações principais são:

- `@Operation` — Descrição do endpoint e suas operações
- `@ApiResponse` — Documentação das respostas possíveis (sucesso, erro, etc.)
- `@Parameter` — Descrição de parâmetros de entrada
- `@Tag` — Agrupamento de endpoints relacionados

**Exemplo de uso:**
```java
@Operation(summary = "Criar nova partida", description = "Cria uma nova partida de Jogo da Velha")
@ApiResponse(responseCode = "201", description = "Partida criada com sucesso")
@ApiResponse(responseCode = "400", description = "Dados inválidos")
@PostMapping("/games")
public ResponseEntity<Game> createGame(@Valid @RequestBody CreateGameRequest request) {
    // implementação
}
```

Esta convenção será cobrada nos critérios de aceite das próximas tarefas.

## Validação de jogadas (BE-018 e BE-018.1)

`MoveValidator.validate(state, playerId, position, blockedPositions)` rejeita casa
ocupada, jogador fora do turno, casa bloqueada por Escudo, turno expirado, Draft
e round ou partida encerrados. No instante do prazo, o turno já está expirado.
As rejeições usam `MoveValidationException`, com o motivo em `getCode()`.
As posições vão de 0 a 15, da esquerda para a direita, linha por linha. O estado
e os bloqueios devem vir do backend, não do cliente. `blockedPositions` contém
as casas bloqueadas para aquele jogador; use `Set.of()` quando não houver bloqueios.

O método apenas valida, sem alterar o estado ou acessar Redis. Ao implementar
a BE-023, calcule o horário no servidor antes de `GameStateManager.update` e use
`validate(state, playerId, position, blockedPositions, nowMillis)` dentro do update,
antes de aplicar a jogada, para que a validação seja refeita se houver conflito
de versão. Os efeitos e a duração do Escudo ficam na BE-014. Esta tarefa não
altera migrations.

## Vitória e empate (BE-019 e BE-018.1)

`GameValidator.findWinner(board)` retorna o símbolo X ou O quando há quatro iguais
em uma linha, coluna ou diagonal do grid 4x4; sem vencedor, retorna `Optional.empty()`.
Essa assinatura antiga não considera efeitos. Para gameplay, use
`resolveWinner(board, frozenPositions, actor)`: peças congeladas não contam para
uma linha, e a marca de quem agiu (X ou O) tem prioridade sobre a do oponente.
Use `state.getPlayerSymbol(playerId)` para obter a marca.

`isDraw(board, frozenPositions)` só retorna true com as 16 casas ocupadas e sem
vencedor pelas mesmas regras. Peças congeladas continuam ocupando casas.
`isDraw(board)` mantém a chamada sem efeitos. Os métodos rejeitam tabuleiro,
marca ou posições inválidos com `IllegalArgumentException` e não alteram o estado.

A BE-014 controla a duração dos efeitos. Ao remover um congelamento, uma linha
que continuou intacta pode vencer na próxima conferência, mesmo sem nova peça.
O fluxo deve encerrar o round ao encontrar um vencedor, sem conferir de novo um
round já encerrado. A chamada nas jogadas fica na BE-023 e no timeout na BE-020.
Essas integrações ainda não estão implementadas. Esta tarefa não altera migrations.

## Efeitos ofensivos (BE-014.1)

`SkillEngine` reconhece `BOMBA`, `TORNADO`, `BURACO_NEGRO` e `CONGELAR` pelos códigos
do catálogo, retornados por `skillCode()`. O alvo deve ser uma peça adversária.
Reforço impede remoção e movimento, mas não impede Congelar.
Alvo com Reforço ou ausência de destino permitido retorna `NO_VALID_TARGET`, sem
aplicar o efeito. Consumo da habilidade e troca de turno continuam na BE-015.
Alvo nulo, fora do tabuleiro, vazio ou próprio lança `SKILL_INVALID_TARGET`.
Destino informado inválido no Tornado lança `SKILL_INVALID_DESTINATION`, mesmo
se o alvo estiver reforçado. Essas rejeições não encerram a ação nem alteram o estado;
o chamador deve validar antes de consumir a habilidade e não capturar o erro para
seguir com `endPlayerAction`.

Tornado recebe origem e destino em `new SkillContext(playerId, origem, destino)`.
As posições vão de 0 a 15, em ordem de linha. O destino deve estar vazio, não
bloqueado por Escudo para quem usa a habilidade e ser vizinho na horizontal ou
vertical, sem diagonais nem passagem entre as bordas das linhas. Sem vizinho
permitido, a chamada sem destino retorna `NO_VALID_TARGET`; se houver opção,
omitir o destino é erro. Essa é a interpretação de adjacência
adotada nesta implementação; o card não especifica diagonais.

Para Buraco Negro, prepare `SkillContext.randomized(playerId, origem, random)` no
servidor, antes de `GameStateManager.update`. O `RandomGenerator` é injetável nos
testes; em produção, use `SecureRandom`. O contexto e o valor sorteado nunca devem
vir do cliente. O efeito usa esse mesmo valor para escolher entre as casas vazias
do estado atual que não estejam bloqueadas para quem usa a habilidade, inclusive
se o update precisar ser refeito por conflito.

Congelar registra contador 2. Seguindo a BE-014, `endAction` confere a vitória e
depois reduz os contadores, já na ação que aplicou o efeito. A peça volta a contar
para vitória na conferência seguinte à expiração. Congelamento acompanha a peça
se ela for movida e é removido junto dela pela Bomba.

Os efeitos alteram apenas o `GameState`, serializado pelo manager existente. O
chamador ainda deve validar jogador, turno e fase, executar a habilidade e chamar
`endAction`; os handlers e o timer não fazem parte desta tarefa. Não há alterações
de schema nem execução de migrations.

## Efeitos defensivos e de tempo (BE-014.2)

- `ESCUDO`: usa uma casa vazia em `targetPosition` e a bloqueia para o oponente.
- `REFORCO`: protege a peça própria em `targetPosition` contra Bomba, Tornado e Buraco Negro, mas não Congelar.
- `CAMPO_DE_FORCA`: protege quem ativou e anula a primeira ofensiva recebida, sem refletir. Retorna `BLOCKED_BY_FORCE_FIELD` e remove a proteção.
- `AMPULHETA`: limita o próximo turno do oponente a 5s, pelo `TurnService` existente.
- `SILENCIO`: impede habilidades no próximo turno do oponente, mas permite marcar uma peça normalmente.
- `TROCA_RAPIDA`: move a peça própria de `targetPosition` para `destinationPosition`, vizinho ortogonal vazio e não bloqueado. Congelamento e Reforço acompanham a peça.

Campo de Força, Ampulheta e Silêncio não recebem posição: use
`new SkillContext(playerId, null)`. Os demais usam as posições 0 a 15 já adotadas.
Troca Rápida segue a validação de destino do Tornado, inclusive `NO_VALID_TARGET`
quando não há vizinho permitido e o destino não foi informado.

Os efeitos persistentes começam com contador 2 e diminuem no fim de cada ação,
inclusive a que os ativou. Campo de Força bloqueia Bomba, Tornado, Buraco Negro,
Congelar, Ampulheta e Silêncio, classificados por `SkillEffect.isOffensive()`
conforme o catálogo. O motor primeiro rejeita jogador silenciado e pedidos
inválidos; depois consome o Campo de Força, antes de resolver Reforço ou falta
de destino. Pedidos inválidos não gastam a proteção. Habilidade anulada conta
como uso na futura integração de consumo, que continua na BE-015.

Sem endpoints, destinos STOMP, migrations ou mudanças no formato do `GameState`.
O evento `ABILITY_RESOLVED` continua sendo proposta no contrato.

## Draft (BE-037)

`DraftService.start(roomId, roomCode, player1Id, player2Id)` inicia o Draft da sala,
com prazo único de 20s contado no servidor. A partida ainda não existe nessa etapa;
criar `Game` e `GameState` após `DraftCompleted` continua na BE-038.

`DraftEligibilityProvider` consulta os inventários por um adaptador dos serviços
de personagens e habilidades. O conjunto elegível é guardado no início do Draft,
incluindo habilidades compradas, independentemente de `initialSkill`. São exigidos
dois jogadores diferentes, personagens que permitam escolhas distintas e pelo
menos duas habilidades elegíveis distintas para cada jogador.

`updatePlayer` é o ponto de integração das BE-011/BE-013. O callback deve ser puro,
pois pode ser reexecutado em conflito. O estado passa por personagem, habilidades
e pronto; duas habilidades confirmadas bloqueiam novas alterações daquele jogador.
Mudar escolhas não reinicia o prazo. No instante do prazo, a confirmação já é
rejeitada, e o timer completa somente o que estiver pendente com sorteio do servidor.
Personagens não se repetem entre os jogadores; habilidades não se repetem no par
do mesmo jogador. O sorteio é injetável e ocorre antes dos retries do Redis.

O estado usa `draft:state:{roomId}`, com gravação inicial exclusiva e updates por
versão. A retenção usa `game.state.ttl` (30 min por padrão), não 20s, para não apagar
o Draft antes da auto-escolha. O timer usa o agendador já existente. Após reinício,
a futura orquestração deve chamar `resume(roomId)` para rearmar o prazo original;
`cancel(roomId)` cancela o agendamento e `DraftStateManager.delete(roomId)` limpa
o estado ao encerrar/reutilizar a sala.

Após persistir, `DraftUpdated` dispara `DRAFT_UPDATE` em `/topic/rooms/{code}`.
O payload contém `deadline`, `characters` por lado (`P1`/`P2`, símbolos do catálogo)
e `ready` por lado, no envelope `{ type, payload, serverTime }`. A ordem dos
jogadores passada a `start` define P1 (host) e P2 (convidado). Inventários e
habilidades escolhidas não são enviados ao tópico. O formato acompanha o consumidor
existente no frontend; a validação conjunta segue pendente na FE-17.

`DraftCompleted` é interno e leva as escolhas finais à BE-038. A auto-escolha não
chama o serviço de turnos nem altera contadores de W.O. Os eventos não têm fila
persistente ou reentrega automática após falha/reinício.

As BE-011/BE-013 implementam as mensagens de escolha usando `updatePlayer`;
a BE-038 liga o início do Draft à sala e cria a partida ao receber `DraftCompleted`.
Essas integrações não fazem parte da BE-037. Não há novos handlers de escolha,
códigos públicos de erro, migrations ou alterações no `GameState` nesta entrega.
Os testes usam Redis, inventários e agendador simulados. A integração STOMP usa
servidor local em porta aleatória e dois clientes autenticados, sem PostgreSQL,
Redis externo ou Flyway, cobrindo conclusão antecipada e auto-escolha por prazo.

## Escolha das habilidades iniciais (BE-013)

Durante o Draft, envie `SEND /app/rooms/{code}/draft/abilities` com
`{"abilities":["BOMBA","ESCUDO"]}` e `content-type: application/json`.
O jogador vem do `Principal` autenticado no STOMP; `playerId` no corpo é ignorado.

É preciso participar da sala e já ter escolhido o personagem. São aceitas exatamente
duas habilidades distintas do conjunto elegível guardado no início do Draft,
incluindo as compradas. A confirmação grava as escolhas no Redis e bloqueia novas
alterações, sem reiniciar os 20s. O oponente recebe apenas o progresso em
`DRAFT_UPDATE`; as habilidades escolhidas não são divulgadas.

Erros vão somente para `/user/queue/errors`, como `ApiError`: `BAD_REQUEST` (400)
para código da sala ou seleção inválidos; `FORBIDDEN` (403) para quem não participa
da sala; `CONFLICT` (409) para Draft ausente, encerrado, expirado ou escolhas já
confirmadas. A fila privada exige uma sessão autenticada e não depende da inscrição
no tópico público da sala. Não há novo endpoint REST, código de erro ou migration.
O sorteio da terceira habilidade e o deck de cada round são tratados pela BE-013.1.

## Sorteio por round (BE-013.1)

Antes de cada round, `RoundSkillDrawService` usa o catálogo completo para sortear
três habilidades distintas por jogador, excluindo as duas fixas. As opções ficam
no `GameState` do Redis e chegam somente ao dono em `/user/queue/room`, no evento
`DRAW_OPTIONS`, com `{options, deadline}`. O prazo é de 8 segundos.

Envie `SEND /app/rooms/{code}/draw/pick` com `{"ability":"TORNADO"}`. A identidade
vem do JWT do CONNECT, e a habilidade precisa estar entre as opções do jogador.
Confirmar bloqueia novas escolhas. Ao expirar, o servidor escolhe uma opção para
quem ainda não confirmou; isso não conta como turno nem como timeout do W.O.

`DECK_UPDATE` envia ao dono `{deck:[{ability, source, usedThisRound}]}`. O deck
contém as duas fixas e a escolhida. Somente após ambos terem deck, o callback de
`RoundSkillDraft` libera o início do turno. Cada round refaz o sorteio e limpa o
consumo das habilidades, preservando as duas fixas e os contadores de timeout.

O snapshot privado recupera as opções pendentes e o prazo original na reconexão;
o tópico público não contém opções nem decks. A fase exposta ao front durante a
escolha é `ROUND_DRAW`. Não há endpoint REST ou migration nesta tarefa.

Erros vão para `/user/queue/errors`: `BAD_REQUEST` (400) para código ou habilidade
inválidos; `FORBIDDEN` (403) para quem não pertence à sala; `CONFLICT` (409) para
sorteio ausente, encerrado, expirado ou escolha já confirmada.

## 🧪 Testes

O `RoomService` da BE-008 cria salas com `createRoom(playerId)` e permite entrada
com `joinRoom(code, playerId)`. Código inválido ou sala inexistente gera
`IllegalArgumentException`; sala cheia, partida iniciada ou jogador ocupado gera
`IllegalStateException`. O controller da BE-009 deve tratar esses erros.
Com dois jogadores, `isReadyToStart()` retorna true e a sala permanece WAITING
até o início da partida. Criação e entrada compartilham o lock já usado na BE-007.
Esta tarefa não altera migrations.

Execute testes com:
```bash
mvn test
```

Room foi validada em PostgreSQL isolado. Antes de rodar a aplicação no banco do
projeto, é preciso integrar as migrations de users e rooms. O arquivo
`docs/room/create_rooms.sql.example` continua inativo e é usado no teste.

`RoomPersistenceIT` roda separadamente, com Java 25 e um PostgreSQL descartável.
Não usa DB_URL nem o banco do projeto. O Flyway cria users apenas como fixture de
teste e aplica o SQL de rooms; o Hibernate mantém `ddl-auto=validate`.

```powershell
docker run --rm -d --name be007-isolated-validation -e POSTGRES_USER=be007_test -e POSTGRES_PASSWORD=be007_test_only -e POSTGRES_DB=be007_validation -p 127.0.0.1::5432 postgres:16-alpine
docker port be007-isolated-validation 5432
# Troque PORTA pelo número retornado acima, nunca pela porta do banco do projeto.
.\mvnw.cmd test "-Dtest=RoomPersistenceIT" "-Droom.test.port=PORTA"
# Remove apenas o container descartável e seus dados de teste.
docker stop be007-isolated-validation
```

## 📝 Próximos Passos

1. Implementar lógica de autenticação (login, cadastro, JWT)
2. Implementar regras do jogo no módulo `game/logic`
3. Implementar serviços de gerenciamento de partidas no módulo `game/service`
4. Implementar endpoints REST nos controllers
5. Implementar handlers WebSocket para comunicação em tempo real
6. Configurar Nginx para balanceamento de carga em produção

## 🌐 Plano de Produção Decidido

Para o ambiente de produção, foi decidido o seguinte stack de hospedagem:

- **Backend**: Render (plano gratuito)
- **PostgreSQL**: Neon (plano gratuito)
- **Redis**: Upstash (plano gratuito)
- **Frontend**: A decidir pelo time de frontend

**Nota importante**: Nenhum provedor foi configurado ainda. Esta é apenas a decisão de arquitetura para produção. A configuração e deploy nos provedores serão feitos em uma etapa futura do projeto.

### Variáveis e conexões de produção (Render, Neon e Upstash)

Quando os serviços forem criados, configure no serviço Web do Render, em **Environment > Environment Variables**, as variáveis abaixo. Cadastre os valores diretamente no painel do Render; não os coloque no repositório nem em arquivos `.env` versionados. Use os dados de conexão fornecidos pelos painéis Neon e Upstash.

| Variável | Valor a cadastrar |
| --- | --- |
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | URL JDBC do Neon no formato `jdbc:postgresql://<host>:5432/<database>` (sem usuário ou senha embutidos) |
| `DB_USER` / `DB_PASSWORD` | Usuário e senha do banco Neon |
| `REDIS_HOST` / `REDIS_PORT` | Host e porta TLS exibidos nos dados de conexão da Upstash |
| `REDIS_USERNAME` | Usuário Redis da Upstash; normalmente `default` (também é o padrão da aplicação se omitido) |
| `REDIS_PASSWORD` | Token/senha Redis da Upstash |
| `JWT_SECRET` | Segredo aleatório forte, com pelo menos 32 bytes |
| `JWT_EXPIRATION` | Validade do token em milissegundos, por exemplo `2592000000` (30 dias) |
| `CORS_ALLOWED_ORIGINS` | Origens autorizadas do frontend, separadas por vírgula |
| `GAME_STATE_TTL` / `GAME_STATE_UPDATE_MAX_RETRIES` | TTL em ms e número máximo de tentativas; padrões `1800000` e `5` |

O Render fornece a porta HTTP da aplicação; a aplicação usa `SERVER_PORT` com padrão `8080`. Não cadastre `DB_NAME` e `DB_PORT` como substitutos de `DB_URL` para produção.

O profile `prod` exige SSL no driver PostgreSQL (`sslmode=require`), desativa SQL logging e mantém `spring.jpa.hibernate.ddl-auto=validate`; o Flyway continua habilitado e é responsável por aplicar migrations. Para Redis, configure usuário e senha da Upstash e TLS fica habilitado na conexão Lettuce.

Depois de configurar e publicar o serviço, valide o health check usando a URL atribuída pelo Render ao serviço (não existe URL de produção definida ainda):

```bash
curl -i https://<URL-atribuida-pelo-Render>/api/health
```

O esperado é HTTP 200 com `{"status":"UP","message":"API disponível"}`. Esse endpoint confirma que a API está respondendo, mas não testa conectividade com Neon ou Upstash. A validação de conexões e o smoke test dependem dos serviços externos estarem provisionados e ficam para a etapa própria de deploy.

## ⚠️ Notas Importantes

- Todas as validações e transições de estado do jogo devem ocorrer no servidor no módulo `game/logic`
- O gateway WebSocket não persiste dados; ele apenas gerencia comunicação em tempo real
- Redis é usado apenas para estado temporário; dados persistentes vão para PostgreSQL
- A estrutura do projeto separa claramente as responsabilidades seguindo convenções do Spring Boot
- As entidades e DTOs utilizam anotações do Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor) para geração automática de getters, setters e construtores
- A estrutura básica dos arquivos foi criada seguindo a arquitetura de domínios definida

## Autenticação JWT

Rotas protegidas exigem o cabeçalho `Authorization: Bearer <token>`. O token é obtido pelo fluxo de autenticação (`POST /api/auth/login`). Tokens válidos autenticam o UUID do usuário; o filtro não consulta o banco. Requisições sem token ou com token inválido recebem HTTP 401 e um corpo JSON com a mensagem genérica `Autenticação necessária ou token inválido`.

O tempo de validade do token é configurado pela variável de ambiente `JWT_EXPIRATION`. Para manter o usuário autenticado entre acessos, recomenda-se utilizar `2592000000` milissegundos (30 dias).

Logout é realizado pelo cliente (frontend). O backend não possui endpoint de logout nem mecanismo de revogação de tokens.

Ao realizar logout, o frontend deve remover o token JWT armazenado localmente. O token permanecerá tecnicamente válido até sua expiração configurada em `JWT_EXPIRATION`.

Rotas públicas: `/api/auth/**`, `GET /api/health`, `GET /api/health/**`, `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` e `/ws/**`.

## Formato de Erro

Todos os erros REST retornam `code` e `message`, além do status HTTP, timestamp e caminho. Erros de validação de corpo também incluem `fields`:

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

Códigos REST disponíveis:

| Código | HTTP | Uso |
| --- | ---: | --- |
| `USERNAME_ALREADY_EXISTS` | 409 | Username já cadastrado |
| `EMAIL_ALREADY_EXISTS` | 409 | E-mail já cadastrado |
| `INVALID_CREDENTIALS` | 401 | Credenciais de login inválidas |
| `VALIDATION_ERROR` | 400 | Validação de campos |
| `INVALID_REQUEST_BODY` | 400 | JSON ausente ou ilegível |
| `NOT_FOUND` | 404 | Rota ou recurso não encontrado |
| `METHOD_NOT_ALLOWED` | 405 | Método HTTP não permitido |
| `BAD_REQUEST` | 400 | Parâmetro HTTP inválido ou ausente |
| `ROOM_BAD_REQUEST` | 400 | Erro temporário de sala; será tipado na BE-009.1 |
| `ROOM_CONFLICT` | 409 | Conflito temporário de sala; será tipado na BE-009.1 |
| `UNAUTHORIZED` | 401 | Autenticação ausente ou token inválido |
| `FORBIDDEN` | 403 | Usuário sem autorização para o recurso |
| `INTERNAL_ERROR` | 500 | Erro inesperado do servidor |

`fields` aparece somente em `VALIDATION_ERROR` originado de validação de corpo. O schema `ApiError` também é reutilizado nas mensagens de erro STOMP.

Erros de habilidade no STOMP (BE-014.1 e BE-014.2), enviados ao autor em `/user/queue/errors`:

| Código | `status` no payload | Uso |
| --- | ---: | --- |
| `SKILL_INVALID_TARGET` | 400 | Jogador ausente ou alvo inválido para a habilidade (posição, ocupação, proprietário ou bloqueio) |
| `SKILL_INVALID_DESTINATION` | 400 | Destino do Tornado ou Troca Rápida ausente quando há opção, fora do tabuleiro, ocupado, bloqueado por Escudo ou não adjacente |
| `SKILL_SILENCED` | 400 | Jogador sob Silêncio tenta ativar uma habilidade |
| `BAD_REQUEST` | 400 | Ação de jogo fora da fase permitida, fora da vez ou após o prazo |
| `SKILL_ALREADY_USED` | 400 | Jogador tenta reutilizar o mesmo código de habilidade no round atual |
| `SKILL_NOT_IN_DECK` | 400 | Habilidade não está no deck selecionado para o round atual |

Esses códigos preservam o erro de domínio no `ApiError`, sem `fields` ou `error`.
Não há endpoint REST de habilidade nem handler público de ativação; a exposição STOMP
continua pertencendo à BE-023. `NO_VALID_TARGET` e
`BLOCKED_BY_FORCE_FIELD` são resultados da ação, não erros. Sua inclusão em
`ABILITY_RESOLVED` está proposta no contrato; o segundo indica uso sem efeito
com consumo do Campo de Força do oponente.

Detalhes e atualizações em `docs/contrato-integracao.md`.

## Códigos de habilidade

O identificador de habilidade usado pelo front-end e nas mensagens STOMP é `code`; `name` permanece como nome de exibição. Os códigos são estáveis, em maiúsculas, e substituem espaços por `_`:

`BOMBA`, `ESCUDO`, `TORNADO`, `BURACO_NEGRO`, `AMPULHETA`, `CONGELAR`, `CAMPO_DE_FORCA`, `TROCA_RAPIDA`, `REFORCO` e `SILENCIO`.

O `SkillEngine` registra os efeitos pelo retorno de `SkillEffect.skillCode()`.
Ao chamar o motor, use `skillEngine.useSkill(state, skill.getCode(), context)`,
nunca `skill.getName()`. Por exemplo, o identificador é `BURACO_NEGRO`, não
`BURACO NEGRO`. O nome de exibição permanece disponível em `Skill.name`, sem
alteração na sua serialização. Códigos desconhecidos são rejeitados, e dois
efeitos não podem ser registrados com o mesmo código.

Detalhes e atualizações em `docs/contrato-integracao.md`.
