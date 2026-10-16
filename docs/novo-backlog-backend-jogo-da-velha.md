# Backlog — Backend Jogo da Velha Multiplayer

Backlog revisado a partir da auditoria do repositório (branch `dev`) contra a Especificação de Requisitos do Sistema (ERS, MVP 1, V1 de 05/10/2026), do GDD v4, do documento de arquitetura e do padrão de tarefa do time.
Cronograma replanejado a partir de **08/10/2026**, com prazo final obrigatório em **31/10/2026**. As tarefas concluídas (✔) permanecem exatamente como estavam (responsável, datas e conclusão); somente as pendentes foram replanejadas.
Última atualização: **08/10/2026**.

**Nesta versão, os personagens são puramente cosméticos/visuais — poderes próprios por personagem ficam fora deste backlog.**

## Premissas de capacidade e distribuição

A distribuição foi recalculada com base nos minicurrículos e disponibilidades informados pelos quatro colaboradores, mantendo todas as tarefas pendentes do backlog. O cronograma continua iniciando em **08/10/2026**, com todas as tarefas planejadas encerrando até **30/10/2026** e **31/10/2026** reservado como margem para correções, revisão final e redeploy.

| Colaborador | Foco das tarefas | Disponibilidade informada | Planejado | Observação |
|-------------|------------------|---------------------------|----------:|------------|
| Victor | WebSocket/STOMP, integração, orquestração e núcleo de gameplay | 2–3h em dias de semana; 4–5h em finais de semana | 50h | Maior carga técnica concentrada no fluxo de tempo real e integração |
| Lorenzo | Regras de domínio, habilidades, Draft, W.O. e pós-jogo | 2h por dia | 50h | Carga próxima da capacidade prevista, com foco em tarefas de domínio |
| Felipe | REST, infraestrutura, TurnService, efeitos, salas e deploy | 14h/semana, podendo chegar a 16–18h | 49h | Carga compatível com a disponibilidade variável informada |
| Rafael | Autenticação complementar, perfil, economia, inventário e regras de aplicação | 4–6h por semana | 26h | Aumento de carga solicitado, mantendo tarefas menores e alinhadas ao conhecimento em Java e regras de negócio |

### Distribuição final das horas

- **Victor:** 50h
- **Lorenzo:** 50h
- **Felipe:** 49h
- **Rafael:** 26h
- **Total:** **175h**

A distribuição mantém as responsabilidades centrais de **WebSocket/STOMP de gameplay com Victor** (BE-023, BE-024, BE-025 e BE-040), sem remover ou reduzir nenhuma tarefa. As tarefas continuam com as mesmas estimativas de esforço do backlog original.

## Resumo da revisão

- **Novas (ERS sem cobertura):** BE-035 (eventos de sala), BE-036 (sair/expirar sala), BE-037 (Draft), BE-038 (orquestração de rounds), BE-039 (pós-jogo/revanche), BE-040 (JWT no WebSocket), BE-041 (inventário de habilidades), BE-044 (erros globais e prefixo da API), BE-045 (compra simbólica de coins).
- **Correções das tarefas concluídas (sem alterá-las):** BE-009.1, BE-012.1, BE-016.1, BE-017.1 e BE-018.1 (esta cobre também a BE-019).
- **Divididas ou reescritas:** BE-014 (framework + BE-014.1 + BE-014.2), BE-013 (+ BE-013.1), BE-011, BE-020, BE-021, BE-023, BE-024, BE-025, BE-026, BE-027, BE-028, BE-029, BE-033 (+ BE-033.1) e BE-034.
- **Reatribuídas:** BE-009.1 (Felipe → Rafael); BE-011 (Rafael → Felipe); BE-014 (Victor → Felipe); BE-017.1 (Victor → Lorenzo); BE-020 (Victor → Felipe); BE-024, BE-025 e BE-040 (Felipe → Victor); BE-029 (Rafael → Lorenzo); BE-039 (Lorenzo → Felipe); demais tarefas mantêm a atribuição da revisão anterior. A BE-006.1 permanece como reserva, fora do esforço planejado.
- **Removidas:** BE-030 e BE-031 (ver seção "Tarefas removidas").
- **Reserva (fora do prazo):** BE-006.1, recuperação de senha, que não consta na ERS.
- **Regras já fechadas incorporadas aos cards:** duração de habilidades em turnos, ordem de verificação de vitória com Congelar, W.O. em 3 timeouts consecutivos (30/0 coins), primeiro jogador sorteado só no round 1 e depois alternado, sorteio de 8s, código de sala numérico, compra de coins simbólica.

## Cronograma consolidado

| ID | WBS | Tarefa | Responsável | Esforço | Início | Término | Dependências | Prioridade | Conclusão |
|----|-----|--------|-------------|--------:|--------|---------|---------------|------------|-----------|
| BE-001 | 1.1.1.3 | Corrigir bug do método main() | Victor | 0,5h | 26/09/2026 | 26/09/2026 | — | Alta | 26/09 |
| BE-002 | 1.1.3.2 | Consolidar estratégia de WebSocket (STOMP vs handler bruto) | Victor | 2h | 26/09/2026 | 26/09/2026 | BE-001 | Alta | 26/09 |
| BE-003 | 1.1.1.1 | Hash de senha e AuthService.register() | Victor | 4h | 01/10/2026 | 03/10/2026 | — | Alta | 03/10 |
| BE-004 | 1.1.1.1 | Login e geração de JWT | Victor | 6h | 03/10/2026 | 04/10/2026 | BE-003 | Alta | 04/10 |
| BE-005 | 1.1.1.1 | Filtro JWT e integração no SecurityConfig | Victor | 5h | 04/10/2026 | 06/10/2026 | BE-004 | Alta | 06/10 |
| BE-005.1 | 1.1.1.1 | Implementação Manter usuário logado | Rafael | 2h | 09/10/2026 | 10/10/2026 | BE-004, BE-005 | Média | — |
| BE-006 | 1.1.1.1 | Endpoints REST de autenticação + testes | Felipe | 5h | 08/10/2026 | 10/10/2026 | BE-005 | Alta | — |
| BE-006.1 | 1.1.1.1 | Implementação de Recuperação de Senha (reserva) | Rafael (sugerido) | 6h | — | — | BE-005, BE-006 | Baixa | Reserva |
| BE-007 | 1.1.3.1 | Entidade Room (código de 4 dígitos) | Lorenzo | 3h | 26/09/2026 | 27/09/2026 | — | Alta | 27/09 |
| BE-008 | 1.1.3.1 | RoomService (criar/entrar/validações) | Lorenzo | 6h | 27/09/2026 | 30/09/2026 | BE-007 | Alta | 30/09 |
| BE-009 | 1.1.3.1 | RoomController (endpoints REST) | Felipe | 3h | 30/09/2026 | 01/10/2026 | BE-008 | Média | 01/10 |
| BE-009.1 | 1.1.3.1 | Refatorar RoomController: jogador autenticado e mensagens de erro da sala | Rafael | 2h | 15/10/2026 | 15/10/2026 | BE-044 | Alta | — |
| BE-010 | 1.1.1.2 | Entidade Personagem/símbolo | Rafael | 3h | 02/10/2026 | 06/10/2026 | — | Média | 06/10 |
| BE-011 | 1.1.1.2 | Seleção de personagem no Draft (reserva exclusiva) | Felipe | 4h | 21/10/2026 | 22/10/2026 | BE-028, BE-037 | Média | — |
| BE-012 | 1.1.1.2 | Catálogo de habilidades (seed das 6 + 4 novas) | Rafael | 4h | 28/09/2026 | 01/10/2026 | — | Alta | 01/10 |
| BE-012.1 | 1.1.1.2 | Corrigir catálogo de habilidades (iniciais, Campo de Força, turnos) | Rafael | 2h | 08/10/2026 | 09/10/2026 | BE-012 | Alta | — |
| BE-013 | 1.1.2.3 | Escolha das 2 habilidades iniciais | Lorenzo | 3h | 20/10/2026 | 21/10/2026 | BE-012.1, BE-041, BE-037 | Alta | — |
| BE-013.1 | 1.1.2.3 | Sorteio de 3 opções por round (escolha em 8s) | Lorenzo | 5h | 22/10/2026 | 24/10/2026 | BE-013, BE-017.1 | Alta | — |
| BE-014 | 1.1.2.3 | Framework de efeitos de habilidades (SkillEngine e estado de efeitos) | Felipe | 5h | 08/10/2026 | 10/10/2026 | BE-017.1 | Alta | — |
| BE-014.1 | 1.1.2.3 | Efeitos ofensivos: Bomba, Tornado, Buraco Negro e Congelar | Lorenzo | 7h | 11/10/2026 | 13/10/2026 | BE-014 | Alta | — |
| BE-014.2 | 1.1.2.3 | Efeitos defensivos e de tempo: Escudo, Reforço, Campo de Força, Ampulheta, Silêncio e Troca Rápida | Lorenzo | 7h | 14/10/2026 | 16/10/2026 | BE-014 | Alta | — |
| BE-015 | 1.1.2.3 | Regra de uso único e consumo de turno | Victor | 4h | 10/10/2026 | 12/10/2026 | BE-014, BE-017.1 | Alta | — |
| BE-016 | 1.1.1.2 | Expandir entidade Game (tabuleiro, rounds, morte súbita) | Victor | 6h | 26/09/2026 | 27/09/2026 | — | Alta | 27/09 |
| BE-016.1 | 1.1.1.2 | Ajustar Game: motivo de encerramento, W.O. e recompensas por motivo | Lorenzo | 3h | 08/10/2026 | 09/10/2026 | BE-016 | Alta | — |
| BE-017 | 1.1.1.2 | GameStateManager — estado da partida em Redis | Victor | 10h | 27/09/2026 | 01/10/2026 | BE-016 | Alta | 01/10 |
| BE-017.1 | 1.1.2.2 | Estender GameState (jogadores, marcas, contador de timeouts, primeiro jogador) | Lorenzo | 2h | 08/10/2026 | 08/10/2026 | BE-017 | Alta | — |
| BE-018 | 1.1.2.1 | MoveValidator — validar jogada no tabuleiro | Lorenzo | 6h | 01/10/2026 | 03/10/2026 | BE-017 | Alta | 03/10 |
| BE-018.1 | 1.1.2.1 | Ajustar validadores: prazo, exceções tipadas e resolução de vitória | Lorenzo | 5h | 09/10/2026 | 11/10/2026 | BE-018, BE-019 | Alta | — |
| BE-019 | 1.1.2.1 | GameValidator — vitória e empate | Lorenzo | 6h | 04/10/2026 | 06/10/2026 | BE-017 | Alta | 06/10 |
| BE-020 | 1.1.2.2 | TurnService — alternância de turnos e timers (10s / 5s) | Felipe | 8h | 12/10/2026 | 15/10/2026 | BE-017.1, BE-018.1 | Alta | — |
| BE-021 | 1.1.2.2 | Regra de W.O. por inatividade (3 timeouts consecutivos) | Lorenzo | 3h | 24/10/2026 | 25/10/2026 | BE-020, BE-016.1 | Alta | — |
| BE-022 | 1.1.3.2 | PlayerService — estado de conexão dos jogadores | Felipe | 5h | 01/10/2026 | 03/10/2026 | BE-008 | Média | 03/10 |
| BE-023 | 1.1.3.2 | Handlers STOMP de gameplay (jogar / ativar habilidade) | Victor | 12h | 15/10/2026 | 20/10/2026 | BE-018.1, BE-020, BE-015, BE-040, BE-044 | Alta | — |
| BE-024 | 1.1.3.2 | Broadcast do estado atualizado da partida | Victor | 5h | 22/10/2026 | 24/10/2026 | BE-023 | Alta | — |
| BE-025 | 1.1.3.2 | Tratamento de desconexão e reconexão | Victor | 6h | 24/10/2026 | 27/10/2026 | BE-023, BE-022, BE-040 | Média | — |
| BE-026 | 1.1.1.1 | Sistema de moedas virtuais (coins): saldo e extrato | Rafael | 4h | 15/10/2026 | 17/10/2026 | BE-016.1 | Média | — |
| BE-027 | 1.1.1.1 | Perfil do jogador: apelido, estatísticas e histórico | Rafael | 6h | 18/10/2026 | 21/10/2026 | BE-016.1, BE-026, BE-028 | Média | — |
| BE-028 | 1.1.1.2 | Posse de personagens por usuário e itens gratuitos (cosmético/visual) | Rafael | 3h | 10/10/2026 | 12/10/2026 | BE-010 | Média | — |
| BE-029 | 1.1.1.1 | Endpoints da loja (listar / comprar / equipar) — personagens e habilidades | Lorenzo | 7h | 24/10/2026 | 28/10/2026 | BE-026, BE-028, BE-041 | Média | — |
| BE-032 | 1.1.3.2 | Teste de integração do fluxo completo de partida | Victor | 8h | 25/10/2026 | 28/10/2026 | BE-023, BE-024, BE-038, BE-037, BE-013.1, BE-014.1, BE-014.2 | Alta | — |
| BE-033 | 1.1.1.3 | Preparar ambiente de produção (Render + Neon + Upstash) | Felipe | 3h | 20/10/2026 | 21/10/2026 | — | Média | — |
| BE-033.1 | 1.1.1.3 | Deploy final e smoke test em produção | Felipe | 3h | 29/10/2026 | 30/10/2026 | BE-032, BE-033, BE-025 | Alta | — |
| BE-034 | 1.1.1.3 | Configurar CORS para o frontend (Vercel) | Felipe | 2h | 16/10/2026 | 16/10/2026 | BE-005 | Média | — |
| BE-035 | 1.1.3.2 | Eventos de sala em tempo real (match feito) | Felipe | 3h | 17/10/2026 | 18/10/2026 | BE-040, BE-009.1 | Alta | — |
| BE-036 | 1.1.3.1 | Sair, encerrar e expirar salas abandonadas | Felipe | 4h | 18/10/2026 | 20/10/2026 | BE-009.1, BE-035 | Alta | — |
| BE-037 | 1.1.2.2 | Máquina de estados do Draft (20s, bloqueio e auto-escolha) | Lorenzo | 8h | 16/10/2026 | 19/10/2026 | BE-040, BE-017.1 | Alta | — |
| BE-038 | 1.1.2.2 | Orquestração de partida e rounds (início, fim de round e fim de partida) | Victor | 10h | 20/10/2026 | 24/10/2026 | BE-020, BE-016.1, BE-018.1, BE-017.1 | Alta | — |
| BE-039 | 1.1.2.4 | Pós-jogo: revanche e voltar ao lobby | Felipe | 8h | 26/10/2026 | 29/10/2026 | BE-038, BE-036 | Média | — |
| BE-040 | 1.1.3.2 | Autenticar conexões STOMP com JWT | Victor | 5h | 12/10/2026 | 14/10/2026 | BE-005, BE-044 | Alta | — |
| BE-041 | 1.1.1.2 | Posse e preço de habilidades (inventário, iniciais gratuitas e pool elegível) | Rafael | 4h | 12/10/2026 | 15/10/2026 | BE-012.1 | Alta | — |
| BE-044 | 1.1.1.3 | Tratamento global de erros (REST e STOMP) e padronização do prefixo da API | Felipe | 4h | 10/10/2026 | 12/10/2026 | — | Alta | — |
| BE-045 | 1.1.1.1 | Compra simbólica de coins (sem pagamento real) | Rafael | 3h | 22/10/2026 | 23/10/2026 | BE-026 | Baixa | — |

## Linha de corte (se houver atraso)

Se o ritmo real ficar abaixo do planejado, cortar nesta ordem, sem comprometer o fluxo jogável de ponta a ponta: BE-045 (compra simbólica), BE-029 (parte de habilidades), BE-027 (estatísticas e sequência), BE-039 (revanche), BE-005.1. O caminho crítico que não pode ser cortado é: BE-017.1 → BE-014 → BE-015 → BE-020 → BE-023 → BE-038 → BE-032 → BE-033.1.

## Cards de tarefa

### [WBS-1.1.1.3] BE-001 — Corrigir bug do método main() - Concluída ✔

**Labels:** back-end, bug
**Categoria:** Infraestrutura
**Membro Atribuído:** @Victor
**Início:** 26/09/2026  |  **Término:** 26/09/2026  |  **Esforço estimado:** 0,5h
**Prioridade:** Alta

**Objetivo / Descrição:**
O método main() em JogoDaVelhaApplication está declarado como `static void main(String[] args)`, sem o modificador `public`. A JVM exige `public static void main` como ponto de entrada; sem isso, `java -jar app.jar` falha com "main method not found" e a aplicação não sobe.

**Dependências:** —

**Anexos / Links de Apoio:** src/main/java/com/jogodavelha/JogoDaVelhaApplication.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `docker compose up --build` sobe a API sem erro de ponto de entrada
- [ ] `GET /api/health` responde 200 após o build
- [ ] Nenhum outro comportamento da aplicação é alterado

---

### [WBS-1.1.3.2] BE-002 — Consolidar estratégia de WebSocket (STOMP vs handler bruto) - Concluída ✔

**Labels:** back-end, refactor
**Categoria:** WebSocket
**Membro Atribuído:** @Victor
**Início:** 26/09/2026  |  **Término:** 26/09/2026  |  **Esforço estimado:** 2h
**Prioridade:** Alta

**Objetivo / Descrição:**
O projeto tem duas abordagens concorrentes de WebSocket: o WebSocketConfig registra um endpoint STOMP em /ws, mas WebSocketHandler implementa a interface WebSocketHandler bruta do Spring e não está registrado em nenhum lugar. Decidir e documentar qual abordagem seguir (recomendado: STOMP, já usado no endpoint ativo) e remover ou adaptar o código órfão.

**Dependências:** BE-001

**Anexos / Links de Apoio:** config/WebSocketConfig.java, websocket/WebSocketHandler.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Decisão documentada no README ou em um ADR curto
- [ ] Código órfão removido ou reaproveitado conforme a decisão
- [ ] Endpoint /ws continua funcional

---

### [WBS-1.1.1.1] BE-003 — Hash de senha e AuthService.register() - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Victor
**Início:** 01/10/2026  |  **Término:** 03/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar o cadastro de usuário em AuthService: validar dados de RegisterRequest, aplicar hash de senha com BCryptPasswordEncoder e persistir o User via UserRepository.

**Dependências:** —

**Anexos / Links de Apoio:** auth/AuthService.java, auth/RegisterRequest.java, auth/User.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Senha nunca é persistida em texto plano
- [ ] Username/e-mail duplicado retorna erro tratado
- [ ] Usuário criado é recuperável via UserRepository

---

### [WBS-1.1.1.1] BE-004 — Login e geração de JWT - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Victor
**Início:** 03/10/2026  |  **Término:** 04/10/2026  |  **Esforço estimado:** 6h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar AuthService.login(): validar credenciais contra o hash armazenado e gerar um JWT (JJWT) com claims mínimas (id do usuário) e expiração configurável via JWT_EXPIRATION.

**Dependências:** BE-003

**Anexos / Links de Apoio:** auth/AuthService.java, auth/LoginRequest.java, application.yml

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Login com credenciais corretas retorna JWT válido
- [ ] Login com credenciais incorretas retorna 401
- [ ] Token expira conforme JWT_EXPIRATION

---

### [WBS-1.1.1.1] BE-005 — Filtro JWT e integração no SecurityConfig - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Victor
**Início:** 04/10/2026  |  **Término:** 06/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
Criar um JwtAuthenticationFilter que valida o token em rotas protegidas e atualizar o SecurityConfig (hoje com anyRequest().permitAll()) para exigir autenticação nas rotas que não sejam de auth/health/swagger.

**Dependências:** BE-004

**Anexos / Links de Apoio:** config/SecurityConfig.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Rota protegida sem token retorna 401
- [ ] Rota protegida com token válido é acessada normalmente
- [ ] Rotas públicas (auth, health, swagger) continuam abertas

---

### [WBS-1.1.1.1] BE-005.1 — Implementação Manter usuário logado

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Rafael
**Início:** 09/10/2026  |  **Término:** 10/10/2026  |  **Esforço estimado:** 2h
**Prioridade:** Média

**Objetivo / Descrição:**
O usuário deve permanecer autenticado entre os acessos, evitando a necessidade de realizar login repetidamente, e deve existir uma opção clara de Logout para encerrar a sessão manualmente.

Implementação simplificada: aumentar a validade do JWT emitido no login, configurada por `JWT_EXPIRATION` no `application.yml` e no `.env.example` (sugestão: 30 dias = `2592000000` ms, substituindo o valor atual de 24h). O `JwtAuthenticationFilter` aceita o token enquanto ele estiver válido e responde 401 no formato padrão (timestamp, status, error, message, path) quando expirar. O Logout é feito no cliente: o frontend apaga o token guardado e o usuário volta para a tela de login, sem endpoint nem tabela nova no back-end. Documentar no README e no Swagger que o logout é client-side e que o token continua válido até expirar.

**Fora do escopo (implementação simplificada):** revogação de tokens no servidor, refresh token e invalidação dos tokens antigos após logout ou recuperação de senha.

**Dependências:** BE-004, BE-005

**Anexos / Links de Apoio:** auth/AuthService.java, application.yml, .env.example, README.md

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] O token emitido no login expira conforme o `JWT_EXPIRATION` configurado
- [ ] Dentro do prazo, o mesmo token acessa rotas protegidas sem novo login
- [ ] Token expirado retorna 401 no formato padrão
- [ ] Rotas públicas (auth, health, swagger) continuam abertas

---

### [WBS-1.1.1.1] BE-006 — Endpoints REST de autenticação + testes

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Felipe
**Início:** 08/10/2026  |  **Término:** 10/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar em `AuthController` os endpoints de cadastro e login (`/auth/register` e `/auth/login`, prefixo conforme BE-044), conectando com `AuthService`, com DTOs de resposta (sem `passwordHash`; o login retorna o JWT, o id, o username e o saldo de coins) e tratamento de erro no próprio pacote `auth` (a BE-044 depois unifica tudo no handler global): username/e-mail duplicado → 409, credenciais inválidas → 401, validação → 400. Absorve os testes de controller que estavam na BE-031 (`AuthService` e JWT já têm testes no repositório).

**Dependências:** BE-005

**Anexos / Links de Apoio:** auth/AuthController.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Cadastro cria usuário e retorna 201
- [ ] Login retorna o JWT em caso de sucesso e 401 em credenciais inválidas
- [ ] Erros de validação retornam 400 com mensagem clara
- [ ] Testes de controller cobrem sucesso e erros
- [ ] Endpoints documentados no Swagger

---

### [WBS-1.1.3.1] BE-007 — Entidade Room (código de 4 dígitos) - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Sala
**Membro Atribuído:** @Lorenzo
**Início:** 26/09/2026  |  **Término:** 27/09/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
Criar o pacote/domínio room com a entidade Room: código de 4 dígitos, status (aguardando/em partida/encerrada) e referência aos dois jogadores.

**Dependências:** —

**Anexos / Links de Apoio:** GDD seção 5.1 — Sistema de salas

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Entidade persiste corretamente via JPA
- [ ] Código de 4 dígitos é gerado sem colisão com salas ativas

---

### [WBS-1.1.3.1] BE-008 — RoomService (criar/entrar/validações) - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Sala
**Membro Atribuído:** @Lorenzo
**Início:** 27/09/2026  |  **Término:** 30/09/2026  |  **Esforço estimado:** 6h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar a lógica de criação de sala, entrada via código e validações (sala cheia, código inválido, sala já iniciada).

**Dependências:** BE-007

**Anexos / Links de Apoio:** —

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Criar sala gera código único
- [ ] Entrar com código inválido retorna erro tratado
- [ ] Sala fica pronta para iniciar partida com 2 jogadores

---

### [WBS-1.1.3.1] BE-009 — RoomController (endpoints REST) - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Sala
**Membro Atribuído:** @Felipe
**Início:** 30/09/2026  |  **Término:** 01/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Média

**Objetivo / Descrição:**
Expor os endpoints REST de criação e entrada em sala, conectando com RoomService.

**Dependências:** BE-008

**Anexos / Links de Apoio:** —

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] POST /rooms cria sala e retorna o código
- [ ] POST /rooms/{codigo}/join adiciona o segundo jogador
- [ ] Endpoints documentados no Swagger

---

### [WBS-1.1.3.1] BE-009.1 — Refatorar RoomController: jogador autenticado e mensagens de erro da sala

**Labels:** back-end, refactor
**Categoria:** Sala
**Membro Atribuído:** @Rafael
**Início:** 15/10/2026  |  **Término:** 15/10/2026  |  **Esforço estimado:** 2h
**Prioridade:** Alta

**Objetivo / Descrição:**
Correção das BE-008/BE-009 (concluídas, não alteradas). O `RoomController` ainda recebe `playerId` por `@RequestParam` (há um TODO no código): qualquer usuário autenticado consegue criar ou entrar em sala como outro jogador. Passar a obter o jogador do usuário autenticado (`SecurityContext`). Separar as mensagens "sala lotada" e "partida já iniciada" (hoje a sala vira `IN_GAME` assim que o 2º jogador entra e as duas situações caem na mesma mensagem). O código de sala continua numérico de 4 dígitos.

**Requisitos ERS:** RF-HUB-05, RNF-ARQ-02.

**Dependências:** BE-044

**Anexos / Links de Apoio:** room/RoomController.java, room/RoomService.java, room/RoomExceptionHandler.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Endpoints de sala não recebem mais `playerId` do cliente
- [ ] Tentar entrar em sala cheia e em sala com partida iniciada retornam mensagens distintas
- [ ] Código inexistente continua retornando erro tratado
- [ ] Swagger atualizado e testes de `Room` passando

---

### [WBS-1.1.1.2] BE-010 — Entidade Personagem/símbolo - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Personagem
**Membro Atribuído:** @Rafael
**Início:** 02/10/2026  |  **Término:** 06/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Média

**Objetivo / Descrição:**
Criar o domínio character com a entidade Personagem: nome e símbolo visual (X, O, Triângulo, Quadrado, Estrela, Coração conforme GDD 4.2). Nesta fase, puramente cosmético/visual — sem poderes vinculados.

**Dependências:** —

**Anexos / Links de Apoio:** GDD seção 4.2 — Loja Cosmética

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Entidade Personagem persiste nome e símbolo
- [ ] Catálogo inicial de personagens/símbolos disponível via seed

---

### [WBS-1.1.1.2] BE-011 — Seleção de personagem no Draft (reserva exclusiva)

**Labels:** back-end, feature
**Categoria:** Personagem
**Membro Atribuído:** @Felipe
**Início:** 21/10/2026  |  **Término:** 22/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Média

**Objetivo / Descrição:**
Reescrita da tarefa original. A seleção passa a ser feita por STOMP dentro do Draft: reserva **atômica** no Redis (SETNX ou Lua; em cliques simultâneos, a primeira confirmação registrada no servidor vence), apenas com personagens que o jogador possui (BE-028), bloqueio visível ao oponente com o nome de quem reservou, e liberação da reserva anterior ao trocar. O personagem é só cosmético.

**Requisitos ERS:** RF-DRAFT-02, RN-DRAFT-01, RN-LOJ-01.

**Dependências:** BE-028, BE-037

**Anexos / Links de Apoio:** game/draft/, character/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Dois pedidos simultâneos pelo mesmo personagem resultam em uma única reserva
- [ ] Jogador não reserva personagem que não possui
- [ ] Oponente recebe o bloqueio com o nome de quem reservou
- [ ] Trocar de personagem libera o anterior

---

### [WBS-1.1.1.2] BE-012 — Catálogo de habilidades (seed das 6 + 4 novas) - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Rafael
**Início:** 28/09/2026  |  **Término:** 01/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Modelar a entidade Habilidade e popular o catálogo fixo com as 10 habilidades: as 6 do GDD (Bomba, Escudo, Tornado, Buraco Negro, Ampulheta, Congelar) mais 4 novas, com nome, categoria de efeito e descrição.

**Dependências:** —

**Anexos / Links de Apoio:** GDD seção 3.2 — Catálogo de Habilidades

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] As 10 habilidades (6 originais + 4 novas) estão persistidas com nome e descrição corretos
- [ ] Catálogo é consultável via repository

---

### [WBS-1.1.1.2] BE-012.1 — Corrigir catálogo de habilidades (iniciais, Campo de Força, turnos)

**Labels:** back-end, fix
**Categoria:** Habilidades
**Membro Atribuído:** @Rafael
**Início:** 08/10/2026  |  **Término:** 09/10/2026  |  **Esforço estimado:** 2h
**Prioridade:** Alta

**Objetivo / Descrição:**
Correção da BE-012 (concluída, não alterada). A migration V6 marca `initial_skill = false` para todas as habilidades, mas a ERS define Bomba, Escudo e Ampulheta como habilidades iniciais gratuitas. Criar uma **nova** migration Flyway (sem editar a V6) que: (1) marca Bomba, Escudo e Ampulheta como iniciais; (2) renomeia `ESPELHO` para `CAMPO DE FORCA`; (3) troca "rodadas" por "turnos" nas descrições (Escudo, Congelar, Reforço, Campo de Força) conforme o GDD v4; (4) move Silêncio para uma categoria ofensiva; (5) adiciona a flag `offensive` (true para Silêncio, Bomba, Tornado, Buraco Negro, Congelar e Ampulheta), usada pelo Campo de Força.

**Requisitos ERS:** RN-LOJ-01, RN-LOJ-02, RN-ARN-04, RF-ARN-03.

**Dependências:** BE-012

**Anexos / Links de Apoio:** resources/db/migration (V6 e seeds anteriores), skill/Skill.java, GDD v4 seção 3.2

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Exatamente Bomba, Escudo e Ampulheta têm `initial_skill = true`
- [ ] Habilidade `CAMPO DE FORCA` substitui `ESPELHO` e a descrição cita "2 turnos"
- [ ] Descrições usam "turnos" e batem com o GDD v4
- [ ] Flag `offensive` consultável via repository
- [ ] Migration aplica sobre o banco existente sem perda de dados

---

### [WBS-1.1.2.3] BE-013 — Escolha das 2 habilidades iniciais

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Lorenzo
**Início:** 20/10/2026  |  **Término:** 21/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
Reescrita da tarefa original (que seguia o GDD v3). Dentro do Draft, o jogador escolhe 2 habilidades iniciais dentre as **elegíveis** dele (BE-041). Validar quantidade e elegibilidade. O sorteio por round fica na BE-013.1.

**Requisitos ERS:** RF-DRAFT-03, RN-DRAFT-02, RN-LOJ-02.

**Dependências:** BE-012.1, BE-041, BE-037

**Anexos / Links de Apoio:** skill/, game/draft/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Jogador escolhe exatamente 2 habilidades elegíveis
- [ ] Escolha de habilidade que o jogador não possui é rejeitada
- [ ] Escolhas ficam salvas no estado do Draft

---

### [WBS-1.1.2.3] BE-013.1 — Sorteio de 3 opções por round (escolha em 8s)

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Lorenzo
**Início:** 22/10/2026  |  **Término:** 24/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
No início de cada round, o servidor sorteia 3 habilidades (o sorteio pode incluir habilidades não compradas) e o jogador escolhe 1 delas para compor o deck de 3 daquele round (2 fixas + 1 sorteada), em até **8 segundos**; se não escolher, o servidor escolhe aleatoriamente. As 3 opções são visíveis só para o dono (envio por fila do usuário). O tempo do sorteio não conta como turno nem como timeout do W.O. O deck é refeito a cada round, e as habilidades voltam a poder ser usadas (BE-015). O "8s" assume que o sorteio citado é a janela de escolha entre as 3 opções (a ERS dizia 5s).

**Requisitos ERS:** RF-DRAFT-03, RN-DRAFT-03, RN-DRAFT-05.

**Dependências:** BE-013, BE-017.1

**Anexos / Links de Apoio:** skill/, game/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Servidor sorteia 3 opções por round e o jogador recebe só as próprias
- [ ] Sem escolha em 8s, uma opção é escolhida aleatoriamente
- [ ] Deck do round contém as 2 fixas + a escolhida
- [ ] Testes com sorteio determinístico (gerador injetado)

---

### [WBS-1.1.2.3] BE-014 — Framework de efeitos de habilidades (SkillEngine e estado de efeitos)

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Felipe
**Início:** 08/10/2026  |  **Término:** 10/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
Substitui a tarefa única de 14h por três tarefas (BE-014, BE-014.1, BE-014.2). Esta cria a base: interface `SkillEffect`, `SkillEngine`, campos de efeito no `GameState` (casas protegidas por Escudo, congeladas, reforçadas, Campo de Força, Silêncio, Ampulheta, cada um com contador) e a rotina de fim de ação.

**Regras comuns (duração em turnos):** o efeito começa com contador 2 no uso e diminui ao fim de **cada** turno, de qualquer jogador, depois da verificação de vitória (na prática, cobre a próxima jogada do oponente). Ordem ao fim de toda ação (jogada, habilidade ou timeout): aplicar a ação → `resolveWinner` (jogador que agiu, depois oponente, com congelamentos ainda ativos) → diminuir contadores e remover efeitos expirados. Habilidade sem alvo válido (por exemplo, sem casa vazia adjacente) é consumida e o turno termina (RN-ARN-05). Nenhuma habilidade garante vitória instantânea (GDD 3.1). Efeitos no mutator do `update` ficam puros; sorteios usam gerador injetável.

**Requisitos ERS:** RF-ARN-03, RF-ARN-04, RN-ARN-04, RN-ARN-05.

**Dependências:** BE-017.1

**Anexos / Links de Apoio:** game/skill/, game/logic/GameState.java, GDD v4 seção 3.2

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `SkillEngine` aplica uma habilidade ao `GameState` por meio de `SkillEffect`
- [ ] Contadores diminuem ao fim de cada turno e efeitos expiram corretamente
- [ ] Ordem aplicar → resolveWinner → diminuir contadores é respeitada, inclusive em timeout
- [ ] Habilidade sem alvo válido é consumida sem efeito
- [ ] Testes unitários do engine passando

---

### [WBS-1.1.2.3] BE-014.1 — Efeitos ofensivos: Bomba, Tornado, Buraco Negro e Congelar

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Lorenzo
**Início:** 11/10/2026  |  **Término:** 13/10/2026  |  **Esforço estimado:** 7h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar sobre o `SkillEngine` (BE-014): **Bomba** remove 1 peça do oponente; **Tornado** move 1 peça do oponente para casa adjacente vazia; **Buraco Negro** move 1 peça do oponente para uma casa vazia aleatória (RNG do servidor, injetável); **Congelar** congela 1 peça por 2 turnos (a peça continua no tabuleiro, mas é ignorada pela verificação de vitória). Alvo reforçado (Reforço, BE-014.2) faz a habilidade falhar: é consumida e o turno termina (premissa a validar). Reforço não protege contra Congelar. Testes por habilidade.

**Requisitos ERS:** RF-ARN-03, RN-ARN-04, RN-ARN-05.

**Dependências:** BE-014

**Anexos / Links de Apoio:** game/skill/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Cada uma das 4 habilidades produz o efeito descrito
- [ ] Buraco Negro é determinístico nos testes (gerador injetado)
- [ ] Congelar faz a peça ser ignorada em `resolveWinner` por 2 turnos
- [ ] Alvo reforçado faz Bomba, Tornado e Buraco Negro falharem
- [ ] Efeitos refletidos no estado em Redis

---

### [WBS-1.1.2.3] BE-014.2 — Efeitos defensivos e de tempo: Escudo, Reforço, Campo de Força, Ampulheta, Silêncio e Troca Rápida

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Lorenzo
**Início:** 14/10/2026  |  **Término:** 16/10/2026  |  **Esforço estimado:** 7h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar sobre o `SkillEngine` (BE-014): **Escudo** bloqueia 1 casa vazia por 2 turnos contra marcações do oponente; **Reforço** protege 1 peça própria contra remoção ou deslocamento por 2 turnos (não protege contra Congelar); **Campo de Força** fica ativo por 2 turnos e anula a primeira habilidade ofensiva recebida (Silêncio, Bomba, Tornado, Buraco Negro, Congelar e Ampulheta; é consumido e não reflete); **Ampulheta** reduz o próximo turno do oponente para 5s; **Silêncio** impede o oponente de usar habilidades no próximo turno dele (ele joga uma peça normalmente); **Troca Rápida** move 1 peça própria para casa adjacente vazia. Testes por habilidade, incluindo as interações entre elas.

**Requisitos ERS:** RF-ARN-03, RN-ARN-04, RN-ARN-05.

**Dependências:** BE-014

**Anexos / Links de Apoio:** game/skill/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Cada uma das 6 habilidades produz o efeito descrito
- [ ] Campo de Força anula a primeira ofensiva e é consumido
- [ ] Reforço protege contra Bomba, Tornado e Buraco Negro, mas não contra Congelar
- [ ] Ampulheta aplica 5s ao próximo turno do oponente
- [ ] Testes cobrem as interações entre habilidades

---

### [WBS-1.1.2.3] BE-015 — Regra de uso único e consumo de turno

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Victor
**Início:** 10/10/2026  |  **Término:** 12/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Garantir que cada habilidade só pode ser ativada uma vez por round e que ativá-la consome a ação do turno: não é possível marcar peça na mesma jogada nem usar 2 habilidades. Os marcadores de uso são zerados ao início de cada round. Silêncio bloqueia a ativação no turno afetado.

**Observação (troca de turno provisória):** como o TurnService (BE-020) ainda não existe nesta etapa, a passagem de turno ao ativar a habilidade deve ser feita pelo `update` do GameStateManager (BE-017), alterando o jogador do turno atual e recalculando o prazo do turno. Essa troca deve ficar isolada em um único método, para ser substituída por uma chamada ao TurnService quando a BE-020 for concluída.

**Requisitos ERS:** RN-ARN-02.

**Dependências:** BE-014, BE-017.1

**Anexos / Links de Apoio:** game/skill/, game/logic/GameStateManager.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Habilidade já usada no round não pode ser reutilizada
- [ ] Ativar habilidade consome a ação do turno e passa o turno ao oponente
- [ ] Marcadores de uso zeram no início do round
- [ ] Troca de turno em método isolado e substituível pelo TurnService

---

### [WBS-1.1.1.2] BE-016 — Expandir entidade Game (tabuleiro, rounds, morte súbita) - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Victor
**Início:** 26/09/2026  |  **Término:** 27/09/2026  |  **Esforço estimado:** 6h
**Prioridade:** Alta

**Objetivo / Descrição:**
Expandir a entidade Game para suportar tabuleiro 4x4, contagem de rounds (melhor de 3), vitórias por round e flag de modo morte súbita no 3º round, com contador de 5 segundos.

**Dependências:** —

**Anexos / Links de Apoio:** GDD seção 2.1 e 2.3

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Schema migrado sem quebrar dados existentes
- [ ] Entidade suporta consultar round atual e placar da partida

---

### [WBS-1.1.1.2] BE-016.1 — Ajustar Game: motivo de encerramento, W.O. e recompensas por motivo

**Labels:** back-end, fix
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 08/10/2026  |  **Término:** 09/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
Correção da BE-016 (concluída, não alterada). A entidade `Game` só consegue encerrar por placar e não tem datas. Adicionar `end_reason` (NORMAL, FORFEIT_TIMEOUT), `started_at`, `finished_at` e o método `forfeit(loser)`, que encerra a **partida inteira**, define o vencedor e registra o motivo. Ajustar `GameService.grantRewards`: partida normal mantém 50/10/20 (vitória/derrota/empate); **W.O. paga 30 ao vencedor e 0 ao perdedor**. Manter a idempotência por `rewardsGranted`. As datas são necessárias para ordenar o histórico e calcular a maior sequência de vitórias.

**Requisitos ERS:** RF-POS-01, RF-POS-02, RN-ARN-01, RF-PRF-01.

**Dependências:** BE-016

**Anexos / Links de Apoio:** game/Game.java, game/GameService.java, resources/db/migration

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `Game.forfeit(loser)` encerra a partida com o outro jogador como vencedor e `end_reason` correto
- [ ] W.O. credita 30 ao vencedor e 0 ao perdedor, uma única vez
- [ ] Partida normal continua pagando 50/10/20
- [ ] `started_at` e `finished_at` preenchidos
- [ ] Testes de `Game`/`GameService` atualizados e passando

---

### [WBS-1.1.1.2] BE-017 — GameStateManager — estado da partida em Redis - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Victor
**Início:** 27/09/2026  |  **Término:** 01/10/2026  |  **Esforço estimado:** 10h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar o gerenciamento do estado ativo da partida (grid, turno atual, tempo restante, quem começa) em Redis, com leitura/escrita segura contra condições de corrida entre jogadas simultâneas.

**Dependências:** BE-016

**Anexos / Links de Apoio:** —

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Estado é lido/escrito corretamente sob concorrência (teste com 2 requisições simultâneas)
- [ ] TTL configurado para partidas abandonadas
- [ ] Nenhum acesso a Redis é feito fora do backend

---

### [WBS-1.1.2.2] BE-017.1 — Estender GameState (jogadores, marcas, contador de timeouts, primeiro jogador)

**Labels:** back-end, refactor
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 08/10/2026  |  **Término:** 08/10/2026  |  **Esforço estimado:** 2h
**Prioridade:** Alta

**Objetivo / Descrição:**
Complemento da BE-017 (concluída, não alterada). Hoje o `GameState` guarda só `firstPlayerId` e `currentTurnPlayerId`, então o TurnService e os handlers não conseguem descobrir o oponente nem qual marca (X/O) pertence a cada jogador. Adicionar `player1Id`, `player2Id`, o mapeamento jogador → marca, o contador de timeouts consecutivos por jogador (não zera na troca de round) e a regra do primeiro jogador: sorteado de forma aleatória (`SecureRandom` injetável, para testes) somente no round 1 e alternado a cada round seguinte (P1, P2, P1 na morte súbita). Criar o helper de início de round que limpa o tabuleiro preservando placar e contadores. Documentar no código que o mutator do `update` é reexecutado em conflito de versão e, por isso, deve ser puro (sem broadcast, sem consumir RNG, sem persistência dentro dele).

**Requisitos ERS:** RF-ARN-02, RN-ARN-01, RF-ARN-05.

**Dependências:** BE-017

**Anexos / Links de Apoio:** game/logic/GameState.java, game/logic/GameStateManager.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `GameState` expõe os dois jogadores e a marca de cada um
- [ ] Primeiro jogador do round 1 é aleatório e alterna a partir do round 2 (teste com gerador injetado)
- [ ] Contador de timeouts consecutivos por jogador existe e não é zerado ao trocar de round
- [ ] Testes existentes de `GameState`/`GameStateManager` continuam passando

---

### [WBS-1.1.2.1] BE-018 — MoveValidator — validar jogada no tabuleiro - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 01/10/2026  |  **Término:** 03/10/2026  |  **Esforço estimado:** 6h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar a validação de jogadas no grid 4x4: posição livre, turno correto do jogador, casa não bloqueada por habilidade ativa.

**Dependências:** BE-017

**Anexos / Links de Apoio:** —

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Jogada em casa ocupada é rejeitada
- [ ] Jogada fora do turno do jogador é rejeitada
- [ ] Jogada em casa bloqueada por Escudo é rejeitada

---

### [WBS-1.1.2.1] BE-018.1 — Ajustar validadores: prazo, exceções tipadas e resolução de vitória

**Labels:** back-end, fix
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 09/10/2026  |  **Término:** 11/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
Correções das BE-018 e BE-019 (concluídas, não alteradas). (1) `MoveValidator` passa a rejeitar jogada após o prazo do turno, com round/partida encerrados ou durante o Draft; as violações viram exceções tipadas (com código de erro) em vez de `IllegalStateException`. As casas bloqueadas passam a vir do `GameState` quando a BE-014 existir. (2) `GameValidator` ganha `resolveWinner(board, frozenPositions, actor)`: peça congelada não conta para linha; confere primeiro o jogador que agiu e depois o oponente (vitória "automática" quando o congelamento acaba, desde que o oponente não tenha vencido antes nem desfeito a linha). `isDraw` considera as mesmas regras. A conferência roda também em turno perdido por timeout.

**Requisitos ERS:** RF-ARN-02, RF-ARN-03 (Congelar), RF-ARN-04, RN-ARN-03, RNF-ARQ-02.

**Dependências:** BE-018, BE-019

**Anexos / Links de Apoio:** game/logic/MoveValidator.java, game/logic/GameValidator.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Jogada após o prazo é rejeitada com exceção tipada
- [ ] `resolveWinner` ignora peças congeladas e prioriza o jogador que agiu
- [ ] Linha formada com peça congelada vale quando o congelamento acaba, se o oponente não venceu nem a desfez
- [ ] Testes unitários cobrem os cenários acima e `mvn test` passa

---

### [WBS-1.1.2.1] BE-019 — GameValidator — vitória e empate - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 04/10/2026  |  **Término:** 06/10/2026  |  **Esforço estimado:** 6h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar a verificação de condição de vitória (4 símbolos iguais em linha, coluna ou diagonal no grid 4x4) e de empate (tabuleiro cheio sem vencedor), conforme GDD 2.1.

**Dependências:** BE-017

**Anexos / Links de Apoio:** GDD seção 2.1

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Vitória é detectada corretamente nas 3 direções (H/V/D)
- [ ] Empate é detectado quando o tabuleiro enche sem vencedor

---

### [WBS-1.1.2.2] BE-020 — TurnService — alternância de turnos e timers (10s / 5s)

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Felipe
**Início:** 12/10/2026  |  **Término:** 15/10/2026  |  **Esforço estimado:** 8h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar a alternância de turnos e o controle de tempo no servidor: **10s** por turno, **5s** na morte súbita, e 5s no turno afetado por Ampulheta. A expiração acontece no servidor, sem depender de mensagem do cliente (scheduler com expiração idempotente) e é contada desde o início do turno, sem pausas. Turno que expira passa a vez, incrementa o contador de timeouts do jogador (BE-017.1) e dispara a verificação de vitória do fim de ação (BE-018.1). O prazo do turno é publicado como timestamp para o broadcast (BE-024).

**Observação (substituir troca provisória da BE-015):** ao concluir, a troca de turno provisória feita na BE-015 (ativar habilidade, via `update` do GameStateManager) deve passar a usar o TurnService, removendo a lógica duplicada.

**Requisitos ERS:** RF-ARN-02, RF-ARN-05, RN-ARN-03, RNF-ARQ-02.

**Dependências:** BE-017.1, BE-018.1

**Anexos / Links de Apoio:** game/service/TurnService.java; BE-015 (troca provisória a ser substituída)

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Turno alterna entre os dois jogadores
- [ ] Turno expira sozinho em 10s (5s na morte súbita; 5s com Ampulheta) e passa para o oponente
- [ ] Timeout incrementa o contador de timeouts consecutivos
- [ ] Expiração é idempotente (não passa o turno duas vezes)
- [ ] BE-015 passa a usar o TurnService

---

### [WBS-1.1.2.2] BE-021 — Regra de W.O. por inatividade (3 timeouts consecutivos)

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 24/10/2026  |  **Término:** 25/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
O jogador que estoura o tempo em **3 turnos consecutivos** perde a **partida inteira** por W.O. O contador (BE-017.1) **não zera na troca de round** e zera quando o jogador joga normalmente (peça ou habilidade); auto-escolhas do Draft e do sorteio não contam. Ao atingir 3: `Game.forfeit(loser)` (BE-016.1), vencedor recebe 30 coins e o perdedor 0, e a notificação de fim de partida carrega a indicação de W.O.

**Requisitos ERS:** RN-ARN-01, RF-POS-01, RF-POS-02.

**Dependências:** BE-020, BE-016.1

**Anexos / Links de Apoio:** game/service/TurnService.java, game/Game.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] 3 timeouts consecutivos encerram a partida por W.O. com `end_reason` correto
- [ ] Contador zera ao jogar normalmente e não zera ao trocar de round
- [ ] Auto-escolha do Draft/sorteio não conta como timeout
- [ ] W.O. paga 30 ao vencedor e 0 ao perdedor

---

### [WBS-1.1.3.2] BE-022 — PlayerService — estado de conexão dos jogadores - Concluída ✔

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Felipe
**Início:** 01/10/2026  |  **Término:** 03/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Média

**Objetivo / Descrição:**
Gerenciar o estado de conexão/desconexão dos jogadores dentro de uma sala/partida, servindo de base para BE-021 e BE-025.

**Dependências:** BE-008

**Anexos / Links de Apoio:** —

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Estado de conectado/desconectado é atualizado corretamente
- [ ] Desconexão de um jogador é detectável pelo backend

---

### [WBS-1.1.3.2] BE-023 — Handlers STOMP de gameplay (jogar / ativar habilidade)

**Labels:** back-end, feature
**Categoria:** WebSocket
**Membro Atribuído:** @Victor
**Início:** 15/10/2026  |  **Término:** 20/10/2026  |  **Esforço estimado:** 12h
**Prioridade:** Alta

**Objetivo / Descrição:**
Implementar os handlers STOMP (`@MessageMapping`) para as ações 'jogar' e 'ativar habilidade', delegando validação para `MoveValidator`/`GameValidator`/`SkillEngine`, atualizando o estado via `GameStateManager` e executando a rotina de fim de ação (aplicar → `resolveWinner` → contadores). O jogador vem do `Principal` (BE-040) e os erros tipados chegam ao usuário em `/user/queue/errors` (BE-044). Ao fim de round ou de partida, chamar a interface `RoundLifecycle`, implementada na BE-038.

**Requisitos ERS:** RF-ARN-01, RF-ARN-03, RF-ARN-04, RNF-ARQ-02.

**Dependências:** BE-018.1, BE-020, BE-015, BE-040, BE-044

**Anexos / Links de Apoio:** websocket/, game/service/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Jogada inválida é rejeitada com mensagem de erro clara no canal do usuário
- [ ] Jogada válida atualiza o estado da partida e roda a verificação de vitória
- [ ] Ativar habilidade segue as regras de BE-013/014/015
- [ ] Jogador é identificado pelo `Principal`, nunca pelo payload

---

### [WBS-1.1.3.2] BE-024 — Broadcast do estado atualizado da partida

**Labels:** back-end, feature
**Categoria:** WebSocket
**Membro Atribuído:** @Victor
**Início:** 22/10/2026  |  **Término:** 24/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
Após cada ação válida, transmitir o novo estado aos dois clientes, conforme o fluxo descrito na arquitetura (seção 3). O estado inclui: prazo do turno como timestamp do servidor, round, placar, flag de morte súbita, estado de cada casa (vazia, P1, P2, bloqueada, congelada ou reforçada) e o motivo de W.O. ao fim da partida. As 3 opções do sorteio vão apenas para a fila do dono. Registrar a latência de entrega para acompanhar a meta de 200 ms.

**Requisitos ERS:** RF-ARN-01, RF-ARN-02, RF-ARN-05, RF-POS-01, RNF-ARQ-01.

**Dependências:** BE-023

**Anexos / Links de Apoio:** Documento de arquitetura, seção 3

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Ambos os jogadores recebem o estado atualizado em tempo real, com timestamp do prazo
- [ ] Estado traz placar, round, morte súbita e casas especiais
- [ ] Fim de partida (vitória/empate/W.O.) é notificado a ambos
- [ ] Opções do sorteio não vazam para o oponente

---

### [WBS-1.1.3.2] BE-025 — Tratamento de desconexão e reconexão

**Labels:** back-end, feature
**Categoria:** WebSocket
**Membro Atribuído:** @Victor
**Início:** 24/10/2026  |  **Término:** 27/10/2026  |  **Esforço estimado:** 6h
**Prioridade:** Média

**Objetivo / Descrição:**
Tratar a desconexão de um jogador durante a partida (integrando com PlayerService/BE-022) e permitir reconexão à mesma partida em andamento, devolvendo um snapshot completo com o prazo do turno. O timer da partida continua correndo durante a desconexão (sem pausas), então os timeouts acumulam e levam ao W.O. pela BE-021. Configurar heartbeat no broker (hoje não há, e quedas de rede abruptas não são detectadas) e TTL nas chaves de presença no Redis.

**Requisitos ERS:** RN-ARN-03, RN-POS-01, RNF-ARQ-01.

**Dependências:** BE-023, BE-022, BE-040

**Anexos / Links de Apoio:** websocket/, game/PlayerService.java, config/WebSocketConfig.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Desconexão não trava a partida indefinidamente
- [ ] Jogador reconecta e recebe o estado atual com o prazo do turno
- [ ] Heartbeat configurado e presença com TTL
- [ ] Teste de integração cobre desconexão e reconexão

---

### [WBS-1.1.1.1] BE-026 — Sistema de moedas virtuais (coins): saldo e extrato

**Labels:** back-end, feature
**Categoria:** Economia
**Membro Atribuído:** @Rafael
**Início:** 15/10/2026  |  **Término:** 17/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Média

**Objetivo / Descrição:**
O crédito ao fim da partida já existe em `GameService.grantRewards` (e a variação por W.O. entra na BE-016.1). Esta tarefa fica com: tabela `coin_transactions` (extrato: tipo, valor, saldo resultante, origem), registro do extrato dentro da mesma transação do crédito de recompensa, consulta de saldo (`GET` do saldo do usuário) e retorno do valor creditado para o `GAME_OVER` (a mensagem é enviada pela BE-038).

**Requisitos ERS:** RF-POS-02, RF-HUB-02, RF-LOJ-01, RF-LOJ-02 (Tabela de Transações, WBS 1.1.1.1).

**Dependências:** BE-016.1

**Anexos / Links de Apoio:** game/GameService.java, auth/User.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Cada crédito/débito gera uma linha de extrato com saldo resultante
- [ ] Saldo do usuário é consultável
- [ ] Recompensa continua idempotente (sem crédito duplicado)
- [ ] Testes de crédito e de extrato passando

---

### [WBS-1.1.1.1] BE-027 — Perfil do jogador: apelido, estatísticas e histórico

**Labels:** back-end, feature
**Categoria:** Economia
**Membro Atribuído:** @Rafael
**Início:** 18/10/2026  |  **Término:** 21/10/2026  |  **Esforço estimado:** 6h
**Prioridade:** Média

**Objetivo / Descrição:**
Expandir a tarefa original. (1) `GET` do perfil com apelido, saldo e personagem favorito/ativo; (2) alteração do apelido (mesmas regras do cadastro: 3 a 15 caracteres, único sem diferenciar maiúsculas); (3) histórico de partidas com resultado e indicação de W.O.; (4) estatísticas: total de partidas, vitórias, taxa de vitória e maior sequência de vitórias (empate e derrota quebram a sequência; vitória por W.O. conta como vitória). Recomendado manter uma tabela `user_stats` atualizada na mesma transação das recompensas (idempotente via `rewardsGranted`).

**Requisitos ERS:** RF-PRF-01, RF-HUB-02, RF-HUB-03, RF-POS-01.

**Dependências:** BE-016.1, BE-026, BE-028

**Anexos / Links de Apoio:** auth/User.java, game/Game.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Perfil retorna apelido, saldo e personagem favorito
- [ ] Apelido duplicado (sem diferenciar maiúsculas) é rejeitado
- [ ] Estatísticas e maior sequência batem com o histórico de testes (empate quebra a sequência)
- [ ] Histórico indica partidas encerradas por W.O.

---

### [WBS-1.1.1.2] BE-028 — Posse de personagens por usuário e itens gratuitos (cosmético/visual)

**Labels:** back-end, feature
**Categoria:** Loja Cosmética
**Membro Atribuído:** @Rafael
**Início:** 10/10/2026  |  **Término:** 12/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Média

**Objetivo / Descrição:**
O preço em coins dos personagens já existe (migration V9). Falta a relação de posse: criar `user_characters` (migration + entidade/repository) e garantir que X e O sejam gratuitos para todo jogador (por preço 0 ou concessão no cadastro). Corrigir o construtor de `Character`, que não preenche `price_coins` (coluna NOT NULL). Expor o inventário de personagens do usuário. Sem poderes vinculados (puramente cosmético).

**Requisitos ERS:** RN-LOJ-01, RF-PRF-02, RF-LOJ-01.

**Dependências:** BE-010

**Anexos / Links de Apoio:** character/Character.java, resources/db/migration (V9)

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Personagem tem preço definido e construtor consistente com a coluna
- [ ] É possível registrar e consultar os personagens que um usuário possui
- [ ] X e O estão disponíveis para todo jogador sem compra
- [ ] Inventário de personagens consultável

---

### [WBS-1.1.1.1] BE-029 — Endpoints da loja (listar / comprar / equipar) — personagens e habilidades

**Labels:** back-end, feature
**Categoria:** Loja Cosmética
**Membro Atribuído:** @Lorenzo
**Início:** 24/10/2026  |  **Término:** 28/10/2026  |  **Esforço estimado:** 7h
**Prioridade:** Média

**Objetivo / Descrição:**
Generalizar a tarefa original para personagens **e** habilidades: listar itens com preço e indicação de posse, comprar com coins e equipar o personagem favorito. Compra com débito atômico no saldo, registro no extrato (BE-026) e restrição única (usuário, item) contra clique duplo. Comprar habilidade amplia o pool de iniciais elegíveis, e o jogador continua escolhendo só 2 no Draft. A aba "Cosméticos" da ERS não tem definição e não entra nesta tarefa.

**Requisitos ERS:** RF-LOJ-01, RF-LOJ-02, RN-LOJ-02, RN-LOJ-03, RF-HUB-03.

**Dependências:** BE-026, BE-028, BE-041

**Anexos / Links de Apoio:** character/, skill/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Compra desconta o valor correto e registra extrato
- [ ] Compra com saldo insuficiente é rejeitada
- [ ] Compra repetida do mesmo item é rejeitada
- [ ] Usuário só equipa personagem que possui
- [ ] Habilidade comprada entra no pool de elegíveis

---

### [WBS-1.1.3.2] BE-032 — Teste de integração do fluxo completo de partida

**Labels:** back-end, test
**Categoria:** Testes
**Membro Atribuído:** @Victor
**Início:** 25/10/2026  |  **Término:** 28/10/2026  |  **Esforço estimado:** 8h
**Prioridade:** Alta

**Objetivo / Descrição:**
Criar um teste de integração (Testcontainers) cobrindo o fluxo completo via WebSocket: cadastro/login, criar e entrar na sala, evento de match, Draft, partida com jogadas, uso de habilidade, W.O. por 3 timeouts e fim de partida com recompensas. Reconexão (BE-025) e revanche (BE-039) têm testes próprios nas respectivas tarefas. Inclui checagem simples de latência.

**Requisitos ERS:** RNF-ARQ-01, RNF-ARQ-02 e fluxos de todas as fases.

**Dependências:** BE-023, BE-024, BE-038, BE-037, BE-013.1, BE-014.1, BE-014.2

**Anexos / Links de Apoio:** src/test/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Fluxo completo de uma partida é validado de ponta a ponta
- [ ] W.O. e fim de partida com recompensas cobertos
- [ ] Teste roda em ambiente de CI local (`mvn test`)

---

### [WBS-1.1.1.3] BE-033 — Preparar ambiente de produção (Render + Neon + Upstash)

**Labels:** back-end, feature
**Categoria:** Deploy
**Membro Atribuído:** @Felipe
**Início:** 20/10/2026  |  **Término:** 21/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Média

**Objetivo / Descrição:**
Antecipar tudo o que não depende do fluxo completo da partida: criar os serviços no Render, na Neon e na Upstash, configurar as variáveis de ambiente de produção (sem segredos versionados) e ajustar o `application-prod.yml`: senha e TLS do Redis (a Upstash exige), `sslmode` do PostgreSQL e `show-sql: false`. Validar `GET /api/health` em produção com a versão atual do `dev`. O deploy final com a partida completa fica na BE-033.1.

**Requisitos ERS:** RNF-ARQ-01 (disponibilidade em produção).

**Dependências:** —

**Anexos / Links de Apoio:** README seção Plano de Produção, application-prod.yml, .env.example

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] API responde publicamente via URL do Render
- [ ] Conexão com Neon e Upstash funcionando em produção
- [ ] Variáveis de produção configuradas e sem segredos versionados
- [ ] `show-sql` desligado em produção

---

### [WBS-1.1.1.3] BE-033.1 — Deploy final e smoke test em produção

**Labels:** back-end, feature
**Categoria:** Deploy
**Membro Atribuído:** @Felipe
**Início:** 29/10/2026  |  **Término:** 30/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
Publicar a versão final do `dev` no ambiente preparado na BE-033 e executar um smoke test: cadastro, login, criar sala, entrar na sala por segundo cliente, partida curta via WebSocket e fim de partida. Registrar a URL e o procedimento de rollback no README.

**Dependências:** BE-032, BE-033, BE-025

**Anexos / Links de Apoio:** README.md

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Fluxo mínimo de partida funciona em produção
- [ ] `GET /api/health` responde 200
- [ ] README com URL de produção e procedimento de rollback

---

### [WBS-1.1.1.3] BE-034 — Configurar CORS para o frontend (Vercel)

**Labels:** back-end, feature
**Categoria:** Deploy
**Membro Atribuído:** @Felipe
**Início:** 16/10/2026  |  **Término:** 16/10/2026  |  **Esforço estimado:** 2h
**Prioridade:** Média

**Objetivo / Descrição:**
Configurar CORS no backend liberando a origem do frontend hospedado na Vercel (e `localhost` para desenvolvimento), por variável de ambiente. A mesma lista de origens é usada pelo endpoint `/ws` (BE-040).

**Dependências:** BE-005

**Anexos / Links de Apoio:** config/SecurityConfig.java, application.yml

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Requisições do domínio do frontend na Vercel são aceitas
- [ ] Origens não autorizadas continuam bloqueadas
- [ ] Lista de origens configurável por variável de ambiente

---

### [WBS-1.1.3.2] BE-035 — Eventos de sala em tempo real (match feito)

**Labels:** back-end, feature
**Categoria:** WebSocket
**Membro Atribuído:** @Felipe
**Início:** 17/10/2026  |  **Término:** 18/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Alta

**Objetivo / Descrição:**
Publicar em `/topic/room/{code}` o evento de entrada do 2º jogador, com o nome do adversário, para disparar o "MATCH FEITO!" no host. Hoje o join só devolve REST para quem entra e o host nunca é avisado. O evento é enviado após a transação de entrada na sala e identifica o destinatário pelo `Principal` (BE-040).

**Requisitos ERS:** RF-SAL-03 (WBS 1.1.3.2).

**Dependências:** BE-040, BE-009.1

**Anexos / Links de Apoio:** room/RoomService.java, config/WebSocketConfig.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Host recebe o evento com o nome do adversário ao entrar o 2º jogador
- [ ] Evento não é enviado em tentativas de entrada rejeitadas
- [ ] Teste de integração STOMP cobre o evento

---

### [WBS-1.1.3.1] BE-036 — Sair, encerrar e expirar salas abandonadas

**Labels:** back-end, feature
**Categoria:** Sala
**Membro Atribuído:** @Felipe
**Início:** 18/10/2026  |  **Término:** 20/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Não existe como sair de uma sala: `requireAvailablePlayer` trava o jogador ("já está em uma sala ativa") e salas `WAITING` abandonadas nunca expiram. Criar `Room.close()`, o endpoint de saída (`leave`), a expiração agendada de salas `WAITING` sem 2º jogador, e o evento `ROOM_CLOSED` ao oponente. Saída do host em sala aguardando fecha a sala.

**Requisitos ERS:** RN-POS-03, RN-POS-01, RN-SAL-01.

**Dependências:** BE-009.1, BE-035

**Anexos / Links de Apoio:** room/Room.java, room/RoomService.java, room/RoomController.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Jogador consegue sair e criar/entrar em outra sala
- [ ] Sala `WAITING` abandonada expira automaticamente
- [ ] Oponente é notificado do fechamento
- [ ] Testes de `Room`/`RoomService` cobrem saída e expiração

---

### [WBS-1.1.2.2] BE-037 — Máquina de estados do Draft (20s, bloqueio e auto-escolha)

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Lorenzo
**Início:** 16/10/2026  |  **Término:** 19/10/2026  |  **Esforço estimado:** 8h
**Prioridade:** Alta

**Objetivo / Descrição:**
Criar o `DraftState` no Redis (etapa 1: personagem; etapa 2: 2 habilidades iniciais) com timer geral de 20s controlado pelo servidor, status de "pronto" do oponente em tempo real e **auto-escolha aleatória** dentre os itens elegíveis do jogador quando o tempo acaba (a auto-escolha não conta como timeout para o W.O.). Ao concluir os dois jogadores, publica o evento `DraftCompleted`, consumido pela orquestração da partida (BE-038). A elegibilidade (personagens e habilidades que o jogador possui) é consultada por interface, implementada pelas BE-028 e BE-041. As escolhas em si são implementadas nas BE-011 (personagem) e BE-013 (habilidades), plugadas nesta máquina de estados.

**Requisitos ERS:** RF-DRAFT-01, RN-DRAFT-02, RN-DRAFT-04.

**Dependências:** BE-040, BE-017.1

**Anexos / Links de Apoio:** game/draft/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Draft expira em 20s e completa as escolhas pendentes de forma aleatória entre os itens elegíveis
- [ ] Status de pronto do oponente é publicado em tempo real
- [ ] Ao concluir, sinaliza o início da partida
- [ ] Testes cobrem conclusão antecipada e timeout

---

### [WBS-1.1.2.2] BE-038 — Orquestração de partida e rounds (início, fim de round e fim de partida)

**Labels:** back-end, feature
**Categoria:** Núcleo do jogo
**Membro Atribuído:** @Victor
**Início:** 20/10/2026  |  **Término:** 24/10/2026  |  **Esforço estimado:** 10h
**Prioridade:** Alta

**Objetivo / Descrição:**
Hoje nada cria `Game` e `GameState` a partir da sala e nada chama `GameService.registerRoundResult`. Implementar: (1) início da partida ao receber o evento `DraftCompleted` da BE-037 (cria `Game` + `GameState`, primeiro jogador aleatório no round 1); (2) início de cada round: limpa o tabuleiro, alterna o primeiro jogador, chama o serviço de sorteio por interface (implementado na BE-013.1, sorteio de 8s) e começa os turnos (3º round com 5s por turno); (3) fim de round: registra o resultado (`registerRoundResult`) e aplica a regra de melhor de 3, que já existe em `Game` (1x0 após o round 2 encerra); (4) fim de partida: persiste, concede recompensas, publica `GAME_OVER` (placar final, W.O., coins creditadas), limpa o Redis e atualiza a sala.

**Requisitos ERS:** RF-ARN-04, RF-ARN-05, RN-ARN-06, RF-POS-01, RF-POS-02.

**Dependências:** BE-020, BE-016.1, BE-018.1, BE-017.1

**Anexos / Links de Apoio:** game/service/GameService.java, game/GameController.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Concluir o Draft cria `Game` e `GameState` e inicia o round 1
- [ ] Round termina por vitória/empate e o próximo começa com tabuleiro limpo e primeiro jogador alternado
- [ ] Partida encerra conforme melhor de 3 (inclusive 1x0 após o round 2) e na morte súbita
- [ ] `GAME_OVER` é publicado com placar, W.O. e coins creditadas; Redis é limpo
- [ ] Teste de integração cobre uma partida de 2 rounds

---

### [WBS-1.1.2.4] BE-039 — Pós-jogo: revanche e voltar ao lobby

**Labels:** back-end, feature
**Categoria:** Pós-jogo
**Membro Atribuído:** @Felipe
**Início:** 26/10/2026  |  **Término:** 29/10/2026  |  **Esforço estimado:** 8h
**Prioridade:** Média

**Objetivo / Descrição:**
A WBS 1.1.2.4 não tinha nenhuma tarefa. Implementar: pedido de revanche com janela de 15s, aceite e recusa; aceite mútuo reinicia o Draft com seleções resetadas e um novo `Game` na mesma sala; cancelamento do pedido fecha a sala e notifica o Jogador A; "Voltar ao Lobby" fecha a sala, notifica o oponente e desabilita a revanche; desconexão na tela de resultado cancela o pedido (usa o estado de conexão da BE-022).

**Requisitos ERS:** RF-POS-03, RN-POS-01, RN-POS-02, RN-POS-03.

**Dependências:** BE-038, BE-036

**Anexos / Links de Apoio:** room/, game/

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Aceite mútuo em até 15s reinicia o Draft na mesma sala
- [ ] Recusa ou expiração fecha a sala e notifica os dois
- [ ] "Voltar ao Lobby" fecha a sala e desabilita a revanche
- [ ] Desconexão cancela o pedido pendente

---

### [WBS-1.1.3.2] BE-040 — Autenticar conexões STOMP com JWT

**Labels:** back-end, feature
**Categoria:** WebSocket
**Membro Atribuído:** @Victor
**Início:** 12/10/2026  |  **Término:** 14/10/2026  |  **Esforço estimado:** 5h
**Prioridade:** Alta

**Objetivo / Descrição:**
O endpoint `/ws/**` é `permitAll` e o `PlayerConnectionController` aceita `playerId` no payload, permitindo se passar por outro jogador. Criar um `ChannelInterceptor` que valida o JWT no `CONNECT` e define o `Principal` da sessão; os handlers passam a usar o `Principal` e o `playerId` do payload é removido. Conexões sem token válido são recusadas. Restringir as origens permitidas do `/ws` (hoje `*`) à lista de origens do BE-034.

**Requisitos ERS:** RNF-ARQ-02 (autoridade estrita no servidor), RN-POS-01.

**Dependências:** BE-005, BE-044

**Anexos / Links de Apoio:** config/WebSocketConfig.java, config/SecurityConfig.java, websocket/PlayerConnectionController.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `CONNECT` sem JWT válido é recusado
- [ ] Handlers STOMP identificam o jogador pelo `Principal`
- [ ] Payload de conexão não carrega mais o `playerId`
- [ ] Teste de integração cobre conexão válida e inválida

---

### [WBS-1.1.1.2] BE-041 — Posse e preço de habilidades (inventário, iniciais gratuitas e pool elegível)

**Labels:** back-end, feature
**Categoria:** Habilidades
**Membro Atribuído:** @Rafael
**Início:** 12/10/2026  |  **Término:** 15/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Adicionar `price_coins` em `skills` e criar `user_skills` (migration + entidade/repository). Bomba, Escudo e Ampulheta são gratuitas para todo jogador (RN-LOJ-01). Expor o inventário de habilidades e o **pool de iniciais elegíveis** do jogador (iniciais gratuitas + compradas), que alimenta o Draft: o jogador escolhe 2 dentre as elegíveis.

**Requisitos ERS:** RN-LOJ-01, RN-LOJ-02, RF-PRF-02.

**Dependências:** BE-012.1

**Anexos / Links de Apoio:** skill/Skill.java, skill/SkillRepository.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] `skills` tem preço e `user_skills` registra a posse
- [ ] Todo jogador possui Bomba, Escudo e Ampulheta sem compra
- [ ] Consulta retorna o pool de iniciais elegíveis do jogador
- [ ] Testes de repository/serviço passando

---

### [WBS-1.1.1.3] BE-044 — Tratamento global de erros (REST e STOMP) e padronização do prefixo da API

**Labels:** back-end, refactor
**Categoria:** Infraestrutura
**Membro Atribuído:** @Felipe
**Início:** 10/10/2026  |  **Término:** 12/10/2026  |  **Esforço estimado:** 4h
**Prioridade:** Alta

**Objetivo / Descrição:**
Hoje só existe o handler do pacote `room` (com a chave malformada `"erro: "`) e as rotas estão inconsistentes (`/api/health`, `/rooms`, `/auth`, e `/api/auth/...` na BE-006.1). Criar um `@RestControllerAdvice` global no mesmo formato do 401 (timestamp, status, error, message, path) e um `@MessageExceptionHandler` que envia o erro ao usuário em `/user/queue/errors`. Definir o prefixo único `/api` para os endpoints REST e ajustar `SecurityConfig` (rotas públicas) e Swagger. Combinar a mudança de rotas com o time do frontend antes do merge.

**Requisitos ERS:** apoio a RF-HUB-05 (mensagens claras), RNF-ARQ-02.

**Dependências:** —

**Anexos / Links de Apoio:** config/SecurityConfig.java, room/RoomExceptionHandler.java, auth/AuthController.java

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Erros REST seguem um único formato em todos os pacotes
- [ ] Erros em handlers STOMP chegam ao usuário em `/user/queue/errors`
- [ ] Todas as rotas REST sob o mesmo prefixo, com `SecurityConfig` e Swagger coerentes
- [ ] Frontend avisado das rotas finais

---

### [WBS-1.1.1.1] BE-045 — Compra simbólica de coins (sem pagamento real)

**Labels:** back-end, feature
**Categoria:** Economia
**Membro Atribuído:** @Rafael
**Início:** 22/10/2026  |  **Término:** 23/10/2026  |  **Esforço estimado:** 3h
**Prioridade:** Baixa

**Objetivo / Descrição:**
Por ora a compra de coins com dinheiro real é apenas simbólica: não há transação financeira real nem cadastro de cartão. Criar o catálogo de pacotes (`coin_packages`), o pedido (`coin_orders`, com `payment_mode = SIMULATED`) e um endpoint de compra que credita as coins na hora e registra o extrato (BE-026). Proteger com uma flag de configuração (`payments.simulated.enabled`) para poder desligar em produção, e isolar o crédito atrás de uma interface `PaymentProvider` fina, para trocar por um gateway real no futuro.

**Requisitos ERS:** RN-LOJ-04 (RN-LOJ-03 continua valendo: todo item é obtível jogando).

**Dependências:** BE-026

**Anexos / Links de Apoio:** economia/coin_packages, economia/coin_orders

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] Listar pacotes e comprar um pacote credita as coins e registra extrato
- [ ] Flag desliga o endpoint
- [ ] Nenhum dado de cartão é recebido ou armazenado

---

## Reserva (fora do prazo de 31/10)

### [WBS-1.1.1.1] BE-006.1 — Implementação de Recuperação de Senha

**Labels:** back-end, feature
**Categoria:** Autenticação
**Membro Atribuído:** @Rafael (sugerido, se sobrar capacidade)
**Início:** a definir  |  **Término:** a definir  |  **Esforço estimado:** 6h
**Prioridade:** Baixa (reserva)

**Objetivo / Descrição:**
Implementar o fluxo de recuperação de senha por e-mail: (1) o usuário seleciona "Esqueci minha senha" e informa o e-mail cadastrado; (2) o sistema gera um token de recuperação de uso único e com expiração e envia um link por e-mail; (3) o link leva a uma tela simples com "Nova senha" e "Confirmar nova senha"; (4) após a alteração, o usuário faz login normalmente com a nova senha.

Escopo back-end:
- `POST /auth/forgot-password` (prefixo conforme BE-044) recebe `{ email }` e sempre responde 200 com mensagem genérica, exista o e-mail ou não, para não revelar quais e-mails estão cadastrados.
- `POST /auth/reset-password` (prefixo conforme BE-044) recebe `{ token, newPassword, confirmPassword }`.
- Entidade `PasswordResetToken` (id UUID, user, token guardado como hash, expiresAt, usedAt, createdAt) com migration Flyway.
- Serviço de e-mail (`JavaMailSender`) que monta o link no formato `{FRONTEND_RESET_PASSWORD_URL}?token={token}`.
- Variável `FRONTEND_RESET_PASSWORD_URL` em `application.yml` (`app.frontend.reset-password-url: ${FRONTEND_RESET_PASSWORD_URL:http://localhost:3000/reset-password}`) e em `.env.example` com valor provisório.
- SMTP por variáveis de ambiente (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`), sem credenciais reais commitadas. Para testes locais, usar SMTP falso (MailHog ou Mailtrap).
- Validações: senha com mínimo de 6 caracteres (mesma regra do cadastro) e `newPassword` igual a `confirmPassword`.
- Senha nova gravada com BCrypt; token invalidado após o uso, e pedidos antigos do mesmo usuário também invalidados.
- Rotas públicas liberadas no `SecurityConfig`.

**Pendência externa (não bloqueia esta tarefa):** a tela de redefinição de senha do frontend ainda não existe, então a URL final não está definida. Quando a tela estiver pronta, basta atualizar `FRONTEND_RESET_PASSWORD_URL` no ambiente, sem mudança de código. Enquanto isso, `reset-password` pode ser testado pelo Swagger ou Postman com o token recebido no e-mail.

**Fora do escopo (implementação simplificada):** os JWTs emitidos antes do reset continuam válidos até expirarem (sem invalidação de sessões antigas).

**Dependências:** BE-005, BE-006

**Anexos / Links de Apoio:** auth/AuthService.java, auth/PasswordResetService.java, auth/PasswordResetToken.java, auth/PasswordResetTokenRepository.java, auth/ForgotPasswordRequest.java, auth/ResetPasswordRequest.java, config/SecurityConfig.java, application.yml, .env.example

**Validação:**
- [ ] Pull Request criada no GitHub
- [ ] Link da Pull Request adicionado ao card
- [ ] Código revisado/validado
- [ ] Critérios de aceite atendidos
- [ ] Seguir Regras Gerais do Back-End
- [ ] README atualizado (se necessário)

**Critérios de Aceite (Definição de Pronto):**
- [ ] E-mail cadastrado recebe o link, e o e-mail não cadastrado recebe a mesma resposta 200 sem envio
- [ ] O link do e-mail usa o valor de `FRONTEND_RESET_PASSWORD_URL` e inclui o token como parâmetro `token`
- [ ] Trocar a variável de ambiente muda o link do e-mail, sem alterar código
- [ ] Token expirado, já usado ou inválido retorna erro tratado (400/410)
- [ ] Senha e confirmação diferentes, ou senha fora da regra, retornam 400
- [ ] Após o reset, o login funciona com a nova senha e falha com a antiga
- [ ] Token reutilizado não funciona uma segunda vez
- [ ] Senha e token nunca aparecem em texto plano no banco nem nos logs
- [ ] Endpoints documentados no Swagger

---


## Tarefas removidas

| ID | Motivo |
|----|--------|
| BE-030 — Testes unitários de game/logic | Os testes de `GameValidator`, `MoveValidator`, `GameState` e `GameStateManager` (incluindo concorrência) já existem no repositório. Testes novos acompanham cada tarefa (BE-014.x, BE-018.1, BE-020, BE-038). |
| BE-031 — Testes unitários de auth/JWT | `AuthService`, `JwtService` e o filtro de segurança já têm testes. Os testes do `AuthController` ficam na BE-006. |
| BE-042 e BE-043 (propostas na auditoria) | Absorvidas por BE-029 (loja de habilidades) e BE-027 (perfil e apelido). |
| BE-046 (gateway de pagamento) | Descartada: a compra de coins é apenas simbólica por ora (BE-045). |

## Cobertura da ERS pelo backlog

| Requisitos da ERS | Tarefas |
|-------------------|---------|
| RF-HUB-02, RF-HUB-03, RF-PRF-01, RF-PRF-02 | BE-027, BE-028, BE-041, BE-029 |
| RF-HUB-05, RF-SAL-01, RN-SAL-01 | BE-008 ✔, BE-009 ✔, BE-009.1, BE-036 |
| RF-SAL-03 | BE-035 |
| RF-DRAFT-01 a RF-DRAFT-03, RN-DRAFT-01 a RN-DRAFT-05 | BE-037, BE-011, BE-013, BE-013.1 |
| RF-ARN-01, RF-ARN-04 | BE-019 ✔, BE-018.1, BE-023, BE-024 |
| RF-ARN-02, RF-ARN-05, RN-ARN-03 | BE-016 ✔, BE-020, BE-038 |
| RF-ARN-03, RN-ARN-02, RN-ARN-04, RN-ARN-05 | BE-012.1, BE-014, BE-014.1, BE-014.2, BE-015 |
| RN-ARN-01 (W.O.), RF-POS-01 | BE-016.1, BE-021, BE-024 |
| RN-ARN-06, RF-POS-02 | BE-016 ✔, BE-038, BE-026 |
| RF-POS-03, RN-POS-01 a RN-POS-03 | BE-039, BE-036, BE-025 |
| RF-LOJ-01, RF-LOJ-02, RN-LOJ-01 a RN-LOJ-04 | BE-028, BE-041, BE-026, BE-029, BE-045 |
| RNF-ARQ-01 | BE-024, BE-025, BE-032 |
| RNF-ARQ-02 | BE-009.1, BE-018.1, BE-020, BE-040 |

## Pontos em aberto (premissas adotadas nos cards)

- Alvo reforçado por Reforço: a habilidade ofensiva falha, é consumida e o turno termina.
- O "sorteio de 8 segundos" foi interpretado como a janela de escolha entre as 3 habilidades sorteadas.
- A aba "Cosméticos" da loja não está definida na ERS e ficou fora do backlog.
- A ERS precisa ser atualizada nos pontos: código de sala numérico, "rodadas" → "turnos", Espelho → Campo de Força, W.O. em 3 timeouts, melhor de 3 (1x0 após o round 2) e sorteio de 8s.
