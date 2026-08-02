# GDAD BAGS production release build

The repository builds ordinary release variants unsigned for automated source/artifact checks. A
production APK is signed only when an operator explicitly requests it and supplies every protected
input. No keystore, password, production URL, or project key belongs in source control.

## Version

- Version name: `0.2.0-rc2`
- Version code: `3`
- Package: `com.gdad.bags`
- Minimum/target Android SDK: 31/36

Increment the version code for every distributed replacement. Version code 2 identifies the signed
rc1 candidate and must never be reused for different bytes; rc2 uses version code 3.

## Required protected inputs

Provide all values through ignored user Gradle properties or environment variables:

```properties
GDAD_RELEASE_STORE_FILE=C:/secure/location/gdad-release.jks
GDAD_RELEASE_STORE_PASSWORD=<secret>
GDAD_RELEASE_KEY_ALIAS=<secret>
GDAD_RELEASE_KEY_PASSWORD=<secret>
SUPABASE_URL=https://<production-project-ref>.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_<production-client-key>
```

`GDAD_RELEASE_STORE_FILE` should be an absolute path. Store the keystore and its recovery material
in two access-controlled locations. Do not send them in chat, commit them, place them in an APK
folder, or reuse the Android debug keystore.

The production gate rejects missing/partial signing inputs, a missing keystore, a blank or malformed
publishable key, the known development Supabase project, and a version code that has not advanced
beyond the initial development build.

## Local build

After the production project, backup/restore evidence, device gate, and release approval exist:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-production-apk.ps1
```

The script runs clean tests/lint and `assembleProductionRelease`, verifies the APK signature, then
copies `GDAD-BAGS-0.2.0-rc2-3-release.apk` to the project root and prints only its path, byte size,
certificate metadata, and SHA-256. Gradle consumes secrets without printing them.

## GitHub Actions

`Android release gate` runs the unsigned verification suite for Android changes. Its manual
`production_release` job uses the protected `production` GitHub environment and requires:

- `ANDROID_RELEASE_KEYSTORE_BASE64`
- `ANDROID_RELEASE_STORE_PASSWORD`
- `ANDROID_RELEASE_KEY_ALIAS`
- `ANDROID_RELEASE_KEY_PASSWORD`
- `SUPABASE_PRODUCTION_URL`
- `SUPABASE_PRODUCTION_PUBLISHABLE_KEY`

Require environment approval and restrict who may trigger it. The job never creates a GitHub
Release or publishes to an app store; it retains the signed APK/checksum as a protected artifact for
14 days so physical acceptance can occur before staged distribution.

## Obfuscation decision

R8 minification and resource shrinking remain disabled for this release candidate. The application
contains serialization, Room, and Supabase client code that must be exercised on a signed physical-
device build before keep rules can be accepted. This is an explicit reliability decision, not an
unreviewed default. Revisit it after the complete Task 7.3 device matrix passes.

The fail-closed installation commands, physical acceptance matrix, rollback policy, incident path,
and staged-distribution gate are in `docs/release-candidate-handoff.md`. Its rc1 identity is marked
superseded until the signed rc2 build supplies a new immutable hash.
