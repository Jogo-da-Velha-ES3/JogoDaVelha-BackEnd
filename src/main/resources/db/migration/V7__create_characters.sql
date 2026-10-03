-- V7: tabela de personagens cosméticos

CREATE TABLE characters (
                            id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                            name VARCHAR(50) NOT NULL,
                            symbol VARCHAR(20) NOT NULL,

                            CONSTRAINT uk_characters_name UNIQUE (name),
                            CONSTRAINT uk_characters_symbol UNIQUE (symbol)
);