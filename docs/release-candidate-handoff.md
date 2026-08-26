# GDAD BAGS first-release candidate handoff

This handoff covers the protected production-signed `0.2.0-rc11` APK with fail-closed Super Admin
shop deletion and legacy existing-PIN verification compatibility. The exact merged main commit, APK size, checksum,
signer, package, SDK levels, and production Supabase binding are independently verified and
pinned. The completed qualification workflow is accepted for controlled direct APK distribution;
the artifact is not published to an app store.

## Candidate identity

- File: `GDAD-BAGS-0.2.0-rc11-12-release.apk`
- Package: `com.gdad.bags`
- Version: `0.2.0-rc11` (`versionCode = 12`)
- Source: merged `main` commit `45bdd93a6c7a0aef1d3690306c2cfcd8b7dc07f7`
- Minimum/target SDK: 31/36
- APK size: 57,477,165 bytes
- APK SHA-256: `A5434D766D843E25EA6E985E35F39A56EA3EB0F23A5807586E6330A38B55A0FF`
- Signer certificate SHA-256:
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`
- Backend: protected production Supabase project `skfxfbssfeetquteubcn`
- Protected signing run: GitHub Actions run `32872992204`; artifact `9594853682` retained through
  `2026-09-09T05:56:22Z`

rc11 supersedes rc10 by allowing an existing 4–8 digit credential during login and privileged
reauthentication, fixing deletion for legacy Super Admin PINs. New and reset PINs remain restricted
to 6–8 digits. Shop deletion still requires the exact active-shop slug, an 8-500 character audit
reason, and the current Super Admin PIN. The Android client, authenticated Edge Function, and
service-role-only database RPCs each recheck the operation; deletion is transactional, shared
cross-shop identities survive, and managed Auth cleanup is metadata-validated and resumable. The
rc9 keyboard-safe purchase dialog and all prior account,
inventory, sales, vendor, financial, reporting, offline, accessibility, and performance controls
remain enforced.
Never install a candidate when its checksum, signer, package, or version differs. Never expose the
keystore, passwords, PIN peppers, service key, backup identity, or production access token while
transferring the APK.

## Safe verification and installation

Connect exactly one Android 12+ phone, enable USB debugging, accept the computer authorization,
and verify the candidate without changing the phone:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\install-release-candidate.ps1
```

For a phone on which GDAD BAGS is not installed:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\install-release-candidate.ps1 -InstallMode Fresh
```

For the upgrade-retention test on a phone with an earlier GDAD BAGS build:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\install-release-candidate.ps1 -InstallMode Upgrade
```

The tool fails closed if there is no single authorized device, the wrong candidate is supplied,
Fresh mode would overwrite an installation, or Upgrade mode has no installed predecessor. It does
not uninstall an app or clear device data. With multiple devices, add `-Serial <device-id>`.

## Completed device acceptance matrix

Record device model, Android version, tester, connection type, commit, APK hash, and timestamp.
The operator completed the existing business workflow against the production backend on rc9 and
accepted it on 2026-08-24. rc10 added only the protected account-administration deletion path, and
rc11 narrowly corrects existing-PIN length compatibility for login and reauthentication. The path has
automated Android/Edge/database coverage and a green production deployment, but the first physical
deletion should target a disposable empty shop, never a live business shop.

| Area | Result |
| --- | --- |
| Install/upgrade | **PASS** — the signed candidate installed/launched through the controlled rc upgrade sequence. |
| Authentication | **PASS for launch scope** — production Owner and Salesman login, session restoration, logout, and fail-closed cache/session clearing passed. Automated generic-failure, lockout, and revocation controls remain green; the operator waived another physical repetition. |
| Roles | **PASS** — Owner and Salesman destinations/data were correctly isolated; privileged administration stayed hidden from Salesman. |
| Core workflow | **PASS** — account/product setup, purchase, stock adjustment, FIFO sale/return, vendor finance/return, cash/bank/expense, reports, and notifications reconciled to authoritative results. |
| Offline/recovery | **PASS** — scoped cached reads remained available, prohibited finance mutation failed safely, and the same-key reconnect retry posted exactly once without duplication. |
| Accessibility | **PASS** — TalkBack order/announcements, approximately 200% text, keyboard reachability, non-touch focus, dialogs, and error states were accepted. |
| Performance | **PASS** — 698 ms median cold start, about 131.7 MiB warm PSS, and 1.67% janky frames meet the recorded budgets. |
| Tenant purge | **PASS** — Owner logout/process restart, Salesman switch, and Owner restoration exposed no prior-role cache or duplicate authoritative rows. |
| Super Admin shop deletion | **PASS (automated/backend)** — exact confirmation, authorization, rollback, tenant isolation, shared-user preservation, audit retention, and retry cleanup pass; disposable-shop physical confirmation remains the first-use gate. |

Capture performance after the manual workflow:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
  .\tools\measure-android-performance.ps1 -ColdStartRuns 5
```

## Rollback and incident handling

- Stop distribution immediately if signature, tenant isolation, authorization, accounting,
  duplicate-mutation, credential exposure, or data-loss behavior is wrong.
- Disable the affected production account/session through the protected administration path when
  identity compromise is suspected. Do not weaken RLS, expose service credentials, or edit ledger
  rows directly.
- An APK downgrade is not the normal rollback. Preserve the signing identity, fix the defect, raise
  `versionCode`, pass all gates again, and issue a signed replacement. Server migrations are
  forward-only; use the reviewed compensating migration procedure in `production-deployment.md`.
- Uninstalling clears device-local app state and must be an explicit operator decision after any
  required diagnostic evidence is captured. Authoritative business records remain in Supabase.
- Use the correlation ID from safe error responses with `operations-runbook.md`; never collect PINs,
  tokens, request bodies, or raw database errors in support reports.

## Final distribution status

The exact version-code-12 candidate carries forward the completed physical business workflow and
passes the 2026-08-25/26 automated Android/database/Edge gates, protected production deployment, and
final checksum/signer/package/binding verification. It is ready for controlled direct APK handoff.
Independently recoverable owner copies of the backup identity, production database
password, and Android signing material remain required before broad unattended distribution; this
continuity work does not change or block the verified APK itself. Production Super Admin bootstrap
and the isolated restore drill pass. Never reuse version code 12 for different bytes.
