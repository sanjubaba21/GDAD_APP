begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(51);

select ok(to_regclass('private.sale_operation_requests') is not null,'sale idempotency state exists');
select ok((select relrowsecurity from pg_class where oid='private.sale_operation_requests'::regclass),'sale request state has RLS');
select ok(not has_table_privilege('authenticated','private.sale_operation_requests','select'),'clients cannot read sale request internals');
select ok(has_function_privilege('authenticated','public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)','execute'),'authenticated may call protected sale RPC');
select ok(not has_table_privilege('authenticated','public.sales','insert') and not has_table_privilege('authenticated','public.inventory_lots','update') and not has_table_privilege('authenticated','public.journal_transactions','insert'),'direct sale stock and ledger writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a3700000-0000-4000-8000-000000000001','atomic-sale-a','Atomic Sale A'),
 ('b3700000-0000-4000-8000-000000000001','atomic-sale-b','Atomic Sale B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10370000-0000-4000-8000-000000000001','authenticated','authenticated','sale-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20370000-0000-4000-8000-000000000002','authenticated','authenticated','sale-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30370000-0000-4000-8000-000000000003','authenticated','authenticated','sale-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10370000-0000-4000-8000-000000000001','fifo.owner.a','FIFO Owner A'),
 ('20370000-0000-4000-8000-000000000002','fifo.sales.a','FIFO Sales A'),
 ('30370000-0000-4000-8000-000000000003','fifo.owner.b','FIFO Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a3700000-0000-4000-8000-000000000001','10370000-0000-4000-8000-000000000001','owner'),
 ('a3700000-0000-4000-8000-000000000001','20370000-0000-4000-8000-000000000002','salesman'),
 ('b3700000-0000-4000-8000-000000000001','30370000-0000-4000-8000-000000000003','owner');
insert into public.products(id,shop_id,sku_code,name,low_stock_threshold,default_selling_price_paisa,current_stock) values
 ('a3800000-0000-4000-8000-000000000001','a3700000-0000-4000-8000-000000000001','FIFO-A1','FIFO Product A1',2,1000,5),
 ('a3800000-0000-4000-8000-000000000002','a3700000-0000-4000-8000-000000000001','FIFO-A2','FIFO Product A2',0,500,3),
 ('b3800000-0000-4000-8000-000000000001','b3700000-0000-4000-8000-000000000001','FIFO-B1','FIFO Product B1',0,700,2);
insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,received_at,unit_cost_paisa,original_quantity,remaining_quantity) values
 ('a3900000-0000-4000-8000-000000000001','a3700000-0000-4000-8000-000000000001','a3800000-0000-4000-8000-000000000001','opening_balance','fifo-a1-old',now()-interval '2 days',300,2,2),
 ('a3900000-0000-4000-8000-000000000002','a3700000-0000-4000-8000-000000000001','a3800000-0000-4000-8000-000000000001','opening_balance','fifo-a1-new',now()-interval '1 day',400,3,3),
 ('a3900000-0000-4000-8000-000000000003','a3700000-0000-4000-8000-000000000001','a3800000-0000-4000-8000-000000000002','opening_balance','fifo-a2',now(),200,3,3),
 ('b3900000-0000-4000-8000-000000000001','b3700000-0000-4000-8000-000000000001','b3800000-0000-4000-8000-000000000001','opening_balance','fifo-b1',now(),250,2,2);
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a3a00000-0000-4000-8000-000000000001','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b3a00000-0000-4000-8000-000000000001','b3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a3b00000-0000-4000-8000-000000000001','a3700000-0000-4000-8000-000000000001','Inventory','inventory','debit','inventory_control',true),
 ('a3b00000-0000-4000-8000-000000000002','a3700000-0000-4000-8000-000000000001','COGS','cogs','debit','cost_of_goods_sold',true),
 ('a3b00000-0000-4000-8000-000000000003','a3700000-0000-4000-8000-000000000001','Receivable','receivable','debit','accounts_receivable',true),
 ('a3b00000-0000-4000-8000-000000000004','a3700000-0000-4000-8000-000000000001','Revenue','revenue','credit','sales_revenue',true),
 ('a3b00000-0000-4000-8000-000000000005','a3700000-0000-4000-8000-000000000001','Cash','cash','debit','cash_main',true),
 ('a3b00000-0000-4000-8000-000000000006','a3700000-0000-4000-8000-000000000001','Bank','bank','debit','bank_main',true);

set local role authenticated;
select set_config('request.jwt.claim.sub','10370000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_fifo_sale('owner-credit','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":4,"effective_unit_price_paisa":900,"line_discount_paisa":100}]',500,true,'  Customer One  ','  9800000000  ',(timezone('Asia/Kathmandu',now()))::date+7,'[{"method":"cash","amount_paisa":1000}]')$$,'Owner posts discounted partial-payment credit sale');
reset role;

select is((select count(*) from public.sales where shop_id='a3700000-0000-4000-8000-000000000001'),1::bigint,'one sale is created');
select results_eq($$select status,is_credit,subtotal_paisa,line_discount_total_paisa,sale_discount_total_paisa,grand_total_paisa from public.sales where id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit')$$,$$select 'posted'::public.sale_status,true,3600::bigint,100::bigint,500::bigint,3000::bigint$$,'server totals and credit state reconcile');
select results_eq($$select configured_unit_price_paisa,effective_unit_price_paisa,line_discount_paisa,allocated_sale_discount_paisa,line_total_paisa from public.sale_lines where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit')$$,$$select 1000::bigint,900::bigint,100::bigint,500::bigint,3000::bigint$$,'line snapshots retain Owner price and discounts');
select is((select count(*) from public.sale_lot_allocations where sale_line_id=(select id from public.sale_lines where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit'))),2::bigint,'sale spans two FIFO lots');
select results_eq($$select allocation.lot_id,allocation.quantity,allocation.unit_cost_paisa from public.sale_lot_allocations allocation join public.inventory_lots lot on lot.id=allocation.lot_id where allocation.sale_line_id=(select id from public.sale_lines where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit')) order by lot.received_at,lot.id$$,$$select * from (values('a3900000-0000-4000-8000-000000000001'::uuid,2,300::bigint),('a3900000-0000-4000-8000-000000000002'::uuid,2,400::bigint)) expected(lot_id,quantity,unit_cost_paisa) order by lot_id$$,'allocation consumes oldest lot then next lot');
select results_eq($$select id,remaining_quantity from public.inventory_lots where product_id='a3800000-0000-4000-8000-000000000001' order by id$$,$$select * from (values('a3900000-0000-4000-8000-000000000001'::uuid,0),('a3900000-0000-4000-8000-000000000002'::uuid,1)) expected(id,remaining_quantity) order by id$$,'FIFO lot balances decrement exactly');
select is((select current_stock from public.products where id='a3800000-0000-4000-8000-000000000001'),1,'stock projection decrements exactly');
select is((select sum(quantity_delta) from public.inventory_movements where source_type='sale' and source_id=(select sale_id::text from private.sale_operation_requests where idempotency_key='owner-credit')),-4::bigint,'sale movements reconcile to quantity');
select is((select result->>'cost_total_paisa' from private.sale_operation_requests where idempotency_key='owner-credit'),'1400','authoritative FIFO cost is returned');
select is((select result->>'due_paisa' from private.sale_operation_requests where idempotency_key='owner-credit'),'2000','authoritative credit due is returned');
select is((select count(*) from public.sale_payments where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit')),1::bigint,'initial partial payment is recorded');
select is((select count(*) from public.journal_transactions where source_id in ((select sale_id from private.sale_operation_requests where idempotency_key='owner-credit'),(select id from public.sale_payments where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='owner-credit')))),2::bigint,'sale and payment journals are created');
select ok(not exists(select 1 from public.journal_entries where journal_transaction_id in(select id from public.journal_transactions where shop_id='a3700000-0000-4000-8000-000000000001') group by journal_transaction_id having sum(debit_paisa)<>sum(credit_paisa)),'all sale journals balance');
select is((select count(*) from public.notifications where record_id='a3800000-0000-4000-8000-000000000001'),1::bigint,'low-stock notification is emitted once');
select is((select count(*) from private.business_audit_events where record_type='sale'),1::bigint,'sale writes one safe audit event');
select lives_ok($$select private.assert_sale_integrity((select sale_id from private.sale_operation_requests where idempotency_key='owner-credit'))$$,'sale integrity helper accepts allocations');
select lives_ok($$select private.assert_sale_money_integrity((select sale_id from private.sale_operation_requests where idempotency_key='owner-credit'))$$,'sale money helper accepts partial credit');

set local role authenticated;
select set_config('request.jwt.claim.sub','10370000-0000-4000-8000-000000000001',true);
select is((public.post_fifo_sale('owner-credit','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":4,"effective_unit_price_paisa":900,"line_discount_paisa":100}]',500,true,'  Customer One  ','  9800000000  ',(timezone('Asia/Kathmandu',now()))::date+7,'[{"method":"cash","amount_paisa":1000}]')->>'sale_id')::uuid,(select id from public.sales where idempotency_key='sale:owner-credit:header'),'exact retry returns original sale');
select is((select count(*) from public.sale_lot_allocations where shop_id='a3700000-0000-4000-8000-000000000001'),2::bigint,'retry cannot duplicate FIFO consumption');
select throws_ok($$select public.post_fifo_sale('owner-credit','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":1}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":1000}]')$$,'22023','idempotency key payload mismatch','changed retry payload is rejected');
select throws_ok($$select public.post_fifo_sale('shortage','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":2}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":2000}]')$$,'23514','insufficient stock','shortage is rejected');
reset role;
select is((select current_stock from public.products where id='a3800000-0000-4000-8000-000000000001'),1,'shortage rollback leaves stock unchanged');
select is((select count(*) from public.sales where idempotency_key='sale:shortage:header'),0::bigint,'shortage rollback leaves no sale');

set local role authenticated;
select set_config('request.jwt.claim.sub','20370000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.post_fifo_sale('salesman-full','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000002","quantity":1}]',0,false,null,null,null,'[{"method":"bank","amount_paisa":500}]')$$,'Salesman posts configured-price fully-paid sale');
select is((select grand_total_paisa from public.sales where idempotency_key='sale:salesman-full:header'),500::bigint,'Salesman price is server configured');
select is((public.post_fifo_sale('salesman-negotiated','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000002","quantity":1,"effective_unit_price_paisa":400}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":400}]')->>'grand_total_paisa')::bigint,400::bigint,'Salesman may post a fully paid negotiated-price sale');
select results_eq($$select configured_unit_price_paisa,effective_unit_price_paisa,line_total_paisa from public.sale_lines where sale_id=(select sale_id from private.sale_operation_requests where idempotency_key='salesman-negotiated')$$,$$select 500::bigint,400::bigint,400::bigint$$,'sale line preserves suggested and negotiated price snapshots');
select throws_ok($$select public.post_fifo_sale('salesman-credit','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000002","quantity":1}]',0,true,'Customer','9800',(timezone('Asia/Kathmandu',now()))::date,'[]')$$,'42501','credit sale is not authorized','Salesman credit is denied');
select throws_ok($$select public.post_fifo_sale('salesman-partial','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000002","quantity":1}]',0,false,null,null,null,'[]')$$,'23514','non-credit sale must be fully paid','Salesman partial payment is denied');
reset role;

set local role authenticated;
select set_config('request.jwt.claim.sub','10370000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_fifo_sale('zero-total','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000002","quantity":1,"effective_unit_price_paisa":0}]',0,false,null,null,null,'[]')$$,'Owner may post zero-total sale with stock cost');
select is((select grand_total_paisa from public.sales where idempotency_key='sale:zero-total:header'),0::bigint,'zero total is stored exactly');
reset role;
select is((select result->>'cost_total_paisa' from private.sale_operation_requests where idempotency_key='zero-total'),'200','zero-total sale still records FIFO cost');
set local role authenticated;
select set_config('request.jwt.claim.sub','10370000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_fifo_sale('discount-too-high','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":1}]',1001,false,null,null,null,'[]')$$,'22023','sale discount exceeds subtotal','negative total is rejected');
select throws_ok($$select public.post_fifo_sale('overpay','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a3800000-0000-4000-8000-000000000001","quantity":1}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":1001}]')$$,'23514','sale payment exceeds total','overpayment is rejected');
select throws_ok($$select public.post_fifo_sale('cross-product','a3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"b3800000-0000-4000-8000-000000000001","quantity":1}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":700}]')$$,'42501','sale product is not available','cross-shop product is rejected');
select set_config('request.jwt.claim.sub','30370000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.post_fifo_sale('missing-accounts','b3700000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"b3800000-0000-4000-8000-000000000001","quantity":1}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":700}]')$$,'55000','inventory sale accounts are unavailable','missing ledger configuration fails atomically');
reset role;
select is((select count(*) from public.sales where shop_id='b3700000-0000-4000-8000-000000000001'),0::bigint,'configuration failure leaves no partial sale');
select is((select current_stock from public.products where id='b3800000-0000-4000-8000-000000000001'),2,'configuration failure leaves stock unchanged');
select is((select count(*) from public.inventory_movements where shop_id='b3700000-0000-4000-8000-000000000001'),0::bigint,'configuration failure leaves no movements');
select is((select count(*) from public.journal_transactions where shop_id='b3700000-0000-4000-8000-000000000001'),0::bigint,'configuration failure leaves no journals');
select is((select count(*) from private.sale_operation_requests where completed_at is null),0::bigint,'failed requests leave no incomplete idempotency rows');
select ok(not exists(select 1 from public.inventory_lots where remaining_quantity<0),'no FIFO lot can become negative');
select ok(not exists(select 1 from public.products where current_stock<0),'no product projection can become negative');
select is((select count(*) from public.sales where shop_id='a3700000-0000-4000-8000-000000000001'),4::bigint,'only four authorized successful sales persist');

set local role authenticated;
select set_config('request.jwt.claim.sub','10370000-0000-4000-8000-000000000001',true);
select throws_ok($$insert into public.sales(shop_id,subtotal_paisa,grand_total_paisa,business_date,actor_user_id,idempotency_key) values('a3700000-0000-4000-8000-000000000001',1,1,(timezone('Asia/Kathmandu',now()))::date,'10370000-0000-4000-8000-000000000001','forged')$$,'42501','permission denied for table sales','direct sale writes remain denied');
reset role;

select * from finish();
rollback;
