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
- Owner-only vendor management and multi-line purchase posting;
- strict `.xlsx` purchase-bill import that previews the bill, matches existing products by SKU,
  creates missing products/vendors, and posts one authoritative purchase without retyping lines;
- an embedded GDAD purchase template with product name, SKU, optional barcode, quantity, unit cost,
  suggested price, minimum selling price, low-stock threshold, and formula-driven line totals;
- online FIFO sale posting for Owner and Salesman with a different negotiated price per sale line;
- a product minimum selling price enforced in the Android/Windows clients and by the sale RPC;
- Owner-only discount/credit controls and Owner-only FIFO cost/profit receipt details;
- safe logout and process-memory-only session handling;
- a public privacy-policy link available before login and from the authenticated navigation rail;
- Windows application-image, EXE/MSI, and Microsoft Store MSIX packaging with the GDAD launcher
  icon;
- keyboard Enter submission, large desktop layout, and explicit in-progress feature boundaries.

Sale returns, cash/bank operations, period reports, notifications, account
administration, encrypted persistent sessions, desktop offline storage, printing, and Microsoft
Store submission remain ordered follow-up slices. Their navigation entries cannot mutate
production until the matching repositories and tests are connected. Desktop product mutation and
financial posting require an internet connection; failed requests retain their idempotency key for
explicit safe retry.

The app handles account and shop business information, so the Microsoft Store privacy declaration
must be **Yes**. The reviewed public policy is `docs/privacy-policy.md`; the Windows login and
authenticated navigation both open its public HTTPS location. Do not submit a package that omits
that link or declares that the app uses no personal information.

## Purchase entry and Excel import

Every Windows purchase, whether typed manually or imported, posts through the same Owner-only
`post_purchase_receipt` operation. A successful manual entry therefore creates the purchase bill,
receipt, FIFO lots, inventory movements, vendor payable, accounting entries, and audit record
automatically. The UI uses the server-returned bill and totals instead of a client-calculated bill.

For Excel entry, choose **Save Excel template**, fill the `Purchase Bill` worksheet, then choose
**Upload Excel bill**. The importer accepts one marked GDAD workbook of at most 5 MB and 1-100
product rows. Formulas are allowed only in the provided line-total column; formulas in bill or
product inputs are rejected. SKU matching is case-insensitive. Duplicate SKUs, ambiguous or
archived catalog matches, invalid dates/numbers, a minimum price above the suggested price, and
inactive vendor/account references block confirmation before any hosted change.

The confirmation preview identifies existing and new records. On confirmation, missing records are
created first and the purchase is posted once. Retained operation IDs make an explicit retry safe
after a network interruption. The importer never accepts a spreadsheet total as authoritative.

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
EXE artifacts with SHA-256 sidecars. Those direct-download packages are not Authenticode-signed;
Smart App Control blocks their unsigned launcher/custom runtime and has no per-app exception.

## Build the Microsoft Store MSIX

The Store route preserves the existing Kotlin application and production backend. It changes only
the Windows package container. Microsoft signs a successfully certified Store MSIX, avoiding the
unsigned direct-download package block without disabling Smart App Control.

First reserve the app name in Partner Center. Copy the exact package identity values from
**Product management > Product identity** into these protected GitHub `production` environment
secrets:

- `WINDOWS_STORE_IDENTITY_NAME`
- `WINDOWS_STORE_PUBLISHER`
- `WINDOWS_STORE_PUBLISHER_DISPLAY_NAME`

Then manually run **Windows desktop release gate** from an exact reviewed `main` commit with both
`production_release` and `store_msix` enabled. The workflow fails closed when an identity value is
missing, validates the production Supabase binding, builds the unchanged application image, and
adds `GDAD-BAGS-Windows-0.1.0.msix` plus its SHA-256 sidecar to the protected artifact. Upload that
MSIX to the matching Partner Center submission; do not rename or invent the Store identity values.

For local/CI manifest validation, `tools/package-windows-msix.ps1` stages the existing application,
generates correctly sized PNG assets from the embedded launcher icon, emits a full-trust desktop
manifest, and invokes the Windows SDK `MakeAppx.exe`. An unsigned local MSIX is validation output,
not an installable release and must not be presented as Smart App Control-compatible.

## Current production Excel-purchase candidate

PR #75 merged exact tested head `e576a2956d687de509d6bfb4db406249ee4c071d` as main
`9669c52ed63fe47d232337160e91cc18d7c05d84`. Protected Supabase run `34681639847`
deployed migration `20260911090000` and the existing three Edge Functions before protected Windows
run `34681940705` built the client. Artifact `GDAD-BAGS-Windows-0.1.0-production` (id
`10294506162`) is retained through `2026-09-26T08:07:02Z`.

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `GDAD-BAGS-Windows-0.1.0-portable.zip` | 125,896,376 | `a33d9bb800f406928e02159f2c591cacbbe4e8cafbb4acf8d091a5d1f4f35059` |
| `GDAD-BAGS-Windows-0.1.0.msi` | 126,135,848 | `6aae5fc8b15532661849792c68feb8a599ca39d1d858b84b9a6d2cfddb467007` |
| `GDAD-BAGS-Windows-0.1.0-setup.exe` | 126,732,288 | `b9f1faf92b3741ba0c7196b1ef0e4d6f79f0b5590ccde588e85e73ab829bd210` |

Independent verification matched every sidecar, validated EXE/MSI headers, opened the portable JAR,
found the embedded Excel template, confirmed the exact production project binding, and found no
secret/service-role/PIN-pepper marker. These exact direct-download packages remain useful only on a
device whose policy permits unsigned applications. Extracting the portable ZIP does not bypass
Smart App Control because the same unsigned executable components remain inside it.

## Earlier verified production 0.1.0 candidate

PR #73 merged exact tested head `efc9cb53e6b383b3109081259610930057631eb1` as main
`4b9f6ca40bc787747111425bc9a4571ddf124dd3`. Protected workflow run `34443927713` produced artifact
`GDAD-BAGS-Windows-0.1.0-production` (id `10139184903`), retained through
`2026-09-24T06:19:00Z`.

The independently verified files are:

| File | Bytes | SHA-256 |
| --- | ---: | --- |
| `GDAD-BAGS-Windows-0.1.0-portable.zip` | 108,204,180 | `33169B0D48FCF88CE0409DDF1CDEEC1DA11501E97AEAAD00BF41E054E8022B55` |
| `GDAD-BAGS-Windows-0.1.0.msi` | 108,446,057 | `19360889A06AF7C1CF27251422398873869DEBDB76B9705789A28214517577DF` |
| `GDAD-BAGS-Windows-0.1.0-setup.exe` | 109,042,688 | `AC4332C9FEB79CD280958CCBA0A4DF991F0112DCDC899C913F4656C6EB6C965C` |

These are self-contained 64-bit Windows 10/11 packages and need no separate Java installation.
For a device that permits unsigned applications, run the setup EXE and follow the Windows installer.
Extracting the portable ZIP does not bypass Smart App Control. Use the Microsoft Store MSIX route on
a protected device; do not disable system-wide protection merely to run this candidate.

The candidate passed exact sidecar comparison, Windows EXE/MSI header checks, portable archive/JAR
inspection, exact production-project binding, client-safe credential classification, forbidden-
marker scanning, and an eight-second launch test on the operator laptop. The launch test entered no
credential and changed no hosted data. The remaining acceptance is one existing Owner login,
product refresh, and explicitly disposable negotiated-price sale.

## Security boundary

The first desktop slice deliberately does not persist Supabase tokens. Closing the process signs the
local UI out. Windows DPAPI-backed persistence must be implemented and reviewed before enabling
session restoration. The random installation ID contains no identity or credential data.
