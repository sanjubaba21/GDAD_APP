begin;

create type public.purchase_bill_status as enum (
    'draft', 'posted', 'partially_received', 'received',
    'partially_returned', 'returned', 'reversed'
);
create type public.purchase_event_status as enum ('posted', 'reversed');

create table public.vendors (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    display_name text not null,
    phone text,
    tax_reference text,
    notes text,
    active boolean not null default true,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (shop_id, id),
    constraint vendors_display_name_not_blank check (length(trim(display_name)) > 0),
    constraint vendors_phone_not_blank check (phone is null or length(trim(phone)) > 0),
    constraint vendors_tax_reference_not_blank check (
        tax_reference is null or length(trim(tax_reference)) > 0
    )
);

create trigger vendors_set_updated_at
before update on public.vendors
for each row execute function public.set_updated_at();

create table public.purchase_bills (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    vendor_id uuid not null,
    status public.purchase_bill_status not null default 'draft',
    invoice_reference text,
    normalized_invoice_reference text generated always as (
        lower(regexp_replace(trim(invoice_reference), '\s+', ' ', 'g'))
    ) stored,
    invoice_date date not null,
    subtotal_paisa bigint not null,
    discount_total_paisa bigint not null default 0,
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
    unique (shop_id, id, vendor_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, vendor_id)
        references public.vendors(shop_id, id) on delete restrict,
    constraint purchase_bills_invoice_reference_not_blank check (
        invoice_reference is null or length(trim(invoice_reference)) > 0
    ),
    constraint purchase_bills_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint purchase_bills_money_nonnegative check (
        subtotal_paisa >= 0 and discount_total_paisa >= 0
        and tax_total_paisa = 0 and grand_total_paisa >= 0
    ),
    constraint purchase_bills_total_reconciles check (
        grand_total_paisa = subtotal_paisa - discount_total_paisa
        and discount_total_paisa <= subtotal_paisa
    ),
    constraint purchase_bills_posted_time_consistent check (
        (status = 'draft' and posted_at is null)
        or (status <> 'draft' and posted_at is not null)
    ),
    constraint purchase_bills_reversal_consistent check (
        (status <> 'reversed' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed' and reversed_at is not null and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create unique index purchase_bills_vendor_invoice_unique
on public.purchase_bills (shop_id, vendor_id, normalized_invoice_reference)
where normalized_invoice_reference is not null;

create table public.purchase_bill_lines (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    purchase_bill_id uuid not null,
    line_number integer not null,
    product_id uuid not null,
    product_name text not null,
    sku_code text not null,
    quantity integer not null,
    unit_cost_paisa bigint not null,
    gross_total_paisa bigint not null,
    discount_paisa bigint not null default 0,
    line_total_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, id, product_id),
    unique (shop_id, purchase_bill_id, id),
    unique (shop_id, purchase_bill_id, id, product_id),
    unique (shop_id, purchase_bill_id, line_number),
    foreign key (shop_id, purchase_bill_id)
        references public.purchase_bills(shop_id, id) on delete restrict,
    foreign key (shop_id, product_id)
        references public.products(shop_id, id) on delete restrict,
    constraint purchase_bill_lines_line_number_positive check (line_number > 0),
    constraint purchase_bill_lines_quantity_positive check (quantity > 0),
    constraint purchase_bill_lines_snapshots_not_blank check (
        length(trim(product_name)) > 0 and length(trim(sku_code)) > 0
    ),
    constraint purchase_bill_lines_money_nonnegative check (
        unit_cost_paisa >= 0 and gross_total_paisa >= 0
        and discount_paisa >= 0 and line_total_paisa >= 0
    ),
    constraint purchase_bill_lines_total_reconciles check (
        gross_total_paisa = unit_cost_paisa * quantity::bigint
        and line_total_paisa = gross_total_paisa - discount_paisa
        and discount_paisa <= gross_total_paisa
    )
);

create table public.purchase_receipts (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    purchase_bill_id uuid not null,
    status public.purchase_event_status not null default 'posted',
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, id, purchase_bill_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, purchase_bill_id)
        references public.purchase_bills(shop_id, id) on delete restrict,
    constraint purchase_receipts_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint purchase_receipts_reversal_consistent check (
        (status = 'posted' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed' and reversed_at is not null and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.purchase_receipt_lines (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    purchase_receipt_id uuid not null,
    purchase_bill_id uuid not null,
    purchase_bill_line_id uuid not null,
    product_id uuid not null,
    quantity integer not null,
    unit_cost_paisa bigint not null,
    line_total_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, id, product_id),
    unique (shop_id, id, purchase_bill_id, product_id),
    unique (shop_id, purchase_receipt_id, purchase_bill_line_id),
    foreign key (shop_id, purchase_receipt_id, purchase_bill_id)
        references public.purchase_receipts(shop_id, id, purchase_bill_id) on delete restrict,
    foreign key (shop_id, purchase_bill_id, purchase_bill_line_id, product_id)
        references public.purchase_bill_lines(shop_id, purchase_bill_id, id, product_id) on delete restrict,
    constraint purchase_receipt_lines_quantity_positive check (quantity > 0),
    constraint purchase_receipt_lines_cost_nonnegative check (unit_cost_paisa >= 0),
    constraint purchase_receipt_lines_total_reconciles check (
        line_total_paisa = unit_cost_paisa * quantity::bigint
    )
);

alter table public.inventory_lots
    add column purchase_receipt_line_id uuid,
    add constraint inventory_lots_purchase_receipt_line_unique
        unique (shop_id, purchase_receipt_line_id),
    add constraint inventory_lots_receipt_line_product_fkey
        foreign key (shop_id, purchase_receipt_line_id, product_id)
        references public.purchase_receipt_lines(shop_id, id, product_id) on delete restrict;

alter table public.inventory_lots
    add constraint inventory_lots_shop_id_id_receipt_product_unique
    unique (shop_id, id, purchase_receipt_line_id, product_id);

create table public.vendor_payments (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    vendor_id uuid not null,
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
    unique (shop_id, id, vendor_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, vendor_id)
        references public.vendors(shop_id, id) on delete restrict,
    constraint vendor_payments_amount_positive check (amount_paisa > 0),
    constraint vendor_payments_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint vendor_payments_reversal_consistent check (
        (status = 'posted' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed' and reversed_at is not null and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.vendor_payment_allocations (
    shop_id uuid not null,
    vendor_payment_id uuid not null,
    vendor_id uuid not null,
    purchase_bill_id uuid not null,
    amount_paisa bigint not null,
    created_at timestamptz not null default now(),
    primary key (shop_id, vendor_payment_id, purchase_bill_id),
    foreign key (shop_id, vendor_payment_id, vendor_id)
        references public.vendor_payments(shop_id, id, vendor_id) on delete restrict,
    foreign key (shop_id, purchase_bill_id, vendor_id)
        references public.purchase_bills(shop_id, id, vendor_id) on delete restrict,
    constraint vendor_payment_allocations_amount_positive check (amount_paisa > 0)
);

create table public.vendor_returns (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    vendor_id uuid not null,
    purchase_bill_id uuid not null,
    status public.purchase_event_status not null default 'posted',
    reason text not null,
    total_value_paisa bigint not null,
    business_date date not null,
    occurred_at timestamptz not null default now(),
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    reversed_at timestamptz,
    reversed_by uuid references auth.users(id) on delete restrict,
    reversal_reason text,
    unique (shop_id, id),
    unique (shop_id, id, purchase_bill_id),
    unique (shop_id, idempotency_key),
    foreign key (shop_id, purchase_bill_id, vendor_id)
        references public.purchase_bills(shop_id, id, vendor_id) on delete restrict,
    constraint vendor_returns_reason_not_blank check (length(trim(reason)) > 0),
    constraint vendor_returns_total_nonnegative check (total_value_paisa >= 0),
    constraint vendor_returns_idempotency_not_blank check (length(trim(idempotency_key)) > 0),
    constraint vendor_returns_reversal_consistent check (
        (status = 'posted' and reversed_at is null and reversed_by is null and reversal_reason is null)
        or (
            status = 'reversed' and reversed_at is not null and reversed_by is not null
            and length(trim(reversal_reason)) > 0
        )
    )
);

create table public.vendor_return_lines (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null,
    vendor_return_id uuid not null,
    purchase_bill_id uuid not null,
    purchase_receipt_line_id uuid not null,
    product_id uuid not null,
    lot_id uuid not null,
    quantity integer not null,
    unit_cost_paisa bigint not null,
    line_total_paisa bigint not null,
    created_at timestamptz not null default now(),
    unique (shop_id, id),
    unique (shop_id, vendor_return_id, purchase_receipt_line_id, lot_id),
    foreign key (shop_id, vendor_return_id, purchase_bill_id)
        references public.vendor_returns(shop_id, id, purchase_bill_id) on delete restrict,
    foreign key (shop_id, purchase_receipt_line_id, purchase_bill_id, product_id)
        references public.purchase_receipt_lines(shop_id, id, purchase_bill_id, product_id) on delete restrict,
    foreign key (shop_id, lot_id, purchase_receipt_line_id, product_id)
        references public.inventory_lots(shop_id, id, purchase_receipt_line_id, product_id) on delete restrict,
    constraint vendor_return_lines_quantity_positive check (quantity > 0),
    constraint vendor_return_lines_cost_nonnegative check (unit_cost_paisa >= 0),
    constraint vendor_return_lines_total_reconciles check (
        line_total_paisa = unit_cost_paisa * quantity::bigint
    )
);

create or replace function private.assert_purchase_bill_integrity(target_bill_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare bill public.purchase_bills%rowtype; line_count bigint; subtotal bigint; discounts bigint;
begin
    select * into bill from public.purchase_bills where id = target_bill_id;
    if not found or bill.status = 'draft' then return; end if;
    select count(*), coalesce(sum(gross_total_paisa),0), coalesce(sum(discount_paisa),0)
    into line_count, subtotal, discounts
    from public.purchase_bill_lines where purchase_bill_id = target_bill_id;
    if line_count = 0 or subtotal <> bill.subtotal_paisa or discounts <> bill.discount_total_paisa then
        raise exception using errcode = '23514', message = 'purchase bill detail does not reconcile';
    end if;
end; $$;

create or replace function private.assert_purchase_receipt_integrity(target_receipt_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare receipt public.purchase_receipts%rowtype; line_count bigint;
begin
    select * into receipt from public.purchase_receipts where id = target_receipt_id;
    if not found or receipt.status = 'reversed' then return; end if;
    select count(*) into line_count from public.purchase_receipt_lines
    where purchase_receipt_id = target_receipt_id;
    if line_count = 0 then
        raise exception using errcode = '23514', message = 'purchase receipt has no detail';
    end if;
    if exists (
        select 1 from public.purchase_receipt_lines candidate
        join public.purchase_receipts event on event.id = candidate.purchase_receipt_id
        where event.purchase_bill_id = receipt.purchase_bill_id and event.status = 'posted'
        group by candidate.purchase_bill_line_id
        having sum(candidate.quantity) > (
            select ordered.quantity from public.purchase_bill_lines ordered
            where ordered.id = candidate.purchase_bill_line_id
        )
    ) then
        raise exception using errcode = '23514', message = 'purchase line over-received';
    end if;
    if exists (
        select 1 from public.purchase_receipt_lines line
        where line.purchase_receipt_id = target_receipt_id
          and line.unit_cost_paisa <> (
              select billed.unit_cost_paisa from public.purchase_bill_lines billed
              where billed.id = line.purchase_bill_line_id
          )
    ) then
        raise exception using errcode = '23514', message = 'receipt cost differs from purchase bill';
    end if;
    if exists (
        select 1 from public.purchase_receipt_lines line
        where line.purchase_receipt_id = target_receipt_id
          and not exists (
              select 1 from public.inventory_lots lot
              where lot.purchase_receipt_line_id = line.id
                and lot.original_quantity = line.quantity
                and lot.unit_cost_paisa = line.unit_cost_paisa
          )
    ) then
        raise exception using errcode = '23514', message = 'receipt FIFO lot does not reconcile';
    end if;
end; $$;

create or replace function private.assert_vendor_payment_integrity(target_payment_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare payment public.vendor_payments%rowtype; allocated bigint;
begin
    select * into payment from public.vendor_payments where id = target_payment_id;
    if not found or payment.status = 'reversed' then return; end if;
    select coalesce(sum(amount_paisa),0) into allocated
    from public.vendor_payment_allocations where vendor_payment_id = target_payment_id;
    if allocated <> payment.amount_paisa then
        raise exception using errcode = '23514', message = 'vendor payment allocation does not reconcile';
    end if;
    if exists (
        select 1 from public.purchase_bills bill
        where bill.vendor_id = payment.vendor_id and bill.status <> 'reversed'
          and (select coalesce(sum(allocation.amount_paisa),0)
               from public.vendor_payment_allocations allocation
               join public.vendor_payments paid on paid.id = allocation.vendor_payment_id
               where allocation.purchase_bill_id = bill.id and paid.status = 'posted')
              > bill.grand_total_paisa
                - (select coalesce(sum(returned.total_value_paisa),0)
                   from public.vendor_returns returned
                   where returned.purchase_bill_id = bill.id and returned.status = 'posted')
    ) then
        raise exception using errcode = '23514', message = 'purchase bill overpaid';
    end if;
end; $$;

create or replace function private.assert_vendor_return_integrity(target_return_id uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare returned public.vendor_returns%rowtype; line_count bigint; line_total bigint;
begin
    select * into returned from public.vendor_returns where id = target_return_id;
    if not found or returned.status = 'reversed' then return; end if;
    select count(*), coalesce(sum(line_total_paisa),0) into line_count, line_total
    from public.vendor_return_lines where vendor_return_id = target_return_id;
    if line_count = 0 or line_total <> returned.total_value_paisa then
        raise exception using errcode = '23514', message = 'vendor return detail does not reconcile';
    end if;
    if exists (
        select 1 from public.vendor_return_lines candidate
        join public.vendor_returns event on event.id = candidate.vendor_return_id
        where event.purchase_bill_id = returned.purchase_bill_id and event.status = 'posted'
        group by candidate.purchase_receipt_line_id
        having sum(candidate.quantity) > (
            select received.quantity from public.purchase_receipt_lines received
            where received.id = candidate.purchase_receipt_line_id
        )
    ) then
        raise exception using errcode = '23514', message = 'purchase receipt line over-returned';
    end if;
    if exists (
        select 1 from public.vendor_return_lines line
        join public.inventory_lots lot on lot.id = line.lot_id
        where line.vendor_return_id = target_return_id
          and line.unit_cost_paisa <> lot.unit_cost_paisa
    ) then
        raise exception using errcode = '23514', message = 'vendor return cost differs from FIFO lot';
    end if;
    if (
        select coalesce(sum(event.total_value_paisa),0)
        from public.vendor_returns event
        where event.purchase_bill_id = returned.purchase_bill_id and event.status = 'posted'
    ) > (
        select bill.grand_total_paisa from public.purchase_bills bill
        where bill.id = returned.purchase_bill_id
    ) then
        raise exception using errcode = '23514', message = 'purchase bill over-returned by value';
    end if;
    if (
        select coalesce(sum(allocation.amount_paisa),0)
        from public.vendor_payment_allocations allocation
        join public.vendor_payments paid on paid.id = allocation.vendor_payment_id
        where allocation.purchase_bill_id = returned.purchase_bill_id and paid.status = 'posted'
    ) > (
        select bill.grand_total_paisa from public.purchase_bills bill
        where bill.id = returned.purchase_bill_id
    ) - (
        select coalesce(sum(event.total_value_paisa),0)
        from public.vendor_returns event
        where event.purchase_bill_id = returned.purchase_bill_id and event.status = 'posted'
    ) then
        raise exception using errcode = '23514', message = 'purchase bill overpaid';
    end if;
end; $$;

create or replace function private.purchasing_integrity_trigger()
returns trigger language plpgsql security definer set search_path = '' as $$
declare bill_id uuid; receipt_id uuid; payment_id uuid; return_id uuid;
begin
    if tg_table_name = 'purchase_bills' then
        bill_id := case when tg_op='DELETE' then old.id else new.id end;
    elsif tg_table_name = 'purchase_bill_lines' then
        bill_id := case when tg_op='DELETE' then old.purchase_bill_id else new.purchase_bill_id end;
    elsif tg_table_name = 'purchase_receipts' then
        receipt_id := case when tg_op='DELETE' then old.id else new.id end;
        bill_id := case when tg_op='DELETE' then old.purchase_bill_id else new.purchase_bill_id end;
    elsif tg_table_name = 'purchase_receipt_lines' then
        receipt_id := case when tg_op='DELETE' then old.purchase_receipt_id else new.purchase_receipt_id end;
        bill_id := case when tg_op='DELETE' then old.purchase_bill_id else new.purchase_bill_id end;
    elsif tg_table_name = 'inventory_lots' then
        receipt_id := (select line.purchase_receipt_id from public.purchase_receipt_lines line
            where line.id = case when tg_op='DELETE' then old.purchase_receipt_line_id else new.purchase_receipt_line_id end);
    elsif tg_table_name = 'vendor_payments' then
        payment_id := case when tg_op='DELETE' then old.id else new.id end;
    elsif tg_table_name = 'vendor_payment_allocations' then
        payment_id := case when tg_op='DELETE' then old.vendor_payment_id else new.vendor_payment_id end;
    elsif tg_table_name = 'vendor_returns' then
        return_id := case when tg_op='DELETE' then old.id else new.id end;
        bill_id := case when tg_op='DELETE' then old.purchase_bill_id else new.purchase_bill_id end;
    elsif tg_table_name = 'vendor_return_lines' then
        return_id := case when tg_op='DELETE' then old.vendor_return_id else new.vendor_return_id end;
    end if;
    if bill_id is not null then perform private.assert_purchase_bill_integrity(bill_id); end if;
    if receipt_id is not null then perform private.assert_purchase_receipt_integrity(receipt_id); end if;
    if payment_id is not null then perform private.assert_vendor_payment_integrity(payment_id); end if;
    if return_id is not null then perform private.assert_vendor_return_integrity(return_id); end if;
    return null;
end; $$;

create constraint trigger purchase_bills_integrity after insert or update or delete on public.purchase_bills
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger purchase_bill_lines_integrity after insert or update or delete on public.purchase_bill_lines
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger purchase_receipts_integrity after insert or update or delete on public.purchase_receipts
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger purchase_receipt_lines_integrity after insert or update or delete on public.purchase_receipt_lines
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger purchase_lots_integrity after insert or update or delete on public.inventory_lots
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger vendor_payments_integrity after insert or update or delete on public.vendor_payments
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger vendor_payment_allocations_integrity after insert or update or delete on public.vendor_payment_allocations
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger vendor_returns_integrity after insert or update or delete on public.vendor_returns
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();
create constraint trigger vendor_return_lines_integrity after insert or update or delete on public.vendor_return_lines
deferrable initially deferred for each row execute function private.purchasing_integrity_trigger();

create index vendors_shop_active_name_idx on public.vendors (shop_id, active, display_name);
create index purchase_bills_vendor_date_idx on public.purchase_bills (shop_id, vendor_id, invoice_date desc);
create index purchase_bill_lines_product_idx on public.purchase_bill_lines (shop_id, product_id, purchase_bill_id);
create index purchase_receipts_bill_idx on public.purchase_receipts (shop_id, purchase_bill_id, occurred_at);
create index purchase_receipt_lines_bill_line_idx on public.purchase_receipt_lines (shop_id, purchase_bill_line_id);
create index vendor_payments_vendor_idx on public.vendor_payments (shop_id, vendor_id, occurred_at);
create index vendor_payment_allocations_bill_idx on public.vendor_payment_allocations (shop_id, purchase_bill_id);
create index vendor_returns_bill_idx on public.vendor_returns (shop_id, purchase_bill_id, occurred_at);
create index vendor_return_lines_receipt_idx on public.vendor_return_lines (shop_id, purchase_receipt_line_id);

alter table public.vendors enable row level security;
alter table public.purchase_bills enable row level security;
alter table public.purchase_bill_lines enable row level security;
alter table public.purchase_receipts enable row level security;
alter table public.purchase_receipt_lines enable row level security;
alter table public.vendor_payments enable row level security;
alter table public.vendor_payment_allocations enable row level security;
alter table public.vendor_returns enable row level security;
alter table public.vendor_return_lines enable row level security;

create policy vendors_select_owner on public.vendors for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy purchase_bills_select_owner on public.purchase_bills for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy purchase_bill_lines_select_owner on public.purchase_bill_lines for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy purchase_receipts_select_owner on public.purchase_receipts for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy purchase_receipt_lines_select_owner on public.purchase_receipt_lines for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy vendor_payments_select_owner on public.vendor_payments for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy vendor_payment_allocations_select_owner on public.vendor_payment_allocations for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy vendor_returns_select_owner on public.vendor_returns for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));
create policy vendor_return_lines_select_owner on public.vendor_return_lines for select to authenticated
using (private.is_super_admin() or private.has_shop_role(shop_id,array['owner']::public.shop_role[]));

revoke all on table public.vendors, public.purchase_bills, public.purchase_bill_lines,
    public.purchase_receipts, public.purchase_receipt_lines, public.vendor_payments,
    public.vendor_payment_allocations, public.vendor_returns, public.vendor_return_lines
from public, anon, authenticated;
grant select on table public.vendors, public.purchase_bills, public.purchase_bill_lines,
    public.purchase_receipts, public.purchase_receipt_lines, public.vendor_payments,
    public.vendor_payment_allocations, public.vendor_returns, public.vendor_return_lines
to authenticated;

revoke all on function private.assert_purchase_bill_integrity(uuid),
    private.assert_purchase_receipt_integrity(uuid),
    private.assert_vendor_payment_integrity(uuid),
    private.assert_vendor_return_integrity(uuid),
    private.purchasing_integrity_trigger()
from public, anon, authenticated;

comment on table public.vendors is 'Owner-only vendor master; balance is derived from immutable purchasing events.';
comment on table public.purchase_bills is 'Posted commercial snapshot with permanent per-vendor invoice identity.';
comment on table public.purchase_receipt_lines is 'Authoritative received quantity/cost; exactly one FIFO lot must reconcile.';
comment on table public.vendor_payments is 'Fully bill-allocated vendor payment; account/journal linkage is added by Task 2.5.';
comment on table public.vendor_returns is 'Lot-linked vendor credit event; corrections use protected reversal state.';

commit;
