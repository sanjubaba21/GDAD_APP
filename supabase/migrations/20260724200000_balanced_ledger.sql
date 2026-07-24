begin;

create extension if not exists btree_gist with schema extensions;

create type public.financial_account_type as enum (
    'cash', 'bank', 'receivable', 'payable', 'inventory',
    'revenue', 'cogs', 'expense', 'equity', 'clearing'
);
create type public.account_normal_side as enum ('debit', 'credit');
create type public.accounting_period_status as enum ('open', 'closed');
create type public.journal_kind as enum (
    'opening_balance', 'sale', 'sale_payment', 'sale_return', 'refund',
    'purchase_receipt', 'vendor_payment', 'vendor_return', 'expense',
    'transfer', 'correction', 'reversal'
);

create table public.financial_accounts (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    display_name text not null,
    normalized_name text generated always as (
        lower(regexp_replace(trim(display_name), '\s+', ' ', 'g'))
    ) stored,
    account_type public.financial_account_type not null,
    normal_side public.account_normal_side not null,
    purpose_code text,
    system_managed boolean not null default false,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, normalized_name),
    constraint financial_accounts_name_not_blank check (length(trim(display_name)) > 0),
    constraint financial_accounts_purpose_format check (
        purpose_code is null or (
            purpose_code = lower(trim(purpose_code))
            and purpose_code ~ '^[a-z][a-z0-9_]{2,63}$'
        )
    ),
    constraint financial_accounts_natural_side check (
        (account_type in ('cash','bank','receivable','inventory','cogs','expense') and normal_side = 'debit')
        or (account_type in ('payable','revenue','equity') and normal_side = 'credit')
        or account_type = 'clearing'
    ),
    constraint financial_accounts_system_purpose check (
        not system_managed or purpose_code is not null
    )
);

create unique index financial_accounts_shop_purpose_unique
on public.financial_accounts (shop_id, purpose_code)
where purpose_code is not null;

create trigger financial_accounts_set_updated_at
before update on public.financial_accounts
for each row execute function public.set_updated_at();

create table public.accounting_periods (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    date_from date not null,
    date_to date not null,
    status public.accounting_period_status not null default 'open',
    created_at timestamptz not null default now(),
    closed_at timestamptz,
    closed_by uuid references auth.users(id) on delete restrict,
    reopened_at timestamptz,
    reopened_by uuid references auth.users(id) on delete restrict,
    reopen_reason text,
    unique (shop_id, id),
    constraint accounting_periods_date_order check (date_to >= date_from),
    constraint accounting_periods_close_consistent check (
        (status = 'open' and closed_at is null and closed_by is null)
        or (status = 'closed' and closed_at is not null and closed_by is not null)
    ),
    constraint accounting_periods_reopen_consistent check (
        (reopened_at is null and reopened_by is null and reopen_reason is null)
        or (
            status = 'open' and reopened_at is not null and reopened_by is not null
            and length(trim(reopen_reason)) > 0
        )
    ),
    exclude using gist (
        shop_id with =,
        daterange(date_from, date_to, '[]') with &&
    )
);

create table public.journal_transactions (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    kind public.journal_kind not null,
    description text not null,
    source_id uuid,
    reversal_of_id uuid,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    posted_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, reversal_of_id)
        references public.journal_transactions(shop_id, id) on delete restrict
        deferrable initially deferred,
    constraint journal_transactions_description_not_blank check (length(trim(description)) > 0),
    constraint journal_transactions_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint journal_transactions_source_consistent check (
        (kind in ('opening_balance','transfer','correction','reversal') and source_id is null)
        or (kind not in ('opening_balance','transfer','correction','reversal') and source_id is not null)
    ),
    constraint journal_transactions_reversal_consistent check (
        (kind = 'reversal' and reversal_of_id is not null)
        or (kind <> 'reversal' and reversal_of_id is null)
    ),
    constraint journal_transactions_not_self_reversal check (reversal_of_id is null or reversal_of_id <> id)
);

create unique index journal_transactions_source_unique
on public.journal_transactions (shop_id, kind, source_id)
where source_id is not null;
create unique index journal_transactions_reversal_unique
on public.journal_transactions (shop_id, reversal_of_id)
where reversal_of_id is not null;

create table public.journal_entries (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    journal_transaction_id uuid not null,
    line_number integer not null,
    financial_account_id uuid not null,
    debit_paisa bigint not null default 0,
    credit_paisa bigint not null default 0,
    memo text,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, journal_transaction_id, line_number),
    foreign key (shop_id, journal_transaction_id)
        references public.journal_transactions(shop_id, id) on delete restrict,
    foreign key (shop_id, financial_account_id)
        references public.financial_accounts(shop_id, id) on delete restrict,
    constraint journal_entries_line_number_positive check (line_number > 0),
    constraint journal_entries_exactly_one_side check (
        (debit_paisa > 0 and credit_paisa = 0)
        or (credit_paisa > 0 and debit_paisa = 0)
    )
);

create table public.expenses (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    category text not null,
    payee text,
    note text,
    amount_paisa bigint not null,
    journal_transaction_id uuid not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, journal_transaction_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, journal_transaction_id)
        references public.journal_transactions(shop_id, id) on delete restrict
        deferrable initially deferred,
    constraint expenses_category_not_blank check (length(trim(category)) > 0),
    constraint expenses_amount_positive check (amount_paisa > 0),
    constraint expenses_idempotency_not_blank check (length(trim(idempotency_key)) > 0)
);

create or replace function private.journal_source_exists(
    source_kind public.journal_kind,
    target_shop_id uuid,
    target_source_id uuid
)
returns boolean language plpgsql stable security definer set search_path = '' as $$
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
      else return target_source_id is null;
    end case;
end; $$;

create or replace function private.assert_journal_integrity(target_transaction_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare journal public.journal_transactions%rowtype; entry_count bigint; debits bigint; credits bigint;
begin
    select * into journal from public.journal_transactions where id = target_transaction_id;
    if not found then return; end if;
    select count(*), coalesce(sum(debit_paisa),0), coalesce(sum(credit_paisa),0)
    into entry_count, debits, credits
    from public.journal_entries where journal_transaction_id = target_transaction_id;
    if entry_count < 2 or debits <= 0 or debits <> credits then
        raise exception using errcode = '23514', message = 'journal transaction is not balanced';
    end if;
    if not private.journal_source_exists(journal.kind, journal.shop_id, journal.source_id) then
        raise exception using errcode = '23503', message = 'journal source does not exist in shop';
    end if;
    if journal.kind = 'expense' and (
        select expense.amount_paisa from public.expenses expense
        where expense.shop_id=journal.shop_id and expense.id=journal.source_id
    ) <> debits then
        raise exception using errcode = '23514', message = 'expense amount does not reconcile to journal';
    end if;
    if journal.kind = 'reversal' and exists (
        with original as (
          select financial_account_id, sum(debit_paisa) debit, sum(credit_paisa) credit
          from public.journal_entries where journal_transaction_id=journal.reversal_of_id
          group by financial_account_id
        ), reversed as (
          select financial_account_id, sum(debit_paisa) debit, sum(credit_paisa) credit
          from public.journal_entries where journal_transaction_id=journal.id
          group by financial_account_id
        )
        select 1 from original full join reversed using(financial_account_id)
        where coalesce(original.debit,0) <> coalesce(reversed.credit,0)
           or coalesce(original.credit,0) <> coalesce(reversed.debit,0)
    ) then
        raise exception using errcode = '23514', message = 'journal reversal does not exactly compensate original';
    end if;
end; $$;

create or replace function private.ledger_integrity_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$
declare transaction_id uuid;
begin
    if tg_table_name='journal_transactions' then
      transaction_id := case when tg_op='DELETE' then old.id else new.id end;
    elsif tg_table_name='journal_entries' then
      transaction_id := case when tg_op='DELETE' then old.journal_transaction_id else new.journal_transaction_id end;
    elsif tg_table_name='expenses' then
      transaction_id := case when tg_op='DELETE' then old.journal_transaction_id else new.journal_transaction_id end;
    end if;
    if transaction_id is not null then perform private.assert_journal_integrity(transaction_id); end if;
    return null;
end; $$;

create constraint trigger journal_transactions_integrity after insert or update or delete on public.journal_transactions
deferrable initially deferred for each row execute function private.ledger_integrity_trigger();
create constraint trigger journal_entries_integrity after insert or update or delete on public.journal_entries
deferrable initially deferred for each row execute function private.ledger_integrity_trigger();
create constraint trigger expenses_integrity after insert or update or delete on public.expenses
deferrable initially deferred for each row execute function private.ledger_integrity_trigger();

create index financial_accounts_shop_type_idx on public.financial_accounts (shop_id, account_type, active);
create index accounting_periods_shop_dates_idx on public.accounting_periods (shop_id, date_from, date_to);
create index journal_transactions_shop_date_idx on public.journal_transactions (shop_id, business_date desc, occurred_at desc);
create index journal_entries_account_timeline_idx on public.journal_entries (shop_id, financial_account_id, journal_transaction_id);
create index expenses_shop_date_category_idx on public.expenses (shop_id, business_date desc, category);

alter table public.financial_accounts enable row level security;
alter table public.accounting_periods enable row level security;
alter table public.journal_transactions enable row level security;
alter table public.journal_entries enable row level security;
alter table public.expenses enable row level security;

create policy financial_accounts_select_owner on public.financial_accounts for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy accounting_periods_select_owner on public.accounting_periods for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy journal_transactions_select_owner on public.journal_transactions for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy journal_entries_select_owner on public.journal_entries for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy expenses_select_owner on public.expenses for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));

revoke all on table public.financial_accounts, public.accounting_periods,
    public.journal_transactions, public.journal_entries, public.expenses
from public, anon, authenticated;
grant select on table public.financial_accounts, public.accounting_periods,
    public.journal_transactions, public.journal_entries, public.expenses to authenticated;

revoke all on function private.journal_source_exists(public.journal_kind,uuid,uuid),
    private.assert_journal_integrity(uuid), private.ledger_integrity_trigger()
from public, anon, authenticated;

comment on table public.financial_accounts is 'Chart of accounts; balances derive only from posted immutable journal entries.';
comment on table public.accounting_periods is 'Non-overlapping Nepal business-date control periods for approved backdating/close policy.';
comment on table public.journal_transactions is 'Immutable balanced transaction header with same-shop typed source and forward reversal.';
comment on table public.journal_entries is 'Immutable debit/credit evidence; no account balance column exists.';
comment on table public.expenses is 'Expense business evidence; monetary effect is the exactly reconciled linked journal.';

commit;
