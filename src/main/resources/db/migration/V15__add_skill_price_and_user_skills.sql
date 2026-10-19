-- V15: adiciona preço às habilidades e posse de habilidades por usuário
ALTER TABLE skills
    ADD COLUMN price_coins INTEGER NOT NULL DEFAULT 0;

ALTER TABLE skills
    ADD COLUMN price_coins INTEGER NOT NULL DEFAULT 0;

UPDATE skills
SET price_coins = 350;

UPDATE skills
SET price_coins = 0
WHERE name IN (
               'BOMBA',
               'ESCUDO',
               'AMPULHETA'
    );

CREATE TABLE user_skills (
                             id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

                             user_id UUID NOT NULL,
                             skill_id UUID NOT NULL,

                             CONSTRAINT fk_user_skills_user
                                 FOREIGN KEY (user_id)
                                     REFERENCES users(id),

                             CONSTRAINT fk_user_skills_skill
                                 FOREIGN KEY (skill_id)
                                     REFERENCES skills(id),

                             CONSTRAINT uk_user_skill
                                 UNIQUE (user_id, skill_id)
);