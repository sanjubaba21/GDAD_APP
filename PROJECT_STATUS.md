# GDAD BAGS — Project Status and Agent Handoff

This is the canonical status file for the GDAD BAGS repository. Every developer or
agent must update this file in the same change as any source code, test, build,
configuration, database, security-rule, or backend change.

Last verified: 2026-07-15 (Asia/Kathmandu)
Current milestone: Android preview shell with Supabase client foundation; hosted backend not configured
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

The repository currently contains a buildable Android preview. Authentication is a
development stub, dashboards contain static action cards, and no operational feature
is connected to persistent data. Supabase Auth, PostgREST, and Functions client modules
and a guarded client provider are present, but no hosted Supabase project, Postgres
schema, migrations, Row Level Security policies, Edge Functions, or production
authentication are present.

Do not ship the current `PreviewAuthRepository`. It accepts any syntactically valid PIN
and derives the role from the user ID prefix.

## Completed work

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

## Work in progress

No implementation item is currently in progress.

When starting work, move exactly one small deliverable here and include:

- owner/agent;
- intended files;
- acceptance criteria;
- dependencies or decisions still needed.

## Remaining work

Items are listed in recommended dependency order. IDs are stable references for agents
and change-log entries.

### Phase B1 — Supabase and environment foundation

- [ ] **B1.1** Create a hosted Supabase development project and record its project
  reference and selected database region. Create production as a separate project
  before release.
- [x] **B1.2** Add Supabase Kotlin Auth, PostgREST, and Functions modules plus the
  Android Ktor engine.
- [x] **B1.3** Add guarded Android client initialization using `SUPABASE_URL` and
  `SUPABASE_PUBLISHABLE_KEY` from Gradle properties or environment variables.
- [ ] **B1.4** Install/configure the Supabase CLI and initialize a repository-local
  `supabase/` workspace for migrations, seed data, Edge Functions, and local services.
- [ ] **B1.5** Link the CLI to the development project without committing access tokens
  or database passwords.
- [ ] **B1.6** Define environment configuration and secret handling. Never expose a
  secret key, `service_role` key, PIN pepper, database password, or access token in the
  Android app or repository.
- [ ] **B1.7** Verify an Android debug build can initialize the configured development
  client and perform a harmless authenticated health/read operation.

### Phase B2 — Postgres data model and migrations

- [ ] **B2.1** Document canonical tables, primary/foreign keys, ownership, timestamps,
  archival behavior, constraints, transaction boundaries, and schema versions.
- [ ] **B2.2** Define tenant-scoped `shops`, `user_profiles`, and memberships/roles tied
  to Supabase Auth user IDs.
- [ ] **B2.3** Define products, immutable inventory lots, and append-only inventory
  movements with database constraints.
- [ ] **B2.4** Define sales, sale lines, payments, discounts, lot allocations, and
  returns with foreign keys and uniqueness constraints.
- [ ] **B2.5** Define vendors, purchase bills, bill lines, payments, dues, and vendor
  returns.
- [ ] **B2.6** Define cash/bank accounts, expenses, deposits, withdrawals, and transfers
  as balanced, auditable ledger entries.
- [ ] **B2.7** Define notifications and immutable audit records.
- [ ] **B2.8** Define SQL views/materialized summaries only after authoritative
  transactional records are specified.
- [ ] **B2.9** Specify Nepal business-date handling. Store authoritative `timestamptz`
  values and derive business dates using `Asia/Kathmandu` rules.
- [ ] **B2.10** Implement all schema changes as versioned SQL migrations and add
  deterministic non-production seed data.

### Phase B3 — Authentication and authorization

- [ ] **B3.1** Finalize how a user ID and PIN maps to a Supabase Auth identity. Keep PIN
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
- [ ] **B3.9** Enable Row Level Security on every exposed table and write policies for
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
- **No persistence:** dashboard values and feature cards are static; app state is lost
  when the process is recreated.
- **No hosted backend/database:** there is no Supabase project, Postgres schema,
  migration, RLS policy, Edge Function, or live Android connection yet.
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
- **Git metadata:** repository history was not available through `git` during the
  2026-07-15 audit, so status is based on the current working tree.

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
- `build-apk.ps1` — local test/build/APK copy workflow.
- `README.md` — project overview and build instructions.

## Latest verification

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
- Not verified: hosted Supabase initialization, network behavior, device UI behavior,
  persistence, RLS policies, backend operations, and release build.

## Recommended next task

Complete **B1.1: create the Supabase development project**, then initialize the local
CLI workspace under **B1.4**. Next, complete **B2.1: canonical Postgres schema design**
as versioned migrations with tenant boundaries, authoritative timestamps, immutable
financial/inventory records, keys, constraints, and transaction boundaries.

## Change log

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
