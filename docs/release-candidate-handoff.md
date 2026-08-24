# GDAD BAGS first-release candidate handoff

This handoff covers the protected production-signed `0.2.0-rc9` APK with the purchase-review
dialog corrected for the Android keyboard. The exact merged main commit, APK size, checksum,
signer, package, SDK levels, and production Supabase binding are independently verified and
pinned. The completed qualification workflow is accepted for controlled direct APK distribution;
the artifact is not published to an app store.

## Candidate identity

- File: `GDAD-BAGS-0.2.0-rc9-10-release.apk`
- Package: `com.gdad.bags`
- Version: `0.2.0-rc9` (`versionCode = 10`)
- Source: merged `main` commit `43ec93100967ff8eb3876734fac7e48f6dc75231`
- Minimum/target SDK: 31/36
- APK size: 57,444,389 bytes
- APK SHA-256: `99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC`
- Signer certificate SHA-256:
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`
- Backend: protected production Supabase project `skfxfbssfeetquteubcn`
- Protected signing run: GitHub Actions run `32395027694`; artifact retained through
  `2026-09-03T17:12:37Z`

rc9 supersedes rc8 because the physical purchase form could leave **Post purchase once** hidden
behind the Android keyboard. The form now scrolls independently inside a keyboard-inset-aware
dialog while **Hide keyboard**, **Cancel**, and **Post purchase once** remain in a fixed footer.
The rc8 account-provisioning error classification and authenticated-subject binding remain
enforced.
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
The operator completed the workflow against the production backend and accepted it as the final
in-app test on 2026-08-24. No Android application source changed after the measured candidate.

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

The exact version-code-10 candidate has passed the completed physical workflow, the 2026-08-24
automated gate, and final checksum/signer/package verification. It is ready for controlled direct
APK handoff. Independently recoverable owner copies of the backup identity, production database
password, and Android signing material remain required before broad unattended distribution; this
continuity work does not change or block the verified APK itself. Production Super Admin bootstrap
and the isolated restore drill pass. Never reuse version code 10 for different bytes.
