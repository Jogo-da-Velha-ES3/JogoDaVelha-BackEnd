-- V18: adiciona personagens selecionados por jogador na partida
ALTER TABLE games
    ADD COLUMN player1_character_id UUID NULL REFERENCES characters(id),
    ADD COLUMN player2_character_id UUID NULL REFERENCES characters(id);

-- Índices para consultas de personagem favorito
CREATE INDEX ix_games_player1_character_id ON games(player1_character_id);
CREATE INDEX ix_games_player2_character_id ON games(player2_character_id);
