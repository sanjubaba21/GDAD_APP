begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(16);

select has_table('private', 'pin_login_rate_limits', 'source rate-limit state exists');
select has_column('private', 'login_credentials', 'last_success_at', 'successful login time is recorded');
select has_function(
    'public',
    'pin_login_prepare',
    array['text', 'text', 'timestamp with time zone'],
    'login preparation RPC exists'
);
select has_function(
    'public',
    'pin_login_complete',
    array['uuid', 'boolean', 'timestamp with time zone'],
    'login completion RPC exists'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.pin_login_prepare(text,text,timestamptz)',
        'execute'
    ),
    'service_role can execute login preparation'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.pin_login_complete(uuid,boolean,timestamptz)',
        'execute'
    ),
    'service_role can execute login completion'
);
select ok(
    not has_function_privilege('anon', 'public.pin_login_prepare(text,text,timestamptz)', 'execute'),
    'anonymous callers cannot load PIN verifiers'
);
select ok(
    not has_function_privilege('authenticated', 'public.pin_login_prepare(text,text,timestamptz)', 'execute'),
    'authenticated callers cannot load PIN verifiers'
);
select ok(
    not has_table_privilege('authenticated', 'private.pin_login_rate_limits', 'select'),
    'authenticated callers cannot read source counters'
);

insert into auth.users (
    instance_id,
    id,
    aud,
    role,
    email,
    encrypted_password,
    email_confirmed_at,
    raw_app_meta_data,
    raw_user_meta_data,
    created_at,
    updated_at,
    confirmation_token,
    email_change,
    email_change_token_new,
    recovery_token
) values (
    '00000000-0000-0000-0000-000000000000',
    '11111111-1111-4111-8111-111111111111',
    'authenticated',
    'authenticated',
    'lockout-fixture@auth.gdad.invalid',
    '',
    now(),
    '{"managed_by":"gdad_pin_v1"}'::jsonb,
    '{}'::jsonb,
    now(),
    now(),
    '',
    '',
    '',
    ''
);

insert into private.login_credentials (user_id, pin_hash)
values (
    '11111111-1111-4111-8111-111111111111',
    '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw'
);

set local role service_role;

select is(
    (select count(*)::integer from public.pin_login_prepare(
        'missing.user',
        repeat('a', 64),
        '2026-07-15 10:00:00+00'::timestamptz
    )),
    1,
    'unknown login returns one timing-safe preparation row'
);

select ok(
    not (select source_limited from public.pin_login_prepare(
        'missing.user',
        repeat('b', 64),
        '2026-07-15 10:00:00+00'::timestamptz
    )),
    'first source attempt is allowed'
);

do $$
begin
    for attempt_number in 1..20 loop
        perform * from public.pin_login_prepare(
            'missing.user',
            repeat('c', 64),
            '2026-07-15 10:00:00+00'::timestamptz
        );
    end loop;
end;
$$;

select ok(
    (select source_limited from public.pin_login_prepare(
        'missing.user',
        repeat('c', 64),
        '2026-07-15 10:00:00+00'::timestamptz
    )),
    'twenty-first source attempt is throttled'
);

do $$
begin
    for attempt_number in 1..4 loop
        perform * from public.pin_login_complete(
            '11111111-1111-4111-8111-111111111111',
            false,
            '2026-07-15 11:00:00+00'::timestamptz
        );
    end loop;
end;
$$;

create temporary table fifth_failure_state as
select * from public.pin_login_complete(
    '11111111-1111-4111-8111-111111111111',
    false,
    '2026-07-15 11:00:00+00'::timestamptz
);

select is(
    (select failed_attempts from fifth_failure_state),
    5,
    'fifth consecutive failure is recorded atomically'
);
select ok(
    (select locked_until from fifth_failure_state) >=
        '2026-07-15 11:15:00+00'::timestamptz,
    'fifth failure locks the account for at least fifteen minutes'
);

create temporary table successful_login_state as
select * from public.pin_login_complete(
    '11111111-1111-4111-8111-111111111111',
    true,
    '2026-07-15 11:16:00+00'::timestamptz
);

select is(
    (select failed_attempts from successful_login_state),
    0,
    'successful verification resets consecutive failures'
);
select is(
    (select locked_until from successful_login_state),
    null::timestamptz,
    'successful verification clears the account lock'
);

reset role;
select * from finish();
rollback;
