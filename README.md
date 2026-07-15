# GDAD BAGS

Native Android sales, stock, vendor and cash-management application for Nepal.

## Current milestone

- Android 12+ Kotlin and Jetpack Compose project
- User ID and PIN login shell with Owner, Salesman and Super Admin routing
- Role-specific dashboard navigation
- NPR display and Nepal-time product decisions
- FIFO lots, negative-stock shortage reporting and return restoration
- Supabase Auth, Postgres and Edge Functions client foundation ready for configuration

The current login repository is explicitly a development preview. It validates the
input shape and routes IDs beginning with `admin` or `sales` to those roles; other
IDs open the Owner shell. It does not store or validate a real PIN. Production login
will replace this with a Supabase Edge Function that checks a salted PIN hash,
rate-limits attempts and establishes a Supabase Auth session. Never ship the preview
repository.

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
client cannot be used until both values are supplied. For local development, put the
following in the user Gradle properties file or pass them as environment variables:

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
