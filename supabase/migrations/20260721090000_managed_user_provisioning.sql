begin;

create table private.account_provisioning_requests (
    request_id uuid primary key,
    operation text not null,
    actor_user_id uuid,
    login_id text not null,
    display_name text not null,
    shop_id uuid,
    auth_user_id uuid not null unique,
    status text not null default 'reserved',
    failure_code text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    completed_at timestamptz,
    constraint account_provisioning_operation_valid check (
        operation in ('bootstrap_super_admin', 'create_owner', 'create_salesman')
    ),
    constraint account_provisioning_status_valid check (
        status in ('reserved', 'complete', 'failed')
    ),
    constraint account_provisioning_login_id_format check (
        login_id = lower(trim(login_id))
        and login_id ~ '^[a-z0-9][a-z0-9._-]{2,63}$'
    ),
    constraint account_provisioning_display_name_not_blank check (
        length(trim(display_name)) between 1 and 120
    ),
    constraint account_provisioning_auth_id_deterministic check (
        auth_user_id = request_id
    ),
    constraint account_provisioning_operation_shape check (
        (operation = 'bootstrap_super_admin' and actor_user_id is null and shop_id is null)
        or
        (operation in ('create_owner', 'create_salesman')
            and actor_user_id is not null and shop_id is not null)
    ),
    constraint account_provisioning_completion_shape check (
        (status = 'complete' and completed_at is not null and failure_code is null)
        or (status = 'failed' and completed_at is null and failure_code is not null)
        or (status = 'reserved' and completed_at is null and failure_code is null)
    )
);

create unique index account_provisioning_active_login_unique
    on private.account_provisioning_requests (login_id)
    where status in ('reserved', 'complete');

comment on table private.account_provisioning_requests is
    'Server-only idempotency and compensation state for managed Auth user creation.';

create table private.account_audit_events (
    id uuid primary key default gen_random_uuid(),
    request_id uuid not null unique
        references private.account_provisioning_requests(request_id) on delete restrict,
    actor_user_id uuid,
    shop_id uuid,
    action text not null,
    target_user_id uuid not null,
    safe_metadata jsonb not null default '{}'::jsonb,
    created_at timestamptz not null default now(),
    constraint account_audit_action_valid check (
        action in (
            'account.bootstrap_super_admin',
            'account.create_owner',
            'account.create_salesman'
        )
    ),
    constraint account_audit_metadata_object check (
        jsonb_typeof(safe_metadata) = 'object'
    )
);

comment on table private.account_audit_events is
    'Append-only account administration audit. PINs, verifiers, tokens, and secrets are forbidden.';

create trigger account_provisioning_requests_set_updated_at
before update on private.account_provisioning_requests
for each row execute function public.set_updated_at();

create or replace function private.account_provision_actor_allowed(
    target_operation text,
    actor_id uuid,
    target_shop_id uuid
)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select case target_operation
        when 'create_owner' then exists (
            select 1
            from public.user_profiles as actor
            join public.shops as shop on shop.id = target_shop_id
            where actor.user_id = actor_id
              and actor.platform_role = 'super_admin'
              and not actor.disabled
              and shop.active
        )
        when 'create_salesman' then exists (
            select 1
            from public.user_profiles as actor
            join public.shop_memberships as membership
              on membership.user_id = actor.user_id
             and membership.shop_id = target_shop_id
            join public.shops as shop on shop.id = membership.shop_id
            where actor.user_id = actor_id
              and actor.platform_role = 'standard'
              and not actor.disabled
              and membership.role = 'owner'
              and membership.active
              and shop.active
        )
        else false
    end;
$$;

create or replace function public.account_provision_start(
    p_request_id uuid,
    p_operation text,
    p_actor_user_id uuid,
    p_login_id text,
    p_display_name text,
    p_shop_id uuid
)
returns table (
    reservation_status text,
    auth_user_id uuid
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    existing_request private.account_provisioning_requests%rowtype;
begin
    if p_request_id is null
       or p_operation not in ('bootstrap_super_admin', 'create_owner', 'create_salesman')
       or p_login_id is null
       or p_login_id <> lower(trim(p_login_id))
       or p_login_id !~ '^[a-z0-9][a-z0-9._-]{2,63}$'
       or p_display_name is null
       or length(trim(p_display_name)) not between 1 and 120 then
        raise exception using errcode = '22023', message = 'invalid provisioning input';
    end if;

    if (p_operation = 'bootstrap_super_admin'
            and (p_actor_user_id is not null or p_shop_id is not null))
       or (p_operation in ('create_owner', 'create_salesman')
            and (p_actor_user_id is null or p_shop_id is null)) then
        raise exception using errcode = '22023', message = 'invalid provisioning shape';
    end if;

    perform pg_catalog.pg_advisory_xact_lock(
        pg_catalog.hashtextextended('gdad-provision:' || p_request_id::text, 0)
    );
    if p_operation = 'bootstrap_super_admin' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('gdad-bootstrap-super-admin', 0)
        );
    end if;

    select * into existing_request
    from private.account_provisioning_requests as request
    where request.request_id = p_request_id;

    if found then
        if existing_request.operation <> p_operation
           or existing_request.actor_user_id is distinct from p_actor_user_id
           or existing_request.login_id <> p_login_id
           or existing_request.display_name <> trim(p_display_name)
           or existing_request.shop_id is distinct from p_shop_id then
            raise exception using errcode = '22023', message = 'provisioning request mismatch';
        end if;

        if p_operation <> 'bootstrap_super_admin'
           and not private.account_provision_actor_allowed(
               p_operation, p_actor_user_id, p_shop_id
           ) then
            raise exception using errcode = '42501', message = 'provisioning denied';
        end if;

        if existing_request.status = 'failed' then
            update private.account_provisioning_requests as request
            set status = 'reserved', failure_code = null
            where request.request_id = p_request_id;
            existing_request.status := 'reserved';
        end if;

        return query select existing_request.status, existing_request.auth_user_id;
        return;
    end if;

    if exists (
        select 1 from public.user_profiles as profile
        where profile.login_id = p_login_id
    ) then
        raise exception using errcode = '23505', message = 'login ID unavailable';
    end if;
    if exists (
        select 1 from auth.users as auth_user
        where auth_user.id = p_request_id
    ) then
        raise exception using errcode = '23505', message = 'Auth subject unavailable';
    end if;

    if p_operation = 'bootstrap_super_admin' then
        if exists (select 1 from public.user_profiles) then
            raise exception using errcode = '42501', message = 'bootstrap unavailable';
        end if;
    elsif not private.account_provision_actor_allowed(
        p_operation, p_actor_user_id, p_shop_id
    ) then
        raise exception using errcode = '42501', message = 'provisioning denied';
    end if;

    insert into private.account_provisioning_requests (
        request_id, operation, actor_user_id, login_id, display_name,
        shop_id, auth_user_id
    ) values (
        p_request_id, p_operation, p_actor_user_id, p_login_id,
        trim(p_display_name), p_shop_id, p_request_id
    );

    return query select 'reserved'::text, p_request_id;
end;
$$;

create or replace function public.account_provision_finalize(
    p_request_id uuid,
    p_pin_hash text
)
returns table (
    auth_user_id uuid,
    login_id text,
    platform_role public.platform_role,
    shop_id uuid,
    shop_role public.shop_role
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    provisioning private.account_provisioning_requests%rowtype;
    expected_email text;
    resulting_platform_role public.platform_role;
    resulting_shop_role public.shop_role;
begin
    if p_request_id is null
       or p_pin_hash is null
       or p_pin_hash !~ '^\$argon2id\$v=19\$m=19456,t=2,p=1\$' then
        raise exception using errcode = '22023', message = 'invalid provisioning finalization';
    end if;

    select * into provisioning
    from private.account_provisioning_requests as request
    where request.request_id = p_request_id
    for update;

    if not found or provisioning.status = 'failed' then
        raise exception using errcode = '22023', message = 'provisioning request unavailable';
    end if;

    resulting_platform_role := case
        when provisioning.operation = 'bootstrap_super_admin'
            then 'super_admin'::public.platform_role
        else 'standard'::public.platform_role
    end;
    resulting_shop_role := case provisioning.operation
        when 'create_owner' then 'owner'::public.shop_role
        when 'create_salesman' then 'salesman'::public.shop_role
        else null
    end;

    if provisioning.status = 'complete' then
        return query select provisioning.auth_user_id, provisioning.login_id,
            resulting_platform_role, provisioning.shop_id, resulting_shop_role;
        return;
    end if;

    expected_email := 'acct.' || replace(provisioning.request_id::text, '-', '')
        || '@auth.gdad.invalid';
    if not exists (
        select 1
        from auth.users as auth_user
        where auth_user.id = provisioning.auth_user_id
          and auth_user.email = expected_email
          and auth_user.raw_app_meta_data ->> 'managed_by' = 'gdad_pin_v1'
    ) then
        raise exception using errcode = '23503', message = 'managed Auth user unavailable';
    end if;

    if provisioning.operation = 'bootstrap_super_admin' then
        perform pg_catalog.pg_advisory_xact_lock(
            pg_catalog.hashtextextended('gdad-bootstrap-super-admin', 0)
        );
        if exists (select 1 from public.user_profiles) then
            raise exception using errcode = '42501', message = 'bootstrap unavailable';
        end if;
    elsif not private.account_provision_actor_allowed(
        provisioning.operation, provisioning.actor_user_id, provisioning.shop_id
    ) then
        raise exception using errcode = '42501', message = 'provisioning denied';
    end if;

    insert into public.user_profiles (
        user_id, login_id, display_name, platform_role
    ) values (
        provisioning.auth_user_id, provisioning.login_id,
        provisioning.display_name, resulting_platform_role
    );

    insert into private.login_credentials (user_id, pin_hash, pepper_version)
    values (provisioning.auth_user_id, p_pin_hash, 1);

    if resulting_shop_role is not null then
        insert into public.shop_memberships (shop_id, user_id, role)
        values (provisioning.shop_id, provisioning.auth_user_id, resulting_shop_role);
    end if;

    update private.account_provisioning_requests as request
    set status = 'complete', completed_at = now()
    where request.request_id = p_request_id;

    insert into private.account_audit_events (
        request_id, actor_user_id, shop_id, action, target_user_id, safe_metadata
    ) values (
        provisioning.request_id,
        provisioning.actor_user_id,
        provisioning.shop_id,
        case provisioning.operation
            when 'bootstrap_super_admin' then 'account.bootstrap_super_admin'
            when 'create_owner' then 'account.create_owner'
            else 'account.create_salesman'
        end,
        provisioning.auth_user_id,
        '{}'::jsonb
    );

    return query select provisioning.auth_user_id, provisioning.login_id,
        resulting_platform_role, provisioning.shop_id, resulting_shop_role;
end;
$$;

create or replace function public.account_provision_fail(
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
       or p_failure_code !~ '^[A-Z0-9_]{3,40}$' then
        raise exception using errcode = '22023', message = 'invalid provisioning failure';
    end if;

    update private.account_provisioning_requests as request
    set status = 'failed', failure_code = p_failure_code
    where request.request_id = p_request_id
      and request.status = 'reserved';
end;
$$;

revoke all on table private.account_provisioning_requests
    from public, anon, authenticated;
revoke all on table private.account_audit_events
    from public, anon, authenticated;
revoke all on function private.account_provision_actor_allowed(text, uuid, uuid)
    from public, anon, authenticated;
revoke all on function public.account_provision_start(uuid, text, uuid, text, text, uuid)
    from public, anon, authenticated;
revoke all on function public.account_provision_finalize(uuid, text)
    from public, anon, authenticated;
revoke all on function public.account_provision_fail(uuid, text)
    from public, anon, authenticated;

grant execute on function public.account_provision_start(uuid, text, uuid, text, text, uuid)
    to service_role;
grant execute on function public.account_provision_finalize(uuid, text)
    to service_role;
grant execute on function public.account_provision_fail(uuid, text)
    to service_role;

alter table private.account_provisioning_requests enable row level security;
alter table private.account_audit_events enable row level security;

commit;
