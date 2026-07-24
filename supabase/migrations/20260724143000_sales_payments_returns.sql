begin;

create type public.sale_status as enum (
    'draft', 'posted', 'partially_returned', 'returned', 'reversed'
);
create type public.payment_method as enum ('cash', 'bank');
create type public.financial_event_status as enum ('posted', 'reversed');
create type public.sale_return_status as enum ('draft', 'posted', 'reversed');
create type public.return_disposition as enum ('sellable', 'damaged');

alter table public.inventory_lots
    add constraint inventory_lots_shop_id_product_id_unique
    unique (shop_id, id, product_id);

create table public.sales (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    status public.sale_status not null default 'draft',
    is_credit boolean not null default false,
    customer_name text,
    customer_contact text,
    due_date date,
    subtotal_paisa bigint not null,
    line_discount_total_paisa bigint not null default 0,
    sale_discount_total_paisa bigint not null default 0,
    tax_total_paisa bigint not null default 0,
    grand_total_paisa bigint not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    posted_at timestamptz,
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, idempotency_key),
    constraint sales_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint sales_money_nonnegative check (
        subtotal_paisa >= 0
        and line_discount_total_paisa >= 0
        and sale_discount_total_paisa >= 0
        and tax_total_paisa = 0
        and grand_total_paisa >= 0
    ),
    constraint sales_header_total_reconciles check (
        grand_total_paisa = subtotal_paisa
            - line_discount_total_paisa
            - sale_discount_total_paisa
        and line_discount_total_paisa + sale_discount_total_paisa <= subtotal_paisa
    ),
    constraint sales_credit_identity_complete check (
        (not is_credit and customer_name is null and customer_contact is null and due_date is null)
        or (
            is_credit
            and coalesce(length(trim(customer_name)) > 0, false)
            and coalesce(length(trim(customer_contact)) > 0, false)
            and coalesce(due_date >= business_date, false)
        )
    ),
    constraint sales_posted_time_consistent check (
        (status = 'draft' and posted_at is null)
        or (status <> 'draft' and posted_at is not null)
    ),
    constraint sales_reversal_metadata_consistent check (
        (status <> 'reversed' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed'
            and reversed_at is not null
            and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.sale_lines (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_id uuid not null,
    line_number integer not null,
    product_id uuid not null,
    product_name text not null,
    sku_code text not null,
    quantity integer not null,
    configured_unit_price_paisa bigint not null,
    effective_unit_price_paisa bigint not null,
    gross_total_paisa bigint not null,
    line_discount_paisa bigint not null default 0,
    allocated_sale_discount_paisa bigint not null default 0,
    line_total_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, id, product_id),
    unique (shop_id, sale_id, id),
    unique (shop_id, sale_id, line_number),
    foreign key (shop_id, sale_id)
        references public.sales(shop_id, id) on delete restrict,
    foreign key (shop_id, product_id)
        references public.products(shop_id, id) on delete restrict,
    constraint sale_lines_line_number_positive check (line_number > 0),
    constraint sale_lines_quantity_positive check (quantity > 0),
    constraint sale_lines_snapshots_not_blank check (
        length(trim(product_name)) > 0 and length(trim(sku_code)) > 0
    ),
    constraint sale_lines_money_nonnegative check (
        configured_unit_price_paisa >= 0
        and effective_unit_price_paisa >= 0
        and gross_total_paisa >= 0
        and line_discount_paisa >= 0
        and allocated_sale_discount_paisa >= 0
        and line_total_paisa >= 0
    ),
    constraint sale_lines_total_reconciles check (
        gross_total_paisa = effective_unit_price_paisa * quantity::bigint
        and line_total_paisa = gross_total_paisa
            - line_discount_paisa
            - allocated_sale_discount_paisa
        and line_discount_paisa + allocated_sale_discount_paisa <= gross_total_paisa
    )
);

create table public.sale_lot_allocations (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_line_id uuid not null,
    product_id uuid not null,
    lot_id uuid not null,
    quantity integer not null,
    unit_cost_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, id, sale_line_id),
    unique (shop_id, sale_line_id, lot_id),
    foreign key (shop_id, sale_line_id, product_id)
        references public.sale_lines(shop_id, id, product_id) on delete restrict,
    foreign key (shop_id, lot_id, product_id)
        references public.inventory_lots(shop_id, id, product_id) on delete restrict,
    constraint sale_lot_allocations_quantity_positive check (quantity > 0),
    constraint sale_lot_allocations_cost_nonnegative check (unit_cost_paisa >= 0)
);

create table public.sale_payments (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_id uuid not null,
    status public.financial_event_status not null default 'posted',
    method public.payment_method not null,
    amount_paisa bigint not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, sale_id)
        references public.sales(shop_id, id) on delete restrict,
    constraint sale_payments_amount_positive check (amount_paisa > 0),
    constraint sale_payments_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint sale_payments_reversal_consistent check (
        (status = 'posted' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed'
            and reversed_at is not null
            and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.sale_returns (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_id uuid not null,
    status public.sale_return_status not null default 'draft',
    reason text not null,
    total_value_paisa bigint not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    posted_at timestamptz,
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, id, sale_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, sale_id)
        references public.sales(shop_id, id) on delete restrict,
    constraint sale_returns_reason_not_blank check (length(trim(reason)) > 0),
    constraint sale_returns_total_nonnegative check (total_value_paisa >= 0),
    constraint sale_returns_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint sale_returns_posted_time_consistent check (
        (status = 'draft' and posted_at is null)
        or (status <> 'draft' and posted_at is not null)
    ),
    constraint sale_returns_reversal_consistent check (
        (status <> 'reversed' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed'
            and reversed_at is not null
            and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.sale_return_lines (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_return_id uuid not null,
    sale_id uuid not null,
    sale_line_id uuid not null,
    quantity integer not null,
    disposition public.return_disposition not null,
    refund_value_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, id, sale_line_id),
    unique (shop_id, sale_return_id, sale_line_id),
    foreign key (shop_id, sale_return_id, sale_id)
        references public.sale_returns(shop_id, id, sale_id) on delete restrict,
    foreign key (shop_id, sale_id, sale_line_id)
        references public.sale_lines(shop_id, sale_id, id) on delete restrict,
    constraint sale_return_lines_quantity_positive check (quantity > 0),
    constraint sale_return_lines_refund_nonnegative check (refund_value_paisa >= 0)
);

create table public.sale_return_allocations (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_return_line_id uuid not null,
    sale_line_id uuid not null,
    sale_lot_allocation_id uuid not null,
    quantity integer not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, sale_return_line_id, sale_lot_allocation_id),
    foreign key (shop_id, sale_return_line_id, sale_line_id)
        references public.sale_return_lines(shop_id, id, sale_line_id) on delete restrict,
    foreign key (shop_id, sale_lot_allocation_id, sale_line_id)
        references public.sale_lot_allocations(shop_id, id, sale_line_id) on delete restrict,
    constraint sale_return_allocations_quantity_positive check (quantity > 0)
);

create table public.refunds (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    sale_return_id uuid not null,
    status public.financial_event_status not null default 'posted',
    method public.payment_method not null,
    amount_paisa bigint not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, sale_return_id)
        references public.sale_returns(shop_id, id) on delete restrict,
    constraint refunds_amount_positive check (amount_paisa > 0),
    constraint refunds_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint refunds_reversal_consistent check (
        (status = 'posted' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed'
            and reversed_at is not null
            and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create or replace function private.assert_sale_integrity(target_sale_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    sale_row public.sales%rowtype;
    line_count bigint;
    subtotal bigint;
    line_discounts bigint;
    sale_discounts bigint;
begin
    select * into sale_row from public.sales where id = target_sale_id;
    if not found or sale_row.status = 'draft' then return; end if;

    select count(*), coalesce(sum(gross_total_paisa), 0),
           coalesce(sum(line_discount_paisa), 0),
           coalesce(sum(allocated_sale_discount_paisa), 0)
    into line_count, subtotal, line_discounts, sale_discounts
    from public.sale_lines where sale_id = target_sale_id;

    if line_count = 0
       or subtotal <> sale_row.subtotal_paisa
       or line_discounts <> sale_row.line_discount_total_paisa
       or sale_discounts <> sale_row.sale_discount_total_paisa then
        raise exception using errcode = '23514', message = 'sale detail does not reconcile';
    end if;

    if exists (
        select 1 from public.sale_lines line
        where line.sale_id = target_sale_id
          and line.quantity <> (
              select coalesce(sum(allocation.quantity), 0)
              from public.sale_lot_allocations allocation
              where allocation.sale_line_id = line.id
          )
    ) then
        raise exception using errcode = '23514', message = 'sale allocations do not reconcile';
    end if;
end;
$$;

create or replace function private.assert_return_integrity(target_return_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    return_row public.sale_returns%rowtype;
    line_count bigint;
    line_total bigint;
begin
    select * into return_row from public.sale_returns where id = target_return_id;
    if not found or return_row.status = 'draft' then return; end if;

    select count(*), coalesce(sum(refund_value_paisa), 0)
    into line_count, line_total
    from public.sale_return_lines where sale_return_id = target_return_id;

    if line_count = 0 or line_total <> return_row.total_value_paisa then
        raise exception using errcode = '23514', message = 'return detail does not reconcile';
    end if;

    if exists (
        select 1 from public.sale_return_lines line
        where line.sale_return_id = target_return_id
          and line.quantity <> (
              select coalesce(sum(allocation.quantity), 0)
              from public.sale_return_allocations allocation
              where allocation.sale_return_line_id = line.id
          )
    ) then
        raise exception using errcode = '23514', message = 'return allocations do not reconcile';
    end if;

    if exists (
        select 1
        from public.sale_return_lines candidate
        join public.sale_returns candidate_return on candidate_return.id = candidate.sale_return_id
        where candidate_return.sale_id = return_row.sale_id
          and candidate_return.status <> 'reversed'
        group by candidate.sale_line_id
        having sum(candidate.quantity) > (
            select original.quantity from public.sale_lines original
            where original.id = candidate.sale_line_id
        )
    ) then
        raise exception using errcode = '23514', message = 'sale line over-returned';
    end if;

    if exists (
        select 1
        from public.sale_return_allocations candidate
        join public.sale_return_lines candidate_line on candidate_line.id = candidate.sale_return_line_id
        join public.sale_returns candidate_return on candidate_return.id = candidate_line.sale_return_id
        where candidate_return.sale_id = return_row.sale_id
          and candidate_return.status <> 'reversed'
        group by candidate.sale_lot_allocation_id
        having sum(candidate.quantity) > (
            select original.quantity from public.sale_lot_allocations original
            where original.id = candidate.sale_lot_allocation_id
        )
    ) then
        raise exception using errcode = '23514', message = 'sale allocation over-returned';
    end if;
end;
$$;

create or replace function private.assert_sale_money_integrity(target_sale_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
    sale_row public.sales%rowtype;
    receipts bigint;
    return_value bigint;
    refund_value bigint;
    net_received bigint;
    net_sale bigint;
begin
    select * into sale_row from public.sales where id = target_sale_id;
    if not found or sale_row.status in ('draft', 'reversed') then return; end if;

    select coalesce(sum(amount_paisa), 0) into receipts
    from public.sale_payments where sale_id = target_sale_id and status = 'posted';
    select coalesce(sum(total_value_paisa), 0) into return_value
    from public.sale_returns where sale_id = target_sale_id and status = 'posted';
    select coalesce(sum(refund.amount_paisa), 0) into refund_value
    from public.refunds refund
    join public.sale_returns returned on returned.id = refund.sale_return_id
    where returned.sale_id = target_sale_id and refund.status = 'posted';

    if exists (
        select 1 from public.sale_returns returned
        where returned.sale_id = target_sale_id and returned.status = 'posted'
          and (select coalesce(sum(refund.amount_paisa), 0) from public.refunds refund
               where refund.sale_return_id = returned.id and refund.status = 'posted')
              > returned.total_value_paisa
    ) then
        raise exception using errcode = '23514', message = 'return refund exceeds returned value';
    end if;

    net_received := receipts - refund_value;
    net_sale := sale_row.grand_total_paisa - return_value;
    if refund_value > receipts or net_received < 0 or net_sale < 0 or net_received > net_sale
       or (not sale_row.is_credit and net_received <> net_sale) then
        raise exception using errcode = '23514', message = 'sale settlement does not reconcile';
    end if;
end;
$$;

create or replace function private.sales_integrity_trigger()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare target_id uuid;
begin
    if tg_table_name = 'sales' then
        if tg_op = 'DELETE' then target_id := old.id; else target_id := new.id; end if;
    elsif tg_table_name = 'sale_lines' then
        if tg_op = 'DELETE' then target_id := old.sale_id; else target_id := new.sale_id; end if;
    elsif tg_table_name = 'sale_lot_allocations' then
        select line.sale_id into target_id from public.sale_lines line
        where line.id = case when tg_op = 'DELETE' then old.sale_line_id else new.sale_line_id end;
    elsif tg_table_name = 'sale_payments' then
        if tg_op = 'DELETE' then target_id := old.sale_id; else target_id := new.sale_id; end if;
    elsif tg_table_name = 'sale_returns' then
        if tg_op = 'DELETE' then target_id := old.sale_id; else target_id := new.sale_id; end if;
    elsif tg_table_name = 'sale_return_lines' then
        select returned.sale_id into target_id from public.sale_returns returned
        where returned.id = case when tg_op = 'DELETE' then old.sale_return_id else new.sale_return_id end;
    elsif tg_table_name = 'sale_return_allocations' then
        select returned.sale_id into target_id
        from public.sale_return_lines line
        join public.sale_returns returned on returned.id = line.sale_return_id
        where line.id = case when tg_op = 'DELETE' then old.sale_return_line_id else new.sale_return_line_id end;
    elsif tg_table_name = 'refunds' then
        select returned.sale_id into target_id from public.sale_returns returned
        where returned.id = case when tg_op = 'DELETE' then old.sale_return_id else new.sale_return_id end;
    end if;

    if target_id is not null then
        perform private.assert_sale_integrity(target_id);
        perform private.assert_sale_money_integrity(target_id);
    end if;
    if tg_table_name = 'sale_returns' then
        perform private.assert_return_integrity(
            case when tg_op = 'DELETE' then old.id else new.id end
        );
    elsif tg_table_name = 'sale_return_lines' then
        perform private.assert_return_integrity(
            case when tg_op = 'DELETE' then old.sale_return_id else new.sale_return_id end
        );
    elsif tg_table_name = 'sale_return_allocations' then
        perform private.assert_return_integrity((
            select line.sale_return_id from public.sale_return_lines line
            where line.id = case when tg_op = 'DELETE' then old.sale_return_line_id else new.sale_return_line_id end
        ));
    elsif tg_table_name = 'refunds' then
        perform private.assert_return_integrity(
            case when tg_op = 'DELETE' then old.sale_return_id else new.sale_return_id end
        );
    end if;
    return null;
end;
$$;

create constraint trigger sales_integrity after insert or update or delete on public.sales
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_lines_integrity after insert or update or delete on public.sale_lines
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_allocations_integrity after insert or update or delete on public.sale_lot_allocations
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_payments_integrity after insert or update or delete on public.sale_payments
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_returns_integrity after insert or update or delete on public.sale_returns
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_return_lines_integrity after insert or update or delete on public.sale_return_lines
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger sale_return_allocations_integrity after insert or update or delete on public.sale_return_allocations
deferrable initially deferred for each row execute function private.sales_integrity_trigger();
create constraint trigger refunds_integrity after insert or update or delete on public.refunds
deferrable initially deferred for each row execute function private.sales_integrity_trigger();

create index sales_shop_business_date_idx on public.sales (shop_id, business_date desc, occurred_at desc);
create index sales_shop_status_idx on public.sales (shop_id, status, occurred_at desc);
create index sale_lines_product_idx on public.sale_lines (shop_id, product_id, sale_id);
create index sale_allocations_lot_idx on public.sale_lot_allocations (shop_id, lot_id);
create index sale_payments_sale_idx on public.sale_payments (shop_id, sale_id, occurred_at);
create index sale_returns_sale_idx on public.sale_returns (shop_id, sale_id, occurred_at);
create index sale_return_lines_original_idx on public.sale_return_lines (shop_id, sale_line_id);
create index sale_return_allocations_original_idx on public.sale_return_allocations (shop_id, sale_lot_allocation_id);
create index refunds_return_idx on public.refunds (shop_id, sale_return_id, occurred_at);

alter table public.sales enable row level security;
alter table public.sale_lines enable row level security;
alter table public.sale_lot_allocations enable row level security;
alter table public.sale_payments enable row level security;
alter table public.sale_returns enable row level security;
alter table public.sale_return_lines enable row level security;
alter table public.sale_return_allocations enable row level security;
alter table public.refunds enable row level security;

create policy sales_select_shop_member on public.sales for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy sale_lines_select_shop_member on public.sale_lines for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy sale_allocations_select_owner on public.sale_lot_allocations for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner']::public.shop_role[]));
create policy sale_payments_select_shop_member on public.sale_payments for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy sale_returns_select_shop_member on public.sale_returns for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy sale_return_lines_select_shop_member on public.sale_return_lines for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy sale_return_allocations_select_shop_member on public.sale_return_allocations for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));
create policy refunds_select_shop_member on public.refunds for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner','salesman']::public.shop_role[]));

drop policy inventory_lots_select_shop_member on public.inventory_lots;
create policy inventory_lots_select_owner on public.inventory_lots for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner']::public.shop_role[]));
drop policy inventory_movements_select_shop_member on public.inventory_movements;
create policy inventory_movements_select_owner on public.inventory_movements for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id, array['owner']::public.shop_role[]));

revoke all on table public.sales, public.sale_lines, public.sale_lot_allocations,
    public.sale_payments, public.sale_returns, public.sale_return_lines,
    public.sale_return_allocations, public.refunds from public, anon, authenticated;
grant select on table public.sales, public.sale_lines, public.sale_lot_allocations,
    public.sale_payments, public.sale_returns, public.sale_return_lines,
    public.sale_return_allocations, public.refunds to authenticated;

revoke all on function private.assert_sale_integrity(uuid),
    private.assert_return_integrity(uuid), private.assert_sale_money_integrity(uuid),
    private.sales_integrity_trigger() from public, anon, authenticated;

comment on table public.sales is 'Server-priced sale header; posted monetary snapshots reconcile to immutable detail.';
comment on table public.sale_lot_allocations is 'Owner-only exact FIFO cost evidence; Salesmen cannot read cost.';
comment on table public.sale_payments is 'Append-only settlement evidence; cash/bank account linkage is added with Task 2.5 ledger schema.';
comment on table public.sale_returns is 'Owner-created return header; protected operations enforce the approved 30-day window.';
comment on table public.refunds is 'Append-only refund evidence; balanced account linkage is added with Task 2.5.';

commit;
