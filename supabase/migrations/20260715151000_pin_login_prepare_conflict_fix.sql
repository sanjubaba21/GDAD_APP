begin;

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
    on conflict on constraint pin_login_rate_limits_pkey do update
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
            coalesce(source_state.blocked_until > request_time, false),
            false,
            null::uuid,
            null::text,
            null::text,
            null::smallint;
    end if;
end;
$$;

revoke all on function public.pin_login_prepare(text, text, timestamptz)
    from public, anon, authenticated;
grant execute on function public.pin_login_prepare(text, text, timestamptz)
    to service_role;

commit;
