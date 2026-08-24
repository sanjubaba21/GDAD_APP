# GDAD BAGS performance and reliability audit

Audit date: 2026-08-24 (Asia/Kathmandu)

## Result

The repository-level Task 6.4 controls are implemented. Production remote and Room reads are
bounded, oversized responses fail closed instead of truncating silently, expensive relationship
assembly is indexed once per snapshot, and authenticated startup no longer launches all ten
feature pipelines. A release build now runs a dedicated performance-safety gate.

The protected production-signed `0.2.0-rc9` (`versionCode = 10`) candidate was measured on a Redmi
`25062RN2DA` running Android 16/API 36. Five cold starts, post-workflow memory, and frame statistics
all pass the first-release budgets below. `tools/measure-android-performance.ps1` was hardened during
the capture for PowerShell's one-device scalar output and dash-prefixed ADB arguments; the successful
physical reruns verify both fixes. No device serial, credential, or business value is retained here.
The complete automated Android release gate also passed on 2026-08-24. The operator accepted the
completed signed-rc9 workflow as the final in-app test and directed final APK handoff without another
device cycle; no Android application source changed after the measured candidate.

## First-release budgets

| Workflow | Target on representative Android 12+ hardware | Enforced or measured evidence |
| --- | --- | --- |
| Cold process start | median `TotalTime` at or below 2,500 ms over five runs | **PASS** — 1,742, 702, 665, 679, and 698 ms; median 698 ms. |
| Session restore/dashboard | no unrelated feature request storm; useful cached state immediately when present | Navigation policy activates only notifications and, except for Super Admin, the dashboard report. |
| Login and authenticated refresh | healthy-network feedback at or below 5 s; fail safely by 15 s | Every remote call has a 15,000 ms hard timeout and typed retry/failure handling; device timing pending. |
| Product search and scrolling | input feedback below 100 ms with the supported 500-product window; janky frames below 5% during the scripted manual pass | **PASS** — 3,051 frames, 51 janky, 1.67% after product search/scroll plus report/return/notification navigation. No visible input lag was reported. |
| Sale/return/purchase mutation | healthy-network result at or below 5 s; no duplicate submission; fail safely by 15 s | Idempotent request IDs, disabled in-flight actions, transactional RPCs, and remote timeout are verified; device timing pending. |
| Dashboard/date report | healthy-network result at or below 5 s for at most 366 days | Client and RPC reject wider ranges; exact totals remain unbounded aggregates while each detail list has a 501st-row overflow sentinel. |
| Warm working-set memory | total PSS at or below 300 MiB after the manual workflow | **PASS** — 134,898 KiB (about 131.7 MiB) after the scripted manual pass. |

Targets are engineering budgets for first-release sign-off, not claims about the hosted network.
Record device model, Android version, build SHA, connection type, raw JSON, and pass/fail in
`PROJECT_STATUS.md` when measurements are taken.

## Physical device evidence

- Candidate: `GDAD-BAGS-0.2.0-rc9-10-release.apk`, package `com.gdad.bags`, source commit
  `43ec93100967ff8eb3876734fac7e48f6dc75231`, SHA-256
  `99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC`.
- Device: Redmi model code `25062RN2DA`, Android 16/API 36; authorized USB ADB using the local
  platform-tools 37.0.1 legacy USB compatibility backend. Device serial is intentionally omitted.
- Connection/workload: laptop-hotspot-backed Wi-Fi for app data; Owner session; product search and
  repeated scrolling, report load/scroll, existing return navigation, notification detail/back, and
  Dashboard return. No business mutation was posted during the measured frame pass.
- Cold start JSON result: five runs `[1742, 702, 665, 679, 698]` ms; median `698` ms; immediate
  post-launch PSS `78,585` KiB. **PASS**.
- Post-workflow current-only JSON result: PSS `134,898` KiB; `3,051` total frames; `51` janky frames;
  `1.67%` jank. **PASS**.
- Operator had already physically accepted the production purchase, sale, sale return, vendor finance,
  cash/bank/expense, stock-adjustment, report, notification, offline/retry, identity-purge, and
  accessibility workflows recorded in `PROJECT_STATUS.md`.

## Bounded data evidence

- All 32 production PostgREST `.select` calls have an explicit deterministic limit. List reads
  request 501 rows for a supported maximum of 500 and throw a safe overflow failure if the sentinel
  is present. Singleton reads request two rows so duplicate authoritative records fail closed.
- All 19 Room `SELECT` paths have an explicit limit and deterministic ordering. Owner cache lists
  use 500; the mutation outbox observer uses 200; primary-key/singleton paths use one.
- Business reports retain exact database-side totals and counts. Only low-stock, vendor-due, and
  account-balance detail arrays are bounded to 501 rows; Android rejects the overflow sentinel.
- Report ranges are limited to 366 days in Android and PostgreSQL.
- Production has no `runBlocking`; Room and remote operations stay in coroutine/repository paths.
- Remote calls have a 15-second hard timeout and one classified auth-refresh retry where allowed.

The 500-row cap is a supported first-release operating window, not pagination disguised as a
complete result. Overflow produces an explicit safe error so operators cannot act on incomplete
catalog, stock, ledger, history, notification, or report-detail data. Cursor pagination is the
required scale-up before a tenant exceeds this window.

## Query and rendering evidence

- The schema contains tenant-first indexes for product search, FIFO lots, stock timelines, sales,
  returns, purchasing, vendor ledgers, financial accounts, expenses, and journal timelines.
- Report pgTAP forces and checks four query plans for sale, return, expense, and journal date paths;
  the bounded-detail migration is also inspected for all three sentinels.
- The Room regression inserts 501 products, expects the deterministic first 500, and checks
  `EXPLAIN QUERY PLAN` for an owner-scoped index. It compiles, but its Robolectric class still needs
  an available Android test runtime to execute in this offline sandbox.
- Sale-return, finance, and vendor-finance assembly groups child rows once rather than scanning full
  relationship lists inside each parent mapping.
- Stock rendering builds lot and movement maps once per snapshot. Account, notification, purchase,
  and stock derivations are remembered; lazy collections use stable keys where identity matters.
- Data slices load on demand by destination and stay warm only for the same signed-in identity.
  Identity change or logout deactivates all slices. Unauthorized destinations activate none.

## Automated controls

`verifyReleasePerformanceSafety` runs before every release build and fails if:

- a production remote select or Room select loses its explicit bound;
- production introduces `runBlocking`;
- report overflow sentinels or Android overflow checks are removed; or
- stock snapshot indexing is removed; or
- destination-scoped authenticated data activation is replaced by all-feature startup effects.

Pure regression tests cover remote boundary behavior and every navigation activation mapping. The
complete unit/Robolectric, lint, security, accessibility, artifact, and performance gates remain the
final automated release command.

That command passed on 2026-08-24 with `BUILD SUCCESSFUL in 10m`: all four release safety tasks,
unit tests, lint, unsigned release assembly/artifact scan, and debug assembly completed successfully.

## Physical-device measurement procedure

1. Install the current `GDAD-BAGS-test.apk`, connect and authorize one Android device, and preserve
   the intended login state (signed out for login launch, signed in for dashboard launch).
2. Reset frame statistics, perform login, product search/scroll, one sale submission, report load,
   and back-navigation. Use a healthy production-like Wi-Fi/mobile connection and repeat once with
   throttled or disconnected networking.
3. Capture cold start, memory, and current frame statistics:

   ```powershell
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File `
     .\tools\measure-android-performance.ps1 -ColdStartRuns 5
   ```

   With multiple devices, add `-Serial <adb-device-id>`. To avoid restarting the app after the
   manual workflow, use `-CaptureCurrentOnly`. The explicit execution-policy flag makes the
   repository-local script usable on Windows hosts that disable unsigned scripts by default.
4. Record median cold start, total PSS, total/janky frames, manual workflow timings, device/Android,
   app commit/APK SHA-256, connection, tester, and any observed retry or duplicate-operation issue
   in `PROJECT_STATUS.md`.

## Deferred scale work

- Add cursor/keyset pagination and incremental UI loading before any supported shop approaches 500
  rows in a bounded entity or report detail list.
- Re-evaluate the 200-row outbox window if offline mutation volume approaches that operational cap.
- Add a CI-connected emulator or Macrobenchmark module when repeatable device infrastructure is
  available; repository structural gates do not replace runtime frame/startup benchmarks.
