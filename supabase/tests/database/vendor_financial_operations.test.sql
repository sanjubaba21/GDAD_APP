begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(59);

select ok('vendor_return'=any(enum_range(null::public.inventory_movement_type)::text[]),'vendor return movement type exists');
select ok('vendor_return_reversal'=any(enum_range(null::public.inventory_movement_type)::text[]),'vendor return reversal movement type exists');
select ok(to_regclass('private.vendor_operation_requests') is not null,'vendor operation request state exists');
select ok((select relrowsecurity from pg_class where oid='private.vendor_operation_requests'::regclass),'vendor request state has RLS');
select ok(not has_table_privilege('authenticated','private.vendor_operation_requests','select'),'clients cannot read vendor request internals');
select ok(has_function_privilege('authenticated','public.post_vendor_payment(text,uuid,uuid,public.payment_method,jsonb,date)','execute'),'authenticated may call protected vendor payment');
select ok(has_function_privilege('authenticated','public.post_vendor_return(text,uuid,uuid,date,text,jsonb)','execute'),'authenticated may call protected vendor return');
select ok(has_function_privilege('authenticated','public.reverse_vendor_event(text,uuid,text,uuid,date,text)','execute'),'authenticated may call protected vendor reversal');
select ok(not has_table_privilege('authenticated','public.vendor_payments','insert') and not has_table_privilege('authenticated','public.vendor_returns','insert'),'direct vendor money/return writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a5100000-0000-4000-8000-000000000001','vendor-ops-a','Vendor Ops A'),
 ('b5100000-0000-4000-8000-000000000001','vendor-ops-b','Vendor Ops B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10510000-0000-4000-8000-000000000001','authenticated','authenticated','vendor-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20510000-0000-4000-8000-000000000002','authenticated','authenticated','vendor-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30510000-0000-4000-8000-000000000003','authenticated','authenticated','vendor-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10510000-0000-4000-8000-000000000001','vendor.owner.a','Vendor Owner A'),
 ('20510000-0000-4000-8000-000000000002','vendor.sales.a','Vendor Sales A'),
 ('30510000-0000-4000-8000-000000000003','vendor.owner.b','Vendor Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a5100000-0000-4000-8000-000000000001','10510000-0000-4000-8000-000000000001','owner'),
 ('a5100000-0000-4000-8000-000000000001','20510000-0000-4000-8000-000000000002','salesman'),
 ('b5100000-0000-4000-8000-000000000001','30510000-0000-4000-8000-000000000003','owner');
insert into public.vendors(id,shop_id,display_name) values
 ('a5200000-0000-4000-8000-000000000001','a5100000-0000-4000-8000-000000000001','Vendor A'),
 ('b5200000-0000-4000-8000-000000000001','b5100000-0000-4000-8000-000000000001','Vendor B');
insert into public.products(id,shop_id,sku_code,name,default_selling_price_paisa) values
 ('a5300000-0000-4000-8000-000000000001','a5100000-0000-4000-8000-000000000001','VENDOR-A1','Vendor Product A1',1500),
 ('a5300000-0000-4000-8000-000000000002','a5100000-0000-4000-8000-000000000001','VENDOR-A2','Vendor Product A2',900),
 ('b5300000-0000-4000-8000-000000000001','b5100000-0000-4000-8000-000000000001','VENDOR-B1','Vendor Product B1',900);
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a5400000-0000-4000-8000-000000000001','a5100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b5400000-0000-4000-8000-000000000001','b5100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a5500000-0000-4000-8000-000000000001','a5100000-0000-4000-8000-000000000001','Inventory','inventory','debit','inventory_control',true),
 ('a5500000-0000-4000-8000-000000000002','a5100000-0000-4000-8000-000000000001','Payable','payable','credit','accounts_payable',true),
 ('a5500000-0000-4000-8000-000000000003','a5100000-0000-4000-8000-000000000001','Cash','cash','debit','cash_main',true),
 ('a5500000-0000-4000-8000-000000000004','a5100000-0000-4000-8000-000000000001','Bank','bank','debit','bank_main',true);

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_purchase_receipt('vendor-source-one','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','V-1',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a5300000-0000-4000-8000-000000000001","quantity":2,"unit_cost_paisa":1000}]',0,null)$$,'create first unpaid purchase bill');
select lives_ok($$select public.post_purchase_receipt('vendor-source-two','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','V-2',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a5300000-0000-4000-8000-000000000002","quantity":1,"unit_cost_paisa":500}]',0,null)$$,'create second unpaid purchase bill');
select lives_ok($$select public.post_vendor_payment('multi-bill-cash','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','cash',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-1'),'amount_paisa',500),jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-2'),'amount_paisa',200)),(timezone('Asia/Kathmandu',now()))::date)$$,'allocate one cash payment across two bills');
reset role;

select is((select amount_paisa from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header'),700::bigint,'payment total is server-derived');
select is((select count(*) from public.vendor_payment_allocations where vendor_payment_id=(select id from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header')),2::bigint,'payment has two complete allocations');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-1')),1500::bigint,'first bill due derives after partial allocation');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-2')),300::bigint,'second bill due derives after partial allocation');
select is((select result->>'vendor_due_after_paisa' from private.vendor_operation_requests where idempotency_key='multi-bill-cash'),'1800','vendor aggregate due is authoritative');
select lives_ok($$select private.assert_vendor_payment_integrity((select id from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header'))$$,'payment allocation integrity passes');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select is((public.post_vendor_payment('multi-bill-cash','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','cash',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-1'),'amount_paisa',500),jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-2'),'amount_paisa',200)),(timezone('Asia/Kathmandu',now()))::date)->>'vendor_payment_id')::uuid,(select id from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header'),'exact payment retry returns original');
select is((select count(*) from public.vendor_payments where vendor_id='a5200000-0000-4000-8000-000000000001'),1::bigint,'payment retry cannot duplicate money');
select throws_ok($$select public.post_vendor_payment('multi-bill-cash','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','bank',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-1'),'amount_paisa',1)),(timezone('Asia/Kathmandu',now()))::date)$$,'22023','idempotency key payload mismatch','changed payment retry is rejected');
select throws_ok($$select public.post_vendor_payment('overpay','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','cash',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-2'),'amount_paisa',301)),(timezone('Asia/Kathmandu',now()))::date)$$,'23514','vendor payment exceeds bill due','bill overpayment is rejected');
select lives_ok($$select public.post_vendor_return('return-one','a5100000-0000-4000-8000-000000000001',(select id from public.purchase_bills where invoice_reference='V-1'),(timezone('Asia/Kathmandu',now()))::date,'Unsuitable stock',jsonb_build_array(jsonb_build_object('purchase_receipt_line_id',(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1')),'quantity',1)))$$,'return one unpaid original-lot unit');
reset role;

select is((select total_value_paisa from public.vendor_returns where idempotency_key='vendor-return:return-one:header'),1000::bigint,'vendor return value derives from purchase lot');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-1')),500::bigint,'return reduces first bill due');
select is((select remaining_quantity from public.inventory_lots where purchase_receipt_line_id=(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1'))),1,'vendor return decrements original lot');
select is((select current_stock from public.products where id='a5300000-0000-4000-8000-000000000001'),1,'vendor return decrements stock projection');
select is((select quantity_delta from public.inventory_movements where idempotency_key='vendor-return:return-one:movement:1'),-1,'vendor return appends negative movement');
select lives_ok($$select private.assert_vendor_return_integrity((select id from public.vendor_returns where idempotency_key='vendor-return:return-one:header'))$$,'vendor return integrity passes');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select is((public.post_vendor_return('return-one','a5100000-0000-4000-8000-000000000001',(select id from public.purchase_bills where invoice_reference='V-1'),(timezone('Asia/Kathmandu',now()))::date,'Unsuitable stock',jsonb_build_array(jsonb_build_object('purchase_receipt_line_id',(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1')),'quantity',1)))->>'vendor_return_id')::uuid,(select id from public.vendor_returns where idempotency_key='vendor-return:return-one:header'),'exact return retry returns original');
select throws_ok($$select public.post_vendor_return('return-two-blocked','a5100000-0000-4000-8000-000000000001',(select id from public.purchase_bills where invoice_reference='V-1'),(timezone('Asia/Kathmandu',now()))::date,'Second unit',jsonb_build_array(jsonb_build_object('purchase_receipt_line_id',(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1')),'quantity',1)))$$,'23514','vendor return exceeds unpaid bill due','return cannot over-credit paid bill');
select lives_ok($$select public.reverse_vendor_event('reverse-cash','a5100000-0000-4000-8000-000000000001','payment',(select id from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header'),(timezone('Asia/Kathmandu',now()))::date,'Payment recalled')$$,'reverse allocated vendor payment');
reset role;

select is((select status from public.vendor_payments where idempotency_key='vendor-payment:multi-bill-cash:header'),'reversed'::public.financial_event_status,'payment reversal marks original event');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-1')),1000::bigint,'payment reversal restores first bill due');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-2')),500::bigint,'payment reversal restores second bill due');
select is((select count(*) from public.journal_transactions where kind='reversal' and reversal_of_id=(select id from public.journal_transactions where kind='vendor_payment')),1::bigint,'payment reversal creates compensating journal');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_vendor_return('return-two','a5100000-0000-4000-8000-000000000001',(select id from public.purchase_bills where invoice_reference='V-1'),(timezone('Asia/Kathmandu',now()))::date,'Second unit',jsonb_build_array(jsonb_build_object('purchase_receipt_line_id',(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1')),'quantity',1)))$$,'return becomes allowed after payment reversal');
reset role;
select is((select status from public.purchase_bills where invoice_reference='V-1'),'returned'::public.purchase_bill_status,'full quantity return closes purchase bill');
select is((select remaining_quantity from public.inventory_lots where purchase_receipt_line_id=(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1'))),0,'second return depletes purchase lot');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-1')),0::bigint,'fully returned unpaid bill has zero due');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.reverse_vendor_event('reverse-return-two','a5100000-0000-4000-8000-000000000001','return',(select id from public.vendor_returns where idempotency_key='vendor-return:return-two:header'),(timezone('Asia/Kathmandu',now()))::date,'Vendor return cancelled')$$,'reverse second vendor return');
reset role;
select is((select status from public.vendor_returns where idempotency_key='vendor-return:return-two:header'),'reversed'::public.purchase_event_status,'return reversal marks original event');
select is((select remaining_quantity from public.inventory_lots where purchase_receipt_line_id=(select id from public.purchase_receipt_lines where purchase_bill_id=(select id from public.purchase_bills where invoice_reference='V-1'))),1,'return reversal restores original lot');
select is((select current_stock from public.products where id='a5300000-0000-4000-8000-000000000001'),1,'return reversal restores stock projection');
select is((select quantity_delta from public.inventory_movements where idempotency_key='vendor-reversal:reverse-return-two:movement:1'),1,'return reversal appends compensating movement');
select is((select status from public.purchase_bills where invoice_reference='V-1'),'partially_returned'::public.purchase_bill_status,'return reversal restores derived bill status');
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-1')),1000::bigint,'return reversal restores bill due');
select ok(not exists(select 1 from public.journal_entries where journal_transaction_id in(select id from public.journal_transactions where shop_id='a5100000-0000-4000-8000-000000000001') group by journal_transaction_id having sum(debit_paisa)<>sum(credit_paisa)),'all vendor journals and reversals balance');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select is((public.reverse_vendor_event('reverse-return-two','a5100000-0000-4000-8000-000000000001','return',(select id from public.vendor_returns where idempotency_key='vendor-return:return-two:header'),(timezone('Asia/Kathmandu',now()))::date,'Vendor return cancelled')->>'event_id')::uuid,(select id from public.vendor_returns where idempotency_key='vendor-return:return-two:header'),'exact reversal retry returns original event');
reset role;
select is((select count(*) from public.inventory_movements where idempotency_key='vendor-reversal:reverse-return-two:movement:1'),1::bigint,'reversal retry cannot restore stock twice');

set local role authenticated;
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_vendor_payment('bill-two-bank','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','bank',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-2'),'amount_paisa',500)),(timezone('Asia/Kathmandu',now()))::date)$$,'post full bank payment after reversal');
reset role;
select is(private.vendor_bill_due((select id from public.purchase_bills where invoice_reference='V-2')),0::bigint,'bank payment fully settles second bill');
select is((select coalesce(sum(private.vendor_bill_due(id)),0) from public.purchase_bills where vendor_id='a5200000-0000-4000-8000-000000000001'),1000::numeric,'final vendor balance reconciles across bills');
select is((select count(*) from public.notifications where category='purchase' and shop_id='a5100000-0000-4000-8000-000000000001'),2::bigint,'successful vendor returns notify Owners');
select is((select count(*) from private.business_audit_events where record_type in('vendor_payment','vendor_return') and shop_id='a5100000-0000-4000-8000-000000000001'),6::bigint,'vendor operations and reversals write safe audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20510000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.post_vendor_payment('salesman-denied','a5100000-0000-4000-8000-000000000001','a5200000-0000-4000-8000-000000000001','cash','[]',(timezone('Asia/Kathmandu',now()))::date)$$,'42501','not authorized','Salesman cannot pay vendors');
select set_config('request.jwt.claim.sub','10510000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_vendor_payment('cross-vendor','a5100000-0000-4000-8000-000000000001','b5200000-0000-4000-8000-000000000001','cash',jsonb_build_array(jsonb_build_object('purchase_bill_id',(select id from public.purchase_bills where invoice_reference='V-2'),'amount_paisa',1)),(timezone('Asia/Kathmandu',now()))::date)$$,'42501','vendor is not available','cross-shop vendor is denied');
reset role;
select is((select count(*) from private.vendor_operation_requests where completed_at is null),0::bigint,'failed vendor operations leave no incomplete request rows');
select ok(not exists(select 1 from public.inventory_lots where remaining_quantity<0 or remaining_quantity>original_quantity),'vendor return and reversal keep lot balances valid');

select * from finish();
rollback;
