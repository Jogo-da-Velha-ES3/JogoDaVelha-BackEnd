ALTER TABLE games
    ADD COLUMN end_reason VARCHAR(16) NOT NULL DEFAULT 'NORMAL',
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN finished_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_games_end_reason CHECK (end_reason IN ('NORMAL', 'FORFEIT_TIMEOUT'));

-- As partidas antigas não têm datas registradas; esses campos permanecem NULL.
