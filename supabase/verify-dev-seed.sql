\set ON_ERROR_STOP on

do $$
begin
  if (select count(*) from public.shops where id::text like 'd1000000-%') <> 2 then
    raise exception 'development seed must contain exactly two shops';
  end if;
  if (select count(*) from public.user_profiles where user_id::text like 'd0000000-%') <> 4
     or not exists(select 1 from public.user_profiles where user_id='d0000000-0000-4000-8000-000000000001' and platform_role='super_admin')
     or (select count(*) from public.shop_memberships where user_id::text like 'd0000000-%' and role='owner') <> 2
     or (select count(*) from public.shop_memberships where user_id::text like 'd0000000-%' and role='salesman') <> 1 then
    raise exception 'development seed role coverage is incomplete';
  end if;
  if exists(select 1 from private.login_credentials where user_id::text like 'd0000000-%')
     or exists(select 1 from auth.users where id::text like 'd0000000-%' and encrypted_password <> '') then
    raise exception 'development seed must not contain reusable authentication credentials';
  end if;
  if (select count(*) from public.products where id::text like 'd2000000-%') <> 3
     or (select count(*) from public.inventory_lots where id::text like 'd4000000-%') <> 3
     or (select count(distinct lot_id) from public.sale_lot_allocations where id::text like 'd5200000-%') <> 2 then
    raise exception 'development seed FIFO/product coverage is incomplete';
  end if;
  if not exists(select 1 from public.purchase_bills where id='d3100000-0000-4000-8000-000000000001' and status='received')
     or not exists(select 1 from public.sales where id='d5000000-0000-4000-8000-000000000001' and is_credit and status='partially_returned')
     or not exists(select 1 from public.sale_payments where id='d5300000-0000-4000-8000-000000000001' and amount_paisa=1500)
     or not exists(select 1 from public.sale_returns where id='d5400000-0000-4000-8000-000000000001' and status='posted') then
    raise exception 'development seed purchasing/sale/payment/return coverage is incomplete';
  end if;
  if (select count(*) from public.expenses where id::text like 'd7200000-%') <> 1
     or (select count(*) from public.journal_transactions where id::text like 'd7300000-%' and kind='transfer') <> 1
     or (select count(*) from public.notifications where id::text like 'd8000000-%') <> 2
     or (select count(*) from public.notification_reads where notification_id::text like 'd8000000-%') <> 1 then
    raise exception 'development seed ledger/notification coverage is incomplete';
  end if;
end;
$$;

with fixture_rows as (
  select 'shops'::text table_name,id::text row_id,to_jsonb(row_data)::text payload from public.shops row_data where id::text like 'd1000000-%'
  union all select 'profiles',user_id::text,to_jsonb(row_data)::text from public.user_profiles row_data where user_id::text like 'd0000000-%'
  union all select 'memberships',shop_id::text||':'||user_id::text,to_jsonb(row_data)::text from public.shop_memberships row_data where user_id::text like 'd0000000-%'
  union all select 'products',id::text,to_jsonb(row_data)::text from public.products row_data where id::text like 'd2000000-%'
  union all select 'vendors',id::text,to_jsonb(row_data)::text from public.vendors row_data where id::text like 'd3000000-%'
  union all select 'purchase_bills',id::text,to_jsonb(row_data)::text from public.purchase_bills row_data where id::text like 'd3100000-%'
  union all select 'purchase_bill_lines',id::text,to_jsonb(row_data)::text from public.purchase_bill_lines row_data where id::text like 'd3200000-%'
  union all select 'purchase_receipts',id::text,to_jsonb(row_data)::text from public.purchase_receipts row_data where id::text like 'd3300000-%'
  union all select 'purchase_receipt_lines',id::text,to_jsonb(row_data)::text from public.purchase_receipt_lines row_data where id::text like 'd3400000-%'
  union all select 'lots',id::text,to_jsonb(row_data)::text from public.inventory_lots row_data where id::text like 'd4000000-%'
  union all select 'sales',id::text,to_jsonb(row_data)::text from public.sales row_data where id::text like 'd5000000-%'
  union all select 'sale_lines',id::text,to_jsonb(row_data)::text from public.sale_lines row_data where id::text like 'd5100000-%'
  union all select 'sale_allocations',id::text,to_jsonb(row_data)::text from public.sale_lot_allocations row_data where id::text like 'd5200000-%'
  union all select 'sale_payments',id::text,to_jsonb(row_data)::text from public.sale_payments row_data where id::text like 'd5300000-%'
  union all select 'sale_returns',id::text,to_jsonb(row_data)::text from public.sale_returns row_data where id::text like 'd5400000-%'
  union all select 'return_lines',id::text,to_jsonb(row_data)::text from public.sale_return_lines row_data where id::text like 'd5500000-%'
  union all select 'return_allocations',id::text,to_jsonb(row_data)::text from public.sale_return_allocations row_data where id::text like 'd5600000-%'
  union all select 'movements',id::text,to_jsonb(row_data)::text from public.inventory_movements row_data where id::text like 'd6000000-%'
  union all select 'accounts',id::text,to_jsonb(row_data)::text from public.financial_accounts row_data where id::text like 'd7000000-%'
  union all select 'periods',id::text,to_jsonb(row_data)::text from public.accounting_periods row_data where id::text like 'd7100000-%'
  union all select 'expenses',id::text,to_jsonb(row_data)::text from public.expenses row_data where id::text like 'd7200000-%'
  union all select 'journals',id::text,to_jsonb(row_data)::text from public.journal_transactions row_data where id::text like 'd7300000-%'
  union all select 'entries',id::text,to_jsonb(row_data)::text from public.journal_entries row_data where id::text like 'd7400000-%'
  union all select 'notifications',id::text,to_jsonb(row_data)::text from public.notifications row_data where id::text like 'd8000000-%'
  union all select 'reads',notification_id::text||':'||user_id::text,to_jsonb(row_data)::text from public.notification_reads row_data where notification_id::text like 'd8000000-%'
)
select md5(string_agg(table_name||'|'||row_id||'|'||payload,E'\n' order by table_name,row_id))
from fixture_rows;
