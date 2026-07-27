begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(53);

select ok(to_regclass('private.sale_return_operation_requests') is not null,'return idempotency state exists');
select ok((select relrowsecurity from pg_class where oid='private.sale_return_operation_requests'::regclass),'return request state has RLS');
select ok(not has_table_privilege('authenticated','private.sale_return_operation_requests','select'),'clients cannot read return request internals');
select ok(has_function_privilege('authenticated','public.post_sale_return(text,uuid,uuid,date,text,jsonb,public.payment_method)','execute'),'authenticated may call protected return RPC');
select ok(not has_table_privilege('authenticated','public.sale_returns','insert') and not has_table_privilege('authenticated','public.inventory_lots','update') and not has_table_privilege('authenticated','public.refunds','insert'),'direct return stock and refund writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a4100000-0000-4000-8000-000000000001','atomic-return-a','Atomic Return A'),
 ('b4100000-0000-4000-8000-000000000001','atomic-return-b','Atomic Return B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10410000-0000-4000-8000-000000000001','authenticated','authenticated','return-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20410000-0000-4000-8000-000000000002','authenticated','authenticated','return-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30410000-0000-4000-8000-000000000003','authenticated','authenticated','return-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10410000-0000-4000-8000-000000000001','return.owner.a','Return Owner A'),
 ('20410000-0000-4000-8000-000000000002','return.sales.a','Return Sales A'),
 ('30410000-0000-4000-8000-000000000003','return.owner.b','Return Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a4100000-0000-4000-8000-000000000001','10410000-0000-4000-8000-000000000001','owner'),
 ('a4100000-0000-4000-8000-000000000001','20410000-0000-4000-8000-000000000002','salesman'),
 ('b4100000-0000-4000-8000-000000000001','30410000-0000-4000-8000-000000000003','owner');
insert into public.products(id,shop_id,sku_code,name,default_selling_price_paisa,current_stock) values
 ('a4200000-0000-4000-8000-000000000001','a4100000-0000-4000-8000-000000000001','RETURN-A1','Return Product A1',1000,5),
 ('a4200000-0000-4000-8000-000000000002','a4100000-0000-4000-8000-000000000001','RETURN-A2','Return Product A2',1000,4),
 ('a4200000-0000-4000-8000-000000000003','a4100000-0000-4000-8000-000000000001','RETURN-A3','Return Product A3',1000,1),
 ('b4200000-0000-4000-8000-000000000001','b4100000-0000-4000-8000-000000000001','RETURN-B1','Return Product B1',1000,1);
insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,received_at,unit_cost_paisa,original_quantity,remaining_quantity) values
 ('a4300000-0000-4000-8000-000000000001','a4100000-0000-4000-8000-000000000001','a4200000-0000-4000-8000-000000000001','opening_balance','return-a1-old',now()-interval '2 days',300,2,2),
 ('a4300000-0000-4000-8000-000000000002','a4100000-0000-4000-8000-000000000001','a4200000-0000-4000-8000-000000000001','opening_balance','return-a1-new',now()-interval '1 day',400,3,3),
 ('a4300000-0000-4000-8000-000000000003','a4100000-0000-4000-8000-000000000001','a4200000-0000-4000-8000-000000000002','opening_balance','return-a2',now(),250,4,4),
 ('a4300000-0000-4000-8000-000000000004','a4100000-0000-4000-8000-000000000001','a4200000-0000-4000-8000-000000000003','opening_balance','return-a3',now(),200,1,1),
 ('b4300000-0000-4000-8000-000000000001','b4100000-0000-4000-8000-000000000001','b4200000-0000-4000-8000-000000000001','opening_balance','return-b1',now(),200,1,1);
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a4400000-0000-4000-8000-000000000001','a4100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b4400000-0000-4000-8000-000000000001','b4100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a4500000-0000-4000-8000-000000000001','a4100000-0000-4000-8000-000000000001','Inventory','inventory','debit','inventory_control',true),
 ('a4500000-0000-4000-8000-000000000002','a4100000-0000-4000-8000-000000000001','COGS','cogs','debit','cost_of_goods_sold',true),
 ('a4500000-0000-4000-8000-000000000003','a4100000-0000-4000-8000-000000000001','Receivable','receivable','debit','accounts_receivable',true),
 ('a4500000-0000-4000-8000-000000000004','a4100000-0000-4000-8000-000000000001','Revenue','revenue','credit','sales_revenue',true),
 ('a4500000-0000-4000-8000-000000000005','a4100000-0000-4000-8000-000000000001','Cash','cash','debit','cash_main',true),
 ('a4500000-0000-4000-8000-000000000006','a4100000-0000-4000-8000-000000000001','Bank','bank','debit','bank_main',true);

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_fifo_sale('return-source-full','a4100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a4200000-0000-4000-8000-000000000001","quantity":4}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":4000}]')$$,'create fully paid multi-lot source sale');
select lives_ok($$select public.post_fifo_sale('return-source-credit','a4100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a4200000-0000-4000-8000-000000000002","quantity":3}]',0,true,'Credit Customer','9800000000',(timezone('Asia/Kathmandu',now()))::date+7,'[{"method":"cash","amount_paisa":1000}]')$$,'create partial-credit source sale');
select lives_ok($$select public.post_fifo_sale('return-source-expired','a4100000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date,'[{"product_id":"a4200000-0000-4000-8000-000000000003","quantity":1}]',0,false,null,null,null,'[{"method":"cash","amount_paisa":1000}]')$$,'create expired-window source sale');
reset role;
update public.sales set business_date=(timezone('Asia/Kathmandu',now()))::date-30 where idempotency_key='sale:return-source-credit:header';
update public.sales set business_date=(timezone('Asia/Kathmandu',now()))::date-31 where idempotency_key='sale:return-source-expired:header';

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_sale_return('damaged-one','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Damaged handle',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',1,'disposition','damaged')),'cash')$$,'post damaged partial return');
reset role;
select is((select count(*) from public.sale_returns where reason='Damaged handle'),1::bigint,'damaged return is created');
select is((select amount_paisa from public.refunds where sale_return_id=(select id from public.sale_returns where reason='Damaged handle')),1000::bigint,'fully paid damaged value is refunded');
select is((select current_stock from public.products where id='a4200000-0000-4000-8000-000000000001'),1,'damaged return does not restore stock');
select is((select count(*) from public.inventory_movements where source_type='sale_return' and source_id=(select id::text from public.sale_returns where reason='Damaged handle')),0::bigint,'damaged return creates no saleable movement');
select is((select lot_id from public.sale_lot_allocations where id=(select sale_lot_allocation_id from public.sale_return_allocations where sale_return_line_id=(select id from public.sale_return_lines where sale_return_id=(select id from public.sale_returns where reason='Damaged handle')))),'a4300000-0000-4000-8000-000000000002'::uuid,'damaged evidence consumes newest original allocation first');
select is((select status from public.sales where idempotency_key='sale:return-source-full:header'),'partially_returned'::public.sale_status,'partial return advances sale status');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_sale_return('over-return','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Too many',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',4,'disposition','sellable')),'cash')$$,'23514','sale line over-returned','cumulative line over-return is rejected');
select throws_ok($$select public.post_sale_return('missing-refund','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Needs refund',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',1,'disposition','sellable')),null)$$,'22023','refund method is required','paid return requires refund method');
select lives_ok($$select public.post_sale_return('sellable-two','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Resalable pair',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',2,'disposition','sellable')),'bank')$$,'post repeated sellable return');
reset role;
select is((select count(*) from public.sale_return_allocations where sale_return_line_id=(select id from public.sale_return_lines where sale_return_id=(select id from public.sale_returns where reason='Resalable pair'))),2::bigint,'sellable return spans remaining newest then oldest allocations');
select results_eq($$select lot.id,lot.remaining_quantity from public.inventory_lots lot where lot.product_id='a4200000-0000-4000-8000-000000000001' order by lot.id$$,$$select * from (values('a4300000-0000-4000-8000-000000000001'::uuid,1),('a4300000-0000-4000-8000-000000000002'::uuid,2)) expected(id,remaining_quantity) order by id$$,'sellable quantities restore exact original lots');
select is((select current_stock from public.products where id='a4200000-0000-4000-8000-000000000001'),3,'sellable return restores stock projection');
select is((select sum(quantity_delta) from public.inventory_movements where source_type='sale_return' and source_id=(select id::text from public.sale_returns where reason='Resalable pair')),2::bigint,'sellable return movements reconcile');
select is((select amount_paisa from public.refunds where sale_return_id=(select id from public.sale_returns where reason='Resalable pair')),2000::bigint,'second full-paid return refunds exact returned value');
select is((select result->>'restored_cost_paisa' from private.sale_return_operation_requests where idempotency_key='sellable-two'),'700','restored FIFO cost is authoritative');
select ok(not exists(select 1 from public.journal_entries where journal_transaction_id in(select id from public.journal_transactions where shop_id='a4100000-0000-4000-8000-000000000001') group by journal_transaction_id having sum(debit_paisa)<>sum(credit_paisa)),'return and refund journals balance');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select is((public.post_sale_return('sellable-two','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Resalable pair',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',2,'disposition','sellable')),'bank')->>'sale_return_id')::uuid,(select id from public.sale_returns where idempotency_key='sale-return:sellable-two:header'),'exact retry returns original return');
select is((select count(*) from public.refunds where sale_return_id=(select id from public.sale_returns where idempotency_key='sale-return:sellable-two:header')),1::bigint,'retry cannot duplicate refund');
select throws_ok($$select public.post_sale_return('sellable-two','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Changed',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',1,'disposition','sellable')),'cash')$$,'22023','idempotency key payload mismatch','changed retry payload is rejected');
select lives_ok($$select public.post_sale_return('sellable-last','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-full:header'),(timezone('Asia/Kathmandu',now()))::date,'Last unit',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-full:header')),'quantity',1,'disposition','sellable')),'cash')$$,'post final partial return');
reset role;
select is((select status from public.sales where idempotency_key='sale:return-source-full:header'),'returned'::public.sale_status,'all returned quantity closes sale');
select is((select current_stock from public.products where id='a4200000-0000-4000-8000-000000000001'),4,'damaged unit remains excluded after full return');
select results_eq($$select id,remaining_quantity from public.inventory_lots where product_id='a4200000-0000-4000-8000-000000000001' order by id$$,$$select * from (values('a4300000-0000-4000-8000-000000000001'::uuid,2),('a4300000-0000-4000-8000-000000000002'::uuid,2)) expected(id,remaining_quantity) order by id$$,'final sellable unit restores remaining reverse allocation');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_sale_return('credit-wrong-refund','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-credit:header'),(timezone('Asia/Kathmandu',now()))::date,'Due reduction',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-credit:header')),'quantity',1,'disposition','sellable')),'cash')$$,'22023','refund is not payable','credit due must reduce before refund');
select lives_ok($$select public.post_sale_return('credit-due','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-credit:header'),(timezone('Asia/Kathmandu',now()))::date,'Due reduction',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-credit:header')),'quantity',1,'disposition','sellable')),null)$$,'day-30 credit return reduces due without refund');
reset role;
select is((select count(*) from public.refunds where sale_return_id=(select id from public.sale_returns where idempotency_key='sale-return:credit-due:header')),0::bigint,'due-only return creates no refund');
select is((select result->>'due_after_paisa' from private.sale_return_operation_requests where idempotency_key='credit-due'),'1000','credit due reduces first');
select is((select result->>'refund_paisa' from private.sale_return_operation_requests where idempotency_key='credit-due'),'0','no paid value is refunded while due absorbs return');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_sale_return('credit-refund','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-credit:header'),(timezone('Asia/Kathmandu',now()))::date,'Credit remainder',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-credit:header')),'quantity',2,'disposition','sellable')),'cash')$$,'credit return refunds only paid remainder');
reset role;
select is((select result->>'refund_paisa' from private.sale_return_operation_requests where idempotency_key='credit-refund'),'1000','refund is capped to effectively paid value');
select is((select result->>'due_after_paisa' from private.sale_return_operation_requests where idempotency_key='credit-refund'),'0','final credit return clears due');
select lives_ok($$select private.assert_sale_money_integrity((select id from public.sales where idempotency_key='sale:return-source-credit:header'))$$,'credit return/refund money integrity passes');
select lives_ok($$select private.assert_return_integrity(id) from public.sale_returns where shop_id='a4100000-0000-4000-8000-000000000001'$$,'all return allocation integrity checks pass');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_sale_return('expired','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-expired:header'),(timezone('Asia/Kathmandu',now()))::date,'Too late',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-expired:header')),'quantity',1,'disposition','sellable')),'cash')$$,'22023','sale return window has expired','day-31 return is rejected');
select throws_ok($$select public.post_sale_return('cross-shop','b4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-expired:header'),(timezone('Asia/Kathmandu',now()))::date,'Cross shop',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-expired:header')),'quantity',1,'disposition','sellable')),'cash')$$,'42501','not authorized','Owner cannot transact without membership in intended shop');
select set_config('request.jwt.claim.sub','20410000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.post_sale_return('salesman-denied','a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-expired:header'),(timezone('Asia/Kathmandu',now()))::date,'Forbidden',jsonb_build_array(jsonb_build_object('sale_line_id',(select id from public.sale_lines where sale_id=(select id from public.sales where idempotency_key='sale:return-source-expired:header')),'quantity',1,'disposition','sellable')),'cash')$$,'42501','not authorized','Salesman cannot post returns');
reset role;
select is((select count(*) from public.sale_returns where sale_id=(select id from public.sales where idempotency_key='sale:return-source-expired:header')),0::bigint,'denied window and role attempts leave no return');
select is((select current_stock from public.products where id='a4200000-0000-4000-8000-000000000003'),0,'denied attempts leave stock unchanged');
select is((select count(*) from private.sale_return_operation_requests where completed_at is null),0::bigint,'failed returns leave no incomplete request row');
select ok(not exists(select 1 from public.inventory_lots where remaining_quantity>original_quantity),'restoration never exceeds original lot quantity');
select ok(not exists(select 1 from public.sale_return_allocations returned_allocation join public.sale_lot_allocations original on original.id=returned_allocation.sale_lot_allocation_id group by original.id,original.quantity having sum(returned_allocation.quantity)>original.quantity),'cumulative return allocations never exceed sale allocations');
select is((select count(*) from public.notifications where category='return' and shop_id='a4100000-0000-4000-8000-000000000001'),5::bigint,'each successful return emits one safe notification');
select is((select count(*) from private.business_audit_events where record_type='sale_return' and shop_id='a4100000-0000-4000-8000-000000000001'),5::bigint,'each successful return emits one safe audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','10410000-0000-4000-8000-000000000001',true);
select throws_ok($$insert into public.sale_returns(shop_id,sale_id,reason,total_value_paisa,business_date,actor_user_id,idempotency_key) values('a4100000-0000-4000-8000-000000000001',(select id from public.sales where idempotency_key='sale:return-source-expired:header'),'Forged',1,(timezone('Asia/Kathmandu',now()))::date,'10410000-0000-4000-8000-000000000001','forged')$$,'42501','permission denied for table sale_returns','direct return writes remain denied');
reset role;

select * from finish();
rollback;
