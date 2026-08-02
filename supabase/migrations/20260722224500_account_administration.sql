begin;

alter table private.account_audit_events
    drop constraint account_audit_events_request_id_fkey;
alter table private.account_audit_events
    drop constraint account_audit_action_valid;
alter table private.account_audit_events
    add constraint account_audit_action_valid check (
        action in (
            'account.bootstrap_super_admin',
            'account.create_owner',
            'account.create_salesman',
            'account.disable_user',
            'account.enable_user',
            'account.reset_pin'
        )
    );

create table private.account_admin_requests (
    request_id uuid primary key,
    action text not null check (
        action in ('disable_user', 'enable_user', 'reset_pin')
    ),
    actor_user_id uuid not null references public.user_profiles(user_id) on delete restrict,
    target_user_id uuid not null references public.user_profiles(user_id) on delete restrict,
    shop_id uuid references public.shops(id) on delete restrict,
    status text not null default 'reserved' check (
        status in ('reserved', 'complete', 'failed')
    ),
    result_disabled boolean,
    failure_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint account_admin_not_self check (actor_user_id <> target_user_id),
    constraint account_admin_completion_shape check (
        (status = 'reserved' and completed_at is null and failure_code is null)
        or (status = 'complete' and completed_at is not null and failure_code is null
            and result_disabled is not null)
        or (status = 'failed' and completed_at is null and failure_code is not null)
    )
);

create table private.account_admin_rate_limits (
    rate_key text primary key,
    window_started_at timestamptz not null,
    attempt_count integer not null default 0 check (attempt_count >= 0),
    updated_at timestamptz not null default now()
);

create trigger account_admin_requests_set_updated_at
before update on private.account_admin_requests
for each row execute function public.set_updated_at();

create or replace function private.account_admin_actor_shop(
    p_actor_user_id uuid,
    p_target_user_id uuid
)
returns uuid
language sql
stable
security definer
set search_path = ''
as $$
    select case
        when actor.platform_role = 'super_admin'
             and not actor.disabled
             and target.platform_role = 'standard'
             and not exists (
                 select 1 from public.shop_memberships as forbidden
                 where forbidden.user_id = target.user_id
                   and forbidden.active
                   and forbidden.role <> 'owner'
             )
        then (
            select membership.shop_id
            from public.shop_memberships as membership
            join public.shops as shop on shop.id = membership.shop_id
            where membership.user_id = target.user_id
              and membership.role = 'owner'
              and membership.active and shop.active
            order by membership.shop_id
            limit 1
        )
        when actor.platform_role = 'standard'
             and not actor.disabled
             and target.platform_role = 'standard'
        then (
            select owner_membership.shop_id
            from public.shop_memberships as owner_membership
            join public.shop_memberships as target_membership
              on target_membership.shop_id = owner_membership.shop_id
            join public.shops as shop on shop.id = owner_membership.shop_id
            where owner_membership.user_id = actor.user_id
              and owner_membership.role = 'owner'
              and owner_membership.active
              and target_membership.user_id = target.user_id
              and target_membership.role = 'salesman'
              and target_membership.active
              and shop.active
            order by owner_membership.shop_id
            limit 1
        )
        else null::uuid
    end
    from public.user_profiles as actor
    join public.user_profiles as target on target.user_id = p_target_user_id
    where actor.user_id = p_actor_user_id
      and actor.user_id <> target.user_id
      and target.platform_role <> 'super_admin';
$$;

create or replace function private.account_admin_consume_rate(
    p_rate_key text,
    p_request_time timestamptz,
    p_limit integer
)
returns boolean
language plpgsql
security definer
set search_path = ''
as $$
declare
    resulting_count integer;
begin
    insert into private.account_admin_rate_limits (
        rate_key, window_started_at, attempt_count
    ) values (p_rate_key, p_request_time, 1)
    on conflict (rate_key) do update
    set window_started_at = case
            when private.account_admin_rate_limits.window_started_at
                 <= p_request_time - interval '15 minutes'
                then p_request_time
            else private.account_admin_rate_limits.window_started_at
        end,
        attempt_count = case
            when private.account_admin_rate_limits.window_started_at
                 <= p_request_time - interval '15 minutes'
                then 1
            else private.account_admin_rate_limits.attempt_count + 1
        end,
        updated_at = p_request_time
    returning attempt_count into resulting_count;
    return resulting_count > p_limit;
end;
$$;

create or replace function public.account_admin_prepare(
    p_request_id uuid,
    p_action text,
    p_actor_user_id uuid,
    p_target_user_id uuid,
    p_source_fingerprint text,
    p_request_time timestamptz
)
returns table (
    reservation_status text,
    actor_pin_hash text,
    actor_pepper_version smallint,
    target_disabled boolean,
    resulting_disabled boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    existing_request private.account_admin_requests%rowtype;
    has_existing boolean := false;
    authorized_shop_id uuid;
    source_limited boolean;
    actor_limited boolean;
begin
    if p_request_id is null
       or p_action not in ('disable_user', 'enable_user', 'reset_pin')
       or p_actor_user_id is null or p_target_user_id is null
       or p_actor_user_id = p_target_user_id
       or p_source_fingerprint !~ '^[0-9a-f]{64}$'
       or p_request_time is null then
        raise exception using errcode = '22023', message = 'invalid administration input';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('gdad-account-admin:' || p_request_id::text, 0)
    );
    select * into existing_request
    from private.account_admin_requests as request
    where request.request_id = p_request_id;
    has_existing := found;

    if has_existing then
        if existing_request.action <> p_action
           or existing_request.actor_user_id <> p_actor_user_id
           or existing_request.target_user_id <> p_target_user_id then
            raise exception using errcode = '22023', message = 'administration request mismatch';
        end if;
        if existing_request.status = 'complete' then
            return query select 'complete'::text, null::text, null::smallint,
                existing_request.result_disabled, existing_request.result_disabled;
            return;
        end if;
        if existing_request.status = 'failed' then
            raise exception using errcode = '42501', message = 'administration denied';
        end if;
    end if;

    authorized_shop_id := private.account_admin_actor_shop(
        p_actor_user_id, p_target_user_id
    );
    if authorized_shop_id is null then
        raise exception using errcode = '42501', message = 'administration denied';
    end if;

    source_limited := private.account_admin_consume_rate(
        'source:' || p_source_fingerprint, p_request_time, 20
    );
    actor_limited := private.account_admin_consume_rate(
        'actor:' || p_actor_user_id::text, p_request_time, 10
    );
    if source_limited or actor_limited then
        raise exception using errcode = '42501', message = 'administration denied';
    end if;

    if not has_existing then
        insert into private.account_admin_requests (
            request_id, action, actor_user_id, target_user_id, shop_id
        ) values (
            p_request_id, p_action, p_actor_user_id, p_target_user_id,
            authorized_shop_id
        );
    end if;

    return query
    select 'reserved'::text, credentials.pin_hash, credentials.pepper_version,
        target.disabled,
        case p_action
            when 'disable_user' then true
            when 'enable_user' then false
            else target.disabled
        end
    from private.login_credentials as credentials
    join public.user_profiles as target on target.user_id = p_target_user_id
    where credentials.user_id = p_actor_user_id;
end;
$$;

create or replace function public.account_admin_fail(
    p_request_id uuid,
    p_failure_code text
)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
    if p_failure_code is null or p_failure_code !~ '^[A-Z0-9_]{3,64}$' then
        raise exception using errcode = '22023', message = 'invalid failure code';
    end if;
    update private.account_admin_requests as request
    set status = 'failed', failure_code = p_failure_code
    where request.request_id = p_request_id and request.status = 'reserved';
end;
$$;

create or replace function public.account_admin_apply(
    p_request_id uuid,
    p_pin_hash text
)
returns table (
    target_user_id uuid,
    action text,
    disabled boolean
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    administration private.account_admin_requests%rowtype;
    authorized_shop_id uuid;
    resulting_disabled boolean;
begin
    select * into administration
    from private.account_admin_requests as request
    where request.request_id = p_request_id
    for update;
    if not found or administration.status = 'failed' then
        raise exception using errcode = '42501', message = 'administration denied';
    end if;
    if administration.status = 'complete' then
        return query select administration.target_user_id, administration.action,
            administration.result_disabled;
        return;
    end if;

    authorized_shop_id := private.account_admin_actor_shop(
        administration.actor_user_id, administration.target_user_id
    );
    if authorized_shop_id is null or authorized_shop_id <> administration.shop_id then
        raise exception using errcode = '42501', message = 'administration denied';
    end if;

    if administration.action = 'reset_pin' then
        if p_pin_hash is null
           or p_pin_hash !~ '^\$argon2id\$v=19\$m=19456,t=2,p=1\$' then
            raise exception using errcode = '22023', message = 'invalid PIN verifier';
        end if;
        update private.login_credentials as credentials
        set pin_hash = p_pin_hash,
            pepper_version = 1,
            failed_attempts = 0,
            locked_until = null,
            last_failed_at = null,
            pin_changed_at = now()
        where credentials.user_id = administration.target_user_id;
    elsif p_pin_hash is not null then
        raise exception using errcode = '22023', message = 'unexpected PIN verifier';
    end if;

    resulting_disabled := case administration.action
        when 'disable_user' then true
        when 'enable_user' then false
        else (select profile.disabled from public.user_profiles as profile
              where profile.user_id = administration.target_user_id)
    end;
    if administration.action in ('disable_user', 'enable_user') then
        update public.user_profiles as profile
        set disabled = resulting_disabled
        where profile.user_id = administration.target_user_id;
    end if;

    delete from auth.sessions as session
    where session.user_id = administration.target_user_id;

    update private.account_admin_requests as request
    set status = 'complete', result_disabled = resulting_disabled,
        completed_at = now()
    where request.request_id = administration.request_id;

    insert into private.account_audit_events (
        request_id, actor_user_id, shop_id, action, target_user_id, safe_metadata
    ) values (
        administration.request_id, administration.actor_user_id,
        administration.shop_id, 'account.' || administration.action,
        administration.target_user_id,
        jsonb_build_object('disabled', resulting_disabled)
    );

    return query select administration.target_user_id, administration.action,
        resulting_disabled;
end;
$$;

revoke all on table private.account_admin_requests,
    private.account_admin_rate_limits from public, anon, authenticated;
revoke all on function private.account_admin_actor_shop(uuid, uuid),
    private.account_admin_consume_rate(text, timestamptz, integer),
    public.account_admin_prepare(uuid, text, uuid, uuid, text, timestamptz),
    public.account_admin_fail(uuid, text),
    public.account_admin_apply(uuid, text)
    from public, anon, authenticated;
grant execute on function public.account_admin_prepare(
    uuid, text, uuid, uuid, text, timestamptz
) to service_role;
grant execute on function public.account_admin_fail(uuid, text) to service_role;
grant execute on function public.account_admin_apply(uuid, text) to service_role;

alter table private.account_admin_requests enable row level security;
alter table private.account_admin_rate_limits enable row level security;

comment on table private.account_admin_requests is
    'Server-only idempotency state for managed account administration.';
comment on table private.account_admin_rate_limits is
    'Atomic per-actor and per-source reauthentication attempt windows.';
comment on function public.account_admin_apply(uuid, text) is
    'Atomically applies an authorized account action, revokes refresh sessions, and audits it.';

commit;
