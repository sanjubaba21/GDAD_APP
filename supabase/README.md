# GDAD BAGS Supabase backend

This directory is the version-controlled source of truth for the Supabase backend.
Dashboard changes must be captured as migrations before they are considered complete.

## Local tooling

The repository pins Supabase CLI `2.111.0` as a development dependency. Use Node.js 20
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

## Deterministic development fixtures

`supabase db reset` loads fixed, non-production fixtures for two shops, all app roles,
multi-lot FIFO inventory, purchasing, a partially paid credit sale and return, expense
and transfer journals, and notifications. The fixture Auth rows use reserved `.invalid`
emails, have empty passwords, and deliberately have no `private.login_credentials` row;
they cannot be used for PIN login. Never add a real credential or customer record.

CI runs `verify-dev-seed.sql` before and after a second reset and requires identical
hashes. It then runs `clear-dev-seed.sql` so existing pgTAP suites retain isolated row
counts. These scripts are local/test tooling only. `supabase db push` deploys migrations
but does not upload `seed.sql` to the hosted development project.

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

## Authentication contract

The approved B3.1 user-ID/PIN mapping, verifier construction, one-time Auth session
exchange, failure contract, Android session rules, and B3.2/B3.3 acceptance tests are
defined in [`docs/authentication.md`](../docs/authentication.md). Treat that document as
the implementation contract; changes require a reviewed migration and status update.
