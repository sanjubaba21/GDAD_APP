begin;

create extension if not exists pgtap with schema extensions;
set local search_path = public, extensions;

select plan(7);

select is(
  (
    select count(*)
    from pg_catalog.pg_class as relation
    join pg_catalog.pg_namespace as namespace on namespace.oid = relation.relnamespace
    where namespace.nspname in ('public', 'private')
      and relation.relkind in ('r', 'p')
      and not relation.relrowsecurity
  ),
  0::bigint,
  'every public/private application table has RLS enabled'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_class as relation
    join pg_catalog.pg_namespace as namespace on namespace.oid = relation.relnamespace
    where namespace.nspname in ('public', 'private')
      and relation.relkind in ('r', 'p', 'v', 'm')
      and pg_catalog.has_table_privilege('anon', relation.oid, 'SELECT')
  ),
  0::bigint,
  'anonymous clients cannot read any application table or view'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_class as relation
    join pg_catalog.pg_namespace as namespace on namespace.oid = relation.relnamespace
    where namespace.nspname = 'public'
      and relation.relkind in ('r', 'p')
      and (
        pg_catalog.has_table_privilege('authenticated', relation.oid, 'INSERT')
        or pg_catalog.has_table_privilege('authenticated', relation.oid, 'UPDATE')
        or pg_catalog.has_table_privilege('authenticated', relation.oid, 'DELETE')
        or pg_catalog.has_table_privilege('authenticated', relation.oid, 'TRUNCATE')
      )
  ),
  0::bigint,
  'authenticated clients cannot directly mutate public application tables'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_class as relation
    join pg_catalog.pg_namespace as namespace on namespace.oid = relation.relnamespace
    where namespace.nspname = 'private'
      and relation.relkind in ('r', 'p', 'v', 'm')
      and pg_catalog.has_table_privilege('authenticated', relation.oid, 'SELECT')
  ),
  0::bigint,
  'authenticated clients cannot read private server state'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_proc as procedure
    join pg_catalog.pg_namespace as namespace on namespace.oid = procedure.pronamespace
    where namespace.nspname in ('public', 'private')
      and procedure.prosecdef
      and not exists (
        select 1
        from unnest(coalesce(procedure.proconfig, array[]::text[])) as setting
        where setting ~ '^search_path=(""|)$'
      )
  ),
  0::bigint,
  'every security-definer function pins an empty search_path'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_proc as procedure
    join pg_catalog.pg_namespace as namespace on namespace.oid = procedure.pronamespace
    where namespace.nspname in ('public', 'private')
      and pg_catalog.has_function_privilege('anon', procedure.oid, 'EXECUTE')
  ),
  0::bigint,
  'anonymous clients cannot execute application functions'
);

select is(
  (
    select count(*)
    from pg_catalog.pg_proc as procedure
    join pg_catalog.pg_namespace as namespace on namespace.oid = procedure.pronamespace
    where namespace.nspname in ('public', 'private')
      and procedure.prokind in ('f', 'p')
      and pg_catalog.pg_get_functiondef(procedure.oid) ~* E'\\mexecute\\M'
  ),
  0::bigint,
  'application functions contain no dynamic SQL execution'
);

select * from finish();
rollback;
