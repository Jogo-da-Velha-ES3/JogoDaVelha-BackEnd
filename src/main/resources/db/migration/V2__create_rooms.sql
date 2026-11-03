-- V2: tabela de salas (Room)
CREATE TABLE rooms (
                       id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       code        VARCHAR(4)  NOT NULL,
                       status      VARCHAR(16) NOT NULL,
                       player1_id  UUID        NOT NULL REFERENCES users(id),
                       player2_id  UUID        NULL     REFERENCES users(id),

                       CONSTRAINT ck_rooms_status CHECK (status IN ('WAITING', 'IN_GAME', 'CLOSED')),
                       CONSTRAINT ck_rooms_code_format CHECK (code ~ '^[0-9]{4}$'),
    CONSTRAINT ck_rooms_players_distinct CHECK (player2_id IS NULL OR player1_id <> player2_id)
);

-- Índices de FK (Postgres não cria automaticamente, só na PK)
CREATE INDEX ix_rooms_player1_id ON rooms(player1_id);
CREATE INDEX ix_rooms_player2_id ON rooms(player2_id);

-- Impede duas salas com o mesmo código de 4 dígitos enquanto estiverem ativas
-- (aguardando jogador ou em partida). Salas fechadas podem repetir código.
CREATE UNIQUE INDEX ux_rooms_active_code ON rooms(code) WHERE status <> 'CLOSED';