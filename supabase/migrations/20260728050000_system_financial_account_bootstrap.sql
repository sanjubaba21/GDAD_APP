begin;

create or replace function private.ensure_shop_financial_accounts(target_shop_id uuid)
returns void language plpgsql security definer set search_path='' as $$
begin
  if not exists(select 1 from public.shops where id=target_shop_id) then
    raise exception using errcode='23503',message='shop does not exist'; end if;
  insert into public.financial_accounts(
    shop_id,display_name,account_type,normal_side,purpose_code,system_managed
  )
  select target_shop_id,definition.display_name,definition.account_type,
    definition.normal_side,definition.purpose_code,true
  from (values
    ('GDAD Cash','cash'::public.financial_account_type,'debit'::public.account_normal_side,'cash_main'),
    ('GDAD Bank','bank'::public.financial_account_type,'debit'::public.account_normal_side,'bank_main'),
    ('GDAD Accounts Receivable','receivable'::public.financial_account_type,'debit'::public.account_normal_side,'accounts_receivable'),
    ('GDAD Accounts Payable','payable'::public.financial_account_type,'credit'::public.account_normal_side,'accounts_payable'),
    ('GDAD Inventory','inventory'::public.financial_account_type,'debit'::public.account_normal_side,'inventory_control'),
    ('GDAD Sales Revenue','revenue'::public.financial_account_type,'credit'::public.account_normal_side,'sales_revenue'),
    ('GDAD Cost of Goods Sold','cogs'::public.financial_account_type,'debit'::public.account_normal_side,'cost_of_goods_sold'),
    ('GDAD Expense','expense'::public.financial_account_type,'debit'::public.account_normal_side,'expense_control'),
    ('GDAD Opening Equity','equity'::public.financial_account_type,'credit'::public.account_normal_side,'opening_equity'),
    ('GDAD Inventory Adjustment','clearing'::public.financial_account_type,'debit'::public.account_normal_side,'inventory_adjustment_control'),
    ('GDAD Cash Movement','clearing'::public.financial_account_type,'credit'::public.account_normal_side,'cash_movement_clearing')
  ) definition(display_name,account_type,normal_side,purpose_code)
  where not exists(
    select 1 from public.financial_accounts existing where existing.shop_id=target_shop_id
      and existing.purpose_code=definition.purpose_code
  );
end;
$$;

create or replace function private.bootstrap_shop_financial_accounts()
returns trigger language plpgsql security definer set search_path='' as $$
begin
  if session_user not in ('postgres','supabase_admin')
    and coalesce(current_setting('app.seed_mode',true),'off')<>'on' then
    perform private.ensure_shop_financial_accounts(new.id);
  end if;
  return new;
end;
$$;

create trigger shops_bootstrap_financial_accounts
after insert on public.shops for each row execute function private.bootstrap_shop_financial_accounts();

do $$
declare target record;
begin
  for target in select id from public.shops order by id loop
    perform private.ensure_shop_financial_accounts(target.id);
  end loop;
end;
$$;

revoke all on function private.ensure_shop_financial_accounts(uuid),
  private.bootstrap_shop_financial_accounts() from public,anon,authenticated;

comment on function private.ensure_shop_financial_accounts(uuid) is
  'Creates any missing protected first-release system accounts for one shop.';
comment on function private.bootstrap_shop_financial_accounts() is
  'Ensures an application-created shop can use atomic sales, purchase, inventory, and finance RPCs; migration/test sessions provision explicitly.';

commit;
