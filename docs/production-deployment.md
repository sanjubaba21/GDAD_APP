# GDAD BAGS production Supabase deployment

Production is a separate hosted Supabase project. The Seoul project
`zniqkuwktvincjndcgpu` is development-only and every production tool in this repository rejects it.
The approved production project is `skfxfbssfeetquteubcn` (`GDAD Bags Production`) in Seoul
(`ap-northeast-2`). It was created healthy on 2026-08-02. Its generated database password and the
four generated deployment values are stored locally under project-scoped Windows Credential
Manager targets; their contents are not committed or printed. Before real transactions, copy the
database password to an independently recoverable approved secret manager and verify the selected
billing/backup plan.

## 1. Create and protect the project

1. Confirm `GDAD Bags Production` (`skfxfbssfeetquteubcn`) remains healthy in Seoul
   (`ap-northeast-2`) and that every deployment target exactly matches this ref.
2. Choose the paid plan/backup mode required by `docs/operations-runbook.md`. Record the accepted
   RPO/RTO and enable spend controls before processing real transactions.
3. Generate a unique database password directly into the private password manager. Keep one
   independently recoverable copy; never paste it into chat, source, an issue, or documentation.
4. Confirm the new ref differs from `zniqkuwktvincjndcgpu`, and record only the non-secret ref and
   region in `PROJECT_STATUS.md`.
5. In Auth settings, keep public signup, anonymous sign-in, and manual identity linking disabled.
   The first release has no external/deep-link callback. Do not push the repository's local
   `127.0.0.1` Auth URLs to production.
6. Restrict organization/project membership, require MFA for owners, and name primary/backup
   operational owners in the private contact store.

## 2. Configure the protected GitHub environment

Create a `production` environment on the canonical GitHub repository. Require a reviewer and limit
deployment branches/tags. Add these environment secrets:

Current state (2026-08-02): the environment exists, requires reviewer `sanjubaba21`, permits
deployment only from branch `main`, and contains all seven expected secret names. GitHub never
returns their values. Preserve these controls when rotating a secret or changing repository access.

| Secret | Purpose |
| --- | --- |
| `SUPABASE_ACCESS_TOKEN` | Least-lived organization token permitted to deploy this project |
| `SUPABASE_PRODUCTION_PROJECT_REF` | Exact 20-character production ref |
| `SUPABASE_PRODUCTION_DB_PASSWORD` | Recoverable production database password |
| `SUPABASE_PRODUCTION_PUBLISHABLE_KEY` | Client-safe `sb_publishable_` key |
| `GDAD_PIN_PEPPER_V1` | Fresh random production PIN HMAC pepper, at least 32 characters |
| `GDAD_RATE_LIMIT_PEPPER_V1` | Independent fresh random rate-limit pepper, at least 32 characters |
| `GDAD_DUMMY_PIN_HASH_V1` | Fresh Argon2id verifier for constant-work unknown-user login |

The three GDAD values must be newly generated for production; never reuse development or restore-
drill values. `SUPABASE_SERVICE_ROLE_KEY` and `SUPABASE_URL` are injected by Supabase into Edge
Functions and are not GitHub workflow inputs. Do not create `GDAD_BOOTSTRAP_TOKEN` here.

The same protected environment later supplies the Android signing secrets listed in
`docs/release-build.md`. Rotate an access token after emergency use or suspected exposure.

## 3. Deploy from the reviewed commit

Run the GitHub Actions workflow `Supabase production deployment` manually. Enter the exact
production project ref and enable its confirmation checkbox. The protected job:

1. rejects the known development ref, a ref mismatch, and missing/malformed inputs;
2. checks Deno formatting/lint/types/tests/audit;
3. replays migrations from zero and verifies the deterministic development seed locally;
4. runs database lint, every pgTAP suite, and the backend concurrency/invariant harness;
5. links only the registered production project and performs `db push --dry-run` before `db push`;
6. installs only the three long-lived GDAD Edge secrets;
7. deploys `pin-login`, `manage-users`, and `manage-accounts` using committed JWT policy;
8. runs linked lint/migration history and safe malformed-request probes; and
9. records commit, project ref, migration head, and pass/fail evidence in the workflow summary.

The workflow deliberately does not upload `seed.sql`, push local Auth redirects, create an Auth
user, install a bootstrap token, build an APK, or publish a release.

## 4. Verify operations before account bootstrap

In the protected dashboard/operations systems, verify and record:

- automatic-backup status, newest recovery point, retention, and accepted RPO/RTO;
- the separate weekly encrypted logical-export destination;
- database/API/Auth/Edge availability and saturation alerts with private destinations;
- the platform status subscription and incident owners;
- signup/anonymous/manual-linking disabled; and
- no development identities, secrets, sessions, or fixtures exist.

Run the isolated restore drill in `docs/operations-runbook.md` and record actual recovery time.
Production is not recoverable merely because migrations can be replayed.

## 5. Create the sole initial Super Admin

Use a trusted local desktop with the signed-in Supabase CLI. Supply only process-local environment
values, then run the tracked masked helper:

```powershell
$env:SUPABASE_PRODUCTION_PROJECT_REF = "<production-ref>"
$env:SUPABASE_URL = "https://<production-ref>.supabase.co"
$env:SUPABASE_PUBLISHABLE_KEY = "sb_publishable_<production-client-key>"
.\tools\bootstrap-production-superadmin.ps1
Remove-Item Env:SUPABASE_PRODUCTION_PROJECT_REF, Env:SUPABASE_URL, Env:SUPABASE_PUBLISHABLE_KEY
```

The helper refuses development, verifies the project is `ACTIVE_HEALTHY`, masks and validates the
PIN, generates a random bootstrap token only in memory, retries one idempotency request, proves the
PIN-login JWT subject equals the created account, prints only a correlation ID, and removes the
hosted bootstrap secret in `finally`. A cleanup failure is critical and leaves the command failed.

Do not screen-record the PIN entry or run this through shared terminal logging. After success,
confirm `GDAD_BOOTSTRAP_TOKEN` is absent from hosted secret names. Create Owner/Salesman test
accounts through authenticated app workflows, never another bootstrap.

## 6. Release binding and rollback

Register the production URL/publishable key and signing material in the protected `production`
environment, then run `Android release gate` with `production_release=true`. Complete the Task 7.3
physical-device matrix before distribution.

Database migrations are forward-only. For a defective migration, stop affected writes and ship a
reviewed corrective migration; restore only when the business owner accepts the recovery point and
downtime. Edge Functions may roll back to the immediately verified prior version while database
compatibility is retained. Android rollback uses a newly signed build with a higher version code;
never distribute an older debug APK as a production rollback.

Record the production project ref/region, migration head, workflow run, backup/restore evidence,
function versions, bootstrap/login proof, signed APK hash/certificate, device matrix, and rollback
decision in `PROJECT_STATUS.md` without credentials or personal account details.
