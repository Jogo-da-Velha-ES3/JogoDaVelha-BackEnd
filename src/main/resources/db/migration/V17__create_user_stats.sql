-- V17: cria estatísticas agregadas dos usuários (user_stats)
CREATE TABLE user_stats
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id            UUID    NOT NULL UNIQUE,

    matches_played     INTEGER NOT NULL DEFAULT 0,
    wins               INTEGER NOT NULL DEFAULT 0,

    current_win_streak INTEGER NOT NULL DEFAULT 0,
    longest_win_streak INTEGER NOT NULL DEFAULT 0,

    CONSTRAINT fk_user_stats_user
        FOREIGN KEY (user_id)
            REFERENCES users (id)
);