begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(31);

select has_table('private', 'account_provisioning_requests', 'provisioning state exists');
select has_table('private', 'account_audit_events', 'account audit exists');
select has_function(
    'public', 'account_provision_start',
    array['uuid', 'text', 'uuid', 'text', 'text', 'uuid'],
    'provisioning reservation RPC exists'
);
select has_function(
    'public', 'account_provision_finalize', array['uuid', 'text'],
    'provisioning finalization RPC exists'
);
select has_function(
    'public', 'account_provision_attach_auth', array['uuid', 'uuid'],
    'Auth subject attachment RPC exists'
);
select has_function(
    'public', 'account_provision_fail', array['uuid', 'text'],
    'provisioning compensation RPC exists'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.account_provision_start(uuid,text,uuid,text,text,uuid)',
        'execute'
    ),
    'service role can reserve provisioning'
);
select ok(
    has_function_privilege(
        'service_role',
        'public.account_provision_attach_auth(uuid,uuid)',
        'execute'
    ),
    'service role can attach an Auth-generated subject'
);
select ok(
    not has_function_privilege(
        'anon',
        'public.account_provision_start(uuid,text,uuid,text,text,uuid)',
        'execute'
    ),
    'anonymous clients cannot reserve provisioning'
);
select ok(
    not has_function_privilege(
        'authenticated',
        'public.account_provision_start(uuid,text,uuid,text,text,uuid)',
        'execute'
    ),
    'authenticated clients cannot call the service RPC directly'
);
select ok(
    not has_table_privilege('authenticated', 'private.account_audit_events', 'select'),
    'authenticated clients cannot read account audit internals'
);

insert into public.shops (id, slug, display_name) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', 'shop-a', 'Shop A'),
    ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', 'shop-b', 'Shop B');

insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token
) values
    ('00000000-0000-0000-0000-000000000000', '10000000-0000-4000-8000-000000000001',
     'authenticated', 'authenticated', 'super@auth.gdad.invalid', '', now(),
     '{"managed_by":"gdad_pin_v1"}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '20000000-0000-4000-8000-000000000002',
     'authenticated', 'authenticated', 'owner@auth.gdad.invalid', '', now(),
     '{"managed_by":"gdad_pin_v1"}', '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '30000000-0000-4000-8000-000000000003',
     'authenticated', 'authenticated', 'sales@auth.gdad.invalid', '', now(),
     '{"managed_by":"gdad_pin_v1"}', '{}', now(), now(), '', '', '', '');

insert into public.user_profiles (user_id, login_id, display_name, platform_role) values
    ('10000000-0000-4000-8000-000000000001', 'admin.fixture', 'Admin Fixture', 'super_admin'),
    ('20000000-0000-4000-8000-000000000002', 'owner.fixture', 'Owner Fixture', 'standard'),
    ('30000000-0000-4000-8000-000000000003', 'sales.fixture', 'Sales Fixture', 'standard');

insert into public.shop_memberships (shop_id, user_id, role) values
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '20000000-0000-4000-8000-000000000002', 'owner'),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', '30000000-0000-4000-8000-000000000003', 'salesman');

set local role service_role;

select throws_ok(
    $$select * from public.account_provision_start(
        '30000000-0000-4000-8000-000000000003', 'create_owner',
        '10000000-0000-4000-8000-000000000001', 'owner.collision', 'Collision Owner',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$,
    '23505', 'Auth subject unavailable',
    'a new request cannot target an existing Auth subject'
);

create temporary table owner_reservation as
select * from public.account_provision_start(
    '40000000-0000-4000-8000-000000000004', 'create_owner',
    '10000000-0000-4000-8000-000000000001', 'owner.created', 'Owner Created',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
);

select is(
    (select reservation_status from owner_reservation), 'reserved',
    'Super Admin may reserve an Owner in an active shop'
);
select ok(
    (select auth_user_id from owner_reservation) is null,
    'new reservation waits for the hosted Auth-generated subject'
);
select is(
    (select reservation_status from public.account_provision_start(
        '40000000-0000-4000-8000-000000000004', 'create_owner',
        '10000000-0000-4000-8000-000000000001', 'owner.created', 'Owner Created',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    )),
    'reserved',
    'same idempotency request returns the same reservation'
);

select throws_ok(
    $$select * from public.account_provision_start(
        '60000000-0000-4000-8000-000000000006', 'create_owner',
        '20000000-0000-4000-8000-000000000002', 'owner.denied', 'Denied Owner',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$,
    '42501', 'provisioning denied',
    'Owner cannot create another Owner'
);
select throws_ok(
    $$select * from public.account_provision_start(
        '70000000-0000-4000-8000-000000000007', 'create_salesman',
        '20000000-0000-4000-8000-000000000002', 'sales.cross', 'Cross Shop Sales',
        'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb')$$,
    '42501', 'provisioning denied',
    'Owner cannot create a Salesman in another shop'
);
select throws_ok(
    $$select * from public.account_provision_start(
        '80000000-0000-4000-8000-000000000008', 'create_salesman',
        '30000000-0000-4000-8000-000000000003', 'sales.denied', 'Denied Sales',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa')$$,
    '42501', 'provisioning denied',
    'Salesman cannot provision users'
);

create temporary table salesman_reservation as
select * from public.account_provision_start(
    '50000000-0000-4000-8000-000000000005', 'create_salesman',
    '20000000-0000-4000-8000-000000000002', 'sales.created', 'Sales Created',
    'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
);
select is(
    (select reservation_status from salesman_reservation), 'reserved',
    'Owner may reserve a Salesman in the authoritative Owner shop'
);

reset role;
insert into auth.users (
    instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
    raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
    confirmation_token, email_change, email_change_token_new, recovery_token
) values
    ('00000000-0000-0000-0000-000000000000', '41000000-0000-4000-8000-000000000041',
     'authenticated', 'authenticated',
     'acct.40000000000040008000000000000004@auth.gdad.invalid', '', now(),
     '{"managed_by":"gdad_pin_v1","provisioning_request_id":"40000000-0000-4000-8000-000000000004"}',
     '{}', now(), now(), '', '', '', ''),
    ('00000000-0000-0000-0000-000000000000', '51000000-0000-4000-8000-000000000051',
     'authenticated', 'authenticated',
     'acct.50000000000040008000000000000005@auth.gdad.invalid', '', now(),
     '{"managed_by":"gdad_pin_v1","provisioning_request_id":"50000000-0000-4000-8000-000000000005"}',
     '{}', now(), now(), '', '', '', '');
set local role service_role;

select is(
    public.account_provision_attach_auth(
        '40000000-0000-4000-8000-000000000004',
        '41000000-0000-4000-8000-000000000041'
    ),
    '41000000-0000-4000-8000-000000000041'::uuid,
    'reservation accepts the exact marked Auth-generated subject'
);
select public.account_provision_attach_auth(
    '50000000-0000-4000-8000-000000000005',
    '51000000-0000-4000-8000-000000000051'
);
select is(
    (select auth_user_id from public.account_provision_start(
        '40000000-0000-4000-8000-000000000004', 'create_owner',
        '10000000-0000-4000-8000-000000000001', 'owner.created', 'Owner Created',
        'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    )),
    '41000000-0000-4000-8000-000000000041'::uuid,
    'repeated reservation reconciles the same Auth-generated subject'
);

create temporary table owner_finalization as
select * from public.account_provision_finalize(
    '40000000-0000-4000-8000-000000000004',
    '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw'
);
reset role;
select is(
    (select platform_role::text from owner_finalization), 'standard',
    'created Owner receives standard platform role'
);
select is(
    (select shop_role::text from owner_finalization), 'owner',
    'created Owner receives Owner shop role'
);
select ok(
    exists (select 1 from public.user_profiles where login_id = 'owner.created'),
    'finalization creates the profile'
);

-- Direct public/private-schema inspection is test-administrator-only. Production
-- service_role callers must use the security-definer provisioning RPCs.
select ok(
    exists (
        select 1 from private.login_credentials
        where user_id = '41000000-0000-4000-8000-000000000041'
          and pepper_version = 1
    ),
    'finalization stores only the versioned PIN verifier'
);
select is(
    (select count(*)::integer from private.account_audit_events
     where request_id = '40000000-0000-4000-8000-000000000004'),
    1,
    'finalization writes one immutable audit event'
);

set local role service_role;
select is(
    (select auth_user_id from public.account_provision_finalize(
        '40000000-0000-4000-8000-000000000004',
        '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw'
    )),
    '41000000-0000-4000-8000-000000000041'::uuid,
    'repeated finalization returns the same user'
);

reset role;
select is(
    (select count(*)::integer from private.account_audit_events
     where request_id = '40000000-0000-4000-8000-000000000004'),
    1,
    'repeated finalization does not duplicate audit events'
);

set local role service_role;
create temporary table salesman_finalization as
select * from public.account_provision_finalize(
    '50000000-0000-4000-8000-000000000005',
    '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw'
);
reset role;
select is(
    (select shop_role::text from salesman_finalization), 'salesman',
    'created Salesman receives Salesman shop role'
);
select ok(
    exists (
        select 1 from public.shop_memberships
        where user_id = '51000000-0000-4000-8000-000000000051'
          and shop_id = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
          and role = 'salesman' and active
    ),
    'Salesman membership is active in the Owner shop'
);

select is(
    (select safe_metadata from private.account_audit_events
     where request_id = '50000000-0000-4000-8000-000000000005'),
    '{}'::jsonb,
    'audit metadata contains no PIN, verifier, token, or secret'
);

select * from finish();
rollback;
