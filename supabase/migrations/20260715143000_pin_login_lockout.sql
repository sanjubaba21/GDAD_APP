begin;

alter table private.login_credentials
    add column last_success_at timestamptz;

create table private.pin_login_rate_limits (
    source_fingerprint text primary key,
    window_started_at timestamptz not null,
    attempt_count integer not null default 0,
    blocked_until timestamptz,
    updated_at timestamptz not null default now(),
    constraint pin_login_rate_limits_fingerprint_format check (
        source_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    constraint pin_login_rate_limits_attempt_count_nonnegative check (
        attempt_count >= 0
    )
);

comment on table private.pin_login_rate_limits is
    'Server-only rolling source counters. Fingerprints are HMACs; raw network identifiers are never stored.';

create trigger pin_login_rate_limits_set_updated_at
before update on private.pin_login_rate_limits
for each row execute function public.set_updated_at();

create or replace function public.pin_login_prepare(
    normalized_login_id text,
    source_fingerprint text,
    request_time timestamptz default now()
)
returns table (
    source_limited boolean,
    account_locked boolean,
    user_id uuid,
    auth_email text,
    pin_hash text,
    pepper_version smallint
)
language plpgsql
security definer
set search_path = ''
as $$
declare
    source_state private.pin_login_rate_limits%rowtype;
begin
    if normalized_login_id is null
       or normalized_login_id !~ '^[a-z0-9][a-z0-9._-]{2,63}$'
       or source_fingerprint is null
       or source_fingerprint !~ '^[0-9a-f]{64}$'
       or request_time is null then
        raise exception using errcode = '22023', message = 'invalid login preparation input';
    end if;

    insert into private.pin_login_rate_limits as limits (
        source_fingerprint,
        window_started_at,
        attempt_count,
        blocked_until
    ) values (
        source_fingerprint,
        request_time,
        1,
        null
    )
    on conflict (source_fingerprint) do update
    set window_started_at = case
            when limits.window_started_at <= request_time - interval '15 minutes'
                then request_time
            else limits.window_started_at
        end,
        attempt_count = case
            when limits.window_started_at <= request_time - interval '15 minutes'
                then 1
            else limits.attempt_count + 1
        end,
        blocked_until = case
            when limits.blocked_until > request_time then limits.blocked_until
            when limits.window_started_at <= request_time - interval '15 minutes' then null
            when limits.attempt_count + 1 > 20 then request_time + interval '15 minutes'
            else null
        end
    returning * into source_state;

    return query
    select
        source_state.blocked_until > request_time,
        credentials.locked_until > request_time,
        profile.user_id,
        auth_user.email::text,
        credentials.pin_hash,
        credentials.pepper_version
    from public.user_profiles as profile
    join auth.users as auth_user on auth_user.id = profile.user_id
    join private.login_credentials as credentials on credentials.user_id = profile.user_id
    where profile.login_id = normalized_login_id
      and not profile.disabled
      and (
          profile.platform_role = 'super_admin'
          or exists (
              select 1
              from public.shop_memberships as membership
              join public.shops as shop on shop.id = membership.shop_id
              where membership.user_id = profile.user_id
                and membership.active
                and shop.active
          )
      );

    if not found then
        return query select
            source_state.blocked_until > request_time,
            false,
            null::uuid,
            null::text,
            null::text,
            null::smallint;
    end if;
end;
$$;

create or replace function public.pin_login_complete(
    target_user_id uuid,
    was_successful boolean,
    request_time timestamptz default now()
)
returns table (
    failed_attempts integer,
    locked_until timestamptz
)
language plpgsql
security definer
set search_path = ''
as $$
begin
    if was_successful is null or request_time is null then
        raise exception using errcode = '22023', message = 'invalid login completion input';
    end if;

    if target_user_id is null then
        return query select null::integer, null::timestamptz;
        return;
    end if;

    if was_successful then
        return query
        update private.login_credentials as credentials
        set failed_attempts = 0,
            locked_until = null,
            last_success_at = request_time
        where credentials.user_id = target_user_id
        returning credentials.failed_attempts, credentials.locked_until;
    else
        return query
        update private.login_credentials as credentials
        set failed_attempts = credentials.failed_attempts + 1,
            last_failed_at = request_time,
            locked_until = case
                when credentials.failed_attempts + 1 >= 5
                    then greatest(
                        coalesce(credentials.locked_until, request_time),
                        request_time + interval '15 minutes'
                    )
                else credentials.locked_until
            end
        where credentials.user_id = target_user_id
        returning credentials.failed_attempts, credentials.locked_until;
    end if;
end;
$$;

revoke all on table private.pin_login_rate_limits from public, anon, authenticated;
revoke all on function public.pin_login_prepare(text, text, timestamptz)
    from public, anon, authenticated;
revoke all on function public.pin_login_complete(uuid, boolean, timestamptz)
    from public, anon, authenticated;

grant execute on function public.pin_login_prepare(text, text, timestamptz)
    to service_role;
grant execute on function public.pin_login_complete(uuid, boolean, timestamptz)
    to service_role;

alter table private.pin_login_rate_limits enable row level security;

commit;
