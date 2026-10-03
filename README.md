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
- `JWT_SECRET` — Chave secreta para assinatura JWT
- `JWT_EXPIRATION` — Tempo de expiração do token JWT em milissegundos (padrão: 86400000)
- `SERVER_PORT` — Porta da aplicação (padrão: 8080)

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
- `JWT_SECRET` — Chave secreta para assinatura JWT
- `JWT_EXPIRATION` — Tempo de expiração do token JWT (ms)
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
- `firstPlayerId` (UUID): ID do jogador que começa a partida
- `currentTurnPlayerId` (UUID): ID do jogador com o turno atual
- `turnDurationMillis` (long): Duração do turno em milissegundos
- `turnDeadlineEpochMillis` (long): Prazo do turno em epoch millis
- `roundNumber` (int): Número do round atual (cópia de leitura rápida)
- `suddenDeath` (boolean): Flag de morte súbita (cópia de leitura rápida)
- `version` (long): Versão para controle otimista de concorrência

O modelo é serializável em JSON e tolerante a campos novos e ausentes, permitindo extensibilidade futura sem quebrar estados já gravados.

### Controle de Concorrência

O `GameStateManager` utiliza controle otimista de concorrência com o seguinte fluxo:

1. Lê o estado atual
2. Aplica o mutator (função de modificação)
3. Tenta gravar de forma atômica se a versão não mudou (compare-and-set via script Lua)
4. Em conflito, relê e tenta novamente (máximo de tentativas configurável)
5. Ao estourar as tentativas, lança `ConcurrentStateModificationException`

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

**Divisão de Responsabilidades:**
- **Entidade Game**: Estado e regras puras da partida (rounds, placar, morte súbita, quem venceu). Sem dependências de Spring e sem alterar outras entidades.
- **GameService**: Orquestração (buscar no repositório, salvar, @Transactional), pagamento de moedas e idempotência do pagamento.

**Regras de Rounds (implementadas na entidade Game):**
- Sempre se jogam pelo menos 2 rounds
- Ao final do round 2, se um jogador tem mais vitórias, ele vence a partida
- Se as vitórias são iguais (1x1 ou 0x0), ocorre o 3º round em modo morte súbita
- Ao final do 3º round, quem vencer ganha a partida; se empatar, a partida termina empatada
- Empate não soma vitória para ninguém, mas conta como round jogado

**Regras de Moedas (implementadas no GameService):**
- Vencedor: +50 moedas
- Perdedor: +10 moedas
- Empate: +20 moedas para cada jogador
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

## ⚠️ Notas Importantes

- Todas as validações e transições de estado do jogo devem ocorrer no servidor no módulo `game/logic`
- O gateway WebSocket não persiste dados; ele apenas gerencia comunicação em tempo real
- Redis é usado apenas para estado temporário; dados persistentes vão para PostgreSQL
- A estrutura do projeto separa claramente as responsabilidades seguindo convenções do Spring Boot
- As entidades e DTOs utilizam anotações do Lombok (@Data, @NoArgsConstructor, @AllArgsConstructor) para geração automática de getters, setters e construtores
- A estrutura básica dos arquivos foi criada seguindo a arquitetura de domínios definida
