# BE-007 — implementação local concluída; integração pendente

O escopo local da BE-007 está implementado e validado em PostgreSQL descartável.
A migration real de users não faz parte desta tarefa: é uma dependência para
integrar rooms ao schema compartilhado, não um bloqueio da implementação local.
Commit, PR, revisão por outra pessoa e link no card ainda estão pendentes.

Room usa UUID, código textual de quatro dígitos, WAITING/IN_GAME/CLOSED e referências
a User. O segundo jogador é opcional enquanto aguarda. A API de entrada e as
transições ficam para BE-008; endpoints ficam para BE-009.

RoomCodeAllocator.reserve reserva o código e salva a sala na mesma transação.
O advisory lock PostgreSQL 7007 serializa reservas feitas por esse componente;
o índice único parcial é a última proteção contra outras gravações concorrentes.
Invocar pelo bean Spring, com isolamento READ_COMMITTED, não por instanciação direta.
O chamador deve finalizar a transação prontamente. Os 10.000 códigos são examinados
no máximo uma vez; esgotamento gera erro explícito.

## Gate de schema

A dev consultada (382f1d4) possui somente .gitkeep em db/migration.
Não há migrations de users/games. Não foram criadas essas tabelas por esta tarefa.
create_rooms.sql.example é um rascunho fora do classpath Flyway, sem versão atribuída.
Não é uma migration pronta para implantação.

A fixture de users existe apenas no teste de integração e não deve ser promovida
a migration da aplicação. Esta entrega não cria nem modifica users no banco do projeto.

Antes de ativar a migration, confirmar a migration de users e o tipo de sua PK,
coordenar a próxima versão com a equipe, verificar o histórico Flyway do ambiente
e validar via Flyway e JPA em PostgreSQL isolado. Não editar migrations aplicadas,
usar repair/baseline/clean, executar SQL manualmente ou mudar ddl-auto para update.

Não iniciar esta versão da aplicação contra o banco existente: Room é uma nova
entidade e ddl-auto=validate exige sua tabela. Os testes unitários não iniciam Spring
nem conectam a banco. A integração isolada é executada separadamente, conforme abaixo.

Reutilização do código após CLOSED é permitida pelo índice proposto, mas a política
de encerramento/reutilização ainda precisa de validação da equipe.

## Validação isolada (27/09/2026)

Quatro testes unitários e um teste de integração passaram em PostgreSQL 16.
O teste de integração usa somente o banco be007_validation, usuário be007_test,
loopback e uma porta explicitamente informada diferente de 5432. Não lê DB_URL
nem sobe o contexto completo da aplicação. Cada execução cria um schema exclusivo.

Flyway executa duas migrations Java exclusivas dos testes: uma fixture de users
compatível com a entidade atual e o SQL exato do rascunho de rooms. Essas versões
não reservam números para migrations da equipe e não fazem parte do artefato final.
Hibernate apenas valida o schema. Nenhum DDL é executado fora do Flyway.

Foram verificados: salvar/recarregar Room via JPA, zeros à esquerda, FK de usuário,
jogadores distintos, exigência de segundo jogador em IN_GAME, código numérico,
unicidade em WAITING e IN_GAME, reutilização após CLOSED e duas reservas simultâneas
disputando o último código livre. Exatamente uma reserva vence; a outra informa
esgotamento. Nova execução de migrate no mesmo schema não aplica migrations adicionais.

Para reproduzir, criar container descartável sem volumes compartilhados:

```powershell
docker run --rm -d --name be007-isolated-validation -e POSTGRES_USER=be007_test -e POSTGRES_PASSWORD=be007_test_only -e POSTGRES_DB=be007_validation -p 127.0.0.1::5432 postgres:16-alpine
docker port be007-isolated-validation 5432
# Substituir PORTA pela porta retornada, usando Java 25:
.\mvnw.cmd test "-Dtest=RoomTest,RoomCodeAllocatorTest,RoomPersistenceIT" "-Droom.test.port=PORTA"
# Remove exclusivamente o container descartável e seus dados de teste:
docker stop be007-isolated-validation
```

Estas credenciais são fixtures públicas de teste, não credenciais do projeto.
O IT não roda no mvn test padrão. A validação isolada não resolve a dependência
da migration real de users nem torna esta branch pronta para deploy.
