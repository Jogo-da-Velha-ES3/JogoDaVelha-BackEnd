# JogoDaVelha-BackEnd
Backend de um projeto acadêmico de Engenharia de Software III — Jogo da Velha inovador multiplayer

## 📋 Visão Geral

Backend de jogo multiplayer por turnos usando Spring Boot + Maven, seguindo uma arquitetura modular com PostgreSQL para dados persistentes e Redis para gerenciamento de estado rápido/temporário.

## 🏗️ Arquitetura

O projeto está organizado em 4 módulos principais sob o pacote `com.jogodavelha.game`:

- **`auth/`** — Autenticação, cadastro, permissões e geração/validação de JWT
- **`game/service/`** — Gerenciamento de partidas, entrada/saída de jogadores, controle de turnos
- **`game/domain/`** — Regras do jogo, validações e transições de estado (camada de validação oficial)
- **`websocket/`** — Gateway de conexões em tempo real (sem persistência)
- **`repository/`** — Camada de acesso a dados centralizada (repositórios JPA para PostgreSQL, componentes de acesso Redis)
- **`config/`** — Configurações de Security, WebSocket e Redis

### Decisão de Design: Camada Repository

O pacote `repository/` centraliza todo o acesso a dados para evitar espalhar preocupações de persistência pelas camadas de domain/service. Este pacote contém:
- Repositórios JPA para dados persistentes (usuários, partidas, histórico)
- Componentes de acesso Redis para dados temporários/rápidos (estado da partida, turnos, locks)

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

**Como atualizar o backend:**

O código-fonte é copiado para a imagem durante o build. Por isso, `docker compose up` sem `--build` pode reutilizar uma imagem antiga e não refletir alterações na API. Depois de alterar o código, execute:

```bash
docker compose up --build
```

**Regra de atualização do Docker:** atualizações e rebuilds da imagem Docker devem ser realizados somente na branch `dev`. Não execute `docker compose up --build` para atualizar a imagem a partir de outras branches.

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
com.jogodavelha.game
├── auth/                    # Autenticação e autorização
├── game/
│   ├── service/            # Serviços de gerenciamento de jogo
│   └── domain/             # Regras e validações do jogo
├── websocket/              # Comunicação em tempo real
├── repository/             # Camada de acesso a dados (PostgreSQL + Redis)
├── config/                 # Classes de configuração
└── JogoDaVelhaApplication  # Classe principal da aplicação
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

1. Implementar módulo de autenticação (login, cadastro, JWT)
2. Implementar lógica de domínio e regras do jogo
3. Implementar camada de serviço do jogo
4. Criar controllers REST e handlers WebSocket
5. Configurar Nginx para balanceamento de carga em produção

## 🌐 Plano de Produção Decidido

Para o ambiente de produção, foi decidido o seguinte stack de hospedagem:

- **Backend**: Render (plano gratuito)
- **PostgreSQL**: Neon (plano gratuito)
- **Redis**: Upstash (plano gratuito)
- **Frontend**: A decidir pelo time de frontend

**Nota importante**: Nenhum provedor foi configurado ainda. Esta é apenas a decisão de arquitetura para produção. A configuração e deploy nos provedores serão feitos em uma etapa futura do projeto.

## ⚠️ Notas Importantes

- Todas as validações e transições de estado do jogo devem ocorrer no servidor no módulo `game/domain`
- O gateway WebSocket não persiste dados; ele apenas gerencia comunicação em tempo real
- Redis é usado apenas para estado temporário; dados persistentes vão para PostgreSQL
- A estrutura do projeto separa claramente as responsabilidades seguindo convenções do Spring Boot
