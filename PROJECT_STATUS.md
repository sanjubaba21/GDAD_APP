# GDAD BAGS — Project Status and Agent Handoff

This is the canonical status file for the GDAD BAGS repository. Every developer or
agent must update this file in the same change as any source code, test, build,
configuration, database, security-rule, or backend change.

Last verified: 2026-07-24 (Asia/Kathmandu)
Current milestone: Execution plan Task 2.4 — vendors and purchasing migration
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
client foundation. Android authentication remains a development stub and no Android
feature is connected to persistent data. The hosted development project
`zniqkuwktvincjndcgpu` in Seoul has repository migrations through
`20260724143000`, deployed `pin-login`,
`manage-users`, and `manage-accounts` Edge
Functions, private rate/credential/provisioning/administration state, and clean
hosted lint. Strict malformed,
invalid-key, and unknown-user failure paths are verified against the hosted function.
The local Android debug environment has the project URL and publishable key in ignored
Gradle properties. One managed Super Admin Auth identity/profile/credential exists; its
correct-PIN session and authenticated RLS profile read are verified. Android still uses
preview authentication, so production authentication is not end-to-end complete.

Do not ship the current `PreviewAuthRepository`. It accepts any syntactically valid PIN
and derives the role from the user ID prefix.

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

### Android foundation

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
- [x] Guarded `SupabaseClientProvider` installs Auth, PostgREST, and Functions only when
  the required configuration is present.
- [x] Firebase Auth, Firestore, and Functions dependencies removed. Firebase is not the
  primary backend; FCM may be evaluated separately for push notifications later.
- [x] Portable JDK, Android SDK, Gradle tooling, and `build-apk.ps1` build flow prepared.
- [x] Debug APK generated at `GDAD-BAGS-test.apk`.
- [x] GDAD Bags artwork configured as the standard and round Android launcher icon
  across all supported density buckets.

### Preview authentication and navigation shell

- [x] User ID and numeric PIN login UI implemented.
- [x] Preview validation requires a nonblank user ID and a 4–8 digit PIN.
- [x] `UserRole` supports `SUPER_ADMIN`, `OWNER`, and `SALESMAN`.
- [x] Preview role routing implemented: `admin*` becomes Super Admin, `sales*` becomes
  Salesman, and other IDs become Owner.
- [x] In-memory `UserSession` model includes user ID, display name, role, shop ID, and
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

- [x] README documents the preview milestone, build process, architecture direction,
  and production authentication warning.
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

- **Owner:** Codex. **Task:** 2.4, add vendors and purchasing schema.
  **Files:** versioned migration, pgTAP security/constraint coverage, schema docs, and
  `PROJECT_STATUS.md`. **Acceptance:** receipt creates reconcilable FIFO source evidence,
  vendor due is derivable, invoice uniqueness and receipt/payment/return limits are
  constrained, cross-shop/direct immutable mutations fail, and fresh CI passes.
  **Dependencies:** Tasks 2.1–2.3 and approved D6 are complete.
- **Preserved inactive work:** uncommitted managed-user provisioning files and shared
  PIN helper from the previous task remain in the working tree. They will be evaluated
  in Phase 1 and are not part of Phase 0 reconciliation.

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
- [ ] **B2.5** Define vendors, purchase bills, bill lines, payments, dues, and vendor
  returns.
- [ ] **B2.6** Define cash/bank accounts, expenses, deposits, withdrawals, and transfers
  as balanced, auditable ledger entries.
- [ ] **B2.7** Define notifications and immutable audit records.
- [ ] **B2.8** Define SQL views/materialized summaries only after authoritative
  transactional records are specified.
- [x] **B2.9** Specify Nepal business-date handling. Store authoritative `timestamptz`
  values and derive business dates using `Asia/Kathmandu` rules.
- [ ] **B2.10** Implement all schema changes as versioned SQL migrations and add
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

- **Production blocker:** `PreviewAuthRepository` does not verify a stored PIN. It must
  never ship.
- **Launch decision:** APKs built at this milestone are development/test artifacts,
  not production-release candidates. Production launch remains blocked by preview
  authentication, static feature screens, missing persistence, and missing release
  signing/rollout controls.
- **No persistence:** dashboard values and feature cards are static; app state is lost
  when the process is recreated.
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
- **Encoding defect:** several UI strings in `GdadApp.kt` are mojibake, including the
  ellipsis, Nepalese rupee text, and bullet separators. Fix before user testing.
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
- `app/src/main/java/com/gdad/bags/data/auth/AuthRepository.kt` — authentication
  contract and unsafe development preview adapter.
- `app/src/main/java/com/gdad/bags/data/remote/SupabaseClientProvider.kt` — guarded
  Supabase Auth/PostgREST/Functions client initialization.
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
- `package.json` / `pnpm-lock.yaml` — pinned Supabase CLI tooling.
- `build-apk.ps1` — local test/build/APK copy workflow.
- `README.md` — project overview and build instructions.

## Latest verification

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
  assertion count matches and both SQL files parse. Fresh execution is pending;
  migration is not deployed.

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

Proceed with **Task 2.2** one decision group at a time, beginning with stock, pricing,
and credit-sale behavior (D1–D3). Record explicit product-owner choices before starting
the affected sales/purchasing migrations.

## Change log

### 2026-07-24 — Implement Task 2.4 purchasing schema
- Status: Partial; migration written, static validation/tests/CI pending.
- Changed: migration `20260724170000_vendors_purchasing.sql` and `PROJECT_STATUS.md`.
- Behavior: Defines vendor masters, immutable purchasing/receiving/payment/return
  evidence, exact FIFO source linkage, and derivable vendor due.
- Data/security impact: Adds nine tenant tables, two enums, lot receipt lineage,
  deferred integrity helpers, Owner-only RLS reads, and zero direct Android writes. No
  hosted change yet.
- Verification: Bundled `pglast` parsed the migration and 31-assertion pgTAP fixture;
  counted plan matches 31 and `git diff --check` passed. Fresh CI is next.
- Next: Parse/fix migration, add purchasing constraints/RLS pgTAP, and run fresh CI.

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
