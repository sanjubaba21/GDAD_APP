begin;

create type public.notification_category as enum (
    'low_stock', 'sale', 'return', 'purchase', 'vendor_due',
    'payment_due', 'expense', 'transfer', 'account', 'system'
);
create type public.notification_record_type as enum (
    'product', 'sale', 'sale_return', 'purchase_bill', 'purchase_receipt',
    'vendor', 'vendor_return', 'expense', 'journal_transaction',
    'user_profile', 'system'
);

create table public.notifications (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    category public.notification_category not null,
    recipient_user_id uuid references public.user_profiles(user_id) on delete restrict,
    target_role public.shop_role,
    title text not null,
    body text not null,
    record_type public.notification_record_type not null,
    record_id uuid,
    safe_payload jsonb not null default '{}'::jsonb,
    created_by uuid references auth.users(id) on delete restrict,
    idempotency_key text not null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null default (now() + interval '90 days'),
    unique (shop_id, id),
    unique (shop_id, idempotency_key),
    constraint notifications_exactly_one_target check (
        (recipient_user_id is not null)::integer + (target_role is not null)::integer = 1
    ),
    constraint notifications_title_not_blank check (length(trim(title)) between 1 and 160),
    constraint notifications_body_not_blank check (length(trim(body)) between 1 and 1000),
    constraint notifications_record_reference_consistent check (
        (record_type = 'system' and record_id is null)
        or (record_type <> 'system' and record_id is not null)
    ),
    constraint notifications_idempotency_not_blank check (length(trim(idempotency_key)) between 1 and 160),
    constraint notifications_exact_retention check (expires_at = created_at + interval '90 days'),
    constraint notifications_payload_object check (jsonb_typeof(safe_payload) = 'object')
);

create table public.notification_reads (
    shop_id uuid not null,
    notification_id uuid not null,
    user_id uuid not null references public.user_profiles(user_id) on delete restrict,
    read_at timestamptz not null default now(),
    primary key (notification_id, user_id),
    foreign key (shop_id, notification_id)
        references public.notifications(shop_id, id) on delete cascade
);

create table private.business_audit_events (
    id uuid primary key default gen_random_uuid(),
    shop_id uuid not null references public.shops(id) on delete restrict,
    actor_user_id uuid not null references auth.users(id) on delete restrict,
    operation text not null,
    record_type text not null,
    record_id uuid not null,
    before_metadata jsonb not null default '{}'::jsonb,
    after_metadata jsonb not null default '{}'::jsonb,
    correlation_id uuid,
    idempotency_key text not null,
    occurred_at timestamptz not null default now(),
    unique (shop_id, idempotency_key),
    constraint business_audit_operation_format check (
        operation = lower(trim(operation)) and operation ~ '^[a-z][a-z0-9_]{1,63}$'
    ),
    constraint business_audit_record_type_format check (
        record_type = lower(trim(record_type)) and record_type ~ '^[a-z][a-z0-9_]{1,63}$'
    ),
    constraint business_audit_idempotency_not_blank check (length(trim(idempotency_key)) between 1 and 160),
    constraint business_audit_before_object check (jsonb_typeof(before_metadata) = 'object'),
    constraint business_audit_after_object check (jsonb_typeof(after_metadata) = 'object')
);

create or replace function private.jsonb_metadata_is_safe(payload jsonb)
returns boolean language plpgsql immutable set search_path = '' as $$
declare pair record; item jsonb;
begin
    if payload is null or pg_column_size(payload) > 8192 then return false; end if;
    if jsonb_typeof(payload) = 'object' then
        for pair in select key, value from jsonb_each(payload) loop
            if lower(pair.key) ~ '(^|_)(pin|password|passwd|secret|token|authorization|cookie|credential|verifier|hash|service_role|api_key|private_key)($|_)' then
                return false;
            end if;
            if not private.jsonb_metadata_is_safe(pair.value) then return false; end if;
        end loop;
    elsif jsonb_typeof(payload) = 'array' then
        for item in select value from jsonb_array_elements(payload) loop
            if not private.jsonb_metadata_is_safe(item) then return false; end if;
        end loop;
    elsif jsonb_typeof(payload) = 'string' and length(payload #>> '{}') > 1000 then
        return false;
    end if;
    return true;
end;
$$;

alter table public.notifications add constraint notifications_payload_safe
    check (private.jsonb_metadata_is_safe(safe_payload));
alter table private.business_audit_events add constraint business_audit_before_safe
    check (private.jsonb_metadata_is_safe(before_metadata));
alter table private.business_audit_events add constraint business_audit_after_safe
    check (private.jsonb_metadata_is_safe(after_metadata));

create or replace function private.notification_source_exists(
    target_type public.notification_record_type, target_shop_id uuid, target_record_id uuid
)
returns boolean language plpgsql stable security definer set search_path = '' as $$
begin
    case target_type
      when 'product' then return exists(select 1 from public.products where shop_id=target_shop_id and id=target_record_id);
      when 'sale' then return exists(select 1 from public.sales where shop_id=target_shop_id and id=target_record_id);
      when 'sale_return' then return exists(select 1 from public.sale_returns where shop_id=target_shop_id and id=target_record_id);
      when 'purchase_bill' then return exists(select 1 from public.purchase_bills where shop_id=target_shop_id and id=target_record_id);
      when 'purchase_receipt' then return exists(select 1 from public.purchase_receipts where shop_id=target_shop_id and id=target_record_id);
      when 'vendor' then return exists(select 1 from public.vendors where shop_id=target_shop_id and id=target_record_id);
      when 'vendor_return' then return exists(select 1 from public.vendor_returns where shop_id=target_shop_id and id=target_record_id);
      when 'expense' then return exists(select 1 from public.expenses where shop_id=target_shop_id and id=target_record_id);
      when 'journal_transaction' then return exists(select 1 from public.journal_transactions where shop_id=target_shop_id and id=target_record_id);
      when 'user_profile' then return exists(select 1 from public.shop_memberships where shop_id=target_shop_id and user_id=target_record_id);
      when 'system' then return target_record_id is null;
    end case;
end;
$$;

create or replace function private.user_authorized_for_shop(target_user_id uuid, target_shop_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.user_profiles profile
        where profile.user_id=target_user_id and not profile.disabled
          and (profile.platform_role='super_admin' or exists (
            select 1 from public.shop_memberships membership
            join public.shops shop on shop.id=membership.shop_id
            where membership.user_id=target_user_id and membership.shop_id=target_shop_id
              and membership.active and shop.active
          ))
    );
$$;

create or replace function private.validate_notification()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if new.recipient_user_id is not null and not exists (
        select 1 from public.shop_memberships membership
        join public.user_profiles profile on profile.user_id=membership.user_id
        join public.shops shop on shop.id=membership.shop_id
        where membership.shop_id=new.shop_id and membership.user_id=new.recipient_user_id
          and membership.active and not profile.disabled and shop.active
    ) then
        raise exception using errcode='23503', message='notification recipient is not an active shop member';
    end if;
    if new.created_by is not null and not private.user_authorized_for_shop(new.created_by,new.shop_id) then
        raise exception using errcode='42501', message='notification actor is not authorized for shop';
    end if;
    if not private.notification_source_exists(new.record_type,new.shop_id,new.record_id) then
        raise exception using errcode='23503', message='notification source does not exist in shop';
    end if;
    return new;
end;
$$;

create trigger notifications_validate before insert or update on public.notifications
for each row execute function private.validate_notification();

create or replace function private.notification_visible_to(target_notification_id uuid, target_user_id uuid)
returns boolean language sql stable security definer set search_path = '' as $$
    select exists (
        select 1 from public.notifications notification
        join public.user_profiles profile on profile.user_id=target_user_id and not profile.disabled
        join public.shops shop on shop.id=notification.shop_id and shop.active
        where notification.id=target_notification_id and notification.expires_at > now()
          and (profile.platform_role='super_admin' or notification.recipient_user_id=target_user_id or exists (
            select 1 from public.shop_memberships membership
            where membership.shop_id=notification.shop_id and membership.user_id=target_user_id
              and membership.role=notification.target_role and membership.active
          ))
    );
$$;

create or replace function public.mark_notification_read(target_notification_id uuid)
returns timestamptz language plpgsql security definer set search_path = '' as $$
declare result timestamptz; actor uuid := (select auth.uid()); target_shop uuid;
begin
    if actor is null or not private.notification_visible_to(target_notification_id,actor) then
        raise exception using errcode='42501', message='notification is not available';
    end if;
    select shop_id into target_shop from public.notifications where id=target_notification_id;
    insert into public.notification_reads(shop_id,notification_id,user_id)
    values(target_shop,target_notification_id,actor)
    on conflict (notification_id,user_id) do nothing returning read_at into result;
    if result is null then
        select read_at into result from public.notification_reads
        where notification_id=target_notification_id and user_id=actor;
    end if;
    return result;
end;
$$;

create or replace function public.cleanup_expired_notifications(batch_size integer default 500)
returns integer language plpgsql security definer set search_path = '' as $$
declare removed integer;
begin
    if batch_size not between 1 and 1000 then
        raise exception using errcode='22023', message='cleanup batch size must be between 1 and 1000';
    end if;
    with expired as (
      select id from public.notifications where expires_at <= now()
      order by expires_at, id limit batch_size for update skip locked
    )
    delete from public.notifications notification using expired where notification.id=expired.id;
    get diagnostics removed = row_count;
    return removed;
end;
$$;

create or replace function private.reject_business_audit_mutation()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    raise exception using errcode='55000', message='business audit events are append-only';
end;
$$;
create trigger business_audit_events_immutable before update or delete on private.business_audit_events
for each row execute function private.reject_business_audit_mutation();

create or replace function private.validate_business_audit_actor()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
    if not private.user_authorized_for_shop(new.actor_user_id,new.shop_id) then
        raise exception using errcode='42501', message='audit actor is not authorized for shop';
    end if;
    return new;
end;
$$;
create trigger business_audit_events_validate_actor before insert on private.business_audit_events
for each row execute function private.validate_business_audit_actor();

create index notifications_recipient_active_idx on public.notifications (recipient_user_id,expires_at desc,created_at desc) where recipient_user_id is not null;
create index notifications_role_active_idx on public.notifications (shop_id,target_role,expires_at desc,created_at desc) where target_role is not null;
create index notification_reads_user_idx on public.notification_reads (user_id,read_at desc);
create index business_audit_record_timeline_idx on private.business_audit_events (shop_id,record_type,record_id,occurred_at desc);

alter table public.notifications enable row level security;
alter table public.notification_reads enable row level security;
alter table private.business_audit_events enable row level security;
create policy notifications_select_target on public.notifications for select to authenticated
using (
    expires_at > now()
    and (
      private.is_super_admin()
      or recipient_user_id=(select auth.uid())
      or (
        target_role is not null
        and private.has_shop_role(shop_id,array[target_role]::public.shop_role[])
      )
    )
);
create policy notification_reads_select_self on public.notification_reads for select to authenticated
using (
    (user_id=(select auth.uid()) or private.is_super_admin())
    and exists (
      select 1 from public.notifications notification
      where notification.id=notification_id
    )
);

revoke all on table public.notifications,public.notification_reads from public,anon,authenticated;
grant select on table public.notifications,public.notification_reads to authenticated;
revoke all on table private.business_audit_events from public,anon,authenticated;
revoke all on function private.jsonb_metadata_is_safe(jsonb),private.notification_source_exists(public.notification_record_type,uuid,uuid),
    private.user_authorized_for_shop(uuid,uuid),private.validate_notification(),private.notification_visible_to(uuid,uuid),
    private.reject_business_audit_mutation(),private.validate_business_audit_actor(),public.mark_notification_read(uuid),
    public.cleanup_expired_notifications(integer) from public,anon,authenticated;
grant execute on function public.mark_notification_read(uuid) to authenticated;
grant execute on function public.cleanup_expired_notifications(integer) to service_role;

comment on table public.notifications is 'Immutable tenant notification sources retained for exactly 90 days; business records remain authoritative.';
comment on table public.notification_reads is 'Per-recipient first-read evidence written only through mark_notification_read.';
comment on table private.business_audit_events is 'Indefinitely retained first-release append-only business audit; safe metadata forbids credential fields.';

commit;
