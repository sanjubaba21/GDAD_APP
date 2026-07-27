-- Task 3.9 backend integration and concurrency verification.
do $$
declare shop_a constant uuid:='a6900000-0000-4000-8000-000000000001';
  race_count bigint; retry_count bigint; payment_count bigint; expense_count bigint;
  bill_id uuid; retry_sale_id uuid; cash_id uuid; report jsonb;
begin
  select count(*) into race_count from public.sales where shop_id=shop_a
    and idempotency_key in ('sale:race-a:header','sale:race-b:header');
  if race_count<>1 then raise exception 'expected exactly one competing sale, found %',race_count; end if;
  if (select current_stock from public.products where id='a6b00000-0000-4000-8000-000000000001')<>0
    or (select remaining_quantity from public.inventory_lots where id='a6c00000-0000-4000-8000-000000000001')<>0
  then raise exception 'competing sale stock did not serialize'; end if;
  select count(*),min(id) into retry_count,retry_sale_id from public.sales where shop_id=shop_a
    and idempotency_key='sale:retry-same:header';
  if retry_count<>1 then raise exception 'exact concurrent retry duplicated sale'; end if;
  if (select current_stock from public.products where id='a6b00000-0000-4000-8000-000000000002')<>1
    or (select count(*) from public.sale_returns where sale_id=retry_sale_id and status='posted')<>1
  then raise exception 'partial return did not restore exactly one unit'; end if;
  select id into bill_id from public.purchase_bills where idempotency_key='purchase:integration-purchase:bill';
  select count(*) into payment_count from public.vendor_payments where shop_id=shop_a
    and idempotency_key in ('vendor-payment:vendor-race-a:header','vendor-payment:vendor-race-b:header');
  if payment_count<>1 or private.vendor_bill_due(bill_id)<>300 then
    raise exception 'competing vendor payments did not serialize to one 700 payment'; end if;
  select count(*) into expense_count from public.expenses where shop_id=shop_a and category='Concurrent debit';
  if expense_count<>1 then raise exception 'competing expense debits did not serialize'; end if;
  select id into cash_id from public.financial_accounts where shop_id=shop_a and purpose_code='cash_main';
  if private.financial_account_balance(cash_id)<>400 then
    raise exception 'cash balance expected 400, found %',private.financial_account_balance(cash_id); end if;
  if exists(select 1 from private.sale_operation_requests where shop_id=shop_a and completed_at is null)
    or exists(select 1 from private.vendor_operation_requests where shop_id=shop_a and completed_at is null)
    or exists(select 1 from private.financial_operation_requests where shop_id=shop_a and completed_at is null)
  then raise exception 'failed operation left incomplete request state'; end if;
  if exists(select 1 from public.journal_transactions journal join public.journal_entries entry
    on entry.journal_transaction_id=journal.id where journal.shop_id=shop_a group by journal.id
    having count(*)<2 or sum(entry.debit_paisa)<>sum(entry.credit_paisa))
  then raise exception 'integration journal is unbalanced'; end if;
  perform set_config('request.jwt.claim.sub','10690000-0000-4000-8000-000000000001',true);
  report:=public.get_dashboard_report(shop_a,now());
  if (report->>'net_sales_paisa')::bigint<>2000 or (report->>'expenses_total_paisa')::bigint<>900
  then raise exception 'integrated report does not reconcile: %',report; end if;
  if exists(select 1 from public.sales where idempotency_key in ('sale:disabled-forged:header','sale:cross-forged:header'))
  then raise exception 'denied actor created a forged sale'; end if;
end $$;
select 'backend concurrency verification passed' as result;
