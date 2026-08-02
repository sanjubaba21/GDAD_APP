begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(42);

select ok(
    (select bool_and(not has_table_privilege('anon', table_name, 'select'))
     from unnest(array[
       'public.shops', 'public.user_profiles', 'public.shop_memberships',
       'public.products', 'public.inventory_lots', 'public.inventory_movements'
     ]) table_name),
    'anonymous users cannot select from any public application table'
);
select ok(
    (select bool_and(has_table_privilege('authenticated', table_name, 'select'))
     from unnest(array[
       'public.shops', 'public.user_profiles', 'public.shop_memberships',
       'public.products', 'public.inventory_lots', 'public.inventory_movements'
     ]) table_name),
    'authenticated receives SELECT only so RLS can filter public application tables'
);
select ok(
    (select bool_and(
       not has_table_privilege('authenticated', table_name, 'insert')
       and not has_table_privilege('authenticated', table_name, 'update')
       and not has_table_privilege('authenticated', table_name, 'delete')
     ) from unnest(array[
       'public.shops', 'public.user_profiles', 'public.shop_memberships',
       'public.products', 'public.inventory_lots', 'public.inventory_movements'
     ]) table_name),
    'authenticated cannot directly mutate any public application table'
);
select ok(
    (select bool_and(not has_table_privilege('authenticated', table_name, 'select'))
     from unnest(array[
       'private.login_credentials', 'private.pin_login_rate_limits',
       'private.account_provisioning_requests', 'private.account_audit_events',
       'private.account_admin_requests', 'private.account_admin_rate_limits'
     ]) table_name),
    'authenticated cannot read any private authentication or account-operation state'
);

select ok(
    (select bool_and(not has_function_privilege('anon', rpc, 'execute'))
     from unnest(array[
       'public.pin_login_prepare(text,text,timestamptz)'::regprocedure,
       'public.pin_login_complete(uuid,boolean,timestamptz)'::regprocedure,
       'public.account_provision_start(uuid,text,uuid,text,text,uuid)'::regprocedure,
       'public.account_provision_attach_auth(uuid,uuid)'::regprocedure,
       'public.account_provision_finalize(uuid,text)'::regprocedure,
       'public.account_provision_fail(uuid,text)'::regprocedure,
       'public.account_admin_prepare(uuid,text,uuid,uuid,text,timestamptz)'::regprocedure,
       'public.account_admin_apply(uuid,text)'::regprocedure,
       'public.account_admin_fail(uuid,text)'::regprocedure
     ]) rpc),
    'anonymous clients cannot execute service-only RPCs'
);
select ok(
    (select bool_and(not has_function_privilege('authenticated', rpc, 'execute'))
     from unnest(array[
       'public.pin_login_prepare(text,text,timestamptz)'::regprocedure,
       'public.pin_login_complete(uuid,boolean,timestamptz)'::regprocedure,
       'public.account_provision_start(uuid,text,uuid,text,text,uuid)'::regprocedure,
       'public.account_provision_attach_auth(uuid,uuid)'::regprocedure,
       'public.account_provision_finalize(uuid,text)'::regprocedure,
       'public.account_provision_fail(uuid,text)'::regprocedure,
       'public.account_admin_prepare(uuid,text,uuid,uuid,text,timestamptz)'::regprocedure,
       'public.account_admin_apply(uuid,text)'::regprocedure,
       'public.account_admin_fail(uuid,text)'::regprocedure
     ]) rpc),
    'authenticated clients cannot bypass Edge authorization by calling service RPCs'
);
select ok(
    (select bool_and(has_function_privilege('service_role', rpc, 'execute'))
     from unnest(array[
       'public.pin_login_prepare(text,text,timestamptz)'::regprocedure,
       'public.pin_login_complete(uuid,boolean,timestamptz)'::regprocedure,
       'public.account_provision_start(uuid,text,uuid,text,text,uuid)'::regprocedure,
       'public.account_provision_attach_auth(uuid,uuid)'::regprocedure,
       'public.account_provision_finalize(uuid,text)'::regprocedure,
       'public.account_provision_fail(uuid,text)'::regprocedure,
       'public.account_admin_prepare(uuid,text,uuid,uuid,text,timestamptz)'::regprocedure,
       'public.account_admin_apply(uuid,text)'::regprocedure,
       'public.account_admin_fail(uuid,text)'::regprocedure
     ]) rpc),
    'trusted service operations retain execute access to backend RPCs'
);

insert into public.shops (id, slug, display_name) values
  ('a2400000-0000-4000-8000-000000000001', 'matrix-shop-a', 'Matrix Shop A'),
  ('b2400000-0000-4000-8000-000000000001', 'matrix-shop-b', 'Matrix Shop B');

insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values
  ('00000000-0000-0000-0000-000000000000', '10240000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'matrix-admin@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '20240000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'matrix-owner-a@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '22240000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'matrix-owner-b@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '30240000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'matrix-sales-a@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '32240000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'matrix-disabled@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '40240000-0000-4000-8000-000000000004', 'authenticated', 'authenticated', 'matrix-nomember@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');

insert into public.user_profiles (user_id, login_id, display_name, platform_role, disabled) values
  ('10240000-0000-4000-8000-000000000001', 'matrix.admin', 'Matrix Admin', 'super_admin', false),
  ('20240000-0000-4000-8000-000000000002', 'matrix.owner.a', 'Matrix Owner A', 'standard', false),
  ('22240000-0000-4000-8000-000000000002', 'matrix.owner.b', 'Matrix Owner B', 'standard', false),
  ('30240000-0000-4000-8000-000000000003', 'matrix.sales.a', 'Matrix Sales A', 'standard', false),
  ('32240000-0000-4000-8000-000000000003', 'matrix.disabled', 'Matrix Disabled', 'standard', true),
  ('40240000-0000-4000-8000-000000000004', 'matrix.nomember', 'Matrix No Member', 'standard', false);

insert into public.shop_memberships (shop_id, user_id, role) values
  ('a2400000-0000-4000-8000-000000000001', '20240000-0000-4000-8000-000000000002', 'owner'),
  ('b2400000-0000-4000-8000-000000000001', '22240000-0000-4000-8000-000000000002', 'owner'),
  ('a2400000-0000-4000-8000-000000000001', '30240000-0000-4000-8000-000000000003', 'salesman'),
  ('a2400000-0000-4000-8000-000000000001', '32240000-0000-4000-8000-000000000003', 'salesman');

insert into public.products (id, shop_id, sku_code, name, default_selling_price_paisa) values
  ('aa240000-0000-4000-8000-000000000001', 'a2400000-0000-4000-8000-000000000001', 'MATRIX-A', 'Matrix Product A', 1000),
  ('bb240000-0000-4000-8000-000000000001', 'b2400000-0000-4000-8000-000000000001', 'MATRIX-B', 'Matrix Product B', 2000);
insert into public.inventory_lots (
  id, shop_id, product_id, source_type, source_id, unit_cost_paisa,
  original_quantity, remaining_quantity
) values
  ('ac240000-0000-4000-8000-000000000001', 'a2400000-0000-4000-8000-000000000001', 'aa240000-0000-4000-8000-000000000001', 'fixture', 'matrix-a', 500, 2, 1),
  ('bc240000-0000-4000-8000-000000000001', 'b2400000-0000-4000-8000-000000000001', 'bb240000-0000-4000-8000-000000000001', 'fixture', 'matrix-b', 900, 2, 1);
insert into public.inventory_movements (
  id, shop_id, product_id, lot_id, movement_type, quantity_delta,
  unit_cost_paisa, source_type, source_id, actor_user_id, idempotency_key
) values
  ('ad240000-0000-4000-8000-000000000001', 'a2400000-0000-4000-8000-000000000001', 'aa240000-0000-4000-8000-000000000001', 'ac240000-0000-4000-8000-000000000001', 'purchase', 1, 500, 'fixture', 'matrix-a', '20240000-0000-4000-8000-000000000002', 'matrix-a'),
  ('bd240000-0000-4000-8000-000000000001', 'b2400000-0000-4000-8000-000000000001', 'bb240000-0000-4000-8000-000000000001', 'bc240000-0000-4000-8000-000000000001', 'purchase', 1, 900, 'fixture', 'matrix-b', '22240000-0000-4000-8000-000000000002', 'matrix-b');

set local role authenticated;
select set_config('request.jwt.claim.sub', '40240000-0000-4000-8000-000000000004', true);
select is((select count(*) from public.shops), 0::bigint, 'no-membership user sees no shops');
select is((select count(*) from public.user_profiles), 1::bigint, 'no-membership user sees only own profile');
select is((select count(*) from public.shop_memberships), 0::bigint, 'no-membership user sees no memberships');
select is((select count(*) from public.products), 0::bigint, 'no-membership user sees no products');
select is((select count(*) from public.inventory_lots), 0::bigint, 'no-membership user sees no inventory lots');
select is((select count(*) from public.inventory_movements), 0::bigint, 'no-membership user sees no inventory movements');

select set_config('request.jwt.claim.sub', '30240000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.shops), 1::bigint, 'active Salesman sees the assigned shop only');
select is((select count(*) from public.user_profiles), 1::bigint, 'active Salesman sees own profile only');
select is((select count(*) from public.shop_memberships), 1::bigint, 'active Salesman sees own membership only');
select is((select count(*) from public.products where shop_id = 'a2400000-0000-4000-8000-000000000001'), 1::bigint, 'active Salesman sees own-shop products');
select is((select count(*) from public.products where shop_id = 'b2400000-0000-4000-8000-000000000001'), 0::bigint, 'active Salesman cannot read cross-shop products');
select is((select count(*) from public.inventory_lots where shop_id = 'b2400000-0000-4000-8000-000000000001'), 0::bigint, 'active Salesman cannot read cross-shop lots');

select set_config('request.jwt.claim.sub', '32240000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.shops), 0::bigint, 'disabled Salesman stale token sees no shops');
select is((select count(*) from public.user_profiles), 0::bigint, 'disabled Salesman stale token sees no profiles');
select is((select count(*) from public.shop_memberships), 0::bigint, 'disabled Salesman stale token sees no memberships');
select is((select count(*) from public.products), 0::bigint, 'disabled Salesman stale token sees no products');
select is((select count(*) from public.inventory_lots), 0::bigint, 'disabled Salesman stale token sees no lots');
select is((select count(*) from public.inventory_movements), 0::bigint, 'disabled Salesman stale token sees no movements');

select set_config('request.jwt.claim.sub', '20240000-0000-4000-8000-000000000002', true);
select is((select count(*) from public.shops where id = 'a2400000-0000-4000-8000-000000000001'), 1::bigint, 'Owner sees correct shop');
select is((select count(*) from public.shops where id = 'b2400000-0000-4000-8000-000000000001'), 0::bigint, 'Owner cannot see wrong shop');
select is((select count(*) from public.user_profiles), 3::bigint, 'Owner sees own profile and profiles assigned to owned shop');
select is((select count(*) from public.shop_memberships), 3::bigint, 'Owner sees memberships in owned shop only');
select is((select count(*) from public.products where shop_id = 'b2400000-0000-4000-8000-000000000001'), 0::bigint, 'Owner cannot read wrong-shop products');
select is((select count(*) from public.inventory_movements where shop_id = 'b2400000-0000-4000-8000-000000000001'), 0::bigint, 'Owner cannot read wrong-shop movements');

select set_config('request.jwt.claim.sub', '10240000-0000-4000-8000-000000000001', true);
select is((select count(*) from public.shops), 2::bigint, 'Super Admin sees all shops');
select is((select count(*) from public.user_profiles), 6::bigint, 'Super Admin sees all profiles');
select is((select count(*) from public.shop_memberships), 4::bigint, 'Super Admin sees all memberships');
select is((select count(*) from public.products), 2::bigint, 'Super Admin sees all products');
select is((select count(*) from public.inventory_lots), 2::bigint, 'Super Admin sees all inventory lots');
select is((select count(*) from public.inventory_movements), 2::bigint, 'Super Admin sees all inventory movements');

select throws_ok(
  $$insert into public.products (shop_id, sku_code, name, default_selling_price_paisa)
    values ('b2400000-0000-4000-8000-000000000001', 'FORGED', 'Forged', 1)$$,
  '42501', 'permission denied for table products',
  'forged shop_id cannot bypass direct INSERT denial'
);
select throws_ok(
  $$update public.products set current_stock = 999$$,
  '42501', 'permission denied for table products',
  'product stock projection cannot be updated directly'
);
select throws_ok(
  $$update public.inventory_lots set remaining_quantity = 0$$,
  '42501', 'permission denied for table inventory_lots',
  'immutable lot state cannot be updated directly'
);
select throws_ok(
  $$delete from public.inventory_movements$$,
  '42501', 'permission denied for table inventory_movements',
  'append-only movement rows cannot be deleted directly'
);
select throws_ok(
  $$delete from public.inventory_lots$$,
  '42501', 'permission denied for table inventory_lots',
  'inventory lots cannot be deleted directly'
);

reset role;
select * from finish();
rollback;
