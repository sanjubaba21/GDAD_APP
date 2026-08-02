begin;

create or replace function private.normalize_product_code(raw_code text)
returns text language sql immutable set search_path = '' as $$
  select nullif(
    lower(normalize(regexp_replace(trim(raw_code), '\s+', ' ', 'g'), NFKC)),
    ''
  );
$$;

drop index public.products_shop_sku_unique;
alter table public.products
  add column barcode text,
  add column normalized_sku text generated always as (private.normalize_product_code(sku_code)) stored,
  add column normalized_barcode text generated always as (private.normalize_product_code(barcode)) stored,
  add constraint products_normalized_sku_valid check (
    normalized_sku is not null and length(normalized_sku) between 1 and 64
    and normalized_sku !~ '[[:cntrl:]]'
  ),
  add constraint products_barcode_valid check (
    barcode is null or (
      normalized_barcode is not null and length(normalized_barcode) between 3 and 64
      and normalized_barcode !~ '[[:cntrl:]]'
    )
  ),
  add constraint products_name_length check (length(trim(name)) between 1 and 160);

create unique index products_shop_normalized_sku_unique
on public.products(shop_id,normalized_sku);
create unique index products_shop_normalized_barcode_unique
on public.products(shop_id,normalized_barcode)
where normalized_barcode is not null;

create type private.product_code_type as enum ('sku','barcode');
create table private.product_code_reservations (
  shop_id uuid not null references public.shops(id) on delete restrict,
  code_type private.product_code_type not null,
  normalized_code text not null,
  product_id uuid not null,
  reserved_at timestamptz not null default now(),
  primary key(shop_id,code_type,normalized_code),
  foreign key(shop_id,product_id) references public.products(shop_id,id) on delete restrict
);

insert into private.product_code_reservations(shop_id,code_type,normalized_code,product_id,reserved_at)
select shop_id,'sku',normalized_sku,id,created_at from public.products;
insert into private.product_code_reservations(shop_id,code_type,normalized_code,product_id,reserved_at)
select shop_id,'barcode',normalized_barcode,id,created_at from public.products
where normalized_barcode is not null;

create table private.product_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  action text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  product_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  constraint product_operation_action_valid check (action in ('create','update','archive')),
  constraint product_operation_idempotency_valid check (length(trim(idempotency_key)) between 1 and 160),
  constraint product_operation_fingerprint_valid check (request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint product_operation_completion_consistent check (
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function private.reserve_product_code(
  target_shop_id uuid, target_type private.product_code_type,
  target_code text, target_product_id uuid
)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if target_code is null then return; end if;
  insert into private.product_code_reservations(shop_id,code_type,normalized_code,product_id)
  values(target_shop_id,target_type,target_code,target_product_id)
  on conflict do nothing;
  if not exists(
    select 1 from private.product_code_reservations reservation
    where reservation.shop_id=target_shop_id and reservation.code_type=target_type
      and reservation.normalized_code=target_code and reservation.product_id=target_product_id
  ) then
    raise exception using errcode='23505', message='product code is permanently reserved';
  end if;
end;
$$;

create or replace function public.manage_product(
  p_idempotency_key text,
  p_action text,
  p_shop_id uuid,
  p_product_id uuid default null,
  p_sku_code text default null,
  p_barcode text default null,
  p_name text default null,
  p_low_stock_threshold integer default null,
  p_default_selling_price_paisa bigint default null
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  normalized_action text := lower(trim(p_action));
  normalized_sku text;
  normalized_barcode text;
  target_product_id uuid;
  fingerprint text;
  request private.product_operation_requests%rowtype;
  before_state jsonb := '{}'::jsonb;
  after_state jsonb;
  product_row public.products%rowtype;
begin
  if actor is null then raise exception using errcode='42501',message='not authorized'; end if;
  if normalized_action not in ('create','update','archive')
     or p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid product operation';
  end if;
  if not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active
      and not profile.disabled and shop.active
  ) then
    raise exception using errcode='42501',message='not authorized';
  end if;

  if normalized_action in ('create','update') then
    normalized_sku := private.normalize_product_code(p_sku_code);
    normalized_barcode := private.normalize_product_code(p_barcode);
    if normalized_sku is null or length(normalized_sku) not between 1 and 64
       or normalized_sku ~ '[[:cntrl:]]'
       or p_name is null or length(trim(p_name)) not between 1 and 160
       or p_low_stock_threshold is null or p_low_stock_threshold < 0
       or p_default_selling_price_paisa is null or p_default_selling_price_paisa < 0
       or (normalized_barcode is not null and length(normalized_barcode) not between 3 and 64) then
      raise exception using errcode='22023',message='invalid product fields';
    end if;
  end if;
  if normalized_action='create' and p_product_id is not null then
    raise exception using errcode='22023',message='create product id must be server generated';
  elsif normalized_action in ('update','archive') and p_product_id is null then
    raise exception using errcode='22023',message='product id is required';
  end if;

  if normalized_action='create' then target_product_id := extensions.gen_random_uuid();
  else
    target_product_id := p_product_id;
    select * into product_row from public.products
    where id=target_product_id and shop_id=p_shop_id for update;
    if not found then raise exception using errcode='42501',message='not authorized'; end if;
    before_state := jsonb_build_object(
      'sku_code',product_row.sku_code,'barcode',product_row.barcode,'name',product_row.name,
      'low_stock_threshold',product_row.low_stock_threshold,
      'default_selling_price_paisa',product_row.default_selling_price_paisa,'active',product_row.active
    );
  end if;

  fingerprint := encode(extensions.digest(concat_ws(E'\x1f',normalized_action,p_shop_id::text,
    coalesce(p_product_id::text,''),coalesce(normalized_sku,''),coalesce(normalized_barcode,''),
    coalesce(trim(p_name),''),coalesce(p_low_stock_threshold::text,''),
    coalesce(p_default_selling_price_paisa::text,'')),'sha256'),'hex');
  insert into private.product_operation_requests(
    shop_id,idempotency_key,action,actor_user_id,request_fingerprint,product_id
  ) values(p_shop_id,trim(p_idempotency_key),normalized_action,actor,fingerprint,target_product_id)
  on conflict do nothing;
  select * into request from private.product_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.action<>normalized_action
     or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  target_product_id := request.product_id;

  if normalized_action='create' then
    insert into public.products(
      id,shop_id,sku_code,barcode,name,low_stock_threshold,default_selling_price_paisa
    ) values(target_product_id,p_shop_id,trim(p_sku_code),nullif(trim(p_barcode),''),trim(p_name),
      p_low_stock_threshold,p_default_selling_price_paisa)
    returning * into product_row;
    perform private.reserve_product_code(p_shop_id,'sku',normalized_sku,target_product_id);
    perform private.reserve_product_code(p_shop_id,'barcode',normalized_barcode,target_product_id);
  elsif normalized_action='update' then
    if not product_row.active then raise exception using errcode='55000',message='archived product cannot be updated'; end if;
    perform private.reserve_product_code(p_shop_id,'sku',normalized_sku,target_product_id);
    perform private.reserve_product_code(p_shop_id,'barcode',normalized_barcode,target_product_id);
    update public.products set sku_code=trim(p_sku_code),barcode=nullif(trim(p_barcode),''),
      name=trim(p_name),low_stock_threshold=p_low_stock_threshold,
      default_selling_price_paisa=p_default_selling_price_paisa
    where id=target_product_id returning * into product_row;
  else
    if not product_row.active then raise exception using errcode='55000',message='product is already archived'; end if;
    if exists(
      select 1 from public.sale_lines line join public.sales sale on sale.id=line.sale_id
      where line.product_id=target_product_id and sale.status='draft'
      union all
      select 1 from public.purchase_bill_lines line join public.purchase_bills bill on bill.id=line.purchase_bill_id
      where line.product_id=target_product_id and bill.status='draft'
    ) then
      raise exception using errcode='55000',message='product is required by an in-progress operation';
    end if;
    update public.products set active=false where id=target_product_id returning * into product_row;
  end if;

  after_state := jsonb_build_object(
    'sku_code',product_row.sku_code,'barcode',product_row.barcode,'name',product_row.name,
    'low_stock_threshold',product_row.low_stock_threshold,
    'default_selling_price_paisa',product_row.default_selling_price_paisa,'active',product_row.active
  );
  insert into private.business_audit_events(
    shop_id,actor_user_id,operation,record_type,record_id,before_metadata,after_metadata,idempotency_key
  ) values(p_shop_id,actor,normalized_action,'product',target_product_id,before_state,after_state,
    'product:'||trim(p_idempotency_key));
  update private.product_operation_requests set result=to_jsonb(product_row),completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key)
  returning result into after_state;
  return after_state;
end;
$$;

alter table private.product_code_reservations enable row level security;
alter table private.product_operation_requests enable row level security;
revoke all on table private.product_code_reservations,private.product_operation_requests from public,anon,authenticated;
revoke all on function private.normalize_product_code(text),
  private.reserve_product_code(uuid,private.product_code_type,text,uuid),public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint)
from public,anon,authenticated;
grant execute on function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint) to authenticated;

comment on table private.product_code_reservations is 'Permanent per-shop SKU/barcode reservation; rows survive product archive and code changes.';
comment on function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint) is 'Owner-only idempotent create/update/archive with server validation, code reservation, and safe audit.';

commit;
