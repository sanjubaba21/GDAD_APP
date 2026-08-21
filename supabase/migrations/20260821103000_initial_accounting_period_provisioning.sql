begin;

create or replace function private.ensure_shop_initial_accounting_period(target_shop_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  nepal_today date := (timezone('Asia/Kathmandu', now()))::date;
begin
  if target_shop_id is null then
    raise exception using errcode = '22023', message = 'shop is required';
  end if;

  perform 1
  from public.shops shop
  where shop.id = target_shop_id
  for update;
  if not found then
    raise exception using errcode = '23503', message = 'shop does not exist';
  end if;

  if exists(
    select 1
    from public.accounting_periods period
    where period.shop_id = target_shop_id
  ) then
    return;
  end if;

  insert into public.accounting_periods(shop_id, date_from, date_to)
  values(target_shop_id, nepal_today - 7, date '9999-12-31');
end;
$$;

create or replace function public.create_shop(
  p_request_id uuid,
  p_slug text,
  p_display_name text
)
returns jsonb
language plpgsql
security definer
set search_path = ''
as $$
declare
  actor uuid := (select auth.uid());
  normalized_slug text := lower(trim(p_slug));
  normalized_display_name text := trim(p_display_name);
  fingerprint text;
  target_shop_id uuid := extensions.gen_random_uuid();
  request private.shop_creation_requests%rowtype;
  shop_row public.shops%rowtype;
  safe_result jsonb;
begin
  if actor is null or not private.is_super_admin() then
    raise exception using errcode = '42501', message = 'not authorized';
  end if;
  if p_request_id is null
     or normalized_slug is null
     or normalized_slug !~ '^[a-z0-9][a-z0-9-]{2,62}$'
     or normalized_display_name is null
     or length(normalized_display_name) not between 1 and 120 then
    raise exception using errcode = '22023', message = 'invalid shop fields';
  end if;

  fingerprint := encode(
    extensions.digest(
      concat_ws(E'\x1f', normalized_slug, normalized_display_name),
      'sha256'
    ),
    'hex'
  );

  insert into private.shop_creation_requests(
    request_id, actor_user_id, request_fingerprint, shop_id
  ) values (
    p_request_id, actor, fingerprint, target_shop_id
  ) on conflict do nothing;

  select * into request
  from private.shop_creation_requests
  where request_id = p_request_id
  for update;

  if request.actor_user_id <> actor
     or request.request_fingerprint <> fingerprint then
    raise exception using errcode = '22023', message = 'idempotency key payload mismatch';
  end if;
  if request.completed_at is not null then
    return request.result;
  end if;
  target_shop_id := request.shop_id;

  insert into public.shops(id, slug, display_name)
  values(target_shop_id, normalized_slug, normalized_display_name)
  returning * into shop_row;

  perform private.ensure_shop_financial_accounts(target_shop_id);
  perform private.ensure_shop_initial_accounting_period(target_shop_id);

  safe_result := jsonb_build_object(
    'id', shop_row.id,
    'slug', shop_row.slug,
    'display_name', shop_row.display_name,
    'active', shop_row.active
  );

  insert into private.business_audit_events(
    shop_id, actor_user_id, operation, record_type, record_id,
    before_metadata, after_metadata, idempotency_key
  ) values (
    target_shop_id, actor, 'create', 'shop', target_shop_id,
    '{}'::jsonb,
    jsonb_build_object(
      'slug', shop_row.slug,
      'display_name', shop_row.display_name,
      'active', shop_row.active
    ),
    'shop:create:' || p_request_id::text
  );

  update private.shop_creation_requests
  set result = safe_result, completed_at = now()
  where request_id = p_request_id
  returning result into safe_result;

  return safe_result;
end;
$$;

do $$
declare
  target record;
begin
  for target in
    select shop.id
    from public.shops shop
    where not exists(
      select 1
      from public.accounting_periods period
      where period.shop_id = shop.id
    )
    order by shop.id
  loop
    perform private.ensure_shop_initial_accounting_period(target.id);
  end loop;
end;
$$;

revoke all on function private.ensure_shop_initial_accounting_period(uuid)
  from public, anon, authenticated;

comment on function private.ensure_shop_initial_accounting_period(uuid) is
  'Creates the first open operating period only when a shop has no accounting-period history.';
comment on function public.create_shop(uuid, text, text) is
  'Active-Super-Admin-only shop creation with system accounts, an initial open accounting period, and immutable safe audit.';

commit;
