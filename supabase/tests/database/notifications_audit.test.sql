begin;
create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(39);

select ok(to_regclass('public.notifications') is not null and to_regclass('public.notification_reads') is not null and to_regclass('private.business_audit_events') is not null,'all Task 2.6 tables exist');
select ok(to_regtype('public.notification_category') is not null and to_regtype('public.notification_record_type') is not null,'typed notification fields exist');
select ok((select bool_and(relrowsecurity) from pg_class where oid=any(array['public.notifications'::regclass,'public.notification_reads'::regclass,'private.business_audit_events'::regclass])),'RLS is enabled on every Task 2.6 table');
select ok(has_table_privilege('authenticated','public.notifications','select') and has_table_privilege('authenticated','public.notification_reads','select'),'authenticated receives RLS-filtered notification reads');
select ok(not has_table_privilege('authenticated','public.notifications','insert') and not has_table_privilege('authenticated','public.notifications','update') and not has_table_privilege('authenticated','public.notifications','delete') and not has_table_privilege('authenticated','public.notification_reads','insert'),'authenticated cannot directly mutate notification state');
select ok(not has_table_privilege('authenticated','private.business_audit_events','select') and not has_table_privilege('authenticated','private.business_audit_events','insert') and not has_table_privilege('authenticated','private.business_audit_events','delete'),'business audit is private and cannot be forged by clients');
select is((select count(*) from pg_trigger where not tgisinternal and tgname in ('business_audit_events_immutable','business_audit_events_validate_actor')),2::bigint,'audit immutability and actor triggers exist');
select ok(not has_function_privilege('authenticated','private.jsonb_metadata_is_safe(jsonb)','execute') and not has_function_privilege('authenticated','private.notification_visible_to(uuid,uuid)','execute') and not has_function_privilege('authenticated','public.cleanup_expired_notifications(integer)','execute'),'validators and cleanup are unavailable to clients');

insert into public.shops(id,slug,display_name) values
 ('a2460000-0000-4000-8000-000000000001','notify-schema-a','Notify Schema A'),
 ('b2460000-0000-4000-8000-000000000001','notify-schema-b','Notify Schema B');
insert into auth.users(instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,email_change,email_change_token_new,recovery_token) values
 ('00000000-0000-0000-0000-000000000000','10246000-0000-4000-8000-000000000001','authenticated','authenticated','notify-admin@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','20246000-0000-4000-8000-000000000002','authenticated','authenticated','notify-owner@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','30246000-0000-4000-8000-000000000003','authenticated','authenticated','notify-sales@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','40246000-0000-4000-8000-000000000004','authenticated','authenticated','notify-other@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
 ('00000000-0000-0000-0000-000000000000','50246000-0000-4000-8000-000000000005','authenticated','authenticated','notify-disabled@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,platform_role,disabled) values
 ('10246000-0000-4000-8000-000000000001','notify.schema.admin','Notify Admin','super_admin',false),
 ('20246000-0000-4000-8000-000000000002','notify.schema.owner','Notify Owner','standard',false),
 ('30246000-0000-4000-8000-000000000003','notify.schema.sales','Notify Sales','standard',false),
 ('40246000-0000-4000-8000-000000000004','notify.schema.other','Notify Other','standard',false),
 ('50246000-0000-4000-8000-000000000005','notify.schema.disabled','Notify Disabled','standard',true);
insert into public.shop_memberships(shop_id,user_id,role) values
 ('a2460000-0000-4000-8000-000000000001','20246000-0000-4000-8000-000000000002','owner'),
 ('a2460000-0000-4000-8000-000000000001','30246000-0000-4000-8000-000000000003','salesman'),
 ('a2460000-0000-4000-8000-000000000001','50246000-0000-4000-8000-000000000005','salesman'),
 ('b2460000-0000-4000-8000-000000000001','40246000-0000-4000-8000-000000000004','owner');
insert into public.products(id,shop_id,sku_code,name,default_selling_price_paisa) values
 ('a0260000-0000-4000-8000-000000000001','a2460000-0000-4000-8000-000000000001','NOTIFY-A','Notify Product A',100),
 ('b0260000-0000-4000-8000-000000000001','b2460000-0000-4000-8000-000000000001','NOTIFY-B','Notify Product B',100);

select ok(private.jsonb_metadata_is_safe('{"stock":{"remaining":2}}'),'nested business metadata is safe');
select ok(not private.jsonb_metadata_is_safe('{"nested":{"access_token":"forbidden"}}'),'recursive secret-key detection rejects tokens');
select lives_ok($$insert into public.notifications(id,shop_id,category,recipient_user_id,title,body,record_type,record_id,safe_payload,created_by,idempotency_key) values('a1260000-0000-4000-8000-000000000001','a2460000-0000-4000-8000-000000000001','low_stock','30246000-0000-4000-8000-000000000003','Low stock','Only two bags remain','product','a0260000-0000-4000-8000-000000000001','{"remaining":2}','20246000-0000-4000-8000-000000000002','notify-direct')$$,'direct-recipient notification is accepted');
select lives_ok($$insert into public.notifications(id,shop_id,category,target_role,title,body,record_type,record_id,created_by,idempotency_key) values('a2260000-0000-4000-8000-000000000001','a2460000-0000-4000-8000-000000000001','system','owner','Daily close','Review daily totals','system',null,'10246000-0000-4000-8000-000000000001','notify-owner-role')$$,'role-targeted notification is accepted');
select lives_ok($$insert into public.notifications(id,shop_id,category,recipient_user_id,title,body,record_type,record_id,idempotency_key,created_at,expires_at) values('a3260000-0000-4000-8000-000000000001','a2460000-0000-4000-8000-000000000001','system','30246000-0000-4000-8000-000000000003','Old notice','Expired notice','system',null,'notify-expired',now()-interval '91 days',now()-interval '1 day')$$,'exactly 90-day historical notification is accepted');
select throws_ok($$insert into public.notifications(shop_id,category,recipient_user_id,target_role,title,body,record_type,idempotency_key) values('a2460000-0000-4000-8000-000000000001','system','30246000-0000-4000-8000-000000000003','salesman','Bad','Bad','system','bad-xor')$$,'23514',null,'recipient and role cannot both be supplied');
select throws_ok($$insert into public.notifications(shop_id,category,target_role,title,body,record_type,idempotency_key,created_at,expires_at) values('a2460000-0000-4000-8000-000000000001','system','owner','Bad','Bad','system','bad-expiry',now(),now()+interval '89 days')$$,'23514',null,'notification expiry cannot be changed');
select throws_ok($$insert into public.notifications(shop_id,category,target_role,title,body,record_type,safe_payload,idempotency_key) values('a2460000-0000-4000-8000-000000000001','system','owner','Bad','Bad','system','{"pin":"forbidden"}','bad-secret')$$,'23514',null,'secret-bearing notification metadata is rejected');
select throws_ok($$insert into public.notifications(shop_id,category,target_role,title,body,record_type,record_id,idempotency_key) values('a2460000-0000-4000-8000-000000000001','low_stock','owner','Bad','Bad','product','ffffffff-ffff-4fff-8fff-ffffffffffff','bad-source')$$,'23503','notification source does not exist in shop','orphan notification source is rejected');
select throws_ok($$insert into public.notifications(shop_id,category,recipient_user_id,title,body,record_type,idempotency_key) values('a2460000-0000-4000-8000-000000000001','system','40246000-0000-4000-8000-000000000004','Bad','Bad','system','bad-recipient')$$,'23503','notification recipient is not an active shop member','cross-shop recipient is rejected');

select lives_ok($$insert into private.business_audit_events(id,shop_id,actor_user_id,operation,record_type,record_id,before_metadata,after_metadata,idempotency_key) values('a4260000-0000-4000-8000-000000000001','a2460000-0000-4000-8000-000000000001','20246000-0000-4000-8000-000000000002','update','product','a0260000-0000-4000-8000-000000000001','{"active":true}','{"active":false}','audit-product-update')$$,'authorized safe business audit is accepted');
select throws_ok($$insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key) values('a2460000-0000-4000-8000-000000000001','20246000-0000-4000-8000-000000000002','update','product','a0260000-0000-4000-8000-000000000001','{"password_hash":"forbidden"}','audit-secret')$$,'23514',null,'secret-bearing audit metadata is rejected');
select throws_ok($$insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,idempotency_key) values('a2460000-0000-4000-8000-000000000001','40246000-0000-4000-8000-000000000004','update','product','a0260000-0000-4000-8000-000000000001','audit-cross-shop')$$,'42501','audit actor is not authorized for shop','cross-shop audit actor is rejected');
select throws_ok($$update private.business_audit_events set operation='delete' where id='a4260000-0000-4000-8000-000000000001'$$,'55000','business audit events are append-only','business audit update is rejected');
select throws_ok($$delete from private.business_audit_events where id='a4260000-0000-4000-8000-000000000001'$$,'55000','business audit events are append-only','business audit delete is rejected');

set local role authenticated;
select set_config('request.jwt.claim.sub','30246000-0000-4000-8000-000000000003',true);
select is((select count(*) from public.notifications),1::bigint,'Salesman sees active direct notification only');
select set_config('request.jwt.claim.sub','20246000-0000-4000-8000-000000000002',true);
select is((select count(*) from public.notifications),1::bigint,'Owner sees active owner-role notification only');
select set_config('request.jwt.claim.sub','40246000-0000-4000-8000-000000000004',true);
select is((select count(*) from public.notifications),0::bigint,'other-shop user sees no notification');
select set_config('request.jwt.claim.sub','50246000-0000-4000-8000-000000000005',true);
select is((select count(*) from public.notifications),0::bigint,'disabled stale session sees no notification');
select set_config('request.jwt.claim.sub','10246000-0000-4000-8000-000000000001',true);
select is((select count(*) from public.notifications),2::bigint,'Super Admin sees active notifications only');
select throws_ok($$insert into public.notifications(shop_id,category,target_role,title,body,record_type,idempotency_key) values('a2460000-0000-4000-8000-000000000001','system','owner','Forged','Forged','system','forged')$$,'42501','permission denied for table notifications','authenticated cannot forge notifications');
select throws_ok($$insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,idempotency_key) values('a2460000-0000-4000-8000-000000000001','10246000-0000-4000-8000-000000000001','update','product','a0260000-0000-4000-8000-000000000001','forged-audit')$$,'42501','permission denied for table business_audit_events','authenticated cannot forge audit events');

select set_config('request.jwt.claim.sub','30246000-0000-4000-8000-000000000003',true);
select lives_ok($$select public.mark_notification_read('a1260000-0000-4000-8000-000000000001')$$,'eligible recipient can mark read');
select is((select count(*) from public.notification_reads),1::bigint,'recipient sees own read receipt');
select is(public.mark_notification_read('a1260000-0000-4000-8000-000000000001'),public.mark_notification_read('a1260000-0000-4000-8000-000000000001'),'repeat mark-read preserves original time');
select set_config('request.jwt.claim.sub','20246000-0000-4000-8000-000000000002',true);
select throws_ok($$select public.mark_notification_read('a1260000-0000-4000-8000-000000000001')$$,'42501','notification is not available','non-recipient cannot mark another notification read');
select throws_ok($$insert into public.notification_reads(shop_id,notification_id,user_id) values('a2460000-0000-4000-8000-000000000001','a2260000-0000-4000-8000-000000000001','20246000-0000-4000-8000-000000000002')$$,'42501','permission denied for table notification_reads','read receipts cannot be forged directly');

reset role;
select is(public.cleanup_expired_notifications(10),1,'bounded backend cleanup removes expired notifications');
select is((select count(*) from public.notifications),2::bigint,'cleanup leaves active notifications intact');
select is((select count(*) from private.business_audit_events),1::bigint,'cleanup never removes audit evidence');
select ok(not has_table_privilege('anon','public.notifications','select'),'anonymous clients cannot read notifications');
select * from finish();
rollback;
