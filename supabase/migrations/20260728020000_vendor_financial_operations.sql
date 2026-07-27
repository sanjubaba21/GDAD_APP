begin;

create table private.vendor_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  operation text not null,
  request_fingerprint text not null,
  record_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  constraint vendor_operation_key_not_blank check(length(trim(idempotency_key)) between 1 and 160),
  constraint vendor_operation_allowed check(operation in ('payment','return','reverse_payment','reverse_return')),
  constraint vendor_operation_fingerprint_format check(request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint vendor_operation_result_consistent check(
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function private.vendor_bill_due(target_bill_id uuid)
returns bigint language sql stable security definer set search_path='' as $$
  select greatest(bill.grand_total_paisa
    - coalesce((select sum(allocation.amount_paisa) from public.vendor_payment_allocations allocation
      join public.vendor_payments payment on payment.id=allocation.vendor_payment_id
      where allocation.purchase_bill_id=bill.id and payment.status='posted'),0)
    - coalesce((select sum(returned.total_value_paisa) from public.vendor_returns returned
      where returned.purchase_bill_id=bill.id and returned.status='posted'),0),0)
  from public.purchase_bills bill where bill.id=target_bill_id and bill.status<>'reversed'
$$;

create or replace function public.post_vendor_payment(
  p_idempotency_key text,p_shop_id uuid,p_vendor_id uuid,p_method public.payment_method,
  p_allocations jsonb,p_business_date date default (timezone('Asia/Kathmandu',now()))::date
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid()); nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  fingerprint text; request private.vendor_operation_requests%rowtype;
  payment_id uuid := extensions.gen_random_uuid(); journal_id uuid := extensions.gen_random_uuid();
  payable_account_id uuid; payment_account_id uuid; item jsonb;
  bill_id uuid; amount bigint; total bigint:=0; vendor_due_after bigint; result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid vendor payment request'; end if;
  if not exists(select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id and membership.role='owner'
      and membership.active and not profile.disabled and shop.active)
  then raise exception using errcode='42501',message='not authorized'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f','payment',p_shop_id::text,p_vendor_id::text,
    coalesce(p_method::text,''),coalesce(p_allocations::text,''),coalesce(p_business_date::text,'')),'sha256'),'hex');
  insert into private.vendor_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,'payment',fingerprint,payment_id) on conflict do nothing;
  select * into request from private.vendor_operation_requests where shop_id=p_shop_id
    and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'payment' or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  payment_id:=request.record_id;
  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window'; end if;
  perform 1 from public.accounting_periods period where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  perform 1 from public.vendors vendor where vendor.id=p_vendor_id and vendor.shop_id=p_shop_id and vendor.active for update;
  if not found then raise exception using errcode='42501',message='vendor is not available'; end if;
  if p_method is null or p_allocations is null or jsonb_typeof(p_allocations)<>'array'
    or jsonb_array_length(p_allocations) not between 1 and 100 then
    raise exception using errcode='22023',message='vendor payment allocations are required'; end if;
  begin
    if exists(select 1 from(select (value->>'purchase_bill_id')::uuid id,count(*)
      from jsonb_array_elements(p_allocations) group by 1 having count(*)>1) duplicate)
    then raise exception using errcode='22023',message='duplicate payment bill'; end if;
    perform 1 from public.purchase_bills candidate
    join(select distinct (value->>'purchase_bill_id')::uuid id from jsonb_array_elements(p_allocations)) requested using(id)
    where candidate.shop_id=p_shop_id and candidate.vendor_id=p_vendor_id and candidate.status<>'reversed'
    order by candidate.id for update of candidate;
    for item in select value from jsonb_array_elements(p_allocations) loop
      bill_id:=(item->>'purchase_bill_id')::uuid; amount:=(item->>'amount_paisa')::bigint;
      perform 1 from public.purchase_bills where id=bill_id and shop_id=p_shop_id and vendor_id=p_vendor_id;
      if not found then raise exception using errcode='42501',message='purchase bill is not available'; end if;
      if amount is null or amount<=0 then raise exception using errcode='22023',message='invalid payment allocation'; end if;
      if amount>private.vendor_bill_due(bill_id) then raise exception using errcode='23514',message='vendor payment exceeds bill due'; end if;
      total:=total+amount;
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid payment allocation';
  end;
  select id into payable_account_id from public.financial_accounts where shop_id=p_shop_id
    and purpose_code='accounts_payable' and account_type='payable' and active for update;
  select id into payment_account_id from public.financial_accounts where shop_id=p_shop_id
    and purpose_code=case p_method when 'cash' then 'cash_main' else 'bank_main' end
    and account_type=p_method::text::public.financial_account_type and active for update;
  if payable_account_id is null or payment_account_id is null then
    raise exception using errcode='55000',message='vendor payment accounts are unavailable'; end if;
  insert into public.vendor_payments(id,shop_id,vendor_id,method,amount_paisa,business_date,actor_user_id,idempotency_key)
  values(payment_id,p_shop_id,p_vendor_id,p_method,total,p_business_date,actor,
    'vendor-payment:'||trim(p_idempotency_key)||':header');
  for item in select value from jsonb_array_elements(p_allocations) loop
    insert into public.vendor_payment_allocations(shop_id,vendor_payment_id,vendor_id,purchase_bill_id,amount_paisa)
    values(p_shop_id,payment_id,p_vendor_id,(item->>'purchase_bill_id')::uuid,(item->>'amount_paisa')::bigint);
  end loop;
  insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,actor_user_id,idempotency_key)
  values(journal_id,p_shop_id,'vendor_payment','Vendor payment',payment_id,p_business_date,actor,
    'vendor-payment:'||trim(p_idempotency_key)||':journal');
  insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
    (p_shop_id,journal_id,1,payable_account_id,total,0),(p_shop_id,journal_id,2,payment_account_id,0,total);
  select coalesce(sum(private.vendor_bill_due(candidate.id)),0) into vendor_due_after
  from public.purchase_bills candidate where candidate.shop_id=p_shop_id and candidate.vendor_id=p_vendor_id
    and candidate.status<>'reversed';
  result_payload:=jsonb_build_object('vendor_payment_id',payment_id,'vendor_id',p_vendor_id,
    'amount_paisa',total,'allocation_count',jsonb_array_length(p_allocations),'vendor_due_after_paisa',vendor_due_after);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','vendor_payment',payment_id,result_payload,
    'vendor-payment:'||trim(p_idempotency_key)||':audit');
  update private.vendor_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create or replace function public.post_vendor_return(
  p_idempotency_key text,p_shop_id uuid,p_purchase_bill_id uuid,p_business_date date,
  p_reason text,p_lines jsonb
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid()); nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  fingerprint text; request private.vendor_operation_requests%rowtype;
  return_id uuid:=extensions.gen_random_uuid(); journal_id uuid:=extensions.gen_random_uuid();
  bill public.purchase_bills%rowtype; item jsonb; receipt_line public.purchase_receipt_lines%rowtype;
  lot public.inventory_lots%rowtype; receipt_line_id uuid; quantity integer; prior_quantity bigint;
  total bigint:=0; line_number integer:=0; returned_quantity bigint; received_quantity bigint;
  inventory_account_id uuid; payable_account_id uuid; vendor_due_after bigint; result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_reason is null or length(trim(p_reason)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid vendor return request'; end if;
  if not exists(select 1 from public.shop_memberships membership join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active)
  then raise exception using errcode='42501',message='not authorized'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f','return',p_shop_id::text,p_purchase_bill_id::text,
    coalesce(p_business_date::text,''),trim(p_reason),coalesce(p_lines::text,'')),'sha256'),'hex');
  insert into private.vendor_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,'return',fingerprint,return_id) on conflict do nothing;
  select * into request from private.vendor_operation_requests where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'return' or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  return_id:=request.record_id;
  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window'; end if;
  perform 1 from public.accounting_periods period where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  select * into bill from public.purchase_bills candidate where candidate.id=p_purchase_bill_id
    and candidate.shop_id=p_shop_id and candidate.status in ('received','partially_returned') for update;
  if not found then raise exception using errcode='42501',message='purchase bill is not returnable'; end if;
  if p_lines is null or jsonb_typeof(p_lines)<>'array' or jsonb_array_length(p_lines) not between 1 and 100 then
    raise exception using errcode='22023',message='vendor return lines are required'; end if;
  begin
    if exists(select 1 from(select (value->>'purchase_receipt_line_id')::uuid id,count(*)
      from jsonb_array_elements(p_lines) group by 1 having count(*)>1) duplicate)
    then raise exception using errcode='22023',message='duplicate vendor return line'; end if;
    perform 1 from public.inventory_lots candidate join(select distinct (value->>'purchase_receipt_line_id')::uuid id
      from jsonb_array_elements(p_lines)) requested on requested.id=candidate.purchase_receipt_line_id
      where candidate.shop_id=p_shop_id order by candidate.id for update of candidate;
    for item in select value from jsonb_array_elements(p_lines) loop
      receipt_line_id:=(item->>'purchase_receipt_line_id')::uuid; quantity:=(item->>'quantity')::integer;
      select * into receipt_line from public.purchase_receipt_lines where id=receipt_line_id
        and purchase_bill_id=p_purchase_bill_id and shop_id=p_shop_id;
      if not found then raise exception using errcode='42501',message='purchase receipt line is not available'; end if;
      select * into lot from public.inventory_lots where purchase_receipt_line_id=receipt_line_id
        and shop_id=p_shop_id and product_id=receipt_line.product_id for update;
      if not found then raise exception using errcode='42501',message='purchase lot is not available'; end if;
      select coalesce(sum(return_line.quantity),0) into prior_quantity from public.vendor_return_lines return_line
        join public.vendor_returns returned on returned.id=return_line.vendor_return_id
        where return_line.purchase_receipt_line_id=receipt_line_id and returned.status='posted';
      if quantity is null or quantity<=0 then raise exception using errcode='22023',message='invalid vendor return quantity'; end if;
      if prior_quantity+quantity>receipt_line.quantity or quantity>lot.remaining_quantity then
        raise exception using errcode='23514',message='vendor return exceeds available quantity'; end if;
      total:=total+quantity::bigint*lot.unit_cost_paisa;
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid vendor return line';
  end;
  if total>private.vendor_bill_due(p_purchase_bill_id) then
    raise exception using errcode='23514',message='vendor return exceeds unpaid bill due'; end if;
  select id into inventory_account_id from public.financial_accounts where shop_id=p_shop_id
    and purpose_code='inventory_control' and account_type='inventory' and active for update;
  select id into payable_account_id from public.financial_accounts where shop_id=p_shop_id
    and purpose_code='accounts_payable' and account_type='payable' and active for update;
  if total>0 and (inventory_account_id is null or payable_account_id is null) then
    raise exception using errcode='55000',message='vendor return accounts are unavailable'; end if;
  insert into public.vendor_returns(id,shop_id,vendor_id,purchase_bill_id,reason,total_value_paisa,
    business_date,actor_user_id,idempotency_key)
  values(return_id,p_shop_id,bill.vendor_id,p_purchase_bill_id,trim(p_reason),total,p_business_date,actor,
    'vendor-return:'||trim(p_idempotency_key)||':header');
  for item in select value from jsonb_array_elements(p_lines) loop
    line_number:=line_number+1; receipt_line_id:=(item->>'purchase_receipt_line_id')::uuid;
    quantity:=(item->>'quantity')::integer;
    select * into receipt_line from public.purchase_receipt_lines where id=receipt_line_id;
    select * into lot from public.inventory_lots where purchase_receipt_line_id=receipt_line_id;
    insert into public.vendor_return_lines(shop_id,vendor_return_id,purchase_bill_id,purchase_receipt_line_id,
      product_id,lot_id,quantity,unit_cost_paisa,line_total_paisa)
    values(p_shop_id,return_id,p_purchase_bill_id,receipt_line_id,receipt_line.product_id,lot.id,
      quantity,lot.unit_cost_paisa,quantity::bigint*lot.unit_cost_paisa);
    update public.inventory_lots set remaining_quantity=remaining_quantity-quantity where id=lot.id;
    update public.products set current_stock=current_stock-quantity where id=receipt_line.product_id;
    insert into public.inventory_movements(shop_id,product_id,lot_id,movement_type,quantity_delta,
      unit_cost_paisa,source_type,source_id,business_date,actor_user_id,idempotency_key)
    values(p_shop_id,receipt_line.product_id,lot.id,'vendor_return',-quantity,lot.unit_cost_paisa,
      'vendor_return',return_id::text,p_business_date,actor,
      'vendor-return:'||trim(p_idempotency_key)||':movement:'||line_number);
  end loop;
  select coalesce(sum(received.quantity),0) into received_quantity
  from public.purchase_receipt_lines received where received.purchase_bill_id=p_purchase_bill_id;
  select coalesce(sum(return_line.quantity),0) into returned_quantity
  from public.vendor_return_lines return_line join public.vendor_returns returned
    on returned.id=return_line.vendor_return_id
  where return_line.purchase_bill_id=p_purchase_bill_id and returned.status='posted';
  update public.purchase_bills set status=case when returned_quantity=received_quantity then 'returned'::public.purchase_bill_status
    else 'partially_returned'::public.purchase_bill_status end where id=p_purchase_bill_id;
  if total>0 then
    insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,actor_user_id,idempotency_key)
    values(journal_id,p_shop_id,'vendor_return','Vendor return',return_id,p_business_date,actor,
      'vendor-return:'||trim(p_idempotency_key)||':journal');
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,journal_id,1,payable_account_id,total,0),(p_shop_id,journal_id,2,inventory_account_id,0,total);
  end if;
  vendor_due_after:=private.vendor_bill_due(p_purchase_bill_id);
  insert into public.notifications(shop_id,category,target_role,title,body,record_type,record_id,
    safe_payload,created_by,idempotency_key)
  values(p_shop_id,'purchase','owner','Vendor return posted','Return value '||total||' paisa',
    'vendor_return',return_id,jsonb_build_object('return_value_paisa',total,'bill_due_after_paisa',vendor_due_after),
    actor,'vendor-return:'||trim(p_idempotency_key)||':notification');
  result_payload:=jsonb_build_object('vendor_return_id',return_id,'purchase_bill_id',p_purchase_bill_id,
    'return_value_paisa',total,'bill_due_after_paisa',vendor_due_after,'line_count',jsonb_array_length(p_lines));
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','vendor_return',return_id,result_payload,
    'vendor-return:'||trim(p_idempotency_key)||':audit');
  update private.vendor_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create or replace function public.reverse_vendor_event(
  p_idempotency_key text,p_shop_id uuid,p_event_type text,p_event_id uuid,
  p_business_date date,p_reason text
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid:=(select auth.uid()); nepal_today date:=(timezone('Asia/Kathmandu',now()))::date;
  operation text; fingerprint text; request private.vendor_operation_requests%rowtype;
  reversal_id uuid:=extensions.gen_random_uuid(); original_journal public.journal_transactions%rowtype;
  returned public.vendor_returns%rowtype; line public.vendor_return_lines%rowtype;
  result_payload jsonb; movement_number integer:=0;
begin
  if actor is null or p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_event_type is null or p_event_type not in ('payment','return')
    or p_reason is null or length(trim(p_reason)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid vendor reversal request'; end if;
  if not exists(select 1 from public.shop_memberships membership join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active)
  then raise exception using errcode='42501',message='not authorized'; end if;
  operation:='reverse_'||p_event_type;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',operation,p_shop_id::text,p_event_id::text,
    coalesce(p_business_date::text,''),trim(p_reason)),'sha256'),'hex');
  insert into private.vendor_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,operation,fingerprint,reversal_id) on conflict do nothing;
  select * into request from private.vendor_operation_requests where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>operation or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  reversal_id:=request.record_id;
  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window'; end if;
  perform 1 from public.accounting_periods period where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  if p_event_type='payment' then
    perform 1 from public.vendor_payments where id=p_event_id and shop_id=p_shop_id and status='posted' for update;
    if not found then raise exception using errcode='42501',message='vendor payment is not reversible'; end if;
    select * into original_journal from public.journal_transactions where shop_id=p_shop_id and kind='vendor_payment'
      and source_id=p_event_id for update;
    update public.vendor_payments set status='reversed',reversed_at=now(),reversed_by=actor,reversal_reason=trim(p_reason)
      where id=p_event_id;
  else
    select * into returned from public.vendor_returns where id=p_event_id and shop_id=p_shop_id and status='posted' for update;
    if not found then raise exception using errcode='42501',message='vendor return is not reversible'; end if;
    select * into original_journal from public.journal_transactions where shop_id=p_shop_id and kind='vendor_return'
      and source_id=p_event_id for update;
    for line in select * from public.vendor_return_lines where vendor_return_id=p_event_id order by lot_id for update loop
      update public.inventory_lots set remaining_quantity=remaining_quantity+line.quantity where id=line.lot_id;
      update public.products set current_stock=current_stock+line.quantity where id=line.product_id;
      movement_number:=movement_number+1;
      insert into public.inventory_movements(shop_id,product_id,lot_id,movement_type,quantity_delta,
        unit_cost_paisa,source_type,source_id,reason,business_date,actor_user_id,idempotency_key)
      values(p_shop_id,line.product_id,line.lot_id,'vendor_return_reversal',line.quantity,line.unit_cost_paisa,
        'vendor_return_reversal',p_event_id::text,trim(p_reason),p_business_date,actor,
        'vendor-reversal:'||trim(p_idempotency_key)||':movement:'||movement_number);
    end loop;
    update public.vendor_returns set status='reversed',reversed_at=now(),reversed_by=actor,reversal_reason=trim(p_reason)
      where id=p_event_id;
    update public.purchase_bills set status=case when exists(select 1 from public.vendor_returns candidate
      where candidate.purchase_bill_id=returned.purchase_bill_id and candidate.status='posted')
      then 'partially_returned'::public.purchase_bill_status else 'received'::public.purchase_bill_status end
      where id=returned.purchase_bill_id;
  end if;
  if original_journal.id is not null then
    insert into public.journal_transactions(id,shop_id,kind,description,reversal_of_id,business_date,actor_user_id,idempotency_key)
    values(reversal_id,p_shop_id,'reversal','Vendor event reversal',original_journal.id,p_business_date,actor,
      'vendor-reversal:'||trim(p_idempotency_key)||':journal');
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa)
    select p_shop_id,reversal_id,row_number() over(order by entry.line_number),entry.financial_account_id,
      entry.credit_paisa,entry.debit_paisa from public.journal_entries entry
    where entry.journal_transaction_id=original_journal.id order by entry.line_number;
  end if;
  result_payload:=jsonb_build_object('event_type',p_event_type,'event_id',p_event_id,
    'reversal_journal_id',case when original_journal.id is null then null else reversal_id end);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,operation,'vendor_'||p_event_type,p_event_id,result_payload,
    'vendor-reversal:'||trim(p_idempotency_key)||':audit');
  update private.vendor_operation_requests set result=result_payload,completed_at=now()
    where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

alter table private.vendor_operation_requests enable row level security;
revoke all on table private.vendor_operation_requests from public,anon,authenticated;
revoke all on function private.vendor_bill_due(uuid) from public,anon,authenticated;
revoke all on function public.post_vendor_payment(text,uuid,uuid,public.payment_method,jsonb,date),
  public.post_vendor_return(text,uuid,uuid,date,text,jsonb),
  public.reverse_vendor_event(text,uuid,text,uuid,date,text) from public,anon,authenticated;
grant execute on function public.post_vendor_payment(text,uuid,uuid,public.payment_method,jsonb,date),
  public.post_vendor_return(text,uuid,uuid,date,text,jsonb),
  public.reverse_vendor_event(text,uuid,text,uuid,date,text) to authenticated;

comment on function public.post_vendor_payment(text,uuid,uuid,public.payment_method,jsonb,date)
is 'Posts one fully allocated Owner-only vendor payment and balanced journal.';
comment on function public.post_vendor_return(text,uuid,uuid,date,text,jsonb)
is 'Posts one unpaid-value-capped, lot-linked Owner-only vendor return.';
comment on function public.reverse_vendor_event(text,uuid,text,uuid,date,text)
is 'Reverses one vendor payment or return with compensating ledger and stock evidence.';

commit;
