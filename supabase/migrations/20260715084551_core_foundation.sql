begin;

create extension if not exists pgcrypto with schema extensions;

create type public.platform_role as enum ('standard', 'super_admin');
create type public.shop_role as enum ('owner', 'salesman');
create type public.inventory_movement_type as enum (
    'purchase',
    'sale',
    'return',
    'damage',
    'loss',
    'manual_add',
    'manual_remove'
);

create schema if not exists private;
revoke all on schema private from public, anon, authenticated;

create table public.shops (
    id uuid primary key default gen_random_uuid(),
    slug text not null,
    display_name text not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint shops_slug_format check (
        slug = lower(trim(slug))
        and slug ~ '^[a-z0-9][a-z0-9-]{2,62}$'
    ),
    constraint shops_display_name_not_blank check (length(trim(display_name)) > 0)
);

create unique index shops_slug_unique on public.shops (lower(slug));

create table public.user_profiles (
    user_id uuid primary key references auth.users(id) on delete cascade,
    login_id text not null,
    display_name text not null,
    platform_role public.platform_role not null default 'standard',
    disabled boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint user_profiles_login_id_format check (
        login_id = lower(trim(login_id))
        and login_id ~ '^[a-z0-9][a-z0-9._-]{2,63}$'
    ),
    constraint user_profiles_display_name_not_blank check (length(trim(display_name)) > 0)
);

create unique index user_profiles_login_id_unique
    on public.user_profiles (lower(login_id));

create table public.shop_memberships (
    shop_id uuid not null references public.shops(id) on delete restrict,
    user_id uuid not null references public.user_profiles(user_id) on delete restrict,
    role public.shop_role not null,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    primary key (shop_id, user_id)
);

create index shop_memberships_user_id_idx
    on public.shop_memberships (user_id, active, shop_id);

create table private.login_credentials (
    user_id uuid primary key references auth.users(id) on delete cascade,
    pin_hash text not null,
    failed_attempts integer not null default 0,
    locked_until timestamptz,
    last_failed_at timestamptz,
    pin_changed_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint login_credentials_pin_hash_not_blank check (length(pin_hash) >= 32),
    constraint login_credentials_failed_attempts_nonnegative check (failed_attempts >= 0)
);

comment on table private.login_credentials is
    'Server-only PIN verifier state. Never expose through PostgREST or Android.';

create table public.products (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    sku_code text not null,
    name text not null,
    low_stock_threshold integer not null default 0,
    default_selling_price_paisa bigint not null,
    current_stock integer not null default 0,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, id),
    constraint products_sku_not_blank check (length(trim(sku_code)) > 0),
    constraint products_name_not_blank check (length(trim(name)) > 0),
    constraint products_low_stock_threshold_nonnegative check (low_stock_threshold >= 0),
    constraint products_default_price_nonnegative check (default_selling_price_paisa >= 0)
);

create unique index products_shop_sku_unique
    on public.products (shop_id, lower(sku_code));
create index products_shop_active_name_idx
    on public.products (shop_id, active, name);

create table public.inventory_lots (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    product_id uuid not null,
    source_type text not null,
    source_id text not null,
    received_at timestamptz not null default now(),
    unit_cost_paisa bigint not null,
    original_quantity integer not null,
    remaining_quantity integer not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    foreign key (shop_id, product_id)
        references public.products(shop_id, id) on delete restrict,
    constraint inventory_lots_source_type_not_blank check (length(trim(source_type)) > 0),
    constraint inventory_lots_source_id_not_blank check (length(trim(source_id)) > 0),
    constraint inventory_lots_unit_cost_nonnegative check (unit_cost_paisa >= 0),
    constraint inventory_lots_original_quantity_positive check (original_quantity > 0),
    constraint inventory_lots_remaining_quantity_valid check (
        remaining_quantity between 0 and original_quantity
    )
);

create index inventory_lots_fifo_idx
    on public.inventory_lots (shop_id, product_id, received_at, id)
    where remaining_quantity > 0;

create table public.inventory_movements (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    product_id uuid not null,
    lot_id uuid,
    movement_type public.inventory_movement_type not null,
    quantity_delta integer not null,
    unit_cost_paisa bigint,
    source_type text not null,
    source_id text not null,
    reason text,
    business_date date not null default (timezone('Asia/Kathmandu', now()))::date,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    foreign key (shop_id, product_id)
        references public.products(shop_id, id) on delete restrict,
    foreign key (shop_id, lot_id)
        references public.inventory_lots(shop_id, id) on delete restrict,
    constraint inventory_movements_quantity_nonzero check (quantity_delta <> 0),
    constraint inventory_movements_unit_cost_nonnegative check (
        unit_cost_paisa is null or unit_cost_paisa >= 0
    ),
    constraint inventory_movements_source_type_not_blank check (length(trim(source_type)) > 0),
    constraint inventory_movements_source_id_not_blank check (length(trim(source_id)) > 0),
    constraint inventory_movements_idempotency_key_not_blank check (
        length(trim(idempotency_key)) > 0
    )
);

create unique index inventory_movements_shop_idempotency_unique
    on public.inventory_movements (shop_id, idempotency_key);
create index inventory_movements_product_timeline_idx
    on public.inventory_movements (shop_id, product_id, occurred_at desc, id desc);
create index inventory_movements_business_date_idx
    on public.inventory_movements (shop_id, business_date, occurred_at desc);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at = now();
    return new;
end;
$$;

create trigger shops_set_updated_at
before update on public.shops
for each row execute function public.set_updated_at();

create trigger user_profiles_set_updated_at
before update on public.user_profiles
for each row execute function public.set_updated_at();

create trigger shop_memberships_set_updated_at
before update on public.shop_memberships
for each row execute function public.set_updated_at();

create trigger login_credentials_set_updated_at
before update on private.login_credentials
for each row execute function public.set_updated_at();

create trigger products_set_updated_at
before update on public.products
for each row execute function public.set_updated_at();

create or replace function private.is_super_admin()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_profiles profile
        where profile.user_id = (select auth.uid())
          and profile.platform_role = 'super_admin'
          and not profile.disabled
    );
$$;

create or replace function private.has_shop_role(
    target_shop_id uuid,
    allowed_roles public.shop_role[]
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.shop_memberships membership
        join public.user_profiles profile on profile.user_id = membership.user_id
        join public.shops shop on shop.id = membership.shop_id
        where membership.user_id = (select auth.uid())
          and membership.shop_id = target_shop_id
          and membership.role = any(allowed_roles)
          and membership.active
          and not profile.disabled
          and shop.active
    );
$$;

create or replace function private.can_view_user(target_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select target_user_id = (select auth.uid())
        or private.is_super_admin()
        or exists (
            select 1
            from public.shop_memberships target_membership
            where target_membership.user_id = target_user_id
              and target_membership.active
              and private.has_shop_role(
                  target_membership.shop_id,
                  array['owner']::public.shop_role[]
              )
        );
$$;

revoke all on function public.set_updated_at() from public, anon, authenticated;
revoke all on function private.is_super_admin() from public, anon, authenticated;
revoke all on function private.has_shop_role(uuid, public.shop_role[])
    from public, anon, authenticated;
revoke all on function private.can_view_user(uuid) from public, anon, authenticated;

grant usage on schema private to authenticated;
grant execute on function private.is_super_admin() to authenticated;
grant execute on function private.has_shop_role(uuid, public.shop_role[]) to authenticated;
grant execute on function private.can_view_user(uuid) to authenticated;

alter table public.shops enable row level security;
alter table public.user_profiles enable row level security;
alter table public.shop_memberships enable row level security;
alter table private.login_credentials enable row level security;
alter table public.products enable row level security;
alter table public.inventory_lots enable row level security;
alter table public.inventory_movements enable row level security;

create policy shops_select_authorized
on public.shops for select to authenticated
using (
    private.is_super_admin()
    or private.has_shop_role(
        id,
        array['owner', 'salesman']::public.shop_role[]
    )
);

create policy user_profiles_select_authorized
on public.user_profiles for select to authenticated
using (private.can_view_user(user_id));

create policy shop_memberships_select_authorized
on public.shop_memberships for select to authenticated
using (
    user_id = (select auth.uid())
    or private.is_super_admin()
    or private.has_shop_role(shop_id, array['owner']::public.shop_role[])
);

create policy products_select_shop_member
on public.products for select to authenticated
using (
    private.is_super_admin()
    or private.has_shop_role(
        shop_id,
        array['owner', 'salesman']::public.shop_role[]
    )
);

create policy inventory_lots_select_shop_member
on public.inventory_lots for select to authenticated
using (
    private.is_super_admin()
    or private.has_shop_role(
        shop_id,
        array['owner', 'salesman']::public.shop_role[]
    )
);

create policy inventory_movements_select_shop_member
on public.inventory_movements for select to authenticated
using (
    private.is_super_admin()
    or private.has_shop_role(
        shop_id,
        array['owner', 'salesman']::public.shop_role[]
    )
);

revoke all on all tables in schema public from anon, authenticated;
grant select on table
    public.shops,
    public.user_profiles,
    public.shop_memberships,
    public.products,
    public.inventory_lots,
    public.inventory_movements
to authenticated;

revoke all on all tables in schema private from public, anon, authenticated;
revoke all on all sequences in schema private from public, anon, authenticated;

alter default privileges in schema public
    revoke all on tables from anon, authenticated;
alter default privileges in schema public
    revoke execute on functions from public, anon, authenticated;
alter default privileges in schema private
    revoke all on tables from public, anon, authenticated;
alter default privileges in schema private
    revoke execute on functions from public, anon, authenticated;

comment on table public.inventory_movements is
    'Append-only inventory ledger. Mutations must use protected transactional functions.';
comment on column public.products.current_stock is
    'Server-maintained projection; clients receive SELECT only and cannot mutate it directly.';

commit;
