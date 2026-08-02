begin;
create extension if not exists pgtap with schema extensions;
set local search_path=public,extensions;
select plan(45);

select ok((select count(*) from information_schema.columns where table_schema='public' and table_name='products' and column_name in ('barcode','normalized_sku','normalized_barcode'))=3,'product normalized code columns exist');
select ok((select count(*) from pg_indexes where schemaname='public' and tablename='products' and indexname in ('products_shop_normalized_sku_unique','products_shop_normalized_barcode_unique'))=2,'normalized product code indexes exist');
select ok(to_regclass('private.product_code_reservations') is not null and to_regclass('private.product_operation_requests') is not null,'private reservation and idempotency tables exist');
select ok((select bool_and(relrowsecurity) from pg_class where oid=any(array['private.product_code_reservations'::regclass,'private.product_operation_requests'::regclass])),'RLS protects product operation internals');
select ok(not has_table_privilege('authenticated','private.product_code_reservations','select') and not has_table_privilege('authenticated','private.product_operation_requests','select'),'clients cannot read product operation internals');
select ok(has_function_privilege('authenticated','public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint)','execute'),'authenticated may call protected product RPC');
select ok(not has_table_privilege('authenticated','public.products','insert') and not has_table_privilege('authenticated','public.products','update') and not has_table_privilege('authenticated','public.products','delete'),'direct product mutation remains denied');
select is(private.normalize_product_code('  Dev   BAG  '),'dev bag','normalization trims collapses whitespace and case-folds');
select is(private.normalize_product_code('ＤＥＶ－ＢＡＧ'),'dev-bag','normalization applies Unicode NFKC equivalence');

insert into public.shops(id,slug,display_name) values
 ('a3100000-0000-4000-8000-000000000001','product-rpc-a','Product RPC A'),
 ('b3100000-0000-4000-8000-000000000001','product-rpc-b','Product RPC B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10310000-0000-4000-8000-000000000001','authenticated','authenticated','product-admin@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20310000-0000-4000-8000-000000000002','authenticated','authenticated','product-owner-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30310000-0000-4000-8000-000000000003','authenticated','authenticated','product-sales-a@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','40310000-0000-4000-8000-000000000004','authenticated','authenticated','product-owner-b@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,platform_role) values
 ('10310000-0000-4000-8000-000000000001','product.rpc.admin','Product Admin','super_admin'),
 ('20310000-0000-4000-8000-000000000002','product.rpc.owner.a','Product Owner A','standard'),
 ('30310000-0000-4000-8000-000000000003','product.rpc.sales.a','Product Sales A','standard'),
 ('40310000-0000-4000-8000-000000000004','product.rpc.owner.b','Product Owner B','standard');
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a3100000-0000-4000-8000-000000000001','20310000-0000-4000-8000-000000000002','owner'),
 ('a3100000-0000-4000-8000-000000000001','30310000-0000-4000-8000-000000000003','salesman'),
 ('b3100000-0000-4000-8000-000000000001','40310000-0000-4000-8000-000000000004','owner');

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_product('create-1','create','a3100000-0000-4000-8000-000000000001',null,' Dev   Bag-01 ',' BAR-001 ',' Dev Bag ',2,1250)$$,'Owner creates product');
select is((select count(*) from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),1::bigint,'create inserts exactly one product');
select results_eq($$select normalized_sku,normalized_barcode,name from public.products where shop_id='a3100000-0000-4000-8000-000000000001'$$,$$values('dev bag-01'::text,'bar-001'::text,'Dev Bag'::text)$$,'product stores normalized comparison and trimmed display values');
reset role;
select set_config('test.product_id',(select id::text from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),true);
select is((select count(*) from private.product_code_reservations where shop_id='a3100000-0000-4000-8000-000000000001'),2::bigint,'create permanently reserves SKU and barcode');
select is((select count(*) from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001' and record_type='product'),1::bigint,'create emits one business audit');
select is((select before_metadata from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001' and operation='create'),'{}'::jsonb,'create audit has safe empty before-state');

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select is((public.manage_product('create-1','create','a3100000-0000-4000-8000-000000000001',null,' Dev   Bag-01 ',' BAR-001 ',' Dev Bag ',2,1250)->>'id')::uuid,(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),'idempotent create replays same product');
select is((select count(*) from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),1::bigint,'create retry does not duplicate product');
reset role;
select is((select count(*) from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001'),1::bigint,'create retry does not duplicate audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.manage_product('create-1','create','a3100000-0000-4000-8000-000000000001',null,'DIFFERENT','BAR-002','Different',2,1250)$$,'22023','idempotency key payload mismatch','changed retry payload is rejected');
select throws_ok($$select public.manage_product('create-dup-sku','create','a3100000-0000-4000-8000-000000000001',null,'DEV BAG-01','OTHER-1','Duplicate',0,100)$$,'23505',null,'case/space-equivalent SKU is rejected');
select throws_ok($$select public.manage_product('create-dup-bar','create','a3100000-0000-4000-8000-000000000001',null,'OTHER-SKU','bar-001','Duplicate',0,100)$$,'23505',null,'case-equivalent barcode is rejected');

select set_config('request.jwt.claim.sub','40310000-0000-4000-8000-000000000004',true);
select lives_ok($$select public.manage_product('create-other-shop','create','b3100000-0000-4000-8000-000000000001',null,'DEV BAG-01','BAR-001','Other Shop Bag',0,100)$$,'same codes are reusable in another shop');
reset role;
select is((select count(*) from public.products where normalized_sku='dev bag-01'),2::bigint,'normalized SKU uniqueness is tenant-scoped');

set local role authenticated;
select set_config('request.jwt.claim.sub','30310000-0000-4000-8000-000000000003',true);
select throws_ok($$select public.manage_product('sales-forged','create','a3100000-0000-4000-8000-000000000001',null,'SALES-1',null,'Sales Forged',0,100)$$,'42501','not authorized','Salesman cannot create product');
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.manage_product('owner-forged-shop','create','b3100000-0000-4000-8000-000000000001',null,'FORGED-1',null,'Forged Shop',0,100)$$,'42501','not authorized','Owner cannot forge another shop');
select set_config('request.jwt.claim.sub','10310000-0000-4000-8000-000000000001',true);
select throws_ok($$select public.manage_product('admin-create','create','a3100000-0000-4000-8000-000000000001',null,'ADMIN-1',null,'Admin Product',0,100)$$,'42501','not authorized','Super Admin does not bypass Owner-only product mutation');

select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_product('update-1','update','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),'DEV-BAG-NEW','BAR-NEW','Updated Dev Bag',4,1500)$$,'Owner updates product');
select results_eq($$select normalized_sku,normalized_barcode,name,low_stock_threshold,default_selling_price_paisa from public.products where shop_id='a3100000-0000-4000-8000-000000000001'$$,$$values('dev-bag-new'::text,'bar-new'::text,'Updated Dev Bag'::text,4,1500::bigint)$$,'update applies validated product fields');
reset role;
select is((select count(*) from private.product_code_reservations where shop_id='a3100000-0000-4000-8000-000000000001'),4::bigint,'update preserves old and reserves new codes');
select is((select count(*) from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001'),2::bigint,'update emits one additional audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_product('update-1','update','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),'DEV-BAG-NEW','BAR-NEW','Updated Dev Bag',4,1500)$$,'update retry replays safely');
reset role;
select is((select count(*) from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001'),2::bigint,'update retry does not duplicate audit');

insert into public.sales(id,shop_id,status,is_credit,subtotal_paisa,grand_total_paisa,business_date,actor_user_id,idempotency_key) values('a3310000-0000-4000-8000-000000000001','a3100000-0000-4000-8000-000000000001','draft',false,0,0,'2026-07-27','20310000-0000-4000-8000-000000000002','draft-product-operation');
insert into public.sale_lines(id,shop_id,sale_id,line_number,product_id,product_name,sku_code,quantity,configured_unit_price_paisa,effective_unit_price_paisa,gross_total_paisa,line_total_paisa) select 'a3410000-0000-4000-8000-000000000001',shop_id,'a3310000-0000-4000-8000-000000000001',1,id,name,sku_code,1,default_selling_price_paisa,default_selling_price_paisa,default_selling_price_paisa,default_selling_price_paisa from public.products where shop_id='a3100000-0000-4000-8000-000000000001';
set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.manage_product('archive-blocked','archive','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'))$$,'55000','product is required by an in-progress operation','draft operation blocks archive');
reset role;
delete from public.sale_lines where id='a3410000-0000-4000-8000-000000000001';
delete from public.sales where id='a3310000-0000-4000-8000-000000000001';

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_product('archive-1','archive','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'))$$,'Owner archives unused product');
select is((select active from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),false,'archive preserves row and marks inactive');
reset role;
select is((select count(*) from private.business_audit_events where shop_id='a3100000-0000-4000-8000-000000000001'),3::bigint,'archive emits one audit');

set local role authenticated;
select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select is((public.manage_product('archive-1','archive','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'))->>'active')::boolean,false,'archive retry replays archived result');
select throws_ok($$select public.manage_product('update-archived','update','a3100000-0000-4000-8000-000000000001',(select id from public.products where shop_id='a3100000-0000-4000-8000-000000000001'),'NEWER-SKU',null,'No Update',0,100)$$,'55000','archived product cannot be updated','archived product cannot be edited');
select throws_ok($$select public.manage_product('reuse-old','create','a3100000-0000-4000-8000-000000000001',null,'DEV BAG-01','NEW-BAR','Old Code Reuse',0,100)$$,'23505','product code is permanently reserved','old SKU remains permanently reserved');
select set_config('request.jwt.claim.sub','40310000-0000-4000-8000-000000000004',true);
select throws_ok($$select public.manage_product('cross-update','update','b3100000-0000-4000-8000-000000000001',current_setting('test.product_id')::uuid,'CROSS',null,'Cross',0,100)$$,'42501','not authorized','cross-shop product update is rejected');
select throws_ok($$update public.products set name='Direct write'$$,'42501','permission denied for table products','direct product update remains denied');
select throws_ok($$select private.normalize_product_code('FORGED')$$,'42501','permission denied for function normalize_product_code','client cannot invoke private normalization helper');

select set_config('request.jwt.claim.sub','20310000-0000-4000-8000-000000000002',true);
select lives_ok($$select public.manage_product('blank-barcode','create','a3100000-0000-4000-8000-000000000001',null,'BLANK-BAR','   ','Blank Barcode',0,100)$$,'blank optional barcode is canonicalized to null');
select is((select barcode from public.products where normalized_sku='blank-bar'),null,'blank barcode is stored as null');
select throws_ok($$select public.manage_product('invalid-negative','create','a3100000-0000-4000-8000-000000000001',null,'NEGATIVE',null,'Negative',-1,100)$$,'22023','invalid product fields','negative product values are rejected');

reset role;
select * from finish();
rollback;
