begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(51);

select ok('inventory_adjustment' = any(enum_range(null::public.journal_kind)::text[]),'inventory adjustment journal kind exists');
select ok(to_regclass('public.inventory_adjustments') is not null,'inventory adjustment source exists');
select ok(to_regclass('private.inventory_adjustment_operation_requests') is not null,'adjustment idempotency state exists');
select ok((select relrowsecurity from pg_class where oid='public.inventory_adjustments'::regclass),'adjustments have RLS');
select ok((select relrowsecurity from pg_class where oid='private.inventory_adjustment_operation_requests'::regclass),'adjustment requests have RLS');
select ok(not has_table_privilege('authenticated','private.inventory_adjustment_operation_requests','select'),'clients cannot read adjustment request internals');
select ok(has_function_privilege('authenticated','public.post_inventory_adjustment(text,uuid,uuid,public.inventory_movement_type,public.inventory_adjustment_reason,integer,uuid,bigint,date,text)','execute'),'authenticated may call protected adjustment RPC');
select ok(not has_table_privilege('authenticated','public.inventory_adjustments','insert') and not has_table_privilege('authenticated','public.inventory_lots','update') and not has_table_privilege('authenticated','public.inventory_movements','insert'),'direct adjustment and stock writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a4600000-0000-4000-8000-000000000001','atomic-adjust-a','Atomic Adjust A'),
 ('b4600000-0000-4000-8000-000000000001','atomic-adjust-b','Atomic Adjust B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10460000-0000-4000-8000-000000000001','authenticated','authenticated','adjust-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20460000-0000-4000-8000-000000000002','authenticated','authenticated','adjust-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30460000-0000-4000-8000-000000000003','authenticated','authenticated','adjust-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name) values
 ('10460000-0000-4000-8000-000000000001','adjust.owner.a','Adjust Owner A'),
 ('20460000-0000-4000-8000-000000000002','adjust.sales.a','Adjust Sales A'),
 ('30460000-0000-4000-8000-000000000003','adjust.owner.b','Adjust Owner B');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a4600000-0000-4000-8000-000000000001','10460000-0000-4000-8000-000000000001','owner'),
 ('a4600000-0000-4000-8000-000000000001','20460000-0000-4000-8000-000000000002','salesman'),
 ('b4600000-0000-4000-8000-000000000001','30460000-0000-4000-8000-000000000003','owner');
insert into public.products(id,shop_id,sku_code,name,default_selling_price_paisa,current_stock) values
 ('a4700000-0000-4000-8000-000000000001','a4600000-0000-4000-8000-000000000001','ADJUST-A1','Adjust Product A1',1000,5),
 ('b4700000-0000-4000-8000-000000000001','b4600000-0000-4000-8000-000000000001','ADJUST-B1','Adjust Product B1',1000,2);
insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,received_at,unit_cost_paisa,original_quantity,remaining_quantity) values
 ('a4800000-0000-4000-8000-000000000001','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','opening_balance','adjust-old',now()-interval '2 days',100,3,3),
 ('a4800000-0000-4000-8000-000000000002','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','opening_balance','adjust-new',now()-interval '1 day',200,2,2),
 ('b4800000-0000-4000-8000-000000000001','b4600000-0000-4000-8000-000000000001','b4700000-0000-4000-8000-000000000001','opening_balance','adjust-b',now(),100,2,2);
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a4900000-0000-4000-8000-000000000001','a4600000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b4900000-0000-4000-8000-000000000001','b4600000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);
insert into public.financial_accounts(id,shop_id,display_name,account_type,normal_side,purpose_code,system_managed) values
 ('a4a00000-0000-4000-8000-000000000001','a4600000-0000-4000-8000-000000000001','Inventory','inventory','debit','inventory_control',true),
 ('a4a00000-0000-4000-8000-000000000002','a4600000-0000-4000-8000-000000000001','Adjustment Control','clearing','debit','inventory_adjustment_control',true);

set local role authenticated;
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_inventory_adjustment('found-three','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',3,null,150,(timezone('Asia/Kathmandu',now()))::date,'Counted sealed cartons')$$,'Owner adds found stock as new FIFO layer');
reset role;
select is((select count(*) from public.inventory_adjustments where idempotency_key='inventory-adjustment:found-three:header'),1::bigint,'addition source is created');
select results_eq($$select movement_type,reason_code,quantity,quantity_delta,unit_cost_paisa,total_cost_paisa from public.inventory_adjustments where idempotency_key='inventory-adjustment:found-three:header'$$,$$select 'manual_add'::public.inventory_movement_type,'stock_found'::public.inventory_adjustment_reason,3,3,150::bigint,450::bigint$$,'addition source records exact direction and cost');
select is((select current_stock from public.products where id='a4700000-0000-4000-8000-000000000001'),8,'addition updates stock projection');
select is((select original_quantity from public.inventory_lots where source_type='inventory_adjustment'),3,'addition creates new lot with exact original quantity');
select is((select remaining_quantity from public.inventory_lots where source_type='inventory_adjustment'),3,'addition creates new lot with exact remaining quantity');
select is((select unit_cost_paisa from public.inventory_lots where source_type='inventory_adjustment'),150::bigint,'addition creates new lot with supplied cost');
select is((select quantity_delta from public.inventory_movements where idempotency_key='inventory-adjustment:found-three:movement'),3,'addition appends positive movement');
select ok((select private.journal_source_exists('inventory_adjustment',shop_id,id) from public.inventory_adjustments where idempotency_key='inventory-adjustment:found-three:header'),'adjustment journal has typed source');

set local role authenticated;
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select is((public.post_inventory_adjustment('found-three','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',3,null,150,(timezone('Asia/Kathmandu',now()))::date,'Counted sealed cartons')->>'inventory_adjustment_id')::uuid,(select id from public.inventory_adjustments where idempotency_key='inventory-adjustment:found-three:header'),'exact retry returns original adjustment');
select is((select count(*) from public.inventory_lots where source_type='inventory_adjustment'),1::bigint,'retry cannot duplicate added lot');
select throws_ok($$select public.post_inventory_adjustment('found-three','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',4,null,150,(timezone('Asia/Kathmandu',now()))::date,'Changed')$$,'22023','idempotency key payload mismatch','changed retry payload is rejected');
select lives_ok($$select public.post_inventory_adjustment('damage-two','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','damage','damaged',2,'a4800000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'Water damage')$$,'Owner removes damaged units from source lot');
reset role;
select is((select remaining_quantity from public.inventory_lots where id='a4800000-0000-4000-8000-000000000001'),1,'damage decrements specified lot');
select is((select current_stock from public.products where id='a4700000-0000-4000-8000-000000000001'),6,'damage decrements stock projection');
select results_eq($$select quantity_delta,unit_cost_paisa,total_cost_paisa from public.inventory_adjustments where idempotency_key='inventory-adjustment:damage-two:header'$$,$$select -2,100::bigint,200::bigint$$,'damage derives exact lot cost');

set local role authenticated;
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_inventory_adjustment('loss-excess','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','loss','lost',2,'a4800000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'Missing')$$,'23514','adjustment exceeds available lot quantity','excessive loss is rejected');
reset role;
select is((select remaining_quantity from public.inventory_lots where id='a4800000-0000-4000-8000-000000000001'),1,'excess rollback leaves lot unchanged');
select is((select count(*) from public.inventory_adjustments where idempotency_key='inventory-adjustment:loss-excess:header'),0::bigint,'excess rollback leaves no source');

set local role authenticated;
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select lives_ok($$select public.post_inventory_adjustment('loss-one','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','loss','lost',1,'a4800000-0000-4000-8000-000000000001',null,(timezone('Asia/Kathmandu',now()))::date,'Missing after delivery')$$,'Owner posts exact remaining loss');
select lives_ok($$select public.post_inventory_adjustment('short-one','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_remove','count_shortage',1,'a4800000-0000-4000-8000-000000000002',null,(timezone('Asia/Kathmandu',now()))::date,'Cycle count')$$,'Owner posts count shortage');
select lives_ok($$select public.post_inventory_adjustment('zero-add','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','data_correction',2,null,0,(timezone('Asia/Kathmandu',now()))::date,'Free samples')$$,'zero-cost addition is valid without fabricated journal');
reset role;
select is((select current_stock from public.products where id='a4700000-0000-4000-8000-000000000001'),6,'all successful deltas reconcile to projection');
select is((select sum(quantity_delta) from public.inventory_movements where source_type='inventory_adjustment' and shop_id='a4600000-0000-4000-8000-000000000001'),1::bigint,'movement deltas reconcile to initial-to-current stock change');
select is((select count(*) from public.journal_transactions where kind='inventory_adjustment' and shop_id='a4600000-0000-4000-8000-000000000001'),4::bigint,'every positive-cost adjustment has one journal');
select is((select count(*) from public.journal_entries entry join public.journal_transactions journal on journal.id=entry.journal_transaction_id where journal.kind='inventory_adjustment' and journal.shop_id='a4600000-0000-4000-8000-000000000001'),8::bigint,'positive-cost adjustment journals have two entries');
select ok(not exists(select 1 from public.journal_entries entry join public.journal_transactions journal on journal.id=entry.journal_transaction_id where journal.kind='inventory_adjustment' group by journal.id having sum(entry.debit_paisa)<>sum(entry.credit_paisa)),'all adjustment journals balance');
select is((select journal_transaction_id from public.inventory_adjustments where idempotency_key='inventory-adjustment:zero-add:header'),null::uuid,'zero-cost addition has no money journal');
select is((select count(*) from public.notifications where category='system' and shop_id='a4600000-0000-4000-8000-000000000001'),5::bigint,'every successful adjustment notifies Owners');
select is((select count(*) from private.business_audit_events where record_type='inventory_adjustment' and shop_id='a4600000-0000-4000-8000-000000000001'),5::bigint,'every successful adjustment writes safe audit');
select results_eq($$select id,source_type,source_id,original_quantity from public.inventory_lots where id in('a4800000-0000-4000-8000-000000000001','a4800000-0000-4000-8000-000000000002') order by id$$,$$select * from (values('a4800000-0000-4000-8000-000000000001'::uuid,'opening_balance'::text,'adjust-old'::text,3),('a4800000-0000-4000-8000-000000000002'::uuid,'opening_balance'::text,'adjust-new'::text,2)) expected(id,source_type,source_id,original_quantity) order by id$$,'removals never rewrite receipt identity or original quantity');

set local role authenticated;
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.post_inventory_adjustment('wrong-reason','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','damage','lost',1,'a4800000-0000-4000-8000-000000000002',null,(timezone('Asia/Kathmandu',now()))::date,null)$$,'22023','adjustment reason does not match movement','reason must match movement');
select throws_ok($$select public.post_inventory_adjustment('remove-cost','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_remove','count_shortage',1,'a4800000-0000-4000-8000-000000000002',999,(timezone('Asia/Kathmandu',now()))::date,null)$$,'22023','stock reduction requires source lot and derived cost','removal cannot forge lot cost');
select throws_ok($$select public.post_inventory_adjustment('add-source','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',1,'a4800000-0000-4000-8000-000000000002',100,(timezone('Asia/Kathmandu',now()))::date,null)$$,'22023','manual addition requires unit cost without source lot','addition cannot rewrite existing lot');
select throws_ok($$select public.post_inventory_adjustment('cross-product','a4600000-0000-4000-8000-000000000001','b4700000-0000-4000-8000-000000000001','manual_add','stock_found',1,null,100,(timezone('Asia/Kathmandu',now()))::date,null)$$,'42501','product is not available','cross-shop product is denied');
select set_config('request.jwt.claim.sub','20460000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.post_inventory_adjustment('salesman-denied','a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',1,null,100,(timezone('Asia/Kathmandu',now()))::date,null)$$,'42501','not authorized','Salesman cannot adjust inventory');
select set_config('request.jwt.claim.sub','30460000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.post_inventory_adjustment('missing-accounts','b4600000-0000-4000-8000-000000000001','b4700000-0000-4000-8000-000000000001','manual_add','stock_found',1,null,100,(timezone('Asia/Kathmandu',now()))::date,null)$$,'55000','inventory adjustment accounts are unavailable','missing accounts fail atomically');
reset role;
select is((select current_stock from public.products where id='b4700000-0000-4000-8000-000000000001'),2,'configuration failure leaves stock unchanged');
select is((select count(*) from public.inventory_adjustments where shop_id='b4600000-0000-4000-8000-000000000001'),0::bigint,'configuration failure leaves no adjustment source');
select is((select count(*) from private.inventory_adjustment_operation_requests where completed_at is null),0::bigint,'failed operations leave no incomplete request rows');
select ok(not exists(select 1 from public.inventory_lots where remaining_quantity<0 or remaining_quantity>original_quantity),'all lot balances remain valid');

set local role authenticated;
select set_config('request.jwt.claim.sub','20460000-0000-4000-8000-000000000002',true);
select is((select count(*) from public.inventory_adjustments),0::bigint,'Salesman cannot read Owner-only adjustment cost records');
select set_config('request.jwt.claim.sub','10460000-0000-4000-8000-000000000001',true);
select throws_ok($$insert into public.inventory_adjustments(id,shop_id,product_id,movement_type,reason_code,created_lot_id,quantity,quantity_delta,unit_cost_paisa,total_cost_paisa,business_date,actor_user_id,idempotency_key) values(gen_random_uuid(),'a4600000-0000-4000-8000-000000000001','a4700000-0000-4000-8000-000000000001','manual_add','stock_found',gen_random_uuid(),1,1,1,1,(timezone('Asia/Kathmandu',now()))::date,'10460000-0000-4000-8000-000000000001','forged')$$,'42501','permission denied for table inventory_adjustments','direct adjustment writes remain denied');
reset role;

select * from finish();
rollback;
