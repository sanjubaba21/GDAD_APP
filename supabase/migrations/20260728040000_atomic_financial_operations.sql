begin;

alter table public.journal_transactions drop constraint journal_transactions_source_consistent;
alter table public.journal_transactions add constraint journal_transactions_source_consistent check (
  (kind in ('opening_balance','deposit','withdrawal','transfer','correction','reversal') and source_id is null)
  or (kind not in ('opening_balance','deposit','withdrawal','transfer','correction','reversal') and source_id is not null)
);

create table private.financial_operation_requests (
  shop_id uuid not null references public.shops(id) on delete restrict,
  idempotency_key text not null,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  operation text not null check (operation in ('expense','deposit','withdrawal','transfer','reversal')),
  request_fingerprint text not null check (request_fingerprint ~ '^[0-9a-f]{64}$'),
  record_id uuid not null,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  primary key(shop_id,idempotency_key),
  check ((result is null and completed_at is null) or (result is not null and completed_at is not null))
);

create or replace function private.financial_account_balance(target_account_id uuid)
returns bigint language sql stable security definer set search_path='' as $$
  select coalesce(sum(entry.debit_paisa-entry.credit_paisa),0)::bigint
  from public.journal_entries entry where entry.financial_account_id=target_account_id
$$;

create or replace function private.assert_financial_actor(target_shop_id uuid)
returns uuid language plpgsql stable security definer set search_path='' as $$
declare actor uuid := (select auth.uid());
begin
  if actor is null or not exists(
    select 1 from public.shop_memberships membership
    join public.user_profiles profile on profile.user_id=membership.user_id
    join public.shops shop on shop.id=membership.shop_id
    where membership.user_id=actor and membership.shop_id=target_shop_id
      and membership.role='owner' and membership.active and not profile.disabled and shop.active
  ) then raise exception using errcode='42501',message='not authorized'; end if;
  return actor;
end;
$$;

create or replace function private.assert_financial_business_date(target_shop_id uuid,target_date date)
returns void language plpgsql security definer set search_path='' as $$
declare nepal_today date := (timezone('Asia/Kathmandu',now()))::date;
begin
  if target_date is null or target_date>nepal_today or target_date<nepal_today-7 then
    raise exception using errcode='22023',message='business date is outside allowed window'; end if;
  perform 1 from public.accounting_periods period where period.shop_id=target_shop_id
    and period.status='open' and target_date between period.date_from and period.date_to for update;
  if not found then raise exception using errcode='55000',message='business date is not in an open period'; end if;
end;
$$;

create or replace function public.post_expense(
  p_idempotency_key text,p_shop_id uuid,p_source_account_id uuid,p_amount_paisa bigint,
  p_business_date date,p_category text,p_payee text default null,p_note text default null
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid; request private.financial_operation_requests%rowtype; fingerprint text;
  expense_id uuid:=gen_random_uuid(); journal_id uuid:=gen_random_uuid(); expense_account_id uuid;
  source_balance bigint; result_payload jsonb;
begin
  actor:=private.assert_financial_actor(p_shop_id);
  if p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_amount_paisa is null or p_amount_paisa<=0
    or p_category is null or length(trim(p_category)) not between 1 and 120
    or (p_payee is not null and length(trim(p_payee)) not between 1 and 200)
    or (p_note is not null and length(trim(p_note)) not between 1 and 500) then
    raise exception using errcode='22023',message='invalid expense request'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f','expense',p_shop_id::text,p_source_account_id::text,
    p_amount_paisa::text,coalesce(p_business_date::text,''),trim(p_category),coalesce(trim(p_payee),''),
    coalesce(trim(p_note),'')),'sha256'),'hex');
  insert into private.financial_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,'expense',fingerprint,expense_id) on conflict do nothing;
  select * into request from private.financial_operation_requests where shop_id=p_shop_id
    and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'expense' or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  expense_id:=request.record_id;
  perform private.assert_financial_business_date(p_shop_id,p_business_date);
  perform 1 from public.financial_accounts account where account.id=p_source_account_id
    and account.shop_id=p_shop_id and account.account_type in ('cash','bank') and account.active for update;
  if not found then raise exception using errcode='42501',message='source account is not available'; end if;
  select id into expense_account_id from public.financial_accounts account where account.shop_id=p_shop_id
    and account.purpose_code='expense_control' and account.account_type='expense' and account.active for update;
  if expense_account_id is null then raise exception using errcode='55000',message='expense account is unavailable'; end if;
  source_balance:=private.financial_account_balance(p_source_account_id);
  if source_balance<p_amount_paisa then raise exception using errcode='23514',message='insufficient account funds'; end if;
  insert into public.expenses(id,shop_id,category,payee,note,amount_paisa,journal_transaction_id,
    business_date,actor_user_id,idempotency_key)
  values(expense_id,p_shop_id,trim(p_category),case when p_payee is null then null else trim(p_payee) end,
    case when p_note is null then null else trim(p_note) end,p_amount_paisa,journal_id,p_business_date,actor,
    'financial-expense:'||trim(p_idempotency_key)||':header');
  insert into public.journal_transactions(id,shop_id,kind,description,source_id,business_date,actor_user_id,idempotency_key)
  values(journal_id,p_shop_id,'expense','Expense: '||trim(p_category),expense_id,p_business_date,actor,
    'financial-expense:'||trim(p_idempotency_key)||':journal');
  insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
    (p_shop_id,journal_id,1,expense_account_id,p_amount_paisa,0),
    (p_shop_id,journal_id,2,p_source_account_id,0,p_amount_paisa);
  result_payload:=jsonb_build_object('expense_id',expense_id,'journal_transaction_id',journal_id,
    'amount_paisa',p_amount_paisa,'source_balance_after_paisa',source_balance-p_amount_paisa);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','expense',expense_id,result_payload,'financial-expense:'||trim(p_idempotency_key)||':audit');
  update private.financial_operation_requests set result=result_payload,completed_at=now()
    where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create or replace function public.post_cash_movement(
  p_idempotency_key text,p_shop_id uuid,p_movement_type text,p_account_id uuid,
  p_amount_paisa bigint,p_business_date date,p_description text
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid; request private.financial_operation_requests%rowtype; fingerprint text;
  journal_id uuid:=gen_random_uuid(); clearing_account_id uuid; account_balance bigint; result_payload jsonb;
begin
  actor:=private.assert_financial_actor(p_shop_id);
  if p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_movement_type is null or p_movement_type not in ('deposit','withdrawal')
    or p_amount_paisa is null or p_amount_paisa<=0
    or p_description is null or length(trim(p_description)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid cash movement request'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f',p_movement_type,p_shop_id::text,p_account_id::text,
    p_amount_paisa::text,coalesce(p_business_date::text,''),trim(p_description)),'sha256'),'hex');
  insert into private.financial_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,p_movement_type,fingerprint,journal_id) on conflict do nothing;
  select * into request from private.financial_operation_requests where shop_id=p_shop_id
    and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>p_movement_type or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  journal_id:=request.record_id;
  perform private.assert_financial_business_date(p_shop_id,p_business_date);
  perform 1 from public.financial_accounts account where account.id=p_account_id and account.shop_id=p_shop_id
    and account.account_type in ('cash','bank') and account.active for update;
  if not found then raise exception using errcode='42501',message='cash or bank account is not available'; end if;
  select id into clearing_account_id from public.financial_accounts account where account.shop_id=p_shop_id
    and account.purpose_code='cash_movement_clearing' and account.account_type='clearing' and account.active for update;
  if clearing_account_id is null then raise exception using errcode='55000',message='cash movement clearing account is unavailable'; end if;
  account_balance:=private.financial_account_balance(p_account_id);
  if p_movement_type='withdrawal' and account_balance<p_amount_paisa then
    raise exception using errcode='23514',message='insufficient account funds'; end if;
  insert into public.journal_transactions(id,shop_id,kind,description,business_date,actor_user_id,idempotency_key)
  values(journal_id,p_shop_id,p_movement_type::public.journal_kind,trim(p_description),p_business_date,actor,
    'financial-'||p_movement_type||':'||trim(p_idempotency_key)||':journal');
  if p_movement_type='deposit' then
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,journal_id,1,p_account_id,p_amount_paisa,0),(p_shop_id,journal_id,2,clearing_account_id,0,p_amount_paisa);
  else
    insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
      (p_shop_id,journal_id,1,clearing_account_id,p_amount_paisa,0),(p_shop_id,journal_id,2,p_account_id,0,p_amount_paisa);
  end if;
  result_payload:=jsonb_build_object('movement_type',p_movement_type,'journal_transaction_id',journal_id,
    'amount_paisa',p_amount_paisa,'account_balance_after_paisa',
    account_balance+case when p_movement_type='deposit' then p_amount_paisa else -p_amount_paisa end);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post',p_movement_type,journal_id,result_payload,
    'financial-'||p_movement_type||':'||trim(p_idempotency_key)||':audit');
  update private.financial_operation_requests set result=result_payload,completed_at=now()
    where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create or replace function public.post_account_transfer(
  p_idempotency_key text,p_shop_id uuid,p_from_account_id uuid,p_to_account_id uuid,
  p_amount_paisa bigint,p_business_date date,p_description text
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid; request private.financial_operation_requests%rowtype; fingerprint text;
  journal_id uuid:=gen_random_uuid(); from_balance bigint; to_balance bigint; locked_count integer; result_payload jsonb;
begin
  actor:=private.assert_financial_actor(p_shop_id);
  if p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_from_account_id is null or p_to_account_id is null or p_from_account_id=p_to_account_id
    or p_amount_paisa is null or p_amount_paisa<=0
    or p_description is null or length(trim(p_description)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid account transfer request'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f','transfer',p_shop_id::text,p_from_account_id::text,
    p_to_account_id::text,p_amount_paisa::text,coalesce(p_business_date::text,''),trim(p_description)),'sha256'),'hex');
  insert into private.financial_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,'transfer',fingerprint,journal_id) on conflict do nothing;
  select * into request from private.financial_operation_requests where shop_id=p_shop_id
    and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'transfer' or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  journal_id:=request.record_id;
  perform private.assert_financial_business_date(p_shop_id,p_business_date);
  select count(*) into locked_count from (
    select 1 from public.financial_accounts account where account.id in (p_from_account_id,p_to_account_id)
      and account.shop_id=p_shop_id and account.account_type in ('cash','bank') and account.active
      order by account.id for update
  ) locked;
  if locked_count<>2 then raise exception using errcode='42501',message='transfer account is not available'; end if;
  from_balance:=private.financial_account_balance(p_from_account_id);
  to_balance:=private.financial_account_balance(p_to_account_id);
  if from_balance<p_amount_paisa then raise exception using errcode='23514',message='insufficient account funds'; end if;
  insert into public.journal_transactions(id,shop_id,kind,description,business_date,actor_user_id,idempotency_key)
  values(journal_id,p_shop_id,'transfer',trim(p_description),p_business_date,actor,
    'financial-transfer:'||trim(p_idempotency_key)||':journal');
  insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa) values
    (p_shop_id,journal_id,1,p_to_account_id,p_amount_paisa,0),(p_shop_id,journal_id,2,p_from_account_id,0,p_amount_paisa);
  result_payload:=jsonb_build_object('journal_transaction_id',journal_id,'amount_paisa',p_amount_paisa,
    'from_balance_after_paisa',from_balance-p_amount_paisa,'to_balance_after_paisa',to_balance+p_amount_paisa);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'post','transfer',journal_id,result_payload,'financial-transfer:'||trim(p_idempotency_key)||':audit');
  update private.financial_operation_requests set result=result_payload,completed_at=now()
    where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

create or replace function public.reverse_financial_operation(
  p_idempotency_key text,p_shop_id uuid,p_journal_transaction_id uuid,p_business_date date,p_reason text
)
returns jsonb language plpgsql security definer set search_path='' as $$
declare actor uuid; request private.financial_operation_requests%rowtype; fingerprint text;
  original public.journal_transactions%rowtype; reversal_id uuid:=gen_random_uuid(); required record; result_payload jsonb;
begin
  actor:=private.assert_financial_actor(p_shop_id);
  if p_idempotency_key is null or length(trim(p_idempotency_key)) not between 1 and 160
    or p_reason is null or length(trim(p_reason)) not between 1 and 500 then
    raise exception using errcode='22023',message='invalid financial reversal request'; end if;
  fingerprint:=encode(extensions.digest(concat_ws(E'\x1f','reversal',p_shop_id::text,p_journal_transaction_id::text,
    coalesce(p_business_date::text,''),trim(p_reason)),'sha256'),'hex');
  insert into private.financial_operation_requests(shop_id,idempotency_key,actor_user_id,operation,request_fingerprint,record_id)
  values(p_shop_id,trim(p_idempotency_key),actor,'reversal',fingerprint,reversal_id) on conflict do nothing;
  select * into request from private.financial_operation_requests where shop_id=p_shop_id
    and idempotency_key=trim(p_idempotency_key) for update;
  if request.actor_user_id<>actor or request.operation<>'reversal' or request.request_fingerprint<>fingerprint then
    raise exception using errcode='22023',message='idempotency key payload mismatch'; end if;
  if request.completed_at is not null then return request.result; end if;
  reversal_id:=request.record_id;
  perform private.assert_financial_business_date(p_shop_id,p_business_date);
  select * into original from public.journal_transactions transaction where transaction.id=p_journal_transaction_id
    and transaction.shop_id=p_shop_id and transaction.kind in ('expense','deposit','withdrawal','transfer') for update;
  if not found or exists(select 1 from public.journal_transactions where shop_id=p_shop_id
    and reversal_of_id=p_journal_transaction_id) then
    raise exception using errcode='42501',message='financial operation is not reversible'; end if;
  perform 1 from public.financial_accounts account where account.id in (
    select entry.financial_account_id from public.journal_entries entry
    where entry.journal_transaction_id=p_journal_transaction_id
  ) order by account.id for update;
  for required in
    select entry.financial_account_id,sum(entry.debit_paisa-entry.credit_paisa)::bigint amount
    from public.journal_entries entry join public.financial_accounts account on account.id=entry.financial_account_id
    where entry.journal_transaction_id=p_journal_transaction_id and account.account_type in ('cash','bank')
    group by entry.financial_account_id having sum(entry.debit_paisa-entry.credit_paisa)>0
  loop
    if private.financial_account_balance(required.financial_account_id)<required.amount then
      raise exception using errcode='23514',message='insufficient account funds for reversal'; end if;
  end loop;
  insert into public.journal_transactions(id,shop_id,kind,description,reversal_of_id,business_date,actor_user_id,idempotency_key)
  values(reversal_id,p_shop_id,'reversal','Reversal: '||trim(p_reason),p_journal_transaction_id,p_business_date,actor,
    'financial-reversal:'||trim(p_idempotency_key)||':journal');
  insert into public.journal_entries(shop_id,journal_transaction_id,line_number,financial_account_id,debit_paisa,credit_paisa)
  select p_shop_id,reversal_id,row_number() over(order by entry.line_number),entry.financial_account_id,
    entry.credit_paisa,entry.debit_paisa from public.journal_entries entry
  where entry.journal_transaction_id=p_journal_transaction_id order by entry.line_number;
  result_payload:=jsonb_build_object('journal_transaction_id',p_journal_transaction_id,
    'reversal_journal_id',reversal_id,'original_kind',original.kind);
  insert into private.business_audit_events(shop_id,actor_user_id,operation,record_type,record_id,after_metadata,idempotency_key)
  values(p_shop_id,actor,'reverse','journal_transaction',p_journal_transaction_id,result_payload,
    'financial-reversal:'||trim(p_idempotency_key)||':audit');
  update private.financial_operation_requests set result=result_payload,completed_at=now()
    where shop_id=p_shop_id and idempotency_key=trim(p_idempotency_key);
  return result_payload;
end;
$$;

alter table private.financial_operation_requests enable row level security;
revoke all on table private.financial_operation_requests from public,anon,authenticated;
revoke all on function private.financial_account_balance(uuid),private.assert_financial_actor(uuid),
  private.assert_financial_business_date(uuid,date) from public,anon,authenticated;
revoke all on function public.post_expense(text,uuid,uuid,bigint,date,text,text,text),
  public.post_cash_movement(text,uuid,text,uuid,bigint,date,text),
  public.post_account_transfer(text,uuid,uuid,uuid,bigint,date,text),
  public.reverse_financial_operation(text,uuid,uuid,date,text) from public,anon;
grant execute on function public.post_expense(text,uuid,uuid,bigint,date,text,text,text),
  public.post_cash_movement(text,uuid,text,uuid,bigint,date,text),
  public.post_account_transfer(text,uuid,uuid,uuid,bigint,date,text),
  public.reverse_financial_operation(text,uuid,uuid,date,text) to authenticated;

comment on function private.financial_account_balance(uuid) is 'Derived signed balance; debit-normal cash/bank funds are positive.';
comment on function public.post_expense(text,uuid,uuid,bigint,date,text,text,text) is 'Owner-only atomic expense with no-overdraft enforcement.';
comment on function public.post_cash_movement(text,uuid,text,uuid,bigint,date,text) is 'Owner-only atomic deposit or withdrawal against clearing.';
comment on function public.post_account_transfer(text,uuid,uuid,uuid,bigint,date,text) is 'Owner-only atomic cash/bank transfer.';
comment on function public.reverse_financial_operation(text,uuid,uuid,date,text) is 'Owner-only exact compensating reversal for Task 3.7 journals.';

commit;
