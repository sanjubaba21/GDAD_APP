# GDAD BAGS — Project Status and Agent Handoff

This is the canonical status file for the GDAD BAGS repository. Every developer or
agent must update this file in the same change as any source code, test, build,
configuration, database, security-rule, or backend change.

Last verified: 2026-07-15 (Asia/Kathmandu)
Current milestone: B3.1 Auth contract implemented; PIN Edge Function next
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
client foundation. Authentication remains a development stub and no Android feature is
connected to persistent data. The hosted development project `zniqkuwktvincjndcgpu` in
the Seoul region is linked, and the first Postgres migration is deployed. Tenant-aware
RLS policies, pgTAP tests, database CI, and successful hosted lint verification are in
place. The local Android debug environment now has the project URL and publishable key
in the ignored bundled Gradle user-home properties. Generated debug constants and
client-key-authenticated hosted health/settings reads are verified; production user
authentication is not implemented.

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

## Work in progress

- Production `pin-login` Edge Function and atomic attempt/lockout behavior (B3.2-B3.3)
  are the next backend deliverable.
- Hosted Auth configuration has not been pushed because `supabase/config.toml` still
  contains local-only site and redirect URLs; finalize the Android redirect strategy
  before using `supabase config push`.
- Behavioral cross-shop role tests (B3.10) remain after the hosted/local Auth test
  fixtures are implemented.

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

- [ ] **B2.1** Document canonical tables, primary/foreign keys, ownership, timestamps,
  archival behavior, constraints, transaction boundaries, and schema versions.
- [x] **B2.2** Define tenant-scoped `shops`, `user_profiles`, and memberships/roles tied
  to Supabase Auth user IDs.
- [x] **B2.3** Define products, immutable inventory lots, and append-only inventory
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
- **No persistence:** dashboard values and feature cards are static; app state is lost
  when the process is recreated.
- **Hosted development backend:** project `zniqkuwktvincjndcgpu` (`Gdad Bags`) is in
  Northeast Asia (Seoul). Migration `20260715084551` is deployed and remotely linted.
  There is no Edge Function, production Auth flow, seed fixture, or user-authenticated
  Android feature integration yet.
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
- `supabase/tests/database/authentication_contract.test.sql` — B3.1 credential schema
  and privilege tests.
- `docs/authentication.md` — authoritative user-ID/PIN mapping and session contract.
- `supabase/tests/database/core_foundation.test.sql` — pgTAP structure, RLS, and
  privilege tests.
- `.github/workflows/database-tests.yml` — Docker-backed database verification in CI.
- `package.json` / `pnpm-lock.yaml` — pinned Supabase CLI tooling.
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
  1m4s with all 44 Android tasks up-to-date. The new seven-test pgTAP file awaits the
  GitHub Actions run triggered by this commit.

## Recommended next task

Proceed with **B3.2-B3.3: implement the production `pin-login` Edge Function and atomic
rate limiting/lockout** from `docs/authentication.md`. Prototype the one-time magic-link
token exchange first, then implement verifier and failure-path tests before deployment.

## Change log

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
  errors; and the Android test/debug build passed in 1m4s. The seven new pgTAP
  assertions are pending GitHub Actions after push.
- Next: Confirm pgTAP CI, then implement B3.2/B3.3.

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
