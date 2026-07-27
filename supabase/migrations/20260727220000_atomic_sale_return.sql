begin;

create table private.sale_return_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  sale_return_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  constraint sale_return_operation_key_not_blank check(length(trim(idempotency_key)) between 1 and 160),
  constraint sale_return_operation_fingerprint_format check(request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint sale_return_operation_result_consistent check(
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function public.post_sale_return(
  p_idempotency_key text,
  p_shop_id uuid,
  p_sale_id uuid,
  p_business_date date,
  p_reason text,
  p_lines jsonb,
  p_refund_method public.payment_method default null
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid());
  nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  sale_row public.sales%rowtype;
  request private.sale_return_operation_requests%rowtype;
  return_id uuid := extensions.gen_random_uuid();
  refund_id uuid;
  return_journal_id uuid := extensions.gen_random_uuid();
  refund_journal_id uuid;
  receivable_account_id uuid;
  revenue_account_id uuid;
  inventory_account_id uuid;
  cogs_account_id uuid;
  refund_account_id uuid;
  fingerprint text;
  line_item jsonb;
  normalized_lines jsonb := '[]'::jsonb;
  sale_line public.sale_lines%rowtype;
  allocation_row record;
  return_line_id uuid;
  v_sale_line_id uuid;
  requested_quantity integer;
  prior_line_quantity bigint;
  prior_line_value bigint;
  line_return_value bigint;
  return_total bigint := 0;
  prior_return_total bigint;
  effective_receipts bigint;
  effective_sale bigint;
  due_before bigint;
  refund_amount bigint;
  allocation_available integer;
  allocation_quantity integer;
  quantity_remaining integer;
  restored_quantity integer := 0;
  restored_cost bigint := 0;
  movement_number integer := 0;
  total_sale_quantity bigint;
  total_returned_quantity bigint;
  result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null
     or length(trim(p_idempotency_key)) not between 1 and 160
     or p_reason is null or length(trim(p_reason)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid sale return request';
  end if;
  if not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active
  ) then raise exception using errcode='42501',message='not authorized'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',p_shop_id::text,p_sale_id::text,
    coalesce(p_business_date::text,''),trim(p_reason),coalesce(p_lines::text,''),
    coalesce(p_refund_method::text,'')),'sha256'),'hex');
  insert into private.sale_return_operation_requests(
    shop_id,idempotency_key,actor_user_id,request_fingerprint,sale_return_id
  ) values(p_shop_id,trim(p_idempotency_key),actor,fingerprint,return_id) on conflict do nothing;
  select * into request from private.sale_return_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  return_id:=request.sale_return_id;
  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window';
  end if;
  perform 1 from public.accounting_periods period
  where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  select * into sale_row from public.sales sale
  where sale.id=p_sale_id and sale.shop_id=p_shop_id for update;
  if not found or sale_row.status not in ('posted','partially_returned') then
    raise exception using errcode='42501',message='sale is not returnable';
  end if;
  if p_business_date<sale_row.business_date or p_business_date>sale_row.business_date+30 then
    raise exception using errcode='22023',message='sale return window has expired';
  end if;
  if p_lines is null or jsonb_typeof(p_lines)<>'array'
     or jsonb_array_length(p_lines) not between 1 and 100 then
    raise exception using errcode='22023',message='sale return lines are required';
  end if;

  begin
    if exists(select 1 from (
      select (value->>'sale_line_id')::uuid id,count(*) count
      from jsonb_array_elements(p_lines) group by 1 having count(*)>1
    ) duplicate) then raise exception using errcode='22023',message='duplicate sale return line'; end if;
    perform 1 from public.sale_lines line
    join (select distinct (value->>'sale_line_id')::uuid id from jsonb_array_elements(p_lines)) requested using(id)
    where line.sale_id=p_sale_id and line.shop_id=p_shop_id order by line.id for update of line;
    if (select count(*) from public.sale_lines line where line.sale_id=p_sale_id and line.shop_id=p_shop_id
        and line.id in(select (value->>'sale_line_id')::uuid from jsonb_array_elements(p_lines)))<>jsonb_array_length(p_lines) then
      raise exception using errcode='42501',message='sale return line is not available';
    end if;
    for line_item in select value from jsonb_array_elements(p_lines) loop
      v_sale_line_id:=(line_item->>'sale_line_id')::uuid;
      requested_quantity:=(line_item->>'quantity')::integer;
      if requested_quantity is null or requested_quantity<=0
         or (line_item->>'disposition') not in ('sellable','damaged') then
        raise exception using errcode='22023',message='invalid sale return line';
      end if;
      select * into sale_line from public.sale_lines where id=v_sale_line_id and sale_id=p_sale_id;
      select coalesce(sum(returned_line.quantity),0),coalesce(sum(returned_line.refund_value_paisa),0)
      into prior_line_quantity,prior_line_value
      from public.sale_return_lines returned_line
      join public.sale_returns returned on returned.id=returned_line.sale_return_id
      where returned.sale_id=p_sale_id and returned.status<>'reversed'
        and returned_line.sale_line_id=v_sale_line_id;
      if prior_line_quantity+requested_quantity>sale_line.quantity then
        raise exception using errcode='23514',message='sale line over-returned';
      end if;
      if prior_line_quantity+requested_quantity=sale_line.quantity then
        line_return_value:=sale_line.line_total_paisa-prior_line_value;
      else
        line_return_value:=least(
          round(sale_line.line_total_paisa::numeric*requested_quantity/sale_line.quantity)::bigint,
          sale_line.line_total_paisa-prior_line_value
        );
      end if;
      return_total:=return_total+line_return_value;
      normalized_lines:=normalized_lines||jsonb_build_array(jsonb_build_object(
        'sale_line_id',v_sale_line_id,'quantity',requested_quantity,
        'disposition',line_item->>'disposition','return_value_paisa',line_return_value
      ));
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid sale return line';
  end;

  select coalesce(sum(total_value_paisa),0) into prior_return_total
  from public.sale_returns where sale_id=p_sale_id and status='posted';
  select coalesce(sum(amount_paisa),0)-coalesce((
    select sum(refund.amount_paisa) from public.refunds refund
    join public.sale_returns returned on returned.id=refund.sale_return_id
    where returned.sale_id=p_sale_id and refund.status='posted'
  ),0) into effective_receipts
  from public.sale_payments where sale_id=p_sale_id and status='posted';
  effective_sale:=sale_row.grand_total_paisa-prior_return_total;
  due_before:=greatest(effective_sale-effective_receipts,0);
  refund_amount:=greatest(return_total-due_before,0);
  if refund_amount>0 and p_refund_method is null then
    raise exception using errcode='22023',message='refund method is required';
  end if;
  if refund_amount=0 and p_refund_method is not null then
    raise exception using errcode='22023',message='refund is not payable';
  end if;

  if return_total>0 then
    select id into receivable_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='accounts_receivable' and account_type='receivable' and active for update;
    select id into revenue_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='sales_revenue' and account_type='revenue' and active for update;
    if receivable_account_id is null or revenue_account_id is null then
      raise exception using errcode='55000',message='sale return accounts are unavailable';
    end if;
  end if;
  if exists(select 1 from jsonb_array_elements(normalized_lines) item where item->>'disposition'='sellable') then
    select id into inventory_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='inventory_control' and account_type='inventory' and active for update;
    select id into cogs_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code='cost_of_goods_sold' and account_type='cogs' and active for update;
    if inventory_account_id is null or cogs_account_id is null then
      raise exception using errcode='55000',message='return inventory accounts are unavailable';
    end if;
  end if;
  if refund_amount>0 then
    select id into refund_account_id from public.financial_accounts
    where shop_id=p_shop_id
      and purpose_code=case p_refund_method when 'cash' then 'cash_main' else 'bank_main' end
      and account_type=p_refund_method::text::public.financial_account_type and active for update;
    if refund_account_id is null then raise exception using errcode='55000',message='refund account is unavailable'; end if;
  end if;

  insert into public.sale_returns(id,shop_id,sale_id,status,reason,total_value_paisa,
    business_date,actor_user_id,idempotency_key,posted_at)
  values(return_id,p_shop_id,p_sale_id,'posted',trim(p_reason),return_total,p_business_date,
    actor,'sale-return:'||trim(p_idempotency_key)||':header',now());

  for line_item in select value from jsonb_array_elements(normalized_lines) loop
    v_sale_line_id:=(line_item->>'sale_line_id')::uuid;
    requested_quantity:=(line_item->>'quantity')::integer;
    line_return_value:=(line_item->>'return_value_paisa')::bigint;
    return_line_id:=extensions.gen_random_uuid();
    insert into public.sale_return_lines(id,shop_id,sale_return_id,sale_id,sale_line_id,
      quantity,disposition,refund_value_paisa)
    values(return_line_id,p_shop_id,return_id,p_sale_id,v_sale_line_id,requested_quantity,
      (line_item->>'disposition')::public.return_disposition,line_return_value);
    quantity_remaining:=requested_quantity;
    for allocation_row in
      select allocation.*,lot.received_at,lot.remaining_quantity,lot.original_quantity
      from public.sale_lot_allocations allocation
      join public.inventory_lots lot on lot.id=allocation.lot_id and lot.shop_id=allocation.shop_id
      where allocation.sale_line_id=v_sale_line_id and allocation.shop_id=p_shop_id
      order by lot.received_at desc,lot.id desc for update of allocation,lot
    loop
      exit when quantity_remaining=0;
      select allocation_row.quantity-coalesce(sum(returned_allocation.quantity),0)
      into allocation_available
      from public.sale_return_allocations returned_allocation
      join public.sale_return_lines returned_line on returned_line.id=returned_allocation.sale_return_line_id
      join public.sale_returns returned on returned.id=returned_line.sale_return_id
      where returned_allocation.sale_lot_allocation_id=allocation_row.id
        and returned.status<>'reversed';
      allocation_available:=coalesce(allocation_available,allocation_row.quantity);
      if allocation_available<=0 then continue; end if;
      allocation_quantity:=least(quantity_remaining,allocation_available);
      insert into public.sale_return_allocations(shop_id,sale_return_line_id,sale_line_id,
        sale_lot_allocation_id,quantity)
      values(p_shop_id,return_line_id,v_sale_line_id,allocation_row.id,allocation_quantity);
      if line_item->>'disposition'='sellable' then
        update public.inventory_lots set remaining_quantity=remaining_quantity+allocation_quantity
        where id=allocation_row.lot_id;
        update public.products set current_stock=current_stock+allocation_quantity
        where id=allocation_row.product_id;
        movement_number:=movement_number+1;
        insert into public.inventory_movements(shop_id,product_id,lot_id,movement_type,
          quantity_delta,unit_cost_paisa,source_type,source_id,business_date,actor_user_id,idempotency_key)
        values(p_shop_id,allocation_row.product_id,allocation_row.lot_id,'return',allocation_quantity,
          allocation_row.unit_cost_paisa,'sale_return',return_id::text,p_business_date,actor,
          'sale-return:'||trim(p_idempotency_key)||':movement:'||movement_number);
        restored_quantity:=restored_quantity+allocation_quantity;
        restored_cost:=restored_cost+allocation_quantity::bigint*allocation_row.unit_cost_paisa;
      end if;
      quantity_remaining:=quantity_remaining-allocation_quantity;
    end loop;
    if quantity_remaining<>0 then raise exception using errcode='23514',message='sale allocation over-returned'; end if;
  end loop;

  select sum(quantity) into total_sale_quantity from public.sale_lines where sale_id=p_sale_id;
  select coalesce(sum(returned_line.quantity),0) into total_returned_quantity
  from public.sale_return_lines returned_line
  join public.sale_returns returned on returned.id=returned_line.sale_return_id
  where returned.sale_id=p_sale_id and returned.status<>'reversed';
  update public.sales set status=case when total_returned_quantity=total_sale_quantity
    then 'returned'::public.sale_status else 'partially_returned'::public.sale_status end
  where id=p_sale_id;

  if return_total>0 or restored_cost>0 then
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,
      actor_user_id,idempotency_key)
    values(return_journal_id,p_shop_id,'sale_return','Sale return '||return_id,return_id,
      p_business_date,actor,'sale-return:'||trim(p_idempotency_key)||':journal');
    movement_number:=0;
    if return_total>0 then
      insert into public.journal_entries(shop_id,journal_transaction_id,line_number,
        financial_account_id,debit_paisa,credit_paisa) values
        (p_shop_id,return_journal_id,1,revenue_account_id,return_total,0),
        (p_shop_id,return_journal_id,2,receivable_account_id,0,return_total);
      movement_number:=2;
    end if;
    if restored_cost>0 then
      insert into public.journal_entries(shop_id,journal_transaction_id,line_number,
        financial_account_id,debit_paisa,credit_paisa) values
        (p_shop_id,return_journal_id,movement_number+1,inventory_account_id,restored_cost,0),
        (p_shop_id,return_journal_id,movement_number+2,cogs_account_id,0,restored_cost);
    end if;
  end if;

  if refund_amount>0 then
    refund_id:=extensions.gen_random_uuid(); refund_journal_id:=extensions.gen_random_uuid();
    insert into public.refunds(id,shop_id,sale_return_id,method,amount_paisa,business_date,
      actor_user_id,idempotency_key)
    values(refund_id,p_shop_id,return_id,p_refund_method,refund_amount,p_business_date,actor,
      'sale-return:'||trim(p_idempotency_key)||':refund');
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,
      actor_user_id,idempotency_key)
    values(refund_journal_id,p_shop_id,'refund','Sale refund',refund_id,p_business_date,actor,
      'sale-return:'||trim(p_idempotency_key)||':refund-journal');
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,
      financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,refund_journal_id,1,receivable_account_id,refund_amount,0),
      (p_shop_id,refund_journal_id,2,refund_account_id,0,refund_amount);
  end if;

  insert into public.notifications(shop_id,category,target_role,title,body,record_type,
    record_id,safe_payload,created_by,idempotency_key)
  values(p_shop_id,'return','owner','Sale return posted','Return value '||return_total||' paisa',
    'sale_return',return_id,jsonb_build_object('return_value_paisa',return_total,
      'refund_paisa',refund_amount,'restored_quantity',restored_quantity),actor,
    'sale-return:'||trim(p_idempotency_key)||':notification');
  result_payload:=jsonb_build_object('sale_return_id',return_id,'sale_id',p_sale_id,
    'return_value_paisa',return_total,'refund_paisa',refund_amount,
    'due_after_paisa',greatest(effective_sale-return_total-(effective_receipts-refund_amount),0),
    'restored_quantity',restored_quantity,'restored_cost_paisa',restored_cost,
    'sale_status',(select status from public.sales where id=p_sale_id));
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,
    record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','sale_return',return_id,result_payload,
    'sale-return:'||trim(p_idempotency_key)||':audit');
  update private.sale_return_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

alter table private.sale_return_operation_requests enable row level security;
revoke all on table private.sale_return_operation_requests from public,anon,authenticated;
revoke all on function public.post_sale_return(text,uuid,uuid,date,text,jsonb,public.payment_method)
from public,anon,authenticated;
grant execute on function public.post_sale_return(text,uuid,uuid,date,text,jsonb,public.payment_method)
to authenticated;

comment on function public.post_sale_return(text,uuid,uuid,date,text,jsonb,public.payment_method)
is 'Posts one Owner-only allocation-linked sale return, required refund, stock restoration, ledger, notification, and audit transaction.';

commit;
