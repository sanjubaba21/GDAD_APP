# Phase 3 backend exit gate

Status: Passed on 2026-07-27 (Asia/Kathmandu)

## Outcome

The first-release Supabase backend contracts in execution-plan Tasks 3.1–3.9 are
implemented and verified from a fresh Docker-backed database. Business mutations are
server-authoritative, tenant-scoped, role-checked, atomic, retry-safe, and audited.
Trusted reports reconcile against the resulting operational and journal records.

No production schema change was required for Task 3.9. The integration harness consumes
the already-deployed contracts; hosted development migration history remains current
through `20260728060000_trusted_business_reports.sql`. A final linked migration-history
check matched every local migration and linked lint reported `No schema errors found`.

## Authoritative verification

GitHub Actions run `30293062132` passed without retrying the failed job. It performed:

1. Deno formatting, linting, type checking, and Edge Function tests.
2. Every migration on a fresh local Supabase/Postgres stack.
3. Two deterministic seed applications with matching checksums, followed by cleanup.
4. Database function lint with warnings enabled and errors fatal.
5. Every pgTAP database test.
6. The backend integration and real multi-session concurrency harness.

The workflow entry point is `.github/workflows/database-tests.yml`. The concurrency
entry point is `supabase/integration-tests/backend_concurrency.sh`; its setup and final
invariant checks are adjacent SQL files. These files intentionally live outside
`supabase/tests/` so `supabase test db` does not discover them as pgTAP suites.

## Integration and concurrency coverage

| Scenario | Required invariant |
| --- | --- |
| Two sales compete for one unit | Exactly one commits; product and FIFO lot reach zero |
| Two identical sale retries | Both callers receive the same result; one sale exists |
| Two payments over-allocate one vendor bill | Exactly one commits; due is 300 paisa |
| Partial sale return | One unit is restored and exactly one posted return exists |
| Two expenses compete for limited cash | Exactly one commits; derived cash is 400 paisa |
| Failed competing operations | No incomplete idempotency/request record remains |
| Disabled and other-shop callers | Both forged sales fail and create no sale record |
| Journal reconciliation | Every tested journal has at least two balanced entries |
| Dashboard reconciliation | Net sales are 2,000 paisa and expenses are 900 paisa |

Earlier Task 3 pgTAP suites additionally cover successful and unauthorized operations,
forged values, insufficient stock, rollback behavior, exact reversals, cross-shop
isolation, report role shaping, and Nepal date boundaries.

## Handoff rules

- Treat migrations and public RPC signatures through `20260728060000` as the stable
  backend contract for Android Phase 4 integration.
- Add every new business mutation to focused pgTAP coverage and, when it shares a
  resource boundary, the multi-session integration harness.
- Do not solve concurrency failures by retrying CI. Diagnose the invariant and make the
  transaction or deterministic test setup correct.
- Keep Android clients on public RPCs and read policies; never grant direct writes to
  protected business tables.
- Create and validate a separate production Supabase project before launch. This gate
  covers the hosted development project and fresh CI databases only.

## Next task

Execution-plan Task 4.1: introduce testable Android application architecture and
dependency injection, with an application-scoped Supabase client and deterministic fake
implementations for unit and UI tests.
