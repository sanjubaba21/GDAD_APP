begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(31);

select ok(
  (select bool_and(to_regclass(table_name) is not null) from unnest(array[
    'public.vendors', 'public.purchase_bills', 'public.purchase_bill_lines',
    'public.purchase_receipts', 'public.purchase_receipt_lines',
    'public.vendor_payments', 'public.vendor_payment_allocations',
    'public.vendor_returns', 'public.vendor_return_lines'
  ]) table_name),
  'all Task 2.4 vendor and purchasing tables exist'
);
select ok(
  (select bool_and(relrowsecurity) from pg_class where oid = any(array[
    'public.vendors'::regclass, 'public.purchase_bills'::regclass,
    'public.purchase_bill_lines'::regclass, 'public.purchase_receipts'::regclass,
    'public.purchase_receipt_lines'::regclass, 'public.vendor_payments'::regclass,
    'public.vendor_payment_allocations'::regclass, 'public.vendor_returns'::regclass,
    'public.vendor_return_lines'::regclass
  ])),
  'RLS is enabled on every Task 2.4 table'
);
select ok(
  (select bool_and(has_table_privilege('authenticated', table_name, 'select'))
   from unnest(array[
    'public.vendors', 'public.purchase_bills', 'public.purchase_bill_lines',
    'public.purchase_receipts', 'public.purchase_receipt_lines',
    'public.vendor_payments', 'public.vendor_payment_allocations',
    'public.vendor_returns', 'public.vendor_return_lines'
  ]) table_name),
  'authenticated receives RLS-filtered SELECT on Task 2.4 tables'
);
select ok(
  (select bool_and(
    not has_table_privilege('authenticated', table_name, 'insert')
    and not has_table_privilege('authenticated', table_name, 'update')
    and not has_table_privilege('authenticated', table_name, 'delete')
  ) from unnest(array[
    'public.vendors', 'public.purchase_bills', 'public.purchase_bill_lines',
    'public.purchase_receipts', 'public.purchase_receipt_lines',
    'public.vendor_payments', 'public.vendor_payment_allocations',
    'public.vendor_returns', 'public.vendor_return_lines'
  ]) table_name),
  'authenticated cannot directly mutate Task 2.4 tables'
);
select is(
  (select count(*) from pg_trigger where tgconstraint <> 0 and tgname in (
    'purchase_bills_integrity', 'purchase_bill_lines_integrity',
    'purchase_receipts_integrity', 'purchase_receipt_lines_integrity',
    'purchase_lots_integrity', 'vendor_payments_integrity',
    'vendor_payment_allocations_integrity', 'vendor_returns_integrity',
    'vendor_return_lines_integrity'
  )), 9::bigint,
  'all nine deferred purchasing integrity triggers exist'
);
select ok(
  not has_function_privilege('authenticated', 'private.assert_purchase_bill_integrity(uuid)', 'execute')
  and not has_function_privilege('authenticated', 'private.assert_purchase_receipt_integrity(uuid)', 'execute')
  and not has_function_privilege('authenticated', 'private.assert_vendor_payment_integrity(uuid)', 'execute')
  and not has_function_privilege('authenticated', 'private.assert_vendor_return_integrity(uuid)', 'execute'),
  'clients cannot invoke purchasing integrity helpers'
);

insert into public.shops (id, slug, display_name) values
  ('a2440000-0000-4000-8000-000000000001', 'purchase-schema-a', 'Purchase Schema A'),
  ('b2440000-0000-4000-8000-000000000001', 'purchase-schema-b', 'Purchase Schema B');
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values
  ('00000000-0000-0000-0000-000000000000', '10244000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'purchase-admin@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '20244000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'purchase-owner@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '30244000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'purchase-sales@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '32244000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'purchase-disabled@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');
insert into public.user_profiles (user_id, login_id, display_name, platform_role, disabled) values
  ('10244000-0000-4000-8000-000000000001', 'purchase.schema.admin', 'Purchase Admin', 'super_admin', false),
  ('20244000-0000-4000-8000-000000000002', 'purchase.schema.owner', 'Purchase Owner', 'standard', false),
  ('30244000-0000-4000-8000-000000000003', 'purchase.schema.sales', 'Purchase Sales', 'standard', false),
  ('32244000-0000-4000-8000-000000000003', 'purchase.schema.disabled', 'Purchase Disabled', 'standard', true);
insert into public.shop_memberships (shop_id, user_id, role) values
  ('a2440000-0000-4000-8000-000000000001', '20244000-0000-4000-8000-000000000002', 'owner'),
  ('a2440000-0000-4000-8000-000000000001', '30244000-0000-4000-8000-000000000003', 'salesman'),
  ('a2440000-0000-4000-8000-000000000001', '32244000-0000-4000-8000-000000000003', 'salesman');
insert into public.products (id, shop_id, sku_code, name, default_selling_price_paisa) values
  ('aa244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001', 'PURCHASE-A', 'Purchase Product A', 1000),
  ('bb244000-0000-4000-8000-000000000001', 'b2440000-0000-4000-8000-000000000001', 'PURCHASE-B', 'Purchase Product B', 1000);
insert into public.vendors (id, shop_id, display_name) values
  ('a1244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001', 'Vendor A'),
  ('b1244000-0000-4000-8000-000000000001', 'b2440000-0000-4000-8000-000000000001', 'Vendor B');

select throws_ok(
  $$insert into public.purchase_bills (
    shop_id, vendor_id, status, invoice_date, subtotal_paisa, grand_total_paisa,
    business_date, actor_user_id, idempotency_key, posted_at
  ) values (
    'a2440000-0000-4000-8000-000000000001', 'a1244000-0000-4000-8000-000000000001',
    'posted', '2026-07-24', 2000, 1999, '2026-07-24',
    '20244000-0000-4000-8000-000000000002', 'bill-bad-total', now()
  )$$,
  '23514', 'new row for relation "purchase_bills" violates check constraint "purchase_bills_total_reconciles"',
  'invalid purchase bill arithmetic is rejected'
);

insert into public.purchase_bills (
  id, shop_id, vendor_id, status, invoice_reference, invoice_date,
  subtotal_paisa, grand_total_paisa, business_date, actor_user_id,
  idempotency_key, posted_at
) values (
  'c0244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'posted', ' INV- 001 ', '2026-07-24',
  2000, 2000, '2026-07-24', '20244000-0000-4000-8000-000000000002',
  'bill-valid', now()
);
insert into public.purchase_bill_lines (
  id, shop_id, purchase_bill_id, line_number, product_id, product_name, sku_code,
  quantity, unit_cost_paisa, gross_total_paisa, line_total_paisa
) values (
  'c1244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'c0244000-0000-4000-8000-000000000001', 1,
  'aa244000-0000-4000-8000-000000000001', 'Purchase Product A', 'PURCHASE-A',
  4, 500, 2000, 2000
);
select lives_ok(
  $$select private.assert_purchase_bill_integrity('c0244000-0000-4000-8000-000000000001')$$,
  'valid posted bill reconciles to its detail'
);
select throws_ok(
  $$insert into public.purchase_bills (
    shop_id, vendor_id, status, invoice_reference, invoice_date,
    subtotal_paisa, grand_total_paisa, business_date, actor_user_id,
    idempotency_key, posted_at
  ) values (
    'a2440000-0000-4000-8000-000000000001', 'a1244000-0000-4000-8000-000000000001',
    'posted', 'inv- 001', '2026-07-24', 1, 1, '2026-07-24',
    '20244000-0000-4000-8000-000000000002', 'bill-duplicate-invoice', now()
  )$$,
  '23505', 'duplicate key value violates unique constraint "purchase_bills_vendor_invoice_unique"',
  'normalized vendor invoice reference is permanently unique per shop/vendor'
);

insert into public.purchase_receipts (
  id, shop_id, purchase_bill_id, business_date, actor_user_id, idempotency_key
) values (
  'd0244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'c0244000-0000-4000-8000-000000000001', '2026-07-24',
  '20244000-0000-4000-8000-000000000002', 'receipt-valid'
);
insert into public.purchase_receipt_lines (
  id, shop_id, purchase_receipt_id, purchase_bill_id, purchase_bill_line_id,
  product_id, quantity, unit_cost_paisa, line_total_paisa
) values (
  'd1244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'd0244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'c1244000-0000-4000-8000-000000000001', 'aa244000-0000-4000-8000-000000000001',
  3, 500, 1500
);
insert into public.inventory_lots (
  id, shop_id, product_id, source_type, source_id, unit_cost_paisa,
  original_quantity, remaining_quantity, purchase_receipt_line_id
) values (
  'd2244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'aa244000-0000-4000-8000-000000000001', 'purchase_receipt',
  'd1244000-0000-4000-8000-000000000001', 500, 3, 3,
  'd1244000-0000-4000-8000-000000000001'
);
select lives_ok(
  $$select private.assert_purchase_receipt_integrity('d0244000-0000-4000-8000-000000000001')$$,
  'valid partial receipt exactly reconciles to its FIFO lot'
);

insert into public.purchase_receipts (
  id, shop_id, purchase_bill_id, business_date, actor_user_id, idempotency_key
) values (
  'd3244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'c0244000-0000-4000-8000-000000000001', '2026-07-24',
  '20244000-0000-4000-8000-000000000002', 'receipt-bad-lot'
);
insert into public.purchase_receipt_lines (
  id, shop_id, purchase_receipt_id, purchase_bill_id, purchase_bill_line_id,
  product_id, quantity, unit_cost_paisa, line_total_paisa
) values (
  'd4244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'd3244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'c1244000-0000-4000-8000-000000000001', 'aa244000-0000-4000-8000-000000000001',
  1, 500, 500
);
insert into public.inventory_lots (
  id, shop_id, product_id, source_type, source_id, unit_cost_paisa,
  original_quantity, remaining_quantity, purchase_receipt_line_id
) values (
  'd5244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'aa244000-0000-4000-8000-000000000001', 'purchase_receipt',
  'd4244000-0000-4000-8000-000000000001', 500, 2, 2,
  'd4244000-0000-4000-8000-000000000001'
);
select throws_ok(
  $$select private.assert_purchase_receipt_integrity('d3244000-0000-4000-8000-000000000001')$$,
  '23514', 'receipt FIFO lot does not reconcile',
  'receipt quantity must exactly match its FIFO lot'
);
delete from public.inventory_lots where id = 'd5244000-0000-4000-8000-000000000001';
delete from public.purchase_receipt_lines where id = 'd4244000-0000-4000-8000-000000000001';
delete from public.purchase_receipts where id = 'd3244000-0000-4000-8000-000000000001';

insert into public.purchase_receipts (
  id, shop_id, purchase_bill_id, business_date, actor_user_id, idempotency_key
) values (
  'd6244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'c0244000-0000-4000-8000-000000000001', '2026-07-24',
  '20244000-0000-4000-8000-000000000002', 'receipt-over'
);
insert into public.purchase_receipt_lines (
  id, shop_id, purchase_receipt_id, purchase_bill_id, purchase_bill_line_id,
  product_id, quantity, unit_cost_paisa, line_total_paisa
) values (
  'd7244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'd6244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'c1244000-0000-4000-8000-000000000001', 'aa244000-0000-4000-8000-000000000001',
  2, 500, 1000
);
select throws_ok(
  $$select private.assert_purchase_receipt_integrity('d6244000-0000-4000-8000-000000000001')$$,
  '23514', 'purchase line over-received',
  'cumulative receipts cannot exceed ordered quantity'
);
delete from public.purchase_receipt_lines where id = 'd7244000-0000-4000-8000-000000000001';
delete from public.purchase_receipts where id = 'd6244000-0000-4000-8000-000000000001';

insert into public.vendor_payments (
  id, shop_id, vendor_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
) values (
  'e0244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'cash', 1000, '2026-07-24',
  '20244000-0000-4000-8000-000000000002', 'vendor-payment-valid'
);
insert into public.vendor_payment_allocations (
  shop_id, vendor_payment_id, vendor_id, purchase_bill_id, amount_paisa
) values (
  'a2440000-0000-4000-8000-000000000001', 'e0244000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001', 1000
);
select lives_ok(
  $$select private.assert_vendor_payment_integrity('e0244000-0000-4000-8000-000000000001')$$,
  'valid vendor payment is fully allocated without overpaying bill'
);

insert into public.vendor_payments (
  id, shop_id, vendor_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
) values (
  'e1244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'bank', 1001, '2026-07-24',
  '20244000-0000-4000-8000-000000000002', 'vendor-payment-over'
);
insert into public.vendor_payment_allocations (
  shop_id, vendor_payment_id, vendor_id, purchase_bill_id, amount_paisa
) values (
  'a2440000-0000-4000-8000-000000000001', 'e1244000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001', 1001
);
select throws_ok(
  $$select private.assert_vendor_payment_integrity('e1244000-0000-4000-8000-000000000001')$$,
  '23514', 'purchase bill overpaid',
  'cumulative vendor payments cannot exceed bill obligation'
);
delete from public.vendor_payment_allocations where vendor_payment_id = 'e1244000-0000-4000-8000-000000000001';
delete from public.vendor_payments where id = 'e1244000-0000-4000-8000-000000000001';

insert into public.vendor_returns (
  id, shop_id, vendor_id, purchase_bill_id, reason, total_value_paisa,
  business_date, actor_user_id, idempotency_key
) values (
  'f0244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'Damaged on receipt', 500, '2026-07-24', '20244000-0000-4000-8000-000000000002',
  'vendor-return-valid'
);
insert into public.vendor_return_lines (
  id, shop_id, vendor_return_id, purchase_bill_id, purchase_receipt_line_id,
  product_id, lot_id, quantity, unit_cost_paisa, line_total_paisa
) values (
  'f1244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'f0244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'd1244000-0000-4000-8000-000000000001', 'aa244000-0000-4000-8000-000000000001',
  'd2244000-0000-4000-8000-000000000001', 1, 500, 500
);
select lives_ok(
  $$select private.assert_vendor_return_integrity('f0244000-0000-4000-8000-000000000001')$$,
  'valid vendor return reconciles to original receipt and FIFO lot'
);

insert into public.vendor_returns (
  id, shop_id, vendor_id, purchase_bill_id, reason, total_value_paisa,
  business_date, actor_user_id, idempotency_key
) values (
  'f2244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'a1244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'Excess return', 1500, '2026-07-24', '20244000-0000-4000-8000-000000000002',
  'vendor-return-over'
);
insert into public.vendor_return_lines (
  id, shop_id, vendor_return_id, purchase_bill_id, purchase_receipt_line_id,
  product_id, lot_id, quantity, unit_cost_paisa, line_total_paisa
) values (
  'f3244000-0000-4000-8000-000000000001', 'a2440000-0000-4000-8000-000000000001',
  'f2244000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001',
  'd1244000-0000-4000-8000-000000000001', 'aa244000-0000-4000-8000-000000000001',
  'd2244000-0000-4000-8000-000000000001', 3, 500, 1500
);
select throws_ok(
  $$select private.assert_vendor_return_integrity('f2244000-0000-4000-8000-000000000001')$$,
  '23514', 'purchase receipt line over-returned',
  'cumulative vendor returns cannot exceed received quantity'
);
delete from public.vendor_return_lines where vendor_return_id = 'f2244000-0000-4000-8000-000000000001';
delete from public.vendor_returns where id = 'f2244000-0000-4000-8000-000000000001';

select throws_ok(
  $$insert into public.purchase_bill_lines (
    shop_id, purchase_bill_id, line_number, product_id, product_name, sku_code,
    quantity, unit_cost_paisa, gross_total_paisa, line_total_paisa
  ) values (
    'a2440000-0000-4000-8000-000000000001', 'c0244000-0000-4000-8000-000000000001', 2,
    'bb244000-0000-4000-8000-000000000001', 'Cross Shop', 'PURCHASE-B', 1, 500, 500, 500
  )$$,
  '23503', 'insert or update on table "purchase_bill_lines" violates foreign key constraint "purchase_bill_lines_shop_id_product_id_fkey"',
  'cross-shop purchase product reference is rejected'
);
select throws_ok(
  $$insert into public.vendor_payments (
    shop_id, vendor_id, method, amount_paisa, business_date, actor_user_id, idempotency_key
  ) values (
    'a2440000-0000-4000-8000-000000000001', 'a1244000-0000-4000-8000-000000000001',
    'cash', 1, '2026-07-24', '20244000-0000-4000-8000-000000000002', 'vendor-payment-valid'
  )$$,
  '23505', 'duplicate key value violates unique constraint "vendor_payments_shop_id_idempotency_key_key"',
  'duplicate vendor payment idempotency key is rejected'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '30244000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.vendors), 0::bigint, 'Salesman cannot read vendors');
select is((select count(*) from public.purchase_bills), 0::bigint, 'Salesman cannot read purchase bills');
select is((select count(*) from public.purchase_receipts), 0::bigint, 'Salesman cannot read purchase receipts');

select set_config('request.jwt.claim.sub', '20244000-0000-4000-8000-000000000002', true);
select is((select count(*) from public.vendors), 1::bigint, 'Owner reads own-shop vendor');
select is((select count(*) from public.purchase_bills), 1::bigint, 'Owner reads own-shop purchase bill');
select is((select count(*) from public.purchase_receipts), 1::bigint, 'Owner reads own-shop receipt');
select is((select count(*) from public.vendor_payments), 1::bigint, 'Owner reads own-shop vendor payment');
select is((select count(*) from public.vendor_returns), 1::bigint, 'Owner reads own-shop vendor return');

select set_config('request.jwt.claim.sub', '32244000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.vendors), 0::bigint, 'disabled stale session reads no vendors');
select set_config('request.jwt.claim.sub', '10244000-0000-4000-8000-000000000001', true);
select is((select count(*) from public.vendors), 2::bigint, 'Super Admin reads vendors across shops');

select throws_ok(
  $$insert into public.vendors (shop_id, display_name)
    values ('a2440000-0000-4000-8000-000000000001', 'Forged Vendor')$$,
  '42501', 'permission denied for table vendors',
  'authenticated cannot forge vendor rows'
);
select throws_ok(
  $$update public.purchase_bills set grand_total_paisa = 0$$,
  '42501', 'permission denied for table purchase_bills',
  'authenticated cannot directly update posted purchase bills'
);
select throws_ok(
  $$delete from public.vendor_returns$$,
  '42501', 'permission denied for table vendor_returns',
  'authenticated cannot delete vendor return evidence'
);

reset role;
select * from finish();
rollback;
