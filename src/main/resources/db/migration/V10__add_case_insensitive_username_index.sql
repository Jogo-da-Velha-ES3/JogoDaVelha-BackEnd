-- V10: Adiciona índice case-insensitive para username
-- Remove a constraint UNIQUE anterior e adiciona índice em lower(username)
-- Isso garante que "Victor" e "victor" sejam considerados duplicados

-- Remove a constraint UNIQUE anterior em username
ALTER TABLE users DROP CONSTRAINT IF EXISTS uk_users_username;

-- Adiciona índice único case-insensitive em lower(username)
CREATE UNIQUE INDEX uk_users_username_lower ON users (lower(username));