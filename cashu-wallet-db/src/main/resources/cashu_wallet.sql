create sequence t_quote_request_id_seq
    as integer;

alter sequence t_quote_request_id_seq owner to postgres;

create sequence t_quote_response_id_seq
    as integer;

alter sequence t_quote_response_id_seq owner to postgres;

create table t_proof
(
    id        serial
        constraint t_proof_pk
            primary key,
    amount    integer not null,
    secret    varchar not null,
    signature varchar not null
        constraint t_proof_pk_2
            unique,
    keyset_id varchar not null
);

alter table t_proof
    owner to postgres;

create index t_proof_secret_index
    on t_proof (secret);

create index t_proof_signature_index
    on t_proof (signature);

create index t_proof_keyset_id_index
    on t_proof (keyset_id);

create table t_mint_quote_request
(
    id             integer default nextval('t_quote_request_id_seq'::regclass) not null
        constraint t_quote_request_pk
            primary key,
    amount         integer,
    unit           varchar,
    correlation_id uuid
        constraint t_quote_request_pk_2
            unique,
    payment_method varchar                                                     not null
);

alter table t_mint_quote_request
    owner to postgres;

alter sequence t_quote_request_id_seq owned by t_mint_quote_request.id;

create table t_mint_quote_response
(
    id             integer default nextval('t_quote_response_id_seq'::regclass) not null
        constraint t_mint_quote_response_pk
            primary key,
    quote          varchar                                                      not null,
    request        varchar                                                      not null,
    correlation_id uuid                                                         not null
        constraint t_quote_response_pk
            unique
);

alter table t_mint_quote_response
    owner to postgres;

alter sequence t_quote_response_id_seq owned by t_mint_quote_response.id;

create index t_quote_response_correlation_id_index
    on t_mint_quote_response (correlation_id);

create index t_quote_response_quote_index
    on t_mint_quote_response (quote);

create table t_mint_request
(
    id              serial
        constraint t_mint_request_pk
            primary key,
    secret          varchar not null,
    blinding_factor varchar not null,
    correlation_id  uuid    not null,
    amount          integer not null,
    keyset_id       varchar not null,
    blind_message   varchar not null
);

alter table t_mint_request
    owner to postgres;

create index t_mint_request_correlation_id_index
    on t_mint_request (correlation_id);

create index t_mint_request_blind_message_index
    on t_mint_request (blind_message);

create unique index t_mint_request_correlation_id_blind_message_uindex
    on t_mint_request (correlation_id, blind_message);

create table t_melt_quote_request
(
    id             serial
        constraint t_melt_quote_request_pk
            primary key,
    request        varchar not null,
    correlation_id uuid    not null,
    unit           varchar not null,
    payment_method varchar not null
);

alter table t_melt_quote_request
    owner to postgres;

create index t_melt_quote_request_correlation_id_index
    on t_melt_quote_request (correlation_id);

create table t_melt_quote_response
(
    id             serial
        constraint t_melt_quote_response_pk
            primary key,
    amount         integer not null,
    correlation_id uuid    not null,
    fee_reserve    integer not null,
    quote          varchar not null
);

alter table t_melt_quote_response
    owner to postgres;

create index t_melt_quote_response_correlation_id_index
    on t_melt_quote_response (correlation_id);

create unique index t_melt_quote_response_quote_uindex
    on t_melt_quote_response (quote);

