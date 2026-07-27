begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(37);

select ok(has_function_privilege('authenticated','public.get_business_report(uuid,date,date)','execute'),'authenticated may call period report');
select ok(has_function_privilege('authenticated','public.get_dashboard_report(uuid,timestamptz)','execute'),'authenticated may call daily dashboard');
select ok(not has_function_privilege('authenticated','private.report_actor_role(uuid)','execute') and not has_function_privilege('authenticated','private.nepal_business_date(timestamptz)','execute'),'private report helpers remain unavailable');
select ok(exists(select 1 from pg_indexes where schemaname='public' and indexname='sale_returns_shop_business_date_idx'),'sale return period index exists');
select is(private.nepal_business_date('2026-07-20 18:14:59+00'::timestamptz),'2026-07-20'::date,'instant before Nepal midnight remains prior date');
select is(private.nepal_business_date('2026-07-20 18:15:00+00'::timestamptz),'2026-07-21'::date,'instant at Nepal midnight advances date');

insert into public.shops(id,slug,display_name) values
 ('a5800000-0000-4000-8000-000000000001','report-shop-a','Report Shop A'),
 ('b5800000-0000-4000-8000-000000000001','report-shop-b','Report Shop B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10580000-0000-4000-8000-000000000001','authenticated','authenticated','report-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20580000-0000-4000-8000-000000000002','authenticated','authenticated','report-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30580000-0000-4000-8000-000000000003','authenticated','authenticated','report-disabled-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','40580000-0000-4000-8000-000000000004','authenticated','authenticated','report-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,disabled) values
 ('10580000-0000-4000-8000-000000000001','report.owner.a','Report Owner A',false),
 ('20580000-0000-4000-8000-000000000002','report.sales.a','Report Sales A',false),
 ('30580000-0000-4000-8000-000000000003','report.disabled.a','Report Disabled A',true),
 ('40580000-0000-4000-8000-000000000004','report.owner.b','Report Owner B',false);
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a5800000-0000-4000-8000-000000000001','10580000-0000-4000-8000-000000000001','owner'),
 ('a5800000-0000-4000-8000-000000000001','20580000-0000-4000-8000-000000000002','salesman'),
 ('a5800000-0000-4000-8000-000000000001','30580000-0000-4000-8000-000000000003','owner'),
 ('b5800000-0000-4000-8000-000000000001','40580000-0000-4000-8000-000000000004','owner');
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a5900000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b5900000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.products(id,shop_id,sku_code,name,low_stock_threshold,default_selling_price_paisa,current_stock) values
 ('a5a00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','REPORT-A1','Report Low Product',5,500,5),
 ('a5a00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','REPORT-A2','Report Healthy Product',1,700,2),
 ('a5a00000-0000-4000-8000-000000000003','a5800000-0000-4000-8000-000000000001','REPORT-PURCHASE','Report Purchase Product',0,1500,0),
 ('b5a00000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001','REPORT-B1','Other Shop Product',10,999,9);
insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,received_at,unit_cost_paisa,original_quantity,remaining_quantity) values
 ('a5b00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a5a00000-0000-4000-8000-000000000001','opening_balance','report-a1',now()-interval '3 days',100,6,5),
 ('a5b00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','a5a00000-0000-4000-8000-000000000002','opening_balance','report-a2',now()-interval '2 days',200,2,2),
 ('b5b00000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001','b5a00000-0000-4000-8000-000000000001','opening_balance','report-b1',now(),999,9,9);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a5c00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','Report Cash','cash','debit','cash_main',true),
 ('a5c00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','Report Bank','bank','debit','bank_main',true),
 ('a5c00000-0000-4000-8000-000000000003','a5800000-0000-4000-8000-000000000001','Report Expense','expense','debit','expense_control',true),
 ('a5c00000-0000-4000-8000-000000000004','a5800000-0000-4000-8000-000000000001','Report Equity','equity','credit','opening_equity',true),
 ('a5c00000-0000-4000-8000-000000000005','a5800000-0000-4000-8000-000000000001','Report Inventory','inventory','debit','inventory_control',true),
 ('a5c00000-0000-4000-8000-000000000006','a5800000-0000-4000-8000-000000000001','Report Payable','payable','credit','accounts_payable',true);

insert into public.sales(id,shop_id,status,is_credit,customer_name,customer_contact,due_date,subtotal_paisa,grand_total_paisa,business_date,actor_user_id,idempotency_key,posted_at) values
 ('a5d00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','partially_returned',true,'Report Credit A','9800000001','2026-07-31',1000,1000,'2026-07-20','10580000-0000-4000-8000-000000000001','report-sale-period',now()),
 ('a5d00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','posted',true,'Report Credit Before','9800000002','2026-07-31',500,500,'2026-07-19','10580000-0000-4000-8000-000000000001','report-sale-before',now()),
 ('b5d00000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001','posted',true,'Report Credit B','9800000003','2026-07-31',999,999,'2026-07-20','40580000-0000-4000-8000-000000000004','report-sale-other',now());
insert into public.sale_lines(id,shop_id,sale_id,line_number,product_id,product_name,sku_code,quantity,configured_unit_price_paisa,effective_unit_price_paisa,gross_total_paisa,line_total_paisa) values
 ('a5e00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a5d00000-0000-4000-8000-000000000001',1,'a5a00000-0000-4000-8000-000000000001','Report Low Product','REPORT-A1',2,500,500,1000,1000),
 ('a5e00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','a5d00000-0000-4000-8000-000000000002',1,'a5a00000-0000-4000-8000-000000000002','Report Healthy Product','REPORT-A2',1,500,500,500,500),
 ('b5e00000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001','b5d00000-0000-4000-8000-000000000001',1,'b5a00000-0000-4000-8000-000000000001','Other Shop Product','REPORT-B1',1,999,999,999,999);
insert into public.sale_lot_allocations(id,shop_id,sale_line_id,product_id,lot_id,quantity,unit_cost_paisa) values
 ('a5f00000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a5e00000-0000-4000-8000-000000000001','a5a00000-0000-4000-8000-000000000001','a5b00000-0000-4000-8000-000000000001',2,100),
 ('a5f00000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','a5e00000-0000-4000-8000-000000000002','a5a00000-0000-4000-8000-000000000002','a5b00000-0000-4000-8000-000000000002',1,200),
 ('b5f00000-0000-4000-8000-000000000001','b5800000-0000-4000-8000-000000000001','b5e00000-0000-4000-8000-000000000001','b5a00000-0000-4000-8000-000000000001','b5b00000-0000-4000-8000-000000000001',1,999);
insert into public.sale_returns(id,shop_id,sale_id,status,reason,total_value_paisa,business_date,actor_user_id,idempotency_key,posted_at) values
 ('a6000000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a5d00000-0000-4000-8000-000000000001','posted','Report return',400,'2026-07-21','10580000-0000-4000-8000-000000000001','report-return',now());
insert into public.sale_return_lines(id,shop_id,sale_return_id,sale_id,sale_line_id,quantity,disposition,refund_value_paisa) values
 ('a6100000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a6000000-0000-4000-8000-000000000001','a5d00000-0000-4000-8000-000000000001','a5e00000-0000-4000-8000-000000000001',1,'sellable',400);
insert into public.sale_return_allocations(id,shop_id,sale_return_line_id,sale_line_id,sale_lot_allocation_id,quantity) values
 ('a6200000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','a6100000-0000-4000-8000-000000000001','a5e00000-0000-4000-8000-000000000001','a5f00000-0000-4000-8000-000000000001',1);

insert into public.journal_transactions(id,shop_id,kind,description,source_id,reversal_of_id,business_date,actor_user_id,idempotency_key) values
 ('a6300000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','opening_balance','Opening cash',null,null,'2026-07-19','10580000-0000-4000-8000-000000000001','report-opening'),
 ('a6300000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','expense','Reversed expense','a6400000-0000-4000-8000-000000000001',null,'2026-07-20','10580000-0000-4000-8000-000000000001','report-expense-reversed'),
 ('a6300000-0000-4000-8000-000000000003','a5800000-0000-4000-8000-000000000001','reversal','Reverse expense',null,'a6300000-0000-4000-8000-000000000002','2026-07-20','10580000-0000-4000-8000-000000000001','report-expense-reversal'),
 ('a6300000-0000-4000-8000-000000000004','a5800000-0000-4000-8000-000000000001','expense','Active expense','a6400000-0000-4000-8000-000000000002',null,'2026-07-21','10580000-0000-4000-8000-000000000001','report-expense-active'),
 ('a6300000-0000-4000-8000-000000000005','a5800000-0000-4000-8000-000000000001','transfer','Cash to bank',null,null,'2026-07-21','10580000-0000-4000-8000-000000000001','report-transfer');
insert into public.expenses(id,shop_id,category,amount_paisa,journal_transaction_id,business_date,actor_user_id,idempotency_key) values
 ('a6400000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','Reversed',500,'a6300000-0000-4000-8000-000000000002','2026-07-20','10580000-0000-4000-8000-000000000001','report-expense-reversed-header'),
 ('a6400000-0000-4000-8000-000000000002','a5800000-0000-4000-8000-000000000001','Active',300,'a6300000-0000-4000-8000-000000000004','2026-07-21','10580000-0000-4000-8000-000000000001','report-expense-active-header');
insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000001',1,'a5c00000-0000-4000-8000-000000000001',2000,0),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000001',2,'a5c00000-0000-4000-8000-000000000004',0,2000),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000002',1,'a5c00000-0000-4000-8000-000000000003',500,0),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000002',2,'a5c00000-0000-4000-8000-000000000001',0,500),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000003',1,'a5c00000-0000-4000-8000-000000000003',0,500),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000003',2,'a5c00000-0000-4000-8000-000000000001',500,0),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000004',1,'a5c00000-0000-4000-8000-000000000003',300,0),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000004',2,'a5c00000-0000-4000-8000-000000000001',0,300),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000005',1,'a5c00000-0000-4000-8000-000000000002',200,0),
 ('a5800000-0000-4000-8000-000000000001','a6300000-0000-4000-8000-000000000005',2,'a5c00000-0000-4000-8000-000000000001',0,200);

insert into public.vendors(id,shop_id,display_name) values
 ('a6500000-0000-4000-8000-000000000001','a5800000-0000-4000-8000-000000000001','Report Vendor');
set local role authenticated;
select set_config('request.jwt.claim.sub','10580000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_purchase_receipt('report-purchase','a5800000-0000-4000-8000-000000000001','a6500000-0000-4000-8000-000000000001','REPORT-INVOICE',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','a5a00000-0000-4000-8000-000000000003','quantity',1,'unit_cost_paisa',1000)),0,null)$$,'create genuine unpaid purchase for report');
reset role;
set constraints all immediate;

set local role authenticated;
select set_config('request.jwt.claim.sub','10580000-0000-4000-8000-000000000001',true);
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'sales_total_paisa')::bigint,1000::bigint,'Owner period sales reconcile');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'returns_total_paisa')::bigint,400::bigint,'Owner returns reconcile');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'net_sales_paisa')::bigint,600::bigint,'Owner net sales reconcile');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'cost_of_goods_sold_paisa')::bigint,100::bigint,'FIFO cost nets exact return restoration');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'gross_profit_paisa')::bigint,500::bigint,'gross profit reconciles to net sales minus net FIFO cost');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'stock_on_hand_quantity')::bigint,8::bigint,'stock quantity reconciles to product projections');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'stock_value_paisa')::bigint,1900::bigint,'stock value reconciles to remaining FIFO lots');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'low_stock_count')::integer,1,'low stock count includes only active threshold breach');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'low_stock_products'->0->>'sku_code'),'REPORT-A1','low stock detail is authoritative');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'vendor_due_total_paisa')::bigint,1000::bigint,'vendor due reconciles to unpaid purchase');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'vendor_dues'->0->>'vendor_name'),'Report Vendor','vendor due detail is grouped');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'expenses_total_paisa')::bigint,300::bigint,'reversed expense is excluded');
select is((select (item->>'balance_paisa')::bigint from jsonb_array_elements(public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'account_balances') item where item->>'account_type'='cash'),1500::bigint,'cash balance derives all journals and reversal');
select is((select (item->>'balance_paisa')::bigint from jsonb_array_elements(public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'account_balances') item where item->>'account_type'='bank'),200::bigint,'bank balance derives transfer');
select is((public.get_dashboard_report('a5800000-0000-4000-8000-000000000001','2026-07-20 18:14:59+00')->>'sales_total_paisa')::bigint,1000::bigint,'daily wrapper uses pre-midnight Nepal date');
select is((public.get_dashboard_report('a5800000-0000-4000-8000-000000000001','2026-07-20 18:15:00+00')->>'returns_total_paisa')::bigint,400::bigint,'daily wrapper advances exactly at Nepal midnight');
select throws_ok($$select public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-21','2026-07-20')$$,'22023','invalid report date range','reverse date range fails');
select throws_ok($$select public.get_business_report('a5800000-0000-4000-8000-000000000001','2025-01-01','2026-07-20')$$,'22023','invalid report date range','range over 366 days fails');
select throws_ok($$select public.get_business_report('b5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')$$,'42501','report is not available','cross-shop report is denied');
select set_config('request.jwt.claim.sub','20580000-0000-4000-8000-000000000002',true);
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'sales_total_paisa')::bigint,1000::bigint,'Salesman receives permitted sales');
select ok(not (public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21') ?| array['cost_of_goods_sold_paisa','gross_profit_paisa','stock_value_paisa','vendor_due_total_paisa','vendor_dues','account_balances','expenses_total_paisa']),'Salesman response omits every cost vendor and finance field');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'low_stock_count')::integer,1,'Salesman receives non-cost low stock count');
select set_config('request.jwt.claim.sub','30580000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')$$,'42501','report is not available','disabled Owner is denied');
reset role;

create function pg_temp.report_explain(query_text text) returns jsonb language plpgsql as $$
declare plan jsonb; begin execute 'explain (format json, costs off) '||query_text into plan; return plan; end $$;
set local enable_seqscan=off;
select matches(pg_temp.report_explain($$select sum(grand_total_paisa) from public.sales where shop_id='a5800000-0000-4000-8000-000000000001' and business_date between '2026-07-20' and '2026-07-21'$$)::text,'sales_shop_business_date_idx','sales period plan uses existing date index');
select matches(pg_temp.report_explain($$select sum(total_value_paisa) from public.sale_returns where shop_id='a5800000-0000-4000-8000-000000000001' and status='posted' and business_date between '2026-07-20' and '2026-07-21'$$)::text,'sale_returns_shop_business_date_idx','return period plan uses new date index');
select matches(pg_temp.report_explain($$select sum(amount_paisa) from public.expenses where shop_id='a5800000-0000-4000-8000-000000000001' and business_date between '2026-07-20' and '2026-07-21'$$)::text,'expenses_shop_date_category_idx','expense period plan uses existing date index');
select matches(pg_temp.report_explain($$select sum(debit_paisa-credit_paisa) from public.journal_entries where shop_id='a5800000-0000-4000-8000-000000000001' and financial_account_id='a5c00000-0000-4000-8000-000000000001'$$)::text,'journal_entries_account_timeline_idx','account balance plan uses existing account index');
select is((select count(*) from jsonb_array_elements(public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'vendor_dues')),1::bigint,'zero-due vendors are omitted');
select is((public.get_business_report('a5800000-0000-4000-8000-000000000001','2026-07-22','2026-07-22')->>'sales_total_paisa')::bigint,0::bigint,'empty period returns numeric zero');
set local role authenticated;
select set_config('request.jwt.claim.sub','40580000-0000-4000-8000-000000000004',true);
select ok((public.get_business_report('b5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->>'sales_total_paisa')::bigint=999 and (public.get_business_report('b5800000-0000-4000-8000-000000000001','2026-07-20','2026-07-21')->'low_stock_products'->0->>'sku_code')='REPORT-B1','Owner B sees only own-shop report data');
reset role;

select * from finish();
rollback;
