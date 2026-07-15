# GDAD BAGS Supabase backend

This directory is the version-controlled source of truth for the Supabase backend.
Dashboard changes must be captured as migrations before they are considered complete.

## Local tooling

The repository pins Supabase CLI `2.101.0` as a development dependency. Use Node.js 20
or newer and install dependencies with `pnpm install` (or `npm install`). A Docker API
compatible container runtime is required to run the local Supabase stack.

```powershell
pnpm supabase:start
pnpm db:reset
pnpm db:lint
pnpm db:test
pnpm supabase:stop
```

Do not expose the local stack to a public network. Do not commit `.env` files, database
passwords, access tokens, secret keys, `service_role` keys, PIN peppers, or real user
data.

## Schema boundaries

- `auth.users` is the source of authenticated identities.
- `public.user_profiles` maps an Auth user to the app's normalized `login_id`, display
  name, enabled state, and platform-level role.
- `public.shop_memberships` assigns Owner or Salesman access to a shop. A user may have
  more than one membership even though the first Android release uses one active shop.
- `private.login_credentials` contains server-only PIN verifier state. The `private`
  schema is not exposed through PostgREST. Android must never read it.
- Every product, lot, and movement carries `shop_id`; composite foreign keys prevent a
  record from referring to another tenant's product or lot.
- Money is stored as integer paisa. Authoritative instants use `timestamptz`; Nepal
  business dates are derived using `Asia/Kathmandu`.
- `inventory_movements` is append-only and idempotent per shop.
- `products.current_stock` and `inventory_lots.remaining_quantity` are server-maintained
  projections. Android receives `SELECT` only.

## Authorization model

RLS is enabled on every exposed table. Authenticated users can read only their own
authorized shop data. Owners can view profiles and memberships in shops they own.
Super Admin can read across shops. No authenticated or anonymous role receives direct
insert, update, or delete privileges on the initial tables.

Future mutations must use narrowly scoped Postgres RPCs or Edge Functions that:

1. derive actor and tenant from the authenticated session;
2. validate role and disabled state;
3. run the entire business operation in one database transaction;
4. require an idempotency key;
5. append inventory/financial events and audit records;
6. never accept `shop_id` or actor identity as authoritative client input.

## Hosted environments

Development and production must use separate Supabase projects. Linking is intentionally
not committed because the CLI stores project-specific state under `supabase/.temp/`.
After the development project exists, link locally and push only reviewed migrations.
