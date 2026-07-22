begin;

alter table private.account_provisioning_requests
    drop constraint account_provisioning_auth_id_deterministic;

create or replace function private.managed_auth_email(provisioning_request_id uuid)
returns text
language sql
immutable
strict
set search_path = ''
as $$
    select 'acct.' || replace(provisioning_request_id::text, '-', '')
        || '@auth.gdad.invalid';
$$;

comment on function private.managed_auth_email(uuid) is
    'Deterministic internal email used to reconcile Auth-generated provisioning subjects.';

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
    recovered_auth_user_id uuid;
    expected_email text;
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

    expected_email := private.managed_auth_email(p_request_id);

    if existing_request.request_id is not null then
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

        select auth_user.id into recovered_auth_user_id
        from auth.users as auth_user
        where auth_user.email = expected_email
          and auth_user.raw_app_meta_data ->> 'managed_by' = 'gdad_pin_v1'
          and auth_user.raw_app_meta_data ->> 'provisioning_request_id' = p_request_id::text;

        if recovered_auth_user_id is null and exists (
            select 1 from auth.users as auth_user
            where auth_user.email = expected_email
        ) then
            raise exception using errcode = '23505', message = 'managed Auth email unavailable';
        end if;

        if recovered_auth_user_id is not null then
            if existing_request.auth_user_id <> p_request_id
               and existing_request.auth_user_id <> recovered_auth_user_id then
                raise exception using errcode = '23505', message = 'managed Auth subject mismatch';
            end if;
            update private.account_provisioning_requests as request
            set auth_user_id = recovered_auth_user_id
            where request.request_id = p_request_id;
            existing_request.auth_user_id := recovered_auth_user_id;
        elsif existing_request.status = 'failed' then
            update private.account_provisioning_requests as request
            set auth_user_id = p_request_id
            where request.request_id = p_request_id;
            existing_request.auth_user_id := p_request_id;
        end if;

        if existing_request.status = 'failed' then
            update private.account_provisioning_requests as request
            set status = 'reserved', failure_code = null
            where request.request_id = p_request_id;
            existing_request.status := 'reserved';
        end if;

        return query select existing_request.status,
            case
                when recovered_auth_user_id is not null
                     or existing_request.status = 'complete'
                    then existing_request.auth_user_id
                else null::uuid
            end;
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
    if exists (
        select 1 from auth.users as auth_user
        where auth_user.email = expected_email
    ) then
        raise exception using errcode = '23505', message = 'managed Auth email unavailable';
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

    return query select 'reserved'::text, null::uuid;
end;
$$;

create or replace function public.account_provision_attach_auth(
    p_request_id uuid,
    p_auth_user_id uuid
)
returns uuid
language plpgsql
security definer
set search_path = ''
as $$
declare
    provisioning private.account_provisioning_requests%rowtype;
    expected_email text;
begin
    if p_request_id is null or p_auth_user_id is null then
        raise exception using errcode = '22023', message = 'invalid Auth attachment';
    end if;

    select * into provisioning
    from private.account_provisioning_requests as request
    where request.request_id = p_request_id
    for update;

    if not found or provisioning.status <> 'reserved' then
        raise exception using errcode = '22023', message = 'provisioning request unavailable';
    end if;

    expected_email := private.managed_auth_email(p_request_id);
    if not exists (
        select 1 from auth.users as auth_user
        where auth_user.id = p_auth_user_id
          and auth_user.email = expected_email
          and auth_user.raw_app_meta_data ->> 'managed_by' = 'gdad_pin_v1'
          and auth_user.raw_app_meta_data ->> 'provisioning_request_id' = p_request_id::text
    ) then
        raise exception using errcode = '23503', message = 'managed Auth user unavailable';
    end if;

    if provisioning.auth_user_id <> provisioning.request_id
       and provisioning.auth_user_id <> p_auth_user_id then
        raise exception using errcode = '23505', message = 'managed Auth subject mismatch';
    end if;
    if exists (
        select 1 from private.account_provisioning_requests as other_request
        where other_request.auth_user_id = p_auth_user_id
          and other_request.request_id <> p_request_id
    ) then
        raise exception using errcode = '23505', message = 'managed Auth subject unavailable';
    end if;

    update private.account_provisioning_requests as request
    set auth_user_id = p_auth_user_id
    where request.request_id = p_request_id;

    return p_auth_user_id;
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

    expected_email := private.managed_auth_email(provisioning.request_id);
    if provisioning.auth_user_id = provisioning.request_id
       or not exists (
           select 1
           from auth.users as auth_user
           where auth_user.id = provisioning.auth_user_id
             and auth_user.email = expected_email
             and auth_user.raw_app_meta_data ->> 'managed_by' = 'gdad_pin_v1'
             and auth_user.raw_app_meta_data ->> 'provisioning_request_id' = provisioning.request_id::text
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

revoke all on function private.managed_auth_email(uuid)
    from public, anon, authenticated;
revoke all on function public.account_provision_attach_auth(uuid, uuid)
    from public, anon, authenticated;
grant execute on function public.account_provision_attach_auth(uuid, uuid)
    to service_role;

comment on function public.account_provision_attach_auth(uuid, uuid) is
    'Attaches a hosted Auth-generated UUID to an exact managed provisioning reservation.';

commit;
