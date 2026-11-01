CREATE TABLE avatars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500) NOT NULL,
    price_coins INTEGER NOT NULL CHECK (price_coins >= 0)
);

INSERT INTO avatars (code, name, description, price_coins)
VALUES ('DEFAULT', 'Avatar padrão', 'Identidade visual padrão do perfil.', 0);

CREATE TABLE user_avatars (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id),
    avatar_id UUID NOT NULL REFERENCES avatars(id),
    CONSTRAINT uk_user_avatar UNIQUE (user_id, avatar_id)
);

CREATE TABLE user_equipment (
    user_id UUID PRIMARY KEY REFERENCES users(id),
    character_id UUID,
    avatar_id UUID,
    FOREIGN KEY (user_id, character_id) REFERENCES user_characters(user_id, character_id),
    FOREIGN KEY (user_id, avatar_id) REFERENCES user_avatars(user_id, avatar_id)
);
