begin;

create table private.shop_deletion_requests (
  request_id uuid primary key,
  actor_user_id uuid not null references public.user_profiles(user_id) on delete restrict,
  target_shop_id uuid not null,
  target_slug text not null,
  target_display_name text not null,
  confirmation_slug text not null,
  reason text not null,
  source_fingerprint text not null,
  status text not null default 'reserved',
  failure_code text,
  managed_auth_users jsonb not null default '[]'::jsonb,
  auth_cleanup_pending boolean not null default false,
  summary jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  completed_at timestamptz,
  constraint shop_deletion_slug_valid check (
    target_slug ~ '^[a-z0-9][a-z0-9-]{2,62}$'
    and confirmation_slug = target_slug
  ),
  constraint shop_deletion_reason_valid check (length(reason) between 8 and 500),
  constraint shop_deletion_source_valid check (source_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint shop_deletion_status_valid check (status in ('reserved', 'complete', 'failed')),
  constraint shop_deletion_managed_users_array check (
    jsonb_typeof(managed_auth_users) = 'array'
  ),
  constraint shop_deletion_summary_object check (
    summary is null or jsonb_typeof(summary) = 'object'
  ),
  constraint shop_deletion_completion_shape check (
    (status = 'reserved' and completed_at is null and failure_code is null
      and not auth_cleanup_pending and summary is null)
    or (status = 'failed' and completed_at is null and failure_code is not null
      and not auth_cleanup_pending and summary is null)
    or (status = 'complete' and completed_at is not null and failure_code is null
      and summary is not null)
  )
);

create table private.shop_deletion_audit_events (
  id uuid primary key default extensions.gen_random_uuid(),
  request_id uuid not null unique
    references private.shop_deletion_requests(request_id) on delete restrict,
  actor_user_id uuid not null,
  deleted_shop_id uuid not null,
  deleted_shop_slug text not null,
  deleted_shop_display_name text not null,
  reason text not null,
  safe_summary jsonb not null,
  created_at timestamptz not null default now(),
  constraint shop_deletion_audit_slug_valid check (
    deleted_shop_slug ~ '^[a-z0-9][a-z0-9-]{2,62}$'
  ),
  constraint shop_deletion_audit_reason_valid check (length(reason) between 8 and 500),
  constraint shop_deletion_audit_summary_object check (
    jsonb_typeof(safe_summary) = 'object'
  )
);

create trigger shop_deletion_requests_set_updated_at
before update on private.shop_deletion_requests
for each row execute function public.set_updated_at();

create or replace function private.reject_shop_deletion_audit_mutation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  raise exception using errcode = '55000', message = 'shop deletion audit is immutable';
end;
$$;

create trigger shop_deletion_audit_events_immutable
before update or delete on private.shop_deletion_audit_events
for each row execute function private.reject_shop_deletion_audit_mutation();

-- Business audit is normally immutable. A service-role-only shop deletion transaction
-- may remove exactly the deleted shop's events after setting a transaction-local marker.
create or replace function private.reject_business_audit_mutation()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  if tg_op = 'DELETE'
     and pg_catalog.current_setting('app.shop_deletion_id', true) = old.shop_id::text then
    return old;
  end if;
  raise exception using errcode = '55000',
    message = 'business audit events are append-only';
end;
$$;

create or replace function private.shop_deletion_schema_is_current()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  with expected(table_schema, table_name) as (
    values
      ('private', 'account_admin_requests'),
      ('private', 'account_audit_events'),
      ('private', 'account_provisioning_requests'),
      ('private', 'business_audit_events'),
      ('private', 'financial_operation_requests'),
      ('private', 'inventory_adjustment_operation_requests'),
      ('private', 'product_code_reservations'),
      ('private', 'product_operation_requests'),
      ('private', 'purchase_operation_requests'),
      ('private', 'sale_operation_requests'),
      ('private', 'sale_return_operation_requests'),
      ('private', 'shop_creation_requests'),
      ('private', 'vendor_operation_requests'),
      ('public', 'accounting_periods'),
      ('public', 'expenses'),
      ('public', 'financial_accounts'),
      ('public', 'inventory_adjustments'),
      ('public', 'inventory_lots'),
      ('public', 'inventory_movements'),
      ('public', 'journal_entries'),
      ('public', 'journal_transactions'),
      ('public', 'notification_reads'),
      ('public', 'notifications'),
      ('public', 'products'),
      ('public', 'purchase_bill_lines'),
      ('public', 'purchase_bills'),
      ('public', 'purchase_receipt_lines'),
      ('public', 'purchase_receipts'),
      ('public', 'refunds'),
      ('public', 'sale_lines'),
      ('public', 'sale_lot_allocations'),
      ('public', 'sale_payments'),
      ('public', 'sale_return_allocations'),
      ('public', 'sale_return_lines'),
      ('public', 'sale_returns'),
      ('public', 'sales'),
      ('public', 'shop_memberships'),
      ('public', 'vendor_payment_allocations'),
      ('public', 'vendor_payments'),
      ('public', 'vendor_return_lines'),
      ('public', 'vendor_returns'),
      ('public', 'vendors')
  ),
  actual(table_schema, table_name) as (
    select columns.table_schema::text, columns.table_name::text
    from information_schema.columns as columns
    join information_schema.tables as tables
      on tables.table_schema = columns.table_schema
     and tables.table_name = columns.table_name
    where columns.column_name = 'shop_id'
      and columns.table_schema in ('public', 'private')
      and tables.table_type = 'BASE TABLE'
  )
  select not exists (
    (select * from expected except select * from actual)
    union all
    (select * from actual except select * from expected)
  );
$$;

create or replace function private.shop_deletion_has_tenant_rows(p_shop_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
  select
    exists(select 1 from private.account_admin_requests where shop_id = p_shop_id)
    or exists(select 1 from private.account_audit_events where shop_id = p_shop_id)
    or exists(select 1 from private.account_provisioning_requests where shop_id = p_shop_id)
    or exists(select 1 from private.business_audit_events where shop_id = p_shop_id)
    or exists(select 1 from private.financial_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.inventory_adjustment_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.product_code_reservations where shop_id = p_shop_id)
    or exists(select 1 from private.product_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.purchase_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.sale_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.sale_return_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.shop_creation_requests where shop_id = p_shop_id)
    or exists(select 1 from private.vendor_operation_requests where shop_id = p_shop_id)
    or exists(select 1 from public.accounting_periods where shop_id = p_shop_id)
    or exists(select 1 from public.expenses where shop_id = p_shop_id)
    or exists(select 1 from public.financial_accounts where shop_id = p_shop_id)
    or exists(select 1 from public.inventory_adjustments where shop_id = p_shop_id)
    or exists(select 1 from public.inventory_lots where shop_id = p_shop_id)
    or exists(select 1 from public.inventory_movements where shop_id = p_shop_id)
    or exists(select 1 from public.journal_entries where shop_id = p_shop_id)
    or exists(select 1 from public.journal_transactions where shop_id = p_shop_id)
    or exists(select 1 from public.notification_reads where shop_id = p_shop_id)
    or exists(select 1 from public.notifications where shop_id = p_shop_id)
    or exists(select 1 from public.products where shop_id = p_shop_id)
    or exists(select 1 from public.purchase_bill_lines where shop_id = p_shop_id)
    or exists(select 1 from public.purchase_bills where shop_id = p_shop_id)
    or exists(select 1 from public.purchase_receipt_lines where shop_id = p_shop_id)
    or exists(select 1 from public.purchase_receipts where shop_id = p_shop_id)
    or exists(select 1 from public.refunds where shop_id = p_shop_id)
    or exists(select 1 from public.sale_lines where shop_id = p_shop_id)
    or exists(select 1 from public.sale_lot_allocations where shop_id = p_shop_id)
    or exists(select 1 from public.sale_payments where shop_id = p_shop_id)
    or exists(select 1 from public.sale_return_allocations where shop_id = p_shop_id)
    or exists(select 1 from public.sale_return_lines where shop_id = p_shop_id)
    or exists(select 1 from public.sale_returns where shop_id = p_shop_id)
    or exists(select 1 from public.sales where shop_id = p_shop_id)
    or exists(select 1 from public.shop_memberships where shop_id = p_shop_id)
    or exists(select 1 from public.vendor_payment_allocations where shop_id = p_shop_id)
    or exists(select 1 from public.vendor_payments where shop_id = p_shop_id)
    or exists(select 1 from public.vendor_return_lines where shop_id = p_shop_id)
    or exists(select 1 from public.vendor_returns where shop_id = p_shop_id)
    or exists(select 1 from public.vendors where shop_id = p_shop_id);
$$;

create or replace function public.shop_delete_prepare(
  p_request_id uuid,
  p_actor_user_id uuid,
  p_target_shop_id uuid,
  p_confirmation_slug text,
  p_reason text,
  p_source_fingerprint text,
  p_request_time timestamptz
)
returns table (
  reservation_status text,
  actor_pin_hash text,
  actor_pepper_version smallint,
  target_shop_id uuid,
  target_slug text,
  target_display_name text,
  managed_auth_users jsonb,
  auth_cleanup_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  existing_request private.shop_deletion_requests%rowtype;
  target_shop public.shops%rowtype;
  normalized_reason text := trim(p_reason);
  source_limited boolean;
  actor_limited boolean;
begin
  if p_request_id is null
     or p_actor_user_id is null
     or p_target_shop_id is null
     or p_confirmation_slug is null
     or p_confirmation_slug !~ '^[a-z0-9][a-z0-9-]{2,62}$'
     or normalized_reason is null
     or length(normalized_reason) not between 8 and 500
     or p_source_fingerprint is null
     or p_source_fingerprint !~ '^[0-9a-f]{64}$'
     or p_request_time is null then
    raise exception using errcode = '22023', message = 'invalid shop deletion input';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('gdad-shop-delete:' || p_request_id::text, 0)
  );

  select * into existing_request
  from private.shop_deletion_requests as request
  where request.request_id = p_request_id;

  if found then
    if existing_request.actor_user_id <> p_actor_user_id
       or existing_request.target_shop_id <> p_target_shop_id
       or existing_request.confirmation_slug <> p_confirmation_slug
       or existing_request.reason <> normalized_reason then
      raise exception using errcode = '22023', message = 'shop deletion request mismatch';
    end if;
    if existing_request.status = 'complete' then
      return query select
        'complete'::text,
        null::text,
        null::smallint,
        existing_request.target_shop_id,
        existing_request.target_slug,
        existing_request.target_display_name,
        existing_request.managed_auth_users,
        existing_request.auth_cleanup_pending;
      return;
    end if;
    if existing_request.status = 'failed' then
      raise exception using errcode = '42501', message = 'shop deletion denied';
    end if;
  else
    if not exists (
      select 1
      from public.user_profiles as actor
      where actor.user_id = p_actor_user_id
        and actor.platform_role = 'super_admin'
        and not actor.disabled
    ) then
      raise exception using errcode = '42501', message = 'shop deletion denied';
    end if;

    select * into target_shop
    from public.shops as shop
    where shop.id = p_target_shop_id and shop.active
    for update;
    if not found or target_shop.slug <> p_confirmation_slug then
      raise exception using errcode = '42501', message = 'shop deletion denied';
    end if;

    source_limited := private.account_admin_consume_rate(
      'shop-delete-source:' || p_source_fingerprint, p_request_time, 10
    );
    actor_limited := private.account_admin_consume_rate(
      'shop-delete-actor:' || p_actor_user_id::text, p_request_time, 5
    );
    if source_limited or actor_limited then
      raise exception using errcode = '42501', message = 'shop deletion denied';
    end if;

    insert into private.shop_deletion_requests (
      request_id, actor_user_id, target_shop_id, target_slug,
      target_display_name, confirmation_slug, reason, source_fingerprint
    ) values (
      p_request_id, p_actor_user_id, p_target_shop_id, target_shop.slug,
      target_shop.display_name, p_confirmation_slug, normalized_reason,
      p_source_fingerprint
    ) returning * into existing_request;
  end if;

  if not exists (
    select 1
    from public.user_profiles as actor
    where actor.user_id = p_actor_user_id
      and actor.platform_role = 'super_admin'
      and not actor.disabled
  ) or not exists (
    select 1
    from public.shops as shop
    where shop.id = p_target_shop_id
      and shop.active
      and shop.slug = p_confirmation_slug
  ) then
    raise exception using errcode = '42501', message = 'shop deletion denied';
  end if;

  return query
  select
    'reserved'::text,
    credentials.pin_hash,
    credentials.pepper_version,
    existing_request.target_shop_id,
    existing_request.target_slug,
    existing_request.target_display_name,
    '[]'::jsonb,
    false
  from private.login_credentials as credentials
  where credentials.user_id = p_actor_user_id;
end;
$$;

create or replace function public.shop_delete_fail(
  p_request_id uuid,
  p_failure_code text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_request_id is null
     or p_failure_code is null
     or p_failure_code !~ '^[A-Z0-9_]{3,64}$' then
    raise exception using errcode = '22023', message = 'invalid shop deletion failure';
  end if;
  update private.shop_deletion_requests as request
  set status = 'failed', failure_code = p_failure_code
  where request.request_id = p_request_id and request.status = 'reserved';
end;
$$;

create or replace function public.shop_delete_apply(p_request_id uuid)
returns table (
  target_shop_id uuid,
  target_slug text,
  target_display_name text,
  managed_auth_users jsonb,
  auth_cleanup_pending boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
  deletion private.shop_deletion_requests%rowtype;
  exclusive_user_ids uuid[] := array[]::uuid[];
  managed_users jsonb := '[]'::jsonb;
  deletion_summary jsonb;
  deleted_rows bigint;
begin
  if p_request_id is null then
    raise exception using errcode = '22023', message = 'invalid shop deletion request';
  end if;

  select * into deletion
  from private.shop_deletion_requests as request
  where request.request_id = p_request_id
  for update;
  if not found or deletion.status = 'failed' then
    raise exception using errcode = '42501', message = 'shop deletion denied';
  end if;
  if deletion.status = 'complete' then
    return query select
      deletion.target_shop_id,
      deletion.target_slug,
      deletion.target_display_name,
      deletion.managed_auth_users,
      deletion.auth_cleanup_pending;
    return;
  end if;

  if not exists (
    select 1
    from public.user_profiles as actor
    where actor.user_id = deletion.actor_user_id
      and actor.platform_role = 'super_admin'
      and not actor.disabled
  ) or not exists (
    select 1
    from public.shops as shop
    where shop.id = deletion.target_shop_id
      and shop.active
      and shop.slug = deletion.target_slug
  ) then
    raise exception using errcode = '42501', message = 'shop deletion denied';
  end if;

  perform pg_catalog.pg_advisory_xact_lock(
    pg_catalog.hashtextextended('gdad-shop:' || deletion.target_shop_id::text, 0)
  );
  perform pg_catalog.set_config(
    'app.shop_deletion_id', deletion.target_shop_id::text, true
  );

  if not private.shop_deletion_schema_is_current() then
    raise exception using errcode = '55000',
      message = 'shop deletion schema manifest mismatch';
  end if;

  select coalesce(array_agg(profile.user_id order by profile.user_id), array[]::uuid[])
  into exclusive_user_ids
  from public.user_profiles as profile
  where profile.platform_role = 'standard'
    and exists (
      select 1 from public.shop_memberships as membership
      where membership.user_id = profile.user_id
        and membership.shop_id = deletion.target_shop_id
    )
    and not exists (
      select 1 from public.shop_memberships as other_membership
      where other_membership.user_id = profile.user_id
        and other_membership.shop_id <> deletion.target_shop_id
    );

  select coalesce(
    jsonb_agg(
      jsonb_build_object(
        'user_id', provisioning.auth_user_id,
        'provisioning_request_id', provisioning.request_id
      ) order by provisioning.auth_user_id
    ),
    '[]'::jsonb
  ) into managed_users
  from private.account_provisioning_requests as provisioning
  where provisioning.auth_user_id = any(exclusive_user_ids)
    and provisioning.request_id = provisioning.auth_user_id
    and provisioning.operation in ('create_owner', 'create_salesman')
    and provisioning.status = 'complete';

  update public.shops as shop
  set active = false
  where shop.id = deletion.target_shop_id;

  delete from auth.sessions as session
  where session.user_id = any(exclusive_user_ids);

  -- Keep this explicit order synchronized with shop_deletion_schema_is_current.
  -- Child rows are removed before their restrictive foreign-key parents.
  delete from private.account_audit_events where shop_id = deletion.target_shop_id;
  delete from private.account_admin_requests where shop_id = deletion.target_shop_id;
  delete from private.account_provisioning_requests where shop_id = deletion.target_shop_id;
  delete from private.financial_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.inventory_adjustment_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.product_code_reservations where shop_id = deletion.target_shop_id;
  delete from private.product_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.purchase_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.sale_return_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.sale_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.vendor_operation_requests where shop_id = deletion.target_shop_id;
  delete from private.shop_creation_requests where shop_id = deletion.target_shop_id;
  delete from private.business_audit_events where shop_id = deletion.target_shop_id;

  delete from public.notification_reads where shop_id = deletion.target_shop_id;
  delete from public.notifications where shop_id = deletion.target_shop_id;
  delete from public.sale_return_allocations where shop_id = deletion.target_shop_id;
  delete from public.refunds where shop_id = deletion.target_shop_id;
  delete from public.sale_return_lines where shop_id = deletion.target_shop_id;
  delete from public.sale_returns where shop_id = deletion.target_shop_id;
  delete from public.sale_lot_allocations where shop_id = deletion.target_shop_id;
  delete from public.sale_payments where shop_id = deletion.target_shop_id;
  delete from public.vendor_return_lines where shop_id = deletion.target_shop_id;
  delete from public.vendor_payment_allocations where shop_id = deletion.target_shop_id;
  delete from public.inventory_adjustments where shop_id = deletion.target_shop_id;
  delete from public.inventory_movements where shop_id = deletion.target_shop_id;
  delete from public.vendor_returns where shop_id = deletion.target_shop_id;
  delete from public.vendor_payments where shop_id = deletion.target_shop_id;
  delete from public.inventory_lots where shop_id = deletion.target_shop_id;
  delete from public.purchase_receipt_lines where shop_id = deletion.target_shop_id;
  delete from public.purchase_receipts where shop_id = deletion.target_shop_id;
  delete from public.purchase_bill_lines where shop_id = deletion.target_shop_id;
  delete from public.purchase_bills where shop_id = deletion.target_shop_id;
  delete from public.sale_lines where shop_id = deletion.target_shop_id;
  delete from public.sales where shop_id = deletion.target_shop_id;
  delete from public.expenses where shop_id = deletion.target_shop_id;
  delete from public.journal_entries where shop_id = deletion.target_shop_id;
  loop
    delete from public.journal_transactions as journal
    where journal.shop_id = deletion.target_shop_id
      and journal.reversal_of_id is not null
      and not exists (
        select 1
        from public.journal_transactions as child
        where child.shop_id = journal.shop_id
          and child.reversal_of_id = journal.id
      );
    get diagnostics deleted_rows = row_count;
    exit when deleted_rows = 0;
  end loop;
  delete from public.journal_transactions where shop_id = deletion.target_shop_id;
  delete from public.financial_accounts where shop_id = deletion.target_shop_id;
  delete from public.accounting_periods where shop_id = deletion.target_shop_id;
  delete from public.products where shop_id = deletion.target_shop_id;
  delete from public.vendors where shop_id = deletion.target_shop_id;
  delete from public.shop_memberships where shop_id = deletion.target_shop_id;

  if private.shop_deletion_has_tenant_rows(deletion.target_shop_id) then
    raise exception using errcode = '23503',
      message = 'shop deletion dependency remains';
  end if;

  delete from private.login_credentials as credentials
  where credentials.user_id = any(exclusive_user_ids);

  delete from public.user_profiles as profile
  where profile.user_id = any(exclusive_user_ids);

  delete from public.shops as shop
  where shop.id = deletion.target_shop_id;
  if not found then
    raise exception using errcode = '23503', message = 'shop deletion failed';
  end if;

  deletion_summary := jsonb_build_object(
    'exclusive_user_count', cardinality(exclusive_user_ids),
    'managed_auth_user_count', jsonb_array_length(managed_users)
  );

  update private.shop_deletion_requests as request
  set status = 'complete',
      managed_auth_users = managed_users,
      auth_cleanup_pending = jsonb_array_length(managed_users) > 0,
      summary = deletion_summary,
      completed_at = now()
  where request.request_id = deletion.request_id
  returning * into deletion;

  insert into private.shop_deletion_audit_events (
    request_id, actor_user_id, deleted_shop_id, deleted_shop_slug,
    deleted_shop_display_name, reason, safe_summary
  ) values (
    deletion.request_id, deletion.actor_user_id, deletion.target_shop_id,
    deletion.target_slug, deletion.target_display_name, deletion.reason,
    deletion.summary
  );

  return query select
    deletion.target_shop_id,
    deletion.target_slug,
    deletion.target_display_name,
    deletion.managed_auth_users,
    deletion.auth_cleanup_pending;
end;
$$;

create or replace function public.shop_delete_mark_auth_cleanup(p_request_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if p_request_id is null then
    raise exception using errcode = '22023', message = 'invalid shop deletion request';
  end if;
  update private.shop_deletion_requests as request
  set auth_cleanup_pending = false
  where request.request_id = p_request_id and request.status = 'complete';
  if not found then
    raise exception using errcode = '42501', message = 'shop deletion denied';
  end if;
end;
$$;

alter table private.shop_deletion_requests enable row level security;
alter table private.shop_deletion_audit_events enable row level security;

revoke all on table private.shop_deletion_requests,
  private.shop_deletion_audit_events from public, anon, authenticated;
revoke all on function private.reject_shop_deletion_audit_mutation(),
  private.shop_deletion_schema_is_current(),
  private.shop_deletion_has_tenant_rows(uuid),
  public.shop_delete_prepare(uuid, uuid, uuid, text, text, text, timestamptz),
  public.shop_delete_fail(uuid, text),
  public.shop_delete_apply(uuid),
  public.shop_delete_mark_auth_cleanup(uuid)
  from public, anon, authenticated;

grant execute on function public.shop_delete_prepare(
  uuid, uuid, uuid, text, text, text, timestamptz
) to service_role;
grant execute on function public.shop_delete_fail(uuid, text) to service_role;
grant execute on function public.shop_delete_apply(uuid) to service_role;
grant execute on function public.shop_delete_mark_auth_cleanup(uuid) to service_role;

comment on table private.shop_deletion_requests is
  'Server-only idempotency, recovery, and managed-Auth-cleanup state for destructive shop deletion.';
comment on table private.shop_deletion_audit_events is
  'Immutable audit retained outside the deleted tenant graph; contains only safe identifiers, reason, and counts.';
comment on function public.shop_delete_apply(uuid) is
  'Atomically deletes one shop tenant graph and exclusive local identities after Super Admin reauthentication.';

commit;
