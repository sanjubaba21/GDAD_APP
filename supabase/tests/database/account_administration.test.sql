begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(27);

select has_table('private', 'account_admin_requests', 'administration idempotency state exists');
select has_table('private', 'account_admin_rate_limits', 'administration rate state exists');
select has_function(
    'public', 'account_admin_prepare',
    array['uuid', 'text', 'uuid', 'uuid', 'text', 'timestamptz'],
    'administration preparation RPC exists'
);
select has_function(
    'public', 'account_admin_apply', array['uuid', 'text'],
    'administration application RPC exists'
);
select has_function(
    'public', 'account_admin_fail', array['uuid', 'text'],
    'administration failure RPC exists'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.account_admin_prepare(uuid,text,uuid,uuid,text,timestamptz)',
        'execute'
    ),
    'service role can prepare administration'
);
select ok(
    not has_function_privilege(
        'authenticated',
        'public.account_admin_apply(uuid,text)',
        'execute'
    ),
    'authenticated clients cannot directly apply administration'
);
select ok(
    not has_table_privilege('authenticated', 'private.account_admin_requests', 'select'),
    'authenticated clients cannot inspect administration state'
);

insert into public.shops (id, slug, display_name) values
    ('a1000000-0000-4000-8000-000000000001', 'admin-shop-a', 'Admin Shop A'),
    ('b1000000-0000-4000-8000-000000000001', 'admin-shop-b', 'Admin Shop B');
insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token
) values
    ('00000000-0000-0000-0000-000000000000', '11000000-0000-4000-8000-000000000001',
     'authenticated', 'authenticated', 'admin@admin.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '21000000-0000-4000-8000-000000000002',
     'authenticated', 'authenticated', 'owner-a@admin.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '22000000-0000-4000-8000-000000000002',
     'authenticated', 'authenticated', 'owner-b@admin.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '31000000-0000-4000-8000-000000000003',
     'authenticated', 'authenticated', 'sales-a@admin.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '32000000-0000-4000-8000-000000000003',
     'authenticated', 'authenticated', 'sales-b@admin.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');
insert into public.user_profiles (user_id, login_id, display_name, platform_role) values
    ('11000000-0000-4000-8000-000000000001', 'admin.manage', 'Admin', 'super_admin'),
    ('21000000-0000-4000-8000-000000000002', 'owner.manage.a', 'Owner A', 'standard'),
    ('22000000-0000-4000-8000-000000000002', 'owner.manage.b', 'Owner B', 'standard'),
    ('31000000-0000-4000-8000-000000000003', 'sales.manage.a', 'Sales A', 'standard'),
    ('32000000-0000-4000-8000-000000000003', 'sales.manage.b', 'Sales B', 'standard');
insert into public.shop_memberships (shop_id, user_id, role) values
    ('a1000000-0000-4000-8000-000000000001', '21000000-0000-4000-8000-000000000002', 'owner'),
    ('b1000000-0000-4000-8000-000000000001', '22000000-0000-4000-8000-000000000002', 'owner'),
    ('a1000000-0000-4000-8000-000000000001', '31000000-0000-4000-8000-000000000003', 'salesman'),
    ('b1000000-0000-4000-8000-000000000001', '32000000-0000-4000-8000-000000000003', 'salesman');
insert into private.login_credentials (user_id, pin_hash, pepper_version) select
    user_id, '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw', 1
from public.user_profiles where login_id like '%.manage%' or login_id = 'admin.manage';

set local role service_role;
create temporary table disable_preparation as
select * from public.account_admin_prepare(
    '41000000-0000-4000-8000-000000000004', 'disable_user',
    '11000000-0000-4000-8000-000000000001',
    '21000000-0000-4000-8000-000000000002', repeat('a', 64), now()
);
select is((select reservation_status from disable_preparation), 'reserved',
    'Super Admin may prepare Owner disable');
select matches((select actor_pin_hash from disable_preparation), '^\$argon2id\$',
    'prepare returns only the actor verifier to the service operation');

select throws_ok(
    $$select * from public.account_admin_prepare(
      '42000000-0000-4000-8000-000000000004', 'disable_user',
      '21000000-0000-4000-8000-000000000002',
      '22000000-0000-4000-8000-000000000002', repeat('b', 64), now())$$,
    '42501', 'administration denied', 'Owner cannot manage another Owner'
);
select throws_ok(
    $$select * from public.account_admin_prepare(
      '43000000-0000-4000-8000-000000000004', 'disable_user',
      '21000000-0000-4000-8000-000000000002',
      '32000000-0000-4000-8000-000000000003', repeat('c', 64), now())$$,
    '42501', 'administration denied', 'Owner cannot manage another shop'
);
select throws_ok(
    $$select * from public.account_admin_prepare(
      '44000000-0000-4000-8000-000000000004', 'disable_user',
      '31000000-0000-4000-8000-000000000003',
      '21000000-0000-4000-8000-000000000002', repeat('d', 64), now())$$,
    '42501', 'administration denied', 'Salesman cannot administer accounts'
);
select throws_ok(
    $$select * from public.account_admin_prepare(
      '45000000-0000-4000-8000-000000000004', 'disable_user',
      '21000000-0000-4000-8000-000000000002',
      '11000000-0000-4000-8000-000000000001', repeat('e', 64), now())$$,
    '42501', 'administration denied', 'Super Admin target is protected'
);

create temporary table disable_result as
select * from public.account_admin_apply(
    '41000000-0000-4000-8000-000000000004', null
);
reset role;
select ok((select disabled from disable_result), 'disable result is disabled');
select ok((select disabled from public.user_profiles where login_id = 'owner.manage.a'),
    'disable updates the authoritative profile');

select is((select count(*)::integer from private.account_audit_events
    where request_id = '41000000-0000-4000-8000-000000000004'), 1,
    'disable writes exactly one audit event');
select is((select safe_metadata from private.account_audit_events
    where request_id = '41000000-0000-4000-8000-000000000004'),
    '{"disabled": true}'::jsonb, 'audit metadata is client-safe state only');

set local role service_role;
select is((select action from public.account_admin_apply(
    '41000000-0000-4000-8000-000000000004', null)),
    'disable_user', 'completed application is idempotent');
reset role;
select is((select count(*)::integer from private.account_audit_events
    where request_id = '41000000-0000-4000-8000-000000000004'), 1,
    'idempotent retry does not duplicate audit');

set local role service_role;
select is((select reservation_status from public.account_admin_prepare(
    '41000000-0000-4000-8000-000000000004', 'disable_user',
    '11000000-0000-4000-8000-000000000001',
    '21000000-0000-4000-8000-000000000002', repeat('a', 64), now())),
    'complete', 'completed preparation returns the saved result');

select * from public.account_admin_prepare(
    '47000000-0000-4000-8000-000000000004', 'enable_user',
    '11000000-0000-4000-8000-000000000001',
    '21000000-0000-4000-8000-000000000002', repeat('7', 64), now()
);
select * from public.account_admin_apply(
    '47000000-0000-4000-8000-000000000004', null
);
reset role;
select ok(not (select disabled from public.user_profiles
    where login_id = 'owner.manage.a'),
    'Super Admin may re-enable the Owner before Owner administration');

set local role service_role;
create temporary table sales_preparation as
select * from public.account_admin_prepare(
    '46000000-0000-4000-8000-000000000004', 'reset_pin',
    '21000000-0000-4000-8000-000000000002',
    '31000000-0000-4000-8000-000000000003', repeat('f', 64), now()
);
select is((select reservation_status from sales_preparation), 'reserved',
    'Owner may prepare same-shop Salesman reset');
select * from public.account_admin_apply(
    '46000000-0000-4000-8000-000000000004',
    '$argon2id$v=19$m=19456,t=2,p=1$bmV3c2FsdA$bmV3aGFzaG1hdGVyaWFs'
);
reset role;
select matches((select pin_hash from private.login_credentials
    where user_id = '31000000-0000-4000-8000-000000000003'),
    '\$bmV3c2FsdA\$', 'PIN reset rotates the verifier');
select ok((select failed_attempts = 0 and locked_until is null
    from private.login_credentials
    where user_id = '31000000-0000-4000-8000-000000000003'),
    'PIN reset clears failure and lock state');
select matches(
    pg_get_functiondef('public.account_admin_apply(uuid,text)'::regprocedure),
    'delete from auth\.sessions', 'application revokes target refresh sessions'
);
select ok(private.account_admin_consume_rate(
    'test-limit', now(), 1) = false and
    private.account_admin_consume_rate('test-limit', now(), 1) = true,
    'administration rate limit is atomic and bounded'
);

select * from finish();
rollback;
