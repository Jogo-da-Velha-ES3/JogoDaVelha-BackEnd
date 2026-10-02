-- V6: ajuste do catálogo de habilidades

DELETE FROM skills
WHERE name = 'RADAR';

UPDATE skills
SET initial_skill = false;

INSERT INTO skills
(name, effect_category, description, initial_skill)
VALUES (
           'SILENCIO',
           'DEFENSE',
           'Impede o adversário de utilizar habilidades durante o próximo turno dele. Nesse turno, o adversário deverá realizar sua jogada de peça normalmente.',
           false
       );