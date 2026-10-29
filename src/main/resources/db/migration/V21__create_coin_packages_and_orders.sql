-- V21: cria catálogo de pacotes de moedas e pedidos de compra
CREATE TABLE coin_packages
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    coins           INTEGER NOT NULL,
    price_in_reais  NUMERIC(10,2) NOT NULL
);

CREATE TABLE coin_orders
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID        NOT NULL,
    package_id   UUID        NOT NULL,
    payment_mode VARCHAR(32) NOT NULL,

    CONSTRAINT fk_coin_orders_user
        FOREIGN KEY (user_id)
            REFERENCES users (id),

    CONSTRAINT fk_coin_orders_package
        FOREIGN KEY (package_id)
            REFERENCES coin_packages (id)
);

INSERT INTO coin_packages (
    coins,
    price_in_reais
)
VALUES
    (100, 4.99),
    (500, 19.99),
    (1000, 34.99);
