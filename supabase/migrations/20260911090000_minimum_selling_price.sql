begin;

alter table public.products
  add column minimum_selling_price_paisa bigint not null default 0,
  add constraint products_minimum_selling_price_valid check (
    minimum_selling_price_paisa >= 0
    and minimum_selling_price_paisa <= default_selling_price_paisa
  );

comment on column public.products.minimum_selling_price_paisa
is 'Lowest negotiated unit price accepted for this product, in integer paisa.';

drop function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint);

create function public.manage_product(
  p_idempotency_key text,
  p_action text,
  p_shop_id uuid,
  p_product_id uuid default null,
  p_sku_code text default null,
  p_barcode text default null,
  p_name text default null,
  p_low_stock_threshold integer default null,
  p_default_selling_price_paisa bigint default null,
  p_minimum_selling_price_paisa bigint default null
)
returns jsonb language plpgsql security definer set search_path = '' as $$
declare
  actor uuid := (select auth.uid());
  normalized_action text := lower(trim(p_action));
  normalized_sku text;
  normalized_barcode text;
  target_product_id uuid;
  effective_minimum_price bigint;
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
       or p_minimum_selling_price_paisa < 0
       or (normalized_barcode is not null and length(normalized_barcode) not between 3 and 64) then
      raise exception using errcode='22023',message='invalid product fields';
    end if;
  end if;
  if normalized_action='create' and p_product_id is not null then
    raise exception using errcode='22023',message='create product id must be server generated';
  elsif normalized_action in ('update','archive') and p_product_id is null then
    raise exception using errcode='22023',message='product id is required';
  end if;

  if normalized_action='create' then
    target_product_id := extensions.gen_random_uuid();
    effective_minimum_price := coalesce(p_minimum_selling_price_paisa,0);
  else
    target_product_id := p_product_id;
    select * into product_row from public.products
    where id=target_product_id and shop_id=p_shop_id for update;
    if not found then raise exception using errcode='42501',message='not authorized'; end if;
    effective_minimum_price := coalesce(
      p_minimum_selling_price_paisa,
      product_row.minimum_selling_price_paisa
    );
    before_state := jsonb_build_object(
      'sku_code',product_row.sku_code,'barcode',product_row.barcode,'name',product_row.name,
      'low_stock_threshold',product_row.low_stock_threshold,
      'default_selling_price_paisa',product_row.default_selling_price_paisa,
      'minimum_selling_price_paisa',product_row.minimum_selling_price_paisa,
      'active',product_row.active
    );
  end if;
  if normalized_action in ('create','update')
     and effective_minimum_price > p_default_selling_price_paisa then
    raise exception using errcode='22023',message='minimum selling price exceeds suggested price';
  end if;

  fingerprint := encode(extensions.digest(concat_ws(E'\x1f',normalized_action,p_shop_id::text,
    coalesce(p_product_id::text,''),coalesce(normalized_sku,''),coalesce(normalized_barcode,''),
    coalesce(trim(p_name),''),coalesce(p_low_stock_threshold::text,''),
    coalesce(p_default_selling_price_paisa::text,''),coalesce(effective_minimum_price::text,'')),
    'sha256'),'hex');
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
      id,shop_id,sku_code,barcode,name,low_stock_threshold,default_selling_price_paisa,
      minimum_selling_price_paisa
    ) values(target_product_id,p_shop_id,trim(p_sku_code),nullif(trim(p_barcode),''),trim(p_name),
      p_low_stock_threshold,p_default_selling_price_paisa,effective_minimum_price)
    returning * into product_row;
    perform private.reserve_product_code(p_shop_id,'sku',normalized_sku,target_product_id);
    perform private.reserve_product_code(p_shop_id,'barcode',normalized_barcode,target_product_id);
  elsif normalized_action='update' then
    if not product_row.active then raise exception using errcode='55000',message='archived product cannot be updated'; end if;
    perform private.reserve_product_code(p_shop_id,'sku',normalized_sku,target_product_id);
    perform private.reserve_product_code(p_shop_id,'barcode',normalized_barcode,target_product_id);
    update public.products set sku_code=trim(p_sku_code),barcode=nullif(trim(p_barcode),''),
      name=trim(p_name),low_stock_threshold=p_low_stock_threshold,
      default_selling_price_paisa=p_default_selling_price_paisa,
      minimum_selling_price_paisa=effective_minimum_price
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
    'default_selling_price_paisa',product_row.default_selling_price_paisa,
    'minimum_selling_price_paisa',product_row.minimum_selling_price_paisa,
    'active',product_row.active
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

revoke all on function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint,bigint)
from public,anon,authenticated;
grant execute on function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint,bigint)
to authenticated;

comment on function public.manage_product(text,text,uuid,uuid,text,text,text,integer,bigint,bigint)
is 'Owner-only idempotent create/update/archive with server validation, minimum negotiated price, code reservation, and audit.';

do $migration$
declare
  function_definition text;
  validation_anchor text := $rule$if quantity is null or quantity<=0 or effective_price<0 or line_discount<0 then$rule$;
begin
  select pg_get_functiondef(
    'public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)'::regprocedure
  ) into function_definition;
  if strpos(function_definition, validation_anchor) = 0 then
    raise exception 'expected sale line validation anchor was not found';
  end if;
  function_definition := replace(
    function_definition,
    $rule$if quantity is null or quantity<=0 or effective_price<0 or line_discount<0 then
        raise exception using errcode='22023',message='invalid sale line';
      end if;
      if actor_role='salesman'$rule$,
    $rule$if quantity is null or quantity<=0 or effective_price<0 or line_discount<0 then
        raise exception using errcode='22023',message='invalid sale line';
      end if;
      if effective_price<product_row.minimum_selling_price_paisa then
        raise exception using errcode='22023',message='sale price below minimum threshold';
      end if;
      if actor_role='salesman'$rule$
  );
  if strpos(function_definition, 'sale price below minimum threshold') = 0 then
    raise exception 'minimum sale price rule was not installed';
  end if;
  execute function_definition;
end;
$migration$;

comment on function public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)
is 'Posts one negotiated-price, no-negative-stock FIFO sale with settlement, accounting, audit, and minimum product price enforcement.';

commit;
