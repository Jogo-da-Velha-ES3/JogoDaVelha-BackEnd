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

A forma recomendada de rodar o projeto é usando Docker Compose, que sobe o backend completo com PostgreSQL e Redis sem necessidade de instalar Java, Maven ou bancos de dados na máquina local.

#### Via Docker Compose (Recomendado para o Front-End)

**Pré-requisitos:**
- Docker Desktop instalado

**Passos:**
1. Clone o repositório
2. Copie `.env.example` para `.env` e configure as variáveis
3. Suba o ambiente:
```bash
docker compose up --build
```

Isso vai:
- Buildar o backend dentro do Docker (não precisa de Java/Maven instalados)
- Subir PostgreSQL com persistência de dados via volume
- Subir Redis
- Configurar o backend para conectar nos serviços corretamente
- Expor o backend em `http://localhost:8080`

**Como atualizar o backend:**

`docker compose up` (sem `--build`) **nunca** reflete uma mudança de código — ele só reaproveita a imagem que já existia. Sempre que o código do backend mudar, é preciso rebuildar. O fluxo muda dependendo de quem está atualizando:

- **Você mesmo alterou o código do backend:**
```bash
docker compose up --build
```

- **Você quer pegar uma atualização feita por outra pessoa** (ex.: dev do front-end buscando uma mudança recente na API) — primeiro traga o código novo, só depois rebuilde:
```bash
git pull origin dev
docker compose up --build
```
A atualização do backend deve sempre vir da branch `dev` do repositório.

O Docker reaproveita o cache das camadas que não mudaram (ex.: dependências do Maven já baixadas), então o rebuild costuma ser rápido — ele recompila só o que realmente foi alterado.

**Para parar o ambiente:**
```bash
docker compose down
```

Os dados do PostgreSQL são persistidos em um volume nomeado, então não são perdidos ao parar os containers.

#### Via Maven (Desenvolvimento Local)

O profile de desenvolvimento depende de PostgreSQL e Redis. Para executar o backend diretamente pela máquina, esses serviços precisam estar disponíveis localmente ou em containers:
- Java 25 ou superior
- Maven 3.6+ (ou usar Maven wrapper)
- PostgreSQL e Redis instalados e rodando

```bash
# Usando Maven (se instalado)
mvn spring-boot:run

# Usando Maven wrapper
.\mvnw.cmd spring-boot:run
```

A aplicação iniciará em `http://localhost:8080` com:
- Swagger UI disponível em `http://localhost:8080/swagger-ui/index.html`
- Endpoint WebSocket em `ws://localhost:8080/ws`

**Nota**: PostgreSQL e Redis devem estar disponíveis conforme as variáveis de ambiente configuradas. Para usar os serviços do Docker Compose, execute o backend também pelo Compose.

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
