begin;

create index sale_returns_shop_business_date_idx
on public.sale_returns(shop_id,business_date,status);

create or replace function private.nepal_business_date(target_time timestamptz)
returns date language sql immutable parallel safe set search_path='' as $$
  select (timezone('Asia/Kathmandu',target_time))::date
$$;

create or replace function private.report_actor_role(target_shop_id uuid)
returns public.shop_role language plpgsql stable security definer set search_path='' as $$
declare actor uuid:=(select auth.uid()); actor_role public.shop_role;
begin
  select membership.role into actor_role from public.shop_memberships membership
  join public.user_profiles profile on profile.user_id=membership.user_id
  join public.shops shop on shop.id=membership.shop_id
  where membership.user_id=actor and membership.shop_id=target_shop_id
    and membership.role in ('owner','salesman') and membership.active
    and not profile.disabled and shop.active;
  if actor_role is null then raise exception using errcode='42501',message='report is not available'; end if;
  return actor_role;
end;
$$;

create or replace function public.get_business_report(
  p_shop_id uuid,p_date_from date,p_date_to date
)
returns jsonb language plpgsql stable security definer set search_path='' as $$
declare actor_role public.shop_role; sales_total bigint; return_total bigint; sale_cost bigint;
  return_cost bigint; expense_total bigint; stock_quantity bigint; stock_value bigint;
  low_stock jsonb; vendor_dues jsonb; vendor_due_total bigint; account_balances jsonb;
  result_payload jsonb;
begin
  actor_role:=private.report_actor_role(p_shop_id);
  if p_date_from is null or p_date_to is null or p_date_to<p_date_from
    or p_date_to-p_date_from>366 then
    raise exception using errcode='22023',message='invalid report date range'; end if;

  select coalesce(sum(sale.grand_total_paisa),0)::bigint into sales_total
  from public.sales sale where sale.shop_id=p_shop_id
    and sale.status not in ('draft','reversed') and sale.business_date between p_date_from and p_date_to;
  select coalesce(sum(returned.total_value_paisa),0)::bigint into return_total
  from public.sale_returns returned where returned.shop_id=p_shop_id and returned.status='posted'
    and returned.business_date between p_date_from and p_date_to;
  select coalesce(sum(product.current_stock),0)::bigint into stock_quantity
  from public.products product where product.shop_id=p_shop_id;
  select coalesce(jsonb_agg(jsonb_build_object('product_id',product.id,'sku_code',product.sku_code,
    'name',product.name,'current_stock',product.current_stock,'low_stock_threshold',product.low_stock_threshold)
    order by product.current_stock,product.name,product.id),'[]'::jsonb) into low_stock
  from public.products product where product.shop_id=p_shop_id and product.active
    and product.current_stock<=product.low_stock_threshold;
  result_payload:=jsonb_build_object('shop_id',p_shop_id,'role',actor_role,'date_from',p_date_from,
    'date_to',p_date_to,'sales_total_paisa',sales_total,'returns_total_paisa',return_total,
    'net_sales_paisa',sales_total-return_total,'stock_on_hand_quantity',stock_quantity,
    'low_stock_count',jsonb_array_length(low_stock),'low_stock_products',low_stock);

  if actor_role='owner' then
    select coalesce(sum(allocation.quantity::bigint*allocation.unit_cost_paisa),0)::bigint into sale_cost
    from public.sale_lot_allocations allocation join public.sale_lines line on line.id=allocation.sale_line_id
    join public.sales sale on sale.id=line.sale_id where sale.shop_id=p_shop_id
      and sale.status not in ('draft','reversed') and sale.business_date between p_date_from and p_date_to;
    select coalesce(sum(return_allocation.quantity::bigint*sale_allocation.unit_cost_paisa),0)::bigint into return_cost
    from public.sale_return_allocations return_allocation
    join public.sale_return_lines return_line on return_line.id=return_allocation.sale_return_line_id
    join public.sale_returns returned on returned.id=return_line.sale_return_id
    join public.sale_lot_allocations sale_allocation on sale_allocation.id=return_allocation.sale_lot_allocation_id
    where returned.shop_id=p_shop_id and returned.status='posted'
      and returned.business_date between p_date_from and p_date_to;
    select coalesce(sum(lot.remaining_quantity::bigint*lot.unit_cost_paisa),0)::bigint into stock_value
    from public.inventory_lots lot where lot.shop_id=p_shop_id;
    select coalesce(sum(expense.amount_paisa),0)::bigint into expense_total
    from public.expenses expense join public.journal_transactions journal
      on journal.id=expense.journal_transaction_id and journal.shop_id=expense.shop_id
    where expense.shop_id=p_shop_id and expense.business_date between p_date_from and p_date_to
      and not exists(select 1 from public.journal_transactions reversal
        where reversal.shop_id=p_shop_id and reversal.reversal_of_id=journal.id);
    select coalesce(sum(private.vendor_bill_due(bill.id)),0)::bigint into vendor_due_total
    from public.purchase_bills bill where bill.shop_id=p_shop_id and bill.status not in ('draft','reversed');
    select coalesce(jsonb_agg(jsonb_build_object('vendor_id',due.vendor_id,'vendor_name',due.vendor_name,
      'due_paisa',due.due_paisa) order by due.vendor_name,due.vendor_id),'[]'::jsonb) into vendor_dues
    from (select vendor.id vendor_id,vendor.display_name vendor_name,
      coalesce(sum(private.vendor_bill_due(bill.id)),0)::bigint due_paisa
      from public.vendors vendor join public.purchase_bills bill on bill.vendor_id=vendor.id
      where vendor.shop_id=p_shop_id and bill.status not in ('draft','reversed')
      group by vendor.id,vendor.display_name having coalesce(sum(private.vendor_bill_due(bill.id)),0)>0) due;
    select coalesce(jsonb_agg(jsonb_build_object('account_id',account.id,'display_name',account.display_name,
      'account_type',account.account_type,'balance_paisa',private.financial_account_balance(account.id))
      order by account.account_type,account.display_name,account.id),'[]'::jsonb) into account_balances
    from public.financial_accounts account where account.shop_id=p_shop_id
      and account.account_type in ('cash','bank') and account.active;
    result_payload:=result_payload||jsonb_build_object('cost_of_goods_sold_paisa',sale_cost-return_cost,
      'gross_profit_paisa',(sales_total-return_total)-(sale_cost-return_cost),
      'stock_value_paisa',stock_value,'vendor_due_total_paisa',vendor_due_total,
      'vendor_dues',vendor_dues,'account_balances',account_balances,'expenses_total_paisa',expense_total);
  end if;
  return result_payload;
end;
$$;

create or replace function public.get_dashboard_report(
  p_shop_id uuid,p_at timestamptz default now()
)
returns jsonb language sql stable security definer set search_path='' as $$
  select public.get_business_report(p_shop_id,private.nepal_business_date(p_at),private.nepal_business_date(p_at))
$$;

revoke all on function private.nepal_business_date(timestamptz),private.report_actor_role(uuid)
  from public,anon,authenticated;
revoke all on function public.get_business_report(uuid,date,date),public.get_dashboard_report(uuid,timestamptz)
  from public,anon;
grant execute on function public.get_business_report(uuid,date,date),public.get_dashboard_report(uuid,timestamptz)
  to authenticated;

comment on function public.get_business_report(uuid,date,date) is
  'Tenant-safe Nepal-date business report; Owner output includes cost/vendor/finance fields and Salesman output omits them.';
comment on function public.get_dashboard_report(uuid,timestamptz) is
  'Daily trusted report using the Asia/Kathmandu date derived from the supplied instant.';

commit;
