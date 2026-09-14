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

**Abordagem**: Usar banco de dados H2 em memória e Redis habilitado para desenvolvimento local

**Justificativa**:
- Permite que o projeto compile e rode sem infraestrutura PostgreSQL completa
- Elimina necessidade de containers Docker locais durante desenvolvimento inicial
- Profile de desenvolvimento (`application-dev.yml`) usa H2 e tenta conectar ao Redis local
- Profile de produção usará PostgreSQL e Redis conforme design
- Segue padrão de configuração baseada em profiles do Spring

## 🚀 Configuração e Execução

### Pré-requisitos
- Java 25 ou superior
- Maven 3.6+ (ou usar Maven wrapper)

### Modo Desenvolvimento

O projeto está configurado para rodar em modo de desenvolvimento usando banco de dados H2 em memória e Redis local:

```bash
# Usando Maven (se instalado)
mvn spring-boot:run

# Usando Maven wrapper
.\mvnw.cmd spring-boot:run
```

A aplicação iniciará em `http://localhost:8080` com:
- Console H2 disponível em `http://localhost:8080/h2-console`
- Endpoint WebSocket em `ws://localhost:8080/ws`

**Nota**: O Redis local deve estar disponível em `localhost:6379` para o modo de desenvolvimento funcionar completamente.

### Modo Produção

Para produção, configure as seguintes variáveis de ambiente e garanta que PostgreSQL e Redis estejam disponíveis:

```bash
export DB_URL=jdbc:postgresql://seu-postgres-host:5432/jogodavelha
export DB_USER=seu-usuario-db
export DB_PASSWORD=sua-senha-db
export REDIS_HOST=seu-redis-host
export REDIS_PORT=6379
export JWT_SECRET=sua-chave-secreta-producao
export SPRING_PROFILES_ACTIVE=prod
```

Depois execute:
```bash
mvn spring-boot:run
```

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

- `DB_URL` — URL de conexão PostgreSQL
- `DB_USER` — Usuário do banco de dados
- `DB_PASSWORD` — Senha do banco de dados
- `REDIS_HOST` — Host do servidor Redis
- `REDIS_PORT` — Porta do servidor Redis
- `JWT_SECRET` — Chave secreta para assinatura JWT
- `JWT_EXPIRATION` — Tempo de expiração do token JWT (ms)
- `SERVER_PORT` — Porta da aplicação (padrão: 8080)
- `SPRING_PROFILES_ACTIVE` — Profile ativo (padrão: dev)

### Arquivos de Configuração

- `application.yml` — Configuração principal com padrões de variáveis de ambiente
- `application-dev.yml` — Profile de desenvolvimento (H2 + Redis local)
- `application-prod.yml` — Profile de produção (PostgreSQL + Redis habilitado)

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
5. Configurar Docker Compose para infraestrutura local (PostgreSQL + Redis)
6. Configurar Nginx para balanceamento de carga em produção

## ⚠️ Notas Importantes

- Todas as validações e transições de estado do jogo devem ocorrer no servidor no módulo `game/domain`
- O gateway WebSocket não persiste dados; ele apenas gerencia comunicação em tempo real
- Redis é usado apenas para estado temporário; dados persistentes vão para PostgreSQL
- A estrutura do projeto separa claramente as responsabilidades seguindo convenções do Spring Boot
