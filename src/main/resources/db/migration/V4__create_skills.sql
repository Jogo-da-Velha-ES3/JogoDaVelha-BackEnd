-- V4: tabela de habilidades (Skill) e catálogo de efeitos
CREATE TABLE skills (
                        id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                        name VARCHAR(50) NOT NULL,
                        effect_category VARCHAR(30) NOT NULL,
                        description VARCHAR(500) NOT NULL,
                        initial_skill BOOLEAN,

                        CONSTRAINT uk_skills_name UNIQUE (name)
);
