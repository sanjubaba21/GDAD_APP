begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(24);

select has_table('public', 'shops', 'shops table exists');
select has_table('public', 'user_profiles', 'user profiles table exists');
select has_table('public', 'shop_memberships', 'shop memberships table exists');
select has_table('public', 'products', 'products table exists');
select has_table('public', 'inventory_lots', 'inventory lots table exists');
select has_table('public', 'inventory_movements', 'inventory movements table exists');
select has_table('private', 'login_credentials', 'private login credentials table exists');

select col_is_pk('public', 'shops', 'id', 'shops.id is the primary key');
select col_is_pk('public', 'user_profiles', 'user_id', 'user_profiles.user_id is primary key');
select col_is_pk('public', 'products', 'id', 'products.id is the primary key');

select ok(
    (select relrowsecurity from pg_class where oid = 'public.shops'::regclass),
    'shops has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.user_profiles'::regclass),
    'user_profiles has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.shop_memberships'::regclass),
    'shop_memberships has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.products'::regclass),
    'products has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.inventory_lots'::regclass),
    'inventory_lots has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'public.inventory_movements'::regclass),
    'inventory_movements has RLS enabled'
);
select ok(
    (select relrowsecurity from pg_class where oid = 'private.login_credentials'::regclass),
    'private.login_credentials has RLS enabled'
);

select policies_are(
    'public',
    'products',
    array['products_select_shop_member'],
    'products exposes only its tenant-scoped select policy'
);
select policies_are(
    'public',
    'inventory_movements',
    array['inventory_movements_select_shop_member'],
    'inventory movements exposes only its tenant-scoped select policy'
);

select ok(
    has_table_privilege('authenticated', 'public.products', 'select'),
    'authenticated users receive product SELECT privilege'
);
select ok(
    not has_table_privilege('authenticated', 'public.products', 'insert'),
    'authenticated users cannot directly insert products'
);
select ok(
    not has_table_privilege('authenticated', 'public.inventory_movements', 'update'),
    'authenticated users cannot update append-only movements'
);
select ok(
    not has_table_privilege('anon', 'public.products', 'select'),
    'anonymous users cannot read products'
);
select ok(
    not has_table_privilege('authenticated', 'private.login_credentials', 'select'),
    'authenticated users cannot read PIN verifier state'
);

select * from finish();
rollback;
