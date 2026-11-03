-- V9: adiciona preço em moedas aos personagens existentes.

ALTER TABLE characters
    ADD COLUMN price_coins INTEGER NOT NULL DEFAULT 0;

UPDATE characters
SET price_coins = CASE symbol
                      WHEN 'X'         THEN 0
                      WHEN 'O'         THEN 0
                      WHEN 'QUADRADO'  THEN 400
                      WHEN 'TRIANGULO' THEN 500
                      WHEN 'CORACAO'   THEN 600
                      WHEN 'ESTRELA'   THEN 700
END;