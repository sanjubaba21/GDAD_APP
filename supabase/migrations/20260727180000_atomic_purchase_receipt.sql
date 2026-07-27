begin;

create table private.purchase_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  purchase_bill_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  constraint purchase_operation_key_valid check(length(trim(idempotency_key)) between 1 and 160),
  constraint purchase_operation_fingerprint_valid check(request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint purchase_operation_completion_consistent check(
    (completed_at is null and result is null) or (completed_at is not null and result is not null)
  )
);

create or replace function public.post_purchase_receipt(
  p_idempotency_key text,
  p_shop_id uuid,
  p_vendor_id uuid,
  p_invoice_reference text,
  p_business_date date,
  p_lines jsonb,
  p_payment_amount_paisa bigint default 0,
  p_payment_method public.payment_method default null
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare
  actor uuid := (select auth.uid());
  nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
  fingerprint text;
  request private.purchase_operation_requests%rowtype;
  bill_id uuid := extensions.gen_random_uuid();
  receipt_id uuid := extensions.gen_random_uuid();
  payment_id uuid;
  purchase_journal_id uuid := extensions.gen_random_uuid();
  payment_journal_id uuid;
  inventory_account_id uuid;
  payable_account_id uuid;
  payment_account_id uuid;
  line_item jsonb;
  product_row public.products%rowtype;
  bill_line_id uuid;
  receipt_line_id uuid;
  lot_id uuid;
  line_number integer := 0;
  product_id uuid;
  quantity integer;
  unit_cost bigint;
  line_total bigint;
  grand_total bigint := 0;
  result_payload jsonb;
begin
  if actor is null or p_idempotency_key is null
     or length(trim(p_idempotency_key)) not between 1 and 160 then
    raise exception using errcode='22023',message='invalid purchase request';
  end if;
  if not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=p_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active
  ) then raise exception using errcode='42501',message='not authorized'; end if;
  if p_business_date is null or p_business_date>nepal_today or p_business_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window';
  end if;
  perform 1 from public.accounting_periods period
  where period.shop_id=p_shop_id and period.status='open'
    and p_business_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
  perform 1 from public.vendors vendor
  where vendor.id=p_vendor_id and vendor.shop_id=p_shop_id and vendor.active for update;
  if not found then raise exception using errcode='42501',message='vendor is not available'; end if;
  if p_invoice_reference is not null and length(trim(p_invoice_reference))=0 then
    raise exception using errcode='22023',message='invalid invoice reference';
  end if;
  if p_lines is null or jsonb_typeof(p_lines)<>'array'
     or jsonb_array_length(p_lines) not between 1 and 100 then
    raise exception using errcode='22023',message='purchase lines are required';
  end if;
  if p_payment_amount_paisa is null or p_payment_amount_paisa<0
     or (p_payment_amount_paisa=0 and p_payment_method is not null)
     or (p_payment_amount_paisa>0 and p_payment_method is null) then
    raise exception using errcode='22023',message='invalid purchase payment';
  end if;

  begin
    if exists(
      select 1 from (
        select (value->>'product_id')::uuid product_id,count(*) count
        from jsonb_array_elements(p_lines) group by 1 having count(*)>1
      ) duplicate
    ) then raise exception using errcode='22023',message='duplicate purchase product'; end if;
    for line_item in select value from jsonb_array_elements(p_lines) loop
      product_id := (line_item->>'product_id')::uuid;
      quantity := (line_item->>'quantity')::integer;
      unit_cost := (line_item->>'unit_cost_paisa')::bigint;
      if quantity<=0 or unit_cost<0 then raise exception using errcode='22023',message='invalid purchase line'; end if;
      line_total := quantity::bigint*unit_cost;
      if quantity<>0 and line_total/quantity<>unit_cost then
        raise exception using errcode='22003',message='purchase line total overflow';
      end if;
      grand_total := grand_total+line_total;
      if grand_total<line_total then raise exception using errcode='22003',message='purchase total overflow'; end if;
    end loop;
  exception when invalid_text_representation or numeric_value_out_of_range then
    raise exception using errcode='22023',message='invalid purchase line';
  end;
  if p_payment_amount_paisa>grand_total then
    raise exception using errcode='23514',message='vendor payment exceeds purchase total';
  end if;

  fingerprint := encode(extensions.digest(concat_ws(E'\x1f',p_shop_id::text,p_vendor_id::text,
    coalesce(private.normalize_product_code(p_invoice_reference),''),p_business_date::text,
    p_lines::text,p_payment_amount_paisa::text,coalesce(p_payment_method::text,'')),'sha256'),'hex');
  insert into private.purchase_operation_requests(
    shop_id,idempotency_key,actor_user_id,request_fingerprint,purchase_bill_id
  ) values(p_shop_id,trim(p_idempotency_key),actor,fingerprint,bill_id) on conflict do nothing;
  select * into request from private.purchase_operation_requests
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then return request.result; end if;
  bill_id := request.purchase_bill_id;

  select id into inventory_account_id from public.financial_accounts
  where shop_id=p_shop_id and purpose_code='inventory_control' and account_type='inventory' and active for update;
  if inventory_account_id is null then raise exception using errcode='55000',message='inventory control account is unavailable'; end if;
  select id into payable_account_id from public.financial_accounts
  where shop_id=p_shop_id and purpose_code='accounts_payable' and account_type='payable' and active for update;
  if payable_account_id is null then raise exception using errcode='55000',message='accounts payable account is unavailable'; end if;
  if p_payment_amount_paisa>0 then
    select id into payment_account_id from public.financial_accounts
    where shop_id=p_shop_id and purpose_code=case p_payment_method when 'cash' then 'cash_main' else 'bank_main' end
      and account_type=p_payment_method::text::public.financial_account_type and active for update;
    if payment_account_id is null then raise exception using errcode='55000',message='payment account is unavailable'; end if;
  end if;

  perform 1 from public.products product
  join (select distinct (value->>'product_id')::uuid id from jsonb_array_elements(p_lines)) requested using(id)
  where product.shop_id=p_shop_id and product.active order by product.id for update of product;
  if (select count(*) from public.products product where product.shop_id=p_shop_id and product.active
      and product.id in(select (value->>'product_id')::uuid from jsonb_array_elements(p_lines)))<>jsonb_array_length(p_lines) then
    raise exception using errcode='42501',message='purchase product is not available';
  end if;

  insert into public.purchase_bills(
    id,shop_id,vendor_id,status,invoice_reference,invoice_date,subtotal_paisa,
    discount_total_paisa,tax_total_paisa,grand_total_paisa,business_date,actor_user_id,
    idempotency_key,posted_at
  ) values(bill_id,p_shop_id,p_vendor_id,'received',nullif(trim(p_invoice_reference),''),
    p_business_date,grand_total,0,0,grand_total,p_business_date,actor,
    'purchase:'||trim(p_idempotency_key)||':bill',now());
  insert into public.purchase_receipts(id,shop_id,purchase_bill_id,business_date,actor_user_id,idempotency_key)
  values(receipt_id,p_shop_id,bill_id,p_business_date,actor,'purchase:'||trim(p_idempotency_key)||':receipt');

  for line_item in select value from jsonb_array_elements(p_lines) loop
    line_number:=line_number+1;
    product_id:=(line_item->>'product_id')::uuid;
    quantity:=(line_item->>'quantity')::integer;
    unit_cost:=(line_item->>'unit_cost_paisa')::bigint;
    line_total:=quantity::bigint*unit_cost;
    select * into product_row from public.products where id=product_id and shop_id=p_shop_id;
    bill_line_id:=extensions.gen_random_uuid(); receipt_line_id:=extensions.gen_random_uuid(); lot_id:=extensions.gen_random_uuid();
    insert into public.purchase_bill_lines(
      id,shop_id,purchase_bill_id,line_number,product_id,product_name,sku_code,quantity,
      unit_cost_paisa,gross_total_paisa,discount_paisa,line_total_paisa
    ) values(bill_line_id,p_shop_id,bill_id,line_number,product_id,product_row.name,product_row.sku_code,
      quantity,unit_cost,line_total,0,line_total);
    insert into public.purchase_receipt_lines(
      id,shop_id,purchase_receipt_id,purchase_bill_id,purchase_bill_line_id,product_id,
      quantity,unit_cost_paisa,line_total_paisa
    ) values(receipt_line_id,p_shop_id,receipt_id,bill_id,bill_line_id,product_id,quantity,unit_cost,line_total);
    insert into public.inventory_lots(
      id,shop_id,product_id,source_type,source_id,unit_cost_paisa,original_quantity,
      remaining_quantity,purchase_receipt_line_id
    ) values(lot_id,p_shop_id,product_id,'purchase_receipt',receipt_id::text,unit_cost,quantity,quantity,receipt_line_id);
    insert into public.inventory_movements(
      shop_id,product_id,lot_id,movement_type,quantity_delta,unit_cost_paisa,source_type,
      source_id,business_date,actor_user_id,idempotency_key
    ) values(p_shop_id,product_id,lot_id,'purchase',quantity,unit_cost,'purchase_receipt',
      receipt_id::text,p_business_date,actor,'purchase:'||trim(p_idempotency_key)||':movement:'||line_number);
    update public.products set current_stock=current_stock+quantity where id=product_id;
  end loop;

  insert into public.journal_transactions(
    id,shop_id,kind,description,source_id,business_date,actor_user_id,idempotency_key
  ) values(purchase_journal_id,p_shop_id,'purchase_receipt','Purchase receipt '||coalesce(nullif(trim(p_invoice_reference),''),bill_id::text),
    receipt_id,p_business_date,actor,'purchase:'||trim(p_idempotency_key)||':journal');
  insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
    (p_shop_id,purchase_journal_id,1,inventory_account_id,grand_total,0),
    (p_shop_id,purchase_journal_id,2,payable_account_id,0,grand_total);

  if p_payment_amount_paisa>0 then
    payment_id:=extensions.gen_random_uuid(); payment_journal_id:=extensions.gen_random_uuid();
    insert into public.vendor_payments(
      id,shop_id,vendor_id,method,amount_paisa,business_date,actor_user_id,idempotency_key
    ) values(payment_id,p_shop_id,p_vendor_id,p_payment_method,p_payment_amount_paisa,
      p_business_date,actor,'purchase:'||trim(p_idempotency_key)||':payment');
    insert into public.vendor_payment_allocations(shop_id,vendor_payment_id,vendor_id,purchase_bill_id,amount_paisa)
    values(p_shop_id,payment_id,p_vendor_id,bill_id,p_payment_amount_paisa);
    insert into public.journal_transactions(
      id,shop_id,kind,description,source_id,business_date,actor_user_id,idempotency_key
    ) values(payment_journal_id,p_shop_id,'vendor_payment','Purchase payment',payment_id,
      p_business_date,actor,'purchase:'||trim(p_idempotency_key)||':payment-journal');
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,payment_journal_id,1,payable_account_id,p_payment_amount_paisa,0),
      (p_shop_id,payment_journal_id,2,payment_account_id,0,p_payment_amount_paisa);
  end if;

  result_payload:=jsonb_build_object(
    'purchase_bill_id',bill_id,'purchase_receipt_id',receipt_id,'vendor_payment_id',payment_id,
    'grand_total_paisa',grand_total,'paid_paisa',p_payment_amount_paisa,
    'due_paisa',grand_total-p_payment_amount_paisa,'line_count',line_number
  );
  insert into private.business_audit_events(
    shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key
  ) values(p_shop_id,actor,'post','purchase_bill',bill_id,result_payload,
    'purchase:'||trim(p_idempotency_key)||':audit');
  update private.purchase_operation_requests set result=result_payload,completed_at=now()
  where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

alter table private.purchase_operation_requests enable row level security;
revoke all on table private.purchase_operation_requests from public,anon,authenticated;
revoke all on function public.post_purchase_receipt(text,uuid,uuid,text,date,jsonb,bigint,public.payment_method)
from public,anon,authenticated;
grant execute on function public.post_purchase_receipt(text,uuid,uuid,text,date,jsonb,bigint,public.payment_method)
to authenticated;

comment on function public.post_purchase_receipt(text,uuid,uuid,text,date,jsonb,bigint,public.payment_method)
is 'Owner-only atomic purchase receipt: bill, FIFO lots, stock, payment/due, balanced journals, audit, and idempotent replay.';
commit;
