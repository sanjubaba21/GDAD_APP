begin;

create type public.inventory_adjustment_reason as enum (
  'damaged','lost','count_shortage','stock_found','opening_balance','data_correction'
);

create table public.inventory_adjustments (
  id uuid primary key,
  shop_id uuid not null references public.shops(id) on delete restrict,
  product_id uuid not null,
  movement_type public.inventory_movement_type not null,
  reason_code public.inventory_adjustment_reason not null,
  source_lot_id uuid,
  created_lot_id uuid,
  quantity integer not null,
  quantity_delta integer not null,
  unit_cost_paisa bigint not null,
  total_cost_paisa bigint not null,
  note text,
  business_date date not null,
  occurred_at timestamptz not null default now(),
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  journal_transaction_id uuid,
  idempotency_key text not null,
  created_at timestamptz not null default now(),
  unique(shop_id,id),
  unique(shop_id,idempotency_key),
  unique(shop_id,journal_transaction_id),
  foreign key(shop_id,product_id) references public.products(shop_id,id) on delete restrict,
  foreign key(shop_id,source_lot_id) references public.inventory_lots(shop_id,id) on delete restrict,
  foreign key(shop_id,created_lot_id) references public.inventory_lots(shop_id,id) on delete restrict
    deferrable initially deferred,
  foreign key(shop_id,journal_transaction_id)
    references public.journal_transactions(shop_id,id) on delete restrict deferrable initially deferred,
  constraint inventory_adjustments_type_allowed check(
    movement_type in ('damage','loss','manual_add','manual_remove')
  ),
  constraint inventory_adjustments_reason_matches check(
    (movement_type='damage' and reason_code='damaged')
    or (movement_type='loss' and reason_code='lost')
    or (movement_type='manual_remove' and reason_code in ('count_shortage','data_correction'))
    or (movement_type='manual_add' and reason_code in ('stock_found','opening_balance','data_correction'))
  ),
  constraint inventory_adjustments_lot_direction check(
    (movement_type='manual_add' and source_lot_id is null and created_lot_id is not null)
    or (movement_type<>'manual_add' and source_lot_id is not null and created_lot_id is null)
  ),
  constraint inventory_adjustments_quantity_positive check(quantity>0),
  constraint inventory_adjustments_delta_matches check(
    (movement_type='manual_add' and quantity_delta=quantity)
    or (movement_type<>'manual_add' and quantity_delta=-quantity)
  ),
  constraint inventory_adjustments_cost_reconciles check(
    unit_cost_paisa>=0 and total_cost_paisa=unit_cost_paisa*quantity::bigint
  ),
  constraint inventory_adjustments_journal_matches_cost check(
    (total_cost_paisa=0 and journal_transaction_id is null)
    or (total_cost_paisa>0 and journal_transaction_id is not null)
  ),
  constraint inventory_adjustments_note_length check(note is null or length(trim(note)) between 1 and 1000),
  constraint inventory_adjustments_key_not_blank check(length(trim(idempotency_key)) between 1 and 160)
);

create table private.inventory_adjustment_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  inventory_adjustment_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  constraint inventory_adjustment_request_key_not_blank check(length(trim(idempotency_key)) between 1 and 160),
  constraint inventory_adjustment_request_fingerprint check(request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint inventory_adjustment_request_result_consistent check(
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function private.journal_source_exists(
  source_kind public.journal_kind,target_shop_id uuid,target_source_id uuid
)
returns boolean language plpgsql stable security definer set search_path='' as $$
begin
  case source_kind
    when 'sale' then return exists(select 1 from public.sales where shop_id=target_shop_id and id=target_source_id);
    when 'sale_payment' then return exists(select 1 from public.sale_payments where shop_id=target_shop_id and id=target_source_id);
    when 'sale_return' then return exists(select 1 from public.sale_returns where shop_id=target_shop_id and id=target_source_id);
    when 'refund' then return exists(select 1 from public.refunds where shop_id=target_shop_id and id=target_source_id);
    when 'purchase_receipt' then return exists(select 1 from public.purchase_receipts where shop_id=target_shop_id and id=target_source_id);
    when 'vendor_payment' then return exists(select 1 from public.vendor_payments where shop_id=target_shop_id and id=target_source_id);
    when 'vendor_return' then return exists(select 1 from public.vendor_returns where shop_id=target_shop_id and id=target_source_id);
    when 'expense' then return exists(select 1 from public.expenses where shop_id=target_shop_id and id=target_source_id);
    when 'inventory_adjustment' then return exists(select 1 from public.inventory_adjustments where shop_id=target_shop_id and id=target_source_id);
    else return target_source_id is null;
  end case;
end;
$$;

create or replace function public.post_inventory_adjustment(
  p_idempotency_key text,
  p_shop_id uuid,
  p_product_id uuid,
  p_movement_type public.inventory_movement_type,
  p_reason_code public.inventory_adjustment_reason,
  p_quantity integer,
  p_source_lot_id uuid default null,
  p_unit_cost_paisa bigint default null,
  p_business_date date default (timezone('Asia/Kathmandu',now()))::date,
  p_note text default null
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid());
  nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  fingerprint text;
  request private.inventory_adjustment_operation_requests%rowtype;
  adjustment_id uuid := extensions.gen_random_uuid();
  created_lot_id uuid;
  journal_id uuid;
  inventory_account_id uuid;
  control_account_id uuid;
  product_row public.products%rowtype;
  source_lot public.inventory_lots%rowtype;
  effective_unit_cost bigint;
  total_cost bigint;
  quantity_delta integer;
  result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null
     or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid inventory adjustment request';
  end if;
  if not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active
  ) then raise exception using errcode='42501',message='not authorized'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',p_shop_id::text,p_product_id::text,
    coalesce(p_movement_type::text,''),coalesce(p_reason_code::text,''),coalesce(p_quantity::text,''),
    coalesce(p_source_lot_id::text,''),coalesce(p_unit_cost_paisa::text,''),
    coalesce(p_business_date::text,''),coalesce(trim(p_note),'')),'sha256'),'hex');
  insert into private.inventory_adjustment_operation_requests(
    shop_id,idempotency_key,actor_user_id,request_fingerprint,inventory_adjustment_id
  ) values(p_shop_id,trim(p_idempotency_key),actor,fingerprint,adjustment_id) on conflict do nothing;
  select * into request from private.inventory_adjustment_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  adjustment_id:=request.inventory_adjustment_id;

  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window';
  end if;
  perform 1 from public.accounting_periods period
  where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  if p_movement_type is null or p_reason_code is null
     or p_movement_type not in ('damage','loss','manual_add','manual_remove')
     or p_quantity is null or p_quantity<=0
     or (p_note is not null and length(trim(p_note)) not between 1 and 1000) then
    raise exception using errcode='22023',message='invalid inventory adjustment';
  end if;
  if not (
    (p_movement_type='damage' and p_reason_code='damaged')
    or (p_movement_type='loss' and p_reason_code='lost')
    or (p_movement_type='manual_remove' and p_reason_code in ('count_shortage','data_correction'))
    or (p_movement_type='manual_add' and p_reason_code in ('stock_found','opening_balance','data_correction'))
  ) then raise exception using errcode='22023',message='adjustment reason does not match movement'; end if;
  select * into product_row from public.products product
  where product.id=p_product_id and product.shop_id=p_shop_id and product.active for update;
  if not found then raise exception using errcode='42501',message='product is not available'; end if;

  if p_movement_type='manual_add' then
    if p_source_lot_id is not null or p_unit_cost_paisa is null or p_unit_cost_paisa<0 then
      raise exception using errcode='22023',message='manual addition requires unit cost without source lot';
    end if;
    effective_unit_cost:=p_unit_cost_paisa;
    created_lot_id:=extensions.gen_random_uuid();
    quantity_delta:=p_quantity;
  else
    if p_source_lot_id is null or p_unit_cost_paisa is not null then
      raise exception using errcode='22023',message='stock reduction requires source lot and derived cost';
    end if;
    select * into source_lot from public.inventory_lots lot
    where lot.id=p_source_lot_id and lot.shop_id=p_shop_id and lot.product_id=p_product_id
      for update;
    if not found then raise exception using errcode='42501',message='source lot is not available'; end if;
    if source_lot.remaining_quantity<p_quantity then
      raise exception using errcode='23514',message='adjustment exceeds available lot quantity';
    end if;
    effective_unit_cost:=source_lot.unit_cost_paisa;
    quantity_delta:=-p_quantity;
  end if;
  total_cost:=effective_unit_cost*p_quantity::bigint;
  if total_cost>0 then
    select id into inventory_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='inventory_control' and account_type='inventory' and active for update;
    select id into control_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='inventory_adjustment_control' and account_type='clearing' and active for update;
    if inventory_account_id is null or control_account_id is null then
      raise exception using errcode='55000',message='inventory adjustment accounts are unavailable';
    end if;
    journal_id:=extensions.gen_random_uuid();
  end if;

  insert into public.inventory_adjustments(id,shop_id,product_id,movement_type,reason_code,
    source_lot_id,created_lot_id,quantity,quantity_delta,unit_cost_paisa,total_cost_paisa,
    note,business_date,actor_user_id,journal_transaction_id,idempotency_key)
  values(adjustment_id,p_shop_id,p_product_id,p_movement_type,p_reason_code,p_source_lot_id,
    created_lot_id,p_quantity,quantity_delta,effective_unit_cost,total_cost,nullif(trim(p_note),''),
    p_business_date,actor,journal_id,'inventory-adjustment:'||trim(p_idempotency_key)||':header');
  if p_movement_type='manual_add' then
    insert into public.inventory_lots(id,shop_id,product_id,source_type,source_id,unit_cost_paisa,
      original_quantity,remaining_quantity)
    values(created_lot_id,p_shop_id,p_product_id,'inventory_adjustment',adjustment_id::text,
      effective_unit_cost,p_quantity,p_quantity);
  else
    update public.inventory_lots set remaining_quantity=remaining_quantity-p_quantity
    where id=p_source_lot_id;
  end if;
  update public.products set current_stock=current_stock+quantity_delta where id=p_product_id;
  insert into public.inventory_movements(shop_id,product_id,lot_id,movement_type,quantity_delta,
    unit_cost_paisa,source_type,source_id,reason,business_date,actor_user_id,idempotency_key)
  values(p_shop_id,p_product_id,coalesce(created_lot_id,p_source_lot_id),p_movement_type,
    quantity_delta,effective_unit_cost,'inventory_adjustment',adjustment_id::text,p_reason_code::text,
    p_business_date,actor,'inventory-adjustment:'||trim(p_idempotency_key)||':movement');
  if total_cost>0 then
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,
      actor_user_id,idempotency_key)
    values(journal_id,p_shop_id,'inventory_adjustment','Inventory adjustment '||p_reason_code,
      adjustment_id,p_business_date,actor,'inventory-adjustment:'||trim(p_idempotency_key)||':journal');
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,
      financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,journal_id,1,case when quantity_delta>0 then inventory_account_id else control_account_id end,total_cost,0),
      (p_shop_id,journal_id,2,case when quantity_delta>0 then control_account_id else inventory_account_id end,0,total_cost);
  end if;
  select * into product_row from public.products where id=p_product_id;
  insert into public.notifications(shop_id,category,target_role,title,body,record_type,record_id,
    safe_payload,created_by,idempotency_key)
  values(p_shop_id,'system','owner','Inventory adjustment: '||product_row.name,
    p_movement_type::text||' '||p_quantity||' units','product',p_product_id,
    jsonb_build_object('movement_type',p_movement_type,'reason_code',p_reason_code,
      'quantity_delta',quantity_delta,'stock_after',product_row.current_stock),actor,
    'inventory-adjustment:'||trim(p_idempotency_key)||':notification');
  result_payload:=jsonb_build_object('inventory_adjustment_id',adjustment_id,
    'product_id',p_product_id,'movement_type',p_movement_type,'reason_code',p_reason_code,
    'quantity_delta',quantity_delta,'unit_cost_paisa',effective_unit_cost,
    'total_cost_paisa',total_cost,'stock_after',product_row.current_stock,
    'source_lot_id',p_source_lot_id,'created_lot_id',created_lot_id);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,
    record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','inventory_adjustment',adjustment_id,result_payload,
    'inventory-adjustment:'||trim(p_idempotency_key)||':audit');
  update private.inventory_adjustment_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create index inventory_adjustments_product_timeline_idx
on public.inventory_adjustments(shop_id,product_id,occurred_at desc,id desc);
create index inventory_adjustments_reason_date_idx
on public.inventory_adjustments(shop_id,reason_code,business_date desc);

alter table public.inventory_adjustments enable row level security;
alter table private.inventory_adjustment_operation_requests enable row level security;
create policy inventory_adjustments_select_owner on public.inventory_adjustments for select to authenticated
using(private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
revoke all on table public.inventory_adjustments,private.inventory_adjustment_operation_requests
from public,anon,authenticated;
grant select on table public.inventory_adjustments to authenticated;
revoke all on function public.post_inventory_adjustment(text,uuid,uuid,public.inventory_movement_type,
  public.inventory_adjustment_reason,integer,uuid,bigint,date,text) from public,anon,authenticated;
grant execute on function public.post_inventory_adjustment(text,uuid,uuid,public.inventory_movement_type,
  public.inventory_adjustment_reason,integer,uuid,bigint,date,text) to authenticated;
revoke all on function private.journal_source_exists(public.journal_kind,uuid,uuid)
from public,anon,authenticated;

comment on table public.inventory_adjustments is
'Immutable Owner-posted source for reason-coded FIFO additions, damage, loss, and removals.';
comment on function public.post_inventory_adjustment(text,uuid,uuid,public.inventory_movement_type,
  public.inventory_adjustment_reason,integer,uuid,bigint,date,text) is
'Posts one idempotent lot-linked inventory adjustment with stock, journal, notification, and audit effects.';

commit;
