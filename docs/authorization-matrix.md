# Authorization matrix

This document is the Phase 1 security contract for the currently exposed Supabase
schema. PostgreSQL grants are the first boundary, Row Level Security (RLS) is the
tenant boundary, and privileged account operations are available only through Edge
Functions that authenticate the caller before using service-role RPCs.

## Principals

| Principal | Meaning |
|---|---|
| Anonymous | No Supabase Auth session. |
| No membership | Active authenticated profile with no shop membership. |
| Salesman | Active standard profile with an active `salesman` membership. |
| Disabled | Auth token may still exist, but the authoritative profile is disabled. |
| Owner | Active standard profile with an active `owner` membership. |
| Super Admin | Active profile whose authoritative `platform_role` is `super_admin`. |
| Service role | Backend-only credential used by trusted Edge Functions; never shipped to Android. |

JWT claims, request `shop_id`, and request role names are not authoritative. Every
decision resolves the caller from `auth.uid()` or an Edge-verified JWT subject and
then reads roles, profile state, membership state, and shop state from PostgreSQL.

## Public table matrix

`Own` means the caller's profile/membership. `Shop` means rows belonging to an active
membership's shop. `All` means every row. A dash means no rows and no direct write
privilege.

| Table | Anonymous | No membership | Salesman | Disabled | Owner | Super Admin | Direct client writes |
|---|---:|---:|---:|---:|---:|---:|---|
| `shops` | — | — | Shop | — | Shop | All | None |
| `user_profiles` | — | Own | Own | — | Own + active members of owned shops | All | None |
| `shop_memberships` | — | — | Own | — | Memberships in owned shops | All | None |
| `products` | — | — | Shop | — | Shop | All | None |
| `inventory_lots` | — | — | — | — | Shop | All | None |
| `inventory_movements` | — | — | — | — | Shop | All | None; append-only ledger |
| `sales`, `sale_lines`, `sale_payments` | — | — | Shop | — | Shop | All | None |
| `sale_returns`, `sale_return_lines`, `sale_return_allocations`, `refunds` | — | — | Shop | — | Shop | All | None |
| `sale_lot_allocations` | — | — | — | — | Shop | All | None; contains cost evidence |
| `vendors`, `purchase_bills`, `purchase_bill_lines` | — | — | — | — | Shop | All | None |
| `purchase_receipts`, `purchase_receipt_lines` | — | — | — | — | Shop | All | None |
| `vendor_payments`, `vendor_payment_allocations`, `vendor_returns`, `vendor_return_lines` | — | — | — | — | Shop | All | None |
| `financial_accounts`, `accounting_periods` | — | — | — | — | Shop | All | None |
| `journal_transactions`, `journal_entries`, `expenses` | — | — | — | — | Shop | All | None; immutable balanced evidence |

Salesmen use `products.current_stock` for quantity and cannot read lot, movement, or
allocation cost evidence under approved policy D9. Cross-shop reads return no rows.
`INSERT`, `UPDATE`, and `DELETE` are revoked from
`anon` and `authenticated` on every public table, so forged tenant identifiers cannot
bypass the protected operation layer. Inventory lots and movements cannot be changed
or deleted directly; future inventory commands must use transactional, security-
definer operations with authoritative tenant checks.

## Private table matrix

All private tables deny every privilege to anonymous and authenticated clients:
`login_credentials`, `pin_login_rate_limits`, `account_provisioning_requests`,
`account_audit_events`, `account_admin_requests`, and `account_admin_rate_limits`.
They contain verifier, throttle, idempotency, or audit state and are backend-only.

## RPC and Edge Function matrix

| Operation | Anonymous | Authenticated client | Trusted backend | Authorization rule |
|---|---:|---:|---:|---|
| `pin_login_prepare`, `pin_login_complete` | Denied | Denied | Service role | Called only by `pin-login`; generic failures and server-side rate/PIN checks. |
| `account_provision_start`, `account_provision_attach_auth`, `account_provision_finalize`, `account_provision_fail` | Denied | Denied | Service role | `manage-users` binds JWT subject to the actor; Super Admin creates Owners and Owners create Salesmen only in owned shops. |
| `account_admin_prepare`, `account_admin_apply`, `account_admin_fail` | Denied | Denied | Service role | `manage-accounts` binds JWT subject, re-verifies actor PIN, and enforces target hierarchy/shop. |
| `set_updated_at` | Denied | Denied | Trigger owner | Trigger-only maintenance function. |
| `is_active_user`, `is_super_admin`, `has_shop_role`, `can_view_user` | Denied | Execute via RLS only | Database | Private helpers derive authority from `auth.uid()` and authoritative rows. |

Clients cannot call the service RPCs directly. Possession of a publishable key or a
forged actor/shop/role payload therefore grants no privileged operation. The Edge
Functions ignore caller-supplied role claims and compare the verified JWT subject to
the authoritative actor.

## Executable coverage map

| Security requirement | Executable coverage |
|---|---|
| Anonymous denial, table grants, every role's row visibility, cross-shop reads, disabled stale-token denial, and immutable/direct-write attempts | `supabase/tests/database/authorization_matrix.test.sql` |
| RLS presence and baseline table policy shape | `supabase/tests/database/core_foundation.test.sql` |
| PIN RPC grants, rate limiting, lockout, and reset | `supabase/tests/database/pin_login_lockout.test.sql` and `supabase/functions/tests/pin-login/*` |
| Forged role/action, Salesman provisioning denial, and Owner cross-shop provisioning denial | `supabase/tests/database/account_provisioning.test.sql` and `supabase/functions/tests/manage-users/core.test.ts` |
| Owner cross-shop administration, Salesman denial, protected Super Admin target, and state transitions | `supabase/tests/database/account_administration.test.sql` and `supabase/functions/tests/manage-accounts/core.test.ts` |

Fresh-database CI applies all migrations before running every pgTAP file, so this
matrix is checked against the actual schema rather than a mocked policy model.
