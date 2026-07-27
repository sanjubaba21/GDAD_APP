begin;

create table private.sale_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  sale_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key (shop_id,idempotency_key),
  constraint sale_operation_key_not_blank check(length(trim(idempotency_key)) between 1 and 160),
  constraint sale_operation_fingerprint_format check(request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint sale_operation_result_consistent check(
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function public.post_fifo_sale(
  p_idempotency_key text,
  p_shop_id uuid,
  p_business_date date,
  p_lines jsonb,
  p_sale_discount_paisa bigint default 0,
  p_is_credit boolean default false,
  p_customer_name text default null,
  p_customer_contact text default null,
  p_due_date date default null,
  p_payments jsonb default '[]'::jsonb
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid());
  actor_role public.shop_role;
  nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  fingerprint text;
  request private.sale_operation_requests%rowtype;
  sale_id uuid := extensions.gen_random_uuid();
  sale_journal_id uuid := extensions.gen_random_uuid();
  payment_id uuid;
  payment_journal_id uuid;
  receivable_account_id uuid;
  revenue_account_id uuid;
  inventory_account_id uuid;
  cogs_account_id uuid;
  payment_account_id uuid;
  line_item jsonb;
  payment_item jsonb;
  normalized_lines jsonb := '[]'::jsonb;
  product_row public.products%rowtype;
  lot_row public.inventory_lots%rowtype;
  sale_line_id uuid;
  product_id uuid;
  quantity integer;
  configured_price bigint;
  effective_price bigint;
  line_discount bigint;
  gross_total bigint;
  subtotal bigint := 0;
  line_discount_total bigint := 0;
  sale_discount_remaining bigint;
  allocated_sale_discount bigint;
  grand_total bigint;
  payment_total bigint := 0;
  total_cost bigint := 0;
  allocation_quantity integer;
  quantity_remaining integer;
  line_number integer := 0;
  allocation_number integer := 0;
  payment_number integer := 0;
  modified_price_lines integer := 0;
  result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null
     or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid sale request';
  end if;
  select membership.role into actor_role
  from public.shop_memberships membership
  join public.user_profiles profile on profile.user_id=membership.user_id
  join public.shops shop on shop.id=membership.shop_id
  where membership.user_id=actor and membership.shop_id=p_shop_id
    and membership.active and not profile.disabled and shop.active;
  if actor_role is null then raise exception using errcode='42501',message='not authorized'; end if;
  if p_business_date is null or p_business_date>nepal_today
     or (actor_role='salesman' and p_business_date<>nepal_today)
     or (actor_role='owner' and p_business_date<nepal_today-7) then
    raise exception using errcode='22023',message='business date is outside allowed window';
  end if;
  perform 1 from public.accounting_periods period
  where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  if p_lines is null or jsonb_typeof(p_lines)<>'array'
     or jsonb_array_length(p_lines) not between 1 and 100 then
    raise exception using errcode='22023',message='sale lines are required';
  end if;
  if p_sale_discount_paisa is null or p_sale_discount_paisa<0 then
    raise exception using errcode='22023',message='invalid sale discount';
  end if;
  if p_payments is null or jsonb_typeof(p_payments)<>'array'
     or jsonb_array_length(p_payments)>10 then
    raise exception using errcode='22023',message='invalid sale payments';
  end if;
  if coalesce(p_is_credit,false) then
    if actor_role<>'owner' or coalesce(length(trim(p_customer_name))>0,false)=false
       or coalesce(length(trim(p_customer_contact))>0,false)=false
       or p_due_date is null or p_due_date<p_business_date then
      raise exception using errcode='42501',message='credit sale is not authorized';
    end if;
  elsif p_customer_name is not null or p_customer_contact is not null or p_due_date is not null then
    raise exception using errcode='22023',message='cash sale cannot contain credit terms';
  end if;

  begin
    if exists(select 1 from (
      select (value->>'product_id')::uuid product_id,count(*) count
      from jsonb_array_elements(p_lines) group by 1 having count(*)>1
    ) duplicate) then raise exception using errcode='22023',message='duplicate sale product'; end if;
    perform 1 from public.products product
    join (select distinct (value->>'product_id')::uuid id from jsonb_array_elements(p_lines)) requested using(id)
    where product.shop_id=p_shop_id and product.active order by product.id for update of product;
    if (select count(*) from public.products product where product.shop_id=p_shop_id and product.active
        and product.id in(select (value->>'product_id')::uuid from jsonb_array_elements(p_lines)))<>jsonb_array_length(p_lines) then
      raise exception using errcode='42501',message='sale product is not available';
    end if;
    for line_item in select value from jsonb_array_elements(p_lines) loop
      product_id:=(line_item->>'product_id')::uuid;
      quantity:=(line_item->>'quantity')::integer;
      line_discount:=coalesce((line_item->>'line_discount_paisa')::bigint,0);
      select * into product_row from public.products where id=product_id and shop_id=p_shop_id;
      configured_price:=product_row.default_selling_price_paisa;
      effective_price:=coalesce((line_item->>'effective_unit_price_paisa')::bigint,configured_price);
      if quantity is null or quantity<=0 or effective_price<0 or line_discount<0 then
        raise exception using errcode='22023',message='invalid sale line';
      end if;
      if actor_role='salesman' and (effective_price<>configured_price or line_discount<>0 or p_sale_discount_paisa<>0) then
        raise exception using errcode='42501',message='price change is not authorized';
      end if;
      gross_total:=effective_price*quantity::bigint;
      if line_discount>gross_total then raise exception using errcode='22023',message='sale line discount exceeds gross'; end if;
      subtotal:=subtotal+gross_total;
      line_discount_total:=line_discount_total+line_discount;
      if effective_price<>configured_price or line_discount>0 then modified_price_lines:=modified_price_lines+1; end if;
      normalized_lines:=normalized_lines||jsonb_build_array(jsonb_build_object(
        'product_id',product_id,'quantity',quantity,'configured_unit_price_paisa',configured_price,
        'effective_unit_price_paisa',effective_price,'line_discount_paisa',line_discount,
        'product_name',product_row.name,'sku_code',product_row.sku_code
      ));
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid sale line';
  end;
  if p_sale_discount_paisa>subtotal-line_discount_total then
    raise exception using errcode='22023',message='sale discount exceeds subtotal';
  end if;
  grand_total:=subtotal-line_discount_total-p_sale_discount_paisa;

  begin
    for payment_item in select value from jsonb_array_elements(p_payments) loop
      if (payment_item->>'method') is null
         or (payment_item->>'method') not in ('cash','bank')
         or (payment_item->>'amount_paisa')::bigint<=0 then
        raise exception using errcode='22023',message='invalid sale payment';
      end if;
      payment_total:=payment_total+(payment_item->>'amount_paisa')::bigint;
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid sale payment';
  end;
  if payment_total>grand_total then raise exception using errcode='23514',message='sale payment exceeds total'; end if;
  if not coalesce(p_is_credit,false) and payment_total<>grand_total then
    raise exception using errcode='23514',message='non-credit sale must be fully paid';
  end if;

  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',p_shop_id::text,p_business_date::text,
    normalized_lines::text,p_sale_discount_paisa::text,coalesce(p_is_credit,false)::text,
    coalesce(trim(p_customer_name),''),coalesce(trim(p_customer_contact),''),coalesce(p_due_date::text,''),
    p_payments::text),'sha256'),'hex');
  insert into private.sale_operation_requests(shop_id,idempotency_key,actor_user_id,request_fingerprint,sale_id)
  values(p_shop_id,trim(p_idempotency_key),actor,fingerprint,sale_id) on conflict do nothing;
  select * into request from private.sale_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  sale_id:=request.sale_id;

  select id into inventory_account_id from public.financial_accounts
  where shop_id=p_shop_id and purpose_code='inventory_control' and account_type='inventory' and active for update;
  select id into cogs_account_id from public.financial_accounts
  where shop_id=p_shop_id and purpose_code='cost_of_goods_sold' and account_type='cogs' and active for update;
  if inventory_account_id is null or cogs_account_id is null then
    raise exception using errcode='55000',message='inventory sale accounts are unavailable';
  end if;
  if grand_total>0 then
    select id into receivable_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='accounts_receivable' and account_type='receivable' and active for update;
    select id into revenue_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='sales_revenue' and account_type='revenue' and active for update;
    if receivable_account_id is null or revenue_account_id is null then
      raise exception using errcode='55000',message='sales accounts are unavailable';
    end if;
  end if;

  insert into public.sales(id,shop_id,status,is_credit,customer_name,customer_contact,due_date,
    subtotal_paisa,line_discount_total_paisa,sale_discount_total_paisa,tax_total_paisa,
    grand_total_paisa,business_date,actor_user_id,idempotency_key,posted_at)
  values(sale_id,p_shop_id,'posted',coalesce(p_is_credit,false),
    case when p_is_credit then trim(p_customer_name) end,case when p_is_credit then trim(p_customer_contact) end,
    case when p_is_credit then p_due_date end,subtotal,line_discount_total,p_sale_discount_paisa,0,
    grand_total,p_business_date,actor,'sale:'||trim(p_idempotency_key)||':header',now());

  sale_discount_remaining:=p_sale_discount_paisa;
  for line_item in select value from jsonb_array_elements(normalized_lines) loop
    line_number:=line_number+1;
    product_id:=(line_item->>'product_id')::uuid;
    quantity:=(line_item->>'quantity')::integer;
    configured_price:=(line_item->>'configured_unit_price_paisa')::bigint;
    effective_price:=(line_item->>'effective_unit_price_paisa')::bigint;
    line_discount:=(line_item->>'line_discount_paisa')::bigint;
    gross_total:=effective_price*quantity::bigint;
    allocated_sale_discount:=least(sale_discount_remaining,gross_total-line_discount);
    sale_discount_remaining:=sale_discount_remaining-allocated_sale_discount;
    sale_line_id:=extensions.gen_random_uuid();
    insert into public.sale_lines(id,shop_id,sale_id,line_number,product_id,product_name,sku_code,
      quantity,configured_unit_price_paisa,effective_unit_price_paisa,gross_total_paisa,
      line_discount_paisa,allocated_sale_discount_paisa,line_total_paisa)
    values(sale_line_id,p_shop_id,sale_id,line_number,product_id,line_item->>'product_name',line_item->>'sku_code',
      quantity,configured_price,effective_price,gross_total,line_discount,allocated_sale_discount,
      gross_total-line_discount-allocated_sale_discount);
    quantity_remaining:=quantity;
    for lot_row in select lot.* from public.inventory_lots lot
      where lot.shop_id=p_shop_id and lot.product_id=post_fifo_sale.product_id
        and lot.remaining_quantity>0
      order by lot.received_at,lot.id for update of lot
    loop
      exit when quantity_remaining=0;
      allocation_quantity:=least(quantity_remaining,lot_row.remaining_quantity);
      allocation_number:=allocation_number+1;
      insert into public.sale_lot_allocations(shop_id,sale_line_id,product_id,lot_id,quantity,unit_cost_paisa)
      values(p_shop_id,sale_line_id,product_id,lot_row.id,allocation_quantity,lot_row.unit_cost_paisa);
      update public.inventory_lots set remaining_quantity=remaining_quantity-allocation_quantity where id=lot_row.id;
      insert into public.inventory_movements(shop_id,product_id,lot_id,movement_type,quantity_delta,
        unit_cost_paisa,source_type,source_id,business_date,actor_user_id,idempotency_key)
      values(p_shop_id,product_id,lot_row.id,'sale',-allocation_quantity,lot_row.unit_cost_paisa,
        'sale',sale_id::text,p_business_date,actor,
        'sale:'||trim(p_idempotency_key)||':movement:'||allocation_number);
      total_cost:=total_cost+allocation_quantity::bigint*lot_row.unit_cost_paisa;
      quantity_remaining:=quantity_remaining-allocation_quantity;
    end loop;
    if quantity_remaining<>0 then raise exception using errcode='23514',message='insufficient stock'; end if;
    update public.products set current_stock=current_stock-quantity where id=product_id;
    select * into product_row from public.products where id=product_id;
    if product_row.current_stock<=product_row.low_stock_threshold then
      insert into public.notifications(shop_id,category,target_role,title,body,record_type,record_id,
        safe_payload,created_by,idempotency_key)
      values(p_shop_id,'low_stock','owner','Low stock: '||product_row.name,
        product_row.current_stock||' units remain','product',product_id,
        jsonb_build_object('remaining',product_row.current_stock,'threshold',product_row.low_stock_threshold),
        actor,'sale:'||trim(p_idempotency_key)||':low-stock:'||product_id);
    end if;
  end loop;

  if grand_total>0 or total_cost>0 then
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,
      actor_user_id,idempotency_key)
    values(sale_journal_id,p_shop_id,'sale','Sale '||sale_id,sale_id,p_business_date,actor,
      'sale:'||trim(p_idempotency_key)||':journal');
    line_number:=0;
    if grand_total>0 then
      insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
        (p_shop_id,sale_journal_id,1,receivable_account_id,grand_total,0),
        (p_shop_id,sale_journal_id,2,revenue_account_id,0,grand_total);
      line_number:=2;
    end if;
    if total_cost>0 then
      insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
        (p_shop_id,sale_journal_id,line_number+1,cogs_account_id,total_cost,0),
        (p_shop_id,sale_journal_id,line_number+2,inventory_account_id,0,total_cost);
    end if;
  end if;

  for payment_item in select value from jsonb_array_elements(p_payments) loop
    payment_number:=payment_number+1;
    select id into payment_account_id from public.financial_accounts
    where shop_id=p_shop_id
      and purpose_code=case payment_item->>'method' when 'cash' then 'cash_main' else 'bank_main' end
      and account_type=(payment_item->>'method')::public.financial_account_type and active for update;
    if payment_account_id is null then raise exception using errcode='55000',message='payment account is unavailable'; end if;
    payment_id:=extensions.gen_random_uuid(); payment_journal_id:=extensions.gen_random_uuid();
    insert into public.sale_payments(id,shop_id,sale_id,method,amount_paisa,business_date,
      actor_user_id,idempotency_key)
    values(payment_id,p_shop_id,sale_id,(payment_item->>'method')::public.payment_method,
      (payment_item->>'amount_paisa')::bigint,p_business_date,actor,
      'sale:'||trim(p_idempotency_key)||':payment:'||payment_number);
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,
      actor_user_id,idempotency_key)
    values(payment_journal_id,p_shop_id,'sale_payment','Sale payment',payment_id,p_business_date,actor,
      'sale:'||trim(p_idempotency_key)||':payment-journal:'||payment_number);
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,payment_journal_id,1,payment_account_id,(payment_item->>'amount_paisa')::bigint,0),
      (p_shop_id,payment_journal_id,2,receivable_account_id,0,(payment_item->>'amount_paisa')::bigint);
  end loop;

  result_payload:=jsonb_build_object('sale_id',sale_id,'grand_total_paisa',grand_total,
    'paid_paisa',payment_total,'due_paisa',grand_total-payment_total,'cost_total_paisa',total_cost,
    'line_count',jsonb_array_length(normalized_lines),'allocation_count',allocation_number);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,
    after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','sale',sale_id,result_payload||jsonb_build_object(
    'is_credit',coalesce(p_is_credit,false),'modified_price_lines',modified_price_lines),
    'sale:'||trim(p_idempotency_key)||':audit');
  update private.sale_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

alter table private.sale_operation_requests enable row level security;
revoke all on table private.sale_operation_requests from public,anon,authenticated;
revoke all on function public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)
from public,anon,authenticated;
grant execute on function public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)
to authenticated;

comment on function public.post_fifo_sale(text,uuid,date,jsonb,bigint,boolean,text,text,date,jsonb)
is 'Posts one server-priced, no-negative-stock FIFO sale with settlement, ledger, notification, and audit effects.';

commit;
