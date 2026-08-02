begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(31);

select ok(
  (select bool_and(to_regclass(table_name) is not null) from unnest(array[
    'public.sales', 'public.sale_lines', 'public.sale_lot_allocations',
    'public.sale_payments', 'public.sale_returns', 'public.sale_return_lines',
    'public.sale_return_allocations', 'public.refunds'
  ]) table_name),
  'all Task 2.3 sales/payment/return tables exist'
);
select ok(
  (select bool_and(relrowsecurity) from pg_class where oid = any(array[
    'public.sales'::regclass, 'public.sale_lines'::regclass,
    'public.sale_lot_allocations'::regclass, 'public.sale_payments'::regclass,
    'public.sale_returns'::regclass, 'public.sale_return_lines'::regclass,
    'public.sale_return_allocations'::regclass, 'public.refunds'::regclass
  ])),
  'RLS is enabled on every Task 2.3 table'
);
select ok(
  (select bool_and(has_table_privilege('authenticated', table_name, 'select'))
   from unnest(array[
    'public.sales', 'public.sale_lines', 'public.sale_lot_allocations',
    'public.sale_payments', 'public.sale_returns', 'public.sale_return_lines',
    'public.sale_return_allocations', 'public.refunds'
   ]) table_name),
  'authenticated receives RLS-filtered SELECT on every Task 2.3 table'
);
select ok(
  (select bool_and(
    not has_table_privilege('authenticated', table_name, 'insert')
    and not has_table_privilege('authenticated', table_name, 'update')
    and not has_table_privilege('authenticated', table_name, 'delete')
  ) from unnest(array[
    'public.sales', 'public.sale_lines', 'public.sale_lot_allocations',
    'public.sale_payments', 'public.sale_returns', 'public.sale_return_lines',
    'public.sale_return_allocations', 'public.refunds'
  ]) table_name),
  'authenticated cannot directly mutate Task 2.3 tables'
);
select is(
  (select count(*) from pg_trigger where tgconstraint <> 0 and tgname in (
    'sales_integrity', 'sale_lines_integrity', 'sale_allocations_integrity',
    'sale_payments_integrity', 'sale_returns_integrity', 'sale_return_lines_integrity',
    'sale_return_allocations_integrity', 'refunds_integrity'
  )),
  8::bigint,
  'all eight deferred integrity triggers exist'
);
select ok(
  not has_function_privilege('authenticated', 'private.assert_sale_integrity(uuid)', 'execute')
  and not has_function_privilege('authenticated', 'private.assert_return_integrity(uuid)', 'execute')
  and not has_function_privilege('authenticated', 'private.assert_sale_money_integrity(uuid)', 'execute'),
  'clients cannot invoke integrity helpers directly'
);

insert into public.shops (id, slug, display_name) values
  ('a2430000-0000-4000-8000-000000000001', 'sales-schema-a', 'Sales Schema A'),
  ('b2430000-0000-4000-8000-000000000001', 'sales-schema-b', 'Sales Schema B');
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values
  ('00000000-0000-0000-0000-000000000000', '10243000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'sale-admin@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '20243000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'sale-owner@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '30243000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'sale-salesman@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '32243000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'sale-disabled@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '40243000-0000-4000-8000-000000000004', 'authenticated', 'authenticated', 'sale-nomember@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');
insert into public.user_profiles (user_id, login_id, display_name, platform_role, disabled) values
  ('10243000-0000-4000-8000-000000000001', 'sale.schema.admin', 'Sale Admin', 'super_admin', false),
  ('20243000-0000-4000-8000-000000000002', 'sale.schema.owner', 'Sale Owner', 'standard', false),
  ('30243000-0000-4000-8000-000000000003', 'sale.schema.sales', 'Sale Salesman', 'standard', false),
  ('32243000-0000-4000-8000-000000000003', 'sale.schema.disabled', 'Sale Disabled', 'standard', true),
  ('40243000-0000-4000-8000-000000000004', 'sale.schema.nomember', 'Sale No Member', 'standard', false);
insert into public.shop_memberships (shop_id, user_id, role) values
  ('a2430000-0000-4000-8000-000000000001', '20243000-0000-4000-8000-000000000002', 'owner'),
  ('a2430000-0000-4000-8000-000000000001', '30243000-0000-4000-8000-000000000003', 'salesman'),
  ('a2430000-0000-4000-8000-000000000001', '32243000-0000-4000-8000-000000000003', 'salesman');
insert into public.products (id, shop_id, sku_code, name, default_selling_price_paisa) values
  ('aa243000-0000-4000-8000-000000000001', 'a2430000-0000-4000-8000-000000000001', 'SALE-A', 'Sale Product A', 1000),
  ('bb243000-0000-4000-8000-000000000001', 'b2430000-0000-4000-8000-000000000001', 'SALE-B', 'Sale Product B', 1000);
insert into public.inventory_lots (
  id, shop_id, product_id, source_type, source_id, unit_cost_paisa,
  original_quantity, remaining_quantity
) values
  ('ac243000-0000-4000-8000-000000000001', 'a2430000-0000-4000-8000-000000000001', 'aa243000-0000-4000-8000-000000000001', 'fixture', 'sale-a', 500, 5, 3),
  ('bc243000-0000-4000-8000-000000000001', 'b2430000-0000-4000-8000-000000000001', 'bb243000-0000-4000-8000-000000000001', 'fixture', 'sale-b', 500, 5, 5);
insert into public.inventory_movements (
  id, shop_id, product_id, lot_id, movement_type, quantity_delta, unit_cost_paisa,
  source_type, source_id, actor_user_id, idempotency_key
) values (
  'ad243000-0000-4000-8000-000000000001', 'a2430000-0000-4000-8000-000000000001',
  'aa243000-0000-4000-8000-000000000001', 'ac243000-0000-4000-8000-000000000001',
  'sale', -2, 500, 'sale', 'sale-valid', '20243000-0000-4000-8000-000000000002', 'sale-schema-movement'
);

select throws_ok(
  $$insert into public.sales (
    id, shop_id, status, subtotal_paisa, grand_total_paisa, business_date,
    actor_user_id, idempotency_key, posted_at
  ) values (
    '50243000-0000-4000-8000-000000000005', 'a2430000-0000-4000-8000-000000000001',
    'posted', 1000, 999, '2026-07-24', '20243000-0000-4000-8000-000000000002',
    'bad-total', now()
  )$$,
  '23514', 'new row for relation "sales" violates check constraint "sales_header_total_reconciles"',
  'invalid sale header arithmetic is rejected'
);
select throws_ok(
  $$insert into public.sales (
    id, shop_id, status, is_credit, subtotal_paisa, grand_total_paisa, business_date,
    actor_user_id, idempotency_key, posted_at
  ) values (
    '51243000-0000-4000-8000-000000000005', 'a2430000-0000-4000-8000-000000000001',
    'posted', true, 1000, 1000, '2026-07-24', '20243000-0000-4000-8000-000000000002',
    'bad-credit', now()
  )$$,
  '23514', 'new row for relation "sales" violates check constraint "sales_credit_identity_complete"',
  'credit sale requires customer identity and due date'
);

insert into public.sales (
  id, shop_id, status, subtotal_paisa, line_discount_total_paisa,
  sale_discount_total_paisa, grand_total_paisa, business_date,
  actor_user_id, idempotency_key, posted_at
) values (
  '60243000-0000-4000-8000-000000000006', 'a2430000-0000-4000-8000-000000000001',
  'posted', 2000, 100, 100, 1800, '2026-07-24',
  '20243000-0000-4000-8000-000000000002', 'sale-valid', now()
);
insert into public.sale_lines (
  id, shop_id, sale_id, line_number, product_id, product_name, sku_code, quantity,
  configured_unit_price_paisa, effective_unit_price_paisa, gross_total_paisa,
  line_discount_paisa, allocated_sale_discount_paisa, line_total_paisa
) values (
  '61243000-0000-4000-8000-000000000006', 'a2430000-0000-4000-8000-000000000001',
  '60243000-0000-4000-8000-000000000006', 1,
  'aa243000-0000-4000-8000-000000000001', 'Sale Product A', 'SALE-A', 2,
  1000, 1000, 2000, 100, 100, 1800
);
insert into public.sale_lot_allocations (
  id, shop_id, sale_line_id, product_id, lot_id, quantity, unit_cost_paisa
) values (
  '62243000-0000-4000-8000-000000000006', 'a2430000-0000-4000-8000-000000000001',
  '61243000-0000-4000-8000-000000000006', 'aa243000-0000-4000-8000-000000000001',
  'ac243000-0000-4000-8000-000000000001', 2, 500
);
insert into public.sale_payments (
  id, shop_id, sale_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
) values (
  '63243000-0000-4000-8000-000000000006', 'a2430000-0000-4000-8000-000000000001',
  '60243000-0000-4000-8000-000000000006', 'cash', 1800, '2026-07-24',
  '20243000-0000-4000-8000-000000000002', 'payment-valid'
);
select lives_ok(
  $$select private.assert_sale_integrity('60243000-0000-4000-8000-000000000006')$$,
  'valid posted sale header, detail, and FIFO allocation reconcile'
);
select lives_ok(
  $$select private.assert_sale_money_integrity('60243000-0000-4000-8000-000000000006')$$,
  'valid non-credit sale is fully settled'
);

insert into public.sale_returns (
  id, shop_id, sale_id, status, reason, total_value_paisa, business_date,
  actor_user_id, idempotency_key, posted_at
) values (
  '70243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '60243000-0000-4000-8000-000000000006', 'posted', 'Customer return', 900,
  '2026-07-24', '20243000-0000-4000-8000-000000000002', 'return-valid', now()
);
insert into public.sale_return_lines (
  id, shop_id, sale_return_id, sale_id, sale_line_id, quantity, disposition, refund_value_paisa
) values (
  '71243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '70243000-0000-4000-8000-000000000007', '60243000-0000-4000-8000-000000000006',
  '61243000-0000-4000-8000-000000000006', 1, 'sellable', 900
);
insert into public.sale_return_allocations (
  id, shop_id, sale_return_line_id, sale_line_id, sale_lot_allocation_id, quantity
) values (
  '72243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '71243000-0000-4000-8000-000000000007', '61243000-0000-4000-8000-000000000006',
  '62243000-0000-4000-8000-000000000006', 1
);
insert into public.refunds (
  id, shop_id, sale_return_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
) values (
  '73243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '70243000-0000-4000-8000-000000000007', 'cash', 900, '2026-07-24',
  '20243000-0000-4000-8000-000000000002', 'refund-valid'
);
select lives_ok(
  $$select private.assert_return_integrity('70243000-0000-4000-8000-000000000007')$$,
  'valid partial return reconciles to original line and FIFO allocation'
);
select lives_ok(
  $$select private.assert_sale_money_integrity('60243000-0000-4000-8000-000000000006')$$,
  'valid refund reconciles paid value to remaining net sale'
);

insert into public.sale_returns (
  id, shop_id, sale_id, status, reason, total_value_paisa, business_date,
  actor_user_id, idempotency_key, posted_at
) values (
  '74243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '60243000-0000-4000-8000-000000000006', 'posted', 'Excess return', 1800,
  '2026-07-24', '20243000-0000-4000-8000-000000000002', 'return-excess', now()
);
insert into public.sale_return_lines (
  id, shop_id, sale_return_id, sale_id, sale_line_id, quantity, disposition, refund_value_paisa
) values (
  '75243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '74243000-0000-4000-8000-000000000007', '60243000-0000-4000-8000-000000000006',
  '61243000-0000-4000-8000-000000000006', 2, 'damaged', 1800
);
insert into public.sale_return_allocations (
  id, shop_id, sale_return_line_id, sale_line_id, sale_lot_allocation_id, quantity
) values (
  '76243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '75243000-0000-4000-8000-000000000007', '61243000-0000-4000-8000-000000000006',
  '62243000-0000-4000-8000-000000000006', 2
);
select throws_ok(
  $$select private.assert_return_integrity('74243000-0000-4000-8000-000000000007')$$,
  '23514', 'sale line over-returned',
  'cumulative returns cannot exceed the original sale line'
);
delete from public.sale_return_allocations where sale_return_line_id = '75243000-0000-4000-8000-000000000007';
delete from public.sale_return_lines where sale_return_id = '74243000-0000-4000-8000-000000000007';
delete from public.sale_returns where id = '74243000-0000-4000-8000-000000000007';

insert into public.refunds (
  id, shop_id, sale_return_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
) values (
  '77243000-0000-4000-8000-000000000007', 'a2430000-0000-4000-8000-000000000001',
  '70243000-0000-4000-8000-000000000007', 'cash', 1, '2026-07-24',
  '20243000-0000-4000-8000-000000000002', 'refund-excess'
);
select throws_ok(
  $$select private.assert_sale_money_integrity('60243000-0000-4000-8000-000000000006')$$,
  '23514', 'return refund exceeds returned value',
  'refunds cannot exceed returned value or effective receipts'
);
delete from public.refunds where id = '77243000-0000-4000-8000-000000000007';

select throws_ok(
  $$insert into public.sale_lines (
    shop_id, sale_id, line_number, product_id, product_name, sku_code, quantity,
    configured_unit_price_paisa, effective_unit_price_paisa, gross_total_paisa, line_total_paisa
  ) values (
    'a2430000-0000-4000-8000-000000000001', '60243000-0000-4000-8000-000000000006', 2,
    'bb243000-0000-4000-8000-000000000001', 'Cross Shop', 'SALE-B', 1,
    1000, 1000, 1000, 1000
  )$$,
  '23503', 'insert or update on table "sale_lines" violates foreign key constraint "sale_lines_shop_id_product_id_fkey"',
  'cross-shop product reference is rejected'
);
select throws_ok(
  $$insert into public.sale_payments (
    shop_id, sale_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
  ) values (
    'a2430000-0000-4000-8000-000000000001', '60243000-0000-4000-8000-000000000006',
    'cash', 1, '2026-07-24', '20243000-0000-4000-8000-000000000002', 'payment-valid'
  )$$,
  '23505', 'duplicate key value violates unique constraint "sale_payments_shop_id_idempotency_key_key"',
  'duplicate payment idempotency key is rejected per shop'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '30243000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.sales), 1::bigint, 'Salesman reads own-shop sale');
select is((select count(*) from public.sale_lines), 1::bigint, 'Salesman reads own-shop sale lines');
select is((select count(*) from public.sale_lot_allocations), 0::bigint, 'Salesman cannot read FIFO cost allocations');
select is((select count(*) from public.inventory_lots), 0::bigint, 'Salesman cannot read lot costs');
select is((select count(*) from public.inventory_movements), 0::bigint, 'Salesman cannot read movement costs');
select is((select count(*) from public.sale_returns), 1::bigint, 'Salesman reads own-shop return status');

select set_config('request.jwt.claim.sub', '20243000-0000-4000-8000-000000000002', true);
select is((select count(*) from public.sale_lot_allocations), 1::bigint, 'Owner reads own-shop FIFO cost allocation');
select is((select count(*) from public.inventory_lots), 1::bigint, 'Owner reads own-shop lot cost');
select is((select count(*) from public.inventory_movements), 1::bigint, 'Owner reads own-shop movement cost');

select set_config('request.jwt.claim.sub', '32243000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.sales), 0::bigint, 'disabled Salesman stale token reads no sales');
select set_config('request.jwt.claim.sub', '40243000-0000-4000-8000-000000000004', true);
select is((select count(*) from public.sales), 0::bigint, 'no-membership user reads no sales');
select set_config('request.jwt.claim.sub', '10243000-0000-4000-8000-000000000001', true);
select is((select count(*) from public.sales), 1::bigint, 'Super Admin reads sales across shops');

select throws_ok(
  $$update public.sales set grand_total_paisa = 0$$,
  '42501', 'permission denied for table sales',
  'authenticated cannot directly update posted sales'
);
select throws_ok(
  $$delete from public.sale_returns$$,
  '42501', 'permission denied for table sale_returns',
  'authenticated cannot directly delete return evidence'
);
select throws_ok(
  $$insert into public.refunds (
    shop_id, sale_return_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
  ) values (
    'a2430000-0000-4000-8000-000000000001', '70243000-0000-4000-8000-000000000007',
    'cash', 1, '2026-07-24', '20243000-0000-4000-8000-000000000002', 'forged-refund'
  )$$,
  '42501', 'permission denied for table refunds',
  'authenticated cannot forge refund rows'
);

reset role;
select * from finish();
rollback;
