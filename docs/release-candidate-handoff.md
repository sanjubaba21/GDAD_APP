# GDAD BAGS first-release candidate handoff

This handoff applies only to the signed `0.2.0-rc2` APK built from main commit
`42b39a68e41533e37118f1d99331b0b67a9450f9` by protected workflow run `30758725027`.
It is approved for controlled device qualification, not public distribution or an app store.

## Candidate identity

- File: `GDAD-BAGS-0.2.0-rc2-3-release.apk`
- Package: `com.gdad.bags`
- Version: `0.2.0-rc2` (`versionCode = 3`)
- Minimum/target SDK: 31/36
- APK size: 57,411,609 bytes
- APK SHA-256: `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC`
- Signer certificate SHA-256:
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`
- Backend: protected production Supabase project `skfxfbssfeetquteubcn`

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

## Device acceptance matrix

Record device model, Android version, tester, connection type, commit, APK hash, and timestamp.
Every row must pass against the production backend before launch:

| Area | Required evidence |
| --- | --- |
| Install/upgrade | Fresh install launches; signed upgrade preserves the intended encrypted session/cache and does not cross identities. |
| Authentication | Valid production user logs in; wrong PIN is generic; lockout/retry is safe; logout clears local session/cache; disabled/revoked session cannot continue. |
| Roles | Super Admin, Owner, and Salesman see only permitted destinations and tenant data. |
| Core workflow | Account/product setup, purchase, stock adjustment, FIFO sale, partial return, vendor finance, cash/bank/expense, report, and notification paths complete with authoritative receipts. |
| Offline/recovery | Cached reads remain scoped; permitted outbox operations retry once; prohibited financial/inventory mutations fail safely; reconnect reconciles without duplicates. |
| Accessibility | TalkBack order/announcements, 200% font/display, keyboard or Switch Access, and slow/error states pass the procedure in `accessibility-nepal-ux-audit.md`. |
| Performance | Five-run cold-start median, PSS, and janky frames meet the budgets in `performance-reliability-audit.md`. |
| Tenant purge | Logout and identity change remove the previous user's session, cache, and pending work; no prior-tenant data appears. |

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

## Final distribution gate

Do not publish or broadly share version code 3 until the exact candidate passes the complete matrix,
the production Super Admin is bootstrapped through the masked helper, backup/signing credentials
have independently recoverable owner copies, the isolated restore drill passes, and the owner
records staged-distribution approval in `PROJECT_STATUS.md`. Once distributed, never reuse version
code 3 for different bytes.
