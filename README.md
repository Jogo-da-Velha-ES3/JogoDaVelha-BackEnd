# JogoDaVelha-BackEnd
Backend de um projeto acadêmico de Engenharia de Software III — Jogo da Velha inovador multiplayer

## 📋 Visão Geral

Backend de jogo da velha multiplayer por turnos usando Spring Boot + Maven, seguindo uma arquitetura modular com PostgreSQL para dados persistentes e Redis para gerenciamento de estado rápido/temporário.

## 📌LEIA ANTES: Regras Gerais do Back-End

**Branch e Git**

- Nunca commitar direto na main/dev — criar branch própria
(ex: feature/nome-da-tarefa)

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

### Arquivos de Configuração

- `application.yml` — Configuração principal com padrões de variáveis de ambiente
- `application-dev.yml` — Profile de desenvolvimento (PostgreSQL + Redis via docker)
- `application-prod.yml` — Profile de produção (PostgreSQL + Redis)

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

Execute testes com:
```bash
mvn test
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
