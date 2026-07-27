begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(41);

select ok(to_regclass('private.purchase_operation_requests') is not null,'purchase idempotency state exists');
select ok((select relrowsecurity from pg_class where oid='private.purchase_operation_requests'::regclass),'purchase request state has RLS');
select ok(not has_table_privilege('authenticated','private.purchase_operation_requests','select'),'clients cannot read purchase request internals');
select ok(has_function_privilege('authenticated','public.post_purchase_receipt(text,uuid,uuid,text,date,jsonb,bigint,public.payment_method)','execute'),'authenticated may call protected purchase RPC');
select ok(not has_table_privilege('authenticated','public.purchase_bills','insert') and not has_table_privilege('authenticated','public.inventory_lots','insert') and not has_table_privilege('authenticated','public.journal_transactions','insert'),'direct purchase stock and ledger writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a3200000-0000-4000-8000-000000000001','atomic-purchase-a','Atomic Purchase A'),
 ('b3200000-0000-4000-8000-000000000001','atomic-purchase-b','Atomic Purchase B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10320000-0000-4000-8000-000000000001','authenticated','authenticated','purchase-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20320000-0000-4000-8000-000000000002','authenticated','authenticated','purchase-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30320000-0000-4000-8000-000000000003','authenticated','authenticated','purchase-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10320000-0000-4000-8000-000000000001','purchase.owner.a','Purchase Owner A'),
 ('20320000-0000-4000-8000-000000000002','purchase.sales.a','Purchase Sales A'),
 ('30320000-0000-4000-8000-000000000003','purchase.owner.b','Purchase Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a3200000-0000-4000-8000-000000000001','10320000-0000-4000-8000-000000000001','owner'),
 ('a3200000-0000-4000-8000-000000000001','20320000-0000-4000-8000-000000000002','salesman'),
 ('b3200000-0000-4000-8000-000000000001','30320000-0000-4000-8000-000000000003','owner');
insert into public.vendors(id,shop_id,display_name) values
 ('a3300000-0000-4000-8000-000000000001','a3200000-0000-4000-8000-000000000001','Atomic Vendor A'),
 ('b3300000-0000-4000-8000-000000000001','b3200000-0000-4000-8000-000000000001','Atomic Vendor B');
insert into public.products(id,shop_id,sku_code,name,default_selling_price_paisa) values
 ('a3400000-0000-4000-8000-000000000001','a3200000-0000-4000-8000-000000000001','ATOMIC-A1','Atomic Product A1',1000),
 ('a3400000-0000-4000-8000-000000000002','a3200000-0000-4000-8000-000000000001','ATOMIC-A2','Atomic Product A2',1200),
 ('b3400000-0000-4000-8000-000000000001','b3200000-0000-4000-8000-000000000001','ATOMIC-B1','Atomic Product B1',900);
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a3500000-0000-4000-8000-000000000001','a3200000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b3500000-0000-4000-8000-000000000001','b3200000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a3600000-0000-4000-8000-000000000001','a3200000-0000-4000-8000-000000000001','Inventory Control','inventory','debit','inventory_control',true),
 ('a3600000-0000-4000-8000-000000000002','a3200000-0000-4000-8000-000000000001','Accounts Payable','payable','credit','accounts_payable',true),
 ('a3600000-0000-4000-8000-000000000003','a3200000-0000-4000-8000-000000000001','Cash','cash','debit','cash_main',true),
 ('a3600000-0000-4000-8000-000000000004','a3200000-0000-4000-8000-000000000001','Bank','bank','debit','bank_main',true);

set local role authenticated;
select set_config('request.jwt.claim.sub','10320000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_purchase_receipt(
 'purchase-ok','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',' INV  001 ',
 (timezone('Asia/Kathmandu',now()))::date,
 '[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":2,"unit_cost_paisa":600},{"product_id":"a3400000-0000-4000-8000-000000000002","quantity":1,"unit_cost_paisa":800}]',500,'cash')$$,'Owner posts atomic received purchase');
reset role;

select is((select count(*) from public.purchase_bills where shop_id='a3200000-0000-4000-8000-000000000001'),1::bigint,'one purchase bill is created');
select results_eq($$select status,subtotal_paisa,grand_total_paisa from public.purchase_bills where shop_id='a3200000-0000-4000-8000-000000000001'$$,$$values('received'::public.purchase_bill_status,2000::bigint,2000::bigint)$$,'bill is fully received with server total');
select is((select count(*) from public.purchase_bill_lines where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'two reconciled bill lines are created');
select is((select count(*) from public.purchase_receipt_lines where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'two receipt lines are created');
select is((select count(*) from public.inventory_lots where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'one FIFO lot per line is created');
select ok(not exists(select 1 from public.inventory_lots lot join public.purchase_receipt_lines line on line.id=lot.purchase_receipt_line_id where lot.original_quantity<>line.quantity or lot.remaining_quantity<>line.quantity or lot.unit_cost_paisa<>line.unit_cost_paisa),'FIFO lots exactly reconcile to receipt quantity and cost');
select results_eq($$select id,current_stock from public.products where shop_id='a3200000-0000-4000-8000-000000000001' order by id$$,$$values('a3400000-0000-4000-8000-000000000001'::uuid,2),('a3400000-0000-4000-8000-000000000002'::uuid,1)$$,'product stock projection increments exactly');
select is((select count(*) from public.inventory_movements where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'one append-only movement per received line is created');
select is((select sum(quantity_delta) from public.inventory_movements where shop_id='a3200000-0000-4000-8000-000000000001'),3::bigint,'movement quantity reconciles to received stock');
select is((select count(*) from public.vendor_payments where shop_id='a3200000-0000-4000-8000-000000000001'),1::bigint,'optional vendor payment is created');
select is((select sum(amount_paisa) from public.vendor_payment_allocations where shop_id='a3200000-0000-4000-8000-000000000001'),500::numeric,'payment is fully allocated to bill');
select is((select bill.grand_total_paisa-coalesce(sum(allocation.amount_paisa),0) from public.purchase_bills bill left join public.vendor_payment_allocations allocation on allocation.purchase_bill_id=bill.id where bill.shop_id='a3200000-0000-4000-8000-000000000001' group by bill.id),1500::numeric,'vendor due is derived as 1500 paisa');
select is((select count(*) from public.journal_transactions where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'receipt and payment journals are created');
select is((select count(*) from public.journal_entries where shop_id='a3200000-0000-4000-8000-000000000001'),4::bigint,'both journals have two entries');
select ok(not exists(select 1 from public.journal_entries where shop_id='a3200000-0000-4000-8000-000000000001' group by journal_transaction_id having sum(debit_paisa)<>sum(credit_paisa)),'every purchase journal balances');
select is((select count(*) from private.business_audit_events where shop_id='a3200000-0000-4000-8000-000000000001' and record_type='purchase_bill'),1::bigint,'purchase writes one safe audit event');
select ok((select completed_at is not null and result->>'due_paisa'='1500' from private.purchase_operation_requests where shop_id='a3200000-0000-4000-8000-000000000001'),'idempotency request stores completed result');
select lives_ok($$select private.assert_purchase_bill_integrity((select id from public.purchase_bills where shop_id='a3200000-0000-4000-8000-000000000001'))$$,'bill integrity passes');
select lives_ok($$select private.assert_purchase_receipt_integrity((select id from public.purchase_receipts where shop_id='a3200000-0000-4000-8000-000000000001'))$$,'receipt and FIFO integrity passes');
select lives_ok($$select private.assert_vendor_payment_integrity((select id from public.vendor_payments where shop_id='a3200000-0000-4000-8000-000000000001'))$$,'payment allocation integrity passes');
select lives_ok($$select private.assert_journal_integrity(id) from public.journal_transactions where shop_id='a3200000-0000-4000-8000-000000000001' order by id$$,'journal integrity helpers accept both journals');

set local role authenticated;
select set_config('request.jwt.claim.sub','10320000-0000-4000-8000-000000000001',true);
select is((public.post_purchase_receipt('purchase-ok','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',' INV  001 ',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":2,"unit_cost_paisa":600},{"product_id":"a3400000-0000-4000-8000-000000000002","quantity":1,"unit_cost_paisa":800}]',500,'cash')->>'purchase_bill_id')::uuid,(select id from public.purchase_bills where shop_id='a3200000-0000-4000-8000-000000000001'),'exact retry returns original bill');
select is((select count(*) from public.inventory_lots where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'retry cannot duplicate FIFO stock');
select is((select count(*) from public.journal_transactions where shop_id='a3200000-0000-4000-8000-000000000001'),2::bigint,'retry cannot duplicate ledger entries');
select throws_ok($$select public.post_purchase_receipt('purchase-ok','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001','CHANGED',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'22023','idempotency key payload mismatch','changed retry payload is rejected');
select throws_ok($$select public.post_purchase_receipt('duplicate-invoice','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001','inv 001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'23505',null,'normalized duplicate vendor invoice is rejected');
select set_config('request.jwt.claim.sub','20320000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.post_purchase_receipt('salesman-forged','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'42501','not authorized','Salesman cannot post purchase');
select set_config('request.jwt.claim.sub','10320000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_purchase_receipt('cross-vendor','a3200000-0000-4000-8000-000000000001','b3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'42501','vendor is not available','cross-shop vendor is rejected');
select throws_ok($$select public.post_purchase_receipt('cross-product','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"b3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'42501','purchase product is not available','cross-shop product is rejected');
select throws_ok($$select public.post_purchase_receipt('future-date','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date+1,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',0,null)$$,'22023','business date is outside allowed window','future purchase date is rejected');
select throws_ok($$select public.post_purchase_receipt('overpay','a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":600}]',601,'cash')$$,'23514','vendor payment exceeds purchase total','purchase overpayment is rejected');
select set_config('request.jwt.claim.sub','30320000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.post_purchase_receipt('missing-accounts','b3200000-0000-4000-8000-000000000001','b3300000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"b3400000-0000-4000-8000-000000000001","quantity":1,"unit_cost_paisa":400}]',0,null)$$,'55000','inventory control account is unavailable','missing ledger configuration fails atomically');
reset role;
select is((select count(*) from public.purchase_bills where shop_id='b3200000-0000-4000-8000-000000000001'),0::bigint,'failed operation leaves no partial bill');
select is((select current_stock from public.products where id='b3400000-0000-4000-8000-000000000001'),0,'failed operation leaves stock unchanged');

set local role authenticated;
select set_config('request.jwt.claim.sub','10320000-0000-4000-8000-000000000001',true);
select throws_ok($$insert into public.purchase_bills(shop_id,vendor_id,invoice_date,subtotal_paisa,grand_total_paisa,business_date,actor_user_id,idempotency_key) values('a3200000-0000-4000-8000-000000000001','a3300000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,1,1,(timezone('Asia/Kathmandu',now()))::date,'10320000-0000-4000-8000-000000000001','forged')$$,'42501','permission denied for table purchase_bills','direct purchase writes remain denied');
reset role;
select * from finish();
rollback;
