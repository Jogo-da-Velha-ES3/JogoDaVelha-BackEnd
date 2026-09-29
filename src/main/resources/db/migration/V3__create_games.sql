-- V3: tabela de partidas (Game) com suporte a rounds, morte súbita e recompensas
CREATE TABLE games (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id             UUID        NOT NULL REFERENCES rooms(id),
    player1_id          UUID        NOT NULL REFERENCES users(id),
    player2_id          UUID        NULL     REFERENCES users(id),
    board               VARCHAR(16) NOT NULL DEFAULT '----------------',
    current_round       INT         NOT NULL DEFAULT 1,
    victories_player1   INT         NOT NULL DEFAULT 0,
    victories_player2   INT         NOT NULL DEFAULT 0,
    sudden_death        BOOLEAN     NOT NULL DEFAULT FALSE,
    status              VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS',
    winner_id           UUID        NULL     REFERENCES users(id),
    rewards_granted     BOOLEAN     NOT NULL DEFAULT FALSE,

    CONSTRAINT ck_games_current_round CHECK (current_round >= 1 AND current_round <= 3),
    CONSTRAINT ck_games_victories_non_negative CHECK (victories_player1 >= 0 AND victories_player2 >= 0),
    CONSTRAINT ck_games_status CHECK (status IN ('IN_PROGRESS', 'FINISHED')),
    CONSTRAINT ck_games_sudden_death_round CHECK (NOT sudden_death OR current_round = 3),
    CONSTRAINT ck_games_players_distinct CHECK (player2_id IS NULL OR player1_id <> player2_id)
);

-- Índices de FK e consultas frequentes
CREATE INDEX ix_games_room_id ON games(room_id);
CREATE INDEX ix_games_player1_id ON games(player1_id);
CREATE INDEX ix_games_player2_id ON games(player2_id);
CREATE INDEX ix_games_winner_id ON games(winner_id);
CREATE INDEX ix_games_status ON games(status);
