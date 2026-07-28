# GDAD BAGS — Project Status and Agent Handoff

This is the canonical status file for the GDAD BAGS repository. Every developer or
agent must update this file in the same change as any source code, test, build,
configuration, database, security-rule, or backend change.

Last verified: 2026-07-28 (Asia/Kathmandu)
Current milestone: Execution plan Task 5.3 — vendor and purchase receipt screens
Current version: `0.1.0` (`versionCode = 1`)

## Mandatory update protocol

After every code or configuration change:

1. Update the relevant item under **Completed work**, **Work in progress**, or
   **Remaining work**.
2. Add a dated entry to **Change log** describing the files and behavior changed.
3. Record the exact verification command and outcome under **Latest verification**.
4. Record new risks, assumptions, migrations, or manual setup under **Known issues and
   decisions**.
5. Never mark an item complete merely because a UI label, interface, dependency, or
   data class exists. Mark it complete only when the behavior is implemented and
   proportionately tested.
6. If verification was not run, explicitly write `Not run` and the reason. Do not imply
   that unverified work passes.

Use this change-log format:

```text
### YYYY-MM-DD — Short change title
- Status: Complete | Partial | Blocked
- Changed: path(s)
- Behavior: what now works or changed
- Data/security impact: none, or details including migrations/rules
- Verification: exact command and result
- Next: the smallest logical follow-up
```

## Product scope

GDAD BAGS is a native Android application for Nepal-focused sales, inventory, vendor,
cash/bank, and reporting workflows. Users are divided into Super Admin, Owner, and
Salesman roles. Cloud data is intended to be tenant-scoped by `shopId`. Inventory cost
is intended to use immutable FIFO lots and exact per-sale lot allocations.

## Current implementation summary

The repository contains the first-release Android UI baseline and a buildable Supabase
client foundation. Android authentication uses the hosted production contract, while
business features are not yet connected to persistent data. The hosted development project
`zniqkuwktvincjndcgpu` in Seoul has repository migrations through
`20260728060000`, deployed `pin-login`,
`manage-users`, and `manage-accounts` Edge
Functions, private rate/credential/provisioning/administration state, and clean
hosted lint. Strict malformed,
invalid-key, and unknown-user failure paths are verified against the hosted function.
The local Android debug environment has the project URL and publishable key in ignored
Gradle properties. One managed Super Admin Auth identity/profile/credential exists; its
correct-PIN session and authenticated RLS profile read are verified. Android production
authentication now consumes that hosted contract, encrypts persisted sessions with
Android Keystore, and derives role/shop from authoritative RLS reads. Android remote
operations now use typed DTOs, bounded timeouts, explicit retry classification, one
authenticated refresh/retry, and safe domain error categories. A versioned Room cache
provides tenant/user-owned offline read models, transactional snapshot replacement, and
fail-closed purge on logout, identity change, tenant change, or missing ownership state.

Preview authentication and prefix-derived roles are absent from production sources. Every
release build runs an authentication safety gate that also rejects embedded Supabase secret/
service-role keys and hard-coded numeric PIN assignments.

## Completed work

### Execution-plan baseline

- [x] **Task 0.1:** Verified this workspace is the canonical `main` checkout of
  `sanjubaba21/GDAD_APP` at commit `1be9d33`, with the expected Android, Supabase,
  documentation, workflow, and build paths present.
- [x] **Task 0.2:** Reconciled source, migration, Edge Function, code-map, hosted-state,
  and UTF-8 status. The plan-referenced external handoff file was not available; the
  supplied execution plan and repository status were used instead.
- [x] **Task 0.3:** Established a passing Android/Edge/hosted-lint baseline. Local
  database runtime tests remain delegated to the successful current-HEAD GitHub CI run
  because Docker is unavailable on this machine.
- [x] **Task 1.1:** Fixed the authoritative Android PIN-session import, encrypted
  storage, restore/refresh/logout/revocation behavior, and deliberately disabled
  deep-link policy for the PIN-only first release.
- [x] **Task 1.2:** Login and provisioning now share one tested login-ID/PIN contract,
  versioned HMAC material, Argon2id hash creation, and fail-closed verification helper.
- [x] **Task 1.3:** Privileged account provisioning validates authoritative hierarchy,
  reserves normalized login IDs, creates/reconciles an exact managed Auth subject,
  attaches profile/membership/private verifier state idempotently, compensates partial
  failures, and writes immutable PIN-free audit events. Fresh-database CI covers
  allowed/denied, retry, collision, cross-shop, and compensation paths; the controlled
  Super Admin bootstrap and subject-matched hosted PIN login both succeeded.
- [x] **Task 1.4:** Dedicated account administration implements authoritative hierarchy,
  actor-PIN reauthentication, per-actor/source limits, idempotent disable/re-enable/PIN
  reset, target refresh-session revocation, safe immutable audits, generic failures,
  and protected Super Admin targets. Edge tests and fresh-database pgTAP cover allowed/
  denied paths and state transitions; migration/function deployment and hosted denial
  smoke verification succeeded.
- [x] **Task 1.5:** Hosted PIN login now proves the exact managed Auth subject, rejects
  reuse of the generated one-time email token, refreshes to the same JWT subject, and
  returns the matching authenticated Super Admin profile. Generic malformed, invalid-
  key, unknown/wrong, rate-limit, fifth-failure lock, and success-reset paths are covered
  by hosted HTTP evidence plus Edge/pgTAP tests; only redacted booleans were retained.
- [x] **Task 1.6:** The documented table/RPC permission matrix is enforced by grants,
  tenant RLS, private service-only RPCs, and 42 fresh-database assertions covering all
  required principals, cross-shop reads, forged writes, and immutable rows. Disabled
  stale sessions now fail closed for self-profile and membership reads as well.
- [x] **Task 2.1:** The canonical data dictionary covers 35 existing/planned objects,
  including 29 detailed business tables, private operation state, atomic transaction
  boundaries, derived-value reconciliation, lifecycle/reversal rules, RLS ownership,
  expected indexes, and the 11-group Task 2.2 decision register.
- [x] **Task 2.2:** Product owner approved D1–D11. The policy contract maps stock,
  pricing, credit, payment, return, purchasing, date/period, product-code, visibility,
  rounding/tax, and retention choices to schema, RPC, permission, UI, and test effects.
- [x] **Task 2.3:** Hosted sales/payment/return schema enforces same-shop keys,
  server-reconciled totals and full FIFO allocation, non-credit settlement, derived
  credit due, cumulative return/allocation limits, refund caps, idempotency, RLS, D9
  cost restriction, and no direct Android writes. All 31 new fresh-database assertions pass.
- [x] **Task 2.4:** Hosted vendor/purchasing schema enforces permanent normalized invoice
  identity, same-shop bill/receipt/payment/return chains, exact receipt-to-FIFO-lot
  quantity/cost, fully allocated payments, derived vendor due, cumulative receipt/return
  limits, Owner-only reads, and no direct Android writes. All 31 new assertions pass.
- [x] **Task 2.5:** Hosted ledger schema provides constrained account masters,
  non-overlapping periods, immutable balanced journals/entries, typed same-shop sources,
  exact reversals, derived-only balances, expense reconciliation, Owner-only reads, and
  no direct Android writes. All 28 new assertions pass.
- [x] **Task 2.6:** Hosted notifications enforce tenant-safe user/role targeting,
  exact 90-day expiry, typed source references, protected per-user first-read state,
  bounded backend cleanup, and secret-free payloads. Private business audits capture
  authorized actor/shop/operation/record evidence and reject every update/delete.
  All 39 new assertions pass and hosted lint is clean.
- [x] **Task 2.7:** Deterministic local fixtures cover two shops, Super Admin/Owner/
  Salesman roles, products and multiple FIFO lots, a received vendor bill, partially
  paid credit sale and return, stock movements, expense/transfer journals, and
  targeted/read notifications. CI proves identical hashes across two resets, commits
  no usable credential, cleans fixture IDs before pgTAP, and passes every suite.
- [x] **Phase 2 exit gate:** Fresh migrations apply from zero, database lint and every
  pgTAP suite pass, and the first-release data model, policies, retention, and local
  development fixtures are documented.
- [x] **Task 3.1:** Hosted Owner-only product management atomically creates, updates,
  and archives with tenant validation, normalized SKU/barcode identity, permanent
  historical code reservation, request-fingerprint idempotency, draft-operation
  archive guards, and safe immutable audit snapshots. All 45 assertions pass.
- [x] **Task 3.2:** Hosted Owner-only purchase receipt atomically creates the received
  bill/receipt, immutable lines, exact FIFO lots and movements, stock projection,
  optional allocated payment, due result, balanced journals, and safe audit evidence.
  Request fingerprints, deterministic locks, exact retries, rollback, and tenant denial
  are covered by 41 assertions; hosted history and lint are clean.
- [x] **Task 3.3:** Hosted atomic FIFO sale derives tenant/role, enforces configured
  Salesman pricing/full payment and Owner-only discounts/credit, locks products and lots
  deterministically, forbids negative stock, computes exact price/cost/due, and writes
  settlement, balanced journals, low-stock notification, retry result, and safe audit.
  All 50 assertions pass; hosted history and lint are clean.
- [x] **Task 3.4:** Hosted Owner-only sale return serializes on the sale, enforces the
  30-day and cumulative limits, restores sellable units to exact reverse allocations,
  preserves damaged evidence without saleable stock, applies due-first credit and exact
  paid-value refunds, and writes status, balanced journals, notification, retry result,
  and safe audit atomically. All 53 assertions pass; hosted history and lint are clean.
- [x] **Task 3.5:** Hosted Owner-only reason-coded inventory adjustment creates new FIFO
  lots for additions and consumes specified lots for damage/loss/removal without
  rewriting receipt history. Exact movements/projection, positive-cost balanced journal,
  zero-cost treatment, Owner notification, retry result, and safe audit are atomic. All
  51 assertions pass; hosted history and lint are clean.
- [x] **Task 3.6:** Hosted Owner-only vendor payments allocate one cash/bank event across
  one or more bills without overpayment; original-lot vendor returns cannot exceed
  available stock or unpaid due; and retry-safe payment/return reversals create exact
  compensating journals and stock movements. Due remains derived, cross-shop/direct
  writes fail closed, and all 59 assertions pass with clean hosted history and lint.
- [x] **Task 3.7:** Hosted Owner-only expense, deposit, withdrawal, transfer, and
  reversal operations post exact balanced journals with derived no-overdraft balances,
  fingerprinted retries, and safe audits. Existing/application-created shops receive
  all 11 protected system accounts. The deterministic fixture and all 52 assertions
  pass; hosted history and lint are clean.
- [x] **Task 3.8:** Hosted tenant-safe daily/period reports reconcile sales, returns,
  FIFO COGS/profit, stock quantity/value, low stock, vendor due, account balances, and
  effective expenses. Nepal midnight and role-shaped Owner/Salesman output are enforced;
  four indexed access paths are measured. All 37 assertions pass with clean hosted
  history and lint.
- [x] **Task 3.9:** A fresh Docker-backed CI run passes Edge verification, migration
  replay, deterministic seed/reset, lint, every pgTAP suite, and a real multi-session
  integration harness covering competing stock, vendor-payment, and cash debits; exact
  retries; partial returns; rollback cleanup; disabled users; cross-shop isolation;
  balanced journals; and report reconciliation.
- [x] **Phase 3 exit gate:** Every first-release backend mutation is atomic,
  idempotent, tenant-safe, and audited; trusted reports reconcile with authoritative
  records; and GitHub Actions run `30293062132` passes from a fresh database.

### Android foundation

- [x] **Task 5.2:** Added a searchable Room-backed product/stock catalog for Owner and
  Salesman, Owner-only cost and create/edit/archive controls, protected idempotent
  `manage_product` mutations, exact-key retry/offline outbox behavior, archived-history
  visibility, SQL-state-aware validation/duplicate/conflict messages, and explicit Room
  v3-to-v4 migration. All 67 tests, release safety, release APK assembly, full lint, and
  diff checks pass.
- [x] **Task 5.1:** Added the first complete feature slice: RLS-backed shop/managed-user
  directory, protected `manage-users` and `manage-accounts` calls, Room v3 owner-scoped
  cache, stable retry UUIDs, role-aware ViewModel, Owner/Salesman lists, create forms,
  reauthenticated disable/re-enable/PIN-reset confirmations, session-revocation/audit-safe
  feedback, and accessible denial/error/retry states. Shop create/archive remains excluded
  because no protected first-release backend mutation exists. All 56 tests, release safety,
  release assembly, and full lint pass.
- [x] **Task 4.7:** Added stable Navigation Compose 2.9.8 type-safe routes,
  authentication graph gating, a complete per-role destination policy enforced at both
  navigation and render time, restored back-stack/process state, back controls, and a
  PIN-only fail-closed external-navigation policy with no registered deep links. Shared
  accessible loading/empty/error/ready and confirmation components are verified through
  Robolectric Compose tests. All 45 tests, release safety/APK assembly, and full lint pass.
- [x] **Phase 4 exit gate:** Production authentication, testable DI, typed remote calls,
  owner-scoped Room cache/outbox, explicit offline policy, and stable role-gated navigation
  are implemented and verified.
- [x] **Task 4.6:** Added Room schema v2 durable mutation outbox with owner/user scope,
  stable UUID idempotency keys, payload credential/size guards, WorkManager connected-
  network execution, bounded exponential retry, stale-claim recovery, and terminal safe
  failure state. Product management and notification reads may queue; financially risky
  and administrative mutations are explicitly online-only. Logout/identity change purges
  pending work, read refresh preserves it, and the dashboard surfaces owner-scoped
  resolution notices. All 37 unit tests, release safety/APK assembly, and full lint pass.
- [x] **Task 4.5:** Added Room 2.8.4/KSP persistence for profile/membership, products,
  stock, vendors, recent sales, accounts, dashboard, and notifications. Owner-filtered
  Flows, transactional complete-snapshot refresh, last-good retention, row ownership
  validation, session-integrated activation/purge, committed schema v1, explicit-only
  migrations, and no destructive fallback are covered by 29 tests. Release assembly,
  release safety, lint-vital, and full lint pass.
- [x] **Task 4.4:** Centralized typed function/query DTOs, bounded remote execution,
  validation/unauthorized/conflict/offline/timeout/rate-limit/unknown classification,
  explicit retry disposition, single authenticated refresh/retry, cancellation
  preservation, sanitized diagnostics, and safe domain error mapping. All 21 unit tests,
  release APK assembly, release safety verification, and full lint pass.
- [x] **Task 4.3:** Removed `PreviewAuthRepository` and all prefix-derived role behavior
  from the production source set. Release pre-build now verifies the production repository
  binding and rejects preview authentication, secret/service-role keys, and hard-coded PIN
  assignments. Unit tests, release APK assembly, release lint-vital, and full lint pass.
- [x] **Task 4.2:** Production Android authentication invokes hosted `pin-login`, imports
  standard sessions into AES-GCM storage backed by a non-exportable Android Keystore key,
  enables automatic refresh, restores and revalidates the Auth subject on startup, and
  loads display name/role/shop only from active RLS-protected rows. Login/restore/logout
  are serialized, errors are sanitized, failed identity validation clears local Auth,
  and logout clears locally even when the remote call fails. All 12 unit tests, debug APK
  assembly, and lint pass; Task 4.2 adds no lint warning.
- [x] **Task 4.1:** Explicit constructor injection now separates the application
  composition root, stateless Supabase client factory, domain authentication contract
  and use case, injected ViewModel, and Compose UI state. `GdadApplication` owns one lazy
  application-scoped client, composables locate no services, and deterministic fakes are
  covered by unit tests. Debug compilation, all four unit tests, APK assembly, and lint
  pass; the architecture and Task 4.2/4.3 transition are documented.
- [x] Kotlin Android application created with Jetpack Compose.
- [x] Package/application ID set to `com.gdad.bags`.
- [x] Minimum SDK set to 31 (Android 12); compile and target SDK set to 36.
- [x] Java/Kotlin toolchain set to Java 17.
- [x] Material 3 Compose theme and edge-to-edge activity configured.
- [x] Internet and notification permissions declared.
- [x] Supabase Auth, PostgREST, and Functions client libraries declared using the
  Supabase Kotlin BoM, with an Android Ktor engine and Kotlin serialization plugin.
- [x] Supabase URL and publishable key can be supplied through Gradle properties or
  environment variables without hard-coding credentials.
- [x] The application-owned dependency container lazily creates one guarded Supabase
  client with Auth, PostgREST, and Functions when configuration is present.
- [x] Firebase Auth, Firestore, and Functions dependencies removed. Firebase is not the
  primary backend; FCM may be evaluated separately for push notifications later.
- [x] Portable JDK, Android SDK, Gradle tooling, and `build-apk.ps1` build flow prepared.
- [x] Debug APK generated at `GDAD-BAGS-test.apk`.
- [x] GDAD Bags artwork configured as the standard and round Android launcher icon
  across all supported density buckets.

### Authentication UI and navigation shell

- [x] User ID and numeric PIN login UI implemented.
- [x] Production validation requires a nonblank user ID and a 4–8 digit PIN.
- [x] `UserRole` supports `SUPER_ADMIN`, `OWNER`, and `SALESMAN`.
- [x] Authoritative profile/membership reads determine role and shop; user-ID prefixes
  have no authorization effect.
- [x] `UserSession` includes user ID, display name, role, shop ID, and
  authentication time.
- [x] Login and logout transitions implemented for the current in-memory session.
- [x] Role-specific dashboard shells and static action cards implemented.

### Domain and inventory foundation

- [x] Non-negative `Money` value type implemented in paisa.
- [x] Overflow-safe money addition and quantity multiplication implemented.
- [x] Initial `Product`, `InventoryLot`, `LotAllocation`, `SaleLine`, and
  `ProductReturn` domain models implemented.
- [x] FIFO allocation consumes lots by `receivedAt`, then lot ID.
- [x] FIFO allocation records exact lot quantities and costs.
- [x] Insufficient stock produces a shortage quantity without inventing a cost.
- [x] Returns restore stock to the original allocations in reverse allocation order.
- [x] Domain guards prevent invalid lot quantities and over-restoration.
- [x] Unit tests cover FIFO order, shortage reporting, and return restoration.

### Documentation

- [x] README documents the production authentication boundary, release safety gate,
  build process, and architecture direction.
- [x] Canonical project status and handoff file created (`PROJECT_STATUS.md`).

### Supabase backend foundation

- [x] Supabase CLI `2.101.0` pinned with a reproducible `pnpm-lock.yaml`.
- [x] Repository-local `supabase/config.toml`, migration, seed placeholder, database
  documentation, and pgTAP test layout created.
- [x] Public tables created for shops, user profiles, shop memberships, products, FIFO
  lots, and append-only inventory movements.
- [x] Server-only PIN verifier state isolated in `private.login_credentials`.
- [x] Tenant relationships enforced with foreign keys, composite keys, checks, unique
  idempotency constraints, and indexes.
- [x] RLS enabled on every initial table with authenticated tenant-scoped read policies.
- [x] Anonymous access and direct authenticated inserts, updates, and deletes denied.
- [x] GitHub Actions database workflow added to apply migrations, lint functions, and
  run pgTAP tests on a Docker-backed runner.
- [x] Production user-ID/PIN identity, verifier, session-exchange, error, and Android
  session contract documented with concrete B3.2/B3.3 acceptance tests.
- [x] PIN verifier schema requires Argon2id PHC strings and records an external Edge
  Function pepper version without storing the pepper.
- [x] Atomic per-source attempt windows and per-account failure/reset/15-minute lockout
  operations implemented as service-role-only security-definer RPCs.
- [x] `pin-login` validates request shape and publishable keys, performs HMAC-peppered
  Argon2id verification/dummy work, and implements subject-checked Auth token exchange
  without exposing server credentials.
- [x] Random PIN/rate peppers and a dummy verifier exist only as hosted Edge secrets;
  active function version 5 disables platform JWT verification so the handler validates
  `sb_publishable_` keys itself.

## Work in progress

- **Owner:** Codex. **Task:** 5.3, vendor and purchase receipt vertical slice.
  **Files:** Vendor/purchase DTOs, repository, Room mapping/migration, ViewModel and
  Owner-only Compose list/detail/forms/cart/review/receipt UI; retry, totals, FIFO refresh,
  balance, role, and migration tests; docs and `PROJECT_STATUS.md`.
  **Acceptance:** Owner can manage vendors and submit a reviewed purchase with invoice,
  quantities/costs, payment/due split, and account; confirmation uses authoritative totals;
  exact retry cannot duplicate receipt; stock/vendor/account balances refresh. Salesman and
  Super Admin cannot reveal purchasing data or controls. **Dependencies:** Tasks 5.2 and
  backend 3.2 are complete. **Progress:** Not started.

When starting work, move exactly one small deliverable here and include:

- owner/agent;
- intended files;
- acceptance criteria;
- dependencies or decisions still needed.

## Remaining work

Items are listed in recommended dependency order. IDs are stable references for agents
and change-log entries.

### Phase B1 — Supabase and environment foundation

- [x] **B1.1** Create a hosted Supabase development project and record its project
  reference and selected database region. Create production as a separate project
  before release.
- [x] **B1.2** Add Supabase Kotlin Auth, PostgREST, and Functions modules plus the
  Android Ktor engine.
- [x] **B1.3** Add guarded Android client initialization using `SUPABASE_URL` and
  `SUPABASE_PUBLISHABLE_KEY` from Gradle properties or environment variables.
- [x] **B1.4** Install/configure the Supabase CLI and initialize a repository-local
  `supabase/` workspace for migrations, seed data, Edge Functions, and local services.
- [x] **B1.5** Link the CLI to the development project without committing access tokens
  or database passwords.
- [x] **B1.6** Define environment configuration and secret handling. Never expose a
  secret key, `service_role` key, PIN pepper, database password, or access token in the
  Android app or repository.
- [x] **B1.7** Verify an Android debug build can initialize the configured development
  client and perform a harmless authenticated health/read operation.

### Phase B2 — Postgres data model and migrations

- [x] **B2.1** Document canonical tables, primary/foreign keys, ownership, timestamps,
  archival behavior, constraints, transaction boundaries, and schema versions.
- [x] **B2.2** Define tenant-scoped `shops`, `user_profiles`, and memberships/roles tied
  to Supabase Auth user IDs.
- [x] **B2.3** Define products, immutable inventory lots, and append-only inventory
  movements with database constraints.
- [x] **B2.4** Define sales, sale lines, payments, discounts, lot allocations, and
  returns with foreign keys and uniqueness constraints.
- [x] **B2.5** Define vendors, purchase bills, bill lines, payments, dues, and vendor
  returns.
- [x] **B2.6** Define cash/bank accounts, expenses, deposits, withdrawals, and transfers
  as balanced, auditable ledger entries.
- [x] **B2.7** Define notifications and immutable audit records.
- [ ] **B2.8** Define SQL views/materialized summaries only after authoritative
  transactional records are specified.
- [x] **B2.9** Specify Nepal business-date handling. Store authoritative `timestamptz`
  values and derive business dates using `Asia/Kathmandu` rules.
- [x] **B2.10** Implement all schema changes as versioned SQL migrations and add
  deterministic non-production seed data.

### Phase B3 — Authentication and authorization

- [x] **B3.1** Finalize how a user ID and PIN maps to a Supabase Auth identity. Keep PIN
  hashes in a private, non-client-readable table.
- [ ] **B3.2** Implement a production Supabase Edge Function with salted/peppered PIN
  verification and a safe Supabase Auth session-establishment flow.
- [ ] **B3.3** Add login rate limiting, failed-attempt tracking, temporary lockout, and
  generic error responses.
- [ ] **B3.4** Store application role and `shop_id` in authoritative membership/profile
  rows; keep JWT claims minimal and refresh-safe.
- [ ] **B3.5** Replace `PreviewAuthRepository` with Supabase Functions/Auth integration.
- [ ] **B3.6** Implement Super Admin creation, disabling, and PIN reset for Owners.
- [ ] **B3.7** Implement Owner creation, disabling, and PIN reset for Salesmen.
- [ ] **B3.8** Define secure reauthentication and offline-session behavior.
- [x] **B3.9** Enable Row Level Security on every exposed table and write policies for
  authentication, tenant isolation, role permissions, immutable rows, and allowed
  field changes.
- [ ] **B3.10** Add local integration tests proving allowed and denied access for every
  role, including attempts to cross `shop_id` boundaries.

### Phase B4 — Transactional backend operations

- [ ] **B4.1** Implement validated, tenant-safe product/SKU creation and updates.
- [ ] **B4.2** Implement purchase receipt creation and immutable FIFO lot creation.
- [ ] **B4.3** Implement atomic sales with price validation, FIFO allocations, inventory
  movements, payment records, and totals.
- [ ] **B4.4** Define and implement negative-stock policy, shortage records, and Owner
  notification.
- [ ] **B4.5** Implement idempotency keys to prevent duplicate sales, purchases,
  returns, and payments during retries.
- [ ] **B4.6** Implement returns against original sales and allocations, including
  partial-return limits and refund records.
- [ ] **B4.7** Implement damage, loss, and manual adjustment workflows with reason,
  actor, and audit trail.
- [ ] **B4.8** Implement vendor bill, payment, due, and vendor-return transactions.
- [ ] **B4.9** Implement cash/bank expenses and transfers using balanced, auditable
  ledger mutations.
- [ ] **B4.10** Use database timestamps and server-authoritative authenticated identity
  for all financial and inventory mutations.

### Phase B5 — Android data integration

- [ ] **B5.1** Introduce dependency injection and production repositories for Supabase
  Auth, PostgREST/RPC, and Edge Functions.
- [ ] **B5.2** Add ViewModels and explicit loading, success, empty, and error states.
- [ ] **B5.3** Connect dashboard summaries to authorized tenant data.
- [ ] **B5.4** Replace static action cards with navigation and functional screens.
- [ ] **B5.5** Implement product, stock, sale, return, vendor, cash/bank, user, report,
  and notification screens.
- [ ] **B5.6** Add Room as an explicit Android offline cache/outbox and define offline
  reads, queued writes, retry behavior, idempotency, and conflict UX for every mutation.
- [ ] **B5.7** Prevent the preview repository from being included in release builds.

### Phase B6 — Reports, notifications, and operations

- [ ] **B6.1** Implement trusted sales, gross-profit, stock, vendor, and cash/bank
  reports from authoritative records or verified aggregates.
- [ ] **B6.2** Implement notification creation, read state, delivery, and retention.
- [ ] **B6.3** Add Postgres indexes based on measured query plans and document why each
  is needed.
- [ ] **B6.4** Add Supabase local-stack integration tests for successful operations,
  authorization failures, duplicate retries, concurrency, partial returns, and
  insufficient stock.
- [ ] **B6.5** Establish logging, monitoring, alerting, backups, restore testing, and
  retention policy.
- [ ] **B6.6** Establish SQL migration, data repair, seed-data, and deployment
  procedures using the Supabase CLI.
- [ ] **B6.7** Add release signing, obfuscation review, secure CI/CD, and staged rollout.

## Known issues and decisions

- **Launch decision:** APKs built at this milestone are development/test artifacts,
  not production-release candidates. Production launch remains blocked by static feature
  screens, missing persistence/synchronization, incomplete feature integration, and missing release
  signing/rollout controls.
- **Feature integration pending:** Room read persistence now exists, but dashboard values
  and feature screens remain empty-state shells until Task 5 repositories map hosted DTOs
  into snapshots. Account management is now functional; the other feature routes remain
  empty-state shells. Dashboard cards navigate through role-gated typed routes.
- **Shop mutation scope:** Task 5.1 lists RLS-visible shops but does not create/archive
  them. The hosted backend has no protected first-release shop mutation contract, and
  direct authenticated table writes remain correctly revoked. Add a separately reviewed
  Edge/RPC transaction before exposing those controls.
  The Task 4.6 outbox transport is wired, but feature repositories must call it only for
  the documented supported operations and keep all other mutation controls online-only.
- **Offline mutation policy:** only product management and notification read state may
  queue. Sales, purchase receipts, returns, stock adjustments, vendor financial events,
  ledger entries, and account administration require a live connection. Terminal rows
  are retained for owner-scoped resolution; no backend message or credential is stored.
- **Hosted development backend:** project `zniqkuwktvincjndcgpu` (`Gdad Bags`) is in
  Northeast Asia (Seoul). Migrations match through `20260722224500`; hosted lint is
  clean; `pin-login`, `manage-users`, and `manage-accounts` are deployed. A managed
  Super Admin and correct-PIN refreshable session have been verified. Android feature
  integration is still pending. Migration history matches through `20260724101500` and
  linked database lint is clean.
- **Hosted Auth configuration pending:** do not run `supabase config push` until the
  local-only Auth `site_url` and redirect URLs are replaced with the agreed Android
  deep-link/callback configuration. Hosted signup settings have not yet been verified.
- **Local database verification:** Docker or another compatible container runtime is
  not installed on the current machine. SQL grammar was checked locally; migration,
  RLS, and pgTAP execution is delegated to the committed GitHub Actions workflow.
- **Client library caveat:** `supabase-kt` is community-maintained. Pin and test upgrades;
  do not assume API compatibility across releases.
- **Security design:** every tenant-owned row must carry `shop_id`; RLS and protected
  database/Edge Functions must derive the authoritative tenant and actor from the
  authenticated identity rather than trusting client input.
- **Inventory design:** purchases/manual additions create immutable lots; mutations
  should be represented by append-only events. Sales retain exact lot allocations so
  returns can restore their source lots.
- **Negative stock:** current domain allocation reports shortages, but the business
  policy, persisted shortage record, accounting behavior, and notification path remain
  undecided.
- **Environment separation:** the Seoul project is development only. Create a distinct
  production Supabase project and credentials before release.

## Code map

- `app/src/main/java/com/gdad/bags/MainActivity.kt` — Android entry point.
- `app/src/main/java/com/gdad/bags/ui/GdadApp.kt` — login and static role dashboards.
- `app/src/main/java/com/gdad/bags/data/auth/ProductionAuthRepository.kt` and
  `SupabaseAuthDataSources.kt` — serialized production PIN/session/identity flow.
- `app/src/main/java/com/gdad/bags/data/auth/EncryptedSessionManager.kt` — Android
  Keystore AES-GCM Supabase Auth storage; preferences contain ciphertext only.
- `app/src/main/java/com/gdad/bags/GdadApplication.kt` and `di/AppContainer.kt` — explicit
  application-scoped production/test dependency boundary.
- `app/src/main/java/com/gdad/bags/data/remote/SupabaseClientFactory.kt` — guarded,
  stateless Supabase Auth/PostgREST/Functions client construction.
- `app/src/main/java/com/gdad/bags/data/remote/RemoteDtos.kt` — typed hosted
  function/query transport contracts.
- `app/src/main/java/com/gdad/bags/data/remote/RemoteContracts.kt` — bounded execution,
  error/retry classification, one auth-refresh retry, and sanitized diagnostic metadata.
- `app/src/main/java/com/gdad/bags/data/local/CacheEntities.kt` and `CacheDao.kt` —
  tenant/user-owned Room offline read/outbox schema and owner-filtered Flow queries.
- `app/src/main/java/com/gdad/bags/data/local/RoomCacheDatabase.kt` and
  `RoomCacheStore.kt` — explicit migrations, transactional snapshot replacement, and purge.
- `app/src/main/java/com/gdad/bags/data/local/CacheSynchronizer.kt` — serialized typed
  remote refresh with last-good-cache retention.
- `app/src/main/java/com/gdad/bags/data/local/MutationOutbox.kt`, `OutboxWorker.kt`, and
  `data/remote/SupabaseOutboxDispatcher.kt` — durable queue policy, processing, and RPC dispatch.
- `app/schemas/com.gdad.bags.data.local.RoomCacheDatabase/1.json` and `2.json` — committed Room schemas.
- `docs/offline-cache.md` — ownership, refresh, storage, and migration contract.
- `app/src/main/java/com/gdad/bags/domain/auth/Authentication.kt` — authentication
  repository/use-case interfaces and domain result.
- `app/src/main/java/com/gdad/bags/ui/auth/AuthViewModel.kt` — injected authentication
  ViewModel and immutable Compose UI state.
- `app/src/main/java/com/gdad/bags/ui/navigation/AppNavigation.kt` — typed routes,
  complete role policy, visible navigation model, and fail-closed external URI policy.
- `app/src/main/java/com/gdad/bags/ui/components/SharedStates.kt` — accessible reusable
  loading, empty, error, ready, retry, and confirmation UI.
- `app/src/main/java/com/gdad/bags/data/account/`, `domain/account/`, and `ui/account/` —
  Task 5.1 protected account directory, mutations, Room store, ViewModel, and screens.
- `docs/android-architecture.md` — Task 4.1 dependency ownership and layer rules.
- `app/src/main/java/com/gdad/bags/domain/model/Models.kt` — initial domain models.
- `app/src/main/java/com/gdad/bags/domain/inventory/FifoAllocator.kt` — pure FIFO
  allocation and restoration logic.
- `app/src/test/java/com/gdad/bags/domain/inventory/FifoAllocatorTest.kt` — inventory
  unit tests.
- `app/build.gradle.kts` — Android and client dependency configuration.
- `supabase/config.toml` — local Supabase service and Auth configuration.
- `supabase/migrations/20260715084551_core_foundation.sql` — initial tenant, identity,
  product, FIFO lot, movement, privilege, and RLS schema.
- `supabase/migrations/20260715121500_authentication_contract.sql` — Argon2id PIN
  verifier and external pepper-version constraints.
- `supabase/migrations/20260715143000_pin_login_lockout.sql` and
  `20260715151000_pin_login_prepare_conflict_fix.sql` — private counters and atomic RPCs.
- `supabase/migrations/20260724101500_disabled_user_rls_lockdown.sql` — fail-closed
  public-row visibility for disabled profiles with stale sessions.
- `supabase/functions/pin-login/` — strict hosted PIN verifier and Auth session exchange.
- `supabase/functions/deno.json` / `deno.lock` — pinned Edge imports and verification task.
- `supabase/functions/tests/pin-login/core.test.ts` — request, HMAC, identity-isolation,
  Argon2id, and source-fingerprint tests.
- `supabase/tests/database/pin_login_lockout.test.sql` — privilege/rate/lock/reset pgTAP.
- `supabase/tests/database/authentication_contract.test.sql` — B3.1 credential schema
  and privilege tests.
- `docs/authentication.md` — authoritative user-ID/PIN mapping and session contract.
- `supabase/tests/database/core_foundation.test.sql` — pgTAP structure, RLS, and
  privilege tests.
- `supabase/tests/database/authorization_matrix.test.sql` — complete principal,
  cross-shop, RPC-grant, forged-write, and immutable-row authorization coverage.
- `docs/authorization-matrix.md` — canonical table/RPC permission matrix and coverage
  map.
- `.github/workflows/database-tests.yml` — Docker-backed database verification in CI.
- `supabase/integration-tests/` — real multi-session backend concurrency setup, runner,
  and final invariant verification outside pgTAP discovery.
- `docs/backend-phase3-exit-gate.md` — Phase 3 evidence, coverage, and Android handoff.
- `package.json` / `pnpm-lock.yaml` — pinned Supabase CLI tooling.
- `build-apk.ps1` — local test/build/APK copy workflow.
- `README.md` — project overview and build instructions.

## Latest verification

### 2026-07-28 — Task 5.2 product catalog vertical slice

- Status: Complete.
- Room schema v4 adds `low_stock_threshold` through explicit `MIGRATION_3_4`; product and
  stock snapshots remain user/tenant owned, transactionally replaced, and purged on
  identity change. Search covers name, SKU, and barcode while archived rows remain visible.
- Owner receives FIFO-lot stock value and create/edit/archive controls. Salesman does not
  query or render cost and cannot invoke mutations. Invalid/missing-tenant calls fail before
  networking; backend RLS/RPC remains authoritative.
- Every mutation carries one UUID across manual retry and the durable product outbox.
  PostgREST SQL states are classified before generic HTTP 400 so validation, permanent-code
  duplicate, archive lifecycle conflict, and unauthorized outcomes use actionable fixed text.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 9m50s: 67 tests across 16 suites, zero failures/errors; release
  APK is 55,984,523 bytes; lint has zero errors and 17 pre-existing warnings.
- `git diff --check` passed; only Git's expected LF-to-CRLF worktree notices were emitted.

### 2026-07-28 — Task 5.1 account management vertical slice

- Status: Complete.
- `SupabaseAccountRemoteDataSource` reads only RLS-visible shops/profiles/memberships and
  invokes `manage-users`/`manage-accounts` with typed exact bodies and response checks.
  The repository repeats hierarchy, UUID, PIN, active-shop, and same-shop target checks
  before network calls; backend authorization remains authoritative.
- Room schema v3 adds owner/user-scoped managed-account and shop tables through explicit
  `MIGRATION_2_3`. Refresh replaces the directory transactionally; logout/identity switch
  purges it. PINs, actor PINs, tokens, response bodies, and backend messages are not stored.
- Super Admin sees shops/Owners and may create/manage Owners. Owner sees and manages only
  same-shop Salesmen. Salesman receives a denial state and no create/disable/reset control.
  Create, disable, re-enable, and reset use safe forms/confirmations; retry retains the
  exact request UUID. Success mentions immutable audit completion and refresh revocation.
- `verifyReleaseAuthSafety testDebugUnitTest --no-daemon --offline --max-workers=1`
  passed 56 tests across 13 suites with zero failures/errors. Coverage includes success,
  denial, invalid input, cross-shop, offline/retry, same-key ViewModel retry, role UI,
  cache purge, and v2-to-v3 migration.
- The first release assembly completed and produced a 55,869,835-byte unsigned APK; the
  clean cached rerun passed all 51 tasks. Full lint passed 30 tasks with zero errors and
  the same 17 pre-existing warnings.

### 2026-07-28 — Task 4.7 navigation and shared UI states

- Status: Complete; Phase 4 exit gate passed.
- Stable Navigation Compose/testing `2.9.8` provides serializable `DashboardRoute` and
  `FeatureRoute`. The authenticated graph is keyed by user/role/shop; both navigation
  requests and route rendering enforce `NavigationPolicy` before feature content appears.
- Super Admin, Owner, and Salesman visible destinations exactly match their allowlists.
  No deep link is registered, and the explicit PIN-only external-navigation policy rejects
  null, custom-scheme callback, and web feature URI attempts.
- NavController state save/restore returns to the typed Products route after simulated
  process recreation; back returns to Dashboard. Logout uses a consistent confirmation.
  Shared loading/empty/error states expose semantic labels and tested refresh/retry controls.
- `verifyReleaseAuthSafety testDebugUnitTest --no-daemon --offline --max-workers=1`
  passed 45 tests across 10 suites with zero failures/errors. `assembleRelease` passed
  51 tasks and produced a 55,673,227-byte unsigned APK. Full `lint` passed 30 tasks with
  zero errors and the same 17 pre-existing warnings.
- Navigation and Compose test artifacts were downloaded only into the ignored Gradle
  cache. Initial focused runs exposed one missing import, one incomplete test-runtime
  resolution, and test-harness state reuse; all were corrected before the full green gates.

### 2026-07-28 — Task 4.6 durable mutation outbox

- Status: Complete.
- Room schema v2 adds an indexed owner-scoped outbox with operation, JSON payload,
  stable UUID idempotency key, timestamps, attempt state, next attempt, and safe error kind.
  Explicit 1-to-2 migration SQL is registered; destructive migration remains forbidden.
- WorkManager `2.11.2` uses unique connected-network work and exponential 30-second
  backoff. The processor recovers stale claims, caps retries at five/six hours, stops on
  validation/conflict/authorization, and never stores exception or response messages.
- Product management reuses the durable key in the backend idempotency ledger;
  notification reads use idempotent upsert behavior. Risky financial/inventory/admin
  operations are rejected as online-only. Logout/identity changes purge old work, while
  same-owner read refresh preserves it. Permanent owner-scoped failures publish a safe
  dashboard notice.
- Fresh unit-test XML records 37 tests across eight suites with zero failures/errors.
  This includes file-backed close/reopen persistence, duplicate suppression, same-key
  retry, permanent failure, migration SQL, snapshot preservation, identity isolation,
  and WorkManager constraint/backoff coverage.
- `verifyReleaseAuthSafety assembleRelease --no-daemon --offline --max-workers=1`
  passed 51 tasks and produced a 55,048,640-byte unsigned APK. `lint --no-daemon
  --offline --max-workers=1` passed 30 tasks with zero errors and 17 pre-existing warnings.
- One initial release attempt required downloading WorkManager's official AndroidX
  transitive artifact into the ignored Gradle cache. An overlong in-process compiler
  daemon was stopped before the successful clean cached build; no binary was committed.

### 2026-07-28 — Task 4.5 Room cache and synchronization model

- Status: Complete.
- Added stable Room `2.8.4`, Room Gradle schema export, KSP `2.3.7`, and JVM Room tests.
  The initial schema contains `cache_identity` plus nine minimum offline read tables; all
  business primary keys and queries include owner user and tenant keys.
- `RoomCacheStore` replaces complete snapshots in one transaction, validates every row's
  owner after deletion so mismatch failures prove rollback, and exposes owner-filtered
  Flows. `CacheSynchronizer` retains the last good snapshot on classified remote failure.
- Production authentication activates the authoritative user/shop only after validation
  and purges cache state on logout, identity failure, user/shop change, or missing identity
  marker. Remote logout failure still performs one local cache purge.
- Final `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 29 tests across
  six suites with zero failures.
- Isolated `assembleRelease` passed 51 tasks including release Room generation, auth
  safety, and lint-vital; the unsigned APK is 54,408,036 bytes. Isolated `lint` passed 30
  tasks with zero errors and the same 17 pre-existing warnings.
- Gradle's first KSP download repeatedly stalled. The five exact missing artifacts were
  downloaded only into ignored Gradle cache paths; the 83,459,632-byte KSP engine was
  checked against its official SHA-1 before use. No dependency binary was committed.

### 2026-07-28 — Task 4.4 typed remote client and error mapping

- Status: Complete.
- Added typed PIN-login/profile/membership/shop DTOs with exact hosted names and
  serialization coverage, plus a reusable remote result/error/retry contract.
- Remote execution applies a 15-second bound, preserves caller cancellation, distinguishes
  validation, unauthorized, conflict, offline, timeout, rate limiting, and unknown errors,
  and retries an authenticated unauthorized operation exactly once after Supabase session
  refresh. Diagnostics contain no request, response, exception message, token, or PIN.
- Repositories expose fixed safe messages plus typed `OperationErrorKind`; transport DTOs
  and SDK exceptions remain inside the data layer.
- Final `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 21 tests across
  five suites with zero failures.
- `assembleRelease lint --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 76 tasks,
  produced a 53,764,594-byte unsigned release APK, and reported zero lint errors with the
  same 17 pre-existing warnings.

### 2026-07-28 — Task 4.3 release authentication safety

- Status: Complete.
- Deleted the production-source preview repository and removed user-ID prefix role inference.
- Added `verifyReleaseAuthSafety`, automatically attached to `preReleaseBuild`, to require
  `ProductionAuthRepository` in the composition root and reject preview adapters, prefix-role
  inference, Supabase secret/service-role keys, and hard-coded numeric PIN assignments.
- `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed all 12 tests.
- `assembleRelease --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process
  --max-workers=1` passed 50 tasks, including the safety gate and release lint-vital, and
  produced the unsigned release APK.
- `lint --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process
  --max-workers=1` passed full Android lint with zero errors.
- Initial combined verification attempts were interrupted by sandbox network/temp access and
  command timeouts; no project failure was hidden. The successful offline split runs above are
  the authoritative result.

### 2026-07-28 — Task 4.2 production authentication state machine

- Status: Complete.
- `AuthRepository` now owns login, restored-session validation, and logout. Injected use
  cases drive an initialization-aware `AuthViewModel`; Compose shows an explicit secure
  session loading state and receives callbacks only.
- Added an Android Keystore AES-GCM `SessionManager` with ciphertext-only preferences,
  SDK auto-load/save/refresh configuration, and a non-secret persisted installation UUID.
  Production repository wiring now invokes hosted `pin-login`, imports the returned
  token pair only into Supabase Auth, reloads the Auth subject, derives role/shop solely
  from RLS-protected profile/membership/shop rows, validates restored sessions, and
  clears local Auth state on identity failure or logout. Compilation remains pending.
- Added deterministic repository tests for normalized request construction, local input
  rejection, sanitized credential errors, cleanup after post-import identity failure,
  authoritative restore, and logout. Local Auth clearing now runs in a non-cancellable
  block after remote sign-out, with repository fallback coverage when sign-out fails.
  Installation-ID/storage failures also become safe unavailable results, and the
  dashboard exposes a disabled `Logging out…` state. Full verification remains pending.
- Repository tests use one explicitly synthetic PIN constant; no operator/user PIN was
  retained in source, generated artifacts, status, or documentation.
- Updated the authentication, Android architecture, and README handoff to reflect the
  production binding and encrypted session boundary; Task 4.3 retains only preview-class
  removal. Final build/lint and optional on-device hosted confirmation remain.
- The first complete gate passed 12 tests, APK assembly, and lint with zero errors.
  Encrypted/session preference writes now use the AndroidX KTX API with synchronous
  durability where Auth requires it. The final cleanup gate again passed all 12 tests;
  lint reports zero errors, 17 pre-existing warnings, and no Task 4.2 warning.
- After replacing all authentication test input with an explicitly synthetic PIN, the
  final offline unit suite again passed all 12 tests in 77 seconds.
- The debug build receives the expected hosted development URL and a valid publishable
  key without either value being printed. ADB did not return a connected-device list, so
  on-device UI confirmation was unavailable; the hosted correct-PIN subject/profile path
  remains independently verified and the Android success path is deterministic-test covered.

### 2026-07-28 — Task 4.1 explicit dependency-injection architecture

- Status: Complete.
- Added an application-owned dependency container, application-scoped lazy Supabase
  client construction, domain authentication interfaces/use case, injected
  `AuthViewModel`, lifecycle-aware UI state collection, and a deterministic fake
  repository unit test. The UI-facing container exposes use cases only, so fake graphs
  require no SDK client. No composable constructs or locates a repository.
- Preview authentication remains centralized in the production graph only as the
  temporary implementation scheduled for replacement in Task 4.2.
- Repository Gradle `testDebugUnitTest --no-daemon` compiled the debug application and
  passed all four unit tests, including deterministic fake injection. The final offline
  rerun after narrowing the fake container seam also passed in 68 seconds.
- Gradle `assembleDebug lintDebug --no-daemon` completed successfully. The APK is
  `71,687,841` bytes; lint reports zero errors and 17 non-blocking version/resource/icon
  warnings. The command exceeded the shell wait window only during teardown; both daemon
  logs record `BUILD SUCCESSFUL` and the APK/lint artifacts were written.

### 2026-07-27 — Task 3.9 and Phase 3 exit gate

- GitHub Actions run `30292372527` reached database testing after Edge checks,
  migration replay, deterministic seed/reset, lint, and all existing pgTAP suites passed.
- The run exposed a discovery conflict: `supabase test db` recursively executed the
  integration SQL files before the dedicated concurrency harness.
- The harness is now under `supabase/integration-tests/`, outside the pgTAP discovery
  tree. Both relocated SQL files parse with bundled `pglast`, the workflow parses with
  PyYAML, and `git diff --check` passes. Run `30292811757` then passed every standard
  gate and executed all parallel operations; its verifier exposed unsupported
  `min(uuid)` aggregation. The verifier now counts the retry row and selects its UUID in
  separate statements.
- GitHub Actions run `30293062132` passed Edge verification, all migrations from zero,
  deterministic seed/reset, database lint, every pgTAP suite, and the complete backend
  integration/concurrency harness on its first run for the final fix.
- Task 3.9 and the Phase 3 exit gate are complete. Hosted development schema remains
  unchanged; linked history matches every local migration through `20260728060000`, and
  linked lint reports `No schema errors found`.

### 2026-07-27 — Task 3.8 trusted business reports

- GitHub Actions run `30291547016` passed Edge checks, all migrations from zero,
  deterministic seed/reset, database lint, every prior pgTAP suite, and all 37 report
  reconciliation, role, tenant, Nepal-boundary, and query-plan assertions.
- `supabase db push --linked --dry-run` selected only
  `20260728060000_trusted_business_reports.sql`; the linked push applied it successfully.
- Hosted migration history matches through `20260728060000`; linked lint reports
  `No schema errors found`.

### 2026-07-27 — Task 3.7 atomic financial operations

- GitHub Actions run `30289339018` passed the initial financial migrations and 49
  assertions. Follow-up run `30289769906` passed the system-account bootstrap, expanded
  deterministic seed/reset, database lint, every prior suite, and all 52 final Task 3.7
  assertions.
- Linked dry runs selected only the expected Task 3.7 migrations before each push.
  Hosted history now matches through `20260728050000`.
- `supabase db lint --linked --level warning --fail-on error` reports
  `No schema errors found` after the final deployment.

### 2026-07-27 — Task 3.6 vendor financial operations

- GitHub Actions run `30288446946` applied every migration to fresh Postgres, verified
  the deterministic seed/reset, linted database functions, and passed all pgTAP suites,
  including all 59 vendor payment, return, due, and reversal assertions.
- `supabase db push --linked --dry-run` selected only
  `20260728010000_vendor_return_movement_types.sql` and
  `20260728020000_vendor_financial_operations.sql`; the subsequent linked push applied
  both successfully.
- `supabase db lint --linked --level warning --fail-on error` returned
  `No schema errors found`. `supabase migration list --linked` shows matching local and
  remote histories through `20260728020000`.

### 2026-07-24 — Task 2.5 balanced ledger implementation

- Added financial accounts, non-overlapping accounting periods, immutable journal
  transactions/entries, and expenses. Account natural sides and system-purpose identity
  are constrained; no writable balance exists.
- Deferred integrity requires at least two entries, exact debit/credit balance, valid
  same-shop typed business sources, expense amount equality, and exact reversal entries.
- Migration parses. A 28-assertion pgTAP suite now covers balance, derived amounts,
  sources, expense equality, reversal, period overlap, cross-shop inputs, RLS, and
  direct writes; count/parsing/fresh execution are pending. Migration is not deployed.
- First fresh run `30075992454` applied/linted the schema and reached assertion 9, where
  pgTAP required an explicit `numeric`-to-`bigint` cast for `sum(bigint)`. The fixture
  now casts the derived balance result; schema behavior is unchanged.
- Corrected run `30076199784` passed all migrations, lint, prior suites, and all 28
  ledger assertions. Hosted histories match through `20260724200000`; linked lint is clean.

### 2026-07-24 — Task 2.4 purchasing schema implementation

- Added Owner-only vendor, bill/line, receipt/line, vendor payment/allocation, and
  vendor return/line tables with same-shop composite foreign keys and idempotency.
- Deferred integrity checks reconcile posted bills, prevent over-receipt and overpayment,
  require exact receipt-to-FIFO-lot quantity/cost, fully allocate payments, and prevent
  cumulative vendor over-return. Normalized invoice references remain unique permanently
  per shop/vendor.
- Static parsing passed. Integrity review added bill-to-receipt cost equality, original-
  lot vendor-return cost equality, and payment revalidation after vendor credits.
  A 31-assertion pgTAP suite now covers structure, arithmetic, invoice normalization,
  receipt/lot reconciliation, limits, cross-shop inputs, RLS, and direct writes;
  assertion count matches and both SQL files parse. Fresh CI run `30075105709` passed
  all gates. Hosted histories match through `20260724170000`; linked lint is clean.

### 2026-07-24 — Task 2.3 sales schema implementation

- Added sales, immutable price/discount lines, exact FIFO cost allocations, payments,
  returns, return-to-original-allocation evidence, and refunds with same-shop composite
  foreign keys, idempotency, lifecycle/reversal metadata, RLS, and read-only client grants.
- Deferred integrity checks reconcile posted header/detail/allocation totals, full
  non-credit settlement, derived credit settlement, cumulative over-return limits, and
  refund caps. Approved D9 now restricts lot/movement/allocation cost evidence to Owners
  and Super Admins.
- SQL static parsing passed. Trigger operation handling and the authorization contract/
  baseline policy expectation were corrected for the new cost restriction. Executable
  31-assertion pgTAP coverage now exercises structure, reconciliation, constraints,
  role/shop visibility, and direct-write denial. First fresh run `30073969515` applied
  and linted the migration, then exposed nullable three-valued logic in the credit check;
  missing identity/due fields now fail closed. The two row-count failures shared that cause.
- Corrected run `30074252359` passed Edge checks, fresh migration application, database
  lint, all prior pgTAP, and all 31 Task 2.3 assertions. Hosted migration history matches
  through `20260724143000`; linked lint reports no schema errors.

### 2026-07-24 — Complete Task 2.2 policies D7–D11

- Product owner approved all remaining policy decisions. D7–D9 use the presented
  backdating/period, permanent product-code, and Salesman visibility rules. Conservative
  D10–D11 first-release defaults use whole-paisa/no-VAT posting, 90-day notifications,
  and no automatic audit deletion.
- Every D1–D11 section now records schema, RPC, permission, UI, and acceptance effects.
  Automated full-policy consistency and `git diff --check` passed; no migration changed.

### 2026-07-24 — Approve Task 2.2 policies D1–D3

- Product owner approved: no negative stock for any role; Owner-only price/discount
  override; and Owner-only identified credit sales with required due date and partial
  payments.
- The policy record maps each choice to schema constraints, atomic RPC behavior,
  permissions, UI behavior, and acceptance tests. D4–D11 remain explicitly pending;
  no migration changed.

### 2026-07-24 — Approve Task 2.2 policies D4–D6

- Product owner approved cash/bank-only settlement with split tender and no
  overpayment; Owner-only 30-day sale returns with sellable/damaged disposition and
  refund cap; and Owner-only purchasing with strict receipt/payment limits, unique
  vendor invoices, draft cancellation, and posted reversal/return.
- Schema, atomic RPC, authorization, UI, accounting, and acceptance consequences are
  recorded. D7–D11 remain pending; no migration changed.

### 2026-07-24 — Task 2.1 canonical model acceptance

- Added a single source-of-truth data dictionary covering identity, products,
  inventory, sales, returns, vendors, purchasing, payments, cash/bank journals,
  expenses, notifications, and audits.
- Each table records purpose, keys, tenant/RLS ownership, time/lifecycle, reversal,
  idempotency, mutation boundary, reconciliation, and expected query/index behavior.
- Balances and dues are explicitly derived; server-calculated posted totals have
  reconciliation rules. Eleven unresolved business-policy groups are isolated in the
  Task 2.2 decision register. Review added exact return-allocation evidence and the
  complete system control-account model required for balanced journals. Existing
  private security/control tables have an explicit lifecycle register; no schema changed.
- Deterministic coverage verification passed for 35 required objects, 29 unique
  detailed table headings, and decision groups D1–D11; `git diff --check` passed.

### 2026-07-24 — Task 1.6 authorization matrix implementation

- Added the canonical table/RPC permissions matrix and 42 executable assertions for
  anonymous, no-membership, active/disabled Salesman, correct/wrong-shop Owner, Super
  Admin, service-only RPCs, forged tenant writes, and immutable ledger rows.
- The audit identified and fixed stale-token self-profile/membership visibility for a
  disabled user through a forward-only RLS migration.
- Bundled `pglast` parsed the migration and pgTAP suite successfully. GitHub Actions
  run `30071770493` passed Edge checks, fresh migrations, database lint, and every
  pgTAP suite. Hosted migration history matches through `20260724101500`; linked lint
  reports no schema errors.

### 2026-07-24 — Task 1.5 hosted authentication acceptance

- Secure masked verification returned the exact managed Auth subject, proved the
  generated token was rejected on second redemption, refreshed the session to the same
  subject, and read the matching authenticated `super_admin` profile through RLS.
- PIN, generated token hash, access/refresh tokens, and Auth bodies remained memory-only.
  The ignored result stores only safe identifiers and four boolean checks.
- Hosted secret-name inspection confirmed `GDAD_LOGIN_DIAGNOSTIC_TOKEN` and
  `GDAD_BOOTSTRAP_TOKEN` are absent. CI runs `30070481674` and `30070939071` passed
  Edge verification, fresh migrations, lint, and all pgTAP suites.

### 2026-07-22 — Task 1.4 fresh CI and hosted deployment

- GitHub Actions run `29941998214` passed all Edge checks, applied all migrations to a
  fresh Supabase database, passed database lint, and passed every pgTAP suite including
  all 27 account-administration assertions.
- Linked dry-run selected only `20260722224500_account_administration.sql`; it applied
  successfully and local/remote migration histories match through that version.
- `manage-accounts` deployed successfully. A valid-shape request without a Bearer user
  returned `401 UNAUTHORIZED`; linked database lint reported no schema errors.

### 2026-07-22 — Identify hosted session-link response mismatch

- The masked diagnostic reached `auth-link-result`: hosted Auth returned success for
  admin link generation, but `pin-login` expected the client-library `properties`
  wrapper instead of the raw endpoint's top-level token hash.
- The temporary `GDAD_LOGIN_DIAGNOSTIC_TOKEN` was removed automatically. No PIN,
  token hash, session token, or Auth body was persisted.
- Current fix parses the raw response (with wrapper compatibility) and exchanges the
  token hash using the documented `email` verification type. Pinned Deno 2.4.0
  `deno task check` passed formatting, lint, both function type-checks, and all 20 tests.

### 2026-07-22 — Hosted Super Admin bootstrap and PIN-login diagnosis

- Hosted account creation completed with one Auth user, one profile, one private PIN
  credential, and one audit event; the one-time bootstrap secret was removed.
- Correct-PIN verification reached deployed `pin-login` but returned HTTP 503
  `SERVICE_UNAVAILABLE`; no PIN or session token was written to disk or logs.
- Current change adds safe, temporary-token-gated stage diagnostics. Pinned Deno 2.4.0
  `deno task check` passed formatting, lint, both function type-checks, and all 19 tests.
  The diagnostic `pin-login` build deployed successfully; the subsequent corrected
  masked verification succeeded as recorded above.

### 2026-07-22 — Hosted correct-PIN subject and profile verification

- The corrected `pin-login` returned a Supabase session for the exact managed Auth
  subject; an authenticated RLS read returned the matching `super_admin` profile.
- The secure helper persisted only safe identifiers and boolean verification evidence;
  the PIN and access/refresh tokens remained memory-only.
- A hosted secret-name check confirmed both one-time diagnostic/bootstrap secret names
  are absent. Task 1.3 is complete and Task 1.4 is now active.

### 2026-07-22 — Task 1.2

- Command: pinned Deno 2.4.0 `deno task check` from `supabase/functions`.
- Result: format and lint passed for nine files; `pin-login/index.ts` and
  `manage-users/core.ts` type-check passed; 11 tests passed, 0 failed.
- Coverage: valid/invalid normalized input, correct/wrong PIN, deterministic HMAC
  isolation, independent salts, pepper-version rejection, malformed PHC fail-closed,
  dummy/real shared verification, Argon2id parameters, and source fingerprint behavior.
- Hosted state was not changed; the shared helper remains local until the provisioning
  operation and its migration pass database/Edge verification.

### 2026-07-22 — Task 1.3 local verification

- Edge command: pinned Deno 2.4.0 `deno task check` from `supabase/functions`.
- Edge result: format/lint and both function type-checks passed; 16 tests passed and 0
  failed across managed-user parsing, shared PIN behavior, and PIN login behavior.
- SQL command: pglast parsed migration `20260721090000` and
  `account_provisioning.test.sql`; both parsed successfully. The pgTAP suite defines 27
  structure, privilege, idempotency, hierarchy, cross-shop, collision, finalization,
  credential, membership, and audit assertions.
- Linked dry run: `supabase db push --linked --dry-run` selected only migration
  `20260721090000_managed_user_provisioning.sql`; no hosted state changed.
- Local database runtime: Not run because Docker is unavailable. Fresh database CI is
  required before deployment or Task 1.3 completion.
- Repository review: `git diff --check` reported no whitespace errors; the scoped secret
  scan found no committed secret/service-role/pepper/bootstrap/access-token value.
- Publishing blocker: the required GitHub publishing workflow cannot proceed because
  GitHub CLI `gh` is not installed. No files were staged, committed, pushed, or deployed.
- Follow-up verification: after documenting the compensation contract and accepting
  empty responses from the failure-marking RPC, `deno task check` again passed all 16
  tests and the documentation contract assertion passed.

### 2026-07-22 — Task 1.1

- Documentation assertions confirmed the package, reserved exact callback, direct
  no-redirect PIN flow, Supabase `auth.importSession`, encrypted Android Keystore
  session manager requirement, process restoration, and logout policy are all present.
- UTF-8 validation passed for `docs/authentication.md` with no replacement characters.
- `supabase/config.toml` and hosted Auth redirects were intentionally not changed or
  pushed: PIN login needs no callback, and the reserved handler is disabled until Task
  4.7 implements and tests exact URI validation.
- Source/tests were not run because Task 1.1 changes documentation only; the Task 0.3
  Android, Deno, hosted-lint, and CI baseline remains current.

### 2026-07-22 — Tasks 0.1 and 0.2

- Repository: `main` at `1be9d33`, tracking `origin/main`; all expected runbook paths
  exist. Existing launcher and managed-user changes were preserved.
- Hosted migrations: `supabase migration list --linked` confirmed local/remote parity
  through `20260715151000`; local `20260721090000` is not deployed.
- Hosted Edge: `supabase functions list --project-ref zniqkuwktvincjndcgpu` confirmed
  `pin-login` ACTIVE at version 5.
- Encoding: a UTF-8 code-point scan of repository Markdown, Kotlin, SQL, and TypeScript
  found no replacement characters or mojibake sequences; six damaged status separators
  were repaired. The execution plan itself is valid UTF-8.
- External handoff: `gdad bag sales app.md` was not found on the Desktop; no claims were
  imported from an unavailable file.

### 2026-07-22 — Task 0.3

- Android: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\build-apk.ps1`
  completed `BUILD SUCCESSFUL` in 33 seconds; 44 tasks were up-to-date, unit tests
  passed, and `GDAD-BAGS-test.apk` was refreshed.
- Edge: pinned Deno 2.4.0 `deno task check` passed format, lint, `pin-login/index.ts`
  type-check, and five tests. One pre-existing untracked `manage-users/core.ts` format
  defect was repaired with `deno fmt` before the successful run.
- Database: Docker is not installed, so local reset/pgTAP was not run. Linked hosted
  lint completed with `No schema errors found`; GitHub Actions run `29423495797` for
  current commit `1be9d33` is completed/success.
- Tooling: portable Java 17.0.19, Android SDK/Build Tools 36, Gradle 9.4.1 build wrapper,
  Supabase CLI 2.101.0, and ignored Deno 2.4.0 are available.
- APK inspection: package `com.gdad.bags`, label `GDAD BAGS`, version `0.1.0`/1, and all
  five launcher densities are present. Size and SHA-256 remain 71,671,378 bytes and
  `193B0DAD57C939A804C0C7DE69AF2DFA58F51A88F80A2F0FC4FEC9FEE3EFE028`.

### 2026-07-21

- Command: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\build-apk.ps1`.
- Result: `BUILD SUCCESSFUL` in 53 seconds; all Android unit tests passed and the debug
  APK was assembled and copied to `GDAD-BAGS-test.apk`.
- Packaged icon check: Android `aapt dump badging` reports `GDAD BAGS` and resolves
  `ic_launcher.png` for mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi densities.
- APK: 71,671,378 bytes; SHA-256
  `193B0DAD57C939A804C0C7DE69AF2DFA58F51A88F80A2F0FC4FEC9FEE3EFE028`.
- Build note: the Kotlin daemon could not create a marker under the user profile, so
  Gradle used its supported non-daemon fallback; compilation and packaging succeeded.
- Not verified: installation on a physical Android device, visual launcher rendering,
  production authentication, persistence, functional feature screens, or release
  signing. This APK is for testing only.

### 2026-07-15

- Command: local bundled Gradle `testDebugUnitTest --no-daemon` with `JAVA_HOME`,
  `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `GRADLE_USER_HOME` pointed at `.tooling`.
- Result: `BUILD SUCCESSFUL` in 30 seconds; 26 tasks up-to-date.
- Tests: 3 executed previously, 0 failures, 0 errors, 0 skipped.
- APK: existing debug APK present at `GDAD-BAGS-test.apk` and
  `app/build/outputs/apk/debug/app-debug.apk`.
- Supabase foundation verification: `testDebugUnitTest --no-daemon --offline` completed
  successfully after dependencies were resolved; 26 tasks, 1 executed and 25 up-to-date.
- Packaging verification: `assembleDebug --no-daemon --offline` completed successfully
  in 47 seconds; 38 tasks, 5 executed and 33 up-to-date. The generated app-module debug
  APK contains the Supabase foundation.
- Export note: the root `GDAD-BAGS-test.apk` was not refreshed during this change; use
  `app/build/outputs/apk/debug/app-debug.apk` for this build or rerun `build-apk.ps1`.
- Not verified: hosted Supabase initialization, Android network behavior, device UI
  behavior, persistence, production authentication, business RPCs, and release build.
- Backend static verification: Supabase CLI `2.101.0` initialized the workspace; the
  migration and pgTAP file passed PostgreSQL grammar parsing with `pglast 8.2`; the
  GitHub Actions workflow parsed successfully with PyYAML 6.0.3; and
  `pnpm install --offline --frozen-lockfile` reported the lockfile up to date.
- Backend runtime verification: GitHub Actions run `29403029753` completed successfully
  for commit `dd9803d`. A fresh Postgres database applied the migration, database lint
  passed, and all 24 pgTAP structure/RLS/privilege assertions passed. Local Docker
  execution remains unavailable on this machine.
- Hosted deployment verification: `supabase migration list --linked` showed local and
  remote migration `20260715084551`; `supabase db lint --linked --level warning
  --fail-on error` completed with `No schema errors found` for `extensions`, `private`,
  and `public`.
- Android client configuration verification: ignored
  `.tooling/gradle-user-home/gradle.properties` contains the expected hosted project
  URL and a correctly formatted publishable key. Generated `BuildConfig` checks passed
  without printing the key. The final `build-apk.ps1` run succeeded in 2m23s with 44
  tasks (10 executed, 34 up-to-date) and refreshed `GDAD-BAGS-test.apk`.
- Hosted client connectivity verification: the client-key-authenticated Auth health and
  settings endpoints returned HTTP `200`; an anonymous `shops` read returned HTTP
  `401`, confirming the initial database deny policy remains effective.
- B3.1 verification: `supabase db push --linked --dry-run` selected only migration
  `20260715121500`; the hosted push applied it successfully; migration history shows
  matching local/remote versions through `20260715121500`; and hosted lint found no
  errors in `extensions`, `private`, or `public`. `build-apk.ps1` then completed in
  1m4s with all 44 Android tasks up-to-date. GitHub Actions run `29410151903` then
  applied both migrations to fresh Postgres, passed database lint, and passed the core
  plus seven-assertion authentication pgTAP suites.

- B3.2/B3.3 Edge verification: from `supabase/functions`, temporary pinned Deno 2.4.0
  ran `deno task check`; formatting, lint, full `index.ts` type-check, and all five tests
  passed. `pglast 8.2` parsed all migrations/tests and PyYAML parsed the CI workflow.
- Hosted database: migrations match through `20260715151000`; linked lint reports no
  errors in `extensions`, `private`, or `public`.
- Hosted Edge: `pin-login` is ACTIVE at version 5. Random pepper/dummy values were
  generated in memory and uploaded as secrets without being written or displayed.
- Hosted HTTP checks using the ignored local publishable key: malformed body returned
  `400`, invalid publishable key returned `401`, and valid-key unknown user returned the
  generic `401` after dummy Argon/RPC work.
- Hosted SQL diagnosis found and the forward migration fixed an ambiguous PL/pgSQL
  conflict target. No request, PIN, key, verifier, one-time token, or session token was
  logged or committed. Fresh-Postgres pgTAP/CI is pending the next push.
## Recommended next task

Proceed with **Task 5.3**, implementing the Owner-only vendor and purchase receipt vertical
slice: vendor lifecycle, purchase cart/invoice/payment forms, authoritative review/result,
stable retry protection, created FIFO-lot detail, and stock/vendor/account refresh.

## Change log

### 2026-07-28 — Complete Task 5.2 product catalog vertical slice
- Status: Complete.
- Changed: Product domain/remote/store/repository/ViewModel/Compose layers; DI, Main and
  typed navigation integration; Room product entity/schema v4/migration; remote SQL-state
  classification; repository, migration, ViewModel and role-visibility tests; README,
  product/architecture docs, and status.
- Behavior: Owner and Salesman search/view product and stock history. Owner alone sees cost
  and can create/edit/archive. Exact-key retries and transient offline queuing cannot create
  a second request; archived rows remain visible without mutation controls.
- Data/security impact: Local Room migrates 3-to-4. No hosted change; Android still has no
  direct product writes. RLS and `manage_product` authorize server-side; cached rows/outbox
  remain identity scoped and no backend error detail or credential is persisted/rendered.
- Verification: 67 tests in 16 suites passed; release safety, release assembly, and full
  lint passed; unsigned APK is 55,984,523 bytes; lint reports zero errors/17 warnings;
  `git diff --check` passed.
- Next: Task 5.3 vendor and purchase receipt vertical slice.

### 2026-07-28 — Complete Task 5.1 account management vertical slice
- Status: Complete.
- Changed: Account domain/repository/remote/store/ViewModel/UI layers; Room managed-account
  and shop entities/DAO/schema v3/migration; Main/DI/navigation integration; repository,
  migration, ViewModel, and Robolectric role-visibility tests; account docs, README, status.
- Behavior: Super Admin manages Owners across visible active shops; Owner manages only
  same-shop Salesmen. Creation and disable/re-enable/PIN reset call protected Edge
  Functions, refresh the Room directory, retain exact retry IDs, and give safe audit/session
  feedback. Salesmen cannot reveal or trigger administration.
- Data/security impact: Local Room schema migrated 2-to-3. No hosted change or direct shop
  write. PIN fields are transient only; directory rows are owner-scoped and purged on
  logout/identity switch. Backend RLS/RPC hierarchy remains authoritative.
- Verification: 56 tests in 13 suites passed; release safety plus 51-task release assembly
  passed; 30-task lint passed with zero errors and 17 pre-existing warnings;
  `git diff --check` passed.
- Next: Task 5.2 product catalog vertical slice.

### 2026-07-28 — Complete Task 4.7 and Phase 4 exit gate
- Status: Complete.
- Changed: Navigation Compose/testing dependencies; typed route/role/external policy;
  authenticated NavHost and clickable dashboard; shared state/confirmation composables;
  navigation and Robolectric Compose tests; README, architecture docs, and status.
- Behavior: Authenticated users receive only role-authorized destinations. Forged direct
  routes return to Dashboard without rendering protected content; external URIs remain
  unsupported for the PIN-only release. Back stacks restore after recreation, and every
  feature shell uses accessible empty/refresh UI with shared loading/error/retry patterns.
- Data/security impact: No hosted or local schema change. Navigation authorization is
  defense in depth; backend RLS/RPC checks remain authoritative. No deep link or alternate
  authentication path was introduced.
- Verification: 45 tests in 10 suites passed; release safety and 51-task release assembly
  passed; 30-task full lint passed with zero errors and 17 pre-existing warnings;
  `git diff --check` passed.
- Next: Task 5.1 account and shop management vertical slice.

### 2026-07-28 — Complete Task 4.6 durable mutation outbox
- Status: Complete.
- Changed: WorkManager dependency; Room outbox entity/DAO/database v2 migration/schema;
  queue, processor, worker, Supabase dispatcher, DI and dashboard resolution notice;
  local/migration tests; README, Android/offline architecture docs; and `PROJECT_STATUS.md`.
- Behavior: Safe offline product and notification-read mutations persist before
  confirmation, execute only with network, reuse stable keys, retry with bounded backoff,
  and retain permanent safe failure states. Risky operations reject offline queueing.
- Data/security impact: Local Room schema migrated 1-to-2. Owner/user isolation and
  logout purge extend to outbox rows. Payload credential names and oversized payloads
  are rejected; response/exception text and secrets are never persisted. Hosted state
  was unchanged.
- Verification: 37 tests in eight suites passed with zero failures/errors; release safety
  plus 51-task release assembly passed; 30-task full lint passed with zero errors and 17
  pre-existing warnings; `git diff --check` passed.
- Next: Task 4.7 app navigation and shared UI states.

### 2026-07-28 — Complete Task 4.5 Room offline read cache
- Status: Complete.
- Changed: root/app Gradle plugins and dependencies, `data/local/` Room entities/DAOs/
  database/store/synchronizer, authentication and DI cache integration, Room/auth tests,
  exported schema v1, README, Android architecture/offline-cache docs, and
  `PROJECT_STATUS.md`.
- Behavior: Nine minimum read models persist as tenant/user-scoped Room Flows. Complete
  remote snapshots replace cache state atomically; failures retain the last good snapshot;
  logout and identity/tenant changes purge before another identity can publish.
- Data/security impact: Local-only schema addition; no hosted change. Cache contains no
  PIN, token, key, verifier, or service credential. Destructive migrations are forbidden.
- Verification: 29 tests across six suites passed; release assembly and release safety
  passed; full lint reports zero errors and 17 pre-existing warnings. Room schema v1 is
  committed. Dependency download workarounds affected ignored Gradle cache only.
- Next: Task 4.6 durable mutation outbox and retry rules.

### 2026-07-28 — Complete Task 4.4 typed remote boundary
- Status: Complete.
- Changed: `data/remote/RemoteContracts.kt`, `RemoteDtos.kt`, production authentication
  data sources/repository, DI composition, domain authentication errors, remote/auth tests,
  README, Android architecture documentation, and `PROJECT_STATUS.md`.
- Behavior: Current Supabase function/query calls use typed DTOs and one reusable bounded
  executor. Failures retain category/retry semantics through safe domain results; an expired
  authenticated request refreshes and retries once; caller cancellation remains cancellation.
- Data/security impact: No hosted state changed. Raw backend/exception messages and request
  bodies are excluded from diagnostics and UI; no credential value was added or printed.
- Verification: 21 tests in five suites passed with zero failures; release safety and APK
  assembly passed; full lint passed with zero errors and 17 pre-existing warnings.
- Next: Task 4.5 Room cache and tenant-safe synchronization model.

### 2026-07-28 — Complete Task 4.3 release authentication safety
- Status: Complete.
- Changed: deleted `app/src/main/java/com/gdad/bags/data/auth/AuthRepository.kt`; updated
  `app/build.gradle.kts`, `README.md`, and `PROJECT_STATUS.md`.
- Behavior: Release source now contains only production Supabase authentication. Every release
  pre-build verifies the production binding and blocks preview/prefix authentication or embedded
  secret/service-role key and numeric-PIN patterns.
- Data/security impact: No hosted data changed. The change removes an unsafe local bypass and
  adds a fail-closed release check; no credential value was added or printed.
- Verification: Auth safety plus all 12 unit tests passed; `assembleRelease` passed all 50
  tasks including lint-vital; full Android lint passed with zero errors.
- Next: Task 4.4 typed remote client and safe domain error mapping.

### 2026-07-28 — Implement Task 4.2 production authentication repository
- Status: Complete.
- Changed: domain authentication contracts/use cases, `AuthViewModel.kt`, `GdadApp.kt`,
  `ProductionAuthRepository.kt`, `SupabaseAuthDataSources.kt`,
  `EncryptedSessionManager.kt`, `InstallationIdProvider.kt`, Supabase client/DI wiring,
  repository tests, README/authentication/architecture docs, and `PROJECT_STATUS.md`.
- Behavior: Implements normalized hosted PIN login, standard session import, automatic
  refresh, encrypted restore, authoritative identity/role/shop loading, initialization,
  safe failure states, and local-first logout. Unconfigured builds fail safely rather
  than constructing preview authentication.
- Data/security impact: No backend change. Tokens remain inside Supabase Auth/data code
  and are stored only as AES-GCM ciphertext; the key is non-exportable from Android
  Keystore. No PIN, token, key value, raw backend error, or credential is logged or
  committed. Disabled/inactive/ambiguous identities fail closed and clear local Auth.
- Verification: Offline `testDebugUnitTest assembleDebug lintDebug` recorded
  `BUILD SUCCESSFUL` in 3m45s with 12 tests, zero failures/errors, APK size 71,802,840
  bytes, zero lint errors, and no new Task 4.2 warning. A cleanup test/lint rerun also
  passed; local BuildConfig checks confirmed the expected development project and a
  publishable-key shape without printing either value. A final synthetic-input-only
  unit rerun passed all 12 tests in 77 seconds.
- Next: Implement Task 4.3 preview-auth removal and release-binding assertions.

### 2026-07-28 — Implement Task 4.1 Android architecture and dependency injection
- Status: Complete.
- Changed: `GdadApplication.kt`, `di/AppContainer.kt`, `domain/auth/Authentication.kt`,
  `data/auth/AuthRepository.kt`, `data/remote/SupabaseClientFactory.kt`,
  `ui/auth/AuthViewModel.kt`, `MainActivity.kt`, `ui/GdadApp.kt`, Android manifest and
  Gradle dependencies, `LoginUseCaseTest.kt`, `docs/android-architecture.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Moves concrete construction to an application composition root, owns one
  lazy Supabase client for the process, injects a domain use-case interface into the
  authentication ViewModel, and renders lifecycle-aware immutable state through
  callback-only composables. Production and deterministic fake graphs share interfaces.
- Data/security impact: No Supabase or persisted-data change. Client configuration still
  accepts only the public URL/publishable key. Preview auth is isolated to one temporary
  binding that Tasks 4.2 and 4.3 must replace/remove before release.
- Verification: Final offline `testDebugUnitTest` passed all four tests in 68 seconds.
  `assembleDebug lintDebug` recorded `BUILD SUCCESSFUL`; lint has zero errors and 17
  non-blocking warnings.
- Next: Implement Task 4.2 production authentication and secure session lifecycle.

### 2026-07-27 — Complete Task 3.9 backend integration and concurrency gate
- Status: Complete.
- Changed: `backend_concurrency_setup.sql`, `backend_concurrency.sh`,
  `backend_concurrency_verify.sql`, `.github/workflows/database-tests.yml`,
  `docs/backend-phase3-exit-gate.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds real parallel database sessions for competing one-unit FIFO sales,
  exact duplicate sale retry, over-allocating vendor payments, and no-overdraft expense
  debits. It then posts a partial return and proves report reconciliation, balanced
  journals, rollback cleanup, disabled-user denial, and cross-shop isolation.
- Data/security impact: Test/CI only; no production schema or hosted state changes.
- Verification: Both SQL files parse with bundled `pglast`, workflow YAML parses with
  PyYAML, and `git diff --check` passes. No Bash executable is available locally for
  `bash -n`. GitHub run `30292372527` passed every earlier gate but confirmed that SQL
  beneath `supabase/tests` is recursively discovered; the harness was relocated to
  `supabase/integration-tests` and its workflow paths were updated. Run `30292811757`
  reached the dedicated harness and exposed a verifier-only `min(uuid)` error after the
  parallel operations; UUID retrieval is now a separate query. Final run `30293062132`
  passed every workflow step, including the full integration/concurrency harness.
- Next: Implement Task 4.1 Android architecture and dependency injection.

### 2026-07-27 — Implement and deploy Task 3.8 trusted business reports
- Status: Complete.
- Changed: migration `20260728060000_trusted_business_reports.sql` and
  test `trusted_business_reports.test.sql`, `docs/trusted-reports.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one parameterized period report and a Nepal-instant daily wrapper.
  Owners receive reconciled sales/returns, FIFO COGS/profit, stock quantity/value, low
  stock, vendor due, cash/bank balances, and effective expenses. Salesmen receive only
  permitted sales/returns/stock/low-stock fields; cost/vendor/finance keys are absent.
- Data/security impact: Read-only security-definer RPCs re-derive active tenant role.
  Adds one targeted sale-return shop/date/status index; existing sales, expenses,
  journal, product, lot, vendor, and account indexes cover the remaining access paths.
- Verification: Migration/test parsing and static count match 37. GitHub Actions run
  `30291547016` passed Edge checks, fresh migration, deterministic seed/reset, database
  lint, every prior suite, and all 37 report assertions. It proves exact reconciliation,
  role/tenant/disabled/range denial, Nepal midnight, empty periods, and indexed plans for
  sales, returns, expenses, and balances. The linked dry run selected only this migration;
  hosted history matches through `20260728060000` and lint reports no schema errors.
- Next: Implement Task 3.9 backend integration and concurrency suite.

### 2026-07-27 — Implement and deploy Task 3.7 atomic financial operations
- Status: Complete.
- Changed: migrations `20260728030000_cash_movement_journal_kinds.sql` and
  `20260728040000_atomic_financial_operations.sql`,
  `20260728050000_system_financial_account_bootstrap.sql`, `supabase/seed.sql`, test
  `atomic_financial_operations.test.sql`, `docs/financial-operations.md`,
  `docs/business-policies.md`, `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds protected Owner-only expense, deposit/withdrawal, transfer, and exact
  compensating reversal RPCs with request fingerprints and authoritative results.
  Cash/bank balances remain derived; debiting operations and reversals lock accounts and
  reject overdrafts. Transfers post as one journal and expenses reconcile to evidence.
- Data/security impact: Adds deposit/withdrawal journal kinds and private retry state;
  direct table writes remain denied. A forward bootstrap creates 11 protected system
  accounts for existing/future shops; deterministic seed mode preserves fixed fixture
  IDs and now includes every required purpose. The previously undocumented funds
  behavior is explicitly conservative for first release: balances cannot go below zero.
- Verification: All migrations, seed, and the final test parse; static count matches 52.
  GitHub Actions run `30289769906` passed Edge checks, fresh migration, expanded
  deterministic seed/reset, database lint, every prior suite, and all 52 assertions.
  Linked dry runs selected only expected migrations, hosted history matches through
  `20260728050000`, and final linked lint reports `No schema errors found`.
- Next: Implement Task 3.8 trusted dashboard and report queries.

### 2026-07-27 — Implement and deploy Task 3.6 vendor financial and return operations
- Status: Complete.
- Changed: migrations `20260728010000_vendor_return_movement_types.sql` and
  `20260728020000_vendor_financial_operations.sql`, test
  `vendor_financial_operations.test.sql`, `docs/vendor-operations.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds Owner-only fingerprinted RPCs for allocated cash/bank vendor payment,
  unpaid-value-capped original-lot vendor return, and payment/return reversal. Due is
  derived from posted bills minus effective allocations and returns; journals, stock,
  statuses, movements, notification, and audits commit atomically.
- Data/security impact: Bills, lots, vendors, periods, and request rows lock before
  consequences. Return/reversal movements are explicit; direct writes remain denied.
  Vendor returns cannot turn a bill into an overpayment—payments must be reversed first.
- Verification: Both migrations and the test parsed with bundled `pglast`; the suite
  declares 59 assertions covering multi-bill allocation, cash/bank, derived due,
  overpayment, original-lot and unpaid-value caps, payment/return reversals,
  compensating stock/journals, exact retries, role/tenant denial, notification, audit,
  rollback, and integrity helpers. Corrected GitHub Actions run `30288446946` passed
  Edge checks, fresh migration, deterministic seed/reset, database lint, all prior
  suites, and all 59 assertions. Linked dry run selected only both Task 3.6 migrations;
  hosted history now matches through `20260728020000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.7 expenses, deposits, withdrawals, and transfers.

### 2026-07-27 — Implement and deploy Task 3.5 atomic inventory adjustment operation
- Status: Complete.
- Changed: migrations `20260727230000_inventory_adjustment_journal_kind.sql` and
  `20260728000000_atomic_inventory_adjustment.sql`, test
  `atomic_inventory_adjustment.test.sql`, `docs/inventory-adjustment.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds an Owner-only reason-coded adjustment source and retry-safe RPC.
  Manual additions create new FIFO lots; damage, loss, and manual removal consume a
  specified lot without rewriting receipts. Every operation appends a movement and
  projection change, positive-cost operations post balanced inventory/clearing entries,
  and all successes create an Owner notification, safe audit, and authoritative result.
- Data/security impact: Explicit reason/type, direction, lot, quantity, cost, and journal
  constraints make reconciliation durable. Zero-cost stock creates no fabricated money
  entry. Direct writes and request internals remain denied; product/lot/request locks
  serialize conflicting or duplicate operations.
- Verification: Both migrations and the test parsed with bundled `pglast`; static
  counting corrected the declared plan to 51 assertions. The comprehensive pgTAP
  suite now covers new-lot addition, source-lot damage/loss/removal, exact cost journals,
  zero-cost treatment, immutable receipts, movement/projection reconciliation, retry,
  excessive rollback, reason/cost/source validation, missing accounts, role/RLS, tenant
  denial, notifications, and safe audits. CI run `30286240589` passed Edge checks, fresh
  migration, deterministic seed/reset, lint, all existing suites, and all 51 adjustment
  assertions. The linked dry run listed only both Task 3.5 migrations; hosted history now
  matches through `20260728000000`, and linked lint reports `No schema errors found`.
- Next: Implement Task 3.6 vendor payment, due, and vendor return operations.

### 2026-07-27 — Implement and deploy Task 3.4 atomic sale return and refund operation
- Status: Complete.
- Changed: migration `20260727220000_atomic_sale_return.sql`, test
  `atomic_sale_return.test.sql`, `docs/sale-return.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one Owner-only request-fingerprinted return RPC that enforces the
  30-day original-sale window, open Nepal business date, cumulative line/allocation
  limits, reverse allocation restoration, sellable/damaged disposition, server-derived
  return value, due-first credit reduction, mandatory paid-value refund, sale status,
  balanced journals, notification, audit, and authoritative result in one transaction.
- Data/security impact: The original sale row serializes concurrent returns; line,
  allocation, and lot locks are deterministic. Sellable quantities restore only their
  original lots, while damaged evidence never increases saleable stock. Direct writes
  remain denied and failed operations roll back all consequences.
- Verification: The migration and test parsed with bundled `pglast`; static counting
  corrected the declared plan to 53 assertions. The comprehensive pgTAP suite
  now uses genuine atomic-sale output to cover the 30-day boundary, repeated partial
  returns, damaged/sellable reverse allocation, exact restoration, due-first credit,
  paid-value refund cap, retry, over-return, rollback, window/role/tenant denial,
  journals, notifications, audits, and integrity helpers. Pre-CI review moved immutable
  request fingerprint locking/result replay ahead of mutable sale-state validation, so
  a completed retry cannot be rejected after its first call changed return quantities or
  sale status. CI run `30284003683` passed Edge checks, fresh migration, deterministic
  seed/reset, lint, all existing suites, and all 53 return/refund assertions. The linked
  dry run listed only this migration; hosted history now matches through
  `20260727220000`, and linked lint reports `No schema errors found`.
- Next: Implement Task 3.5 inventory adjustment, damage, and loss.

### 2026-07-27 — Implement and deploy Task 3.3 atomic FIFO sale operation
- Status: Complete.
- Changed: migration `20260727200000_atomic_fifo_sale.sql`, test
  `atomic_fifo_sale.test.sql`, `docs/fifo-sale.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one authenticated, request-fingerprinted sale RPC that applies D1–D4
  and D7/D10: tenant/role-derived authority, configured Salesman pricing, Owner-only
  overrides/discounts/credit, exact whole-paisa totals, split cash/bank payments,
  deterministic FIFO allocation with no negative stock, stock projection/movements,
  low-stock notification, balanced sale/COGS/payment journals, and safe audit/result.
- Security/data impact: Direct table mutation remains denied. Product rows lock in
  stable ID order before FIFO lots lock by `received_at,id`; request locks and unique
  consequence keys prevent retry duplication. Failed shortages or configuration errors
  roll back all sale, stock, money, notification, and audit effects.
- Verification: The migration and test parsed with bundled `pglast`; static counting
  corrected the declared plan to 50 assertions. The pgTAP suite
  now covers permissions, Owner pricing/discount/partial credit, multi-lot FIFO and exact
  cost, due/payment/journals, low stock/audit, retry mismatch, shortage rollback,
  Salesman rules, zero total, overpayment, cross-shop denial, missing-account rollback,
  and negative-stock invariants. CI run `30282328392` passed Edge checks, fresh
  migration, and deterministic seed/reset, then lint identified one variable qualifier
  that PL/pgSQL could not resolve in the FIFO query; pgTAP did not run. The product
  variable is now explicitly named `v_product_id` throughout. CI run `30282596268`
  then passed fresh migration, deterministic reset, and lint. pgTAP exposed two test
  expectation/input defects before a private-state assertion ran under `authenticated`
  and stopped the file: allocation output ordering, a null payments argument, and the
  assertion role were corrected without changing production behavior. CI run
  `30282947284` passed Edge checks, fresh migration, deterministic seed/reset, lint, all
  existing suites, and all 50 FIFO-sale assertions. The linked dry run listed only this
  migration; hosted history now matches through `20260727200000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.4 atomic sale return and refund.

### 2026-07-27 — Implement and deploy Task 3.2 atomic purchase receipt operation
- Status: Complete.
- Changed: migration `20260727180000_atomic_purchase_receipt.sql`, test
  `atomic_purchase_receipt.test.sql`, `docs/purchase-receipt.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds one Owner-only idempotent RPC that validates Nepal date/open period,
  vendor, products, accounts, lines, and optional payment before creating the received
  bill, receipt, exact FIFO lots/movements, stock projection, balanced journals, due,
  and safe audit in one transaction.
- Data/security impact: Tenant and actor authority are re-derived; private request
  fingerprints prevent changed retries and duplicate stock/money. Direct writes remain
  denied. This first operation deliberately accepts zero purchase discount/tax so FIFO
  unit cost and billed unit cost remain identical under the approved schema.
- Verification: Bundled `pglast` parsed the migration and test; static counting found
  and corrected the plan to 41 assertions. Review also made deterministic product locks
  explicit and executes the void journal-integrity helper through `lives_ok` for both
  journals. CI run `30280756573` passed fresh migration, deterministic seed/reset, lint,
  all existing suites, and the first seven new assertions, then exposed two test-only
  expected queries that began with bare `VALUES`, which pgTAP cannot open as cursors.
  Both expected datasets now begin with `SELECT`. Corrected CI run `30281161087` passed
  Edge checks, fresh migration, two-reset deterministic fixture verification, lint, all
  existing suites, and all 41 purchase assertions. The linked dry run listed only this
  migration; hosted history now matches through `20260727180000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.3 atomic FIFO sale.

### 2026-07-27 — Implement and deploy Task 3.1 atomic product management
- Status: Complete.
- Changed: migration `20260727163000_product_management.sql`, test
  `product_management.test.sql`, `docs/product-management.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds normalized SKU/barcode columns, permanent private code reservations,
  private request fingerprints, and one Owner-only create/update/archive RPC with
  archive guards, idempotent result replay, and safe business audit snapshots.
- Data/security impact: Shop intent is validated against active Owner membership;
  Salesman/cross-shop/direct writes remain denied. Old and archived codes stay reserved;
  no secret or credential metadata enters audit.
- Verification: Bundled `pglast` parsed the migration and test; static counting found
  and corrected the declared plan to 45 assertions. Review also corrected blank-barcode
  canonicalization and preserved a target UUID outside RLS for the cross-shop denial
  test. Run `30279053076` passed migration, two-reset seed determinism, cleanup, lint,
  and all prior suites, then stopped before assertion 1 because two pgTAP meta-functions
  were incorrectly combined as booleans. Direct catalog predicates now replace them;
  run `30279388635` then executed all 45 assertions with 44 passing. The only mismatch
  counted cross-shop storage under Owner B RLS (correctly seeing one row); that count is
  now performed in the test-administrator context. Corrected run `30279665131` passed
  Edge checks, fresh migrations, two-reset deterministic seed verification, database
  lint, all existing suites, and all 45 product assertions. Hosted migration history
  matches through `20260727163000`; linked lint reports `No schema errors found`.
- Next: Implement Task 3.2 atomic purchase receipt and FIFO creation.

### 2026-07-27 — Implement deterministic Task 2.7 development fixtures
- Status: Complete.
- Changed: `supabase/seed.sql`, `supabase/verify-dev-seed.sql`,
  `supabase/clear-dev-seed.sql`, `.github/workflows/database-tests.yml`, and
  `PROJECT_STATUS.md`.
- Behavior: Defines fixed two-shop fixtures for all roles, products/multiple FIFO lots,
  purchasing, a partially paid credit sale and return, stock movements, expense and
  transfer journals, and targeted/read notifications. CI resets twice and compares a
  canonical fixture hash before removing only dev rows for isolated pgTAP suites.
- Data/security impact: Local/reset only; Auth fixtures have empty passwords, reserved
  `.invalid` emails, and no PIN verifier. No hosted secret, PIN, hash, or customer data
  is present, and seed data is not being pushed to the hosted project.
- Verification: Bundled `pglast` parsed all seed tooling, PyYAML parsed the workflow,
  and `git diff --check` passed. GitHub run `30278308128` applied migrations from zero,
  loaded and integrity-checked the fixture graph, reset and reproduced the identical
  canonical hash, removed only fixture rows, passed database lint, and passed every
  pgTAP suite.
- Next: Implement Task 3.1 atomic product management operations.

### 2026-07-27 — Implement and deploy Task 2.6 notifications and immutable audit
- Status: Complete.
- Changed: migrations `20260724220000_notifications_audit.sql` and
  `20260727150000_notifications_audit_lint.sql`, test `notifications_audit.test.sql`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds exact 90-day immutable notification sources, user/role targeting,
  per-user first-read state, typed source validation, bounded expiry cleanup, and an
  append-only business audit contract.
- Data/security impact: Safe JSON validation recursively rejects credential-bearing
  keys; authenticated clients receive only targeted RLS reads and the protected
  mark-read RPC, while audit append and cleanup remain backend-only.
- Verification: Bundled `pglast` parsed the migration and 39-assertion test; assertion
  count matches the plan and `git diff --check` passed. GitHub run `30276338069`
  passed Edge checks, fresh migration, database lint, all preceding suites, and the
  first 23 new assertions, then exposed a self-referential notification SELECT policy
  that terminated that test backend. Run `30276657008` proved its row-local correction
  through assertion 31, then exposed the same unsafe helper shape in receipt SELECT
  RLS; receipt visibility now uses a direct `EXISTS` against the safe notification
  policy. Corrected run `30276915806` passed all fresh migration, Edge, lint, existing
  suites, and 39 new assertions. Hosted migration `20260724220000` then deployed and
  parity matched, but hosted lint exposed two non-failing function warnings. Forward
  migration `20260727150000` corrected function volatility and exhaustive return flow;
  run `30277296630` passed every gate. Hosted histories now match through that forward
  migration and linked lint reports `No schema errors found`.
- Next: Implement Task 2.7 deterministic development seed fixtures.

### 2026-07-24 — Implement and deploy Task 2.5 balanced ledger schema
- Status: Complete.
- Changed: migration `20260724200000_balanced_ledger.sql` and `PROJECT_STATUS.md`.
- Behavior: Defines chart of accounts, accounting-period controls, balanced immutable
  journals, exact reversals, typed source validation, derived balances, and expenses.
- Data/security impact: Adds five hosted Owner-only tenant tables, four enums, deferred
  integrity helpers, RLS/read grants, and no direct Android writes.
- Verification: Bundled `pglast` parsed migration/test. Run `30075992454` passed
  migration/lint; pgTAP stopped on the derived-balance type mismatch after 8 assertions.
  Explicit fixture cast applied; corrected run `30076199784` passed fresh migration,
  lint, every suite, and all 28 assertions. Hosted histories match through
  `20260724200000`; linked lint is clean.
- Next: Implement Task 2.6 notifications and immutable business-audit migration/tests.

### 2026-07-24 — Implement and deploy Task 2.4 purchasing schema
- Status: Complete.
- Changed: migration `20260724170000_vendors_purchasing.sql` and `PROJECT_STATUS.md`.
- Behavior: Defines vendor masters, immutable purchasing/receiving/payment/return
  evidence, exact FIFO source linkage, and derivable vendor due.
- Data/security impact: Adds nine hosted tenant tables, two enums, lot receipt lineage,
  deferred integrity helpers, Owner-only RLS reads, and zero direct Android writes.
- Verification: Bundled `pglast` parsed migration/tests; plan matches 31. GitHub run
  `30075105709` passed fresh migration, lint, all existing suites, and all 31 purchasing
  assertions. Hosted histories match through `20260724170000`; linked lint is clean.
- Next: Implement Task 2.5 balanced cash/bank ledger migration/tests.

### 2026-07-24 — Implement and deploy Task 2.3 sales schema
- Status: Complete.
- Changed: migration `20260724143000_sales_payments_returns.sql`, authorization matrix,
  core-foundation pgTAP policy expectation, and `PROJECT_STATUS.md`.
- Behavior: Defines server-reconciled sale totals, full FIFO allocation evidence,
  append-only payment/refund events, cumulative-safe returns, and cost-restricted reads.
- Data/security impact: Adds eight hosted tenant tables, five enums, deferred security-
  definer integrity helpers, RLS/read grants, and Owner-only lot/movement/cost visibility.
- Verification: Bundled `pglast` parsed all SQL. Run `30073969515` identified one
  nullable credit check; fail-closed correction run `30074252359` passed fresh migration,
  lint, all prior suites, and all 31 new assertions. Hosted histories match through
  `20260724143000`; linked lint is clean.
- Next: Implement Task 2.4 vendor and purchasing migration/tests.

### 2026-07-24 — Complete schema-affecting business policies
- Status: Complete for Task 2.2 decisions D1–D11.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Finalizes backdating/period locks, permanent normalized product codes,
  Salesman financial-data restriction, whole-paisa/no-VAT first-release arithmetic,
  notification expiry, and indefinite first-release audit retention.
- Data/security impact: Documentation only. Future schemas/RPCs must enforce closed
  periods, restricted cost/finance projections, server arithmetic, safe retention, and
  forward-only policy change.
- Verification: Automated consistency passed for all D1–D11 approvals in policy and
  dictionary plus schema, RPC, permission, and UI/acceptance consequences per decision;
  `git diff --check` passed.
- Next: Implement Task 2.3 sales, payment, allocation, and return migration/tests.

### 2026-07-24 — Record payment, return, and purchasing policies
- Status: Complete for Task 2.2 decisions D4–D6; D7–D11 pending.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Defines cash/bank split tender without overpayment, Owner-only 30-day
  returns with exact lot disposition and paid-value refund cap, and strict Owner-only
  purchasing/receiving/payment/reversal rules.
- Data/security impact: Documentation only. Future operations must use same-shop
  accounts, balanced journals, immutable compensation, derived dues, and protected
  Owner authority.
- Verification: Automated consistency passed for D1–D6 approval in both policy and
  dictionary, all four consequence categories per approved decision, and D7–D11
  pending coverage; `git diff --check` passed.
- Next: Obtain D7–D9 backdating, product-code, and Salesman visibility policies.

### 2026-07-24 — Record stock, pricing, and credit-sale policies
- Status: Complete for Task 2.2 decisions D1–D3; D4–D11 pending.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: First release blocks insufficient-stock sales, permits price/discount
  changes only for Owners, and permits identified due-dated credit sales only for
  Owners with append-only partial payments.
- Data/security impact: Documentation only. Policies require authoritative database
  roles, full FIFO allocation, protected Owner operations, and derived due values.
- Verification: Cross-checked each approved choice against its data-dictionary objects
  and execution-plan Tasks 2.2, 2.3, 3.3, and 3.4. Automated consistency passed for
  D1–D3 approval/consequence sections and D4–D11 pending coverage; `git diff --check`
  passed.
- Next: Obtain D4–D6 payment, return, and purchasing policies.

### 2026-07-24 — Publish canonical first-release data dictionary
- Status: Complete.
- Changed: `docs/data-dictionary.md` and `PROJECT_STATUS.md`.
- Behavior: Defines existing/planned authoritative tables, lifecycle, tenant/security
  ownership, atomic command boundaries, derived-value reconciliation, and expected
  indexes before transactional migrations begin.
- Data/security impact: Documentation only; no hosted or local schema changed. The
  model preserves service-only writes, same-shop composite references, append-only
  financial/stock/audit records, and forbids client-supplied authority or totals.
- Verification: Cross-checked against execution-plan Tasks 2.1–3.6 and current Phase 1
  schema. Deterministic coverage check passed for 35 required objects, 29 unique
  detailed table headings, and D1–D11; `git diff --check` passed.
- Next: Obtain and record Task 2.2 business-policy decisions, beginning with D1–D3.

### 2026-07-24 — Implement and deploy role and tenant authorization matrix
- Status: Complete.
- Changed: `docs/authorization-matrix.md`, migration
  `20260724101500_disabled_user_rls_lockdown.sql`,
  `supabase/tests/database/authorization_matrix.test.sql`, and `PROJECT_STATUS.md`.
- Behavior: Every exposed table and service RPC now has a documented permission rule
  and executable coverage. Disabled stale sessions are denied even self-profile and
  self-membership reads; direct/forged writes and immutable-row mutations are tested.
- Data/security impact: Adds `private.is_active_user()` and tightens profile/membership
  read policies without granting any new client table or RPC privilege.
- Verification: Bundled `pglast` parsed both new SQL files; assertion count equals the
  declared plan of 42; GitHub run `30071770493` passed Edge, fresh-migration, lint, and
  all pgTAP gates. Hosted histories match through `20260724101500`; linked lint is clean.
- Next: Begin Task 2.1 canonical data dictionary.

### 2026-07-24 — Close hosted PIN-login acceptance gate
- Status: Complete.
- Changed: hosted `pin-login`, ignored secure verifier, authentication contract, and
  `PROJECT_STATUS.md`.
- Behavior: Operator-only acceptance now proves exact subject, one-time exchange
  rejection, refresh continuity, and authenticated profile equality; normal login
  remains a single exchange with no diagnostic fields.
- Data/security impact: No PIN, token hash, access/refresh token, or Auth body was
  persisted or logged. Both temporary operator secret names are absent after cleanup.
- Verification: Local Deno passed 25 tests after safe-code refinement; CI runs
  `30070481674` and `30070939071` passed all gates; hosted safe result contains four
  true checks for subject, one-time rejection, refresh, and profile.
- Next: Execute Task 1.6 authorization matrix and fresh-database security tests.

### 2026-07-24 — Refine hosted Auth failure diagnosis
- Status: Partial; safe error-code parser implemented, verification/deployment pending.
- Changed: `pin-login` core/index/tests, authentication contract, and status.
- Behavior: Trusted diagnostics may append only a strictly sanitized upstream Auth
  machine code to the existing status stage; ordinary clients remain generic.
- Data/security impact: No Auth message/body, email, identifier, PIN, token, or secret
  is returned or logged. The prior temporary diagnostic secret was removed.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, all three
  function type-checks, and all 25 tests; `git diff --check` passed.
- Next: Verify/deploy and repeat the masked acceptance request once.

### 2026-07-24 — Add operator-only one-time exchange proof
- Status: Complete; deployed and proven against hosted Auth.
- Changed: `pin-login` core/index/tests, `docs/authentication.md`, and
  `PROJECT_STATUS.md`.
- Behavior: A trusted temporary diagnostic request performs a second redemption of the
  already-consumed generated email token and succeeds only when hosted Auth rejects it.
  Normal PIN login still performs exactly one token exchange.
- Data/security impact: Only a boolean proof may be returned to the trusted operator;
  token hashes, Auth bodies, PINs, and sessions remain server-only and unlogged.
- Verification: Local Deno passed 24 tests; CI run `30070481674` passed all gates;
  deployed function proved one-time rejection, refresh subject equality, and profile
  equality. Follow-up safe diagnostic CI run `30070939071` also passed.
- Next: Complete Task 1.6 authorization matrix and executable coverage.

### 2026-07-22 — Deploy and close Task 1.4 account administration
- Status: Complete.
- Changed: hosted project `zniqkuwktvincjndcgpu`, Task 1.4 migration/function/tests,
  administration contract, and `PROJECT_STATUS.md`.
- Behavior: Hosted backend now supports hierarchy-safe disable, re-enable, and PIN
  reset with reauthentication, rate limits, idempotency, session revocation, generic
  failures, immutable audit, and protected Super Admin targets.
- Data/security impact: Added private request/rate state and service-only RPCs; hosted
  smoke verification mutated no application row and anonymous access remains denied.
- Verification: Deno passed 23 tests; pglast parsed migration/tests; GitHub run
  `29941998214` passed fresh migration/lint/all pgTAP; hosted histories match, linked
  lint is clean, deployment succeeded, and anonymous smoke returned 401.
- Next: Execute Task 1.5 hosted PIN-login acceptance evidence, then replace Android
  preview authentication.

### 2026-07-22 — Implement Task 1.4 account-administration Edge handler
- Status: Partial; handler and local unit verification complete, database CI pending.
- Changed: `manage-accounts` core/index/config, function task config, parser tests, and
  `PROJECT_STATUS.md`.
- Behavior: Validates exact requests/project key/session, derives a source fingerprint,
  obtains a service-only reservation, verifies the actor PIN with the shared helper,
  hashes only reset PINs, atomically applies the action, and returns safe generic output.
- Data/security impact: No hosted change. PINs/verifiers/service credentials remain
  server-only; authorization and target role/shop remain database-derived.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, all three
  function type-checks, and all 23 tests.
- Next: Format/type-check/test Edge code, parse SQL, and run fresh-database CI.

### 2026-07-22 — Add Task 1.4 database acceptance suite
- Status: Partial; first CI run reached pgTAP and fixture-order correction is pending.
- Changed: `account_administration.test.sql` and `PROJECT_STATUS.md`.
- Behavior: Covers service-only privileges, hierarchy allow/deny paths, protected Super
  Admin targets, disable mutation, safe/unique audit, idempotent retry, same-shop Owner
  PIN reset, lock clearing, session-revocation statement, and atomic rate limiting.
- Data/security impact: Tests only; hosted state unchanged.
- Verification: pglast parsed the original suite. CI run `29941460301` applied and
  linted the migration successfully, then correctly denied a disabled Owner used by a
  later fixture. The corrected 27-assertion suite now re-enables that Owner first;
  CI run `29941756699` passed through that fix and all preceding assertions, then found
  the unavailable pgTAP `like` helper. It is replaced with supported `matches`; rerun
  pending.
- Next: Parse migration/tests, correct SQL issues, then implement `manage-accounts`.

### 2026-07-22 — Draft Task 1.4 account-administration database layer
- Status: Partial; forward migration drafted, verification and Edge integration pending.
- Changed: `20260722224500_account_administration.sql` and `PROJECT_STATUS.md`.
- Behavior: Added service-only idempotent prepare/fail/apply RPCs, authoritative
  Super-Admin-to-Owner and Owner-to-Salesman hierarchy derivation, per-actor/source
  reauthentication limits, disable/re-enable/PIN rotation, refresh-session deletion,
  last-Super-Admin protection, and append-only safe audit actions.
- Data/security impact: Migration not deployed. Private verifier/rate/request state is
  unavailable to Android; role/shop inputs remain server-derived; Super Admin targets
  are denied pending an explicit recovery path.
- Verification: pglast parsed the forward migration; runtime verification remains.
- Next: Add pgTAP coverage, parse SQL, then implement the Edge handler.

### 2026-07-22 — Define Task 1.4 account-administration contract
- Status: Partial; security/API design complete, implementation pending.
- Changed: `docs/account-administration.md` and `PROJECT_STATUS.md`.
- Behavior: Defined exact disable/re-enable/reset request shapes, authoritative
  hierarchy, actor-PIN reauthentication, rate limiting, idempotency, session revocation,
  last-Super-Admin protection, generic errors, audit content, and safe responses.
- Data/security impact: Documentation only. The design keeps role/shop derivation and
  verifier access server-side and forbids first-release Super Admin targets.
- Verification: Contract cross-checked against current profile, membership, login,
  verifier, RLS, session, and audit structures; executable verification is pending.
- Next: Add the administration state/RPC migration and pgTAP coverage.

### 2026-07-22 — Correct raw Auth session-link parsing
- Status: Complete.
- Changed: `pin-login` core/index/tests, `docs/authentication.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Correct-PIN session establishment now reads `hashed_token` from the raw
  GoTrue response rather than requiring the Supabase client wrapper and uses the
  documented email token-hash exchange contract.
- Data/security impact: Generated links and token hashes remain server-only. Normal
  failures stay generic, and the completed diagnostic secret was removed.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, both
  function type-checks, and all 20 tests; `git diff --check` passed; corrected
  `pin-login` deployed successfully; masked hosted login returned a subject-matched
  session and the authenticated profile matched the expected Super Admin.
- Next: Begin Task 1.4 account disable, re-enable, and PIN reset.

### 2026-07-22 — Add secure PIN-login stage diagnostics
- Status: Complete; the diagnostic identified the response-shape mismatch and its
  temporary hosted secret was removed.
- Changed: `supabase/functions/pin-login/core.ts`, `index.ts`, Edge tests,
  `docs/authentication.md`, ignored verifier helper, and `PROJECT_STATUS.md`.
- Behavior: A caller proving a temporary operator diagnostic secret may receive only a
  developer-authored failure stage; ordinary app callers retain the generic 503 body.
- Data/security impact: No PIN, token, Auth body, verifier, identifier, or database
  error is returned. The temporary hosted secret is designed for immediate removal.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, both
  function type-checks, and all 19 tests. Supabase CLI deployed `pin-login`
  successfully to project `zniqkuwktvincjndcgpu`; the token-gated stage identified
  `auth-link-result`, and the corrected masked login later passed.
- Next: Retain the inactive token-gated mechanism for safe operator troubleshooting;
  proceed with Task 1.4.

### 2026-07-22 — Adopt hosted Auth-generated provisioning subjects
- Status: Partial; local static/Edge verification passes, fresh database CI pending.
- Changed: forward migration `20260722154500_auth_generated_provisioning_subjects.sql`,
  `manage-users`, pgTAP coverage, `docs/account-provisioning.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Replaced cloud-blocked custom-ID Auth creation with supported collection
  creation. The idempotency request remains stable while a service-role-only RPC
  validates and attaches the Auth-generated UUID. Retries reconcile an ambiguous Auth
  response by deterministic internal email plus immutable request marker before any
  compensation or finalization.
- Data/security impact: No direct `auth.users` insert is used. Attachment/finalization
  prove exact email, marker, and subject; compensation deletes only that marked user.
  Existing failed reservations are safely reset to an unattached placeholder on retry.
- Verification: Hosted version 3 proved the root cause as
  `auth-user-create-403`; pglast parsed the forward migration and updated pgTAP suite;
  `deno task check` passed formatting, lint, type-checking, and all 17 Edge tests;
  `git diff --check` passed; linked dry-run selected only migration
  `20260722154500`. Local database runtime remains unavailable without Docker.
- Next: Require fresh CI, deploy the forward migration and function, then complete the
  controlled bootstrap.

### 2026-07-22 — Refine hosted Auth bootstrap diagnostics
- Status: Partial; local verification passes, deployment pending.
- Changed: `supabase/functions/manage-users/index.ts`,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Token-gated bootstrap diagnostics now distinguish managed Auth lookup,
  create-response status, conflict reconciliation, and invalid safe result stages.
- Data/security impact: Only developer-authored stage names and an upstream HTTP status
  may be returned to a proven bootstrap operator. Auth response bodies, emails, user
  identifiers, credentials, tokens, and request data remain excluded.
- Verification: `deno task check` passed formatting, lint, type-checking, and all 17
  Edge tests; `git diff --check` passed.
- Next: Publish, require CI, deploy, and repeat the controlled bootstrap to identify the
  exact Auth operation failure before implementing a fix.

### 2026-07-22 — Add token-gated bootstrap stage diagnostics
- Status: Complete; diagnostic deployed for controlled bootstrap troubleshooting.
- Changed: `supabase/functions/manage-users/core.ts`, `index.ts`, tests,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: A bootstrap caller that has already proven the one-time operator token may
  receive only the developer-authored failing stage with a generic operation error.
  Normal, unauthenticated, and invalid-token callers receive no stage detail.
- Data/security impact: No PIN, token, header, request body, identifier, verifier, or
  database error is returned. The prior hosted attempt was compensated: table statistics
  show one failed reservation and zero profiles, credentials, memberships, and audits;
  secret-name inspection confirms the bootstrap token was removed.
- Verification: `deno task check` passed formatting, lint, type-checking, and all 17
  Edge tests; `git diff --check` passed; GitHub Actions run `29934652041` passed Edge,
  fresh migration, database lint, and all pgTAP gates in 1m39s; `manage-users` version
  2 deployed successfully.
- Next: Repeat the masked bootstrap once and use the safe stage to fix the underlying
  hosted failure.

### 2026-07-22 — Deploy Task 1.3 provisioning backend
- Status: Partial; deployment and unauthenticated denial checks pass, controlled
  bootstrap plus role-path verification pending.
- Changed: hosted Supabase project `zniqkuwktvincjndcgpu`, ignored local secure-input
  helper `.tooling/bootstrap-superadmin.ps1`, `docs/account-provisioning.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Applied the managed-account provisioning migration and deployed
  `manage-users` version 1. The function now exposes the validated provisioning
  boundary while authenticated role operations remain unusable until the controlled
  first Super Admin bootstrap is completed.
- Data/security impact: Created server-only provisioning reservation/audit state and
  service-role-only RPCs. No application user, PIN verifier, bootstrap token, or audit
  event was created during deployment.
- Verification: Linked dry-run selected only migration `20260721090000`; push applied
  it; local/remote histories match through that migration; hosted lint found no schema
  errors; function list reports `manage-users` ACTIVE at version 1. Hosted HTTP checks
  returned `401` for an invalid project key and `401 UNAUTHORIZED` for a valid key
  without a user session. A masked local bootstrap prompt was cancelled because the
  operator is remote by phone; its process was terminated, no safe success record was
  produced, and hosted secret-name/digest inspection confirmed
  `GDAD_BOOTSTRAP_TOKEN` is absent. No credential value was printed or committed.
- Next: When secure local desktop input is available, run the masked helper, bootstrap
  once, confirm PIN-login subject equality, remove the one-time token, then verify
  authorized and denied hierarchy paths before Task 1.4.

### 2026-07-22 — Correct Task 1.3 pgTAP execution roles
- Status: Complete.
- Changed: `supabase/tests/database/account_provisioning.test.sql`,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Provisioning RPC acceptance checks still execute as `service_role`, while
  direct assertions against the intentionally protected `private` schema now execute
  only as the pgTAP test administrator.
- Data/security impact: None; production migration privileges are unchanged. The
  failure confirmed that `service_role` cannot directly inspect private credential or
  audit tables and must use the security-definer provisioning RPCs.
- Verification: GitHub Actions run `29921388428` applied all migrations and passed
  database lint, but stopped at assertion 21 because the test attempted a direct
  `private.login_credentials` read as `service_role`. After correction, pglast parsed
  the migration and test, `deno task check` passed formatting/lint/type-check plus all
  16 tests, and `build-apk.ps1` passed all 44 Android tasks. Replacement GitHub Actions
  run `29922170046` then applied all migrations to fresh Postgres, passed database
  lint, and passed all four pgTAP suites in 1m48s.
- Next: Deploy the verified migration and `manage-users` function to the linked
  development project, then complete hosted allowed/denied-path verification.

### 2026-07-22 — Implement execution-plan Task 1.3 account provisioning
- Status: Partial; repository and fresh-database checks pass, hosted deployment pending.
- Changed: local-only migration `20260721090000_managed_user_provisioning.sql`,
  `supabase/functions/manage-users/`, Edge/database tests and check configuration,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Added idempotent reservations/finalization for controlled Super Admin
  bootstrap, Super Admin→Owner, and Owner→Salesman creation, with authority rechecks,
  collision-safe managed Auth subjects, guarded compensation that never
  deletes unrelated Auth users, compensation state, and immutable safe audit.
- Failure handling: ambiguous finalization is reconciled before compensation, and the
  void failure-marking RPC accepts an empty response instead of falsely reporting a
  compensation failure.
- Data/security impact: Migration remains undeployed. Tables/RPCs are server-only;
  PIN/verifier/token/secret values are excluded from audit metadata.
- Verification: Deno checks passed 16 tests; SQL migration/test grammar passed; linked
  dry-run selected only the new migration; no secrets found. GitHub Actions run
  `29922170046` applied all migrations to fresh Postgres, passed database lint, and
  passed all pgTAP suites after the test-role correction.
- Next: Deploy the migration and `manage-users` function to development, verify hosted
  allowed/denied role paths, and only then begin Task 1.4.

### 2026-07-22 — Implement execution-plan Task 1.2 shared PIN helper
- Status: Complete.
- Changed: `supabase/functions/_shared/pin.ts`, `pin-login` and `manage-users` callers,
  `supabase/functions/deno.json`, Edge tests, and `PROJECT_STATUS.md`.
- Behavior: Centralized login-ID/PIN normalization, pepper version, HMAC material,
  Argon2id creation, random salting, and fail-closed verification for both login and
  provisioning.
- Data/security impact: Raw PINs and pepper remain function-memory-only; no hosted state
  changed and no secret/test PIN is intended for deployment.
- Verification: Deno format/lint/type-check passed; 11 tests passed and 0 failed.
- Next: Task 1.3 privileged idempotent account provisioning.

### 2026-07-22 — Complete execution-plan Task 1.1
- Status: Complete.
- Changed: `docs/authentication.md` and `PROJECT_STATUS.md`.
- Behavior: Selected one direct-token Android PIN session flow, specified secure import
  and encrypted persistence, and defined restore, refresh, expiry, revocation, logout,
  offline, concurrency, and future callback behavior.
- Data/security impact: No hosted state changed. The PIN-only flow exposes no browser or
  external intent; the future callback stays disabled and exact-match only.
- Verification: Documentation contract assertions and UTF-8 validation passed.
- Next: Task 1.2 shared PIN verifier helper and failure-path tests.

### 2026-07-22 — Complete execution-plan Task 0.3
- Status: Complete with local database-runtime delegation documented.
- Changed: formatted pre-existing `supabase/functions/manage-users/core.ts` and updated
  `PROJECT_STATUS.md`; installed ignored Deno 2.4.0 tooling.
- Behavior: Established a reproducible passing Android and Edge baseline before new
  feature work.
- Data/security impact: None; hosted database lint and GitHub CI inspection were
  read-only and no credential values were emitted.
- Verification: `build-apk.ps1` passed; `deno task check` passed five tests; hosted lint
  was clean; current-HEAD database CI run `29423495797` succeeded.
- Next: Task 1.1 Android Auth redirect and session strategy.

### 2026-07-22 — Complete execution-plan Tasks 0.1 and 0.2
- Status: Complete.
- Changed: `PROJECT_STATUS.md` only; unrelated launcher and provisioning work preserved.
- Behavior: Verified the canonical checkout and reconciled repository, hosted migration,
  Edge Function, code-map, and UTF-8 status against the supplied execution plan.
- Data/security impact: None; hosted checks were read-only and emitted no credentials.
- Verification: `git remote -v`, branch/log/path/status inspection, UTF-8 code-point
  scan, linked migration list, and hosted Edge Function list all completed.
- Next: Task 0.3 baseline Android, Deno, database-lint, and tooling verification.

### 2026-07-21 — Add branded Android launcher icon
- Status: Complete for launcher branding; app launch readiness remains blocked.
- Changed: `app/src/main/AndroidManifest.xml`, Android `drawable-nodpi`/`mipmap-*`
  launcher assets, `tools/generate_launcher_icons.py`, and `PROJECT_STATUS.md`.
- Behavior: Android launchers now display the supplied GDAD Bags branding for standard
  and round icon presentations.
- Data/security impact: None.
- Verification: `build-apk.ps1` passed all Android unit tests and assembled the debug
  APK. `aapt dump badging` confirmed the app label and all five packaged icon densities.
- Next: Install the test APK on an Android device and visually confirm both standard
  and round launcher treatments before replacing preview authentication.

### 2026-07-15 — Draft PIN login Edge Function and atomic lockout backend
- Status: Partial overall; B3.3 is implemented/hosted and awaits CI, while B3.2 lacks a happy-path fixture.
- Changed: `supabase/config.toml`, `supabase/functions/**`, migration
  `20260715143000_pin_login_lockout.sql`, forward fix `20260715151000`, pgTAP coverage,
  database CI, and this file.
- Behavior: Added strict request validation, publishable-key validation, HMAC-peppered
  Argon2id verification, timing-safe unknown-user work, Auth magic-link token exchange,
  per-source throttling, and atomic per-account failure/reset behavior.
- Data/security impact: Server credentials and peppers stay in Edge secrets; PIN hashes,
  source HMACs, and counters remain inaccessible to Android roles.
- Verification: Local Deno format/lint/type-check and five tests pass; SQL/YAML grammar pass.
  First hosted deploy exposed an import-map bundling mismatch; explicit pinned npm import applied.
- Diagnostic safety: Internal failures log only developer-authored stage messages; request/PIN
  values, server keys, one-time tokens, and session tokens are excluded.
- Hosted diagnostic: Deployed handler reaches PostgREST but `pin_login_prepare` fails; status-only
  logging added to distinguish RPC discovery/authorization/SQL failure without logging bodies.
- Root cause/fix: Hosted SQL verification found PL/pgSQL ambiguity in the conflict target.
  Forward migration `20260715151000_pin_login_prepare_conflict_fix.sql` now targets the
  named primary-key constraint and makes the unknown-user `source_limited` result non-null.
- Test expansion: pgTAP now provisions a synthetic Auth credential and asserts fifth-failure
  lockout plus success reset; fresh-Postgres execution is pending CI.
- CI run `29422667662` failed only at Deno lint because inline `npm:` imports are forbidden.
  Added function-local `pin-login/deno.json` so both repository lint and Supabase remote
  bundling resolve the pinned alias; local Deno and hosted bundling now pass.
- Hosted result: Both migrations deployed, remote lint is clean, function version 5 is
  ACTIVE, and malformed/invalid-key/unknown-user paths return `400`/`401`/generic `401`.
- CI run `29423183238` passed format/lint but current Deno rejected generic
  `Uint8Array<ArrayBufferLike>` Web Crypto key input. HMAC now copies identical bytes
  into an explicit `ArrayBuffer` for cross-Deno type compatibility; rerun pending.
- Verification gap: Correct-PIN Auth session establishment and one-time-token non-reuse
  require a managed user created by the upcoming privileged provisioning operation.
- Next: Push and pass fresh-Postgres/Deno CI, then implement B3.6 provisioning and use it
  for the B3.2 happy-path integration test before touching Android preview auth.

### 2026-07-15 — Finalize the production PIN authentication contract

- Status: Complete for B3.1; B3.2/B3.3 implementation pending.
- Changed: `docs/authentication.md`, `supabase/README.md`,
  `supabase/migrations/20260715121500_authentication_contract.sql`,
  `supabase/tests/database/authentication_contract.test.sql`, and `PROJECT_STATUS.md`.
- Behavior: Fixed the mapping from normalized app login IDs to immutable Supabase Auth
  subjects, selected a server-only one-time magic-link exchange for refreshable
  sessions, specified six-to-eight-digit PIN handling, HMAC peppering plus Argon2id,
  generic failures, session rules, and implementation acceptance tests.
- Data/security impact: PIN verifier rows now require Argon2id PHC format and store only
  the external pepper version. Pepper, PIN, one-time token, service credentials, and
  session tokens remain forbidden from client-readable storage and logs.
- Verification: Supabase dry-run selected only migration `20260715121500`; hosted push
  applied it; local/remote migration history matches; hosted lint found no schema
  errors; and the Android test/debug build passed in 1m4s. GitHub Actions run
  `29410151903` passed fresh migration application, database lint, and both pgTAP test
  files, including all seven new authentication assertions.
- Next: Implement B3.2/B3.3.

### 2026-07-15 — Configure and verify the Android Supabase development client

- Status: Complete for B1.7 client configuration and API-key connectivity verification.
- Changed: ignored `.tooling/gradle-user-home/gradle.properties`, `README.md`, generated
  debug APK, and `PROJECT_STATUS.md`.
- Behavior: Configured the Android debug build for hosted project
  `zniqkuwktvincjndcgpu` using its client-safe publishable key. The key value remains
  local and is not recorded in tracked files or this status document.
- Data/security impact: No hosted data changed. No secret/service-role credential was
  retrieved or stored. Row Level Security remains the security boundary for the
  publishable client key.
- Verification: Git confirmed the bundled Gradle user-home properties are ignored and
  zero tracked files contain the publishable key. Generated URL/key-format checks
  passed; `build-apk.ps1` succeeded in 2m23s; hosted Auth health/settings returned
  `200`; anonymous `shops` access returned `401` as intended.
- Next: Finalize the production PIN-to-Supabase-Auth identity design (B3.1).

### 2026-07-15 — Deploy and verify the hosted Supabase foundation

- Status: Complete for hosted project linking and the initial schema deployment.
- Changed: hosted project `zniqkuwktvincjndcgpu` and `PROJECT_STATUS.md`.
- Behavior: Linked the repository to the `Gdad Bags` development project in Northeast
  Asia (Seoul) and deployed migration `20260715084551_core_foundation.sql`.
- Data/security impact: Created the initial tenant, identity, product, FIFO inventory,
  movement, private PIN-verifier, privilege, and RLS structures in the hosted database.
  No credentials or tokens were committed. Hosted Auth config was deliberately not
  pushed while repository redirect URLs remain local-only.
- Verification: `supabase migration list --linked` showed matching local/remote
  migration `20260715084551`; remote database lint completed with no schema errors.
- Next: Supply the publishable key locally and complete B1.7 with a harmless
  authenticated client read.

### 2026-07-15 — Add Supabase database foundation and CI verification

- Status: Complete for the repository-side foundation; hosted project/link pending.
- Changed: `.gitignore`, `package.json`, `pnpm-lock.yaml`, `supabase/config.toml`,
  `supabase/seed.sql`, `supabase/README.md`,
  `supabase/migrations/20260715084551_core_foundation.sql`,
  `supabase/tests/database/core_foundation.test.sql`,
  `.github/workflows/database-tests.yml`, and `PROJECT_STATUS.md`.
- Behavior: Added the pinned CLI workspace, tenant/identity/product/FIFO schema,
  server-only PIN verifier storage, append-only inventory movement ledger, RLS helpers
  and policies, default-deny privileges, pgTAP coverage, and Docker-backed CI.
- Data/security impact: No hosted data changed. Anonymous access and direct Android
  mutations are denied; authenticated reads are tenant-scoped; PIN hashes remain in an
  unexposed private schema. Signup is disabled for admin-controlled account creation.
- Verification: SQL grammar parsed for the migration and test file; workflow YAML
  parsed; lockfile reinstall passed offline. GitHub Actions run `29403029753` then
  applied the migration to fresh Postgres, passed database lint, and passed all 24
  pgTAP assertions.
- Next: Create and link the hosted Supabase development project.

### 2026-07-15 — Connect workspace to canonical GitHub repository

- Status: Complete; canonical repository initialized and published on GitHub.
- Changed: `.gitignore`, Git repository metadata, and `PROJECT_STATUS.md`.
- Behavior: Initialized the workspace on `main`, configured
  `https://github.com/sanjubaba21/GDAD_APP.git` as `origin`, and excluded generated
  Kotlin state plus APK/AAB artifacts from source control.
- Data/security impact: No application data or credentials added. Generated binaries,
  local configuration, environment files, tooling, and build outputs remain ignored.
- Verification: The remote returned no refs before initialization. Initial commit
  `1d04635` was created after reviewing 22 staged source/configuration files and pushed
  successfully to `origin/main`; the local branch now tracks `origin/main`. Application
  tests were not rerun because this change only affects repository metadata and ignore
  rules.
- Next: Create the hosted Supabase development project (B1.1).

### 2026-07-15 — Select Supabase and add the Android client foundation

- Status: Partial; repository foundation complete, hosted project not yet created.
- Changed: `build.gradle.kts`, `app/build.gradle.kts`, `.gitignore`,
  `app/src/main/java/com/gdad/bags/data/remote/SupabaseClientProvider.kt`, `README.md`,
  and `PROJECT_STATUS.md`.
- Behavior: Replaced unused Firebase Auth/Firestore/Functions dependencies with
  Supabase Auth/PostgREST/Functions, Kotlin serialization, and Android Ktor. Added safe
  Gradle/environment configuration and guarded client initialization.
- Data/security impact: No database exists yet. Android accepts only the Supabase URL
  and publishable client key; secret and `service_role` keys are explicitly forbidden.
- Verification: Bundled Gradle `testDebugUnitTest --no-daemon --offline` succeeded in
  52 seconds and all 3 inventory tests remain passing. A subsequent
  `assembleDebug --no-daemon --offline` succeeded in 47 seconds. The first online
  resolution attempts timed out after downloading dependencies; orphan Gradle daemons
  were stopped before the successful offline verifications.
- Next: Create the hosted Supabase development project (B1.1), supply its URL and
  publishable key locally, and initialize the Supabase CLI workspace (B1.4).

### 2026-07-15 — Establish canonical project status and handoff

- Status: Complete
- Changed: `PROJECT_STATUS.md`
- Behavior: Added a verified implementation inventory, ordered backend/database
  backlog, agent update protocol, code map, risks, verification record, and next task.
- Data/security impact: None; documentation only.
- Verification: Content cross-checked against current source, Gradle configuration,
  README, test XML, build outputs, and a successful `testDebugUnitTest` invocation.
- Next: Originally Firestore schema design; superseded by the 2026-07-15 Supabase
  platform decision and the current Postgres backlog.
