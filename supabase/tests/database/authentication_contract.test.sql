begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(7);

select has_column(
    'private',
    'login_credentials',
    'pepper_version',
    'login credentials record the external pepper version'
);
select col_type_is(
    'private',
    'login_credentials',
    'pepper_version',
    'smallint',
    'pepper version uses a bounded integer type'
);
select col_not_null(
    'private',
    'login_credentials',
    'pepper_version',
    'pepper version is required'
);
select ok(
    (
        select column_default = '1'
        from information_schema.columns
        where table_schema = 'private'
          and table_name = 'login_credentials'
          and column_name = 'pepper_version'
    ),
    'new credential rows default to pepper version 1'
);
select ok(
    exists (
        select 1
        from pg_constraint
        where conrelid = 'private.login_credentials'::regclass
          and conname = 'login_credentials_pepper_version_positive'
          and contype = 'c'
    ),
    'pepper versions must be positive'
);
select ok(
    exists (
        select 1
        from pg_constraint
        where conrelid = 'private.login_credentials'::regclass
          and conname = 'login_credentials_pin_hash_argon2id_phc'
          and contype = 'c'
    ),
    'PIN verifiers must use the Argon2id PHC format'
);
select ok(
    not has_table_privilege('authenticated', 'private.login_credentials', 'select'),
    'authenticated clients still cannot read PIN verifier state'
);

select * from finish();
rollback;
