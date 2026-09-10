# GDAD BAGS Windows desktop application

The Windows client is a separate Compose Desktop module that reuses the Android application's
domain models, authentication contract, remote error handling, Supabase client factory, PIN-login
data sources, and trusted reporting RPC. Android remains independently buildable and distributable.

## Current implemented slice

- production-contract user ID/PIN login through the hosted `pin-login` Edge Function;
- Supabase session import and authoritative profile, role, membership, and active-shop checks;
- fail-closed role navigation for Super Admin, Owner, and Salesman;
- trusted dashboard reporting for shop roles;
- authoritative product search and refresh for Owner and Salesman;
- Owner-only audited product create, edit, and archive operations;
- online FIFO sale posting for Owner and Salesman with a different negotiated price per sale line;
- Owner-only discount/credit controls and Owner-only FIFO cost/profit receipt details;
- safe logout and process-memory-only session handling;
- Windows application-image plus EXE/MSI packaging configuration with the GDAD launcher icon;
- keyboard Enter submission, large desktop layout, and explicit in-progress feature boundaries.

Purchases, sale returns, vendors, cash/bank operations, period reports, notifications, account
administration, encrypted persistent sessions, desktop offline storage, printing, and the final
Windows installer remain ordered follow-up slices. Their navigation entries cannot mutate
production until the matching repositories and tests are connected. Desktop product mutation and
financial posting require an internet connection; failed requests retain their idempotency key for
explicit safe retry.

## Run locally

The runtime reads only `SUPABASE_URL` and a client-safe `SUPABASE_PUBLISHABLE_KEY`. For local
development they may come from the ignored Gradle user-home properties already used by Android.

```powershell
.\run-windows-app.ps1
```

No service-role/secret key, PIN pepper, database password, signing secret, or bootstrap token may be
placed in a desktop build.

## Build a Windows application image

```powershell
.\build-windows-app.ps1
```

The output application directory is
`desktopApp/build/compose/binaries/main/app/GDAD BAGS`. Native EXE/MSI tasks require a compatible
Windows WiX installation. Production packaging additionally requires protected production URL/key
inputs and `GDAD_DESKTOP_PRODUCTION_RELEASE=true`; the production gate rejects the development
Supabase project.

The `Windows desktop release gate` GitHub Actions workflow runs tests and application-image
packaging on pull requests and `main`. Its manually approved production job reads the existing
`SUPABASE_PRODUCTION_URL` and `SUPABASE_PRODUCTION_PUBLISHABLE_KEY` secrets only inside the protected
`production` environment, verifies the packaged binding, and uploads portable ZIP, MSI, and setup
EXE artifacts with SHA-256 sidecars. The installers are not Authenticode-signed yet and Windows may
show a SmartScreen warning; no app store or public release is created.

## Security boundary

The first desktop slice deliberately does not persist Supabase tokens. Closing the process signs the
local UI out. Windows DPAPI-backed persistence must be implemented and reviewed before enabling
session restoration. The random installation ID contains no identity or credential data.
