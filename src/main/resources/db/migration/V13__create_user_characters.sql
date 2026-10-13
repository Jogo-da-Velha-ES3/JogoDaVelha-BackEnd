CREATE TABLE user_characters
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id      UUID NOT NULL,
    character_id UUID NOT NULL,

    CONSTRAINT uk_user_character
        UNIQUE (user_id, character_id),

    CONSTRAINT fk_user_characters_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),

    CONSTRAINT fk_user_characters_character
        FOREIGN KEY (character_id)
            REFERENCES characters (id)
);