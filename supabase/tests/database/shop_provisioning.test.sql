begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;
select plan(33);

select has_table('private', 'shop_creation_requests', 'private shop creation state exists');
select has_function(
  'public', 'create_shop', array['uuid', 'text', 'text'],
  'protected shop creation RPC exists'
);
select has_function(
  'private', 'ensure_shop_initial_accounting_period', array['uuid'],
  'private initial accounting-period provisioner exists'
);
select ok(
  (select relrowsecurity from pg_class where oid = 'private.shop_creation_requests'::regclass),
  'shop creation state has RLS enabled'
);
select ok(
  not has_table_privilege('authenticated', 'private.shop_creation_requests', 'select'),
  'authenticated clients cannot read shop creation internals'
);
select ok(
  has_function_privilege('authenticated', 'public.create_shop(uuid,text,text)', 'execute'),
  'authenticated users may reach the protected RPC'
);
select ok(
  not has_function_privilege('anon', 'public.create_shop(uuid,text,text)', 'execute'),
  'anonymous users cannot execute shop creation'
);
select ok(
  not has_function_privilege(
    'authenticated',
    'private.ensure_shop_initial_accounting_period(uuid)',
    'execute'
  ),
  'authenticated users cannot execute period provisioning directly'
);
select ok(
  not has_table_privilege('authenticated', 'public.shops', 'insert')
  and not has_table_privilege('authenticated', 'public.shops', 'update')
  and not has_table_privilege('authenticated', 'public.shops', 'delete'),
  'direct shop mutation remains denied'
);

insert into public.shops(id, slug, display_name) values
  ('a8200000-0000-4000-8000-000000000001', 'existing-shop', 'Existing Shop');
select lives_ok(
  $$select private.ensure_shop_initial_accounting_period('a8200000-0000-4000-8000-000000000001')$$,
  'an existing shop with no period receives its initial period'
);
select is(
  (select count(*) from public.accounting_periods where shop_id='a8200000-0000-4000-8000-000000000001'),
  1::bigint,
  'existing-shop provisioning creates exactly one period'
);
select ok(
  exists(
    select 1
    from public.accounting_periods
    where shop_id='a8200000-0000-4000-8000-000000000001'
      and status='open'
      and date_from=(timezone('Asia/Kathmandu',now()))::date-7
      and date_to=date '9999-12-31'
  ),
  'initial period covers the allowed backdate window and remains open'
);
select lives_ok(
  $$select private.ensure_shop_initial_accounting_period('a8200000-0000-4000-8000-000000000001')$$,
  'initial-period provisioning is idempotent'
);
select is(
  (select count(*) from public.accounting_periods where shop_id='a8200000-0000-4000-8000-000000000001'),
  1::bigint,
  'idempotent provisioning does not duplicate the period'
);
insert into auth.users(
  instance_id,id,aud,role,email,encrypted_password,email_confirmed_at,
  raw_app_meta_data,raw_user_meta_data,created_at,updated_at,
  confirmation_token,email_change,email_change_token_new,recovery_token
) values
  ('00000000-0000-0000-0000-000000000000','10820000-0000-4000-8000-000000000001','authenticated','authenticated','shop-admin@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
  ('00000000-0000-0000-0000-000000000000','20820000-0000-4000-8000-000000000002','authenticated','authenticated','shop-owner@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
  ('00000000-0000-0000-0000-000000000000','30820000-0000-4000-8000-000000000003','authenticated','authenticated','shop-sales@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','',''),
  ('00000000-0000-0000-0000-000000000000','40820000-0000-4000-8000-000000000004','authenticated','authenticated','shop-disabled-admin@auth.gdad.invalid','',now(),'{}','{}',now(),now(),'','','','');
insert into public.user_profiles(user_id,login_id,display_name,platform_role,disabled) values
  ('10820000-0000-4000-8000-000000000001','shop.admin','Shop Admin','super_admin',false),
  ('20820000-0000-4000-8000-000000000002','shop.owner','Shop Owner','standard',false),
  ('30820000-0000-4000-8000-000000000003','shop.sales','Shop Sales','standard',false),
  ('40820000-0000-4000-8000-000000000004','shop.disabled.admin','Disabled Admin','super_admin',true);
insert into public.shop_memberships(shop_id,user_id,role) values
  ('a8200000-0000-4000-8000-000000000001','20820000-0000-4000-8000-000000000002','owner'),
  ('a8200000-0000-4000-8000-000000000001','30820000-0000-4000-8000-000000000003','salesman');

set local role authenticated;
select set_config('request.jwt.claim.sub','10820000-0000-4000-8000-000000000001',true);
select lives_ok(
  $$select public.create_shop('11820000-0000-4000-8000-000000000001','  GDAD-KTM  ',' GDAD Kathmandu ')$$,
  'active Super Admin creates a shop'
);
select is(
  (select count(*) from public.shops where slug='gdad-ktm' and display_name='GDAD Kathmandu'),
  1::bigint,
  'shop fields are normalized and stored once'
);
select is(
  (public.create_shop('11820000-0000-4000-8000-000000000001','gdad-ktm','GDAD Kathmandu')->>'id')::uuid,
  (select id from public.shops where slug='gdad-ktm'),
  'exact retry returns the original shop'
);
reset role;

select is(
  (select count(*) from public.shops where slug='gdad-ktm'),
  1::bigint,
  'exact retry does not duplicate the shop'
);
select is(
  (select count(*) from public.financial_accounts account
   join public.shops shop on shop.id=account.shop_id where shop.slug='gdad-ktm'),
  11::bigint,
  'shop creation atomically provisions all system accounts'
);
select is(
  (select count(*) from public.accounting_periods period
   join public.shops shop on shop.id=period.shop_id
   where shop.slug='gdad-ktm' and period.status='open'
     and (timezone('Asia/Kathmandu',now()))::date between period.date_from and period.date_to),
  1::bigint,
  'shop creation and exact retry leave one current open accounting period'
);
select is(
  (select count(*) from private.business_audit_events audit
   join public.shops shop on shop.id=audit.shop_id
   where shop.slug='gdad-ktm' and audit.record_type='shop' and audit.operation='create'),
  1::bigint,
  'shop creation emits one immutable audit event'
);
select is(
  (select before_metadata from private.business_audit_events audit
   join public.shops shop on shop.id=audit.shop_id where shop.slug='gdad-ktm'),
  '{}'::jsonb,
  'shop audit has an empty before-state'
);
select ok(
  private.jsonb_metadata_is_safe(
    (select after_metadata from private.business_audit_events audit
     join public.shops shop on shop.id=audit.shop_id where shop.slug='gdad-ktm')
  ),
  'shop audit metadata is credential-safe'
);

set local role authenticated;
select set_config('request.jwt.claim.sub','10820000-0000-4000-8000-000000000001',true);
select throws_ok(
  $$select public.create_shop('11820000-0000-4000-8000-000000000001','changed-shop','Changed Shop')$$,
  '22023','idempotency key payload mismatch','changed retry payload is rejected'
);
select throws_ok(
  $$select public.create_shop('12820000-0000-4000-8000-000000000002','GDAD KTM','Invalid Slug')$$,
  '22023','invalid shop fields','invalid slug is rejected'
);
select throws_ok(
  $$select public.create_shop('13820000-0000-4000-8000-000000000003','valid-slug','   ')$$,
  '22023','invalid shop fields','blank display name is rejected'
);
select throws_ok(
  $$select public.create_shop('14820000-0000-4000-8000-000000000004','gdad-ktm','Duplicate Slug')$$,
  '23505',null,'duplicate normalized slug is rejected'
);
select set_config('request.jwt.claim.sub','20820000-0000-4000-8000-000000000002',true);
select throws_ok(
  $$select public.create_shop('15820000-0000-4000-8000-000000000005','owner-forged','Owner Forged')$$,
  '42501','not authorized','Owner cannot create a shop'
);
select set_config('request.jwt.claim.sub','30820000-0000-4000-8000-000000000003',true);
select throws_ok(
  $$select public.create_shop('16820000-0000-4000-8000-000000000006','sales-forged','Sales Forged')$$,
  '42501','not authorized','Salesman cannot create a shop'
);
select set_config('request.jwt.claim.sub','40820000-0000-4000-8000-000000000004',true);
select throws_ok(
  $$select public.create_shop('17820000-0000-4000-8000-000000000007','disabled-forged','Disabled Forged')$$,
  '42501','not authorized','disabled Super Admin cannot create a shop'
);
select set_config('request.jwt.claim.sub','99820000-0000-4000-8000-000000000009',true);
select throws_ok(
  $$select public.create_shop('18820000-0000-4000-8000-000000000008','unknown-forged','Unknown Forged')$$,
  '42501','not authorized','unknown authenticated subject cannot create a shop'
);
select throws_ok(
  $$insert into public.shops(slug,display_name) values('direct-forged','Direct Forged')$$,
  '42501','permission denied for table shops','direct shop insert remains denied'
);
select throws_ok(
  $$select * from private.shop_creation_requests$$,
  '42501','permission denied for table shop_creation_requests','client cannot read shop creation requests'
);

reset role;
select * from finish();
rollback;
