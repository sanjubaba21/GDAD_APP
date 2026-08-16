\set ON_ERROR_STOP on

-- Privacy-safe production acceptance snapshot. This deliberately returns only aggregate
-- counts: never identifiers, login IDs, names, credentials, request payloads, or tokens.
select jsonb_build_object(
  'auth_users', (select count(*) from auth.users),
  'profiles', (select count(*) from public.user_profiles),
  'credentials', (select count(*) from private.login_credentials),
  'active_super_admins', (
    select count(*)
    from public.user_profiles
    where platform_role = 'super_admin' and not disabled
  ),
  'active_shops', (select count(*) from public.shops where active),
  'eligible_owner_authority_pairs', (
    select count(*)
    from public.user_profiles as actor
    cross join public.shops as shop
    where actor.platform_role = 'super_admin'
      and not actor.disabled
      and shop.active
  ),
  'eligible_salesman_authority_pairs', (
    select count(*)
    from public.user_profiles as actor
    join public.shop_memberships as membership on membership.user_id = actor.user_id
    join public.shops as shop on shop.id = membership.shop_id
    where actor.platform_role = 'standard'
      and not actor.disabled
      and membership.role = 'owner'
      and membership.active
      and shop.active
  ),
  'owner_memberships', (
    select count(*) from public.shop_memberships where role = 'owner'
  ),
  'active_owner_memberships', (
    select count(*) from public.shop_memberships where role = 'owner' and active
  ),
  'owner_requests_total', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_owner'
  ),
  'owner_requests_reserved', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_owner' and status = 'reserved'
  ),
  'owner_requests_failed', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_owner' and status = 'failed'
  ),
  'owner_requests_complete', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_owner' and status = 'complete'
  ),
  'owner_audits', (
    select count(*)
    from private.account_audit_events
    where action = 'account.create_owner'
  ),
  'salesman_memberships', (
    select count(*) from public.shop_memberships where role = 'salesman'
  ),
  'active_salesman_memberships', (
    select count(*) from public.shop_memberships where role = 'salesman' and active
  ),
  'salesman_requests_total', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_salesman'
  ),
  'salesman_requests_reserved', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_salesman' and status = 'reserved'
  ),
  'salesman_requests_failed', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_salesman' and status = 'failed'
  ),
  'salesman_requests_complete', (
    select count(*)
    from private.account_provisioning_requests
    where operation = 'create_salesman' and status = 'complete'
  ),
  'salesman_audits', (
    select count(*)
    from private.account_audit_events
    where action = 'account.create_salesman'
  )
);
