# GDAD BAGS first-release candidate handoff

This handoff covers the protected production-signed `0.2.0-rc13` APK with negotiated per-sale
pricing for both shop roles, FIFO cost-based Owner profit, fail-closed Super Admin shop deletion,
and legacy existing-PIN verification compatibility. The exact merged main commit, APK size, checksum,
signer, package, SDK levels, and production Supabase binding are independently verified and
pinned. The completed qualification workflow is accepted for controlled direct APK distribution;
the artifact is not published to an app store.

## Candidate identity

- File: `GDAD-BAGS-0.2.0-rc13-14-release.apk`
- Package: `com.gdad.bags`
- Version: `0.2.0-rc13` (`versionCode = 14`)
- Source: merged `main` commit `c9d89b3b1f7639c4694a6b72fce4fdad54afe698`
- Minimum/target SDK: 31/36
- APK size: 57,493,549 bytes
- APK SHA-256: `A1F3E6D9311D91A8650F256C3EBE9B4243ED9A6B7F1BB113790BCBF7D992FC47`
- Signer certificate SHA-256:
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`
- Backend: protected production Supabase project `skfxfbssfeetquteubcn`
- Protected signing run: GitHub Actions run `34092077872`; artifact `10007596078` retained through
  `2026-09-21T06:58:27Z`

rc13 supersedes rc12 by making each product's suggested price an editable actual selling price for
both Owner and Salesman. The server preserves suggested and negotiated snapshots and calculates
Owner-only receipt/end-day gross profit from actual revenue minus exact FIFO cost. Salesmen still
cannot discount, create credit/partial-payment sales, or see cost/profit. rc12 keeps the deletion
dialog open until authoritative success, shows
field-specific slug/reason/PIN guidance, normalizing the shop slug, and discarding a terminally
rejected PIN/idempotency request so the corrected submission is fresh. Existing credentials retain
the rc11 4–8 digit verification compatibility; new and reset PINs remain restricted to 6–8 digits.
Shop deletion still requires the exact active-shop slug, an 8-500 character audit reason, and the
PIN for the currently signed-in Super Admin account. The Android client, authenticated Edge
Function, and
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
accepted it on 2026-08-24. rc10 added only the protected account-administration deletion path,
rc11 corrected existing-PIN length compatibility, rc12 corrected deletion preflight and rejected-
request retry behavior, and rc13 adds negotiated pricing. The path has
automated Android/Edge/database coverage and a green production deployment, but the first physical
deletion should target a disposable empty shop, never a live business shop.

The exact rc12 predecessor was installed on a Xiaomi `23021RAAEG` running Android 14 on 2026-09-03. HyperOS
blocked ADB package-manager installation with `INSTALL_FAILED_USER_RESTRICTED`, so the APK was copied
to Download and installed through Xiaomi File Manager/native package installer. The installed
`base.apk` checksum exactly matched the protected artifact, rc12/code13 launched in 160 ms, and the
app reached a role-appropriate dashboard with a successful trusted-report refresh. The temporary
Download APK was removed afterward. This confirms candidate installation and launch, not the pending
disposable-shop deletion outcome.

| Area | Result |
| --- | --- |
| Install/upgrade | **PASS through rc12** — rc13 is independently verified and ready for an in-place upgrade; physical rc13 confirmation is pending. |
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

The exact version-code-14 candidate carries forward the completed physical business workflow and
passes the 2026-09-05/09-07 Android/database gates, protected production migration/signing, and final
checksum/signer/package/binding verification. It is ready for controlled direct APK handoff and an
in-place negotiated-price smoke test; the first disposable-shop physical deletion retest also remains.
Independently recoverable owner copies of the backup identity, production database
password, and Android signing material remain required before broad unattended distribution; this
continuity work does not change or block the verified APK itself. Production Super Admin bootstrap
and the isolated restore drill pass. Never reuse version code 14 for different bytes.
