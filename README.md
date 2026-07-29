# GDAD BAGS

Native Android sales, stock, vendor and cash-management application for Nepal.

## Current milestone

- Android 12+ Kotlin and Jetpack Compose project
- Hosted user ID and PIN login with authoritative Owner, Salesman and Super Admin roles
- Role-specific dashboard navigation
- NPR display and Nepal-time product decisions
- FIFO lots, negative-stock shortage reporting and return restoration
- Supabase Auth, Postgres and Edge Functions client foundation ready for configuration
- Typed remote DTOs, bounded timeouts, auth-refresh retry, and sanitized error mapping
- Tenant/user-scoped Room offline read cache with transactional snapshot refresh
- Durable owner-scoped offline outbox for safe product/read-state changes
- Type-safe role-gated Compose navigation with reusable accessible screen states
- Functional role-aware Owner/Salesman account management through protected Edge Functions
- Searchable offline-backed product catalog with Owner-only create/edit/archive and cost visibility
- Owner-only vendor management and duplicate-proof purchase receipt workflow with authoritative totals
- Role-shaped stock, FIFO lot and movement views with protected Owner inventory adjustments
- Role-aware atomic FIFO point-of-sale cart with authoritative receipt and duplicate-proof retry
- Searchable Owner/Salesman sale history with original-line detail, Owner-only FIFO cost,
  and duplicate-proof partial return/refund posting against the original sale
- Owner vendor ledger with reconciled open bills, allocated cash/bank payments,
  original-lot purchase returns, immutable reversals, and authoritative due receipts
- Owner cash/bank ledger with derived balances, expense/deposit/withdrawal/transfer
  posting, immutable compensating reversals, exact retry keys, and authoritative receipts
- Trusted cached daily dashboards and Nepal-date period reports for Owner/Salesman, with
  true zero states, explicit cache age, and Owner-only cost/profit/vendor/finance values
- RLS-authorized 90-day notification feed for every role, with unread dashboard badge,
  category/detail routing, retention-aware Room cache, and idempotent offline mark-read

Authentication uses the hosted Supabase `pin-login` Edge Function. The Android app
imports the returned Supabase session, stores it with Android Keystore-backed AES-GCM,
and derives role and shop only from RLS-protected authoritative rows. Release builds
run an authentication safety check that rejects preview role inference, Supabase
secret/service-role keys, and hard-coded numeric PIN assignments in production source.

## Build without Android Studio

The portable JDK 17, Android SDK 36, Build Tools 36 and Gradle wrapper are installed
under the ignored `.tooling` directory. Run:

```powershell
.\build-apk.ps1
```

This runs the unit tests and creates `GDAD-BAGS-test.apk` in the project root.
Android Studio is not required.

## Supabase configuration

The app reads `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` from Gradle properties or
environment variables. Builds remain possible with blank values, but the Supabase
client cannot be used until both values are supplied. For the repository's bundled
`build-apk.ps1` flow, put the following in the ignored
`.tooling/gradle-user-home/gradle.properties` file. For Android Studio, use the user
Gradle properties file (`%USERPROFILE%\.gradle\gradle.properties` on Windows), or pass
the values as environment variables:

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_PUBLISHABLE_KEY=your-publishable-key
```

Only use a publishable/anonymous client key in the Android app. Never place a Supabase
secret key or `service_role` key in the repository, Gradle properties, APK, or device.

## Architecture direction

The application is divided into UI, data and domain layers. Inventory mutations are
append-only events. Purchases and manual additions create immutable FIFO lots; sale
lines store exact lot allocations. A returned sale restores those allocations. Every
database row will be tenant-scoped by `shop_id`, and Postgres Row Level Security will
enforce that scope using the authenticated user identity and role. Sensitive inventory
and financial mutations will run as transactional Postgres functions or protected Edge
Functions. Firebase may be added later only for push notifications through FCM.
- **Authentication:** production user-ID/PIN login calls the hosted `pin-login` Edge
  Function, stores the imported Supabase session with Android Keystore AES-GCM
  encryption, restores/refreshes through Supabase Auth, and derives role/shop from
  RLS-protected database rows. Preview authentication has been removed from the release
  source set.
