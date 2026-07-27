begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(49);

select ok('deposit'=any(enum_range(null::public.journal_kind)::text[]) and 'withdrawal'=any(enum_range(null::public.journal_kind)::text[]),'deposit and withdrawal journal kinds exist');
select ok(to_regclass('private.financial_operation_requests') is not null,'private financial retry state exists');
select ok((select relrowsecurity from pg_class where oid='private.financial_operation_requests'::regclass),'financial request state has RLS');
select ok(not has_table_privilege('authenticated','private.financial_operation_requests','select'),'clients cannot read financial request internals');
select ok(has_function_privilege('authenticated','public.post_expense(text,uuid,uuid,bigint,date,text,text,text)','execute'),'authenticated may call expense RPC');
select ok(has_function_privilege('authenticated','public.post_cash_movement(text,uuid,text,uuid,bigint,date,text)','execute'),'authenticated may call cash movement RPC');
select ok(has_function_privilege('authenticated','public.post_account_transfer(text,uuid,uuid,uuid,bigint,date,text)','execute'),'authenticated may call transfer RPC');
select ok(has_function_privilege('authenticated','public.reverse_financial_operation(text,uuid,uuid,date,text)','execute'),'authenticated may call financial reversal RPC');
select ok(not has_function_privilege('authenticated','private.financial_account_balance(uuid)','execute'),'client cannot invoke private balance helper');
select ok(not has_table_privilege('authenticated','public.expenses','insert') and not has_table_privilege('authenticated','public.journal_transactions','insert') and not has_table_privilege('authenticated','public.journal_entries','insert'),'direct financial writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a5000000-0000-4000-8000-000000000001','atomic-finance-a','Atomic Finance A'),
 ('b5000000-0000-4000-8000-000000000001','atomic-finance-b','Atomic Finance B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10500000-0000-4000-8000-000000000001','authenticated','authenticated','finance-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20500000-0000-4000-8000-000000000002','authenticated','authenticated','finance-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30500000-0000-4000-8000-000000000003','authenticated','authenticated','finance-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10500000-0000-4000-8000-000000000001','finance.owner.a','Finance Owner A'),
 ('20500000-0000-4000-8000-000000000002','finance.sales.a','Finance Sales A'),
 ('30500000-0000-4000-8000-000000000003','finance.owner.b','Finance Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a5000000-0000-4000-8000-000000000001','10500000-0000-4000-8000-000000000001','owner'),
 ('a5000000-0000-4000-8000-000000000001','20500000-0000-4000-8000-000000000002','salesman'),
 ('b5000000-0000-4000-8000-000000000001','30500000-0000-4000-8000-000000000003','owner');
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a5100000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b5100000-0000-4000-8000-000000000001','b5000000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a5200000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000001','Cash','cash','debit','cash_main',true),
 ('a5200000-0000-4000-8000-000000000002','a5000000-0000-4000-8000-000000000001','Bank','bank','debit','bank_main',true),
 ('a5200000-0000-4000-8000-000000000003','a5000000-0000-4000-8000-000000000001','Expense Control','expense','debit','expense_control',true),
 ('a5200000-0000-4000-8000-000000000004','a5000000-0000-4000-8000-000000000001','Cash Movement Clearing','clearing','credit','cash_movement_clearing',true),
 ('a5200000-0000-4000-8000-000000000005','a5000000-0000-4000-8000-000000000001','Opening Equity','equity','credit','opening_equity',true),
 ('b5200000-0000-4000-8000-000000000001','b5000000-0000-4000-8000-000000000001','Other Cash','cash','debit','cash_main',true),
 ('b5200000-0000-4000-8000-000000000002','b5000000-0000-4000-8000-000000000001','Other Expense','expense','debit','expense_control',true);
insert into public.journal_transactions(id,shop_id,kind,description,business_date,actor_user_id,idempotency_key) values
 ('a5300000-0000-4000-8000-000000000001','a5000000-0000-4000-8000-000000000001','opening_balance','Opening cash',(timezone('Asia/Kathmandu',now()))::date,'10500000-0000-4000-8000-000000000001','finance-opening-cash'),
 ('a5300000-0000-4000-8000-000000000002','a5000000-0000-4000-8000-000000000001','opening_balance','Opening bank',(timezone('Asia/Kathmandu',now()))::date,'10500000-0000-4000-8000-000000000001','finance-opening-bank');
insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
 ('a5000000-0000-4000-8000-000000000001','a5300000-0000-4000-8000-000000000001',1,'a5200000-0000-4000-8000-000000000001',5000,0),
 ('a5000000-0000-4000-8000-000000000001','a5300000-0000-4000-8000-000000000001',2,'a5200000-0000-4000-8000-000000000005',0,5000),
 ('a5000000-0000-4000-8000-000000000001','a5300000-0000-4000-8000-000000000002',1,'a5200000-0000-4000-8000-000000000002',1000,0),
 ('a5000000-0000-4000-8000-000000000001','a5300000-0000-4000-8000-000000000002',2,'a5200000-0000-4000-8000-000000000005',0,1000);

set local role authenticated;
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_expense('expense-one','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',1000,(timezone('Asia/Kathmandu',now()))::date,'Supplies','Local Shop','Packing tape')$$,'Owner posts expense');
reset role;
select is((select count(*) from public.expenses where category='Supplies'),1::bigint,'expense evidence is created once');
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000001')),4000::bigint,'expense reduces source balance');
select ok(not exists(select 1 from public.journal_entries entry join public.journal_transactions journal on journal.id=entry.journal_transaction_id where journal.kind='expense' group by journal.id having sum(entry.debit_paisa)<>sum(entry.credit_paisa)),'expense journal balances');
select is((select sum(debit_paisa-credit_paisa)::bigint from public.journal_entries where financial_account_id='a5200000-0000-4000-8000-000000000003'),1000::bigint,'expense control receives exact debit');

set local role authenticated;
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select is((public.post_expense('expense-one','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',1000,(timezone('Asia/Kathmandu',now()))::date,'Supplies','Local Shop','Packing tape')->>'expense_id')::uuid,(select id from public.expenses where category='Supplies'),'exact expense retry returns original');
select throws_ok($$select public.post_expense('expense-one','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',1001,(timezone('Asia/Kathmandu',now()))::date,'Supplies','Local Shop','Changed')$$,'22023','idempotency key payload mismatch','changed expense retry fails');
select throws_ok($$select public.post_expense('expense-too-high','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',4001,(timezone('Asia/Kathmandu',now()))::date,'Rent',null,null)$$,'23514','insufficient account funds','expense cannot overdraw cash');
select lives_ok($$select public.post_cash_movement('deposit-one','a5000000-0000-4000-8000-000000000001','deposit','a5200000-0000-4000-8000-000000000001',2000,(timezone('Asia/Kathmandu',now()))::date,'Owner capital deposit')$$,'Owner posts deposit');
select lives_ok($$select public.post_cash_movement('withdraw-one','a5000000-0000-4000-8000-000000000001','withdrawal','a5200000-0000-4000-8000-000000000001',500,(timezone('Asia/Kathmandu',now()))::date,'Petty cash withdrawal')$$,'Owner posts withdrawal');
reset role;
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000001')),5500::bigint,'deposit and withdrawal derive exact cash balance');
select is((select count(*) from public.journal_transactions where kind in ('deposit','withdrawal') and shop_id='a5000000-0000-4000-8000-000000000001'),2::bigint,'each movement creates one journal');
select ok(not exists(select 1 from public.journal_entries entry join public.journal_transactions journal on journal.id=entry.journal_transaction_id where journal.kind in ('deposit','withdrawal') group by journal.id having sum(entry.debit_paisa)<>sum(entry.credit_paisa)),'cash movement journals balance');

set local role authenticated;
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_cash_movement('withdraw-too-high','a5000000-0000-4000-8000-000000000001','withdrawal','a5200000-0000-4000-8000-000000000001',5501,(timezone('Asia/Kathmandu',now()))::date,'Too much')$$,'23514','insufficient account funds','withdrawal cannot overdraw cash');
select lives_ok($$select public.post_account_transfer('transfer-one','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000002',1500,(timezone('Asia/Kathmandu',now()))::date,'Cash to bank')$$,'Owner transfers cash to bank atomically');
reset role;
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000001')),4000::bigint,'transfer source balance is exact');
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000002')),2500::bigint,'transfer destination balance is exact');
select ok((select count(*)=2 and sum(debit_paisa)=sum(credit_paisa) from public.journal_entries where journal_transaction_id=(select id from public.journal_transactions where kind='transfer')),'transfer is one two-sided balanced transaction');

set local role authenticated;
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select is((public.post_account_transfer('transfer-one','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000002',1500,(timezone('Asia/Kathmandu',now()))::date,'Cash to bank')->>'journal_transaction_id')::uuid,(select id from public.journal_transactions where kind='transfer'),'exact transfer retry returns original');
select throws_ok($$select public.post_account_transfer('transfer-same','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',1,(timezone('Asia/Kathmandu',now()))::date,'Same')$$,'22023','invalid account transfer request','same-account transfer fails');
select throws_ok($$select public.post_account_transfer('transfer-too-high','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000002',4001,(timezone('Asia/Kathmandu',now()))::date,'Too much')$$,'23514','insufficient account funds','transfer cannot overdraw source');
select lives_ok($$select public.reverse_financial_operation('reverse-transfer','a5000000-0000-4000-8000-000000000001',(select id from public.journal_transactions where kind='transfer'),(timezone('Asia/Kathmandu',now()))::date,'Transfer entered in error')$$,'Owner reverses transfer');
reset role;
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000001')),5500::bigint,'transfer reversal restores source');
select is((select private.financial_account_balance('a5200000-0000-4000-8000-000000000002')),1000::bigint,'transfer reversal restores destination');
select ok((select count(*)=1 from public.journal_transactions where reversal_of_id=(select id from public.journal_transactions where kind='transfer')),'reversal creates one compensating journal');

set local role authenticated;
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select is((public.reverse_financial_operation('reverse-transfer','a5000000-0000-4000-8000-000000000001',(select id from public.journal_transactions where kind='transfer'),(timezone('Asia/Kathmandu',now()))::date,'Transfer entered in error')->>'reversal_journal_id')::uuid,(select id from public.journal_transactions where reversal_of_id=(select id from public.journal_transactions where kind='transfer')),'reversal retry returns same journal');
select throws_ok($$select public.reverse_financial_operation('reverse-transfer-again','a5000000-0000-4000-8000-000000000001',(select id from public.journal_transactions where kind='transfer'),(timezone('Asia/Kathmandu',now()))::date,'Again')$$,'42501','financial operation is not reversible','original cannot be reversed twice');
select set_config('request.jwt.claim.sub','20500000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.post_cash_movement('salesman-deposit','a5000000-0000-4000-8000-000000000001','deposit','a5200000-0000-4000-8000-000000000001',1,(timezone('Asia/Kathmandu',now()))::date,'Denied')$$,'42501','not authorized','Salesman cannot post finance operations');
select set_config('request.jwt.claim.sub','10500000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_account_transfer('cross-shop','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','b5200000-0000-4000-8000-000000000001',1,(timezone('Asia/Kathmandu',now()))::date,'Forged')$$,'42501','transfer account is not available','cross-shop account is denied');
select throws_ok($$select public.post_expense('future-expense','a5000000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001',1,(timezone('Asia/Kathmandu',now()))::date+1,'Future',null,null)$$,'22023','business date is outside allowed window','future expense is denied');
select set_config('request.jwt.claim.sub','30500000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.post_cash_movement('missing-clearing','b5000000-0000-4000-8000-000000000001','deposit','b5200000-0000-4000-8000-000000000001',1,(timezone('Asia/Kathmandu',now()))::date,'No clearing')$$,'55000','cash movement clearing account is unavailable','missing clearing account fails atomically');
reset role;

select is((select count(*) from public.journal_transactions where shop_id='b5000000-0000-4000-8000-000000000001'),0::bigint,'configuration failure leaves no journal');
select is((select count(*) from private.financial_operation_requests where completed_at is null),0::bigint,'failed operations leave no incomplete retry row');
select is((select count(*) from private.business_audit_events where shop_id='a5000000-0000-4000-8000-000000000001' and record_type in ('expense','deposit','withdrawal','transfer','journal_transaction')),5::bigint,'each successful original or reversal writes one audit');
select ok(not exists(select 1 from public.journal_transactions journal join public.journal_entries entry on entry.journal_transaction_id=journal.id where journal.shop_id='a5000000-0000-4000-8000-000000000001' group by journal.id having count(*)<2 or sum(entry.debit_paisa)<>sum(entry.credit_paisa)),'all finance journals remain balanced');
select ok(private.financial_account_balance('a5200000-0000-4000-8000-000000000001')>=0 and private.financial_account_balance('a5200000-0000-4000-8000-000000000002')>=0,'cash and bank balances remain nonnegative');
select is((select count(*) from public.expenses where category='Rent'),0::bigint,'failed overdraft leaves no expense evidence');
select is((select count(*) from public.journal_transactions where idempotency_key like 'financial-%' and shop_id='a5000000-0000-4000-8000-000000000001'),5::bigint,'retries do not duplicate financial journals');
select is((select count(*) from private.financial_operation_requests where completed_at is not null and shop_id='a5000000-0000-4000-8000-000000000001'),5::bigint,'each successful operation stores one authoritative result');

select * from finish();
rollback;
