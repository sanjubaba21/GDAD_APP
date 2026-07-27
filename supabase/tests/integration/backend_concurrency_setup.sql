begin;
set local app.seed_mode='on';

insert into public.shops(id,slug,display_name) values
 ('a6900000-0000-4000-8000-000000000001','integration-race-a','Integration Race A'),
 ('b6900000-0000-4000-8000-000000000001','integration-race-b','Integration Race B');
select private.ensure_shop_financial_accounts('a6900000-0000-4000-8000-000000000001');
select private.ensure_shop_financial_accounts('b6900000-0000-4000-8000-000000000001');

insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10690000-0000-4000-8000-000000000001','authenticated','authenticated','integration-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20690000-0000-4000-8000-000000000002','authenticated','authenticated','integration-disabled-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30690000-0000-4000-8000-000000000003','authenticated','authenticated','integration-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,disabled) values
 ('10690000-0000-4000-8000-000000000001','integration.owner.a','Integration Owner A',false),
 ('20690000-0000-4000-8000-000000000002','integration.disabled.a','Integration Disabled A',true),
 ('30690000-0000-4000-8000-000000000003','integration.owner.b','Integration Owner B',false);
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a6900000-0000-4000-8000-000000000001','10690000-0000-4000-8000-000000000001','owner'),
 ('a6900000-0000-4000-8000-000000000001','20690000-0000-4000-8000-000000000002','owner'),
 ('b6900000-0000-4000-8000-000000000001','30690000-0000-4000-8000-000000000003','owner');
insert into public.accounting_periods(id,shop_id,date_from,date_to) values
 ('a6a00000-0000-4000-8000-000000000001','a6900000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date),
 ('b6a00000-0000-4000-8000-000000000001','b6900000-0000-4000-8000-000000000001',(timezone('Asia/Kathmandu',now()))::date-7,(timezone('Asia/Kathmandu',now()))::date);

insert into public.products(id,shop_id,sku_code,name,low_stock_threshold,default_selling_price_paisa,current_stock) values
 ('a6b00000-0000-4000-8000-000000000001','a6900000-0000-4000-8000-000000000001','RACE-ONE','Race One Unit',0,1000,1),
 ('a6b00000-0000-4000-8000-000000000002','a6900000-0000-4000-8000-000000000001','RETRY-TWO','Retry Two Units',0,1000,2),
 ('a6b00000-0000-4000-8000-000000000003','a6900000-0000-4000-8000-000000000001','PURCHASE-ONE','Purchase One Unit',0,1200,0),
 ('b6b00000-0000-4000-8000-000000000001','b6900000-0000-4000-8000-000000000001','OTHER-ONE','Other Shop Unit',0,1000,1);
insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,received_at,unit_cost_paisa,original_quantity,remaining_quantity) values
 ('a6c00000-0000-4000-8000-000000000001','a6900000-0000-4000-8000-000000000001','a6b00000-0000-4000-8000-000000000001','opening_balance','race-one',now()-interval '2 days',400,1,1),
 ('a6c00000-0000-4000-8000-000000000002','a6900000-0000-4000-8000-000000000001','a6b00000-0000-4000-8000-000000000002','opening_balance','retry-two',now()-interval '1 day',500,2,2),
 ('b6c00000-0000-4000-8000-000000000001','b6900000-0000-4000-8000-000000000001','b6b00000-0000-4000-8000-000000000001','opening_balance','other-one',now(),300,1,1);
insert into public.vendors(id,shop_id,display_name) values
 ('a6d00000-0000-4000-8000-000000000001','a6900000-0000-4000-8000-000000000001','Concurrency Vendor');

set local role authenticated;
select set_config('request.jwt.claim.sub','10690000-0000-4000-8000-000000000001',true);
select public.post_purchase_receipt('integration-purchase','a6900000-0000-4000-8000-000000000001','a6d00000-0000-4000-8000-000000000001','CONCURRENT-INVOICE',(timezone('Asia/Kathmandu',now()))::date,jsonb_build_array(jsonb_build_object('product_id','a6b00000-0000-4000-8000-000000000003','quantity',1,'unit_cost_paisa',1000)),0,null);
reset role;
set constraints all immediate;
commit;
