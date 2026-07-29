# GDAD BAGS security hardening audit

Audit date: 2026-07-29 (Asia/Kathmandu)
Scope: first-release Android client, release APK, Supabase Postgres/RLS/RPCs, Edge Functions,
sessions, CI, and direct dependencies.

## Result

No known high-severity issue remains in the reviewed source or development backend. The
application is still a development candidate, not a production release: production signing,
R8 validation, production-environment provisioning, backup/restore drills, and physical-device
smoke tests remain mandatory Phase 7 launch gates.

## Controls and evidence

| Boundary | Result and evidence |
| --- | --- |
| Android configuration | Only `INTERNET` is requested. Backup and device transfer are disabled and explicitly exclude every credential/device-protected storage domain. Cleartext is disabled by the manifest and network-security policy; only system trust anchors are accepted. |
| Supabase client input | Configuration accepts only an HTTPS origin without credentials/path/query/fragment and a bounded `sb_publishable_` key. Secret, legacy, malformed, and cleartext inputs fail closed before client creation. |
| Session storage | Access/refresh sessions use Android Keystore-backed AES-GCM with a non-exportable key and randomized IVs. Corrupt ciphertext is deleted. Logout clears local Auth state and identity-scoped Room data even if the remote logout call fails. |
| Local business cache | Room remains app-private and contains business cache/outbox data, not PINs, peppers, service keys, or Supabase sessions. Android cloud backup and device transfer are both excluded. |
| Android logs/errors | No production `Log`, `println`, stack-trace, HTTP logging plugin, raw backend message, or raw exception-message path was found. Typed errors and diagnostics retain only operation, status class, retry disposition, and exception type. |
| Components/deep links | The only unprotected exported component is the launcher activity. No `VIEW`/`BROWSABLE` filter, WebView, or external deep-link contract exists. Transitive exported WorkManager/profile receivers are permission-protected. |
| Release artifact | `verifyReleaseAuthSafety` checks production source and manifest policy. `verifyReleaseArtifactSafety` scans every uncompressed APK entry and fails on preview-auth, secret-key, pepper, bootstrap, or diagnostic credential markers. A publishable client key is intentionally public and is not treated as a secret. |
| Database tables/views | Static inspection and live linked `supabase db lint --level warning` found no schema errors. Every current `public`/`private` application table has RLS; anonymous table access and direct authenticated table mutation are denied. There are no exposed application views. |
| Database functions | Every `SECURITY DEFINER` function pins `search_path = ''`, identifiers are schema-qualified, no dynamic SQL is used, and execute grants are explicit. A generalized pgTAP suite now prevents RLS/grant/search-path/dynamic-SQL regression as tables and functions are added. |
| Edge authentication | `pin-login` is intentionally public-at-gateway and validates a project publishable key, performs dummy Argon2id work, and applies source/account throttling. `manage-accounts` uses gateway JWT verification plus manual Auth subject verification and PIN reauthentication. Mixed bootstrap/authenticated `manage-users` manually validates either a constant-time bootstrap token or a live Auth subject before service-role RPCs enforce authorization. |
| Edge input/output | POST-only, strict allow-listed request shapes, 2/4 KiB body limits, no-store/nosniff/CSP/referrer headers, generic client failures, no secret/request logging, and 10-second upstream timeouts are enforced. No CORS headers are emitted because the first release is a native Android client. |
| Supply chain/CI | Direct versions and Deno dependency integrity hashes are pinned. GitHub Actions use immutable full commit SHAs, the Gradle wrapper distribution has the official SHA-256, CI uses exact Deno 2.9.0 with frozen `deno ci`, and high/critical Deno advisories fail CI. Dependabot monitors Gradle, npm, and Actions weekly. |

The RLS and function posture follows Supabase guidance on
[Row Level Security](https://supabase.com/docs/guides/database/postgres/row-level-security),
[security-definer helpers](https://supabase.com/docs/guides/troubleshooting/do-i-need-to-expose-security-definer-functions-in-row-level-security-policies-iI0uOw),
and [Edge Function authentication](https://supabase.com/docs/guides/functions/auth).
Android transport rules follow the official
[Network Security Configuration](https://developer.android.com/privacy-and-security/security-config)
guidance. CI action pinning follows GitHub's
[secure-use guidance](https://docs.github.com/en/actions/reference/security/secure-use), and
the wrapper checksum is taken from Gradle's
[official checksum reference](https://gradle.org/release-checksums/).

## Closed findings

1. **Unvalidated remote origin/key:** nonblank strings could reach the network client. Fixed
   with strict, tested HTTPS-origin and publishable-key validation.
2. **Implicit transport/transfer defaults:** security depended on target-SDK defaults and
   `allowBackup=false` alone. Fixed with explicit network, cloud-backup, legacy-backup, and
   device-transfer policies.
3. **Unneeded notification permission:** the first release implements an in-app feed only.
   Removed `POST_NOTIFICATIONS` until system delivery exists.
4. **Source-only release safety:** the prior check did not inspect the packaged output. Fixed
   with an APK-entry scanner and manifest-policy assertions.
5. **Edge diagnostic detail:** `pin-login` logged a caught exception message. Removed; all Edge
   failures now log only developer-authored stage names.
6. **Unbounded Edge upstream waits:** internal Auth/PostgREST calls relied on platform wall-clock
   limits. Fixed with explicit 10-second abort signals.
7. **Incomplete Edge secret validation:** account administration accepted a decoded short rate
   pepper and could misclassify a short PIN pepper as failed reauthentication. Both now fail as
   server misconfiguration before use.
8. **Mutable CI/tool downloads:** action major tags and a wrapper URL were mutable without an
   independent checksum. Actions are SHA-pinned and the Gradle distribution checksum is pinned.

## Accepted development risks

- Certificate pinning is deliberately not used. HTTPS, Android's system trust store, and strict
  origin validation protect transit while avoiding outages during managed Supabase certificate
  rotation. Revisit only with an owned domain and a tested backup-pin rotation process.
- Room business cache is not separately encrypted. It is app-private, excluded from backup and
  transfer, and contains no Auth session or PIN material. A rooted/fully compromised device can
  still read app-private data; device integrity controls are outside the first-release scope.
- `manage-users` retains `verify_jwt=false` because one endpoint currently serves both the
  one-time bootstrap operation and authenticated provisioning. The handler validates the project
  key and then either a constant-time bootstrap token or the caller against Supabase Auth; the
  database independently authorizes the actor. Phase 7 must remove/rotate the bootstrap secret
  after production bootstrap or split bootstrap into a separately disabled endpoint.
- CI dependency audit availability depends on the advisory registry. Registry outages fail the
  job; no ignore list or fail-open flag is configured.

## Production launch blockers (not accepted risks)

1. Create a production Supabase project and apply the reviewed migrations/functions through the
   documented deployment procedure; do not reuse the development project.
2. Set fresh production peppers and bootstrap material, create the sole initial super admin, then
   remove/rotate bootstrap access and verify Auth/session rate limits.
3. Configure release signing outside the repository, enable and test R8/resource shrinking with
   minimal keep rules, generate an AAB/APK, and rerun the source plus artifact safety gates.
4. Complete accessibility review, backup/restore and incident-response drills, monitoring/alerts,
   Play integrity/privacy declarations, staged rollout, and physical-device online/offline tests.
