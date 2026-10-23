-- V16: cria extrato de moedas (coin_transactions)
create table coin_transactions
(
    id            uuid primary key,

    user_id       uuid                     not null,

    type          varchar(16)              not null,

    amount        integer                  not null,

    balance_after integer                  not null,

    source        varchar(64)              not null,

    created_at    timestamp with time zone not null,

    constraint fk_coin_transactions_user
        foreign key (user_id)
            references users (id)
);