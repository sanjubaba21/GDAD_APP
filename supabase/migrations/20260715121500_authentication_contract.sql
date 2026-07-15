begin;

alter table private.login_credentials
    add column pepper_version smallint not null default 1,
    add constraint login_credentials_pepper_version_positive
        check (pepper_version > 0),
    add constraint login_credentials_pin_hash_argon2id_phc
        check (pin_hash ~ '^\$argon2id\$v=19\$');

comment on column private.login_credentials.pin_hash is
    'Argon2id PHC verifier of versioned HMAC-peppered PIN material; never plaintext.';
comment on column private.login_credentials.pepper_version is
    'Selects the Edge Function secret version; the pepper itself never enters Postgres.';

commit;
