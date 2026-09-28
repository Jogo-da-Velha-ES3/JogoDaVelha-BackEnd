-- V1: tabela base de usuários.
CREATE TABLE users (
                       id             UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
                       username       VARCHAR(30)  NOT NULL,
                       email          VARCHAR(255) NOT NULL,
                       password_hash  VARCHAR(255) NOT NULL,
                       coins_balance  INTEGER      NOT NULL DEFAULT 0,
                       created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                       CONSTRAINT uk_users_username UNIQUE (username),
                       CONSTRAINT uk_users_email UNIQUE (email),
                       CONSTRAINT ck_users_coins_balance_non_negative CHECK (coins_balance >= 0)
);

