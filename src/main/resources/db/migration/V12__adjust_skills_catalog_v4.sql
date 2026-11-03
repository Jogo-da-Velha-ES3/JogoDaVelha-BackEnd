-- V12: ajustes do catálogo de habilidades conforme GDD v4

ALTER TABLE skills
    ADD COLUMN offensive BOOLEAN NOT NULL DEFAULT FALSE;

-- Habilidades iniciais
UPDATE skills
SET initial_skill = false;

UPDATE skills
SET initial_skill = true
WHERE name IN (
               'BOMBA',
               'ESCUDO',
               'AMPULHETA'
    );

-- Renomeia Espelho
UPDATE skills
SET
    name = 'CAMPO DE FORCA',
    description = 'Anula a próxima habilidade ofensiva usada contra você durante os próximos 2 turnos.'
WHERE name = 'ESPELHO';

-- Ajusta descrições para turnos
UPDATE skills
SET description = 'Bloqueia uma casa vazia do tabuleiro, impedindo que o oponente ocupe esse espaço por 2 turnos.'
WHERE name = 'ESCUDO';

UPDATE skills
SET description = 'Congela uma peça do oponente por 2 turnos. A peça permanece no tabuleiro, mas é ignorada no cálculo de vitória.'
WHERE name = 'CONGELAR';

UPDATE skills
SET description = 'Protege uma peça própria contra remoção ou movimentação por 2 turnos.'
WHERE name = 'REFORCO';

-- Silêncio passa a ser ofensiva
UPDATE skills
SET effect_category = 'OFFENSE'
WHERE name = 'SILENCIO';

-- Marca habilidades ofensivas
UPDATE skills
SET offensive = true
WHERE name IN (
               'BOMBA',
               'TORNADO',
               'BURACO NEGRO',
               'CONGELAR',
               'AMPULHETA',
               'SILENCIO'
    );