# GDAD BAGS operations, monitoring, backup, and restore runbook

Last reviewed: 2026-07-29 (Asia/Kathmandu)

## Current decision and launch gate

Project `zniqkuwktvincjndcgpu` is the hosted **development** project. Its current billing plan could
not be inspected in this session and must be verified in the Supabase organization billing page.
Do not treat it as production and do not copy its users, sessions, PIN verifier rows, peppers, or
test fixtures into a production project.

The Supabase [pricing comparison](https://supabase.com/pricing) currently states that Free has
one-day API/database log access and no automatic backups. The official
[backup guide](https://supabase.com/docs/guides/platform/backups) recommends regular CLI dumps and
off-site storage for Free projects. Pro provides daily backups retained for seven days and a metrics
endpoint; [log drains](https://supabase.com/docs/guides/telemetry/log-drains) and
[point-in-time recovery](https://supabase.com/docs/guides/platform/manage-your-usage/point-in-time-recovery)
are separate paid add-ons.

Production launch is blocked until all of the following are recorded in `PROJECT_STATUS.md`:

- a distinct production project and region exist;
- the selected plan/backup mode and accepted RPO/RTO are named;
- the latest backup timestamp and restorable window are visible;
- alert destinations and current on-call owners are configured outside Git;
- a restore into an isolated non-production project has passed with recorded evidence; and
- no development Auth identity, PIN material, secret, session, or fixture was copied to production.

## Ownership

| Responsibility | Primary owner | Backup owner | Escalation |
| --- | --- | --- | --- |
| Business incident decision and customer communication | GDAD business owner | Store operations delegate | Security/release engineer |
| Supabase availability, Auth, Edge, database, backup/restore | Supabase organization Owner role | Organization Administrator | Supabase support/status page |
| Android release/crash triage | Release engineer | Android maintainer | Business owner for rollback decision |
| Security or tenant-isolation incident | Security/release engineer | Supabase organization Owner | Stop writes, revoke/rotate, preserve evidence |

Names, phone numbers, personal email addresses, paging URLs, and provider credentials belong in the
organization's private incident-contact store. They must not be committed to this repository.
Review the roster quarterly and after any access change.

## Privacy-safe correlation and logging

`pin-login` assigns every invocation a UUID. After a valid request is parsed, the existing
idempotency `request_id` becomes the correlation ID; malformed requests keep an opaque server UUID.
Every response from `pin-login`, `manage-users`, and `manage-accounts` includes
`x-gdad-correlation-id`. Structured failure events contain exactly:

```json
{
  "event": "edge_operation_failed",
  "function": "pin-login",
  "stage": "auth-token-exchange-503",
  "correlation_id": "uuid"
}
```

Never log or attach PINs, login IDs, display names, shop/user IDs, authorization/API/service keys,
session tokens, request/response bodies, IP addresses, source fingerprints, exception messages,
database URLs, peppers, diagnostic/bootstrap secrets, or Auth email addresses. Android production
code sends no raw log/stack trace and retains only typed operation/error/status/exception-class
metadata. A crash provider must not be added until its DPA, residency, retention, access, deletion,
and redaction configuration are approved; default crash breadcrumbs and network/body capture must
remain disabled. Until then, use reproducible user steps, app version/APK hash, Android version, safe
error category, and Edge correlation ID for support.

## Monitoring sources

- Supabase Logs Explorer: API, Auth, Postgres, and Edge logs. Search the exact correlation UUID or
  `edge_operation_failed`; never search a PIN/login ID.
- Supabase Reports: database CPU, memory, disk, IOPS, connections, Auth/API/Edge behavior. Official
  [Reports documentation](https://supabase.com/docs/guides/telemetry/reports) exposes up to 24 hours
  on Free and longer ranges by paid tier.
- Supabase Metrics API: use for production Prometheus/Grafana alerts when the selected plan exposes
  it. The endpoint is credential-protected; store its credentials only in the monitoring provider.
- GitHub Actions: migration replay, deterministic seed, database lint, pgTAP, concurrency/invariant
  harness, Deno tests/audit, and Android release gates.
- Android: dashboard outbox-permanent-failure notice and user-reported safe error category. There is
  no central device telemetry in the first test APK.
- Supabase platform status: [status page](https://status.supabase.com/) and its RSS/Atom feed.

## Alert policy

Thresholds are first-release defaults and must be tuned after two weeks of production baseline data.

| Severity and signal | Threshold | Primary owner | First response |
| --- | --- | --- | --- |
| SEV-1 tenant/auth bypass, leaked credential/PIN/session, unexplained ledger or stock corruption | Any credible event | Security/release engineer | Disable affected writes/function, revoke exposed material, preserve safe logs/correlation IDs, notify business owner. |
| SEV-1 database/API unavailable | Continuous 5xx/unreachable for 5 minutes | Supabase organization Owner | Check status page and Reports, stop retry storms, announce outage, open provider incident. |
| SEV-2 Edge internal failures | At least 10 or over 5% of calls in 5 minutes for one function/stage | Release engineer | Group by function/stage, sample only correlation IDs, check recent deploy and upstream status, rollback/forward-fix. |
| SEV-2 PIN abuse/lockout | At least 25 rate-limited results in 15 minutes or 5 accounts locked unexpectedly | Security/release engineer | Confirm HMAC-only rate data, block source through approved platform control, do not weaken generic errors/rate limits. |
| SEV-2 database saturation | CPU or memory over 85%, connections over 80%, or disk over 85% for 15 minutes | Supabase organization Owner | Identify query/traffic change, preserve query-plan evidence, rate-limit nonessential work, resize only with approval. |
| SEV-2 backup stale/restore unavailable | No successful backup/export in 26 hours or newest recovery point outside accepted RPO | Backup owner | Create/verify export, investigate platform backup state, block release/data migration until recoverability returns. |
| SEV-3 Android permanent outbox failure/crash cluster | 3 same-version reports in 24 hours | Android maintainer | Reproduce with safe metadata, inspect correlation IDs, fix and rerun the owning workflow/gates. |

Every incident record needs: start/end Nepal time, severity, owner, affected environment/version,
correlation IDs, safe symptom/count, containment, cause, repair, verification, customer decision, and
follow-up owner/date. Do not paste raw logs into public issues.

## Backup and retention policy

### Development (Free-compatible)

- After every migration deployment and at least daily while development data matters, create the
  three official logical exports: roles, schema, and data. Use the documented
  [Supabase CLI backup/restore procedure](https://supabase.com/docs/guides/platform/migrating-within-supabase/backup-restore).
- Keep encrypted exports in an access-controlled off-site store, not the repository or developer
  desktop. Retain seven daily and four weekly sets; automatically expire older development exports.
- Store SHA-256, byte size, UTC/Nepal capture time, source project ref, migration head, CLI version,
  and operator in the private backup catalog. Never store the connection string/password there.
- A dump command containing a database password must not be pasted into chat, shell history, CI
  output, `PROJECT_STATUS.md`, or an issue. Prefer an ephemeral protected environment/secret input.

### Production minimum

- Minimum: distinct paid production project with automatic daily backups and an explicitly accepted
  24-hour RPO / four-hour RTO. Verify backup success daily and before every migration.
- If the business cannot accept up to one day of transaction loss, enable PITR with an approved
  recovery window/compute/cost before launch and record the narrower RPO. Do not claim PITR merely
  because it is available in the dashboard.
- Keep a weekly encrypted logical export for 90 days in a separate access-controlled account to
  cover operator/platform failure modes. Review legal/tax retention before deleting authoritative
  sales, finance, inventory, vendor, or immutable audit records.
- Database backups do not restore deleted Storage objects; the first release uses no business-file
  Storage dependency. If Storage is later added, create and test a separate object backup policy.
- Test fixtures: never production. Notification cache expires at 90 days on device; business/audit
  database retention remains immutable/indefinite until a reviewed Nepal legal/tax schedule exists.

## Restore drill (isolated target only)

Never conduct a drill by overwriting the source or production project. Restoring is a destructive
external action and requires the incident/backup owner to confirm the exact isolated target at
action time.

1. Record source backup ID/time/checksum, source migration head, accepted RPO/RTO, drill owner, and
   start time. Do not record credentials.
2. Create a disposable Supabase project in the same region/major Postgres version. Confirm its ref
   differs from development and production. Do not create real users or reuse production secrets.
3. Restore using Supabase's supported "restore to a new project" flow or the official roles/schema/
   data CLI sequence. With a logical restore, preserve migration history as documented and use a
   single-transaction/`ON_ERROR_STOP` data restore where supported.
4. Deploy the repository's Edge code only after the database restore. Supply newly generated drill-
   only peppers/dummy verifier/bootstrap material; never copy production secret values.
5. Run database lint, all pgTAP tests, the backend concurrency/invariant harness, and these read-only
   reconciliations: migration head/count; RLS enabled on every exposed table; zero cross-shop
   references; zero negative/lot-overflow stock; product projection equals eligible lot stock; every
   posted journal balances; posted sale/purchase/return idempotency keys are unique; audit tables
   remain append-only; restored row counts match the private backup catalog.
6. Create a fresh drill-only Super Admin through the controlled bootstrap, verify one login and one
   RLS profile read, then remove the one-time bootstrap/diagnostic secret. Do not test with source
   users or PINs.
7. Record completion time, actual RPO/RTO, validation outputs/counts, defects, fixes, retest result,
   and reviewer. Destroy the disposable project only after evidence review; record destruction and
   delete temporary plaintext dump files. Preserve only encrypted backup/evidence and checksums.

### Restore evidence template

```text
Drill date/time (Nepal):
Source environment/project ref:
Target disposable project ref:
Backup ID/capture time/SHA-256/bytes:
Source and restored migration head/count:
Postgres/Supabase CLI versions:
Start / database-ready / app-verified / target-destroyed times:
Actual RPO and RTO:
RLS/cross-shop/FIFO/journal/idempotency/audit/row-count checks:
Fresh drill identity login + RLS read result:
Secrets confirmed newly generated and removed:
Defects and retest:
Operator and independent reviewer:
Final result: PASS / FAIL
```

No restore drill is recorded as passed yet. Task 6.5 remains incomplete until the template contains
real evidence from an isolated target and the current production alert/backup configuration is
verified.

## Incident response and recovery flow

1. Triage severity and assign the named role; note Nepal time and app/backend versions.
2. Contain narrowly: disable affected mutation/function/account, revoke only exposed tokens/secrets,
   or enable maintenance messaging. Never disable RLS or generic auth failures to restore service.
3. Investigate with safe correlation IDs, function/stage counts, provider metrics, audit records,
   and query plans. Keep raw restricted logs in the approved incident store.
4. Recover by rollback or reviewed forward-fix. For data corruption, stop writes and restore only
   after the business owner accepts the recovery point/downtime.
5. Verify tenant isolation, authentication, FIFO/stock, ledger balance, idempotency, reports, and
   Android smoke paths before reopening writes.
6. Rotate affected material, remove temporary access/diagnostics, communicate resolution, and
   schedule a blameless follow-up with concrete owners/dates.

## Quarterly checklist

- Test on-call destinations and provider status subscription.
- Review organization members/roles, MFA, service tokens, Edge secrets, CI environments, and backup
  store access; remove stale access.
- Review log/backup plan entitlements, latest backup age, restore window, cost/spend-cap settings,
  and storage/database growth.
- Execute one isolated restore drill and one SEV-1 tabletop; record actual RPO/RTO and fixes.
- Confirm correlation/redaction tests, dependency audit, migration replay, database lint/pgTAP,
  Android release gates, and physical-device smoke tests pass on the current release branch.
