begin;

alter table public.shops
  add constraint shops_display_name_length
  check (length(trim(display_name)) between 1 and 120);

create table private.shop_creation_requests (
  request_id uuid primary key,
  actor_user_id uuid not null references auth.users(id) on delete restrict,
  request_fingerprint text not null,
  shop_id uuid not null unique,
  result jsonb,
  created_at timestamptz not null default now(),
  completed_at timestamptz,
  constraint shop_creation_fingerprint_valid check (request_fingerprint ~ '^[0-9a-f]{64}$'),
  constraint shop_creation_completion_consistent check (
    (completed_at is null and result is null)
    or (completed_at is not null and result is not null)
  )
);

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

alter table private.shop_creation_requests enable row level security;

revoke all on table private.shop_creation_requests from public, anon, authenticated;
revoke all on function public.create_shop(uuid, text, text) from public, anon, authenticated;
grant execute on function public.create_shop(uuid, text, text) to authenticated;

comment on table private.shop_creation_requests is
  'Server-only exactly-idempotent Super Admin shop creation state.';
comment on function public.create_shop(uuid, text, text) is
  'Active-Super-Admin-only shop creation with normalized fields, system accounts, and immutable safe audit.';

commit;
