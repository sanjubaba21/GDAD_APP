begin;

create or replace function private.is_active_user()
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select exists (
        select 1
        from public.user_profiles profile
        where profile.user_id = (select auth.uid())
          and not profile.disabled
    );
$$;

revoke all on function private.is_active_user() from public, anon, authenticated;
grant execute on function private.is_active_user() to authenticated;

create or replace function private.can_view_user(target_user_id uuid)
returns boolean
language sql
stable
security definer
set search_path = ''
as $$
    select private.is_active_user()
       and (
            target_user_id = (select auth.uid())
            or private.is_super_admin()
            or exists (
                select 1
                from public.shop_memberships target_membership
                where target_membership.user_id = target_user_id
                  and target_membership.active
                  and private.has_shop_role(
                      target_membership.shop_id,
                      array['owner']::public.shop_role[]
                  )
            )
       );
$$;

drop policy if exists shop_memberships_select_authorized
    on public.shop_memberships;
create policy shop_memberships_select_authorized
on public.shop_memberships for select to authenticated
using (
    private.is_active_user()
    and (
        user_id = (select auth.uid())
        or private.is_super_admin()
        or private.has_shop_role(shop_id, array['owner']::public.shop_role[])
    )
);

comment on function private.is_active_user() is
    'Fail-closed RLS guard: stale sessions belonging to disabled profiles see no public rows.';

commit;
