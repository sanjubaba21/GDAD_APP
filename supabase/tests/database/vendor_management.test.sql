begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(26);

select ok(to_regclass('private.vendor_operation_requests') is not null,'vendor request ledger exists');
select ok((select relrowsecurity from pg_class where oid='private.vendor_operation_requests'::regclass),'vendor request ledger has RLS');
select ok(not has_table_privilege('authenticated','private.vendor_operation_requests','select'),'clients cannot read vendor requests');
select ok(has_function_privilege('authenticated','public.manage_vendor(text,text,uuid,uuid,text,text,text,text)','execute'),'authenticated may call protected vendor RPC');
select ok(not has_table_privilege('authenticated','public.vendors','insert') and not has_table_privilege('authenticated','public.vendors','update') and not has_table_privilege('authenticated','public.vendors','delete'),'direct vendor writes remain denied');

insert into public.shops(id,slug,display_name) values
 ('a8100000-0000-4000-8000-000000000001','vendor-rpc-a','Vendor RPC A'),
 ('b8100000-0000-4000-8000-000000000001','vendor-rpc-b','Vendor RPC B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10810000-0000-4000-8000-000000000001','authenticated','authenticated','vendor-admin@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20810000-0000-4000-8000-000000000002','authenticated','authenticated','vendor-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-8000-000000000000','30810000-0000-4000-8000-000000000003','authenticated','authenticated','vendor-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-8000-000000000000','40810000-0000-4000-8000-000000000004','authenticated','authenticated','vendor-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,platform_role) values
 ('10810000-0000-4000-8000-000000000001','vendor.rpc.admin','Vendor Admin','super_admin'),
 ('20810000-0000-4000-8000-000000000002','vendor.rpc.owner.a','Vendor Owner A','standard'),
 ('30810000-0000-4000-8000-000000000003','vendor.rpc.sales.a','Vendor Sales A','standard'),
 ('40810000-0000-4000-8000-000000000004','vendor.rpc.owner.b','Vendor Owner B','standard');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a8100000-0000-4000-8000-000000000001','20810000-0000-4000-8000-000000000002','owner'),
 ('a8100000-0000-4000-8000-000000000001','30810000-0000-4000-8000-000000000003','salesman'),
 ('b8100000-0000-4000-8000-000000000001','40810000-0000-4000-8000-000000000004','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub','20810000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_vendor('vendor-create-1','create','a8100000-0000-4000-8000-000000000001',null,' Kathmandu Bags ',' 9800000000 ',' PAN-1 ',' Primary supplier ')$$,'Owner creates vendor');
reset role;
select is((select count(*) from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),1::bigint,'create inserts one vendor');
select results_eq($$select display_name,phone,tax_reference,notes,active from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'$$,$$values('Kathmandu Bags'::text,'9800000000'::text,'PAN-1'::text,'Primary supplier'::text,true)$$,'create trims fields');
select is((select count(*) from private.business_audit_events where record_type='vendor' and shop_id='a8100000-0000-4000-8000-000000000001'),1::bigint,'create writes one audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20810000-0000-4000-8000-000000000002',true);
select is((public.manage_vendor('vendor-create-1','create','a8100000-0000-4000-8000-000000000001',null,' Kathmandu Bags ',' 9800000000 ',' PAN-1 ',' Primary supplier ')->>'id')::uuid,(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),'exact retry returns same vendor');
reset role;
select is((select count(*) from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),1::bigint,'retry does not duplicate vendor');
select is((select count(*) from private.business_audit_events where record_type='vendor' and shop_id='a8100000-0000-4000-8000-000000000001'),1::bigint,'retry does not duplicate audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20810000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.manage_vendor('vendor-create-1','create','a8100000-0000-4000-8000-000000000001',null,'Changed',null,null,null)$$,'22023','idempotency key payload mismatch','changed retry is rejected');
select throws_ok($$select public.manage_vendor('vendor-invalid','create','a8100000-0000-4000-8000-000000000001',null,' ',null,null,null)$$,'22023','invalid vendor fields','blank name is rejected');
select set_config('request.jwt.claim.sub','30810000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.manage_vendor('vendor-sales','create','a8100000-0000-4000-8000-000000000001',null,'Forged',null,null,null)$$,'42501','not authorized','Salesman cannot create vendor');
select set_config('request.jwt.claim.sub','10810000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.manage_vendor('vendor-admin','create','a8100000-0000-4000-8000-000000000001',null,'Forged',null,null,null)$$,'42501','not authorized','Super Admin cannot bypass Owner role');
select set_config('request.jwt.claim.sub','20810000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.manage_vendor('vendor-cross','update','b8100000-0000-4000-8000-000000000001',(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),'Cross',null,null,null)$$,'42501','not authorized','Owner cannot mutate another shop');
select lives_ok($$select public.manage_vendor('vendor-update-1','update','a8100000-0000-4000-8000-000000000001',(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),'Updated Vendor','9811111111',null,null)$$,'Owner updates active vendor');
reset role;
select results_eq($$select display_name,phone,tax_reference,notes from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'$$,$$values('Updated Vendor'::text,'9811111111'::text,null::text,null::text)$$,'update replaces optional fields');
select is((select count(*) from private.business_audit_events where record_type='vendor' and shop_id='a8100000-0000-4000-8000-000000000001'),2::bigint,'update writes one audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20810000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_vendor('vendor-archive-1','archive','a8100000-0000-4000-8000-000000000001',(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'))$$,'Owner archives vendor');
select throws_ok($$select public.manage_vendor('vendor-update-archived','update','a8100000-0000-4000-8000-000000000001',(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),'Nope',null,null,null)$$,'55000','archived vendor cannot be updated','archived vendor cannot be edited');
select throws_ok($$select public.manage_vendor('vendor-archive-again','archive','a8100000-0000-4000-8000-000000000001',(select id from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'))$$,'55000','vendor is already archived','archive is not repeated under a new key');
reset role;
select is((select active from public.vendors where shop_id='a8100000-0000-4000-8000-000000000001'),false,'archive preserves historical row');
select is((select count(*) from private.business_audit_events where record_type='vendor' and shop_id='a8100000-0000-4000-8000-000000000001'),3::bigint,'archive writes one audit');
select is((select count(*) from private.vendor_operation_requests where shop_id='a8100000-0000-4000-8000-000000000001' and completed_at is not null),3::bigint,'only successful unique operations complete');

select * from finish();
rollback;
