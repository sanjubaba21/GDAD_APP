begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(41);

select has_table('private', 'shop_deletion_requests', 'shop deletion recovery state exists');
select has_table('private', 'shop_deletion_audit_events', 'shop deletion audit exists');
select has_function(
  'public', 'shop_delete_prepare',
  array['uuid', 'uuid', 'uuid', 'text', 'text', 'text', 'timestamptz'],
  'shop deletion preparation RPC exists'
);
select has_function('public', 'shop_delete_apply', array['uuid'],
  'shop deletion application RPC exists');
select has_function('public', 'shop_delete_fail', array['uuid', 'text'],
  'shop deletion failure RPC exists');
select has_function('public', 'shop_delete_mark_auth_cleanup', array['uuid'],
  'shop deletion Auth cleanup marker exists');
select ok(
  has_function_privilege(
    'service_role',
    'public.shop_delete_prepare(uuid,uuid,uuid,text,text,text,timestamptz)',
    'execute'
  ),
  'service role may prepare deletion'
);
select ok(
  not has_function_privilege(
    'authenticated', 'public.shop_delete_apply(uuid)', 'execute'
  ),
  'authenticated clients cannot directly apply deletion'
);
select ok(
  not has_table_privilege(
    'authenticated', 'private.shop_deletion_requests', 'select'
  ),
  'authenticated clients cannot inspect deletion recovery state'
);

insert into public.shops (id, slug, display_name) values
  ('a9000000-0000-4000-8000-000000000001', 'delete-shop-a', 'Delete Shop A'),
  ('b9000000-0000-4000-8000-000000000001', 'keep-shop-b', 'Keep Shop B');

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values
  ('00000000-0000-0000-0000-000000000000', '19000000-0000-4000-8000-000000000001',
   'authenticated', 'authenticated', 'admin@delete.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '29000000-0000-4000-8000-000000000002',
   'authenticated', 'authenticated', 'acct.29000000000040008000000000000002@auth.gdad.invalid', '', now(),
   '{"managed_by":"gdad_pin_v1","provisioning_request_id":"29000000-0000-4000-8000-000000000002"}',
   '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '39000000-0000-4000-8000-000000000003',
   'authenticated', 'authenticated', 'shared@delete.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');

insert into public.user_profiles (user_id, login_id, display_name, platform_role) values
  ('19000000-0000-4000-8000-000000000001', 'delete.admin', 'Delete Admin', 'super_admin'),
  ('29000000-0000-4000-8000-000000000002', 'delete.owner', 'Delete Owner', 'standard'),
  ('39000000-0000-4000-8000-000000000003', 'shared.owner', 'Shared Owner', 'standard');

insert into public.shop_memberships (shop_id, user_id, role) values
  ('a9000000-0000-4000-8000-000000000001', '29000000-0000-4000-8000-000000000002', 'owner'),
  ('a9000000-0000-4000-8000-000000000001', '39000000-0000-4000-8000-000000000003', 'salesman'),
  ('b9000000-0000-4000-8000-000000000001', '39000000-0000-4000-8000-000000000003', 'owner');

insert into private.login_credentials (user_id, pin_hash, pepper_version) values
  ('19000000-0000-4000-8000-000000000001', '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw', 1),
  ('29000000-0000-4000-8000-000000000002', '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw', 1),
  ('39000000-0000-4000-8000-000000000003', '$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaG1hdGVyaWFsZm9ydGVzdGluZw', 1);

insert into private.account_provisioning_requests (
  request_id, operation, actor_user_id, login_id, display_name, shop_id,
  auth_user_id, status, completed_at
) values (
  '29000000-0000-4000-8000-000000000002', 'create_owner',
  '19000000-0000-4000-8000-000000000001', 'delete.owner', 'Delete Owner',
  'a9000000-0000-4000-8000-000000000001',
  '29000000-0000-4000-8000-000000000002', 'complete', now()
);

insert into public.products (
  id, shop_id, sku_code, name, default_selling_price_paisa
) values
  ('a9100000-0000-4000-8000-000000000001', 'a9000000-0000-4000-8000-000000000001',
   'DELETE-1', 'Delete Product', 10000),
  ('b9100000-0000-4000-8000-000000000001', 'b9000000-0000-4000-8000-000000000001',
   'KEEP-1', 'Keep Product', 20000);

insert into private.business_audit_events (
  shop_id, actor_user_id, operation, record_type, record_id,
  after_metadata, idempotency_key
) values
  ('a9000000-0000-4000-8000-000000000001', '19000000-0000-4000-8000-000000000001',
   'create', 'product', 'a9100000-0000-4000-8000-000000000001', '{}', 'delete-audit-a'),
  ('b9000000-0000-4000-8000-000000000001', '19000000-0000-4000-8000-000000000001',
   'create', 'product', 'b9100000-0000-4000-8000-000000000001', '{}', 'delete-audit-b');

set local role service_role;
create temporary table deletion_preparation as
select * from public.shop_delete_prepare(
  '49000000-0000-4000-8000-000000000004',
  '19000000-0000-4000-8000-000000000001',
  'a9000000-0000-4000-8000-000000000001',
  'delete-shop-a', 'Controlled launch test cleanup', repeat('a', 64), now()
);
select is((select reservation_status from deletion_preparation), 'reserved',
  'active Super Admin may reserve exact shop deletion');
select matches((select actor_pin_hash from deletion_preparation), '^\$argon2id\$',
  'prepare returns only the actor verifier');
select is((select target_shop_id from deletion_preparation),
  'a9000000-0000-4000-8000-000000000001'::uuid,
  'prepare binds the exact target shop');

select throws_ok(
  $$select * from public.shop_delete_prepare(
    '4a000000-0000-4000-8000-000000000004',
    '29000000-0000-4000-8000-000000000002',
    'a9000000-0000-4000-8000-000000000001',
    'delete-shop-a', 'Owner must not delete shops', repeat('b', 64), now())$$,
  '42501', 'shop deletion denied', 'Owner cannot reserve shop deletion'
);
select throws_ok(
  $$select * from public.shop_delete_prepare(
    '4b000000-0000-4000-8000-000000000004',
    '19000000-0000-4000-8000-000000000001',
    'a9000000-0000-4000-8000-000000000001',
    'wrong-shop', 'Wrong slug must fail closed', repeat('c', 64), now())$$,
  '42501', 'shop deletion denied', 'wrong confirmation slug fails closed'
);
select throws_ok(
  $$select * from public.shop_delete_prepare(
    '49000000-0000-4000-8000-000000000004',
    '19000000-0000-4000-8000-000000000001',
    'b9000000-0000-4000-8000-000000000001',
    'keep-shop-b', 'Changed idempotent payload', repeat('d', 64), now())$$,
  '22023', 'shop deletion request mismatch', 'request ID cannot be rebound'
);

select * from public.shop_delete_prepare(
  '4c000000-0000-4000-8000-000000000004',
  '19000000-0000-4000-8000-000000000001',
  'b9000000-0000-4000-8000-000000000001',
  'keep-shop-b', 'Cancelled destructive request', repeat('e', 64), now()
);
select public.shop_delete_fail(
  '4c000000-0000-4000-8000-000000000004', 'REAUTH_FAILED'
);
reset role;
select is((select status from private.shop_deletion_requests
  where request_id = '4c000000-0000-4000-8000-000000000004'), 'failed',
  'failed reauthentication permanently fails that request ID');

set local role service_role;
select throws_ok(
  $$select * from public.shop_delete_apply('4c000000-0000-4000-8000-000000000004')$$,
  '42501', 'shop deletion denied', 'failed request cannot be applied'
);
create temporary table deletion_result as
select * from public.shop_delete_apply(
  '49000000-0000-4000-8000-000000000004'
);
reset role;

select is((select target_shop_id from deletion_result),
  'a9000000-0000-4000-8000-000000000001'::uuid,
  'apply returns the deleted shop identifier');
select is((select count(*)::integer from public.shops
  where id = 'a9000000-0000-4000-8000-000000000001'), 0,
  'target shop is deleted');
select is((select count(*)::integer from public.shops
  where id = 'b9000000-0000-4000-8000-000000000001'), 1,
  'unrelated shop is preserved');

create or replace function pg_temp.shop_owned_rows(target_shop uuid)
returns bigint language plpgsql set search_path = '' as $$
declare
  relation record;
  relation_count bigint;
  total_count bigint := 0;
begin
  for relation in
    select columns.table_schema, columns.table_name
    from information_schema.columns as columns
    join information_schema.tables as tables
      on tables.table_schema = columns.table_schema
     and tables.table_name = columns.table_name
    where columns.column_name = 'shop_id'
      and columns.table_schema in ('public', 'private')
      and tables.table_type = 'BASE TABLE'
  loop
    execute format(
      'select count(*) from %I.%I where shop_id = $1',
      relation.table_schema, relation.table_name
    ) into relation_count using target_shop;
    total_count := total_count + relation_count;
  end loop;
  return total_count;
end;
$$;

select is(pg_temp.shop_owned_rows('a9000000-0000-4000-8000-000000000001'), 0::bigint,
  'no target-shop row survives in any current tenant-owned table');
select is((select count(*)::integer from public.products
  where shop_id = 'b9000000-0000-4000-8000-000000000001'), 1,
  'unrelated product data is preserved');
select is((select count(*)::integer from private.business_audit_events
  where shop_id = 'b9000000-0000-4000-8000-000000000001'), 1,
  'unrelated immutable business audit is preserved');
select is((select count(*)::integer from public.user_profiles
  where user_id = '29000000-0000-4000-8000-000000000002'), 0,
  'exclusive managed profile is removed');
select is((select count(*)::integer from private.login_credentials
  where user_id = '29000000-0000-4000-8000-000000000002'), 0,
  'exclusive managed PIN verifier is removed');
select is((select count(*)::integer from public.user_profiles
  where user_id = '39000000-0000-4000-8000-000000000003'), 1,
  'identity shared with another shop is preserved');
select is((select count(*)::integer from public.shop_memberships
  where shop_id = 'b9000000-0000-4000-8000-000000000001'
    and user_id = '39000000-0000-4000-8000-000000000003'), 1,
  'shared identity keeps its unrelated membership');
select is((select count(*)::integer from auth.users
  where id = '29000000-0000-4000-8000-000000000002'), 1,
  'managed Auth identity remains only for validated Edge cleanup');
select ok((select status = 'complete' and auth_cleanup_pending
  from private.shop_deletion_requests
  where request_id = '49000000-0000-4000-8000-000000000004'),
  'completed deletion records pending managed Auth cleanup');
select is((select jsonb_array_length(managed_auth_users) from private.shop_deletion_requests
  where request_id = '49000000-0000-4000-8000-000000000004'), 1,
  'cleanup list contains exactly the exclusive managed identity');
select is((select count(*)::integer from private.shop_deletion_audit_events
  where request_id = '49000000-0000-4000-8000-000000000004'), 1,
  'one independent deletion audit survives');
select ok(not ((select safe_summary from private.shop_deletion_audit_events
  where request_id = '49000000-0000-4000-8000-000000000004')::text
  ~* 'pin|hash|token|secret'), 'deletion audit contains no credential-shaped keys');

set local role service_role;
select is((select target_shop_id from public.shop_delete_apply(
  '49000000-0000-4000-8000-000000000004')),
  'a9000000-0000-4000-8000-000000000001'::uuid,
  'completed application is idempotent');
reset role;
select is((select count(*)::integer from private.shop_deletion_audit_events
  where request_id = '49000000-0000-4000-8000-000000000004'), 1,
  'idempotent apply does not duplicate the audit');

set local role service_role;
select is((select reservation_status from public.shop_delete_prepare(
  '49000000-0000-4000-8000-000000000004',
  '19000000-0000-4000-8000-000000000001',
  'a9000000-0000-4000-8000-000000000001',
  'delete-shop-a', 'Controlled launch test cleanup', repeat('a', 64), now())),
  'complete', 'completed preparation returns recovery state');
select public.shop_delete_mark_auth_cleanup(
  '49000000-0000-4000-8000-000000000004'
);
reset role;
select ok(not (select auth_cleanup_pending from private.shop_deletion_requests
  where request_id = '49000000-0000-4000-8000-000000000004'),
  'validated Auth cleanup can be marked complete');

select throws_ok(
  $$update private.shop_deletion_audit_events set reason = 'Tampered deletion reason'$$,
  '55000', 'shop deletion audit is immutable', 'deletion audit cannot be updated'
);
select throws_ok(
  $$delete from private.shop_deletion_audit_events$$,
  '55000', 'shop deletion audit is immutable', 'deletion audit cannot be deleted'
);
select throws_ok(
  $$delete from private.business_audit_events
    where shop_id = 'b9000000-0000-4000-8000-000000000001'$$,
  '55000', 'business audit is immutable',
  'business audit remains immutable outside the marked deletion transaction'
);
select ok(
  not has_function_privilege(
    'authenticated',
    'public.shop_delete_prepare(uuid,uuid,uuid,text,text,text,timestamptz)',
    'execute'
  ),
  'authenticated clients cannot directly prepare deletion'
);
select ok(
  not has_table_privilege(
    'authenticated', 'private.shop_deletion_audit_events', 'delete'
  ),
  'authenticated clients cannot delete deletion audits'
);

select * from finish();
rollback;
