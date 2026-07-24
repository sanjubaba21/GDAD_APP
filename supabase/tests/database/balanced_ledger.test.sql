begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(28);

select ok(
  (select bool_and(to_regclass(table_name) is not null) from unnest(array[
    'public.financial_accounts', 'public.accounting_periods',
    'public.journal_transactions', 'public.journal_entries', 'public.expenses'
  ]) table_name),
  'all Task 2.5 ledger/control tables exist'
);
select ok(
  (select bool_and(relrowsecurity) from pg_class where oid = any(array[
    'public.financial_accounts'::regclass, 'public.accounting_periods'::regclass,
    'public.journal_transactions'::regclass, 'public.journal_entries'::regclass,
    'public.expenses'::regclass
  ])),
  'RLS is enabled on every Task 2.5 table'
);
select ok(
  (select bool_and(has_table_privilege('authenticated', table_name, 'select'))
   from unnest(array[
    'public.financial_accounts', 'public.accounting_periods',
    'public.journal_transactions', 'public.journal_entries', 'public.expenses'
  ]) table_name),
  'authenticated receives RLS-filtered ledger SELECT'
);
select ok(
  (select bool_and(
    not has_table_privilege('authenticated', table_name, 'insert')
    and not has_table_privilege('authenticated', table_name, 'update')
    and not has_table_privilege('authenticated', table_name, 'delete')
  ) from unnest(array[
    'public.financial_accounts', 'public.accounting_periods',
    'public.journal_transactions', 'public.journal_entries', 'public.expenses'
  ]) table_name),
  'authenticated cannot mutate ledger/control tables directly'
);
select is(
  (select count(*) from pg_trigger where tgconstraint <> 0 and tgname in (
    'journal_transactions_integrity', 'journal_entries_integrity', 'expenses_integrity'
  )), 3::bigint,
  'all three deferred ledger integrity triggers exist'
);
select ok(
  not has_function_privilege('authenticated', 'private.assert_journal_integrity(uuid)', 'execute')
  and not has_function_privilege(
    'authenticated', 'private.journal_source_exists(public.journal_kind,uuid,uuid)', 'execute'
  ),
  'clients cannot invoke private ledger integrity helpers'
);

insert into public.shops (id, slug, display_name) values
  ('a2450000-0000-4000-8000-000000000001', 'ledger-schema-a', 'Ledger Schema A'),
  ('b2450000-0000-4000-8000-000000000001', 'ledger-schema-b', 'Ledger Schema B');
insert into auth.users (
  instance_id, id, aud, role, email, encrypted_password, email_confirmed_at,
  raw_app_meta_data, raw_user_meta_data, created_at, updated_at,
  confirmation_token, email_change, email_change_token_new, recovery_token
) values
  ('00000000-0000-0000-0000-000000000000', '10245000-0000-4000-8000-000000000001', 'authenticated', 'authenticated', 'ledger-admin@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '20245000-0000-4000-8000-000000000002', 'authenticated', 'authenticated', 'ledger-owner@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '30245000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'ledger-sales@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', ''),
  ('00000000-0000-0000-0000-000000000000', '32245000-0000-4000-8000-000000000003', 'authenticated', 'authenticated', 'ledger-disabled@auth.gdad.invalid', '', now(), '{}', '{}', now(), now(), '', '', '', '');
insert into public.user_profiles (user_id, login_id, display_name, platform_role, disabled) values
  ('10245000-0000-4000-8000-000000000001', 'ledger.schema.admin', 'Ledger Admin', 'super_admin', false),
  ('20245000-0000-4000-8000-000000000002', 'ledger.schema.owner', 'Ledger Owner', 'standard', false),
  ('30245000-0000-4000-8000-000000000003', 'ledger.schema.sales', 'Ledger Sales', 'standard', false),
  ('32245000-0000-4000-8000-000000000003', 'ledger.schema.disabled', 'Ledger Disabled', 'standard', true);
insert into public.shop_memberships (shop_id, user_id, role) values
  ('a2450000-0000-4000-8000-000000000001', '20245000-0000-4000-8000-000000000002', 'owner'),
  ('a2450000-0000-4000-8000-000000000001', '30245000-0000-4000-8000-000000000003', 'salesman'),
  ('a2450000-0000-4000-8000-000000000001', '32245000-0000-4000-8000-000000000003', 'salesman');

insert into public.financial_accounts (
  id, shop_id, display_name, account_type, normal_side, purpose_code, system_managed
) values
  ('a0250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001', 'Cash', 'cash', 'debit', 'cash_main', true),
  ('a1250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001', 'Bank', 'bank', 'debit', 'bank_main', true),
  ('a2250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001', 'Expense Control', 'expense', 'debit', 'expense_control', true),
  ('a3250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001', 'Opening Equity', 'equity', 'credit', 'opening_equity', true),
  ('b0250000-0000-4000-8000-000000000001', 'b2450000-0000-4000-8000-000000000001', 'Other Cash', 'cash', 'debit', 'cash_main', true);

insert into public.accounting_periods (id, shop_id, date_from, date_to) values (
  'a4250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  '2026-07-01', '2026-07-31'
);
select ok(
  exists(select 1 from pg_constraint where conrelid='public.accounting_periods'::regclass and contype='x'),
  'accounting periods have an overlap exclusion constraint'
);

insert into public.journal_transactions (
  id, shop_id, kind, description, business_date, actor_user_id, idempotency_key
) values (
  'c0250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'opening_balance', 'Opening cash', '2026-07-24',
  '20245000-0000-4000-8000-000000000002', 'journal-opening'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c0250000-0000-4000-8000-000000000001', 1, 'a0250000-0000-4000-8000-000000000001', 1000, 0),
  ('a2450000-0000-4000-8000-000000000001', 'c0250000-0000-4000-8000-000000000001', 2, 'a3250000-0000-4000-8000-000000000001', 0, 1000);
select lives_ok(
  $$select private.assert_journal_integrity('c0250000-0000-4000-8000-000000000001')$$,
  'balanced opening journal passes integrity'
);
select is(
  (select sum(debit_paisa-credit_paisa) from public.journal_entries
   where financial_account_id='a0250000-0000-4000-8000-000000000001'),
  1000::bigint,
  'cash balance is derived from entries rather than a writable column'
);

insert into public.journal_transactions (
  id, shop_id, kind, description, business_date, actor_user_id, idempotency_key
) values (
  'c1250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'opening_balance', 'Unbalanced', '2026-07-24',
  '20245000-0000-4000-8000-000000000002', 'journal-unbalanced'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c1250000-0000-4000-8000-000000000001', 1, 'a0250000-0000-4000-8000-000000000001', 100, 0),
  ('a2450000-0000-4000-8000-000000000001', 'c1250000-0000-4000-8000-000000000001', 2, 'a3250000-0000-4000-8000-000000000001', 0, 90);
select throws_ok(
  $$select private.assert_journal_integrity('c1250000-0000-4000-8000-000000000001')$$,
  '23514', 'journal transaction is not balanced',
  'unbalanced journal is rejected'
);
delete from public.journal_entries where journal_transaction_id='c1250000-0000-4000-8000-000000000001';
delete from public.journal_transactions where id='c1250000-0000-4000-8000-000000000001';

select throws_ok(
  $$insert into public.journal_entries (
    shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa
  ) values (
    'a2450000-0000-4000-8000-000000000001', 'c0250000-0000-4000-8000-000000000001',
    3, 'b0250000-0000-4000-8000-000000000001', 1
  )$$,
  '23503', 'insert or update on table "journal_entries" violates foreign key constraint "journal_entries_shop_id_financial_account_id_fkey"',
  'cross-shop journal account is rejected'
);

insert into public.journal_transactions (
  id, shop_id, kind, description, source_id, business_date, actor_user_id, idempotency_key
) values (
  'c2250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'sale_payment', 'Orphan receipt', 'ffffffff-ffff-4fff-8fff-ffffffffffff',
  '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-orphan'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c2250000-0000-4000-8000-000000000001', 1, 'a0250000-0000-4000-8000-000000000001', 10, 0),
  ('a2450000-0000-4000-8000-000000000001', 'c2250000-0000-4000-8000-000000000001', 2, 'a3250000-0000-4000-8000-000000000001', 0, 10);
select throws_ok(
  $$select private.assert_journal_integrity('c2250000-0000-4000-8000-000000000001')$$,
  '23503', 'journal source does not exist in shop',
  'orphan typed journal source is rejected'
);
delete from public.journal_entries where journal_transaction_id='c2250000-0000-4000-8000-000000000001';
delete from public.journal_transactions where id='c2250000-0000-4000-8000-000000000001';

insert into public.expenses (
  id, shop_id, category, amount_paisa, journal_transaction_id, business_date,
  actor_user_id, idempotency_key
) values (
  'd0250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'Supplies', 200, 'c3250000-0000-4000-8000-000000000001', '2026-07-24',
  '20245000-0000-4000-8000-000000000002', 'expense-valid'
);
insert into public.journal_transactions (
  id, shop_id, kind, description, source_id, business_date, actor_user_id, idempotency_key
) values (
  'c3250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'expense', 'Supplies expense', 'd0250000-0000-4000-8000-000000000001',
  '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-expense'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c3250000-0000-4000-8000-000000000001', 1, 'a2250000-0000-4000-8000-000000000001', 200, 0),
  ('a2450000-0000-4000-8000-000000000001', 'c3250000-0000-4000-8000-000000000001', 2, 'a0250000-0000-4000-8000-000000000001', 0, 200);
select lives_ok(
  $$select private.assert_journal_integrity('c3250000-0000-4000-8000-000000000001')$$,
  'expense amount reconciles to balanced journal'
);

insert into public.expenses (
  id, shop_id, category, amount_paisa, journal_transaction_id, business_date,
  actor_user_id, idempotency_key
) values (
  'd1250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'Mismatch', 300, 'c4250000-0000-4000-8000-000000000001', '2026-07-24',
  '20245000-0000-4000-8000-000000000002', 'expense-mismatch'
);
insert into public.journal_transactions (
  id, shop_id, kind, description, source_id, business_date, actor_user_id, idempotency_key
) values (
  'c4250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'expense', 'Mismatch expense', 'd1250000-0000-4000-8000-000000000001',
  '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-expense-mismatch'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c4250000-0000-4000-8000-000000000001', 1, 'a2250000-0000-4000-8000-000000000001', 200, 0),
  ('a2450000-0000-4000-8000-000000000001', 'c4250000-0000-4000-8000-000000000001', 2, 'a0250000-0000-4000-8000-000000000001', 0, 200);
select throws_ok(
  $$select private.assert_journal_integrity('c4250000-0000-4000-8000-000000000001')$$,
  '23514', 'expense amount does not reconcile to journal',
  'expense header cannot diverge from journal amount'
);
delete from public.journal_entries where journal_transaction_id='c4250000-0000-4000-8000-000000000001';
delete from public.expenses where id='d1250000-0000-4000-8000-000000000001';
delete from public.journal_transactions where id='c4250000-0000-4000-8000-000000000001';

insert into public.journal_transactions (
  id, shop_id, kind, description, reversal_of_id, business_date, actor_user_id, idempotency_key
) values (
  'c5250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'reversal', 'Reverse supplies', 'c3250000-0000-4000-8000-000000000001',
  '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-reversal'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c5250000-0000-4000-8000-000000000001', 1, 'a2250000-0000-4000-8000-000000000001', 0, 200),
  ('a2450000-0000-4000-8000-000000000001', 'c5250000-0000-4000-8000-000000000001', 2, 'a0250000-0000-4000-8000-000000000001', 200, 0);
select lives_ok(
  $$select private.assert_journal_integrity('c5250000-0000-4000-8000-000000000001')$$,
  'exact compensating reversal passes integrity'
);

insert into public.journal_transactions (
  id, shop_id, kind, description, reversal_of_id, business_date, actor_user_id, idempotency_key
) values (
  'c6250000-0000-4000-8000-000000000001', 'a2450000-0000-4000-8000-000000000001',
  'reversal', 'Bad opening reversal', 'c0250000-0000-4000-8000-000000000001',
  '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-bad-reversal'
);
insert into public.journal_entries (
  shop_id, journal_transaction_id, line_number, financial_account_id, debit_paisa, credit_paisa
) values
  ('a2450000-0000-4000-8000-000000000001', 'c6250000-0000-4000-8000-000000000001', 1, 'a0250000-0000-4000-8000-000000000001', 0, 900),
  ('a2450000-0000-4000-8000-000000000001', 'c6250000-0000-4000-8000-000000000001', 2, 'a3250000-0000-4000-8000-000000000001', 900, 0);
select throws_ok(
  $$select private.assert_journal_integrity('c6250000-0000-4000-8000-000000000001')$$,
  '23514', 'journal reversal does not exactly compensate original',
  'partial or mismatched reversal is rejected'
);
delete from public.journal_entries where journal_transaction_id='c6250000-0000-4000-8000-000000000001';
delete from public.journal_transactions where id='c6250000-0000-4000-8000-000000000001';

select throws_ok(
  $$insert into public.journal_transactions (
    shop_id, kind, description, business_date, actor_user_id, idempotency_key
  ) values (
    'a2450000-0000-4000-8000-000000000001', 'opening_balance', 'Duplicate',
    '2026-07-24', '20245000-0000-4000-8000-000000000002', 'journal-opening'
  )$$,
  '23505', 'duplicate key value violates unique constraint "journal_transactions_shop_id_idempotency_key_key"',
  'journal idempotency is unique per shop'
);

set local role authenticated;
select set_config('request.jwt.claim.sub', '30245000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.financial_accounts), 0::bigint, 'Salesman cannot read financial accounts');
select is((select count(*) from public.journal_transactions), 0::bigint, 'Salesman cannot read journals');
select is((select count(*) from public.expenses), 0::bigint, 'Salesman cannot read expenses');

select set_config('request.jwt.claim.sub', '20245000-0000-4000-8000-000000000002', true);
select is((select count(*) from public.financial_accounts), 4::bigint, 'Owner reads own-shop accounts');
select is((select count(*) from public.journal_transactions), 3::bigint, 'Owner reads valid own-shop journals');
select is((select count(*) from public.expenses), 1::bigint, 'Owner reads own-shop expense evidence');

select set_config('request.jwt.claim.sub', '32245000-0000-4000-8000-000000000003', true);
select is((select count(*) from public.financial_accounts), 0::bigint, 'disabled stale session reads no accounts');
select set_config('request.jwt.claim.sub', '10245000-0000-4000-8000-000000000001', true);
select is((select count(*) from public.financial_accounts), 5::bigint, 'Super Admin reads accounts across shops');

select throws_ok(
  $$insert into public.financial_accounts (
    shop_id, display_name, account_type, normal_side
  ) values ('a2450000-0000-4000-8000-000000000001','Forged','cash','debit')$$,
  '42501', 'permission denied for table financial_accounts',
  'authenticated cannot forge financial accounts'
);
select throws_ok(
  $$update public.journal_transactions set description='Changed'$$,
  '42501', 'permission denied for table journal_transactions',
  'authenticated cannot rewrite journal headers'
);
select throws_ok(
  $$delete from public.journal_entries$$,
  '42501', 'permission denied for table journal_entries',
  'authenticated cannot delete journal evidence'
);

reset role;
select * from finish();
rollback;
