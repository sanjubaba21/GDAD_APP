# GDAD BAGS — Project Status and Agent Handoff

This is the canonical status file for the GDAD BAGS repository. Every developer or
agent must update this file in the same change as any source code, test, build,
configuration, database, security-rule, or backend change.

Last verified: 2026-09-05 (Asia/Kathmandu)
Current milestone: rc13 negotiated sale pricing is implemented locally and awaiting verification, protected backend deployment, signing, and physical smoke testing; rc12 remains the independently verified installed candidate
Current source: `0.2.0-rc13` (`versionCode = 14`); current controlled handoff remains verified rc12/code13 until protected rc13 bytes are independently verified

## Mandatory update protocol

After every code or configuration change:

1. Update the relevant item under **Completed work**, **Work in progress**, or
   **Remaining work**.
2. Add a dated entry to **Change log** describing the files and behavior changed.
3. Record the exact verification command and outcome under **Latest verification**.
4. Record new risks, assumptions, migrations, or manual setup under **Known issues and
   decisions**.
5. Never mark an item complete merely because a UI label, interface, dependency, or
   data class exists. Mark it complete only when the behavior is implemented and
   proportionately tested.
6. If verification was not run, explicitly write `Not run` and the reason. Do not imply
   that unverified work passes.

Use this change-log format:

```text
### YYYY-MM-DD — Short change title
- Status: Complete | Partial | Blocked
- Changed: path(s)
- Behavior: what now works or changed
- Data/security impact: none, or details including migrations/rules
- Verification: exact command and result
- Next: the smallest logical follow-up
```

## Product scope

GDAD BAGS is a native Android application for Nepal-focused sales, inventory, vendor,
cash/bank, and reporting workflows. Users are divided into Super Admin, Owner, and
Salesman roles. Cloud data is intended to be tenant-scoped by `shopId`. Inventory cost
is intended to use immutable FIFO lots and exact per-sale lot allocations.

## Current implementation summary

The repository contains the complete first-release Android feature set backed by production
repositories, Room cache/outbox behavior, and trusted Supabase operations. Android authentication
uses the hosted production contract, and every first-release business screen is connected to
persistent tenant-scoped data. The hosted development project
`zniqkuwktvincjndcgpu` in Seoul has repository migrations through
`20260728060000`, deployed `pin-login`,
`manage-users`, and `manage-accounts` Edge
Functions, private rate/credential/provisioning/administration state, and clean
hosted lint. Strict malformed,
invalid-key, and unknown-user failure paths are verified against the hosted function.
The local Android debug environment has the project URL and publishable key in ignored
Gradle properties. One managed Super Admin Auth identity/profile/credential exists; its
correct-PIN session and authenticated RLS profile read are verified. Android production
authentication now consumes that hosted contract, encrypts persisted sessions with
Android Keystore, and derives role/shop from authoritative RLS reads. Android remote
operations now use typed DTOs, bounded timeouts, explicit retry classification, one
authenticated refresh/retry, and safe domain error categories. A versioned Room cache
provides tenant/user-owned offline read models, transactional snapshot replacement, and
fail-closed purge on logout, identity change, tenant change, or missing ownership state.

Preview authentication and prefix-derived roles are absent from production sources. Every
release build runs an authentication safety gate that also rejects embedded Supabase secret/
service-role keys and hard-coded numeric PIN assignments.

## Completed work

### 2026-09-05 negotiated per-sale pricing and cost-based profit — implementation

- [x] Sale entry now pre-fills each product's suggested price in an editable `Actual selling price`
  field for both Owner and Salesman, so the final price may be changed for every sale without
  changing the product master.
- [x] Salesmen may send a nonnegative negotiated unit price while separate line/sale discounts,
  credit sales, and partial settlement remain Owner-only or prohibited as before.
- [x] The protected FIFO sale RPC retains both suggested/configured and actual/effective immutable
  price snapshots, calculates revenue from the actual price, and keeps exact FIFO allocation cost.
- [x] Owner receipts show FIFO cost and gross profit; trusted end-day/period reports continue to
  calculate gross profit as actual net sale revenue minus returned-adjusted FIFO cost.
- [x] Forward migration `20260905120000_flexible_sale_pricing.sql`, Android role/UI tests, pgTAP
  negotiated-price coverage, report fixtures with a suggestion different from actual revenue,
  product labels, business policy, release version rc13/code14, and release artifact naming were
  updated together.
- [x] Focused 8-test pricing/Sale-screen verification and the complete 194-test Android release
  safety/test/lint/build gate pass locally with zero failures, zero errors, and zero lint errors.
- [ ] PR merge, fresh-database/pgTAP CI, protected production migration, protected signing,
  independent APK verification, guarded-installer update, and physical rc13 smoke test remain pending.

### 2026-09-03 Xiaomi HyperOS rc12 installation and launch

- [x] The exact checksum-pinned rc12 APK is installed on a Xiaomi `23021RAAEG` running Android 14.
  Android reports `com.gdad.bags`, version `0.2.0-rc12`, versionCode 13, min/target SDK 31/36.
- [x] The installed `/data/app/.../base.apk` SHA-256 is
  `0056F8A3099F69C4FCCB157C599C3369E8AFCCFD901B3AB381C3F4C3D895DBEA`, exactly
  matching the protected workflow artifact and guarded handoff candidate.
- [x] HyperOS rejected both streamed and non-streamed ADB package-manager installation with
  `INSTALL_FAILED_USER_RESTRICTED`. The same verified APK was transferred to Download and installed
  successfully through Xiaomi's native package-installer flow, which reported passed security tests.
- [x] `MainActivity` launched successfully in 160 ms and reached an authenticated role-appropriate
  dashboard with a successful trusted-report refresh. The temporary APK copied to phone storage was
  removed after installation; no account ID, PIN, token, or business value was collected or logged.

### 2026-09-03 protected rc12 Android candidate and deletion retry correction

- [x] PR #68 merged exact green head `f851fb23c2b260c20f54276209b9e60477d42e9a` into
  `main` as `317e2ad7146804ed62d4f7d3839ec86e51e9587a`.
- [x] Protected signing workflow `33476614349` passed the complete Android release gate and
  production-release job on exact merged main. Artifact `9788683152` contains the 57,493,549-byte
  `GDAD-BAGS-0.2.0-rc12-13-release.apk`, SHA-256
  `0056F8A3099F69C4FCCB157C599C3369E8AFCCFD901B3AB381C3F4C3D895DBEA`.
- [x] The downloaded APK matches its workflow sidecar. Independent inspection passes APK Signature
  Scheme v2 with one signer and pinned certificate SHA-256
  `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`, package
  `com.gdad.bags`, rc12/code 13, SDK 31/36, launcher, label, and all five icon densities.
  Archive inspection finds the production project reference in one entry, the development reference
  in zero, and every forbidden preview/secret/pepper/bootstrap/diagnostic marker in zero.
- [x] The guarded installer is checksum-pinned to the exact protected artifact and passes VerifyOnly.
  rc12 keeps the shop-deletion dialog open on denial, gives field-specific safe guidance, normalizes
  the slug, and creates a fresh request after a terminally rejected reauthentication attempt.

### 2026-08-26 protected rc11 Android candidate and legacy PIN compatibility

- [x] PR #64 merged exact green head `5536dacc3830dd257d52cd5e4be56560523b4feb` into
  `main` as `45bdd93a6c7a0aef1d3690306c2cfcd8b7dc07f7`. Database run `32871787076`
  and Android run `32871787441` passed their complete required gates.
- [x] Protected production deployment `32872604166` passed exact-project/input checks, complete Edge
  verification, zero-state backend replay, migration preview/apply with no new migration, linked
  database verification, and redacted probes; it redeployed the three reviewed Functions without
  creating, deleting, or changing a user, shop, Auth identity, session, or business row.
- [x] Protected signing workflow `32872992204` passed its Android gate and production-release job on
  exact merged main. Artifact `9594853682` contains the 57,477,165-byte
  `GDAD-BAGS-0.2.0-rc11-12-release.apk`, SHA-256
  `A5434D766D843E25EA6E985E35F39A56EA3EB0F23A5807586E6330A38B55A0FF`.
- [x] Independent inspection passes APK Signature Scheme v2 with one signer and pinned certificate
  SHA-256 `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`,
  package `com.gdad.bags`, rc11/code 12, SDK 31/36, launcher, label, and all five icon densities.
  Production binding occurs in one archive entry; development binding and forbidden auth/secret/
  diagnostic markers occur in zero. The guarded installer is checksum-pinned and passes VerifyOnly.

### 2026-08-25 protected rc10 Android candidate and Super Admin shop deletion

- [x] PR #62 merged exact green head `7f21aff57a58825fe9714444829df99778fc1b53` into
  `main` as `86e2803a4991b5050967b2f370f53d5ddb9670e3`. Database run `32821256012` passed
  zero-state migration replay, deterministic seed, lint, every pgTAP suite, and backend
  integration/concurrency checks. Android run `32821256010` passed the complete release gate.
- [x] Protected production deployment `32821910493` applied migration `20260825120000`, installed
  the existing production-only Edge secrets, deployed the three reviewed Functions, passed linked
  database verification, and passed redacted probes against project `skfxfbssfeetquteubcn`. It did
  not create/delete a shop, user, Auth identity, or business record.
- [x] Protected signing workflow `32822345644` passed the Android gate and production-release job
  on exact merged main. Artifact `9555080881` contains the 57,477,165-byte
  `GDAD-BAGS-0.2.0-rc10-11-release.apk`, SHA-256
  `D9AEDF2ABCD277F7C2A47A53D4BE25ABA9A1DD49151A2FFC700B0F76E94B23A3`.
- [x] Independent inspection passes APK Signature Scheme v2 with one signer and pinned certificate
  SHA-256 `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`,
  package `com.gdad.bags`, rc10/code 11, SDK 31/36, launcher, label, and all five icon densities.
  Archive inspection finds the production project ref in one file, the development ref in zero
  files, and privileged/diagnostic markers in zero files. No app-store publication step exists.

### 2026-08-20 protected rc9 Android candidate

- [x] Exact merged `main` commit `43ec93100967ff8eb3876734fac7e48f6dc75231` passed the protected
  Android signing workflow `32395027694`; no app-store publication step exists or ran.
- [x] The 57,444,389-byte APK is pinned at SHA-256
  `99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC` with production signer
  certificate SHA-256 `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`.
- [x] Independent inspection passes package `com.gdad.bags`, rc9/code 10, SDK 31/36, launcher,
  v2 signature, production Supabase reference present, development reference absent, and privileged
  key/token/pepper/preview-auth marker absence. The checksum-pinned installer now targets rc9.
- [x] Owner/Salesman identity isolation, core business operations, offline/retry behavior, reporting,
  notifications, stock adjustment, TalkBack, large text, non-touch input, cold start, memory, and frame
  rendering all passed on the signed rc9 physical-device workflow recorded below.
- [x] On 2026-08-24 the operator accepted that completed workflow as the final in-app test and directed
  release finalization without another device cycle. No Android application code changed after the
  tested candidate, so the exact checksum-pinned rc9 bytes remain the final direct-distribution APK.

### 2026-08-16 first production product-to-report workflow

- [x] The new shop first showed the expected empty product state to its authenticated Salesman.
- [x] The operator then signed in as Owner and reported successful creation of the first clearly
  marked test product through **Products and stock**. This is the first intentional production
  business record for the physical workflow.
- [x] The Owner subsequently created the first clearly marked test vendor through **Vendors**.
  Product and vendor master setup now pass.
- [x] After protected migration `20260821103000`, the Owner retried the controlled unpaid purchase
  and reported the authoritative success message `Purchase posted with authoritative totals`.
  This accepts the physical production purchase RPC and atomic bill/receipt/FIFO lot/inventory/
  payable-journal path. No PIN, invoice reference, quantity, cost, or total was collected by the agent.
- [x] After refreshing the production app, the operator reported `stock and vendor due correct`.
  This accepts the physical read-back of the received FIFO stock and unpaid vendor obligation without
  exposing product, vendor, invoice, quantity, cost, total, or identifier values to the agent.
- [x] After the first Salesman sale, the operator reported `sale stock cash and report correct` from
  the Owner views. This accepts the stock decrement, cash settlement, dashboard, and date-range
  sales/FIFO cost/gross-profit report read-back without exposing business values to the agent.
- [x] The Owner posted one controlled sellable return against the first production sale and reported
  `return posted`. This accepts the production return authorization, authoritative cash-refund,
  FIFO-lot restoration, journal, audit, and idempotent receipt path. No business values, record
  identifiers, credentials, or tokens were collected by the agent.
- [x] After refreshing the Owner views, the operator reported `return stock cash and report correct`.
  This accepts the restored FIFO stock, reduced cash, completed return state, and adjusted daily
  return/net-sales/gross-profit reporting without exposing business values or record identifiers.
- [x] The Owner allocated and posted one controlled cash payment against the production vendor bill
  and reported `vendor payment posted`. This accepts the Owner-only payment authorization, bill
  allocation, payable/cash journal, audit, idempotency, and authoritative receipt path without
  exposing the payment amount, vendor, bill reference, credential, or identifier.
- [x] After refreshing the Owner views, the operator reported
  `vendor due cash payment and report correct`. This accepts the reduced bill/vendor due, reduced
  cash, posted payment-history event, and adjusted daily report without exposing business values,
  vendor details, bill references, record identifiers, or credentials.
- [x] The Owner reversed the first controlled vendor payment with a dated reason and reported
  `vendor payment reversed`. This accepts the Owner-only immutable-event reversal, compensating
  payable/cash journal, audit, idempotency, and authoritative receipt path without exposing business
  values, event identifiers, vendor details, bill references, or credentials.
- [x] After refreshing the Owner views, the operator reported
  `vendor payment reversal due cash and report correct`. This accepts restored bill/vendor due,
  restored cash, preserved reversed-event history/reason, and adjusted daily reporting without
  exposing business values, vendor details, references, identifiers, or credentials.
- [x] The Owner posted one controlled quantity-one stock return against the production purchase bill
  and reported `vendor stock return posted`. This accepts the Owner-only return authorization,
  inventory/FIFO-lot decrement, payable reduction, balanced journal, audit, idempotency, and
  authoritative receipt path without exposing business values, vendor/bill details, or identifiers.
- [x] After refreshing the Owner views, the operator reported
  `vendor return stock due history and report correct`. This accepts the quantity-one inventory
  decrement, vendor-due reduction, posted return history/reason, unchanged cash, and adjusted report
  without exposing business values, vendor/bill details, identifiers, or credentials.
- [x] The Owner reversed the first controlled vendor stock return with a dated reason and reported
  `vendor stock return reversed`. This accepts the Owner-only immutable-event reversal, original
  FIFO-lot restoration, compensating payable/inventory journal, audit, idempotency, and authoritative
  receipt path without exposing business values, vendor/bill details, identifiers, or credentials.
- [x] After refreshing the Owner views, the operator reported
  `vendor return reversal stock due history and report correct`. This accepts restored FIFO stock and
  vendor due, preserved reversed-event history/reason, unchanged cash, and adjusted reporting without
  exposing business values, vendor/bill details, identifiers, or credentials.
- [x] The Owner posted one controlled deposit into the production main cash account and reported
  `cash deposit posted`. This accepts the Owner-only deposit authorization, balanced cash/equity
  journal, audit, idempotency, no-overflow validation, and authoritative receipt path without exposing
  the amount, balance, account identifiers, credentials, or tokens.
- [x] After refreshing the Owner views, the operator reported
  `cash deposit balance history and report correct`. This accepts the increased main-cash balance,
  immutable deposit history/description, reversal availability, correct cash reporting, and unchanged
  sales/expense/profit projections without exposing business values, identifiers, or credentials.
- [x] The Owner posted one controlled production expense from the main cash account and reported
  `cash expense posted`. This accepts the Owner-only expense authorization, no-overdraft validation,
  balanced expense/cash journal, evidence equality, audit, idempotency, and authoritative receipt path
  without exposing the amount, balance, account/journal identifiers, or credentials.
- [x] After refreshing the Owner views, the operator reported
  `cash expense balance history and report correct`. This accepts the reduced main-cash balance,
  immutable/reversible expense history, increased expense total, and unchanged gross profit without
  exposing business values, account/journal identifiers, or credentials.
- [x] The Owner posted one controlled production withdrawal from the main cash account and reported
  `ash withdrawal posted` (interpreted as `cash withdrawal posted`). This accepts the Owner-only
  withdrawal authorization, no-overdraft validation, balanced movement journal, audit, idempotency,
  and authoritative receipt path without exposing the amount, balance, identifiers, or credentials.
- [x] After refreshing the Owner views, the operator reported
  `cash withdrawal balance history and report correct`. This accepts the reduced main-cash balance,
  immutable/reversible withdrawal history, correct cash reporting, and unchanged sales/expenses/gross
  profit without exposing business values, account/journal identifiers, or credentials.
- [x] The Owner posted one controlled production transfer from main Cash to main Bank and reported
  `cash to bank transfer posted`. This accepts the Owner-only transfer authorization, no-overdraft
  validation, exact opposing account entries, balanced transfer journal, audit, idempotency, and
  authoritative receipt path without exposing amounts, balances, identifiers, or credentials.
- [x] After refreshing the Owner views, the operator reported
  `cash bank transfer balances history and report correct`. This accepts equal opposing Cash/Bank
  changes, immutable/reversible transfer history, unchanged combined balance, and unchanged operating
  metrics without exposing business values, account/journal identifiers, or credentials.
- [x] After an initial attempt returned the generic safe validation message, the Owner explicitly
  selected the correct controlled Transfer card and reported `success`
  for its reversal. This accepts the Owner-only immutable-event reversal, exact compensating Cash/Bank
  journal, audit, idempotency, and authoritative receipt path without exposing values or identifiers.
- [x] After refreshing the Owner views, the operator reported
  `transfer reversal balances history and report correct`. This accepts restored pre-transfer Cash/Bank
  balances, preserved reversed history/reason, unchanged combined balance, and unchanged operating
  metrics without exposing business values, account/journal identifiers, or credentials.
- [x] The Owner refreshed Notifications, opened the authorized Vendor return notification, followed
  its allowed related-record route, refreshed again, and reported
  `notification opened routed and read receipt correct`. This accepts notification visibility,
  read-receipt persistence, Nepal-time/90-day messaging, and role-safe routing without exposing the
  notification body, business values, record identifiers, credentials, or tokens.
- [x] After refreshing key Owner screens online and disconnecting the phone, the operator reported
  `offline cached reads remain and refresh failed safely`. This accepts owner/shop-scoped cached
  Products, Vendors, Cash and bank, Reports, and Notifications plus last-good-snapshot preservation on
  offline refresh without exposing cached business values, identifiers, credentials, or tokens.
- [x] While still disconnected, the Owner attempted one controlled expense and first reported the safe
  message `financial operations require an internet connection`, then confirmed
  `offline finance blocked balance and history unchanged`. This accepts online-only enforcement, no
  authoritative receipt or local financial mutation, unchanged cache/history, and explicit same-key
  retry availability without exposing the amount, balance, identifiers, credentials, or tokens.
- [x] After reconnecting, the Owner tapped the preserved **Retry same operation** action exactly once
  and reported `reconnect retry expense posted once`. This accepts preservation of the original
  request/idempotency identity across the transient failure and one authoritative connected expense
  receipt without exposing the amount, balance, identifiers, credentials, or tokens.
- [x] After refreshing Finance and the report twice, the operator reported
  `reconnect expense single history balance and report correct`. This accepts exactly one matching
  Expense history row, one cash decrease, one expense-total increase, unchanged Gross profit, and no
  duplicate after refresh without exposing values, identifiers, credentials, or tokens.
- [x] The Owner selected the exact `Expense: Offline rejection test` transaction and reported
  `reconnect recovery expense reversed`. This accepts the Owner-only immutable-event reversal,
  compensating expense/cash journal, audit, idempotency, and authoritative receipt path without
  exposing the amount, balance, account/journal identifiers, credentials, or tokens.
- [x] After refreshing Finance and the report twice, the operator reported
  `recovery expense reversal cash history and report correct`. This accepts one restored Cash effect,
  one reduced effective-Expenses effect, preserved original/reversal history and reason, unchanged
  Gross profit, and no duplicate reversal without exposing values, identifiers, or credentials.
- [x] The Owner selected the exact unreversed `Expense: Launch testing` transaction and reported
  `launch test expense reversed`. This accepts the immutable compensating expense/cash journal, audit,
  idempotency, and authoritative receipt path while restoring that controlled expense effect without
  exposing the amount, balance, identifiers, credentials, or tokens.
- [x] The Owner selected the exact controlled Withdrawal transaction and reported
  `launch test withdrawal reversed`. This accepts the immutable compensating movement journal, audit,
  idempotency, and authoritative receipt path while restoring the withdrawal's Cash effect without
  exposing the amount, balance, identifiers, credentials, or tokens.
- [x] The Owner attempted to reverse the original controlled Deposit after reversing its dependent
  expenses, transfer, and withdrawal. The app returned the generic account-review validation message;
  after refresh, the operator confirmed `eposit reversal safely blocked and state unchang` (interpreted
  as `deposit reversal safely blocked and state unchanged`). No receipt/reversal was created and the
  balance/history remained unchanged, so no funding transaction was fabricated to bypass the guard.
- [x] While connected, the Owner confirmed the logout warning, logged out, verified protected screens
  were unreachable, closed/reopened the process, and reported
  `owner logout login screen and session not restored`. This accepts local session clearing, cache/outbox
  purge invocation, fail-closed navigation, and no process-session restoration without exposing the
  Owner login ID, PIN, cached values, credentials, or tokens.
- [x] The operator signed in using the existing Salesman account, verified the exact role-scoped
  destinations and current-shop product, confirmed Owner-only Vendor/Finance/Account data was absent,
  and reported `salesman login role screens correct and no owner cache`. This accepts same-device user
  identity switching, cache isolation/repopulation, and navigation authorization without exposing the
  Salesman login ID, PIN, cached values, credentials, or tokens.
- [x] The Salesman logged out and the operator signed in privately as the existing Owner, refreshed key
  Owner views, confirmed full Owner destinations and one authoritative copy of shop data, and reported
  `owner restored full screens data correct and no duplicates`. This accepts clean local purge/repopulation,
  Owner role restoration, and no stale Salesman session or duplicate cache/business rows without exposing
  either login ID, PIN, business values, identifiers, credentials, or tokens.
- [x] The Owner posted one controlled quantity-one Manual add/Stock found adjustment at the displayed
  FIFO unit cost and reported `stock manual add posted`. This accepts the Owner-only adjustment
  authorization, authoritative product/FIFO-lot increment, inventory movement/journal, audit,
  idempotency, and receipt path without exposing product, cost, stock value, identifiers, or credentials.
- [x] The Owner then posted one quantity-one Manual remove/Data correction against the exact newly
  created one-unit lot and reported `stock manual remove posted`. This accepts lot-bounded decrement,
  product stock restoration, compensating inventory adjustment journal, audit, idempotency, and
  authoritative receipt without exposing product, cost, stock value, identifiers, or credentials.
- [x] After refreshing Stock adjustments, Products, and the bounded report twice, the operator reported
  `stock adjustment net quantity value history and report correct`. This accepts baseline quantity/value
  restoration, exactly one +1 and one -1 immutable movement, no duplicate, and no unintended Sales,
  Returns, Expenses, Cash, or Vendor-due effect without exposing business values or identifiers.
- [x] With Android TalkBack enabled, the operator traversed the Owner dashboard, Products, an existing
  Return, Reports, Notifications, and one offline Refresh error, then reported
  `talkback order labels and offline announcement correc` (interpreted as `talkback order labels and
  offline announcement correct`). This accepts logical focus order, understandable labels, one-time
  error announcement, cached-data retention, and no credential speech.
- [x] With Android font size set to approximately 200%/largest in portrait, the operator traversed key
  screens and dialogs, exercised keyboard appearance/closure, logged out and privately restored Owner,
  and reported `200 percent text portrait keyboard and actions correc` (interpreted as `200 percent text
  portrait keyboard and actions correct`). This accepts scroll reachability, no clipping/overlap,
  keyboard avoidance, and large-text Login/Dashboard usability without exposing credentials or values.
- [x] Using non-touch input, the operator traversed forward/back focus, edited fields inside the New
  expense dialog, confirmed focus containment, activated Cancel, exercised Report input/action focus,
  and reported `non touch focus dialog editing and activation correct`. This completes hardware-keyboard
  or Switch Access focus-order, dialog-containment, editing, and activation sign-off without posting data.
- [x] The first authorized Redmi performance run exposed that `measure-android-performance.ps1` treated
  a single ADB output line as a scalar string and selected its first character as the device serial.
  The capture tool now materializes device lines as an array before count/index operations, matching
  the already hardened installer pattern and preserving zero/multiple-device fail-closed behavior.
- [x] Physical performance measurement on signed rc9 now passes all release budgets: five cold starts
  `[1742, 702, 665, 679, 698]` ms (median 698 ms), post-workflow warm PSS 134,898 KiB, and 51 janky of
  3,051 frames (1.67%). The device was Redmi `25062RN2DA`, Android 16/API 36; no serial or business
  value is recorded. The capture fix also uses explicit ADB argument arrays so `-W` remains literal.

### 2026-08-16 first production Salesman acceptance

- [x] The operator installed rc8, signed in as the intended Owner, and successfully created the
  first Salesman after using a new Login ID and a server-accepted private PIN.
- [x] Protected aggregate-only run `31938915526` passed on exact main
  `9d4585bda7106e196e5e4d7f5ee9cd138f21feb1`. Production now has exactly 3 Auth users, 3 profiles,
  3 private credentials, 1 completed Salesman request, 1 Salesman audit, and 1 active Salesman
  membership, with zero failed or reserved Salesman requests.
- [x] The same snapshot retains 1 active Owner membership, 1 eligible Owner-to-Salesman authority
  pair, and the prior completed Owner transaction. No Login ID, name, PIN, token, verifier, hash,
  or business value was queried or logged.
- [x] The operator subsequently reported successful physical login with the new Salesman account.
  This accepts the production PIN/session/profile/membership path and Android role transition; the
  PIN and Login ID were not sent to or requested by the agent.
- [x] From that authenticated session, the operator confirmed that account and shop administration
  options are hidden. This accepts the Salesman navigation allowlist on the physical rc8 device;
  no credential, account identifier, or business value was collected.
- [x] The Sales/New Sale workflow opens for the Salesman and shows the safe empty product state for
  the new shop. No demo, phantom, or cross-tenant product is exposed; no sale was submitted.
- [x] After the Owner posted and verified the first FIFO stock, the Salesman completed one fully paid
  controlled cash sale and the operator reported `sale posted`. This accepts the production
  Salesman authorization, configured-price/full-settlement policy, atomic FIFO allocation, negative
  inventory movement, revenue/COGS/inventory/cash journals, audit, and authoritative receipt path.
  No product, quantity, price, total, customer value, account identifier, PIN, or token was collected.

### 2026-08-16 rc8 provisioning reservation correction, production deployment, and signed candidate

- [x] `manage-users` now reads only the PostgreSQL SQLSTATE from a failed service-role RPC and maps
  uniqueness to HTTP 409, invalid input to HTTP 400, authorization to HTTP 403, and all unknown
  failures to HTTP 503. Raw database messages/details never enter the client response.
- [x] A reservation-stage failure returns immediately without reconciliation/compensation because
  the failed database transaction created no reservation. Later-stage compensation remains intact.
- [x] Android account creation maps HTTP 409 to: `This Login ID is already in use. Choose a
  different Login ID.` Other account-administration conflicts retain their prior refresh guidance.
- [x] rc8 source/release tooling uses version code 9 and the installer is deliberately checksum-
  pinned to the independently verified protected signed artifact.
- [x] PR #46 passed Android and fresh-database CI and merged exact head `b0626355` as main
  `45a2a852`. Protected production deployment run `31937292138` passed from-zero replay, migration
  preview/apply, Function deployment, linked lint/history, and redacted probes.
- [x] Protected reconciliation run `31937448494` found one eligible Owner-to-Salesman authority
  pair and zero Salesman requests/memberships/audits, proving no partial failed-attempt state.
- [x] Protected release run `31937446942` passed exact-main verification/signing and produced the
  independently verified 57,427,997-byte rc8 APK with SHA-256
  `CCDB61CAEB52392EC6499D32935496B1C4E65ADC1B7DEB2F239DE33D588817B0`.

### 2026-08-16 verified rc7 MTP delivery

- [x] Copied the exact signed `GDAD-BAGS-0.2.0-rc7-8-release.apk` to Redmi 15
  `Internal shared storage/Download` through Windows MTP after proving the filename was absent.
- [x] Read the phone item back into an ignored local directory and verified exactly 57,427,993 bytes
  with SHA-256 `52BF20F3F83D7AD8E39A2B84CD1090DF7D2934E071032B011650A40A927992FC`, then removed the
  temporary read-back. No phone file was overwritten, and no app data was installed or cleared.

### 2026-08-16 first production Owner acceptance

- [x] The operator installed the exact signed rc6 candidate and reported successful first Owner
  creation from the physical Android device.
- [x] Protected aggregate-only run `31930685338` passed on exact main
  `0dc3bbd1ae2cb3893cd70c46e9e8c014669a2729`. Production now has 2 Auth users, 2 profiles,
  2 private credentials, 1 active Super Admin, 1 completed Owner request, 1 Owner audit, and 1
  active Owner membership, with zero failed or reserved Owner requests. No identity, name, PIN,
  token, hash, or business value was queried or logged.
- [x] Production reports 2 active shops and 2 eligible Owner-authority pairs, up from the prior
  single-shop snapshot. This does not invalidate the completed Owner transaction; Owner login and
  shop-scope testing must confirm the intended tenant before business fixtures are added.

### 2026-08-15 rc6 protected Owner-session correction and signed candidate

- [x] Aggregate-only production reconciliation proved one active Super Admin, one active shop, and
  one eligible Owner-authority pair, with zero Owner requests, memberships, or audits. The rejected
  phone action stopped at hosted session verification before reservation or commit; no identity,
  credential, PIN, token, or business value was queried or logged.
- [x] Protected account actions now refresh the current hosted session before sending. HTTP 401
  reports an actionable sign-out/sign-in message while a true HTTP 403 retains the permission
  denial. A committed account mutation remains successful when only the follow-up directory refresh
  is pending, preventing unsafe duplicate submissions.
- [x] PR #40 merged exact tested head `3b97341b64f4b1d0eabd35694c418b55fa6fca12` as main
  `012817f5d631fc65dc041c6789100feadcb9d77d`. Protected run `31870004171` passed the full Android
  gate and production review, then built the signed 57,427,997-byte rc6 APK. Its SHA-256 is
  `55FC4438B3A7083B9157ED7FE1C95D74E66B8C001B170718DE16C9A718CD1D9A`; approved signer,
  package/version, SDK 31/36, production project, and absence of the development project all pass.

### 2026-08-12 rc3 physical production login and first-shop creation

- [x] After installing the rc3 replacement, the operator's previously verified production ID/PIN
  reached production and completed successfully. Aggregate state at the test time shows
  `failed_attempts=0`, no credential/source lockout, and fresh credential/rate timestamps; no ID,
  PIN, session, key, or request body was read or logged.
- [x] The physical app created the first production shop through the protected idempotent RPC.
  Reconciliation proves exactly one active shop, one completed creation request/result, one immutable
  shop-create audit, and all 11 distinct active system-managed financial accounts.
- [x] Production remains clean beyond bootstrap: zero memberships, products, sales, and journal
  entries. This is the correct boundary before creating the first Owner account and testing the
  shop-scoped role/core workflow.

### 2026-08-12 rc3 Android production-login transport fix

- [x] Root cause reproduced safely against production: the typed `functions-kt` 3.6.0 invocation
  serialized a JSON body but did not explicitly declare its media type. The strict production
  handler returned HTTP 400 `INVALID_REQUEST` before credential verification; the same random,
  non-account request with `Content-Type: application/json` passed request validation and returned
  the expected HTTP 401 `INVALID_CREDENTIALS`.
- [x] Android PIN login now explicitly sends `application/json`; remote HTTP 400 no longer blames
  valid credentials and instead directs operators to install the latest APK. Regression tests pin
  both the transport header and safe message. Explicit production builds now prefer protected
  environment URL/key values over ignored development Gradle properties, while normal debug builds
  retain their existing local-property precedence.
- [x] Version advanced to `0.2.0-rc3`/4 and the replacement was production-signed locally. The
  57,427,997-byte APK has SHA-256
  `780ECA05D898116AB28130A102E73714EC93F4422F59CE7B42E42AF8B67981EA`, the approved signing
  certificate, package `com.gdad.bags`, production project `skfxfbssfeetquteubcn`, and SDK 31/36.
  All 178 tests passed; lint completed with zero errors/15 existing warnings; installer `VerifyOnly`
  passes the immutable rc3 identity.

### 2026-08-12 production credential and authenticated-profile verification

- [x] The exact production login ID and masked 6-8 digit PIN entered by the operator completed the
  deployed `pin-login` flow with HTTP 200. The returned session was accepted by Supabase Auth and
  its subject loaded exactly one enabled `super_admin` profile through authenticated RLS.
- [x] The approved signed APK was independently reverified as package `com.gdad.bags`, version
  `0.2.0-rc2`/3, production project `skfxfbssfeetquteubcn`, approved signer, and approved SHA-256.
  No ADB device is exposed, so Windows cannot inspect or replace the currently installed package.
- [x] The disposable verifier/result were deleted after the test. No login ID, PIN, publishable key,
  access token, refresh token, or session was printed, written to the repository, or retained.

### 2026-08-12 production Super Admin bootstrap

- [x] Created the sole initial production Super Admin through the masked local helper after explicit
  full-phrase confirmation; bootstrap HTTP response and PIN login both succeeded, and the returned
  login JWT subject matched the created Auth subject.
- [x] Independent management reconciliation confirms exactly one Auth user, one profile, one enabled
  `super_admin`, one private login credential, one account audit event, zero shops, and zero business
  audit events. No account identifier, display name, PIN, session, token, or key was logged here.
- [x] The one-time `GDAD_BOOTSTRAP_TOKEN` was removed; exactly the three intended long-lived GDAD
  secret names remain. Client-safe production health probes pass Auth, REST denial, malformed Edge,
  and protected-function denial boundaries without privileged credentials or mutation.

### 2026-08-11 uninterrupted production-backup restore drill — RPO/RTO proven

- [x] Protected backup run `31428910379` produced fresh ciphertext-only artifact
  `gdad-production-daily-20260811T085706Z`; catalog/companion checksum and authenticated inner
  manifest passed for production ref `skfxfbssfeetquteubcn`, migration head
  `20260802163000_initial_shop_provisioning`, 87,666 bytes, and SHA-256
  `05af91c225e59126db068675aa31cf2591daef56069759bfec2202132c7d494d`.
- [x] From clean timed start `2026-08-11T14:58:35.5651536+05:45`, paused only development and
  created same-region/Postgres disposable target `vdyjynwmvjfkpdbbaqpd`; production stayed
  read-only. Preventive fresh-target ACL normalization ran before the unedited logical restore.
- [x] Restored roles/schema/data and all 28 migration-history entries, then passed Supabase lint,
  21 pgTAP plans/723 assertions, exact 74-table zero-row recovery, RLS/tenant/FIFO/stock/ledger/
  idempotency/audit reconciliation, and the complete committing multi-session concurrency harness.
- [x] Removed fixtures to the zero-row source baseline; installed only new drill secrets; deployed
  all three repository Functions; received bootstrap HTTP 201 and PIN-login HTTP 200; and read
  exactly one enabled `super_admin` profile through authenticated RLS. The one-time secret was
  removed and no drill login/PIN/session was retained.
- [x] App verification completed `2026-08-11T16:08:27.1392027+05:45`: RPO
  `00:16:29.5651536` passes 24 hours and RTO `01:09:51.5740491` passes four hours. The target,
  database credential, and all plaintext were destroyed; preserved ciphertext hash still matches.
  Development and production are `ACTIVE_HEALTHY`, only development is linked, and production was
  unchanged. Result: **Functional PASS / RPO PASS / RTO PASS**.

### 2026-08-10 isolated production-backup restore drill — functional recovery

- [x] Restored encrypted production backup `gdad-production-daily-20260803T073503Z` only to
  disposable project `tjhqfjjxzgumnnzgzwnq` in the source region/Postgres major version. The
  verified roles/schema/data transaction and separate migration-history transaction both exited 0;
  the source dumps were not edited.
- [x] Corrected fresh-target ACL inheritance in one target-only transaction, then passed Supabase
  database lint, all 21 pgTAP plans/723 assertions, exact 74-table/zero-row recovery, migration
  head/count, RLS, tenant, FIFO/stock, ledger, idempotency, audit, and complete multi-session
  concurrency/invariant checks.
- [x] Installed only newly generated drill secrets, deployed all three repository Functions, created
  a random drill-only Super Admin, received bootstrap 201/PIN-login 200, and read exactly its enabled
  `super_admin` profile through authenticated RLS. Source users/PINs and production secret values
  were not used; the one-time bootstrap secret was removed.
- [x] Deleted the disposable cloud project, its Credential Manager database secret, and all plaintext
  restore files while preserving encrypted evidence. Development resumed to `ACTIVE_HEALTHY`,
  production remained `ACTIVE_HEALTHY`, and the workspace link remains development.
- Recovery-point age at drill start was `00:57:34.273`, within the accepted 24-hour RPO. Functional
  recovery is **PASS**. Wall-clock start-to-app-verification was `6d 23:09:19.063` because execution
  stopped between 2026-08-03 and 2026-08-10; the accepted four-hour RTO is therefore **not proven**
  and an uninterrupted timed rerun remains required before closing Task 6.5/launch recovery evidence.

### Execution-plan baseline

- [x] **Task 0.1:** Verified this workspace is the canonical `main` checkout of
  `sanjubaba21/GDAD_APP` at commit `1be9d33`, with the expected Android, Supabase,
  documentation, workflow, and build paths present.
- [x] **Task 0.2:** Reconciled source, migration, Edge Function, code-map, hosted-state,
  and UTF-8 status. The plan-referenced external handoff file was not available; the
  supplied execution plan and repository status were used instead.
- [x] **Task 0.3:** Established a passing Android/Edge/hosted-lint baseline. Local
  database runtime tests remain delegated to the successful current-HEAD GitHub CI run
  because Docker is unavailable on this machine.
- [x] **Task 1.1:** Fixed the authoritative Android PIN-session import, encrypted
  storage, restore/refresh/logout/revocation behavior, and deliberately disabled
  deep-link policy for the PIN-only first release.
- [x] **Task 1.2:** Login and provisioning now share one tested login-ID/PIN contract,
  versioned HMAC material, Argon2id hash creation, and fail-closed verification helper.
- [x] **Task 1.3:** Privileged account provisioning validates authoritative hierarchy,
  reserves normalized login IDs, creates/reconciles an exact managed Auth subject,
  attaches profile/membership/private verifier state idempotently, compensates partial
  failures, and writes immutable PIN-free audit events. Fresh-database CI covers
  allowed/denied, retry, collision, cross-shop, and compensation paths; the controlled
  Super Admin bootstrap and subject-matched hosted PIN login both succeeded.
- [x] **Task 1.4:** Dedicated account administration implements authoritative hierarchy,
  actor-PIN reauthentication, per-actor/source limits, idempotent disable/re-enable/PIN
  reset, target refresh-session revocation, safe immutable audits, generic failures,
  and protected Super Admin targets. Edge tests and fresh-database pgTAP cover allowed/
  denied paths and state transitions; migration/function deployment and hosted denial
  smoke verification succeeded.
- [x] **Task 1.5:** Hosted PIN login now proves the exact managed Auth subject, rejects
  reuse of the generated one-time email token, refreshes to the same JWT subject, and
  returns the matching authenticated Super Admin profile. Generic malformed, invalid-
  key, unknown/wrong, rate-limit, fifth-failure lock, and success-reset paths are covered
  by hosted HTTP evidence plus Edge/pgTAP tests; only redacted booleans were retained.
- [x] **Task 1.6:** The documented table/RPC permission matrix is enforced by grants,
  tenant RLS, private service-only RPCs, and 42 fresh-database assertions covering all
  required principals, cross-shop reads, forged writes, and immutable rows. Disabled
  stale sessions now fail closed for self-profile and membership reads as well.
- [x] **Task 2.1:** The canonical data dictionary covers 35 existing/planned objects,
  including 29 detailed business tables, private operation state, atomic transaction
  boundaries, derived-value reconciliation, lifecycle/reversal rules, RLS ownership,
  expected indexes, and the 11-group Task 2.2 decision register.
- [x] **Task 2.2:** Product owner approved D1–D11. The policy contract maps stock,
  pricing, credit, payment, return, purchasing, date/period, product-code, visibility,
  rounding/tax, and retention choices to schema, RPC, permission, UI, and test effects.
- [x] **Task 2.3:** Hosted sales/payment/return schema enforces same-shop keys,
  server-reconciled totals and full FIFO allocation, non-credit settlement, derived
  credit due, cumulative return/allocation limits, refund caps, idempotency, RLS, D9
  cost restriction, and no direct Android writes. All 31 new fresh-database assertions pass.
- [x] **Task 2.4:** Hosted vendor/purchasing schema enforces permanent normalized invoice
  identity, same-shop bill/receipt/payment/return chains, exact receipt-to-FIFO-lot
  quantity/cost, fully allocated payments, derived vendor due, cumulative receipt/return
  limits, Owner-only reads, and no direct Android writes. All 31 new assertions pass.
- [x] **Task 2.5:** Hosted ledger schema provides constrained account masters,
  non-overlapping periods, immutable balanced journals/entries, typed same-shop sources,
  exact reversals, derived-only balances, expense reconciliation, Owner-only reads, and
  no direct Android writes. All 28 new assertions pass.
- [x] **Task 2.6:** Hosted notifications enforce tenant-safe user/role targeting,
  exact 90-day expiry, typed source references, protected per-user first-read state,
  bounded backend cleanup, and secret-free payloads. Private business audits capture
  authorized actor/shop/operation/record evidence and reject every update/delete.
  All 39 new assertions pass and hosted lint is clean.
- [x] **Task 2.7:** Deterministic local fixtures cover two shops, Super Admin/Owner/
  Salesman roles, products and multiple FIFO lots, a received vendor bill, partially
  paid credit sale and return, stock movements, expense/transfer journals, and
  targeted/read notifications. CI proves identical hashes across two resets, commits
  no usable credential, cleans fixture IDs before pgTAP, and passes every suite.
- [x] **Phase 2 exit gate:** Fresh migrations apply from zero, database lint and every
  pgTAP suite pass, and the first-release data model, policies, retention, and local
  development fixtures are documented.
- [x] **Task 3.1:** Hosted Owner-only product management atomically creates, updates,
  and archives with tenant validation, normalized SKU/barcode identity, permanent
  historical code reservation, request-fingerprint idempotency, draft-operation
  archive guards, and safe immutable audit snapshots. All 45 assertions pass.
- [x] **Task 3.2:** Hosted Owner-only purchase receipt atomically creates the received
  bill/receipt, immutable lines, exact FIFO lots and movements, stock projection,
  optional allocated payment, due result, balanced journals, and safe audit evidence.
  Request fingerprints, deterministic locks, exact retries, rollback, and tenant denial
  are covered by 41 assertions; hosted history and lint are clean.
- [x] **Task 3.3:** Hosted atomic FIFO sale derives tenant/role, enforces configured
  Salesman pricing/full payment and Owner-only discounts/credit, locks products and lots
  deterministically, forbids negative stock, computes exact price/cost/due, and writes
  settlement, balanced journals, low-stock notification, retry result, and safe audit.
  All 50 assertions pass; hosted history and lint are clean.
- [x] **Task 3.4:** Hosted Owner-only sale return serializes on the sale, enforces the
  30-day and cumulative limits, restores sellable units to exact reverse allocations,
  preserves damaged evidence without saleable stock, applies due-first credit and exact
  paid-value refunds, and writes status, balanced journals, notification, retry result,
  and safe audit atomically. All 53 assertions pass; hosted history and lint are clean.
- [x] **Task 3.5:** Hosted Owner-only reason-coded inventory adjustment creates new FIFO
  lots for additions and consumes specified lots for damage/loss/removal without
  rewriting receipt history. Exact movements/projection, positive-cost balanced journal,
  zero-cost treatment, Owner notification, retry result, and safe audit are atomic. All
  51 assertions pass; hosted history and lint are clean.
- [x] **Task 3.6:** Hosted Owner-only vendor payments allocate one cash/bank event across
  one or more bills without overpayment; original-lot vendor returns cannot exceed
  available stock or unpaid due; and retry-safe payment/return reversals create exact
  compensating journals and stock movements. Due remains derived, cross-shop/direct
  writes fail closed, and all 59 assertions pass with clean hosted history and lint.
- [x] **Task 3.7:** Hosted Owner-only expense, deposit, withdrawal, transfer, and
  reversal operations post exact balanced journals with derived no-overdraft balances,
  fingerprinted retries, and safe audits. Existing/application-created shops receive
  all 11 protected system accounts. The deterministic fixture and all 52 assertions
  pass; hosted history and lint are clean.
- [x] **Task 3.8:** Hosted tenant-safe daily/period reports reconcile sales, returns,
  FIFO COGS/profit, stock quantity/value, low stock, vendor due, account balances, and
  effective expenses. Nepal midnight and role-shaped Owner/Salesman output are enforced;
  four indexed access paths are measured. All 37 assertions pass with clean hosted
  history and lint.
- [x] **Task 3.9:** A fresh Docker-backed CI run passes Edge verification, migration
  replay, deterministic seed/reset, lint, every pgTAP suite, and a real multi-session
  integration harness covering competing stock, vendor-payment, and cash debits; exact
  retries; partial returns; rollback cleanup; disabled users; cross-shop isolation;
  balanced journals; and report reconciliation.
- [x] **Phase 3 exit gate:** Every first-release backend mutation is atomic,
  idempotent, tenant-safe, and audited; trusted reports reconcile with authoritative
  records; and GitHub Actions run `30293062132` passes from a fresh database.

### Android foundation

- [x] **Task 6.4 repository gate:** Every production PostgREST/Room read is bounded;
  oversized remote results fail closed; relationship assembly and Compose snapshot derivations
  avoid repeated whole-list scans; authenticated startup loads data by destination instead of
  activating every feature. Performance budgets, an ADB measurement tool, release checks, lint,
  artifact inspection, and the rebuilt test APK are complete. Physical-device measurements and
  the sandbox-blocked Room/Robolectric assertion remain explicit external evidence gates.
- [x] **Task 6.2:** Completed Android, release-artifact, Supabase database/Edge, session,
  dependency, and CI security hardening. No known high-severity source/development-backend
  finding remains; 160 Android tests, 25 Edge tests, 697 pgTAP assertions, the concurrency
  harness, release scans, lint, hosted smoke checks, and Deno's advisory audit all pass.
- [x] **Task 6.1:** Completed the automated coverage audit and closed exact-money,
  authentication shell/ViewModel, FIFO integrity, overflow validation, and durable-outbox
  retry gaps. Every production ViewModel and first-release destination has focused evidence;
  the full gate passes 156 tests/43 suites, release assembly, and lint with zero errors.
- [x] **Task 5.10:** Added an all-role RLS notification feed, dashboard unread badge,
  category/detail and authorized related-record navigation, Room v6 retention/source cache,
  and immediate idempotent offline mark-read through the protected outbox. All 133 tests,
  release/lint/diff gates pass and the installable APK was rebuilt.
- [x] **Phase 5 exit gate:** Every first-release destination now has a functional Android
  workflow, no static/demo dashboard values or feature placeholders remain, and each slice
  has focused repository/ViewModel/Compose coverage plus the full regression gate.
- [x] **Task 5.9:** Replaced demo dashboard totals with trusted cached daily reports,
  explicit cache age/refresh, and true zero states. Added Owner/Salesman Nepal-date period
  reports with fail-closed role/shop validation and Owner-only cost/profit/vendor/finance
  values. All 122 tests/release/lint/diff gates pass and the installable APK was rebuilt.
- [x] **Task 5.8:** Added an Owner-only cash/bank ledger with derived immutable balances,
  journal effects/history, exact whole-paisa expense/deposit/withdrawal/transfer forms,
  protected immutable reversals, exact-key retry/conflict refresh, and authoritative
  post-operation balances/identifiers. All 113 tests/release/lint/diff gates pass and the
  installable APK was rebuilt.
- [x] **Task 5.7:** Added an Owner-only vendor ledger with reconciled bill dues,
  allocation history, original-lot returnable quantities, allocated cash/bank payments,
  purchase returns, immutable payment/return reversals, exact-key retry/conflict refresh,
  directory/stock refresh, and authoritative receipts. All 105 tests/release/lint/diff
  gates pass and the installable APK was rebuilt.
- [x] **Task 5.6:** Added searchable/filterable RLS sale history, expandable original
  line/payment/return/due detail, Owner-only FIFO allocation/cost visibility, validated
  partial sellable/damaged returns, exact-key retry, visible conflict refresh, stock
  refresh, and authoritative return/refund receipts. All 98 tests/release/lint/diff gates
  pass and the installable debug-signed APK was rebuilt.
- [x] **Task 5.5:** Added Owner/Salesman atomic FIFO POS with active in-stock cart,
  role-correct price/discount/credit controls, cash/bank settlement, online-only exact
  retry, authoritative receipt/FIFO allocation evidence, and stock refresh. All 89
  tests/release/lint/diff gates pass.
- [x] **Task 5.4:** Added searchable/low-stock product summaries, Owner-only FIFO lots,
  movement history and cost, protected online-only reason-coded adjustment forms, exact
  retry, authoritative result, and post-success stock refresh. Salesman sees no cost,
  history, or mutation controls. All 82 tests/release/lint/diff gates pass.
- [x] **Task 5.3:** Added protected idempotent vendor lifecycle, Room v5 cached vendor
  details/trusted dues/account balances, Owner-only purchase cart/review, online-only exact
  retry, server-authoritative totals, FIFO receipt evidence, and post-success stock/vendor/
  account refresh. All 76 Android tests/release/lint gates and fresh-database CI pass.
- [x] **Task 5.2:** Added a searchable Room-backed product/stock catalog for Owner and
  Salesman, Owner-only cost and create/edit/archive controls, protected idempotent
  `manage_product` mutations, exact-key retry/offline outbox behavior, archived-history
  visibility, SQL-state-aware validation/duplicate/conflict messages, and explicit Room
  v3-to-v4 migration. All 67 tests, release safety, release APK assembly, full lint, and
  diff checks pass.
- [x] **Task 5.1:** Added the first complete feature slice: RLS-backed shop/managed-user
  directory, protected `manage-users` and `manage-accounts` calls, Room v3 owner-scoped
  cache, stable retry UUIDs, role-aware ViewModel, Owner/Salesman lists, create forms,
  reauthenticated disable/re-enable/PIN-reset confirmations, session-revocation/audit-safe
  feedback, and accessible denial/error/retry states. Shop create/archive remains excluded
  because no protected first-release backend mutation exists. All 56 tests, release safety,
  release assembly, and full lint pass.
- [x] **Task 4.7:** Added stable Navigation Compose 2.9.8 type-safe routes,
  authentication graph gating, a complete per-role destination policy enforced at both
  navigation and render time, restored back-stack/process state, back controls, and a
  PIN-only fail-closed external-navigation policy with no registered deep links. Shared
  accessible loading/empty/error/ready and confirmation components are verified through
  Robolectric Compose tests. All 45 tests, release safety/APK assembly, and full lint pass.
- [x] **Phase 4 exit gate:** Production authentication, testable DI, typed remote calls,
  owner-scoped Room cache/outbox, explicit offline policy, and stable role-gated navigation
  are implemented and verified.
- [x] **Task 4.6:** Added Room schema v2 durable mutation outbox with owner/user scope,
  stable UUID idempotency keys, payload credential/size guards, WorkManager connected-
  network execution, bounded exponential retry, stale-claim recovery, and terminal safe
  failure state. Product management and notification reads may queue; financially risky
  and administrative mutations are explicitly online-only. Logout/identity change purges
  pending work, read refresh preserves it, and the dashboard surfaces owner-scoped
  resolution notices. All 37 unit tests, release safety/APK assembly, and full lint pass.
- [x] **Task 4.5:** Added Room 2.8.4/KSP persistence for profile/membership, products,
  stock, vendors, recent sales, accounts, dashboard, and notifications. Owner-filtered
  Flows, transactional complete-snapshot refresh, last-good retention, row ownership
  validation, session-integrated activation/purge, committed schema v1, explicit-only
  migrations, and no destructive fallback are covered by 29 tests. Release assembly,
  release safety, lint-vital, and full lint pass.
- [x] **Task 4.4:** Centralized typed function/query DTOs, bounded remote execution,
  validation/unauthorized/conflict/offline/timeout/rate-limit/unknown classification,
  explicit retry disposition, single authenticated refresh/retry, cancellation
  preservation, sanitized diagnostics, and safe domain error mapping. All 21 unit tests,
  release APK assembly, release safety verification, and full lint pass.
- [x] **Task 4.3:** Removed `PreviewAuthRepository` and all prefix-derived role behavior
  from the production source set. Release pre-build now verifies the production repository
  binding and rejects preview authentication, secret/service-role keys, and hard-coded PIN
  assignments. Unit tests, release APK assembly, release lint-vital, and full lint pass.
- [x] **Task 4.2:** Production Android authentication invokes hosted `pin-login`, imports
  standard sessions into AES-GCM storage backed by a non-exportable Android Keystore key,
  enables automatic refresh, restores and revalidates the Auth subject on startup, and
  loads display name/role/shop only from active RLS-protected rows. Login/restore/logout
  are serialized, errors are sanitized, failed identity validation clears local Auth,
  and logout clears locally even when the remote call fails. All 12 unit tests, debug APK
  assembly, and lint pass; Task 4.2 adds no lint warning.
- [x] **Task 4.1:** Explicit constructor injection now separates the application
  composition root, stateless Supabase client factory, domain authentication contract
  and use case, injected ViewModel, and Compose UI state. `GdadApplication` owns one lazy
  application-scoped client, composables locate no services, and deterministic fakes are
  covered by unit tests. Debug compilation, all four unit tests, APK assembly, and lint
  pass; the architecture and Task 4.2/4.3 transition are documented.
- [x] Kotlin Android application created with Jetpack Compose.
- [x] Package/application ID set to `com.gdad.bags`.
- [x] Minimum SDK set to 31 (Android 12); compile and target SDK set to 36.
- [x] Java/Kotlin toolchain set to Java 17.
- [x] Material 3 Compose theme and edge-to-edge activity configured.
- [x] Internet and notification permissions declared.
- [x] Supabase Auth, PostgREST, and Functions client libraries declared using the
  Supabase Kotlin BoM, with an Android Ktor engine and Kotlin serialization plugin.
- [x] Supabase URL and publishable key can be supplied through Gradle properties or
  environment variables without hard-coding credentials.
- [x] The application-owned dependency container lazily creates one guarded Supabase
  client with Auth, PostgREST, and Functions when configuration is present.
- [x] Firebase Auth, Firestore, and Functions dependencies removed. Firebase is not the
  primary backend; FCM may be evaluated separately for push notifications later.
- [x] Portable JDK, Android SDK, Gradle tooling, and `build-apk.ps1` build flow prepared.
- [x] Debug APK generated at `GDAD-BAGS-test.apk`.
- [x] GDAD Bags artwork configured as the standard and round Android launcher icon
  across all supported density buckets.

### Authentication UI and navigation shell

- [x] User ID and numeric PIN login UI implemented.
- [x] Production validation requires a nonblank user ID and a 4–8 digit PIN.
- [x] `UserRole` supports `SUPER_ADMIN`, `OWNER`, and `SALESMAN`.
- [x] Authoritative profile/membership reads determine role and shop; user-ID prefixes
  have no authorization effect.
- [x] `UserSession` includes user ID, display name, role, shop ID, and
  authentication time.
- [x] Login and logout transitions implemented for the current in-memory session.
- [x] Role-specific dashboard shells and static action cards implemented.

### Domain and inventory foundation

- [x] Non-negative `Money` value type implemented in paisa.
- [x] Overflow-safe money addition and quantity multiplication implemented.
- [x] Initial `Product`, `InventoryLot`, `LotAllocation`, `SaleLine`, and
  `ProductReturn` domain models implemented.
- [x] FIFO allocation consumes lots by `receivedAt`, then lot ID.
- [x] FIFO allocation records exact lot quantities and costs.
- [x] Insufficient stock produces a shortage quantity without inventing a cost.
- [x] Returns restore stock to the original allocations in reverse allocation order.
- [x] Domain guards prevent invalid lot quantities and over-restoration.
- [x] Unit tests cover FIFO order, shortage reporting, and return restoration.

### Documentation

- [x] README documents the production authentication boundary, release safety gate,
  build process, and architecture direction.
- [x] Canonical project status and handoff file created (`PROJECT_STATUS.md`).

### Supabase backend foundation

- [x] Supabase CLI `2.101.0` pinned with a reproducible `pnpm-lock.yaml`.
- [x] Repository-local `supabase/config.toml`, migration, seed placeholder, database
  documentation, and pgTAP test layout created.
- [x] Public tables created for shops, user profiles, shop memberships, products, FIFO
  lots, and append-only inventory movements.
- [x] Server-only PIN verifier state isolated in `private.login_credentials`.
- [x] Tenant relationships enforced with foreign keys, composite keys, checks, unique
  idempotency constraints, and indexes.
- [x] RLS enabled on every initial table with authenticated tenant-scoped read policies.
- [x] Anonymous access and direct authenticated inserts, updates, and deletes denied.
- [x] GitHub Actions database workflow added to apply migrations, lint functions, and
  run pgTAP tests on a Docker-backed runner.
- [x] Production user-ID/PIN identity, verifier, session-exchange, error, and Android
  session contract documented with concrete B3.2/B3.3 acceptance tests.
- [x] PIN verifier schema requires Argon2id PHC strings and records an external Edge
  Function pepper version without storing the pepper.
- [x] Atomic per-source attempt windows and per-account failure/reset/15-minute lockout
  operations implemented as service-role-only security-definer RPCs.
- [x] `pin-login` validates request shape and publishable keys, performs HMAC-peppered
  Argon2id verification/dummy work, and implements subject-checked Auth token exchange
  without exposing server credentials.
- [x] Random PIN/rate peppers and a dummy verifier exist only as hosted Edge secrets;
  active function version 5 disables platform JWT verification so the handler validates
  `sb_publishable_` keys itself.

## Work in progress

### 2026-09-05 rc13 negotiated-pricing release

- [x] Android implementation, backend forward migration, policy/docs, regression fixtures, rc13/code14
  versioning, focused tests, SQL syntax/replacement checks, and complete local Android gate.
- [ ] Publish branch and require both database and Android CI to pass on a pull request.
- [ ] Merge the exact green head, deploy only migration `20260905120000` through the protected
  production workflow, then build and independently verify the protected signed rc13 APK.
- [ ] Advance the guarded installer only after the protected artifact checksum/signature/package/
  version/production-binding scans pass; upgrade the connected phone without clearing app data and
  smoke-test one negotiated-price sale with an authorized disposable product/transaction plan.

### 2026-08-30 Super Admin shop-deletion preflight correction

- [x] Production read-only reconciliation returned only aggregate counts: one active Super Admin,
  one matching credential, zero active credential locks, zero accumulated Super Admin login
  failures, two active shops, and zero shop-deletion request rows (reserved, failed, complete, or
  `REAUTH_FAILED`). No PIN, identifier, login ID, token, hash, or request payload was returned.
- [x] Android deletion UX now keeps the confirmation dialog open across a failed request, identifies
  the three independent form requirements, normalizes the typed slug to the hosted lowercase
  contract, distinguishes field-format acceptance from server authorization, and explicitly asks
  for the current signed-in Super Admin PIN rather than an Owner or device PIN. Source and protected
  artifact naming advance to rc12/code13 so phones can upgrade from rc11/code12.
- [x] Terminal deletion failures now discard the rejected PIN/idempotency request; only backoff-safe
  transport/rate failures retain the exact request for retry. Focused account regressions and the
  complete Android release-safety/test/lint/build gate pass.
- [x] Published through PR #68, merged exact green head, built the protected production-signed rc12
  APK, independently verified it, and checksum-pinned the guarded installer.
- [x] Installed the exact pinned rc12 APK on a Xiaomi Android 14 phone and independently confirmed
  package, version, installed-byte checksum, launch, authenticated dashboard, and trusted refresh.
- [ ] Retest only a clearly disposable empty shop deletion with the currently signed-in Super Admin.

- [x] **Owner:** Codex. **Task:** diagnose the reported rc11 Super Admin PIN rejection without
  collecting the PIN or exposing account identifiers. Privacy-safe production reconciliation is
  complete and showed the request did not reach persisted hosted deletion processing; the rc12
  client/preflight correction and fresh terminal retry behavior are now merged, signed, verified,
  and pinned. No credential or application data was changed.

- [x] **Owner:** Codex. **Task:** correct legacy Super Admin PIN rejection during protected shop
  deletion. Root cause is the rc10 client and Function request parsers accepting only 6–8 digits,
  while an already-provisioned Super Admin may retain a legacy 4- or 5-digit verifier and an active
  restored session. The shared server verifier, login request, account-administration reauthentication,
  Android login, repository guard, and confirmation UI now accept 4–8 digits only when checking an
  existing credential. New account and reset PIN creation remain strict at 6–8 digits. Role/subject
  binding, exact shop slug, audited reason, rate limiting, lockout, idempotency, transactional deletion,
  and managed-Auth cleanup are unchanged. Source advances to rc11/code 12; the guarded installer
  is now pinned to independently verified rc11 bytes. All 36 Edge tests plus formatting/lint/type-check
  pass; the focused 31-test Android set and complete 109-task Android release gate pass with zero lint
  errors. PR #64, protected Function deployment, protected signing, independent package/signature/
  binding inspection, and guarded installer verification all pass. A physical retry should use the
  installed rc11 version and a clearly disposable empty test shop first.

- [x] **Owner:** Codex. **Task:** add fail-closed Super Admin shop deletion. Migration
  `20260825120000` introduces service-role-only prepare/apply/fail/cleanup RPCs, exact slug and reason
  confirmation, current-Super-Admin PIN reauthentication support, rate limiting, idempotent retry state,
  transactional tenant-graph deletion, immediate managed-user session revocation, resumable managed Auth
  cleanup, and an immutable deletion audit retained outside the deleted shop graph. `manage-accounts`
  now routes the strict request through those RPCs and deletes only Auth identities whose deterministic
  internal email and managed provisioning metadata both match. No production shop or Auth identity has
  been changed. Strict parser regressions cover valid deletion, exact casing, reason length, extra
  fields, and PIN shape. A 41-assertion pgTAP suite now exercises privilege boundaries, role and exact
  slug denial, request binding/failure, transactional tenant isolation, exclusive versus shared users,
  resumable Auth cleanup, idempotency, and audit immutability. The Android domain, authenticated remote
  transport, repository validation/error mapping, and retry-stable ViewModel operation are connected;
  visible UI now exposes one active-shop-only destructive control to Super Admins, followed by an
  IME-aware confirmation dialog requiring the exact slug, an audited reason, and the Super Admin's
  own masked PIN. Owners and Salesmen cannot reach it. Repository tests cover successful refresh,
  role denial, and exact cached-slug binding; ViewModel tests cover exact request-ID retry; Compose
  tests cover the disabled-until-exact confirmation and Owner invisibility. Release-version
  advancement to rc10/code 11 is complete. The complete local Android gate passes all four
  release-safety scans, all debug unit tests, lint with zero errors/10 existing warnings, release
  artifact inspection, and debug assembly. PR #62 is merged. Its first fresh-database run
  `32820322031` proved all 41 shop-deletion assertions pass, then exposed two compatibility guards:
  the established append-only audit error text must remain exact, and application functions may not
  contain dynamic SQL. The merged correction restores the exact message and replaces schema-driven
  deletion with an explicit foreign-key-safe 42-table manifest, a future-schema mismatch stop, and an
  explicit no-row-remains check. Corrected Database run `32821256012` and Android run
  `32821256010` pass; production deployment `32821910493` and protected rc10 signing run
  `32822345644` pass. The checksum, signer, package/version, SDK, launcher, icons, and production
  binding are independently verified and pinned. No live shop has been deleted.

- [x] **Owner:** Codex. **Task:** correct missing initial accounting-period provisioning. The first
  controlled production purchase reached `post_purchase_receipt` but Android displayed the safe
  conflict message `The invoice already exists or the accounting period/resource is unavailable.`
  Source inspection proves `create_shop` atomically creates the shop and 11 system accounts but no
  `accounting_periods` row, while the purchase RPC rejects before recording its idempotency request,
  bill, receipt, lot, journal, or audit when no open period covers the business date. Forward
  migration `20260821103000` adds an idempotent private provisioner, backfills only shops with zero
  period history, and makes future `create_shop` calls create one open initial period beginning at
  Nepal today minus seven days. It never changes or reopens existing period history. The 33-case
  shop pgTAP suite covers private permissions, existing-shop backfill behavior, date bounds,
  idempotency, and atomic future-shop provisioning. Pinned `pglast 8.2` parses all 50 migration/test
  SQL files and the test plan count matches. PR #56 final-head run `32452592593` and post-merge main
  run `32452802516` pass the complete database gate on exact merge commit
  `1040316632a54765f3e40203f4a2d78d76612d25`. Protected production deployment `32452999186`
  applied the migration and passed linked lint/history plus redacted probes. Physical purchase
  retry remains; no APK replacement is required.

- [x] **Owner:** Codex. **Task:** make the purchase-review form usable with the Android keyboard
  open. The physical rc8 device could not scroll to **Post purchase once** because the IME remained
  over the dialog. rc9 replaces that AlertDialog with a keyboard-inset-aware bounded dialog whose
  form scrolls independently while **Hide keyboard**, **Cancel**, and **Post purchase once** remain
  in a fixed footer. Invoice, date, quantity, unit-cost, and immediate-payment fields now expose an
  IME Done action that clears focus and hides the keyboard; the activity also requests
  `adjustResize`. Version advanced to `0.2.0-rc9`/10 because version code 9 bytes cannot be reused.
  Debug source/test compilation, debug APK assembly, all three release safety scans, and lint with
  zero errors/15 existing warnings pass. PR #55 exact-head Android run `32393392860` also passes the
  complete release gate, including all four purchase screen tests in CI's known-good Robolectric
  environment. PR #55 is merged as exact `main` `43ec93100967ff8eb3876734fac7e48f6dc75231`;
  protected workflow `32395027694`, signature/package/binding inspection, and checksum pinning pass.
  The operator subsequently reached **Post purchase once** and reported the server-authoritative
  success message after the accounting-period backend correction, completing this physical gate.

- [x] **Owner:** Codex. **Task:** correct the misleading rc7 Owner-to-Salesman authorization message.
  The operator installed rc7, signed in as the intended Owner, and received the same exact remote
  HTTP 403 message. Because rc7 would stop a hosted-subject mismatch locally as HTTP 401, this proves
  the request reached `manage-users`. The Function currently converts every exception at its
  reservation stage into HTTP 403, including PostgreSQL uniqueness and invalid-input failures.
  rc8 preserves SQL error details server-side, maps only safe SQLSTATE classes to 409/400/403/503,
  skips compensation when no reservation exists, and gives account creation an actionable duplicate
  Login ID message. The complete local gate passes 186 Android tests, all release safety scans,
  lint with zero errors/15 existing warnings, and debug assembly; all 30 Edge tests plus format,
  lint, and type-check also pass. PR #46, production deployment, aggregate reconciliation,
  protected signing, and checksum pinning pass. The operator installed rc8 and created the first
  Salesman. Aggregate run `31938915526` proves one complete request/audit/active membership with
  zero failed/reserved requests. The operator reports successful Salesman login and confirms that
  account/shop administration is hidden, completing the physical Salesman role-navigation check.
  The Sales workflow also renders the expected empty catalog before Owner product/stock setup. The
  Owner has now created the first controlled test product and vendor; FIFO stock setup remains.

- [x] **Owner:** Codex. **Task:** diagnose the rc5 physical-device Owner-creation authorization
  message without exposing production identity or credential data. The Android client library was
  bytecode-checked and confirmed to preserve the authenticated bearer token when the app adds the
  required JSON media-type header, ruling out the suspected header replacement. A protected,
  manually dispatched production reconciliation now queries only aggregate account/authorization
  counts and fails closed on the exact production project; it never selects IDs, login IDs, names,
  PINs, hashes, payloads, or tokens. PR #39 merged exact head `6095cf9`; its complete fresh-database
  gate passed and protected run `31868432360` returned one active Super Admin, one active shop, one
  eligible Owner-authorization pair, and zero Owner requests/memberships/audits. This proves the
  phone request was rejected at the hosted-session boundary before database reservation. The scoped
  correction now refreshes protected account sessions before sending, retains HTTP 401 as safe typed
  metadata for an actionable re-login message, and never reports a committed mutation as failed only
  because its follow-up directory refresh is pending. The focused executor/account repository suite
  passes. Candidate source/tooling is advanced to `0.2.0-rc6`/7. The full local release gate passes
  184 tests with zero failures/errors/skips, zero lint errors/15 existing warnings, all auth/
  accessibility/performance safety checks, unsigned release artifact scanning, and debug assembly.
  PR #40 merged exact head `3b97341` as main `012817f`; its Android gate passed. Protected run
  `31870004171` passed review and produced the independently verified signed rc6 APK. The exact
  checksum is pinned in the fail-closed installer. The operator installed rc6 and created the first
  Owner; aggregate protected run `31930685338` proves complete request/audit/membership/Auth/profile/
  credential state with no failed or reserved request. Owner login and shop-scope testing are next.

- [ ] **Owner:** Codex. **Task:** replace rc4 with an rc5 candidate that makes account submission
  state explicit and validates the exact hosted Login ID contract before closing the form. Two
  privacy-preserving production reconciliations after the operator reported rc4 Owner creation
  remained unchanged at one Auth user/profile/credential and zero Owner requests, audits, or
  memberships; even failed/reserved Owner requests were zero, proving no call reached production.
  The form previously accepted any nonblank Login ID, closed immediately, and only afterward could
  the repository reject it locally. rc5 shows its own version, explains/enforces the 3–64-character
  lowercase Login ID contract, stays open while submitting, closes only on audited success, and
  displays safe failures inside the dialog. Focused Compose/repository regressions pass, including
  proof that all other required fields plus an invalid Login ID keep Create disabled and replacing
  it with a compliant ID enables submission. The full local release gate passes 180 tests with zero
  failures/errors/skips, zero lint errors/15 existing warnings, all source safety checks, artifact
  scanning, and debug assembly. PR #37 merged exact verified head `2c7303c` as main `c9aa1e7`;
  protected run `31617468852` repeated the clean gate, passed production review, and signed rc5.
  Archive/checksum/signature/package/version/SDK/production-target verification passes. Device
  installation and Owner retest remain.

- [ ] **Owner:** Codex. **Task:** replace rc3 with an rc4 Android candidate that explicitly declares
  `Content-Type: application/json` for both protected account Edge Functions. After the operator
  submitted the first Owner form, privacy-preserving production reconciliation still showed one
  Auth user/profile/credential, zero memberships, zero Owner provisioning requests/audits, and no
  failed or reserved request. The strict `manage-users` and `manage-accounts` handlers reject a
  missing JSON media type before creating idempotency state, while Android previously supplied the
  required header only to `pin-login`. Source, regression assertion, version `0.2.0-rc4`/5, and
  release tooling are updated. The focused regression and full local Android gate pass with 179
  tests, zero failures/errors/skips, zero lint errors/15 existing warnings, all release safety
  checks, the unsigned artifact scan, and debug assembly. PR #35 merged exact verified head
  `e120243` as main `241acd3`; protected run `31592190757` repeated the clean gate, passed production
  review, and built the signed rc4 artifact. Signature/package/version/SDK/production-target/archive/
  checksum verification passes. Installation, Owner provisioning, and Owner login remain.

- [x] **Owner:** Codex. **Task:** replace the rc2 Android login transport with an rc3 candidate that
  explicitly declares `Content-Type: application/json`. Production accepts the same masked
  credentials end to end, while the phone receives HTTP 400 and displays the remote-validation
  message. Bytecode inspection of `functions-kt` 3.6.0 confirms its typed invoke path serializes the
  body to a string without adding a JSON content-type header. Source and regression assertions are
  updated and focused auth tests pass. The first production build failed closed before signing
  because ignored development Gradle properties had higher precedence than protected production
  environment values. Production builds now deliberately prefer protected environment URL/key
  values while ordinary debug builds retain Gradle-property precedence. The complete production
  gate, signing, and immutable artifact verification now pass for rc3. Two Windows MTP writes were
  refused while the Redmi remained readable; only the older rc2 is visible in its Download folder.
  Phone delivery/installation, physical production login, and initial-shop creation/reconciliation
  now pass. The next device gate is first Owner provisioning and shop-scoped role/core workflow.

- [x] **Owner:** Codex. **Task:** repeat the production logical-backup restore drill without interruption
  to prove the accepted four-hour RTO; functional recoverability already passed.
  **Authorization:** owner supplied `PAUSE DEVELOPMENT FOR RESTORE DRILL` on 2026-08-03.
  **Timed rerun start:** `2026-08-10T14:01:03.5333751+05:45`. Current `main` is merge commit
  `4ad88640021ccd6884ae857b9a29fac961350c5f`; development and production are both
  `ACTIVE_HEALTHY` in `ap-northeast-2` on Postgres 17; exact encrypted input is present at 87,656
  bytes with SHA-256 `4c6794ef177069d04c7398b65c6ef205fa737938b4c232927a56788698766fe4`.
  This rerun uses the corrected preventive ACL and fixture-cleanup procedure and measures one
  continuous start-to-app-verification interval. Official Management API pause for only development
  returned HTTP 200; production was not targeted. Development reached `INACTIVE`. A native Win32
  write/read roundtrip stored a new 64-character database password only under Credential Manager
  target `GDAD_TIMED_RESTORE_DRILL_DB_20260810`. One Free disposable project,
  `ukafudhlxuzoeaqxxmam` (`GDAD Timed Restore Drill 20260810`), was created in `ap-northeast-2` and
  is `ACTIVE_HEALTHY`; production remains `ACTIVE_HEALTHY`. The platform approval reviewer rejected
  the subsequent decrypt/extract action before execution. Read-only inspection proved no plaintext
  drill directory and no `age`/`tar` process. The session then crossed into 2026-08-11, invalidating
  this rerun's four-hour RTO measurement before any restore statement ran. The still-empty target and
  its password credential will be destroyed and development resumed; a new explicitly approved,
  uninterrupted rerun remains required. Exact empty target `ukafudhlxuzoeaqxxmam` is now deleted and
  absent from authoritative inventory; Credential Manager target
  `GDAD_TIMED_RESTORE_DRILL_DB_20260810` is deleted and unreadable. The official Management API
  accepted development resume with HTTP 200. Development transitioned through `COMING_UP` and
  `RESTORING` to `ACTIVE_HEALTHY`; production remained `ACTIVE_HEALTHY`, the invalidated target is
  absent, and no plaintext was created. After the temporary-plaintext risk was reported, the owner
  explicitly instructed Codex to continue until completion. A fresh uninterrupted measurement began
  at `2026-08-11T14:33:40.1252728+05:45`; the ciphertext hash still matches and no 2026-08-11 drill
  workspace existed at start. Preflight found exactly the healthy development and production
  projects in the source region/Postgres major. Official pause for only development returned HTTP
  200; production was not targeted. Development reached `INACTIVE`. Fresh target
  `xdavyhgvpkdbasyozorq` (`GDAD Timed Restore Drill 20260811`) is `ACTIVE_HEALTHY` in
  `ap-northeast-2`; its new 64-character password passed native Credential Manager roundtrip at
  `GDAD_TIMED_RESTORE_DRILL_DB_20260811`. Production remains healthy. Explicitly approved
  decrypt/extract then passed the six-entry allow-list, production project/migration metadata, and
  all five inner hash/size checks. The plaintext tarball was deleted; only the ignored extracted SQL
  workspace remains temporarily for restore and will be removed during teardown. The first
  password-redacted Session-pooler preflight to the fresh target failed before any SQL ran, so the
  preventive default-privilege transaction did not execute and the target is still empty. Recovery
  work is paused at this safe point while the newest protected encrypted-backup artifact and the
  target connection path are revalidated; the local 2026-08-03 artifact is too old to prove the
  accepted 24-hour RPO at this drill start.
  GitHub Actions inspection found nightly encrypted-backup runs blocked at the protected
  `production` environment and then cancelled by the next schedule; the newest run
  `31428910379` (scheduled `2026-08-10T20:24:59Z`, exact `main` head
  `4ad88640021ccd6884ae857b9a29fac961350c5f`) was still waiting. Its single
  `export-production` deployment was approved under the owner's standing authorization for
  encrypted read-only backups. The workflow is now allowed to capture a fresh production backup;
  it uploads only age-encrypted ciphertext/catalog/checksum, has no decryption identity, and does
  not enable any paid service.
  The apparent missing database credential was isolated to the sandboxed Windows-vault view; the
  persisted credential exists in the real user context and is a 64-character lowercase-hex value
  encoded as UTF-16. The first routed connection then safely failed `tenant/user not found`, proving
  `aws-1` was not this target's pooler. The authenticated read-only Management API returned the
  authoritative primary pooler `aws-0-ap-northeast-2.pooler.supabase.com` for exactly
  `xdavyhgvpkdbasyozorq`. Its session port reached database/role `postgres` on server 17.6. Before
  any restore object existed, one transaction successfully revoked future default table, sequence,
  and function privileges from `anon`/`authenticated` in `public`; this is the preventive ACL
  normalization proven by the first drill. No production SQL or write occurred.
  Protected backup run `31428910379` then completed successfully at `2026-08-11T09:00:08Z`:
  fail-closed input checks, five logical dumps, age encryption, ciphertext-only upload, and summary
  all passed. Unexpired artifact `gdad-production-daily-20260811T085706Z` (artifact id
  `9095125480`, GitHub archive bytes `88814`, expiry `2026-08-19T09:00:04Z`) was downloaded into the
  ignored backup workspace. It contains only the 87,666-byte `.age` ciphertext, public catalog, and
  checksum file; no plaintext or decryption identity came from GitHub.
  Public-catalog validation passed for capture `2026-08-11T08:57:06Z`, production ref
  `skfxfbssfeetquteubcn`, migration head `20260802163000_initial_shop_provisioning`, exact `main`
  source commit `4ad88640021ccd6884ae857b9a29fac961350c5f`, ciphertext bytes `87666`, and SHA-256
  `05af91c225e59126db068675aa31cf2591daef56069759bfec2202132c7d494d`; the companion checksum
  filename/hash agree exactly. This fresh artifact is eligible for the accepted 24-hour RPO.
  Under the owner's explicit temporary-plaintext authorization, the stale ignored 2026-08-11
  extraction was path-verified and removed, then the fresh ciphertext was decrypted with the local
  age identity held only in memory. The archive contained exactly the six allow-listed files;
  production metadata and all five inner SQL size/hash records passed. The temporary plaintext
  tarball was deleted immediately. Only the ignored extracted fresh SQL remains for the restore and
  will be destroyed during teardown.
  Because the `14:33` measurement began against the older recovery point and the current empty
  target predates this fresh backup, it cannot honestly prove the full start-to-app-verification
  RTO for the new source. This attempt is therefore stopped before restore. Exact target
  `xdavyhgvpkdbasyozorq`, its credential, and extracted plaintext will be removed; development will
  be resumed, then a clean timer will include development pause, new isolated-target provisioning,
  decrypt/restore, validation, and app verification against the fresh capture. Production remains
  read-only and unchanged.
  The first positional CLI delete wrapper stopped locally because PowerShell promoted the CLI's
  confirmation stream; authoritative inventory proved the target still existed, so no cleanup was
  assumed. A retry that checked the native exit code passed and submitted deletion for exact empty
  target `xdavyhgvpkdbasyozorq`. Absence confirmation, local cleanup, and development resume follow.
  Authoritative inventory now confirms `xdavyhgvpkdbasyozorq` absent. Credential target
  `GDAD_TIMED_RESTORE_DRILL_DB_20260811` is deleted, the exact ignored plaintext directory is absent,
  and the official Management API accepted resume for only development ref
  `zniqkuwktvincjndcgpu`. Production remained out of scope and unchanged; development health polling
  is in progress before the clean timed run begins.
  Development returned `ACTIVE_HEALTHY`; production is also `ACTIVE_HEALTHY`, the disposable ref is
  absent, and exactly the two persistent projects remain. The workspace link still targets
  development. This is the clean normal-state baseline for the final timed run.
  **Final uninterrupted timed start:** `2026-08-11T14:58:35.5651536+05:45`. The official Management
  API accepted pause for only development ref `zniqkuwktvincjndcgpu`; production was explicitly not
  targeted. RTO will be measured from this timestamp through fresh drill-only app login and RLS
  verification. RPO will use fresh capture `2026-08-11T08:57:06Z`.
  Development reached `INACTIVE`; production remains `ACTIVE_HEALTHY`. The Free active-project slot
  is available and no disposable target currently exists.
  Fresh random-password Credential Manager roundtrip passed at
  `GDAD_FINAL_TIMED_RESTORE_DB_20260811`. Exactly one disposable Free target was created:
  `vdyjynwmvjfkpdbbaqpd` (`GDAD Final Timed Restore Drill 20260811`), `ACTIVE_HEALTHY` in
  `ap-northeast-2` on Postgres 17.6.1.155. Its ref differs from development and production; no
  production credential or secret was used.
  Authenticated Management API metadata identified primary pooler
  `aws-0-ap-northeast-2.pooler.supabase.com` and user
  `postgres.vdyjynwmvjfkpdbbaqpd`. Password-redacted session-pooler preflight reached only the final
  target as database/role `postgres` on server 17.6. Before any restored object existed, the
  preventive default-privilege revoke for public tables, sequences, and functions committed in one
  transaction. No production SQL or write occurred.
  Two initial age retries failed before plaintext because .NET/PowerShell standard-input transport
  prefixed the valid in-memory identity with an encoding marker; the tarball was removed and the
  extraction directory remained empty after each. An ephemeral Windows named pipe then exposed the
  identity to age as a BOM-free file stream without writing the key to disk. Decrypt succeeded, the
  exact six-entry allow-list and all five inner size/hash records passed again, and the temporary
  tarball was deleted. Only the ignored validated SQL extraction remains temporarily.
  The first final-target restore wrapper exceeded its 180-second local timeout before returning a
  phase result. Follow-up proved zero local `psql` processes, zero public tables, no `profiles` or
  migration table, and zero other active target sessions: PostgreSQL's single transaction rolled
  back completely, so no partial restore exists. The target remains safely at the pre-object ACL
  baseline while the local invocation is narrowed and retried.
  The verified 297-byte role-settings phase then committed independently with `ON_ERROR_STOP` and
  `--single-transaction` in 2.846 seconds. No schema or business row exists yet.
  The isolated 361,496-byte schema phase also reached the 180-second local timeout, proving the
  earlier combined delay was not output buffering or the role file. Its transaction was terminated;
  the next attempt will use a distinct PostgreSQL application name and a second read-only session to
  capture the live wait event and exact blocked statement before any further retry.
  A later error-only diagnostic reported duplicate `private.product_code_type` at schema line 64.
  Authoritative inventory then found 31 public tables, 48 public custom types, 17 private types, 37
  private functions, and 38 private relations. This proves the first isolated schema transaction
  completed and committed even though its local wrapper did not return before timeout; subsequent
  attempts correctly rolled back on the already-restored first type. The temporary diagnostic log
  was deleted. The unedited schema is present; data and migration history are next and no schema
  cleanup/replay will be attempted.
  Direct child-process restore then passed: the verified data dump committed with replica-mode
  loading in 38.005 seconds, and migration-history schema/data committed in 9.290 seconds. The final
  isolated target now contains the complete logical restore; database lint, pgTAP, reconciliation,
  and concurrency validation follow.
  Pinned Supabase CLI 2.111.0 lint against restored `public,private` completed with exit 0 and
  reported no schema errors.
  The first all-files pgTAP child-process wrapper reached its 180-second local ceiling without
  returning aggregate output. No `psql` process remains, and every test file owns/rolls back its
  fixtures. Validation will retry as 21 short-lived file sessions and aggregate exact plans/assertions,
  avoiding the Windows long-lived stream wrapper.
  The per-file retry passed its first nine suites (354 assertions) before
  `balanced_ledger.test.sql` could not start because Windows reported a temporary DNS-resolution
  failure for the authoritative pooler. No assertion failed and all completed suite fixtures rolled
  back. The complete 21-file aggregate will be rerun with bounded connection retry/pinned resolved
  pooler IPv4 for this session.
  The complete rerun then passed all 21 plans and exactly 723 assertions with zero `not ok` results
  in 318.228 seconds. Each suite matched its declared plan and rolled back its fixtures.
  Pre-fixture reconciliation then matched all 74 copied tables and zero source rows exactly;
  restored migration history is 28 entries at head `20260802163000`. RLS-disabled exposed tables,
  cross-shop references, invalid lots, negative product projections, product/lot projection
  mismatches, unbalanced journals, and duplicate business idempotency groups are all zero. Both
  required enabled audit guard triggers are present.
  The reviewed repository concurrency setup committed only synthetic fixtures on the disposable
  target. Production and recovered source counts remain untouched; the committing race harness and
  final invariant verifier are next.
  The exact repository concurrency workflow passed: competing FIFO sale, vendor payment, and
  expense each produced exactly one success; concurrent exact sale retry returned one identical
  result; partial return succeeded; disabled and cross-shop forged actors were denied; final stock,
  due, cash, request-state, ledger, report, and forgery invariants all passed. Only disposable
  synthetic fixtures were committed.
  Post-harness cleanup then truncated exactly the 74 authenticated source-catalog tables with
  `CASCADE` in one transaction and deliberately did not reset Auth sequences. All 74 rechecked with
  zero total rows and zero nonempty tables, restoring the verified backup baseline for bootstrap.
  The first Deno invocation rejected an obsolete `--allow-env` flag before any hosted change. The
  corrected pinned Deno 2.4 command generated an Argon2id dummy verifier, and three independent new
  drill-only values were installed on `vdyjynwmvjfkpdbbaqpd` under names
  `GDAD_PIN_PEPPER_V1`, `GDAD_RATE_LIMIT_PEPPER_V1`, and `GDAD_DUMMY_PIN_HASH_V1`. Their names were
  verified; no value was logged, written, or copied from production.
  Repository Functions `pin-login`, `manage-users`, and `manage-accounts` deployed successfully by
  pinned CLI/API only to the disposable target. Function status and gateway JWT settings are being
  verified before bootstrap.
  Raw non-secret Function metadata confirms all three are `ACTIVE` version 1. Gateway JWT settings
  are `false` for `pin-login`/`manage-users` and `true` for `manage-accounts`; the first strict local
  verifier rejection was a PowerShell comparison artifact, not a hosted mismatch.
  The first final identity attempt selected the publishable key after correcting PowerShell's nested
  JSON-array handling, installed the one-time bootstrap secret, created one random drill-only Super
  Admin, and completed PIN login. Its local RLS GET then failed before transmission because the
  helper attached an empty content body to GET—the known .NET verb defect. Guaranteed cleanup
  removed `GDAD_BOOTSTRAP_TOKEN`; hosted verification confirms it absent. Exactly one discarded
  drill profile/Auth user remains and its random credentials were not logged or retained. The same
  74-table cleanup will restore zero rows before a final bodyless-GET retry.
  Exact 74-table cleanup removed the discarded identity without sequence reset and reverified zero
  total catalog rows. The target is again at the authenticated backup baseline.
  A compact-script syntax error then stopped one retry before installing any secret. The corrected
  final fresh drill-only bootstrap returned HTTP 201, PIN login returned HTTP 200, the JWT subject
  matched the created user, and a truly bodyless authenticated Data API GET returned exactly one
  enabled `super_admin` profile through RLS. `GDAD_BOOTSTRAP_TOKEN` was removed and confirmed absent;
  no login ID, PIN, session, or token was logged or retained. App verification completed at
  `2026-08-11T16:08:27.1392027+05:45`. Against timed start
  `2026-08-11T14:58:35.5651536+05:45` and backup capture `2026-08-11T08:57:06Z`, actual RPO is
  `00:16:29.5651536` (**PASS**, <=24h) and actual RTO is `01:09:51.5740491` (**PASS**, <=4h).
  Final pre-destruction snapshot confirmed production and the disposable target
  `ACTIVE_HEALTHY`, development `INACTIVE`, the workspace linked only to development, exactly three
  expected `ACTIVE` Functions, exactly the three drill secret names, and no bootstrap secret.
  Pinned CLI accepted permanent deletion for exact disposable ref `vdyjynwmvjfkpdbbaqpd` after all
  evidence gates passed. Authoritative absence, credential/plaintext cleanup, and development resume
  follow; the fresh encrypted ciphertext artifact is preserved.
  Authoritative inventory confirms `vdyjynwmvjfkpdbbaqpd` absent. Credential target
  `GDAD_FINAL_TIMED_RESTORE_DB_20260811` and the exact ignored final plaintext directory are deleted;
  the fresh encrypted ciphertext/catalog/checksum remain. The official Management API accepted
  resume for only development `zniqkuwktvincjndcgpu`; final health polling is in progress.
  Development transitioned through `COMING_UP`/`RESTORING` to `ACTIVE_HEALTHY`. Final normal-state
  audit confirms exactly the two persistent projects healthy, the workspace linked only to
  development, production unmodified, the disposable ref absent, its Windows credential absent,
  all final plaintext absent, and preserved ciphertext SHA-256
  `05af91c225e59126db068675aa31cf2591daef56069759bfec2202132c7d494d` intact. The uninterrupted
  encrypted-backup restore drill is **Functional PASS / RPO PASS / RTO PASS**.
  **Scope:** pause only development project `zniqkuwktvincjndcgpu`, create a disposable Free-plan
  project in `ap-northeast-2` on Postgres 17, restore encrypted production backup
  `gdad-production-daily-20260803T073503Z`, use only newly generated drill credentials, run the
  database/security/reconciliation/identity checks, destroy the target, and resume development.
  Production is read-only throughout. **Progress:** source/backup checksums and manifests pass;
  both persistent projects are `ACTIVE_HEALTHY`; official Supabase pause/resume and logical-restore
  procedures plus pinned CLI creation flags are confirmed. The official Management API preflight
  matched only the intended development/production refs; development pause was accepted and entered
  `PAUSING`, then `INACTIVE`. Production remained `ACTIVE_HEALTHY` and unchanged. A random drill
  database password was written only to Credential Manager target
  `GDAD_RESTORE_DRILL_DB_20260803`. The first CLI create request succeeded but mixed progress text
  with JSON; authoritative inventory prevented a duplicate and confirmed target
  `dgecerkopdngzuwqxzpf`. Connection preflight proved `cmdkey` had not persisted its password, so no
  restore statement ran and that empty target was deleted. A native Win32 Credential Manager
  write/read roundtrip then passed without logging or writing the secret, and exact replacement
  target `tjhqfjjxzgumnnzgzwnq` (`GDAD Restore Drill 20260803B`) is `ACTIVE_HEALTHY` in
  `ap-northeast-2` on Postgres 17.6.1.155. Official EDB PostgreSQL 17.10 client binaries are
  isolated under ignored `.tooling`; the 333,927,270-byte source archive SHA-256 is
  `ef9b1e5e23d2e8a83914ba13d9dc536a72210fba53fd1808ff1f7e06bb22b106`, ZIP validation passed,
  and `psql --version` reports 17.10. The authenticated backup manifest and every inner SQL hash
  pass again; verified plaintext exists only in ignored `.tooling/restore-drill/20260803/plain`
  for this drill and will be destroyed afterward. A password-redacted Session-pooler preflight
  reached only the replacement target and returned database `postgres`, role `postgres`, and
  server 17.6. The documented roles/schema/data sequence then completed with exit 0 in one
  rollback-on-error transaction (including replica-mode data loading); the verified source dumps
  required no edits. The separate migration-history schema/data transaction also completed with
  exit 0. The isolated target now contains the complete logical restore; database/security/
  reconciliation checks and drill-only application identity validation are next. Initial pgTAP
  execution completed all 21 plans/723 assertions but found 53 client-privilege failures. Diagnosis
  proved the fresh target's permissive `public` default privileges were inherited when dump objects
  were created: the source dump cannot emit revokes for rights absent on its source. A target-only
  transaction therefore revoked inherited `anon`/`authenticated` rights on restored public objects
  and replayed all 181 ACL statements from the verified schema dump; it completed with exit 0.
  This is a restore-procedure defect/retest, not a production-schema change; the source dump and
  production remain unchanged. The full corrected-parser retest then passed all 21 plans and 723
  assertions with zero `not ok` results and no persistent test fixtures. Lint, concurrency,
  reconciliation, and drill-only application identity checks remain. Supabase CLI 2.111.0 linked-
  equivalent lint against `public,private` passed with exit 0. Pre-fixture reconciliation matched
  all 74 copied tables and zero source rows exactly; restored migration history is 28 migrations at
  `20260802163000_initial_shop_provisioning`; RLS-disabled, cross-shop, negative/overflow stock,
  projection mismatch, unbalanced journal, and duplicate business-idempotency counts are all zero,
  with two enabled audit mutation guards. The concurrency setup fixture committed only on the
  disposable target. Its first PowerShell wrapper attempt created no sale: native Windows argument
  quoting removed embedded JSON quotes and PostgreSQL rejected the JSON. The retry will send SQL on
  standard input, matching the repository Bash harness semantics; production remains untouched.
  That transport passed: competing FIFO sale, vendor payment, and expense each produced exactly one
  success; exact concurrent sale retry returned one identical result; partial return succeeded;
  disabled/cross-shop forged actors were denied; and final stock, due, cash, request-state, ledger,
  reporting, and forgery invariants passed. Only disposable test fixtures were created. Drill-only
  Edge secret/function deployment and fresh identity/login/RLS verification remain. Three entirely
  new in-memory drill values (`GDAD_PIN_PEPPER_V1`, `GDAD_RATE_LIMIT_PEPPER_V1`, and an Argon2id
  `GDAD_DUMMY_PIN_HASH_V1`) were installed and their names verified on the disposable target; zero
  production secret values were read or reused. No bootstrap secret exists yet. Function deployment
  then completed with exit 0. The target lists exactly `pin-login`, `manage-users`, and
  `manage-accounts` as `ACTIVE` version 1, with gateway JWT settings `false`, `false`, and `true`
  respectively. The first drill-only bootstrap reached the reservation RPC but was correctly denied
  because the preceding concurrency harness intentionally left three temporary profiles, while the
  sole bootstrap contract requires an empty profile table. HTTP was 403 at `reserve`; no drill
  identity was created, no PIN/token was logged, and the one-time bootstrap secret was removed.
  Because the verified source row catalog is empty, the disposable fixture rows will be cleared and
  zero-row reconciliation repeated before bootstrap retry. A first `TRUNCATE ... RESTART IDENTITY`
  attempt rolled back because the session role does not own an Auth sequence. The retry omitted
  sequence resets, committed for exactly the 74 cataloged tables, and reverified all 74 with zero
  total rows. Sequence position is not part of the logical row recovery contract. Production is
  unchanged. The next retry created the random drill-only Super Admin and PIN login returned a valid
  session, but the local .NET helper then rejected its RLS GET because it attached an empty body to
  the GET verb. The bootstrap secret was removed successfully. Random credentials were discarded,
  so this temporary identity will be cleared back to the verified zero-row baseline before one final
  bodyless-GET retry; this is a local drill-client defect, not a backend/login failure. The discarded
  drill identity was then removed by clearing exactly the same 74 cataloged tables, and their total
  row count is again zero. The final fresh drill-only bootstrap returned HTTP 201, PIN login returned
  HTTP 200 with a valid session, and a bodyless authenticated Data API read returned exactly the
  caller's enabled `super_admin` profile through RLS. No source user/PIN was used, no credential or
  session was logged/written, and the one-time `GDAD_BOOTSTRAP_TOKEN` was removed. All functional
  restore gates now pass; safe evidence capture, target destruction, plaintext/credential cleanup,
  and development resume remain. Final pre-destruction snapshot at
  `2026-08-10T13:26:56.336+05:45` confirmed production `ACTIVE_HEALTHY`, development `INACTIVE`,
  disposable target `ACTIVE_HEALTHY`, exactly three ACTIVE repository Functions, all three expected
  drill secret names, and no bootstrap secret. Pinned CLI positional deletion then permanently
  removed exact target `tjhqfjjxzgumnnzgzwnq`; authoritative project inventory confirms zero
  remaining entries for that ref. Local drill credential/plaintext cleanup and development resume
  remain. Credential Manager target `GDAD_RESTORE_DRILL_DB_20260803` is now deleted and unreadable;
  the absolute-path-verified ignored plaintext restore directory is removed. The original encrypted
  backup/ciphertext evidence remains intact. Development resume and final health confirmation remain.
  The first official Management API resume attempt returned 401 because the CLI Credential Manager
  blob was decoded as UTF-16; encoding-shape inspection exposed no value and proved it is UTF-8. The
  corrected in-memory decode produced HTTP 200 for only development ref `zniqkuwktvincjndcgpu`.
  Development transitioned through `COMING_UP`/`RESTORING` to `ACTIVE_HEALTHY`; production remained
  `ACTIVE_HEALTHY`, the disposable ref remained absent, and the workspace link still targets
  development. `docs/operations-runbook.md` now records the proven hosted-target ACL normalization
  and pre-harness row-count/post-harness fixture-cleanup requirements so the next agent does not
  repeat these defects.

- **Owner:** Codex. **Task:** close the initial production shop-provisioning gap.
  **Files:** forward SQL migration/pgTAP, account domain/remote/repository/ViewModel/Compose tests,
  authorization/data/account contracts, and status. **Acceptance:** only an active Super Admin can
  create a normalized shop through an authenticated exactly-idempotent RPC; direct table writes stay
  denied; all 11 protected system financial accounts and one credential-free immutable audit are
  created atomically; an empty production directory exposes Create Shop; exact retry cannot
  duplicate or change the request. **Progress:** migration, 25-assertion pgTAP suite, direct RPC
  transport, repository validation/refresh, retry-stable ViewModel operation, empty-directory state,
  and Super Admin UI/dialog are drafted. Production/test Kotlin compilation and the focused 13-test
  account suite pass; both SQL files parse and the pgTAP plan matches 25. The changed candidate is
  advanced to `0.2.0-rc2`/3 so signed rc1 bytes/version are never reused. Fresh-database and full
  Android regression verification are complete. Corrected PR #20 checks passed fresh database
  replay, all 723 pgTAP assertions, backend integration/concurrency, and the complete Android
  release gate. Exact head `2744a4250eeda2da8ae7f6cd628d0d95e9569d6d` merged to `main` as
  `42b39a68e41533e37118f1d99331b0b67a9450f9`. Development dry-run selected only the new migration;
  it is deployed with all 28 local/remote versions aligned and linked lint clean. Protected
  production run `30758557549` also passed from the exact merge commit, applying the migration and
  verifying linked production lint/history and live redacted probes. Protected Android run
  `30758725027` then passed clean verification and built signed rc2 from the same commit. The
  installer and handoff now pin its independently verified identity.

- **Owner:** Codex. **Task:** 6.6, protected production Supabase deployment automation.
  **Files:** manual GitHub Actions workflow, production operations documentation, README, and
  status. **Acceptance:** deployment can target only the project ref registered in the protected
  `production` environment; the known development ref is rejected; all backend checks pass from
  zero before any hosted mutation; reviewed migrations, named Edge secrets, and all three functions
  deploy reproducibly; linked lint/history and redacted probes pass; no bootstrap user or long-lived
  bootstrap secret is created. **Dependencies:** approved production project
  `skfxfbssfeetquteubcn` now exists healthy in `ap-northeast-2`. Its generated database password,
  client-safe publishable key, independent production peppers, and Argon2id dummy verifier are
  stored only in project-scoped Windows Credential Manager entries. An independently recoverable
  password copy, approved Free-plan logical-export automation, and restore evidence remain
  operator-owned gates. GitHub environment `production` now requires reviewer `sanjubaba21`,
  permits only `main`,
  and contains the seven expected encrypted secret names. The protected manual workflow,
  operator deployment/rollback guide, and tracked masked production bootstrap helper are
  implemented and statically verified. Repository automation and isolated project creation are
  complete. Production run `30738097255` replayed the complete backend gate, applied all 27
  migrations, installed the three Edge secrets, deployed all three Functions, and passed linked
  lint/history. Its final probe incorrectly sent malformed `{}` to `manage-accounts` and expected
  gateway `401`; the handler correctly returned its pre-authentication `400 INVALID_REQUEST`.
  The corrected probe now sends a valid-shaped unauthenticated request and requires
  `401 UNAUTHORIZED` before any RPC. PR #11 merged the correction as `7b42263`; protected run
  `30739060872` then passed every deployment and verification step. Backup/restore proof and the
  masked Super Admin bootstrap remain.

- **Owner:** Codex. **Tasks:** 7.1/7.2, secure production signing and signed clean gate.
  **Files:** Android Gradle release configuration, release build tooling/documentation, CI, and
  status. **Acceptance:** versionCode advances beyond 1; normal development builds remain unsigned;
  an explicit production build fails closed unless all signing inputs exist and the Supabase URL is
  non-development; no keystore/password is committed or printed. **Dependencies:** independent
  keystore recovery, physical smoke test, and rollout approval remain
  external. Version `0.2.0-rc1`/2, the fail-closed Gradle gate, secret-safe local build script,
  immutable-action CI gate, protected manual signing job, keystore ignore rules, CLI-aligned
  database CI, and release guide are implemented and verified. The first Gradle gate invocation
  exposed an eager `assembleRelease` lookup before the
  Android plugin registered that task; lazy task matching corrected it. Three negative-path checks
  now prove the gate rejects missing release approval, missing signing inputs, and the development
  Supabase URL. The ordinary unsigned regression/release gate passed 173 tests and rebuilt both
  artifacts. Lint then identified one new warning caused by explicitly assigning the default
  `shrinkResources=false`; that redundant assignment was removed and the focused release/lint gate
  returned to the existing 16-warning baseline with zero errors. R8 and shrinking remain disabled
  until signed physical-device smoke evidence exists. The production keystore was generated under
  ignored `.tooling/release`, its passwords and alias were stored in Windows Credential Manager,
  and its base64 form plus passwords, alias, and production URL were registered only as protected
  GitHub `production` environment secrets. Protected Android run `30754590770` built artifact
  `GDAD-BAGS-0.2.0-rc1-2-release` from exact main SHA
  `36aaf7772d795b5a8da8d207df27a405989802d7`. Independent download verification passed the checksum,
  APK Signature Scheme v2, signer certificate, package/version/SDK/label/activity/icon metadata,
  production-project marker, and the repository's exact secret scanner. The installable candidate
  is `GDAD-BAGS-0.2.0-rc1-2-release.apk` (57,395,229 bytes; SHA-256
  `DBDD6D9B079E41DFD03E332E6D163228A75D102DE99025017D2F4B6331339C82`), signed by certificate
  SHA-256 `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`.
  No GitHub Release, app-store publication, user, or business data was created. Tasks 7.1 and 7.2
  are complete; independently recoverable signing material and physical-device evidence remain
  launch gates. Superseding protected run `30758725027` built rc2/version 3 from exact main commit
  `42b39a68e41533e37118f1d99331b0b67a9450f9`; its downloaded 57,411,609-byte APK has SHA-256
  `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC` and the same verified
  production signing certificate. The fail-closed installer now accepts only that rc2 identity.

- **Owner:** Codex. **Task:** 6.5, privacy-safe monitoring, backups, and restore operations.
  **Files:** shared Edge operational helpers/tests, Edge handlers, operational runbook, restore
  drill tooling, README, and status. **Acceptance:** failures have safe correlation IDs and
  allow-listed context; alert ownership/thresholds/response steps, backup retention, and a tested
  non-production restore procedure are documented without copying credentials into production.
  **Dependencies:** backend behavior is stable; hosted dashboard access is browser-policy blocked
  in this session and remains an explicit operator step. **Task 6.4 handoff:** the first audit found
  implicit/silently truncating Supabase list caps, unbounded
  Room observers, and quadratic history assembly. An explicit 500-row first-release support
  window now requests a sentinel 501st row and fails closed on overflow; boundary regression
  tests cover the exact supported maximum and sentinel rejection. Identity, account, product,
  active-lot, and vendor reads now use explicit deterministic windows; account membership
  filtering moved to PostgREST instead of discarding unrelated rows on-device. Notifications
  and every sale/return relationship now have deterministic fail-closed windows. Sale history
  assembly indexes related rows once instead of repeatedly scanning whole lists. Remaining
  stock, finance, and vendor-ledger relationships now also use explicit windows. Finance and
  vendor history assembly use pre-grouped indexes instead of nested whole-list scans. Room bounds,
  database/Room indexes, compilation, and query-plan evidence are being applied next. The first
  compile reached all new PostgREST limit calls successfully and exposed only local API/type
  corrections: this pinned client requires an explicit `Order` argument, and the vendor file
  needed the new window imports plus explicit grouped-row types. The corrected offline production
  and unit-test Kotlin compile passes in 2m53s; only the pre-existing deprecated
  `kotlinx.datetime.Instant` warnings remain. Every Room singleton/list SELECT now has an explicit
  limit; owner-scoped cached lists share the 500-row contract, outbox observation is capped at 200,
  and all limited lists retain deterministic ordering. Room/KSP compilation passes in 3m16s. A
  501-row Room regression now verifies that the product observer returns the deterministic first
  500 and that SQLite's plan uses an owner-scoped index. Its first compile stopped on Kotlin's
  strict mixed-array inference for SQL bind values; the corrected focused run compiled and both
  pure remote-window tests passed. Robolectric then attempted a network artifact fetch that the
  offline sandbox denied before the Room test class started; no Room assertion failed. The
  approval service is temporarily usage-limited, so the already-defined Room runtime check remains
  pending while source work continues. Trusted report and purchase-dashboard JSON arrays now apply
  the same client-side sentinel rejection, so bounded SQL aggregation cannot silently omit low-
  stock, vendor-due, or account-balance rows. Forward migration
  `20260729170000_bounded_report_detail_windows.sql` keeps financial/count totals exact while
  deterministically limiting each JSON detail array to the 501-row overflow sentinel. SQL grammar,
  regression coverage, and hosted migration/plan verification are next. The existing report pgTAP
  already asserts four forced query plans use the supported sales/returns/expense/journal indexes;
  it now also asserts that all three detail aggregates retain their 501-row sentinels.
  Recomposition audit moved notification category/filter derivation, account role filtering, and
  active purchase-directory filtering behind stable `remember` keys; formerly unkeyed dashboard,
  shop, and return-filter lazy items now have stable identity. Stock history now builds per-product
  lot/recent-movement maps once per history snapshot instead of filtering two full histories for
  every visible product on every recomposition; adjustment-lot derivation is also memoized.
  `verifyReleasePerformanceSafety` is now part of every release pre-build and statically enforces
  one explicit limit per remote select, one limit per Room SELECT, no production `runBlocking`,
  all three report sentinels/client overflow checks, the stock snapshot indexes, and destination-
  scoped authenticated activation without per-ViewModel session launch effects. The gate
  itself passed on its first run; production compilation then caught the new managed-shop lazy key
  using `shopId` instead of the domain model's `id`. The corrected performance gate plus production
  and unit-test compilation pass in 3m5s.
  Startup/data activation is now destination-scoped instead of launching all ten feature pipelines
  immediately after authentication. The dashboard activates only notification counts plus business
  reporting for non-Super-Admin roles; each feature activates only its own data, with the vendor
  screen explicitly activating its purchase and vendor-ledger slices. Once loaded, a slice remains
  warm for fast back-navigation during that same identity's session; identity changes and logout
  deactivate every slice. Unauthorized destinations activate nothing, and a pure policy regression
  suite covers the complete mapping. The performance gate, production/unit compilation, and all
  three focused activation-policy tests pass offline in 3m10s. The performance/reliability audit
  now records explicit launch/network/search/report/memory budgets, separates structural proof from
  pending device measurements, and provides a reusable ADB JSON capture for cold starts, PSS, and
  janky-frame evidence. The bundled ADB currently reports no attached device, so no runtime number
  has been claimed. The measurement script parses successfully, and the expanded static release
  performance gate plus `git diff --check` pass after the documentation/tooling increment. The
  complete offline gate assembled and scanned the release APK and passed every static safety check;
  102 unit entries then ran with 20 Robolectric class-start failures because the sandbox denied the
  Android runtime fetch. No test assertion failed. A matching host Maven artifact exists but is not
  readable by sandboxed JVM workers, so lint is being run separately and the last all-Robolectric
  passing baseline remains the Task 6.3 gate. Separate lint completed in 2m50s with zero errors and
  the same 16 known warnings. The newly assembled/scanned unsigned release APK is 57,378,833 bytes
  with SHA-256 `0B77A68FA67D884046A41A45245E5A8F0D7732F9DA08A9E0C71BC53293A8F2A3`.
  Offline debug assembly then passed in 1m12s and refreshed the installable
  `GDAD-BAGS-test.apk` at 76,810,239 bytes, SHA-256
  `41A8B9C25F6EB051B76AB8110C0CA7A81DB6CD5FAC741AFC80E5BB8704C409B8`.
  `aapt` confirms package `com.gdad.bags`, label `GDAD BAGS`, min/target SDK 31/36, launcher
  activity, and all five GDAD launcher-icon densities. `apksigner` verifies one Android Debug
  signer using APK Signature Scheme v2; this remains a test APK, not production signing.
  **Task 6.5 progress:** Android already avoids raw production logs and Edge errors already avoid
  request bodies/messages. A new shared helper now chooses a validated idempotency UUID or opaque
  server UUID, exposes it in a no-store `x-gdad-correlation-id` header, and serializes failure events
  with only event/function/stage/correlation fields. `pin-login`, `manage-users`, and
  `manage-accounts` now use the contract for every success/error/method response and safe failure
  log, switching to a validated request UUID after parsing. `manage-users` compensation failures
  use the same allow-listed event instead of a separate free-form log. Pinned Deno 2.9.0 formatting,
  lint, all three handler type-checks, and all 29 Edge tests pass with zero failures.
  All three handlers were deployed successfully to hosted development after passing the pinned
  Deno 2.9.0 gate. Hosted versions 28/23/7 are ACTIVE for `pin-login`/`manage-users`/
  `manage-accounts`; malformed public requests to the first two return `400 INVALID_REQUEST` with
  valid no-store correlation headers. The JWT-protected account handler remains fail-closed at the
  gateway without a session and will be exercised through the authenticated app smoke test. The operations
  runbook now assigns response roles, defines safe
  telemetry/incident fields and severity thresholds, documents Free-compatible exports and the
  approved Free-pilot logical-export/PITR exclusion, and provides an isolated restore procedure plus evidence
  template. Official current plan limits are linked. No restore or hosted alert is falsely marked
  complete: production configuration and a measured drill remain launch gates.

When starting work, move exactly one small deliverable here and include:

- owner/agent;
- intended files;
- acceptance criteria;
- dependencies or decisions still needed.

## Remaining work

Items are listed in recommended dependency order. IDs are stable references for agents
and change-log entries.

- **rc13 negotiated pricing:** run fresh-database migration/lint/pgTAP CI, merge the exact green PR,
  deploy the forward function migration to production, produce and independently verify the signed
  rc13/code14 APK, then upgrade and smoke-test flexible Sale entry plus Owner profit display.

- **Task 6.3 physical gate:** complete TalkBack, 200% font/display, keyboard/Switch Access, and
  intermittent-network traversal on a supported Android device.
- **Task 6.4 physical evidence:** run the ADB startup/memory/frame procedure on the target device.
  The 501-row Room/query-plan regression now passes in the complete 173-test suite; the bounded-
  report migration is applied to hosted development, all 27 migration versions match, and linked
  lint is clean.
- **Task 6.5 operations:** the six-hourly secret-minimal production health workflow is implemented
  and active; manual hosted run `30739922795` is green using only its registered client-safe
  publishable-key copy. The protected daily `age`-encrypted logical-export workflow is active after
  explicit acceptance of its public-repository ciphertext destination. Run `30750734823` produced
  the first encrypted artifact; independent download, checksum, decryption, five-file allow-list,
  and inner-manifest hash/size validation all pass. Its private identity remains only in Windows
  Credential Manager and GitHub holds only the public recipient. Place an independently recoverable
  identity copy outside this PC and confirm free GitHub/Supabase owner notifications. The approved
  Free pilot accepts a 24-hour RPO/four-hour operator RTO and
  enables no paid backup, PITR, log-drain, metrics, or alert add-on. Scheduled run `30767417848`
  subsequently produced and uploaded a fresh post-shop-migration daily ciphertext from current
  `main`; its outer and authenticated inner manifests also pass independent local verification and
  all temporary plaintext was deleted. Fresh protected run `31428910379` produced capture
  `2026-08-11T08:57:06Z`, and the uninterrupted isolated restore passed functional recovery, RPO
  `00:16:29.5651536`, and RTO `01:09:51.5740491`. Nightly jobs still require prompt owner approval
  at the protected environment; missed approvals caused later schedules to cancel waiting runs.
- **Task 6.6 production backend:** production project `skfxfbssfeetquteubcn` exists healthy; all 28
  migrations, three production Edge secrets, and three Functions are deployed with clean linked
  lint/history. Protected run `30758557549` is fully green on main commit `42b39a6`, including the
  initial-shop migration and authentication-boundary probes. Its deployment values are stored locally and as seven
  encrypted GitHub `production` environment
  secrets. The environment requires `sanjubaba21` review and permits only `main`. Copy the database
  password to an independently recoverable approved manager and confirm owner notifications. The
  one-time masked production Super Admin bootstrap is complete: subject-matched PIN login passed,
  independent counts show exactly one enabled Super Admin/credential and zero shops, and the
  temporary bootstrap secret is absent. The Free logical-export restore drill passes functional/
  RPO/RTO gates. Repository deployment/bootstrap tooling rejects the known development project.
- [x] **Task 7.1 signing inputs:** the release keystore is ignored locally, passwords/alias are in
  Windows Credential Manager, and base64 keystore/passwords/alias plus the production Supabase URL
  are protected GitHub `production` environment secrets. An independent owner-controlled recovery
  copy remains a launch-continuity gate.
- [x] **Task 7.2 signed clean gate:** protected run `30758725027` passed clean tests/lint/build,
  signature/package/version/SDK/icons/production-target/secret verification, and uploaded the
  signed rc2 candidate without publishing it.
- [x] **Task 7.3 physical gate:** signed rc9 passed Owner/Salesman role and identity isolation,
  product/vendor setup, purchase, sale/return, vendor and cash/bank operations, notification,
  offline/retry, logout/cache purge, stock adjustment, TalkBack, approximately 200% text,
  non-touch focus, cold start, memory, and frame-jank checks. The operator accepted this completed
  workflow as the final in-app test on 2026-08-24 and waived an additional device cycle before the
  direct APK build/handoff. No application-code change followed the measured candidate.
- **Task 7.4 final handoff:** candidate/source/backend traceability plus install, upgrade, rollback,
  incident, support, and staged-distribution instructions are documented, and the physical-device
  results plus final build approval are recorded. Independently recoverable owner-controlled copies
  of the backup identity, production database password, and Android signing material remain external
  continuity tasks; they do not block controlled use of the already signed and verified APK.

### Phase B1 — Supabase and environment foundation

- [x] **B1.1** Create a hosted Supabase development project and record its project
  reference and selected database region. Create production as a separate project
  before release.
- [x] **B1.2** Add Supabase Kotlin Auth, PostgREST, and Functions modules plus the
  Android Ktor engine.
- [x] **B1.3** Add guarded Android client initialization using `SUPABASE_URL` and
  `SUPABASE_PUBLISHABLE_KEY` from Gradle properties or environment variables.
- [x] **B1.4** Install/configure the Supabase CLI and initialize a repository-local
  `supabase/` workspace for migrations, seed data, Edge Functions, and local services.
- [x] **B1.5** Link the CLI to the development project without committing access tokens
  or database passwords.
- [x] **B1.6** Define environment configuration and secret handling. Never expose a
  secret key, `service_role` key, PIN pepper, database password, or access token in the
  Android app or repository.
- [x] **B1.7** Verify an Android debug build can initialize the configured development
  client and perform a harmless authenticated health/read operation.

### Phase B2 — Postgres data model and migrations

- [x] **B2.1** Document canonical tables, primary/foreign keys, ownership, timestamps,
  archival behavior, constraints, transaction boundaries, and schema versions.
- [x] **B2.2** Define tenant-scoped `shops`, `user_profiles`, and memberships/roles tied
  to Supabase Auth user IDs.
- [x] **B2.3** Define products, immutable inventory lots, and append-only inventory
  movements with database constraints.
- [x] **B2.4** Define sales, sale lines, payments, discounts, lot allocations, and
  returns with foreign keys and uniqueness constraints.
- [x] **B2.5** Define vendors, purchase bills, bill lines, payments, dues, and vendor
  returns.
- [x] **B2.6** Define cash/bank accounts, expenses, deposits, withdrawals, and transfers
  as balanced, auditable ledger entries.
- [x] **B2.7** Define notifications and immutable audit records.
- [x] **B2.8** Trusted parameterized report RPCs were chosen instead of freely exposed/materialized
  summaries; they derive bounded role-shaped results from authoritative transactional records.
- [x] **B2.9** Specify Nepal business-date handling. Store authoritative `timestamptz`
  values and derive business dates using `Asia/Kathmandu` rules.
- [x] **B2.10** Implement all schema changes as versioned SQL migrations and add
  deterministic non-production seed data.

### Phase B3 — Authentication and authorization

- [x] **B3.1** Finalize how a user ID and PIN maps to a Supabase Auth identity. Keep PIN
  hashes in a private, non-client-readable table.
- [x] **B3.2** Implement a production Supabase Edge Function with salted/peppered PIN
  verification and a safe Supabase Auth session-establishment flow.
- [x] **B3.3** Add login rate limiting, failed-attempt tracking, temporary lockout, and
  generic error responses.
- [x] **B3.4** Store application role and `shop_id` in authoritative membership/profile
  rows; keep JWT claims minimal and refresh-safe.
- [x] **B3.5** Replace `PreviewAuthRepository` with Supabase Functions/Auth integration.
- [x] **B3.6** Implement Super Admin creation, disabling, and PIN reset for Owners.
- [x] **B3.7** Implement Owner creation, disabling, and PIN reset for Salesmen.
- [x] **B3.8** Define secure reauthentication and offline-session behavior.
- [x] **B3.9** Enable Row Level Security on every exposed table and write policies for
  authentication, tenant isolation, role permissions, immutable rows, and allowed
  field changes.
- [x] **B3.10** Add local integration tests proving allowed and denied access for every
  role, including attempts to cross `shop_id` boundaries.

### Phase B4 — Transactional backend operations

- [x] **B4.1** Implement validated, tenant-safe product/SKU creation and updates.
- [x] **B4.2** Implement purchase receipt creation and immutable FIFO lot creation.
- [x] **B4.3** Implement atomic sales with price validation, FIFO allocations, inventory
  movements, payment records, and totals.
- [x] **B4.4** Define and implement negative-stock policy, shortage records, and Owner
  notification.
- [x] **B4.5** Implement idempotency keys to prevent duplicate sales, purchases,
  returns, and payments during retries.
- [x] **B4.6** Implement returns against original sales and allocations, including
  partial-return limits and refund records.
- [x] **B4.7** Implement damage, loss, and manual adjustment workflows with reason,
  actor, and audit trail.
- [x] **B4.8** Implement vendor bill, payment, due, and vendor-return transactions.
- [x] **B4.9** Implement cash/bank expenses and transfers using balanced, auditable
  ledger mutations.
- [x] **B4.10** Use database timestamps and server-authoritative authenticated identity
  for all financial and inventory mutations.

### Phase B5 — Android data integration

- [x] **B5.1** Introduce dependency injection and production repositories for Supabase
  Auth, PostgREST/RPC, and Edge Functions.
- [x] **B5.2** Add ViewModels and explicit loading, success, empty, and error states.
- [x] **B5.3** Connect dashboard summaries to authorized tenant data.
- [x] **B5.4** Replace static action cards with navigation and functional screens.
- [x] **B5.5** Implement product, stock, sale, return, vendor, cash/bank, user, report,
  and notification screens.
- [x] **B5.6** Add Room as an explicit Android offline cache/outbox and define offline
  reads, queued writes, retry behavior, idempotency, and conflict UX for every mutation.
- [x] **B5.7** Prevent the preview repository from being included in release builds.

### Phase B6 — Reports, notifications, and operations

- [x] **B6.1** Implement trusted sales, gross-profit, stock, vendor, and cash/bank
  reports from authoritative records or verified aggregates.
- [x] **B6.2** Implement notification creation, read state, delivery, and retention.
- [x] **B6.3** Add Postgres indexes based on measured query plans and document why each
  is needed.
- [x] **B6.4** Add Supabase local-stack integration tests for successful operations,
  authorization failures, duplicate retries, concurrency, partial returns, and
  insufficient stock.
- [ ] **B6.5** Establish logging, monitoring, alerting, backups, restore testing, and
  retention policy.
- [x] **B6.6** Establish SQL migration, data repair, seed-data, and deployment
  procedures using the Supabase CLI.
- [ ] **B6.7** Add release signing, obfuscation review, secure CI/CD, and staged rollout.

## Known issues and decisions

### 2026-09-05 negotiated-price authorization boundary

- Product `default_selling_price_paisa` is now a suggested/reference value. The immutable sale-line
  `effective_unit_price_paisa` is authoritative revenue and may be supplied by Owner or Salesman.
- Salesman permission did not expand to discounts, credit, partial settlement, cost, or profit.
  Server authorization continues to reject those fields and Salesman report responses still omit
  FIFO cost and gross profit.
- Trusted Owner profit is unchanged mathematically: actual posted/returned net revenue minus exact
  sale/return FIFO allocation cost. A report fixture now deliberately differs from the product
  suggestion so regression tests cannot accidentally calculate profit from product master price.
- Local Docker is unavailable. The migration parsed successfully through `pglast`, and the exact
  function-body replacement was statically proven once; fresh Postgres migration replay, lint,
  pgTAP, and backend integration remain mandatory PR gates before production deployment.

### 2026-08-30 shop-deletion PIN report is client/preflight, not credential lockout

- The privacy-safe production snapshot proves the active Super Admin has a credential, is not
  locked, has zero accumulated login failures, and has produced no persisted shop-deletion request.
  Therefore the reported deletion failure cannot yet be classified as a failed hosted PIN hash.
  The current correction targets disabled/ambiguous client preflight and rejected-request reuse.
- Existing PIN verification intentionally accepts 4–8 digits for legacy credentials; creation and
  reset of new PINs remains 6–8 digits. Do not weaken reauthentication, reveal the PIN, or substitute
  an Owner/device PIN. A new APK/device retest must use the same PIN that successfully signs in to
  the currently active Super Admin account.

- **Reported rc11 PIN rejection:** no Android device is connected, so the installed package/version
  cannot yet be independently read. Secure provisioning has always required 6–8 digits; a phone PIN,
  Windows PIN, Owner PIN, or a presumed four-digit legacy value is not the Super Admin credential.
  Read-only aggregate reconciliation must determine whether the request reaches hosted
  reauthentication and whether the sole active Super Admin credential is login-locked before any
  recovery design or credential mutation is considered.

- **Legacy PIN compatibility boundary:** existing credential verification accepts 4–8 digits so a
  legacy Super Admin can sign in and reauthenticate destructive administration. Credential creation
  and reset remain 6–8 digits, weak-PIN rejection remains active, and no PIN is migrated or exposed.
  The protected production-signed rc11 replacement passes every source, production-binding, signature,
  and artifact check and is now the only checksum-pinned installer target.

- **Launch decision:** feature implementation, production backend deployment, production Super Admin
  bootstrap, authenticated session/RLS verification, protected signing, exact artifact pinning,
  restore RPO/RTO, Owner/Salesman role isolation, core financial/inventory workflows, offline/retry,
  accessibility, and physical performance evidence are complete. The accounting-period provisioning
  correction is deployed and its purchase retry/read-back passed without changing rc9 application
  bytes. The 2026-08-24 final automated gate passed, and the operator accepted the completed rc9
  workflow as the final in-app test. There is no remaining code or device-qualification blocker for
  controlled direct APK use. Independently recoverable owner copies of backup/signing/database
  recovery material remain operational-continuity work before broad unattended rollout.
- **Restore ACL decision:** a fresh hosted Supabase target can grant broader `public` defaults to
  `anon`/`authenticated` than the source. Normalize those target defaults before object creation or
  revoke inherited rights/replay the authenticated dump's ACL statements transactionally afterward;
  then require all permission pgTAP suites. Capture row counts before the committing concurrency
  harness and remove its reviewed fixtures before an empty-source bootstrap check.
- **Accessibility device gate:** automated semantics, font-scale, contrast, Nepal date/currency,
  keyboard, slow-state, and source-regression checks pass. Physical TalkBack traversal, offline
  announcement behavior, approximately 200% text, keyboard reachability, and non-touch focus/dialog
  activation also pass on signed rc9.
- **Feature integration complete:** Tasks 5.1–5.10 provide functional account, product,
  vendor/purchase/financial, stock, POS, sale-return, cash/bank/expense, trusted dashboard/
  report, and notification workflows. No feature placeholder remains.
- **Shop mutation scope:** rc2 adds active-Super-Admin-only initial shop creation through a
  protected exactly-idempotent RPC; direct table writes remain revoked and system accounts/audit
  are atomic. Shop archive/reactivation remains intentionally deferred until lifecycle constraints
  for memberships, sessions, drafts, open periods, and retained business history are approved.
  The Task 4.6 outbox transport is wired, but feature repositories must call it only for
  the documented supported operations and keep all other mutation controls online-only.
- **Offline mutation policy:** only product management and notification read state may
  queue. Sales, purchase receipts, returns, stock adjustments, vendor financial events,
  ledger entries, and account administration require a live connection. Terminal rows
  are retained for owner-scoped resolution; no backend message or credential is stored.
- **Hosted development backend:** project `zniqkuwktvincjndcgpu` (`Gdad Bags`) is in
  Northeast Asia (Seoul). Migrations match through `20260728110000`; hosted lint is
  clean; `pin-login`, `manage-users`, and `manage-accounts` are deployed. A managed
  Super Admin and correct-PIN refreshable session have been verified. Hardened method,
  request, key, credential, and gateway-JWT failures have also been smoke-tested without
  logging credentials. Android feature integration is complete through the Phase 5 exit gate.
- **Hosted Auth configuration pending:** do not run `supabase config push` until the
  local-only Auth `site_url` and redirect URLs are replaced with the agreed Android
  deep-link/callback configuration. Hosted signup settings have not yet been verified.
- **Local database verification:** Docker or another compatible container runtime is
  not installed on the current machine. SQL grammar, including the generalized security
  suite, was checked locally. GitHub Actions run `30438664328` replayed the database from
  zero and passed 697 pgTAP assertions plus the real concurrency/integration harness.
- **Client library caveat:** `supabase-kt` is community-maintained. Pin and test upgrades;
  do not assume API compatibility across releases.
- **Security design:** every tenant-owned row must carry `shop_id`; RLS and protected
  database/Edge Functions must derive the authoritative tenant and actor from the
  authenticated identity rather than trusting client input.
- **Inventory design:** purchases/manual additions create immutable lots; mutations
  should be represented by append-only events. Sales retain exact lot allocations so
  returns can restore their source lots.
- **Negative stock:** current domain allocation reports shortages, but the business
  policy, persisted shortage record, accounting behavior, and notification path remain
  undecided.
- **Environment separation:** development remains `zniqkuwktvincjndcgpu`; production is the
  distinct healthy Seoul project `skfxfbssfeetquteubcn`. All 28 production migrations and three
  Functions are deployed. Production contains the single enabled bootstrap Super Admin and no shop
  or business rows. Never target development with production credentials or copy development
  identities, secrets, sessions, or fixtures into production. The phone's validation message while
  the same credentials pass production end to end is evidence that the installed package must be
  replaced or verified before further credential attempts.

## Code map

- `app/src/main/java/com/gdad/bags/MainActivity.kt` — Android entry point.
- `app/src/main/java/com/gdad/bags/ui/GdadApp.kt` — login and static role dashboards.
- `app/src/main/java/com/gdad/bags/data/auth/ProductionAuthRepository.kt` and
  `SupabaseAuthDataSources.kt` — serialized production PIN/session/identity flow.
- `app/src/main/java/com/gdad/bags/data/auth/EncryptedSessionManager.kt` — Android
  Keystore AES-GCM Supabase Auth storage; preferences contain ciphertext only.
- `app/src/main/java/com/gdad/bags/GdadApplication.kt` and `di/AppContainer.kt` — explicit
  application-scoped production/test dependency boundary.
- `app/src/main/java/com/gdad/bags/data/remote/SupabaseClientFactory.kt` — guarded,
  stateless Supabase Auth/PostgREST/Functions client construction.
- `app/src/main/java/com/gdad/bags/data/remote/RemoteDtos.kt` — typed hosted
  function/query transport contracts.
- `app/src/main/java/com/gdad/bags/data/remote/RemoteContracts.kt` — bounded execution,
  error/retry classification, one auth-refresh retry, and sanitized diagnostic metadata.
- `app/src/main/java/com/gdad/bags/data/local/CacheEntities.kt` and `CacheDao.kt` —
  tenant/user-owned Room offline read/outbox schema and owner-filtered Flow queries.
- `app/src/main/java/com/gdad/bags/data/local/RoomCacheDatabase.kt` and
  `RoomCacheStore.kt` — explicit migrations, transactional snapshot replacement, and purge.
- `app/src/main/java/com/gdad/bags/data/local/CacheSynchronizer.kt` — serialized typed
  remote refresh with last-good-cache retention.
- `app/src/main/java/com/gdad/bags/data/local/MutationOutbox.kt`, `OutboxWorker.kt`, and
  `data/remote/SupabaseOutboxDispatcher.kt` — durable queue policy, processing, and RPC dispatch.
- `app/schemas/com.gdad.bags.data.local.RoomCacheDatabase/1.json` and `2.json` — committed Room schemas.
- `docs/offline-cache.md` — ownership, refresh, storage, and migration contract.
- `app/src/main/java/com/gdad/bags/domain/auth/Authentication.kt` — authentication
  repository/use-case interfaces and domain result.
- `app/src/main/java/com/gdad/bags/ui/auth/AuthViewModel.kt` — injected authentication
  ViewModel and immutable Compose UI state.
- `app/src/main/java/com/gdad/bags/ui/navigation/AppNavigation.kt` — typed routes,
  complete role policy, visible navigation model, and fail-closed external URI policy.
- `app/src/main/java/com/gdad/bags/ui/components/SharedStates.kt` — accessible reusable
  loading, empty, error, ready, retry, and confirmation UI.
- `app/src/main/java/com/gdad/bags/ui/components/BusinessDateField.kt` and
  `domain/model/NepalDateTime.kt` — validated Nepal business-date input and canonical
  `Asia/Kathmandu` transaction clock.
- `app/src/main/java/com/gdad/bags/data/account/`, `domain/account/`, and `ui/account/` —
  Task 5.1 protected account directory, mutations, Room store, ViewModel, and screens.
- `docs/android-architecture.md` — Task 4.1 dependency ownership and layer rules.
- `app/src/main/java/com/gdad/bags/domain/model/Models.kt` — initial domain models.
- `app/src/main/java/com/gdad/bags/domain/inventory/FifoAllocator.kt` — pure FIFO
  allocation and restoration logic.
- `app/src/test/java/com/gdad/bags/domain/inventory/FifoAllocatorTest.kt` — inventory
  unit tests.
- `app/build.gradle.kts` — Android and client dependency configuration.
- `supabase/config.toml` — local Supabase service and Auth configuration.
- `supabase/migrations/20260715084551_core_foundation.sql` — initial tenant, identity,
  product, FIFO lot, movement, privilege, and RLS schema.
- `supabase/migrations/20260715121500_authentication_contract.sql` — Argon2id PIN
  verifier and external pepper-version constraints.
- `supabase/migrations/20260715143000_pin_login_lockout.sql` and
  `20260715151000_pin_login_prepare_conflict_fix.sql` — private counters and atomic RPCs.
- `supabase/migrations/20260724101500_disabled_user_rls_lockdown.sql` — fail-closed
  public-row visibility for disabled profiles with stale sessions.
- `supabase/functions/pin-login/` — strict hosted PIN verifier and Auth session exchange.
- `supabase/functions/deno.json` / `deno.lock` — pinned Edge imports and verification task.
- `supabase/functions/tests/pin-login/core.test.ts` — request, HMAC, identity-isolation,
  Argon2id, and source-fingerprint tests.
- `supabase/tests/database/pin_login_lockout.test.sql` — privilege/rate/lock/reset pgTAP.
- `supabase/tests/database/authentication_contract.test.sql` — B3.1 credential schema
  and privilege tests.
- `docs/authentication.md` — authoritative user-ID/PIN mapping and session contract.
- `supabase/tests/database/core_foundation.test.sql` — pgTAP structure, RLS, and
  privilege tests.
- `supabase/tests/database/authorization_matrix.test.sql` — complete principal,
  cross-shop, RPC-grant, forged-write, and immutable-row authorization coverage.
- `docs/authorization-matrix.md` — canonical table/RPC permission matrix and coverage
  map.
- `.github/workflows/database-tests.yml` — Docker-backed database verification in CI.
- `docs/security-hardening-audit.md`, `app/src/main/res/xml/`, and
  `supabase/tests/database/security_hardening.test.sql` — Task 6.2 findings, explicit
  Android transport/transfer controls, and generalized database security regression gate.
- `docs/accessibility-nepal-ux-audit.md` — Task 6.3 workflow matrix, contrast evidence,
  automated accessibility/Nepal UX coverage, and physical-device sign-off procedure.
- `supabase/integration-tests/` — real multi-session backend concurrency setup, runner,
  and final invariant verification outside pgTAP discovery.
- `docs/backend-phase3-exit-gate.md` — Phase 3 evidence, coverage, and Android handoff.
- `package.json` / `pnpm-lock.yaml` — pinned Supabase CLI tooling.
- `tools/install-release-candidate.ps1` — exact-candidate verification and fail-closed fresh/upgrade
  installation on one authorized Android device.
- `docs/release-candidate-handoff.md` — signed candidate identity, physical acceptance matrix,
  rollback/incident policy, support path, and staged-distribution gate.
- `build-apk.ps1` — local test/build/APK copy workflow.
- `README.md` — project overview and build instructions.

## Latest verification

### 2026-09-05 — rc13 flexible sale pricing local gate

- Focused Android command: `:app:testDebugUnitTest` for
  `ProductionSaleCheckoutRepositoryTest` and `SaleCheckoutScreenTest`, offline, one worker, and
  in-process Kotlin passed 8 tests after correcting only an off-screen test assertion to scroll to
  the payment field. The initial restricted attempt was inconclusive because Windows denied one
  project-local Gradle cache JAR; the unrestricted rerun compiled and passed.
- Complete Android command: `verifyReleaseAuthSafety verifyReleaseAccessibilitySafety
  verifyReleasePerformanceSafety verifyReleaseArtifactSafety testDebugUnitTest lint assembleDebug
  --no-daemon --offline --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` passed in
  6m22s (`BUILD SUCCESSFUL`, 109 tasks). All 194 tests in 47 suites passed with zero failures/errors;
  lint reports zero errors and 10 warnings. Release auth/accessibility/performance/artifact checks
  all passed. The unsigned release APK is 57,477,165 bytes; debug APK is 76,941,343 bytes with
  SHA-256 `D830903BF66CD69FECFA3392BF95464B1A597174A0E338FA78FEFC6046FCA5F7`.
- SQL safety: `pglast` parsed migration `20260905120000_flexible_sale_pricing.sql` as four statements.
  A dry source-string audit found the expected restrictive authorization rule exactly once and the
  transformed definition contains zero old rules and exactly one flexible rule. Fresh database
  execution is intentionally pending Docker-backed PR CI.
- Hygiene: `git diff --check` passed before the final status update; no hosted database, user,
  account, shop, Auth identity, business row, Edge Function, secret, or release artifact changed.

### 2026-09-03 — Install and launch rc12 on Xiaomi HyperOS

- Device discovery: bundled ADB reported one authorized Xiaomi `23021RAAEG`, Android 14/API 34,
  using the primary Android user. No device-owner/profile-owner install restriction was present.
- Install path: guarded verification passed before mutation. HyperOS returned
  `INSTALL_FAILED_USER_RESTRICTED` for streamed and non-streamed ADB installs; the exact APK was then
  pushed to `/sdcard/Download` and installed through Xiaomi File Manager/native package installer.
- Installed identity: `pm path` reports `com.gdad.bags`; `dumpsys package` reports rc12/code13 and
  SDK 31/36; device-side `sha256sum` exactly matches the protected artifact checksum.
- Launch/readiness: `am start -W` returned `Status: ok`, `Activity: com.gdad.bags/.MainActivity`, and
  160 ms total time. UI inspection showed the role-appropriate dashboard and a successful trusted-
  report refresh. The app process remained running after the temporary Download APK was removed.

### 2026-09-03 — Pin and independently verify protected rc12

- Provenance: GitHub PR #68 is merged from exact head
  `f851fb23c2b260c20f54276209b9e60477d42e9a` as main
  `317e2ad7146804ed62d4f7d3839ec86e51e9587a`. Protected workflow `33476614349`
  completed successfully on that exact main commit; both `verify-android` and `production-release`
  passed. Artifact `9788683152` remains retained through `2026-09-15T06:25:03Z`.
- Integrity: downloaded sidecar and independent `Get-FileHash -Algorithm SHA256` both report
  `0056F8A3099F69C4FCCB157C599C3369E8AFCCFD901B3AB381C3F4C3D895DBEA` for the
  57,493,549-byte APK.
- Identity: bundled `apksigner verify --verbose --print-certs` passes v2 with one signer and the
  pinned production certificate; bundled `aapt dump badging` passes package `com.gdad.bags`,
  rc12/code13, SDK 31/36, label `GDAD BAGS`, launcher activity, and all five icon densities.
- Binding/safety: an independent uncompressed-entry scan finds the production project reference in
  one entry, the development project reference in zero, and the release gate's forbidden preview,
  secret-key, PIN/rate-pepper, bootstrap-token, and diagnostic-token markers in zero.
- Guard: `tools/install-release-candidate.ps1 -InstallMode VerifyOnly` passes against the root
  handoff APK after advancing its expected file/version/code/hash to rc12.

### 2026-08-30 — Shop-deletion client/preflight correction

- Production privacy-safe reconciliation: passed through the official Supabase Management API
  using the existing CLI credential in memory; the tracked aggregate-only query returned one active
  unlocked Super Admin credential, zero Super Admin failed attempts, two active shops, and zero
  shop-deletion requests/failures. Secret values and row identifiers were not printed.
- Focused account verification: `testDebugUnitTest` for `AccountManagementViewModelTest`,
  `AccountManagementScreenTest`, and `ProductionAccountManagementRepositoryTest` passed (`BUILD
  SUCCESSFUL`, 32 tasks). The first sandboxed offline attempt compiled successfully but Robolectric
  could not fetch its missing official SDK artifact; the corrected online run passed.
- Complete Android gate: `verifyReleaseAuthSafety verifyReleaseAccessibilitySafety
  verifyReleasePerformanceSafety verifyReleaseArtifactSafety testDebugUnitTest lint assembleDebug
  --no-daemon --offline --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` passed
  (`BUILD SUCCESSFUL`, 109 tasks). It also assembled the unsigned release artifact required by the
  artifact-safety scan. `git diff --check` passed; line-ending notices only.
- Post-version-bump rerun: the same complete 109-task Android gate passed on rc12/code13 (`BUILD
  SUCCESSFUL` in 12m07s), including rebuilt release/debug variants, artifact safety, the full unit
  suite, lint, and debug APK assembly.

### 2026-08-26 - Deploy, sign, and independently pin rc11

- Status: **PASS** for merge, production Function deployment, protected signing, independent APK
  inspection, and controlled direct-installation pinning.
- Traceability: PR #64 exact head `5536dacc3830dd257d52cd5e4be56560523b4feb` merged as exact main
  `45bdd93a6c7a0aef1d3690306c2cfcd8b7dc07f7`; required Database run `32871787076` and
  Android run `32871787441` pass.
- Backend: protected run `32872604166` passed all pre-deploy/replay/linked/probe stages and redeployed
  all three reviewed Functions to production project `skfxfbssfeetquteubcn`. No new migration was
  pending, and no application data or identity was created, deleted, or changed.
- Android: protected run `32872992204` passed `verify-android` and `production-release`. Artifact
  `9594853682`, retained through `2026-09-09T05:56:22Z`, contains the 57,477,165-byte
  `GDAD-BAGS-0.2.0-rc11-12-release.apk`; file hash matches its sidecar at
  `A5434D766D843E25EA6E985E35F39A56EA3EB0F23A5807586E6330A38B55A0FF`.
- Independent inspection: `apksigner` passes v2 with one signer and certificate SHA-256
  `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`; `aapt` passes
  package `com.gdad.bags`, rc11/code 12, SDK 31/36, label, launch activity, and five icon densities.
  Archive scan reports `production_binding_files=1`, `development_binding_files=0`, and
  `forbidden_marker_files=0`.
- Guarded installer: `tools/install-release-candidate.ps1 -InstallMode VerifyOnly` passed at
  `2026-08-26T11:46:12.1090294+05:45` with the same checksum, signer, package, and version; it reports
  `Installed=false` and records no device serial.
- Data/security impact: only reviewed production Functions changed. No migration, PIN, secret, token,
  session, Auth identity, shop, or business row was logged or mutated; no app-store publication or
  paid service ran.
- Next: install/upgrade exactly rc11 on one authorized phone and retry deletion only against a clearly
  disposable empty test shop before considering any live-shop deletion.

### 2026-08-25 - Verify legacy PIN compatibility contract and local release gates

- Status: **LOCAL PASS**; complete Edge and Android local release gates pass, while hosted review,
  deployment, signing, and final artifact pinning remain.
- Changed: shared PIN derivation/verification, `pin-login` and `manage-accounts` request parsers,
  Android authentication/account repository/UI guards, their regression tests, source release metadata,
  account-administration/release documentation, and this status file.
- Command: from `supabase/functions`, repository-local Deno 2.4.0 ran targeted `fmt --check`, then
  `lint .`, type-check of all three Function entry points, and `test --allow-env tests`. Android ran
  the three focused authentication/account/UI test classes, then `verifyReleaseAuthSafety
  verifyReleaseAccessibilitySafety verifyReleasePerformanceSafety verifyReleaseArtifactSafety
  testDebugUnitTest lint assembleDebug --no-daemon --offline --max-workers=1`.
- Result: all 36 Edge tests pass. Coverage proves a historical 4-digit Argon2id verifier can be
  checked with the unchanged peppered material, wrong legacy PINs fail, 3-/9-digit and non-digit values
  fail, login/admin/delete verification accepts 4 digits, and `createPinHash` plus reset parsing still
  reject a 4-digit new credential. The focused Android run passes all 31 tests; the complete Android
  gate is `BUILD SUCCESSFUL in 7m 16s` with 109 tasks, all four release safety checks, all unit tests,
  release artifact inspection, lint with 0 errors/9 warnings, and debug assembly passing. The first
  focused run was blocked by restricted cache access, then exposed only a new test fixture's default
  subject mismatch; authorized cache access and the corrected subject-bound fixture produced the
  recorded pass. No production-code assertion failed.
- Data/security impact: no migration, hosted function, database row, Auth identity, session, shop, or
  business record changed. No credential value is recorded. Existing authorization, rate, audit, and
  deletion boundaries remain in force.
- Next: publish the reviewed fix, deploy only the three Functions, and sign/inspect/pin rc11.

### 2026-08-24 - Complete the final automated gate and close device testing

- Status: **PASS**; the completed signed-rc9 physical workflow is the final in-app acceptance cycle.
- Changed: `tools/measure-android-performance.ps1`, `docs/performance-reliability-audit.md`,
  `docs/release-candidate-handoff.md`, and `PROJECT_STATUS.md`; no Android application source changed.
- Operator decision: skip an additional in-app retest and proceed with final APK build/handoff. No
  Android application source changed after the physically measured checksum-pinned candidate.
- Command: with repository-local JDK 17, Android SDK, and offline Gradle cache configured,
  `.\gradlew.bat verifyReleaseAuthSafety verifyReleaseAccessibilitySafety
  verifyReleasePerformanceSafety verifyReleaseArtifactSafety testDebugUnitTest lint assembleDebug
  --no-daemon --offline --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process'`.
- Result: `BUILD SUCCESSFUL in 10m`; 109 actionable tasks (40 executed, 69 up-to-date). Authentication,
  accessibility, performance, and assembled-release artifact safety passed; unit tests, lint, unsigned
  release assembly, and debug assembly passed.
- Final APK verification: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
  .\tools\install-release-candidate.ps1 -InstallMode VerifyOnly` passed on the unchanged signed rc9
  artifact. It confirmed package `com.gdad.bags`, version `0.2.0-rc9`/10, pinned APK SHA-256
  `99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC`, and pinned signer
  certificate SHA-256 `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`.
- Environment note: two earlier invocations stopped before a result because the shell lacked Android
  SDK configuration and the restricted Windows process could not read one local Gradle cache JAR.
  Explicit local SDK/JDK paths plus the approved unrestricted Gradle process resolved the host issue;
  no application test failed.
- Data/security impact: no hosted data, device data, release credentials, or application bytes changed.
  No device serial, PIN, token, credential value, or business value is recorded.
- Publication: PR #60 merged exact verified head `fd4afafea392cda9184cce656b50faac4360134c`
  into `main` as `67160a1`, publishing the qualification evidence and measurement-script correction.
- Next: hand off the exact production-signed rc9 APK for controlled direct use. Complete the three
  independent recovery-material copies before broad unattended rollout; do not rebuild or rename
  different bytes under version code 10.

### 2026-08-23 - Pass physical cold-start, memory, and frame-performance budgets

- Status: physical signed-candidate performance **PASS**; final automated repository gate subsequently
  passed on 2026-08-24.
- Candidate/device: production-signed `0.2.0-rc9` (`versionCode = 10`, source `43ec931...`, pinned APK
  SHA-256), Redmi model code `25062RN2DA`, Android 16/API 36. Authorized USB ADB used local
  platform-tools 37.0.1 with `ADB_USB_LEGACY=1`; device serial is intentionally omitted.
- Cold-start command: `$env:ADB_USB_LEGACY='1'; powershell.exe -NoProfile -ExecutionPolicy Bypass
  -File .\tools\measure-android-performance.ps1 -ColdStartRuns 5` returned runs 1,742, 702, 665, 679,
  and 698 ms; median 698 ms versus the 2,500-ms budget. Immediate PSS was 78,585 KiB. **PASS**.
- Frame workload: after `dumpsys gfxinfo com.gdad.bags reset`, the operator completed product
  search/scroll, report load/scroll, existing return navigation, notification detail/back, and
  Dashboard return without posting a transaction.
- Current-only command: `$env:ADB_USB_LEGACY='1'; powershell.exe -NoProfile -ExecutionPolicy Bypass
  -File .\tools\measure-android-performance.ps1 -CaptureCurrentOnly` returned warm PSS 134,898 KiB
  (about 131.7 MiB) versus the 300-MiB budget and 51 janky of 3,051 frames (1.67%) versus 5%. **PASS**.
- Tool verification: the successful full and current-only physical runs prove the one-device array and
  explicit `-Arguments` fixes. `git diff --check` passed before both successful measurements.
- Data/security impact: measurement force-stopped/launched the app and reset frame counters only; no
  business mutation was posted and no serial, credential, PIN, token, or business value was retained.
- Next: publish accumulated physical qualification evidence and the measurement-script correction,
  retaining the unchanged signed APK bytes as the final tested artifact.

### 2026-08-23 - Fix single-device serial parsing in the physical performance capture

- Status: two PowerShell argument-normalization fixes implemented; patched physical rerun pending.
- Failure evidence: with one authorized Redmi visible, running
  `$env:ADB_USB_LEGACY='1'; powershell.exe -NoProfile -ExecutionPolicy Bypass -File
  .\tools\measure-android-performance.ps1 -ColdStartRuns 5` failed before any measurement because the
  script derived a one-character pseudo-serial from the first character of the single device line.
- Root cause: PowerShell unwraps a one-item pipeline result unless it is explicitly array-materialized;
  `$deviceLines[0]` therefore indexed the first character of the string rather than the first line.
- Fix: wrap the filtered `adb devices` pipeline in `@(...)`; serial selection now receives the complete
  line for exactly one device while existing explicit-serial, zero-device, and multiple-device guards remain.
- Second failure evidence: the first post-array rerun selected the full device correctly, then PowerShell
  bound Android Activity Manager flag `-W` as an ambiguous parameter of the surrounding pipeline and
  stopped before the first launch measurement.
- Second root cause/fix: positional calls into advanced function `Invoke-Adb` allowed dash-prefixed ADB
  arguments to enter PowerShell parameter binding. Every call now passes an explicit string array through
  `-Arguments`, preserving `-W` and `-n` as literal Android command arguments.
- Data/security impact: local ignored Android platform-tools were updated to 37.0.1 and run with
  `ADB_USB_LEGACY=1`; the source change contains no device serial, credential, token, PIN, or business data.
- Next: rerun the five-start capture against the authorized Redmi and evaluate all recorded budgets.

### 2026-08-23 - Complete physical non-touch focus, dialog, editing, and activation pass

- Status: physical hardware-keyboard or Switch Access workflow **PASS**.
- Device evidence: the operator reported exactly
  `non touch focus dialog editing and activation correct` after forward/back Dashboard traversal,
  New expense dialog containment/editing, non-touch Cancel activation, and Report field/action focus.
  No input value, business value, identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: focus followed visible order in both directions, remained contained in the modal,
  text controls accepted non-touch editing, and buttons could be activated without touch; Cancel created
  no expense or other business mutation.
- Accessibility exit: TalkBack order/live regions, 200% text/portrait reachability, keyboard avoidance,
  non-touch focus/containment/editing, and one slow/offline failure announcement now pass physically.
- Next: connect the qualified phone over authorized ADB and capture five cold starts, warm PSS, and frame
  statistics using the pinned performance script/budgets.

### 2026-08-23 - Complete physical 200% text, portrait, keyboard, and action reachability pass

- Status: physical large-text/layout workflow **PASS**.
- Device evidence: the operator reported `200 percent text portrait keyboard and actions correc`,
  interpreted as the requested successful confirmation, after using approximately 200%/largest Android
  font in portrait across Dashboard, New sale, Returns, Reports, Notifications, Cash and bank, a
  keyboard-visible form, Logout, Login, and restored Owner Dashboard. No credential or business value
  was requested or collected.
- Accepted behavior: content remained scrollable, labels/actions did not clip or overlap, bottom/final
  actions stayed reachable after keyboard appearance/closure, and large-text Logout/Login plus restored
  Dashboard remained usable.
- Data/security impact: accessibility and authentication/cache-cycle verification only; no business
  transaction, role change, or tenant-access policy change occurred.
- Next: use a connected hardware keyboard or Android Switch Access to verify forward/back focus order,
  dialog focus containment, text editing, and action activation without touch.

### 2026-08-23 - Complete physical TalkBack and offline-error announcement pass

- Status: physical TalkBack navigation/live-region workflow **PASS**.
- Device evidence: the operator reported `talkback order labels and offline announcement correc`,
  interpreted as the requested successful confirmation, after traversing Dashboard, Products, an
  existing Return, Reports, Notifications, and one disconnected Refresh. No spoken business value,
  identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: headings, role/unread context, controls, fields, and related navigation followed
  logical visible order with understandable non-duplicated announcements; the offline failure was
  announced once while cached content remained; no hidden credential was spoken.
- Data/security impact: accessibility/read-only verification plus one safe rejected refresh; no
  business mutation, outbox operation, role change, or tenant-access policy change occurred.
- Next: set Android font size to its largest/approximately 200% setting in portrait and verify Login,
  Dashboard, sale form, return history, Reports, Notifications, dialogs, keyboard avoidance, and final
  actions remain reachable without clipping or overlap.

### 2026-08-23 - Verify controlled stock-adjustment cycle reconciliation

- Status: physical production stock-adjustment/history/report workflow **PASS**.
- Device evidence: the operator reported exactly
  `stock adjustment net quantity value history and report correct` after refreshing Stock adjustments,
  Products, and the bounded report twice. No product, quantity baseline, cost, stock value, record
  identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: product On hand and stock value returned to their pre-test baselines, exactly one
  controlled +1 and one controlled -1 movement remain immutable, refresh exposed no duplicate, and
  Sales, Returns, Expenses, Cash, and Vendor due remained unaffected.
- Data/security impact: read-only verification after the intentional net-zero adjustment cycle; no
  additional production mutation or permission expansion was performed.
- Next: enable TalkBack and traverse Dashboard, Products, an existing Return, Reports, Notifications,
  and one safe offline/error message, checking order, labels, one-time announcements, and no credential speech.

### 2026-08-23 - Post the controlled quantity-one stock Manual remove

- Status: physical Owner stock-adjustment cleanup submission and authoritative completion **PASS**.
- Device evidence: the operator reported `stock manual remove posted` after selecting Manual remove,
  Data correction, quantity one, the exact newly created `1/1` FIFO lot, today's date, and the controlled
  cleanup note, then submitting once. No product, cost, stock value, identifier, credential, PIN, or
  token was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after the atomic exact-lot decrement,
  product stock restoration, inventory movement/adjustment journal, audit, idempotency result, and
  authoritative receipt committed.
- Data/security impact: one intentional controlled production stock-remove movement was posted; the
  add/remove cycle is designed to have zero net quantity and stock-value effect.
- Next: refresh Stock adjustments, Products, and the bounded report; verify stock quantity/value returned
  to pre-test values and both +1/-1 movement-history rows remain visible without duplication.

### 2026-08-23 - Post the controlled quantity-one stock Manual add

- Status: physical Owner stock-adjustment submission and authoritative completion **PASS**.
- Device evidence: the operator reported `stock manual add posted` after selecting Manual add, Stock
  found, quantity one, the privately displayed FIFO cost, today's date, and the controlled note, then
  submitting Post adjustment once. No product, cost, stock value, identifier, credential, PIN, or token
  was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after the atomic product stock increment,
  one-unit FIFO lot/movement, balanced inventory adjustment journal, audit, idempotency result, and
  authoritative receipt committed.
- Data/security impact: one intentional controlled production stock-add movement was posted; no role or
  tenant-access policy changed.
- Next: select Manual remove/Data correction for quantity one from that exact new one-unit lot and post
  once, then reconcile net stock/report restoration and both immutable movements.

### 2026-08-23 - Verify Salesman logout and clean Owner-session restoration

- Status: physical same-device return-to-Owner identity/cache workflow **PASS**.
- Device evidence: the operator reported exactly
  `owner restored full screens data correct and no duplicates` after Salesman logout, private Owner
  authentication, Owner destination review, and authoritative refreshes of Products, Vendors, Cash and
  bank, and Reports. No login ID, PIN, business value, identifier, credential, or token was collected.
- Accepted behavior: full Owner navigation returned, the Salesman session/restrictions did not persist,
  identity-scoped cache repopulated from the authoritative shop, and no duplicate product, bill,
  transaction, or report row appeared.
- Data/security impact: authentication and identity-scoped cache replacement only; no business mutation,
  outbox operation, role change, or tenant-access policy change occurred.
- Next: execute a quantity-one Owner stock adjustment cycle—manual add into a new controlled lot followed
  by removal from that exact lot—then verify net stock/report restoration and immutable movement history.

### 2026-08-23 - Verify Salesman identity-switch isolation and role-scoped navigation

- Status: physical same-device Salesman identity/cache isolation **PASS**.
- Device evidence: the operator reported exactly
  `salesman login role screens correct and no owner cache` after private Salesman authentication,
  destination review, and an online Products refresh. No login ID, PIN, product value, identifier,
  credential, or token was requested or collected.
- Accepted behavior: the Salesman saw only New sale, Products and stock, Damage or loss, Product return,
  Reports, and Notifications; Vendors, Cash and bank, and Salesmen/Accounts were absent. The current-shop
  product refreshed without exposing prior Owner-only finance, vendor, or account cache data.
- Data/security impact: authentication and identity-scoped cache replacement only; no business mutation,
  outbox operation, role change, or tenant-access policy change occurred.
- Next: log out the Salesman, sign in privately as the existing Owner, and verify the full Owner navigation
  plus authoritative shop data repopulate without Salesman-session restoration or duplicate business rows.

### 2026-08-23 - Verify Owner logout, protected-route closure, and no session restoration

- Status: physical production logout/local-purge workflow **PASS**.
- Device evidence: the operator reported exactly
  `owner logout login screen and session not restored` after accepting the explicit offline-data removal
  warning, reaching Login, checking Back navigation, removing the app from Recents, and reopening it.
  No login ID, PIN, cached business value, credential, or token was requested or collected.
- Accepted behavior: logout cleared the local authenticated state and invoked identity-scoped Room/outbox
  purge, protected business destinations were no longer reachable, and process restart remained on Login
  instead of restoring the logged-out session.
- Data/security impact: expected local session/cache/outbox removal only; authoritative Supabase business
  records and account membership were not deleted or modified.
- Next: sign in as the existing Salesman on this same device and verify only Salesman-authorized screens
  and current-shop catalog data appear, with no Owner-only cache/navigation leakage.

### 2026-08-23 - Verify safe rejection of original controlled Deposit reversal

- Status: physical reversal guard and unchanged-state verification **PASS**; Deposit intentionally retained.
- Device evidence: the original Deposit reversal returned the generic account-review validation message.
  After refreshing, the operator reported `eposit reversal safely blocked and state unchang`, interpreted
  as the requested confirmation that the reversal was blocked and state unchanged. No amount, balance,
  account/journal identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: no authoritative reversal receipt/history row was created, the original Deposit
  remains posted, and the Cash balance/history did not change. This is consistent with the backend's
  fail-closed validation/no-overdraft reversal contract; no new Deposit was created to circumvent it.
- Cleanup outcome: both controlled expenses, the controlled Withdrawal, and the Cash-to-Bank Transfer
  are reversed and preserved immutably. The original Deposit remains intentionally effective as
  traceable launch-test funding rather than forcing an unsafe compensating mutation.
- Next: while connected, log out the Owner and verify the app immediately returns to Login, no protected
  business screen remains reachable, and reopening the process does not restore the logged-out session.

### 2026-08-23 - Reverse the controlled launch-test cash withdrawal

- Status: physical Owner withdrawal cleanup reversal **PASS**.
- Device evidence: the operator reported `launch test withdrawal reversed` after selecting the exact
  unreversed Withdrawal card with the controlled description, entering the cleanup reason, and
  submitting Reverse once. No amount, balance, account/journal identifier, credential, PIN, or token
  was requested or collected.
- Accepted behavior: the original Withdrawal remains immutable and is marked reversed while its exact
  compensating cash/clearing journal, audit event, idempotency result, and receipt committed atomically.
- Data/security impact: one intentional cleanup reversal was posted; no source deletion, external money
  movement, role change, or tenant-access policy change occurred.
- Next: reverse the exact original controlled Deposit now that the dependent expense and withdrawal
  effects have been restored, then reconcile the complete finance-test cleanup.

### 2026-08-23 - Reverse the controlled Launch testing expense

- Status: physical Owner launch-expense cleanup reversal **PASS**.
- Device evidence: the operator reported `launch test expense reversed` after selecting the exact
  unreversed `Expense: Launch testing` card, entering the controlled cleanup reason, and submitting
  Reverse once. No amount, balance, account/journal identifier, credential, PIN, or token was requested
  or collected.
- Accepted behavior: the original expense remains immutable and is marked reversed while its exact
  compensating expense/cash journal, audit event, idempotency result, and receipt committed atomically.
- Data/security impact: one intentional cleanup reversal was posted; no source deletion, external money
  movement, role change, or tenant-access policy change occurred.
- Next: reverse the exact controlled `Withdrawal` transaction so its Cash effect is restored before the
  original controlled Deposit is reversed.

### 2026-08-23 - Verify recovery-expense reversal cleanup

- Status: physical post-recovery-expense-reversal reconciliation **PASS**.
- Device evidence: the operator reported exactly
  `recovery expense reversal cash history and report correct` after checking Finance, the bounded
  report, and a second refresh. No amount, balance, account/journal identifier, credential, PIN, or
  token was requested or collected.
- Accepted behavior: the original recovery expense is marked Reversed, cleanup history/reason is
  preserved, Cash was restored exactly once, effective Expenses decreased exactly once, Gross profit
  remained unchanged, and refresh exposed no duplicate reversal.
- Data/security impact: read-only verification after the intentional controlled reversal; no additional
  production mutation or permission expansion was performed.
- Next: clean up the remaining controlled finance movements in dependency-safe reverse order: reverse
  `Expense: Launch testing`, then the controlled Withdrawal, then the controlled Deposit.

### 2026-08-23 - Reverse the reconnect-recovery test expense

- Status: physical Owner recovery-expense reversal and authoritative completion **PASS**.
- Device evidence: the operator reported `reconnect recovery expense reversed` after selecting the
  exact `Expense: Offline rejection test` card, entering the controlled cleanup reason, and submitting
  Reverse once. No amount, balance, account/journal identifier, credential, PIN, or token was requested
  or collected.
- Accepted behavior: the original expense remains immutable and is marked reversed while the exact
  compensating expense/cash journal, audit event, idempotency result, and receipt committed atomically.
- Data/security impact: one intentional controlled production expense reversal was posted; no source
  record deletion, external money movement, role change, or tenant-access policy change occurred.
- Next: refresh Finance and the bounded report; verify Cash was restored once, effective Expenses
  decreased once, the original/reversal history and reason remain visible, and Gross profit is unchanged.

### 2026-08-23 - Verify reconnect retry created exactly one expense and accounting effect

- Status: physical production reconnect/idempotency reconciliation **PASS**.
- Device evidence: the operator reported exactly
  `reconnect expense single history balance and report correct` after inspecting Finance, the bounded
  report, and a second refresh. No amount, balance, account/journal identifier, credential, PIN, or
  token was requested or collected.
- Accepted behavior: exactly one `Expense: Offline rejection test` history row exists, Cash decreased
  once, Expenses increased once, Gross profit stayed unchanged, and refresh exposed no duplicate from
  the prior offline attempt or connected retry.
- Data/security impact: read-only verification after the one intended recovery expense; no additional
  production mutation or permission expansion was performed.
- Next: reverse the controlled recovery expense, then verify restored cash, reduced effective Expenses,
  preserved reversed history/reason, and unchanged Gross profit.

### 2026-08-23 - Retry the preserved finance operation once after reconnect

- Status: physical production reconnect/retry submission **PASS**; downstream duplicate reconciliation pending.
- Device evidence: the operator reported exactly `reconnect retry expense posted once` after restoring
  connectivity and tapping Retry same operation once without creating a new expense. No amount,
  balance, account/journal identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: the pending ViewModel operation retained its original idempotency key across the
  offline failure and produced one server-authoritative Expense receipt when explicitly retried online.
- Data/security impact: one intentional controlled production expense/evidence/journal/audit result was
  posted on reconnect; the earlier offline attempt remained non-mutating.
- Next: refresh finance and the bounded report; confirm exactly one matching Expense history entry, one
  cash decrease, one expense-total increase, unchanged Gross profit, and no duplicate receipt/history row.

### 2026-08-23 - Verify finance mutation is blocked safely while offline

- Status: physical production prohibited-offline-mutation workflow **PASS**.
- Device evidence: while disconnected, the operator reported the safe message
  `financial operations require an internet connection`, then exactly
  `offline finance blocked balance and history unchanged` after inspecting the finance screen. No
  amount, balance, account/journal identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: the risky expense operation was not queued or treated as successful; no receipt,
  cash change, expense/history row, or report mutation occurred. The ViewModel retained only the
  explicit same-operation retry action for a later connected attempt.
- Data/security impact: safe rejected attempt only; no backend or local business mutation occurred and
  no cross-tenant data was exposed.
- Next: reconnect the phone, tap Retry same operation exactly once, verify one authoritative expense
  receipt, then confirm exactly one matching history entry and one balance/report effect.

### 2026-08-23 - Verify owner-scoped cached reads and safe offline refresh

- Status: physical production offline read/cache-preservation workflow **PASS**.
- Device evidence: the operator reported exactly
  `offline cached reads remain and refresh failed safely` after refreshing key Owner views online,
  disabling Wi-Fi/mobile data, reopening those views, and invoking one offline Products refresh. No
  cached business value, identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: the last authoritative owner/shop-scoped Products, Vendors, Cash and bank,
  Reports, and Notifications snapshots remained readable while disconnected; the failed refresh used a
  safe offline message and did not erase or replace the last good cache.
- Data/security impact: read-only offline verification; no business mutation, outbox operation, role
  change, or tenant-access policy change occurred.
- Next: while still offline, submit one controlled finance expense request and verify it is rejected with
  the live-connection message, leaves balance/history unchanged, and offers only explicit same-operation retry.

### 2026-08-23 - Verify notifications, read receipt, and safe related routing

- Status: physical production notification workflow **PASS**.
- Device evidence: the operator reported exactly
  `notification opened routed and read receipt correct` after refreshing Notifications, opening the
  authorized Vendor return notification, using Open related record, returning, and refreshing. No
  notification body, amount, record identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: the Owner could see the authorized active notification, its detail used Nepal time
  and disclosed the 90-day window, selection persisted an idempotent read receipt, unread state/count
  updated, and the allowed vendor-return source routed safely to Vendors.
- Data/security impact: one intended notification read receipt was persisted; no business ledger,
  inventory, role, or tenant-access mutation occurred.
- Next: refresh the key Owner read models while online, disconnect the phone, and verify owner-scoped
  cached Products, Vendors, Cash and bank, dashboard/report, and Notifications remain readable while
  refresh fails safely without erasing the last good snapshot.

### 2026-08-22 - Verify transfer-reversal balances, history, and report

- Status: physical post-transfer-reversal reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `transfer reversal balances history and report correct` after checking both account balances,
  original/reversal history, the combined balance, and bounded report. No amount, balance,
  account/journal identifier, or credential was requested or collected.
- Accepted behavior: the exact compensating reversal restored Cash and Bank to their pre-transfer
  balances, preserved the original as Reversed plus its reason/reversal transaction, kept the combined
  Cash and Bank total constant, and left Sales, Expenses, and Gross profit unchanged.
- Data/security impact: read-only verification after the intentional controlled reversal; no
  additional production mutation or permission expansion was performed.
- Next: verify the Owner notification center contains authorized recent business-event notifications,
  persists a read receipt, and safely routes a selected notification to its allowed destination.

### 2026-08-22 - Reverse the first controlled production cash-to-bank transfer

- Status: physical Owner transfer reversal and authoritative completion **PASS**.
- Device evidence: the first attempt produced the generic safe validation message. After refreshing
  and explicitly selecting the card headed Transfer with the controlled description, the operator
  reported `success`. No amount, balance, account/journal
  identifier, credential, PIN, or token was requested or collected.
- Accepted behavior: the original Transfer record was preserved and marked reversed while an atomic
  exact-opposite Cash/Bank journal, audit event, idempotency result, and authoritative receipt committed.
- Data/security impact: one intentional controlled production transfer reversal was posted; no source
  record deletion, external money movement, role change, or tenant-access policy change occurred.
- Next: refresh Cash and bank plus the bounded report; verify Cash and Bank returned to their pre-transfer
  balances, reversed history/reason is preserved, the combined total remains unchanged, and operating
  metrics remain unchanged.

### 2026-08-22 - Verify first cash-to-bank transfer balances, history, and report

- Status: physical post-transfer reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `cash bank transfer balances history and report correct` after checking both account balances,
  transaction history, reversal availability, the combined balance, and bounded report. No amount,
  balance, account/journal identifier, or credential was requested or collected.
- Accepted behavior: Cash decreased and Bank increased by the same value, immutable transfer history
  retained the dated description with reversal available, the combined Cash and Bank total stayed
  constant, and Sales, Expenses, and Gross profit remained unchanged.
- Data/security impact: read-only verification after the intentional controlled internal transfer; no
  additional production mutation or permission expansion was performed.
- Next: reverse the controlled transfer with a dated reason, then verify restored Cash/Bank balances,
  preserved reversed history, unchanged combined total, and unchanged operating report metrics.

### 2026-08-22 - Post the first controlled production cash-to-bank transfer

- Status: physical Owner account-transfer submission and authoritative completion **PASS**.
- Device evidence: the operator reported `cash to bank transfer posted` after selecting main Cash as
  From, main Bank as To, today's date, and the controlled description, then submitting Transfer once.
  No credential, amount, balance, account identifier, or journal identifier was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after no-overdraft validation and the
  atomic opposing Cash/Bank entries, balanced transfer journal, derived balances, audit, idempotency
  result, and authoritative receipt committed.
- Data/security impact: one intentional controlled internal accounting transfer was posted; the app did
  not contact a bank/payment processor or change any role or tenant-access policy.
- Next: refresh Cash and bank plus the bounded report; verify Cash decreased and Bank increased by the
  same value, reversible Transfer history is present, the combined total is unchanged, and operating
  report metrics remain unchanged.

### 2026-08-22 - Verify first cash withdrawal balance, history, and report

- Status: physical post-withdrawal reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `cash withdrawal balance history and report correct` after checking the main cash balance,
  transaction history, reversal availability, and bounded report. No amount, balance, account/journal
  identifier, or credential was requested or collected.
- Accepted behavior: derived cash decreased by the withdrawal, immutable history preserved the dated
  description with reversal available, cash reporting updated, and Sales, Expenses, and Gross profit
  remained unaffected by the non-expense movement.
- Data/security impact: read-only verification after the intentional controlled withdrawal; no
  additional production mutation or permission expansion was performed.
- Next: as Owner, transfer a controlled amount from main Cash to main Bank, then verify the exact
  opposing balance changes, reversible transfer history, unchanged combined Cash and bank total, and
  unchanged sales/expenses/gross profit.

### 2026-08-22 - Post the first controlled production cash withdrawal

- Status: physical Owner cash-withdrawal submission and authoritative completion **PASS**.
- Device evidence: the operator reported `ash withdrawal posted`, interpreted as the requested
  confirmation `cash withdrawal posted`, after selecting Withdrawal, main Cash, today's date, and the
  controlled description, then submitting once. No credential, amount, balance, account identifier,
  or journal identifier was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after no-overdraft validation and the
  atomic withdrawal journal, derived account balance, audit, idempotency result, and authoritative
  receipt committed.
- Data/security impact: one intentional controlled production accounting withdrawal was posted; the app
  did not contact a bank/payment processor or change any role or tenant-access policy.
- Next: refresh Cash and bank plus the bounded report; verify reduced cash, a reversible Withdrawal
  history entry with the controlled description, and unchanged sales/expenses/gross profit.

### 2026-08-22 - Verify first cash expense balance, history, and report

- Status: physical post-expense reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly `cash expense balance history and report correct`
  after checking the main cash balance, transaction history, reversal availability, and bounded report.
  No amount, balance, account/journal identifier, or credential was requested or collected.
- Accepted behavior: derived cash decreased by the expense amount, immutable history preserved the
  dated expense with reversal available, Expenses increased by the same value, and Gross profit
  remained unchanged because the report presents operating expense separately.
- Data/security impact: read-only verification after the intentional controlled expense; no additional
  production mutation or permission expansion was performed.
- Next: as Owner, post one controlled withdrawal from main Cash for less than the available balance,
  then verify reduced cash, reversible withdrawal history, and unchanged sales/expenses/gross profit.

### 2026-08-22 - Post the first controlled production cash expense

- Status: physical Owner cash-expense submission and authoritative completion **PASS**.
- Device evidence: the operator reported `cash expense posted` after selecting main Cash, a due-bounded
  test amount, today's date, the Launch testing category, and controlled note, then submitting once.
  No credential, amount, balance, account identifier, or journal identifier was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after no-overdraft validation and the
  atomic expense evidence, balanced expense/cash journal, derived balance, audit, idempotency result,
  and authoritative receipt committed.
- Data/security impact: one intentional controlled production expense was posted; it did not invoke an
  external payment system, paid service, role change, or tenant-access policy change.
- Next: refresh Cash and bank plus the bounded report; verify reduced cash, a reversible Expense history
  entry with category/note, increased expense total, and unchanged gross profit.

### 2026-08-22 - Verify first cash deposit balance, history, and report

- Status: physical post-deposit reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly `cash deposit balance history and report correct`
  after checking the main cash balance, transaction history, reversal availability, and bounded report.
  No amount, balance, account/journal identifier, or credential was requested or collected.
- Accepted behavior: the derived cash balance increased by the deposit, immutable history preserved its
  date and description with reversal available, cash reporting updated, and sales, expenses, and profit
  remained unaffected by the non-operating movement.
- Data/security impact: read-only verification after the intentional controlled deposit; no additional
  production mutation or permission expansion was performed.
- Next: as Owner, post one controlled expense from main Cash for less than the available balance, then
  verify reduced cash, posted expense history, increased expenses, and unchanged gross profit.

### 2026-08-22 - Post the first controlled production cash deposit

- Status: physical Owner cash-deposit submission and authoritative completion **PASS**.
- Device evidence: the operator reported `cash deposit posted` after selecting Deposit, the main Cash
  account, today's business date, and the controlled description, then submitting once. No credential,
  amount, balance, account identifier, or journal identifier was requested or collected.
- Accepted behavior: the Owner-only flow reported success only after the atomic deposit journal,
  derived account-balance update, audit, idempotency result, and authoritative receipt committed.
- Data/security impact: one intentional controlled production cash deposit was posted; it is an
  internal accounting movement and did not invoke a bank, payment processor, paid service, role change,
  or tenant-access policy change.
- Next: refresh Cash and bank plus the bounded report; verify increased cash, a posted Deposit history
  entry with the controlled description, and consistent cash/report totals.

### 2026-08-22 - Verify vendor-return reversal stock, due, history, cash, and report

- Status: physical post-vendor-return-reversal reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `vendor return reversal stock due history and report correct` after checking Owner product/stock,
  vendor bill and reversal history, cash, and the bounded report. No business value, vendor, bill
  reference, identifier, or credential was requested or collected.
- Accepted behavior: the compensating reversal restored the exact original FIFO-lot quantity and
  vendor due, preserved the return with reversed status and reason, left cash unchanged, and updated
  stock/payable report projections consistently without deleting financial history.
- Data/security impact: read-only verification after the intentional controlled reversal; no
  additional production mutation or permission expansion was performed.
- Next: as Owner, post and reconcile one controlled cash deposit before exercising expense,
  withdrawal, transfer, finance reversal, notification, and remaining physical launch gates.

### 2026-08-21 - Reverse the first controlled production vendor stock return

- Status: physical Owner vendor-return reversal and authoritative completion **PASS**.
- Device evidence: the operator reported `vendor stock return reversed` after selecting the latest
  Posted Vendor return, entering the controlled dated reason, and submitting Reverse once. No
  credential, business value, vendor, bill reference, or event/journal identifier was requested or collected.
- Accepted behavior: the original vendor-return record was preserved and marked reversed while an
  atomic compensating original-lot inventory/payable journal, audit event, and idempotency result committed.
- Data/security impact: one intentional controlled production vendor-return reversal was posted; no
  source record deletion, cash mutation, role change, or tenant-access policy change occurred.
- Next: refresh Products/stock, the vendor bill and return history, cash, and today's report; verify
  restored stock and vendor due, preserved reversed status/reason, unchanged cash, and consistent totals.

### 2026-08-21 - Verify first vendor return stock, due, history, and report

- Status: physical post-vendor-return reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `vendor return stock due history and report correct` after checking Owner product/stock, vendor bill
  and return history, cash, and today's report. No business value, vendor, bill reference, identifier,
  or credential was requested or collected.
- Accepted behavior: the return reduced original FIFO-lot stock by one and reduced vendor due by its
  authoritative value, remained visible as Posted with its reason, left cash unchanged, and updated
  bounded stock/payable report projections consistently.
- Data/security impact: read-only verification after the intentional controlled return; no additional
  production mutation or permission expansion was performed.
- Next: reverse the controlled vendor return with a dated reason, then verify restored stock, restored
  vendor due, preserved reversed-event history, unchanged cash, and adjusted report totals.

### 2026-08-21 - Post the first controlled production vendor stock return

- Status: physical Owner vendor-return submission and authoritative completion **PASS**.
- Device evidence: the operator reported `vendor stock return posted` after selecting quantity one,
  entering the controlled dated reason, and submitting Post return once. No credential, business
  value, vendor, bill reference, or record identifier was requested or collected.
- Accepted behavior: the Owner-only flow accepted an eligible returnable purchase line and reported
  success only after the atomic vendor return, original FIFO-lot inventory decrement, accounts-
  payable reduction, inventory/payable journal, audit, and idempotency result committed.
- Data/security impact: one intentional controlled production vendor stock return was posted; no cash
  mutation, role change, or tenant-access policy change occurred.
- Next: refresh Products/stock, the vendor bill and return history, and today's report; verify stock
  decreased by one, bill/vendor due decreased by return value, the return is Posted, and report totals agree.

### 2026-08-21 - Verify first vendor-payment reversal due, cash, history, and report

- Status: physical post-reversal reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly
  `vendor payment reversal due cash and report correct` after checking the Owner vendor ledger,
  payment/reversal history, cash activity, and today's report. No business value, vendor, bill
  reference, identifier, or credential was requested or collected.
- Accepted behavior: the compensating reversal restored bill/vendor due and cash, preserved the
  original payment with reversed status and reason, and updated bounded report projections
  consistently without deleting financial history.
- Data/security impact: read-only verification after the intentional controlled reversal; no
  additional production mutation or permission expansion was performed.
- Next: as Owner, post one controlled vendor stock return of quantity one from the purchase bill,
  then verify reduced stock, reduced vendor due, return history, and today's report.

### 2026-08-21 - Reverse the first controlled production vendor payment

- Status: physical Owner vendor-payment reversal and authoritative completion **PASS**.
- Device evidence: the operator reported `vendor payment reversed` after selecting the latest Posted
  payment, entering the controlled dated reason, and submitting Reverse once. No credential, business
  value, vendor, bill reference, or event/journal identifier was requested or collected.
- Accepted behavior: the original payment record was preserved and marked reversed while an atomic
  compensating accounts-payable/cash journal, audit event, and idempotency result committed.
- Data/security impact: one intentional controlled production payment reversal was posted; no source
  record deletion, role change, or tenant-access policy change occurred.
- Next: refresh the vendor ledger, cash activity, and today's report; verify restored bill/vendor due,
  restored cash, preserved reversed status/reason, and consistent adjusted report totals.

### 2026-08-21 - Verify first vendor payment due, cash, history, and report

- Status: physical post-payment reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly `vendor due cash payment and report correct` after
  checking the Owner vendor ledger, payment history, cash activity, and today's report. No business
  value, vendor, bill reference, identifier, or credential was requested or collected.
- Accepted behavior: the payment allocation reduced the bill/vendor due, the cash credit reduced the
  cash balance, the immutable payment event is visible as posted, and the bounded report projections
  reconcile with the payment.
- Data/security impact: read-only verification after the intentional controlled payment; no additional
  production mutation or permission expansion was performed.
- Next: reverse the controlled vendor payment with a dated reason, then verify restored vendor due,
  restored cash, preserved reversed-event history, and adjusted report totals.

### 2026-08-21 - Post the first controlled production vendor payment

- Status: physical Owner vendor-payment submission and authoritative completion **PASS**.
- Device evidence: the operator reported `vendor payment posted` after allocating a valid amount to
  the outstanding bill, selecting Cash, and submitting once. No credential, business value, vendor,
  bill reference, or record identifier was requested or collected.
- Accepted behavior: the Owner-only flow accepted a due-bounded allocation and reported success only
  after the atomic vendor payment, allocation, accounts-payable debit, cash credit, audit, and
  idempotency result committed.
- Data/security impact: one intentional controlled production vendor payment was posted; no role or
  tenant-access policy was changed.
- Next: refresh the vendor ledger, cash activity, and today's report; verify reduced vendor/bill due,
  reduced cash, a posted payment-history event, and consistent report totals.

### 2026-08-21 - Verify first return stock, cash, state, and report

- Status: physical post-return reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly `return stock cash and report correct` after checking
  Owner product/stock, cash activity, Returns, and today's report. No business value, identifier, or
  credential was requested or collected.
- Accepted behavior: the sellable return restored the sold quantity to its original FIFO lot, reduced
  cash by the refund, exhausted the controlled sale line's returnable quantity, and adjusted the
  bounded return, net-sales, cash, stock, FIFO-cost, and gross-profit projections consistently.
- Data/security impact: read-only verification after the intentional controlled return; no additional
  production mutation or permission expansion was performed.
- Next: as Owner, allocate and post one controlled cash payment against the outstanding vendor bill,
  then verify reduced vendor due, reduced cash, payment history, and today's report.

### 2026-08-21 - Post the first controlled sellable production return

- Status: physical Owner return submission and authoritative completion **PASS**.
- Device evidence: the operator reported `return posted` after selecting quantity one from the first
  controlled sale, Sellable disposition, and Cash refund. No credential, business value, or record
  identifier was requested or collected.
- Accepted behavior: the Owner-only return flow accepted an eligible sold item and reported success
  only after the atomic return, cash refund, exact original FIFO-lot stock restoration, balanced
  reversal journals, audit, and idempotency result committed.
- Data/security impact: one intentional controlled production return/refund/restoration was posted;
  the return did not expand Salesman permissions or expose cross-tenant data.
- Verification source: operator-reported authoritative success followed by physical confirmation that
  stock, cash, return state, and today's report are correct. No direct production value query was run.
- Next: post one controlled payment against the outstanding vendor bill.

### 2026-08-21 - Verify first sale stock, cash, dashboard, and report

- Status: physical post-sale reconciliation and reporting **PASS**.
- Device evidence: the operator reported exactly `sale stock cash and report correct` after checking
  Owner product/stock, cash/bank transaction history, dashboard, and today's report. No business
  value, record identifier, or credential was requested or collected.
- Accepted behavior: the sale's FIFO stock decrement and cash settlement are visible downstream;
  today's dashboard sales and bounded report reconcile with sales, FIFO cost, gross profit, stock,
  cash/bank, and vendor-due projections. The first product-to-report production chain is complete.
- Data/security impact: read-only verification; no additional production mutation was performed.
- Next: as Owner, post one sellable return of quantity one from the controlled sale using a cash
  refund, then verify restored stock, reduced cash, return status, and today's report.

### 2026-08-21 - Post the first controlled production Salesman sale

- Status: physical Salesman checkout and authoritative FIFO sale completion **PASS**.
- Device evidence: the operator reported `sale posted` after using the existing restricted Salesman
  account for one fully settled cash sale. No credential or business value was requested/collected.
- Accepted behavior: a Salesman can load verified in-stock catalog data, use configured pricing,
  fully settle the sale, and receive success only after the atomic sale, FIFO allocation, inventory
  decrement, settlement, balanced revenue/COGS/inventory/cash journals, audit, and retry result commit.
- Data/security impact: one intentional controlled production sale was posted. Salesman discount,
  price-override, and credit capabilities remain prohibited; no due was created for the full-payment sale.
- Next: post one controlled sellable return against this sale.

### 2026-08-21 - Verify FIFO stock and vendor due after purchase

- Status: physical post-purchase stock and vendor-due reconciliation **PASS**.
- Device evidence: the operator reported exactly `stock and vendor due correct` after refreshing the
  Owner product/stock and vendor views. No business value, record identifier, or credential was
  requested or collected.
- Accepted behavior: the authoritative purchase result is visible through both downstream read
  models: received inventory is available for sale and the unpaid purchase is reflected as vendor
  due. This completes the first production purchase-to-stock/payable acceptance gate.
- Data/security impact: verification was read-only; no additional production mutation was performed.
- Next: verify the resulting stock, cash balance, dashboard, and report read-back.

### 2026-08-21 - Post the first controlled production purchase

- Status: physical purchase submission and authoritative backend completion **PASS**.
- Device evidence: the operator reported exactly `Purchase posted with authoritative totals` after
  retrying the controlled unpaid purchase in the signed production app. No credential, invoice
  reference, product value, quantity, unit cost, or total was requested or collected.
- Accepted behavior: the purchase action is physically reachable after the rc9 keyboard/dialog fix;
  production accepts the business date after migration `20260821103000`; and the atomic RPC returns
  only after its purchase bill/receipt, FIFO lot/inventory movement, payable journal, vendor due,
  idempotency result, and audit effects commit together.
- Payment impact: the controlled receipt was unpaid, so it establishes vendor due and inventory but
  intentionally creates no immediate cash/bank payment mutation.
- Verification source: operator-reported server-authoritative success message followed by physical
  confirmation that stock and vendor due are correct. No direct production row/value query was run.
- Next: perform one controlled sale from the Salesman account.

### 2026-08-21 - Provision an initial accounting period for every new shop

- Status: root-cause diagnosis, forward migration, regression coverage, static SQL, fresh-Postgres
  CI, exact merge, protected hosted deployment, and physical purchase retry **PASS**.
- Device evidence: the operator reported the exact safe purchase conflict message after attempting
  the first controlled purchase. No PIN, Login ID, invoice value, token, or business amount was
  requested or collected.
- Root cause: `public.create_shop` called the system-account provisioner but did not create an
  accounting period. `post_purchase_receipt` checks for an open period before it inserts any request,
  invoice/bill, receipt, FIFO lot, inventory movement, journal, payment, or audit row. The failed
  attempt is therefore atomic and did not post the purchase.
- Changed: `supabase/migrations/20260821103000_initial_accounting_period_provisioning.sql`,
  `supabase/tests/database/shop_provisioning.test.sql`, `docs/business-policies.md`,
  `docs/purchase-receipt.md`, and `PROJECT_STATUS.md`.
- Behavior/security: only a shop with no period history receives one initial open period, starting
  seven Nepal calendar days before provisioning. The helper locks the shop, is idempotent, remains
  private from `anon`/`authenticated`, and never changes/reopens a configured open or closed period.
- Verification: pinned `pglast 8.2` parsed the changed migration/test and then all 50 SQL migration/
  pgTAP files. A PowerShell top-level pgTAP count found exactly 33 tests for `select plan(33)`.
  `git diff --check` passed. Full local database execution was not run because Docker is not
  installed. PR #56 run `32452378934` then passed pinned Edge verification, zero-state migration
  replay, deterministic seed reset, database lint, all pgTAP suites, and backend integration/
  concurrency tests in 2m1s on exact implementation commit
  `f077a181678a77af2642d733859225042134eaa7`. Final PR head `380b4ac5b90bcd5408258894f10d5cf90afee122`
  passed run `32452592593` in 2m8s; PR #56 merged as exact main
  `1040316632a54765f3e40203f4a2d78d76612d25`, whose push run `32452802516` also passed in 2m8s.
  Protected deployment `32452999186` passed in 2m42s, including zero-state replay, dry-run, migration
  apply, linked lint/history, unchanged Function deployment, and redacted production probes.
- Data/security impact: migration `20260821103000` is now applied to production and creates one open
  period only for each shop with no period history. It does not alter configured periods. No invoice,
  purchase, stock, vendor due, financial balance, journal, user, PIN, or account was created/changed
  by the deployment beyond that intended period-control row; no paid service or app-store action ran.
- Next: verify the resulting FIFO stock and vendor due in the app.

### 2026-08-20 - Generate and independently pin protected rc9

- Status: exact-main verification, protected signing/upload, independent artifact inspection,
  checksum pinning, and guarded installer `VerifyOnly` **PASS**; physical installation and purchase
  retry pending.
- Source/run: GitHub Actions workflow `android-release.yml` run `32395027694` used exact `main`
  `43ec93100967ff8eb3876734fac7e48f6dc75231`. `verify-android` passed in 6m52s and the protected
  `production-release` job passed in 5m54s. No app-store publication step exists or ran.
- Artifact: `GDAD-BAGS-0.2.0-rc9-10-release.apk`, 57,444,389 bytes, SHA-256
  `99719A389E83FB81CA792DA3832147CB1CD962C867E78DDF7213EF0E8FC3F1CC`; workflow artifact
  `9416759332` is retained through `2026-09-03T17:12:37Z`.
- Identity pass: `apksigner verify --verbose --print-certs` passes v2 with one signer and the pinned
  production certificate; `aapt dump badging` passes package `com.gdad.bags`, rc9/code 10,
  minimum/target SDK 31/36, and launcher `com.gdad.bags.MainActivity`.
- Binding/safety pass: archive inspection finds production project `skfxfbssfeetquteubcn`, not
  development project `zniqkuwktvincjndcgpu`, and no `sb_secret_`, preview-auth, pepper, bootstrap,
  or login-diagnostic markers. `service_role` appears only in `MutationOutbox.SENSITIVE_FIELDS`, a
  denylist that prevents sensitive payload persistence; it is not a credential.
- Changed after verification: pinned rc9 identity in `tools/install-release-candidate.ps1`, refreshed
  `docs/release-candidate-handoff.md`, and copied the ignored APK/sidecar to the repository root for
  controlled local installation.
- Installer pass: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
  .\tools\install-release-candidate.ps1 -InstallMode VerifyOnly` returned the pinned checksum,
  signer, package, rc9/code 10, `Installed=false`, and no device serial at
  `2026-08-20T23:03:17.7819568+05:45`.
- Data/security impact: no backend, account, business-data, paid-service, or app-store change.
- Next: deploy the initial accounting-period correction, then use the installed/pinned rc9 candidate
  to retry the controlled unpaid purchase once with the keyboard open/dismissed.

### 2026-08-16 - Fix the purchase keyboard and confirmation layout

- Status: implementation, version bump, local gates, and exact-head GitHub Android CI **PASS**;
  merge, signed rc9 artifact, checksum pinning, and physical-device retest pending.
- Device evidence: on physical rc8, the operator reported that the keyboard remains over the long
  purchase form and prevents scrolling far enough to press **Post purchase once**. No purchase was
  submitted, so product stock, vendor due, cash/bank balances, and journals remain unchanged.
- Changed: `app/src/main/AndroidManifest.xml`, `BusinessDateField.kt`,
  `PurchaseManagementScreen.kt`, `PurchaseManagementScreenTest.kt`, Android version/release artifact
  configuration, `README.md`, `docs/release-build.md`, and `PROJECT_STATUS.md`.
- Behavior: the purchase form now scrolls independently inside an IME-aware bounded dialog; the
  action footer remains separate from the form and includes an explicit **Hide keyboard** action.
  Every editable purchase amount/date field supports IME Done dismissal, and the activity requests
  resize behavior. Confirmation still uses the existing validated, duplicate-safe submission path.
- Verification pass: bundled JDK/SDK Gradle
  `:app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:assembleDebug` completed successfully;
  a subsequent compile-only rerun also passed after formatting.
- Release/static pass: `:app:verifyReleaseAuthSafety`, `:app:verifyReleaseAccessibilitySafety`, and
  `:app:verifyReleasePerformanceSafety` passed. `:app:lintDebug --no-daemon --max-workers=1` passed
  outside the sandbox after its first cache-file access denial, with zero errors and the same 15
  existing warnings.
- Focused-test limitation: `PurchaseManagementScreenTest` compiles, including the new keyboard/action
  assertion. Local execution first failed fetching Robolectric's API-35 runtime due denied socket
  access; offline execution found the cached runtime but all four class tests failed before their
  assertions with the environment's native-runtime/`DeviceConfig` mismatch.
- CI pass: PR #55 run `32393392860` completed `verify-android` successfully in 6m47s on exact head
  `97ed28e9888e314a5b2a20af8086c2bca36277aa`, providing the authoritative runtime result for the
  focused test and complete Android release gate. The production-release job correctly skipped on
  the pull-request event.
- Data/security impact: no schema, backend, credential, or production-data change. Version advances
  to rc9/code 10; the existing rc8 installer remains checksum-pinned until a verified rc9 artifact
  is generated and pinned.
- Next: approve and merge exact PR #55 head, generate and verify the protected signed rc9 artifact,
  pin its checksum/installer identity, upgrade the phone, and retry the controlled unpaid purchase.

### 2026-08-16 - Confirm the first production Salesman transaction, login, and role navigation

- Status: rc8 Owner-to-Salesman creation, aggregate production acceptance, Salesman login, and
  physical role-navigation restriction **PASS**.
- Device evidence: the operator reported `created` after using a new Salesman Login ID and a private
  server-accepted PIN. No credential value was sent to or requested by the agent.
- Production evidence: protected aggregate-only run `31938915526` passed on exact main
  `9d4585bda7106e196e5e4d7f5ee9cd138f21feb1`. Counts are `auth_users=3`, `profiles=3`,
  `credentials=3`, `salesman_requests_total=1`, `salesman_requests_complete=1`,
  `salesman_requests_failed=0`, `salesman_requests_reserved=0`, `salesman_audits=1`,
  `salesman_memberships=1`, and `active_salesman_memberships=1`.
- Authority continuity: `active_owner_memberships=1` and `eligible_salesman_authority_pairs=1`.
- Login evidence: the operator reported successful physical Salesman login after the complete
  aggregate provisioning snapshot. This is manual device evidence; no credential was collected.
- Navigation evidence: from the authenticated Salesman session, the operator reported `admin
  options hidden`. Account and shop administration are therefore absent from the Salesman-visible
  navigation on the physical rc8 device.
- Catalog evidence: the operator opened Sales/New Sale and reported `empty product list`. This is
  the expected zero-state before Owner product and stock setup, and no sale was submitted.
- Owner product evidence: the operator subsequently reported `Product created` after following the
  controlled Owner-only product setup. This is manual device evidence; no credential was collected.
- Owner vendor evidence: the operator then reported `Vendor created` after the controlled Owner-only
  vendor setup. The purchase was intentionally not posted during this step.
- Data/security: the device workflow intentionally created one test product, one test vendor, and
  their protected audit evidence. Agent-side verification queried only prior numeric account
  aggregates and did not read or log a Login ID, PIN, token, verifier, hash, or request body.
- Next: as Owner, post one unpaid controlled purchase receipt to establish the first FIFO stock lot
  without changing cash or bank balances.

### 2026-08-16 - Classify account-provisioning reservation failures safely

- Status: implementation, local/CI verification, merge, production deployment/reconciliation,
  protected signing, and artifact pinning **PASS**; device delivery/installation and retry pending.
- Device evidence: exact rc7 HTTP 403 after successful Owner login proves rc7 subject verification
  passed and the request reached the Edge Function; no PIN, Login ID, token, or account field was
  requested or logged.
- Root cause: `manage-users` returned HTTP 403 for every exception while `stage == "reserve"`.
  PostgreSQL deliberately uses SQLSTATE `23505` for an unavailable Login ID/Auth subject, `22023`
  for invalid or mismatched provisioning input, and `42501` for a genuine authority denial.
- Changed: safe SQLSTATE classification in the Edge core/handler, reservation-failure compensation
  control flow, Android creation-conflict copy/test, rc8/9 version/release scripts, and handoff docs.
- Verification: Edge format/lint/type-check and all 30 Edge unit tests pass. The complete Android
  release gate passes in 5m51s with 186 tests, zero failures/errors/skips, zero lint errors/15
  existing warnings, all authentication/accessibility/performance/artifact safety tasks, and debug
  assembly. The first focused sandboxed run failed only because Robolectric could not download a
  Maven artifact; the identical approved dependency-access run passed without a source change.
  `git diff --check` passes after all source/documentation/version updates.
- CI/production: PR #46 passed Android in 6m34s and fresh-database CI in 1m56s; exact head
  `b0626355b89f9c834204e5bda9a2bf846d1bcb5d` merged as main
  `45a2a852fed6266ce526cf128fef896019d3ef84`. Deployment run `31937292138` passed in 2m19s.
  Aggregate-only run `31937448494` reports one eligible Salesman authority pair and zero Salesman
  requests/memberships/audits. Protected release run `31937446942` passed exact-main verification
  in 6m20s and signing/upload in 5m57s.
- Artifact: 57,427,997-byte APK SHA-256
  `CCDB61CAEB52392EC6499D32935496B1C4E65ADC1B7DEB2F239DE33D588817B0`; sidecar, approved signer,
  v2 signature, package `com.gdad.bags`, rc8/9, SDK 31/36, launcher, production ref present, and
  development ref absent all pass. The checksum-pinned installer `VerifyOnly` passes against the
  root handoff copy at `2026-08-16T14:51:36.3128460+05:45`.
- Next: deliver/install rc8, then retry Salesman creation with a unique Login ID.

### 2026-08-16 - Deliver the exact rc7 APK to Redmi 15

- Status: MTP delivery and phone-to-PC checksum verification **PASS**; manual installation pending.
- Delivery: Windows exposes Redmi 15 through WPD/MTP but not ADB, so the guarded upgrade installer
  correctly refused automation. The fallback copied the exact root rc7 APK only after confirming no
  same-named destination item existed.
- Verification: the phone Download item read back as 57,427,993 bytes with SHA-256
  `52BF20F3F83D7AD8E39A2B84CD1090DF7D2934E071032B011650A40A927992FC`, matching the pinned
  protected artifact exactly. Temporary read-back data was removed.
- Data/security impact: added one APK file to phone Download; did not overwrite, install, uninstall,
  clear app data, read credentials, or change production.
- Next: tap/install rc7 on the phone, sign the Owner in again, and retry Salesman creation once.

### 2026-08-16 - Bind protected mutations to the displayed account subject

- Status: Implementation, local/CI verification, merge, production diagnosis, protected signing,
  and immutable artifact identity **PASS**; physical rc7 retry pending.
- Device evidence: the Owner signs in with the intended shop, while the first Salesman creation
  attempt receives the safe HTTP 403 authorization message. The operator was told not to retry.
- Root-cause boundary: the database permits only an active non-disabled standard profile with an
  active Owner membership in the target active shop. Android previously refreshed whichever hosted
  session was current without comparing its subject to the authoritative `UserSession` shown in UI.
- Changed: the refresh contract now accepts an expected subject; protected shop/account mutations
  pass their actor subject; the production refresher retrieves and compares the hosted subject before
  send. Any mismatch becomes a safe 401 and no mutation request is transmitted. Aggregate-only SQL
  now includes Salesman authority/membership/request/audit counts. Release source/tooling is rc7/8.
- Data/security impact: no backend schema, Function, production account, or business-data mutation.
  The failed phone request produced no reservation; no credential value was collected.
- Verification: focused `RemoteCallExecutorTest` and `ProductionAccountManagementRepositoryTest`
  pass. The full release gate completed in 15m06s with 185 tests, zero failures/errors/skips, lint
  zero errors/15 existing warnings, all auth/accessibility/performance/artifact safety checks, and
  debug assembly. The first focused invocation stopped before compilation because Java was not set;
  the configured sandbox run then reached a Windows cache-JAR access denial, and the identical
  approved bundled-toolchain run passed without a source change.
- CI/production: PR #43 Android and fresh-database gates pass in 6m46s/1m59s. Exact head
  `ee12380aea6d2546ab4817e3fbd9263366ad2bc4` merged as
  `d3c82ff323ab808c77a3a25c4abc0a7b1d54d6be`. Aggregate-only run `31932921133` reports
  `eligible_salesman_authority_pairs=1` and all Salesman request/membership/audit counts zero.
  Protected run `31933084512` passes verification/signing in 5m39s/4m26s.
- Artifact: 57,427,993-byte APK SHA-256
  `52BF20F3F83D7AD8E39A2B84CD1090DF7D2934E071032B011650A40A927992FC`; sidecar, approved signer,
  v2 signature, package `com.gdad.bags`, rc7/8, SDK 31/36, launcher, production ref present, and
  development ref absent all pass. The pinned installer `VerifyOnly` passes against the root handoff
  copy at `2026-08-16T13:08:22.0470310+05:45`.
- Next: install rc7, sign the Owner in again, and retry the Salesman creation once.

### 2026-08-16 - Confirm the first production Owner transaction

- Status: rc6 installation and first Owner creation **PASS**; Owner login/tenant scope pending.
- Device evidence: the operator confirmed the new version is installed and the Owner creation UI
  completed successfully. Windows detects the Redmi through WPD/MTP, but not ADB, so the installed
  package cannot yet be inspected remotely.
- Production evidence: protected run `31930685338` succeeded on exact main `0dc3bbd1`. Aggregate
  counts are `auth_users=2`, `profiles=2`, `credentials=2`, `active_super_admins=1`,
  `owner_requests_total=1`, `owner_requests_complete=1`, `owner_requests_failed=0`,
  `owner_requests_reserved=0`, `owner_audits=1`, `owner_memberships=1`, and
  `active_owner_memberships=1`.
- Tenant observation: `active_shops=2` and `eligible_owner_authority_pairs=2`; confirm the Owner sees
  only the intended selected shop before entering business data.
- Privacy/security: the workflow queried only numeric counts and made no production mutation. It did
  not select or log identities, names, PINs, credentials, hashes, tokens, or business values.
- Next: sign out of the Super Admin account, sign in as the Owner, confirm the correct shop and
  Owner-only navigation, then create and test one Salesman.

### 2026-08-15 - Merge, sign, and pin rc6 Owner-session correction

- Status: Source, local/CI verification, merge, protected signing, and immutable artifact identity
  **PASS**; physical Owner retest pending.
- Local verification: focused remote-executor/account repository tests passed. The complete release
  command passed 184 tests with zero failures/errors/skips, lint with zero errors and 15 existing
  warnings, release auth/accessibility/performance checks, unsigned artifact safety, and debug
  assembly. `git diff --check` passed before the implementation commit.
- CI/signing: PR #40 `verify-android` passed in 5m21s. Exact head `3b97341b64f4b1d0eabd35694c418b55fa6fca12`
  merged as `012817f5d631fc65dc041c6789100feadcb9d77d`. Protected run `31870004171` repeated the full gate
  in 6m59s and signed/uploaded rc6 after production-environment review.
- Artifact verification: both independent downloads and the protected sidecar match SHA-256
  `55FC4438B3A7083B9157ED7FE1C95D74E66B8C001B170718DE16C9A718CD1D9A`. `apksigner` passes v2
  with the approved certificate; `aapt` reports `com.gdad.bags`, `0.2.0-rc6`/7, SDK 31/36, and the
  expected launcher. Binary inspection finds production ref `skfxfbssfeetquteubcn` and no
  development ref. The APK is 57,427,997 bytes. The pinned installer `VerifyOnly` pass completed at
  `2026-08-15T12:45:30.2803932+05:45` against the root handoff copy.
- Next: run the pinned installer in `VerifyOnly`, install/upgrade rc6 on the phone, sign out and sign
  in again, create the first Owner once, then run aggregate reconciliation before Owner login.

### 2026-08-12 - Reconcile rc4 Owner attempt and harden form submission

- Status: Production reconciliation/root cause **PASS**; rc5 verification in progress.
- Production evidence: both immediate and delayed aggregate checks remained at `auth_users=1`,
  `profiles=1`, `credentials=1`, Owner requests/audits/memberships `=0`, and no failed/reserved
  provisioning row. The newest provisioning row was the earlier bootstrap, not the phone action.
  No identity, name, PIN, token, or session was queried or logged.
- Root cause boundary: a valid hosted request always reserves an Owner provisioning row before Auth
  creation; its total absence proves the phone submission stopped locally. The dialog accepted a
  broader Login ID shape than the repository/backend and closed before reporting asynchronous
  success or failure. rc5 aligns the form contract and makes pending/failure/success visible.
- Focused verification: `AccountManagementScreenTest` and
  `ProductionAccountManagementRepositoryTest` pass. The strengthened form test fills a valid display
  name/PIN/shop, proves a spaced Login ID alone disables Create, then proves `owner.name` enables it.
- Full verification: the complete release-quality command passed 180 tests with zero failures,
  errors, or skips; lint with zero errors and 15 existing warnings; auth/accessibility/performance
  source safety; unsigned release artifact safety; and debug assembly.
- CI/artifact verification: PR #37 and exact merged main passed `verify-android`; protected run
  `31617468852` passed reviewer approval and `production-release`. Its artifact archive matched
  SHA-256 `0B1573EBE853FE4EE5A65AD00FB56E435A2199784385B73AE5FBA7B830E1EFDD`
  and contained exactly the signed APK/checksum. The 57,427,997-byte APK matches SHA-256
  `A565DDCA7B2A09D785E4C9B046A569C55007011AE8D2E4BFF9F13AB3649E2398`, the approved signer,
  package `com.gdad.bags`, version `0.2.0-rc5`/6, SDK 31/36, and only the production Supabase origin.
- Next: verify/sign/install rc5, submit a compliant Login ID, and reconcile the complete Owner state.

### 2026-08-12 - Diagnose missing first Owner and implement account JSON transport

- Status: Production diagnosis and local rc4 source/build verification **PASS**; protected signing
  and device acceptance in progress.
- Production evidence: `auth_users=1`, `profiles=1`, `credentials=1`, enabled Super Admins `=1`,
  memberships/active Owner memberships `=0`, Owner provisioning requests/audits `=0`, and no
  failed/reserved provisioning request. No account identifier, name, PIN, token, or session was
  queried or logged.
- Root cause: both protected account handlers require JSON media type before parsing or reserving a
  request. Android's typed account invocations omitted that explicit header even though the rc3 PIN
  login invocation already carried it. Both account creation and administration now share an
  explicit JSON header; a regression assertion pins that contract.
- Local verification: focused `ProductionAccountManagementRepositoryTest` passed. The complete
  release-quality command passed 179 tests with zero failures/errors/skips, lint with zero errors
  and 15 existing warnings, all three source safety tasks, unsigned release artifact safety, and
  debug assembly. The first focused attempt was interrupted by a two-minute wrapper timeout; a
  second restricted run reached a Windows access denial on one Gradle cache JAR, and the identical
  bounded run outside that restriction passed without a source change.
- CI/artifact verification: PR #35 and exact merged main both passed `verify-android`; protected run
  `31592190757` then passed reviewer approval and `production-release`. Its only artifact archive
  matched SHA-256 `20F31DF67015DCFF9AE27B48C9FF1FA89800EB4CA9BC87E9101A50065B4F6CAA`
  and contained exactly the signed APK plus checksum. The 57,427,993-byte APK matches SHA-256
  `2BC25CA27EA802B0B4291AF24FEE092D906E8E5A8A79E53BDF3AA5578CF13DA8`, one approved signer,
  package `com.gdad.bags`, version `0.2.0-rc4`/5, SDK 31/36, and only production Supabase origin.
- Next: install the version-code-5 upgrade, repeat Owner creation, and reconcile Auth/profile/
  credential/membership/request/audit state before Owner login.

### 2026-08-12 - Pass rc3 physical production login and initial-shop reconciliation

- Status: Physical production authentication and initial-shop setup **PASS**.
- Authentication evidence: a fresh successful login updated only aggregate credential/source state;
  failed attempts remain zero and neither account nor source is blocked. Because rc2 always failed
  strict request validation while rc3 explicitly sends JSON, this successful physical flow is also
  behavioral evidence that the corrected replacement is installed despite the unavailable ADB
  package inspector.
- Shop evidence: `shops=1`, `active_shops=1`, `shop_creation_requests=1`, completed request/results
  `=1`, shop-create audit events `=1`, financial accounts `=11`, active system-managed accounts
  `=11`, and distinct system purposes `=11`.
- Clean boundary: memberships, products, sales, and journal entries are all zero. No production
  name, slug, identifier, credential, session, or business value was returned or recorded.
- Next: provision the first Owner into the active shop, verify Owner login/tenant scope, then execute
  the product-purchase-stock-sale-return-finance-report-notification acceptance path.

### 2026-08-12 - Fix and sign the Android JSON login transport

- Status: Source/build/artifact **PASS**; phone delivery blocked because its current MTP session is
  read-only in practice even though Windows can enumerate `Internal shared storage/Download`.
- Production contract proof: a random non-account request without an explicit JSON media type
  returned HTTP 400 `INVALID_REQUEST`; the identical body with `application/json` returned HTTP 401
  `INVALID_CREDENTIALS`. This isolates rc2's false credential message to its request header without
  targeting or changing the real Super Admin.
- Verification: focused `ProductionAuthRepositoryTest` passed offline; the protected clean
  production build completed 178 tests with zero failures/errors/skips, lint with zero errors and 15
  existing warnings, and signed `assembleProductionRelease`. `apksigner` verifies one signer and APK
  Signature Scheme v2 with certificate SHA-256
  `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`. `aapt` verifies
  `com.gdad.bags`, `0.2.0-rc3`/4, SDK 31/36; binary inspection finds only the production Supabase
  origin; `tools/install-release-candidate.ps1 -InstallMode VerifyOnly` passes.
- Artifact: `GDAD-BAGS-0.2.0-rc3-4-release.apk`, 57,427,997 bytes, SHA-256
  `780ECA05D898116AB28130A102E73714EC93F4422F59CE7B42E42AF8B67981EA`.
- Device: two bounded Shell/MTP copy methods could read the Redmi and its old rc2 file but no rc3
  item appeared after 120/240 seconds. No existing phone file was overwritten or removed. Unlock the
  phone, select **File transfer / Android Auto**, reconnect USB if needed, copy/install rc3, and
  verify the same already-proven production credentials.

### 2026-08-12 - Verify the operator credentials across the complete production auth path

- Status: Backend credential/session/profile path **PASS**; installed phone package remains
  unverified because the Redmi exposes MTP but no authorized ADB interface.
- Credential path: one corrected masked local run returned HTTP 200 from production `pin-login`,
  HTTP 200 from `/auth/v1/user` with the returned bearer session, and HTTP 200 from authenticated
  `user_profiles`; the JWT/Auth/profile subjects matched and the profile was enabled
  `super_admin`. A preliminary successful login was followed by a local body-bearing-GET helper
  error; that helper defect was corrected before the complete pass and did not indicate a backend
  or credential failure.
- APK path: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File
  .\tools\install-release-candidate.ps1 -ApkPath
  .\GDAD-BAGS-0.2.0-rc2-3-release.apk -InstallMode VerifyOnly` passed package, version, signer,
  checksum, SDK, and launcher checks. Binary inspection confirms the release embeds the production
  URL/ref. `adb devices -l` returned no device, so installed package identity is not observable.
- Security: login ID/PIN and returned sessions were accepted only through masked/in-memory inputs,
  then discarded. The ignored verifier and sanitized result were deleted; no credential or token
  value was logged or committed.
- Conclusion: do not reset or guess the production credentials. Remove any development/debug/rc2
  app from the phone and install the verified rc3 production-signed APK, then use the same accepted
  credentials.

### 2026-08-12 — Diagnose first physical login validation message

- Status: Production account/backend healthy; phone credential submission remains operator-side.
- Observation: the phone reported the generic `Check your user ID and PIN` message. Independent
  aggregate state shows the sole profile is enabled, real-account `failed_attempts=0`, no credential
  lockout, maximum source attempts `=1`, and no source block. Therefore no valid 6–8-digit PIN
  attempt reached the real credential verifier.
- Contract probe: one random non-existent login ID, random diagnostic six-digit PIN, UUID request,
  and UUID device ID was submitted with the stored production publishable key. It returned HTTP 401
  `INVALID_CREDENTIALS`, proving the deployed production Function accepts the exact current Android
  request shape. The diagnostic values/key were cleared and not printed; the real account was not
  targeted or changed.
- Conclusion: use the exact production login ID and 6–8-digit PIN entered during masked bootstrap;
  the PC password and older four-digit test PIN are not valid app credentials. If those production
  values are forgotten, use a reviewed secure recovery path rather than guessing into lockout.

### 2026-08-12 — Verified MTP delivery to Redmi 15

- Status: Signed APK delivery **PASS**; manual Android package installation and device matrix remain.
- Device: Windows exposes `Redmi 15` through Xiaomi WPD/MTP only; USB ADB and local Wireless
  Debugging discovery still return no device/service after rescan and clean daemon restart.
- Delivery: after proving the destination filename was absent, copied
  `GDAD-BAGS-0.2.0-rc2-3-release.apk` to `Internal shared storage/Download`. Windows MTP reports a
  hidden extension/zero metadata size, so the phone item was copied back to an ignored local folder:
  it returned as exactly 57,411,609 bytes with SHA-256
  `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC`.
- Cleanup/security: the local read-back copy was deleted after verification. No existing phone file
  was overwritten, no APK was installed, no app/phone data was changed, and no credential value was
  used or stored. The phone user must tap the verified APK in Download and approve Android's package
  installer; ADB-dependent acceptance/performance evidence remains pending.

### 2026-08-12 — Bound ADB release-install operations

- Status: Complete and locally verified.
- Tool: `tools/install-release-candidate.ps1` now runs ADB discovery and device commands through a
  captured native-process helper with 15-second discovery, 30-second shell, and 180-second install
  bounds. A discovery timeout terminates only bundled-ADB processes and fails with safe remediation
  instead of hanging indefinitely; existing checksum/signer/package/mode protections remain.
- Device diagnosis: Windows currently detects `Redmi 15` only as Xiaomi WPD/MTP
  (`USB\\VID_2717&PID_FF40`), with no Android ADB interface. No driver was changed, APK installed,
  or phone data accessed. The PC password supplied in chat was not stored, repeated, or used.
- Verification: PowerShell parser reports zero errors. `VerifyOnly` passes the exact approved APK,
  signer, package/version, SDK, and launcher. `Fresh` with the current MTP-only phone state exits 1
  safely in 1.40 seconds with `No authorized ADB device is connected`; no installation occurs.
  `git diff --check` remains next before publishing.

### 2026-08-12 — Post-bootstrap signed APK and device-gate recheck

- Status: Signed candidate **PASS**; physical-device execution remains blocked by absent hardware.
- `tools/install-release-candidate.ps1 -InstallMode VerifyOnly` passed at
  `2026-08-12T12:14:09.6263972+05:45`: 57,411,609-byte APK SHA-256
  `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC`, signer certificate
  `C1B015D22B09F79F801B8677CDBC054775322C4A0535064F0AA1DA89160269C9`, package
  `com.gdad.bags`, version `0.2.0-rc2`/3, SDK 31/36, and expected launcher all match.
- Windows connected-device enumeration shows no Android/ADB/MTP phone. A stale ADB diagnostic
  daemon was stopped; a fresh ADB listing again blocked with no device response and was terminated.
  No APK was installed, no phone data was changed, and port 5037 has no listening daemon afterward.
- Recovery-material presence audit found the local ignored production keystore and the expected
  Windows Credential Manager target names for signing passwords/alias, backup age identity, and
  production database password. Values were not read or printed. No owner-designated off-PC secret
  store or recipient is available, so no recovery material was exported by assumption.

### 2026-08-12 — Production Super Admin bootstrap and reconciliation

- Status: **PASS.** The masked helper completed at `2026-08-12T06:12:39Z` with safe correlation ID
  `cff05526-f395-4b2f-b9c6-f04c6bd51e49`; it reported successful account creation and PIN-login
  subject equality. The sanitized local result was deleted after verification.
- Independent database counts through the authenticated Supabase management query endpoint:
  `auth_users=1`, `profiles=1`, `enabled_super_admins=1`, `credentials=1`, `shops=0`,
  `account_audit_events=1`, and `business_audit_events=0`.
- Hosted secret-name verification: `GDAD_BOOTSTRAP_TOKEN` is absent and exactly the intended three
  long-lived GDAD secret names are present. No secret value was printed or persisted.
- Read-only `tools/Test-ProductionHealth.ps1`: Auth health `200`; anonymous REST boundary `401`;
  malformed `pin-login` and `manage-users` `400`; unauthenticated protected `manage-accounts` `401`;
  all correlation/no-store contracts pass.
- Security: the account login ID/display name, PIN, JWT/session, publishable key, CLI token, database
  password, bootstrap token, and long-lived secret values were not included in logs, status, or Git.

### 2026-08-12 — Accessible explicit production confirmation

- Status: Local verification passed; controlled runtime follows immediately.
- Tool: `tools/bootstrap-production-superadmin.ps1` continues to require the complete interactive
  phrase `CREATE PRODUCTION`, but now trims surrounding whitespace and accepts capitalization
  differences. An incorrect phrase still clears the in-memory PIN and restarts secure entry without
  contacting Supabase; the confirmation value is cleared on retry, success path, and final cleanup.
- Data/security impact: none yet. This does not bypass confirmation or put it on a command line.
  The rejected automatic-launcher command never executed and prior production reconciliation remains
  zero users/profiles/Super Admins/shops with no `GDAD_BOOTSTRAP_TOKEN`.
- Verification: PowerShell parser reports zero errors; static assertions prove trimming,
  case-insensitive full-phrase comparison, confirmation-value clearing, and absence of any automatic
  bypass; the known-development child process still fails closed at preflight with sanitized output.
  `git diff --check` passes and the temporary self-test result was deleted.

### 2026-08-11 — Preserve explicit production-creation confirmation

- Status: Safety review complete; confirmation bypass was rejected and removed before execution.
- Tool: `tools/bootstrap-production-superadmin.ps1` still requires the interactive exact
  `CREATE PRODUCTION` confirmation. A proposed trusted-launcher switch was locally parsed and
  fail-closed tested, but the production launcher was rejected because it would supply confirmation
  automatically; the switch was then removed rather than circumventing the safeguard.
- Data/security impact: none. The rejected launcher command never ran, no production request was
  made, and no PIN/session/bootstrap token was logged or persisted. The prior reconciliation remains
  zero production users/profiles/Super Admins/shops and no `GDAD_BOOTSTRAP_TOKEN`.
- Verification: after removal, PowerShell parsing reports zero errors, the source contains no
  automatic-confirmation switch, the exact interactive phrase gate remains, and `git diff --check`
  passes.

### 2026-08-11 — Production bootstrap confirmation recovery

- Status: Local verification passed; controlled production runtime follows.
- Tool: `tools/bootstrap-production-superadmin.ps1` now keeps an incorrect case-sensitive
  `CREATE PRODUCTION` confirmation inside the same secure retry loop, clears the rejected PIN, and
  reports that nothing changed instead of terminating the bootstrap.
- Data/security impact: none. The reported prior result was `operator-input` /
  `Cancelled before hosted change.` with no correlation ID, so no Supabase request or hosted change
  occurred and no PIN/session/bootstrap token was logged.
- Verification: PowerShell parsing reports zero errors; static assertions prove the confirmation
  stage, PIN clearing, safe retry text/loop, and removal of the legacy terminating cancellation;
  the known-development child-process self-test still exits 1 at preflight with only sanitized
  output. `git diff --check` passes and the temporary self-test result was deleted. The first
  controlled retry was then externally interrupted (`0xC000013A`) before producing a result; final
  read-only reconciliation proved production still has zero Auth users/profiles/Super Admins/shops
  and no `GDAD_BOOTSTRAP_TOKEN`.

### 2026-08-11 — Production bootstrap input recovery and safe diagnosis

- Status: Helper runtime path verified; production bootstrap remains pending operator confirmation.
- Tool: `tools/bootstrap-production-superadmin.ps1` now keeps local login/display/PIN validation
  inside the masked prompt, clears rejected PIN values, and lets the operator retry without
  terminating or making a hosted request.
- Hosted reconciliation before the change: production remained empty (`auth.users=0`,
  `user_profiles=0`, enabled Super Admins `=0`, shops `=0`) after two unsuccessful local attempts;
  `GDAD_BOOTSTRAP_TOKEN` was absent and only the expected three long-lived GDAD secret names plus
  platform-managed names remained. No partial identity or business data exists.
- Security: no login ID, display name, PIN, session, publishable-key value, access-token value, or
  database-password value was printed, persisted, or committed.
- Verification: PowerShell parser reported zero errors; retry-loop, PIN clearing, exact confirmation,
  and sanitized-result schema assertions passed. Static inspection confirms the optional result has
  only status, stage, curated message, safe correlation ID, and timestamp—not identity or secret
  fields. The diagnostic runtime wrote `failed` / `Cancelled before hosted change.` with no
  correlation ID, proving no request passed the explicit operator confirmation. Final management
  reconciliation then confirmed `auth.users=0`, profiles `=0`, enabled Super Admins `=0`, shops
  `=0`, and no `GDAD_BOOTSTRAP_TOKEN`. A child-process self-test against the explicitly forbidden
  development ref exited 1, emitted exactly the five sanitized fields with `stage=preflight`, and
  made no request; PowerShell parsing and `git diff --check` pass.

### 2026-08-11 — Uninterrupted encrypted-backup restore, RPO, and RTO

- Status: **Functional PASS / RPO PASS / RTO PASS.** Fresh protected run `31428910379` captured
  production at `2026-08-11T08:57:06Z`; ciphertext is 87,666 bytes at SHA-256
  `05af91c225e59126db068675aa31cf2591daef56069759bfec2202132c7d494d`, and all outer/inner
  manifest checks passed.
- Target: disposable `vdyjynwmvjfkpdbbaqpd`, Seoul, Postgres 17.6.1.155; production remained
  read-only/healthy. Timed start was `2026-08-11T14:58:35.5651536+05:45`; app verification was
  `2026-08-11T16:08:27.1392027+05:45`.
- Database: preventive ACL normalization; unedited logical restore; 28 migrations at
  `20260802163000`; CLI lint clean; 21 pgTAP plans/723 assertions; 74-table/zero-row exact recovery;
  all RLS, tenant, FIFO/stock, ledger, idempotency, audit, and multi-session concurrency gates pass.
- Application: three new drill-only secrets and all three Functions; bootstrap HTTP 201; PIN login
  HTTP 200; matching JWT subject; exactly one enabled `super_admin` profile through RLS; one-time
  bootstrap secret removed.
- Timing: RPO `00:16:29.5651536` (<=24h) and RTO `01:09:51.5740491` (<=4h). Target, credential,
  and plaintext were destroyed; ciphertext is intact. Development and production are healthy and
  only development remains linked.
- Core verification commands (credentials/URLs redacted): `gh run view 31428910379`,
  `supabase db lint --db-url <target> --schema public,private --level warning --fail-on error`,
  PostgreSQL 17.10 `psql --variable ON_ERROR_STOP=1` over every sorted
  `supabase/tests/database/*.test.sql`, the SQL workflow in
  `supabase/integration-tests/backend_concurrency.sh`, `supabase functions deploy pin-login
  manage-users manage-accounts --project-ref <target> --use-api --jobs 3`, and bodyless authenticated
  `GET /rest/v1/user_profiles?...` after bootstrap/PIN-login HTTP checks.

### 2026-08-10 — Isolated encrypted-backup functional restore

- Status: **Functional PASS / RPO PASS / wall-clock RTO not proven.** Exact source ciphertext was
  87,656 bytes at SHA-256
  `4c6794ef177069d04c7398b65c6ef205fa737938b4c232927a56788698766fe4`; all authenticated inner
  hashes/sizes passed before restore.
- Target: disposable `tjhqfjjxzgumnnzgzwnq`, Seoul, Postgres 17.6; production
  `skfxfbssfeetquteubcn` stayed read-only/healthy. Development `zniqkuwktvincjndcgpu` was paused for
  the slot, then resumed through `COMING_UP`/`RESTORING` to `ACTIVE_HEALTHY`.
- Database: official single-transaction roles/schema/data restore exit 0; history restore exit 0;
  CLI 2.111.0 lint exit 0; 21 pgTAP plans, 723 assertions, zero `not ok`; 28 migrations at
  `20260802163000_initial_shop_provisioning`; 74 cataloged tables and zero source rows matched.
- Reconciliation: zero RLS-disabled exposed tables, cross-shop references, negative/overflow stock,
  stock-projection mismatches, unbalanced journals, and duplicate business idempotency keys; two
  enabled audit mutation guards. FIFO/vendor/expense races each allowed exactly one success, exact
  retry replayed identically, denied actors stayed denied, and final integrated invariants passed.
- Application: three fresh drill-only Edge secrets, three ACTIVE Functions with expected JWT modes,
  bootstrap HTTP 201, PIN-login HTTP 200, and one authenticated RLS `super_admin` profile row. The
  bootstrap secret, random credentials/session, disposable project, local DB credential, and
  plaintext SQL were removed; encrypted evidence remains.
- Timing: backup capture `2026-08-03T07:35:03Z`; drill start
  `2026-08-03T14:17:37.2730014+05:45`; app verified `2026-08-10T13:26:56.336+05:45`; teardown/resume
  complete `2026-08-10T13:34:57.394+05:45`. RPO `00:57:34.273` passes 24 hours. Wall-clock RTO
  `6d 23:09:19.063` fails the four-hour objective due the operator interruption, so repeat timed.

### 2026-08-03 — Verify current health and post-migration encrypted backup

- Status: Current-main production health and encrypted logical export are green; the separate
  isolated-target restore drill and off-PC identity recovery copy remain required.
- Scheduled health run `30768291677` passed against main commit
  `ff858a8deadd65aa9c47851fded8ff7d417918ff`.
- Scheduled backup run `30767417848` was approved at the protected `production` environment and
  passed. Artifact `gdad-production-daily-20260803T073503Z` contains exactly ciphertext, checksum,
  and catalog; artifact ID `8848237493` is 88,804 bytes and expires 2026-08-11 UTC.
- The 87,656-byte ciphertext SHA-256 is
  `4c6794ef177069d04c7398b65c6ef205fa737938b4c232927a56788698766fe4`.
  Checksum and catalog agree. Official age v1.3.1 Windows asset digest matched its GitHub release
  digest before use. The private identity was read from Windows Credential Manager and sent only to
  the decryptor's stdin; it was never printed or written to a file.
- Decryption, strict archive allow-list, production project/current source metadata, and all five
  inner SQL hash/size checks pass at migration head
  `20260802163000_initial_shop_provisioning`. The first attempt stopped before decryption because
  Windows PowerShell 5.1 lacks `ProcessStartInfo.ArgumentList`; the compatible quoted-argument retry
  passed. Both paths left no plaintext archive/directory or identity file.
- ADB reports no attached device. No production database, Auth user, shop, business row, secret,
  billing plan, backup add-on, or app installation changed.

### 2026-08-02 — Deploy shop provisioning and verify signed rc2

- Status: PR #20 is merged, the migration is verified on hosted development and production, and
  signed rc2 is independently verified. External recovery/bootstrap/device gates remain.
- Corrected database run `30758156914` passed fresh migration replay, deterministic seed, lint, all
  723 pgTAP assertions, and backend integration/concurrency. Android run `30758156973` passed the
  full release gate in 6m38s. The exact green head
  `2744a4250eeda2da8ae7f6cd628d0d95e9569d6d` merged as
  `42b39a68e41533e37118f1d99331b0b67a9450f9`.
- Linked development dry-run selected only `20260802163000_initial_shop_provisioning.sql`; push
  applied it successfully. `migration list --linked` shows all 28 local/remote versions aligned and
  `db lint --linked --level error` reports no schema errors.
- No development user, shop, business data, Edge secret, or Function changed; only the reviewed
  schema/RPC migration was applied.
- Protected production run `30758557549` was dispatched from exact merged commit
  `42b39a68e41533e37118f1d99331b0b67a9450f9` to registered project
  `skfxfbssfeetquteubcn`. Its `production` environment deployment was approved after GitHub
  confirmed the exact commit/ref/project gate. The run passed in 2m29s: clean backend replay,
  production link/dry-run/push, three protected Edge-secret installs, three Function deployments,
  linked history/lint, redacted live probes, and traceable summary all succeeded. No user, shop, or
  business row was created. Protected Android run `30758725027` was then dispatched from the same
  main commit with `production_release=true`. Its clean verification job passed in 6m11s, and the
  exact `42b39a6` environment-protected signing job was approved without publishing to a store.
  Both jobs passed (6m11s verification; 5m31s production release), and artifact upload succeeded.
- The downloaded APK is 57,411,609 bytes. Its published and independently computed SHA-256 both
  equal `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC`.
  `apksigner` verifies one signer and APK Signature Scheme v2 with certificate SHA-256
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`.
  `aapt` verifies package `com.gdad.bags`, version `0.2.0-rc2`/3, SDK 31/36, label `GDAD BAGS`,
  launcher activity, and all five launcher-icon densities. The ignored installable copy is
  `GDAD-BAGS-0.2.0-rc2-3-release.apk`; the installer/handoff now pin this identity.
- `tools/install-release-candidate.ps1` verify-only mode passes against the default local rc2 copy
  and returns only the approved path, hashes, package/version, and non-install result. No device or
  app data changed.

### 2026-08-02 — Correct initial-shop private-ledger pgTAP expectation

- Status: Product security behavior is correct; the single CI-only expectation is corrected and
  awaiting a fresh database workflow run on PR #20.
- Database run `30757887695` passed Edge verification, fresh migration replay, deterministic seed,
  and database lint. Of 723 pgTAP assertions, only shop-provisioning assertion 25 failed: PostgreSQL
  returned `42501: permission denied for table shop_creation_requests`, proving authenticated
  clients cannot read the private idempotency ledger, while the test expected a schema-level error.
- The test now asserts the observed table-level denial. No migration, grant, runtime behavior,
  hosted project, user, shop, business data, or secret changed.

### 2026-08-02 — Implement protected initial-shop provisioning locally

- Status: Local Android and SQL static gates pass; fresh-Postgres runtime CI and hosted deployment
  remain before the slice is complete.
- `pglast 8.2` parsed migration `20260802163000_initial_shop_provisioning.sql` and its pgTAP suite;
  static assertion count matches `plan(25)`. `git diff --check` passes.
- Production/test Kotlin compilation passed. The focused account suite passed 13/13 across
  repository, ViewModel, and Compose. The complete Android suite then passed 176 tests in 47 suites
  with zero failures. All four release safety tasks plus unsigned release/debug assembly passed in
  2m59s. Separate full lint completed with zero errors and 10 warnings.
- The refreshed debug APK is 76,843,015 bytes with SHA-256
  `D5267D10B2D432DCFC01173BF39686634BDF4CCFCE28D27E52308B2E5AC24703`; `aapt` verifies package
  `com.gdad.bags`, version `0.2.0-rc2`/3, SDK 31/36, and label `GDAD BAGS`. The scanned unsigned
  release APK is 57,411,609 bytes with SHA-256
  `2DC868BC225E7AED7B4231963087C735851DA74B6899ED58B3CE8401644EB5CF`.
- The first combined full gate exceeded its shell wrapper while child Gradle JVMs continued; the
  exact gates were rerun separately to completion. An orphaned lint child likewise completed after
  the wrapper timeout and produced the zero-error report; only JVMs started by these runs were
  stopped after completion. No hosted project, user, shop, or business data changed.

### 2026-08-02 — Add fail-closed signed-candidate installation handoff

- Status: Complete for repository-side Task 7.3 preparation and the documented portion of Task
  7.4; device-dependent acceptance remains pending because ADB reports no attached device.
- `tools/install-release-candidate.ps1` verify-only mode passed against the local production APK and
  returned only its approved path, SHA-256, certificate SHA-256, package, version, install mode, and
  empty device fields. The exact APK hash is
  `DBDD6D9B079E41DFD03E332E6D163228A75D102DE99025017D2F4B6331339C82`; signer/package/version/SDK/
  activity checks all passed. The first invocation exposed that the Windows `apksigner.bat`
  requires `JAVA_HOME`; the validator now selects the existing bundled JDK before signature checks.
- Bundled ADB returns an empty authorized-device list. Fresh mode therefore remains fail-closed and
  no install, uninstall, launch, device-data change, production login, or backend mutation occurred.
  `git diff --check` passes. After PR #18 merged the handoff as
  `addfe9e2222938daa7a5a261e3cc441355b0f9f6`, current-main production health run `30755813345`
  passed Auth, Data API, and all three Edge security-boundary probes in 10 seconds using no
  privileged credential and performing no mutation.

### 2026-08-02 — Build and independently verify signed production APK

- Status: Complete for Tasks 7.1 and 7.2; the artifact is ready for controlled physical-device
  installation but is not published.
- Protected workflow run `30754590770` completed from exact main commit
  `36aaf7772d795b5a8da8d207df27a405989802d7`: the clean Android safety/test/lint/debug job passed
  in 5m41s, then the production release job materialized the protected signing inputs, built and
  verified the signed APK, generated its checksum, and uploaded artifact
  `GDAD-BAGS-0.2.0-rc1-2-release` in 5m42s. The artifact is retained through
  `2026-08-16T15:44:33Z`; no GitHub Release or app-store submission was made.
- Independent download verification matched the sidecar and APK SHA-256
  `DBDD6D9B079E41DFD03E332E6D163228A75D102DE99025017D2F4B6331339C82`. `apksigner` verified APK
  Signature Scheme v2 and the expected certificate SHA-256
  `C1:B0:15:D2:2B:09:F7:9F:80:1B:86:77:CD:BC:05:47:75:32:2C:4A:05:35:06:4F:0A:A1:DA:89:16:02:69:C9`.
  `aapt` verified `com.gdad.bags`, versionCode 2, versionName `0.2.0-rc1`, min/target SDK 31/36,
  label `GDAD BAGS`, launchable `com.gdad.bags.MainActivity`, and six launcher-density metadata
  entries. The approved production project ref is embedded and the development ref is absent.
- The repository's exact artifact scanner found none of `PreviewAuthRepository`, `sb_secret_`, PIN
  or rate-limit pepper names, bootstrap/diagnostic tokens, or an age private identity. An initial
  broader exploratory string search found the generic library validation term `service_role` in a
  bundled SDK; it is not a credential and is intentionally outside the exact scanner rule. The APK
  was copied only after the exact checks passed. Temporary verification material was removed.

### 2026-08-02 — Deploy production backend and correct final authentication probe

- Status: Complete for the scoped production backend deployment. The schema, three Edge secret
  names, and three Functions are deployed and the protected workflow is fully green.
- Main commit `e14cbadfeb119f36fb621758e6b6ce5ae14af7dc` passed fresh Database run
  `30736929985` and Android run `30736929993`. Owner-approved production runs remained constrained
  to Free project `skfxfbssfeetquteubcn`; no billing plan, add-on, user, bootstrap token, fixture, or
  business row was created.
- Run `30737663500` failed its first shape guard because the initial secret transfer was malformed;
  every mutation step skipped. Re-uploaded local inputs passed the shape gate. Run `30737954801`
  then passed the full fresh backend replay and stopped at link because the Supabase CLI Credential
  Manager token had been decoded as UTF-16 instead of its actual UTF-8 `sbp_...` representation;
  migration/deployment steps again skipped.
- Corrected-token run `30738097255` passed input/Edge verification, all migrations/tests from zero,
  production link/dry-run, all 27 production migrations, three Edge secret installs, three Function
  deployments, linked lint, and exact migration history. It stopped only at the final malformed
  `manage-accounts` probe after all hosted deployment checks passed.
- Direct redacted production probes show `pin-login`, `manage-users`, and `manage-accounts` each
  return `400 INVALID_REQUEST`, no-store, and a valid correlation ID for malformed `{}`. This is the
  handler's documented pre-authentication contract. The corrected protected probe supplies a valid
  request shape without Authorization and requires `401 UNAUTHORIZED`, no-store, and correlation
  evidence before any RPC can run. The exact valid-shaped production probe now passes all four
  expectations. PR #11 merged the corrected workflow into `main` as
  `7b422631ef8b1c23007764e73c926cf82cd021a6`. Protected run `30739060872` passed the environment
  guard, Edge tests, full zero-state backend replay, project link, migration preview/application,
  all three Edge secret installs, all three Function deployments, linked lint/history, corrected
  redacted probes, and traceable summary. No user, business data, paid plan, backup, log drain, or
  paid alert was created.

### 2026-08-02 — Protect GitHub production environment and register deployment secrets

- Status: Complete for the Task 6.6 protected-environment prerequisite; deployment has not run.
- Explicit owner approval authorized creation of the environment and transfer of all seven values.
  GitHub environment `production` requires reviewer `sanjubaba21`, has self-review prevention off
  so the sole repository owner can approve, and uses a custom deployment branch policy containing
  only `main`.
- `gh secret list --env production` returns exactly seven expected names:
  `SUPABASE_ACCESS_TOKEN`, `SUPABASE_PRODUCTION_PROJECT_REF`,
  `SUPABASE_PRODUCTION_DB_PASSWORD`, `SUPABASE_PRODUCTION_PUBLISHABLE_KEY`,
  `GDAD_PIN_PEPPER_V1`, `GDAD_RATE_LIMIT_PEPPER_V1`, and `GDAD_DUMMY_PIN_HASH_V1`.
  GitHub does not return secret values; transfer used process standard input and cleared local
  variables afterward.
- A read-only environment API verification confirms the required-reviewer rule and `main` branch
  policy. No workflow was dispatched, and production remains without migrations, functions,
  identities, bootstrap token, or application data.

### 2026-08-02 — Create isolated production Supabase project and secure initial inputs

- Status: Partial for Task 6.6. Project and local protected inputs are complete; GitHub environment,
  deployment, recoverability, operations, and bootstrap remain.
- Explicit owner approval authorized creation of the potentially billable project and secure local
  credential storage. Supabase created `GDAD Bags Production` with ref
  `skfxfbssfeetquteubcn` in `ap-northeast-2`; the creation response and subsequent project listing
  report `ACTIVE_HEALTHY`. Development remains separately linked as `zniqkuwktvincjndcgpu`.
- A 48-character cryptographically generated database password is stored in Windows Credential
  Manager target `GDAD_SUPABASE_PRODUCTION_DB_skfxfbssfeetquteubcn`. The project publishable key,
  two independently generated 48-byte base64 peppers, and a fresh Argon2id dummy verifier are in
  four additional project-scoped targets. Only target names and successful storage booleans were
  printed; no value was written to the repository or terminal output.
- A non-secret local probe proves the dummy verifier generator emits the required
  `$argon2id$v=19$m=19456,t=2,p=1$...` contract using the pinned Edge helper. The production
  verifier was generated with the canonical dummy subject and a unique random salt.

### 2026-08-02 — Current-head pull-request gates green

- Status: Complete for repository-automated release verification on source commit
  `b44e98fbc9e079318c79096e323493e1b5bbd26a`.
- GitHub Database tests run `30733686244` (run 109), job `91458333358`, completed successfully.
  Checkout, pinned Supabase CLI, Deno 2, all Edge checks, all migrations from zero,
  deterministic seed verification, database-function lint, every pgTAP suite, and backend
  integration/concurrency tests passed.
- GitHub Android release gate run `30733686252` (run 5), job `91458353947`, completed
  successfully. Checkout, Java 17, Gradle setup, and the complete Android release gate passed.
  The production-release job was correctly skipped because this pull-request run has no protected
  production approval, signing material, or production backend inputs.
- These runs close the Linux Gradle-wrapper and account-test role-cleanup regressions without
  weakening a release, grant, RLS, migration, application, or production-deployment gate.

### 2026-08-02 — Repair Linux Android CI wrapper execution

- Status: Complete. Replacement Android run `30733686252` passed the complete release gate.
- Draft PR #1 accepted commit `fae904f` and started Database tests run `30733108775` plus Android
  release gate run `30733108805`. The Android job failed before Gradle with exit 126 and the exact
  log `/home/runner/...sh: line 1: ./gradlew: Permission denied`; checkout/Java/Gradle setup passed.
- Root cause: the tracked Windows-origin wrapper had mode `100644`, so Ubuntu could not execute it.
  The index now records `gradlew` as executable (`100755`). No task, test, lint, release-safety, or
  application behavior was changed or bypassed.

### 2026-08-02 — Correct account pgTAP execution-role cleanup

- Status: Complete. Replacement Database tests run `30733686244` passed the complete fresh-stack
  verification pipeline.
- Database run `30733270253` passed checkout, pinned CLI/Deno, Edge checks, all 27 migrations from
  zero, deterministic seed replay, and linked-schema-equivalent local lint. Eighteen of 20 pgTAP
  files passed; account administration/provisioning stopped only when their test connection retained
  `service_role` after a successful protected RPC and then directly selected `public.user_profiles`.
- The two tests now `reset role` immediately after materializing the protected RPC result, before
  test-administrator-only public/private inspection. Production grants remain unchanged: Edge
  `service_role` callers still use security-definer RPCs and receive no direct table access.
- Replacement run `30733408305` proves account administration now passes and 696 assertions run.
  It exposed the same retained-role pattern once more after Salesman finalization, at the direct
  `shop_memberships` check. A complete audit of every service-role block found no other direct table
  inspection; the finalization block now resets immediately after its RPC result as well.

### 2026-08-02 — Protected production Supabase deployment gate

- Status: Complete for repository automation and static verification. Production project creation,
  deployment, recoverability, alerts, and account bootstrap remain external and were not claimed.
- Supabase CLI 2.111.0 read-only account inspection returned one organization and exactly one
  project: healthy linked development ref `zniqkuwktvincjndcgpu` in `ap-northeast-2`. A separate
  production project does not exist. ADB reports no attached Android device; the dashboard browser
  is signed out.
- The new protected manual workflow rejects the development ref and mismatched/missing protected
  inputs, replays Edge/database/seed/pgTAP/concurrency checks from zero, previews then applies
  migrations, installs only the three required long-lived Edge secrets, deploys all three functions,
  runs linked lint/history, and performs credential-redacted probes. It deliberately does not create
  a Super Admin or install a bootstrap token.
- `docs/production-deployment.md` now defines project/plan ownership, protected GitHub secrets,
  reproducible deployment, Auth/operations checks, restore prerequisite, one-time bootstrap,
  release binding, and forward-fix/rollback policy. `tools/bootstrap-production-superadmin.ps1`
  refuses development or mismatched URLs, verifies the production project is healthy, masks the PIN,
  keeps its random bootstrap token in process memory, proves login subject equality, and removes the
  hosted token in `finally`.
- PowerShell AST parsing and `git diff --check` pass. Three isolated no-network negative invocations
  exit 1 before input/network/secret operations for the development ref, a production-ref/URL
  mismatch, and a malformed publishable key; their safe output contains no supplied key value.
- Secret-boundary review replaced direct success/session JSON parsing with generic safe failures and
  clears bootstrap/login response and session objects during `finally`, preventing malformed hosted
  response fragments or tokens from reaching terminal error output.
- PyYAML structure parsing and bashlex parsing pass the workflow and all 11 embedded Bash scripts.
  PowerShell AST, three negative helper paths, `git diff --check`, and the current Edge format/lint/
  type-check plus all 29 tests pass. The workflow itself remains deliberately unexecuted because no
  production project/secrets exist; it will replay the full fresh-Postgres gate before deployment.

### 2026-08-01 — Task 7.1 production release foundation

- Status: Complete for repository implementation and automated verification. Production secrets,
  keystore, protected-environment approval, signed artifact, and device evidence do not exist yet.
- Android version is now `0.2.0-rc1`/2. Normal release verification remains unsigned; an explicit
  production build requires `GDAD_PRODUCTION_RELEASE=true`, all four signing values, an existing
  keystore, a client-safe publishable key, and a Supabase URL that is not the development project.
- A local production build script and protected manual GitHub Actions job consume secrets only from
  ignored properties/environment or protected environment secrets. Keystore formats are ignored;
  action dependencies are pinned to immutable official SHAs; no store/GitHub release is published.
- R8/resource shrinking remains deliberately disabled until the signed physical-device matrix can
  prove serialization, Room, and Supabase behavior. Workflow YAML and PowerShell parse cleanly;
  Gradle rejects missing approval, missing signing, and the development backend exactly as intended.
  The full unsigned gate passed 173 tests across 47 suites with zero failures/errors/skips, all four
  release safety gates passed, and lint returned zero errors with the existing 16-warning baseline.
- The refreshed debug APK is 76,810,247 bytes with SHA-256
  `B9E15DAE446507EED40A8B1389080E045858841CE3833B297F167592A097B77F`; `aapt` confirms package
  `com.gdad.bags`, versionCode 2, versionName `0.2.0-rc1`, SDK 31/36, label, and launcher. It remains
  debug-signed with APK Signature Scheme v2 and certificate SHA-256
  `A6273FF9478AED588D42B853768AE3B6DCE22ADA28A3D2C3F4D3C7FE3CB79A3C`.
- The unsigned release APK is 57,378,845 bytes with SHA-256
  `766BB1A0CEB60BF9DD0BD592CA93069340C0E16C4C74926321813E73EDCC44E8`. It is deliberately not a
  launch artifact; the production job cannot run until external production inputs are configured.

### 2026-08-01 — Hosted synchronization and complete Android release gate

- Status: Complete for repository automation and hosted development deployment; production signing,
  production backup/restore, and physical-device gates remain pending.
- Supabase CLI 2.111.0 applied migration `20260729170000_bounded_report_detail_windows.sql` after a
  one-file dry run. All 27 local/remote migration versions now match; linked lint reports no errors
  in `extensions`, `private`, or `public`.
- `pin-login`, `manage-users`, and `manage-accounts` are ACTIVE at hosted versions 28, 23, and 7.
  Redacted malformed requests to the public handlers returned `400 INVALID_REQUEST`, a valid UUID
  `x-gdad-correlation-id`, and `Cache-Control: no-store`. The protected account handler remains
  gateway-JWT protected and is reserved for the authenticated device smoke test.
- Pinned Deno 2.9.0 `deno task check` passed formatting for 17 files, lint for 13 files, all three
  handler type-checks, and 29 tests with zero failures.
- The complete Android gate passed in 7m37s: release auth/accessibility/performance/artifact checks,
  173 tests across 47 suites with zero failures/errors/skips, lint with zero errors and 16 existing
  warnings, unsigned release assembly, and debug APK assembly.
- Refreshed `GDAD-BAGS-test.apk`: 76,810,239 bytes, SHA-256
  `6BFB54C22F54AC3C135D15A95CDE78326E0CB20AFECE3D1BFCE38F0065D776F7`. `aapt` confirms package
  `com.gdad.bags`, version `0.1.0`/1, min/target SDK 31/36, label `GDAD BAGS`, and launcher activity.
  `apksigner` verifies APK Signature Scheme v2 with the Android Debug certificate
  `A6273FF9478AED588D42B853768AE3B6DCE22ADA28A3D2C3F4D3C7FE3CB79A3C`.
- Unsigned release APK: 57,378,833 bytes, SHA-256
  `1FB53ACADB4885C2A5F22365E54AF3096ED9303AFC7A9A4D3A2A9BB950E15D93`.
- Supabase backup inspection reports region `ap-northeast-2`, WAL-G enabled, PITR disabled, and no
  available physical backups. This is acceptable only for development; production launch remains
  blocked on a paid/recoverable production policy and measured isolated restore drill.
- A final CLI 2.111.0 function listing parsed the renamed `[local_smtp]` configuration without the
  previous deprecation warning and reconfirmed versions 28/23/7 ACTIVE.
- `supabase/README.md` now documents the actual pinned CLI 2.111.0 version.
- Host-access `adb devices -l` returned no attached device, so installation, TalkBack/200% traversal,
  startup/memory/frame capture, and end-to-end physical workflow evidence remain external gates.
- Final integrity rerun: pinned Deno 2.9.0 again passed formatting/lint/type-check and all 29 tests;
  `pnpm install --frozen-lockfile --offline` was already up to date; `git diff --check` passed; only
  B6.5 and B6.7 remain unchecked in the canonical checklist.

### 2026-08-01 — Complete local `manage-users` correlation migration

- Status: Complete locally and deployed to hosted development; public HTTP evidence passes.
- `manage-users` now returns a no-store correlation header on every response and emits only the
  shared allow-listed failure event, including compensation failures. The shared regression covers
  all three public Edge handler names.
- The first pinned Deno 2.9.0 gate stopped only on canonical formatting for the expanded shared
  regression. After `deno fmt`, `deno task check` passed formatting for 17 files, lint for 13 files,
  all three handler type-checks, and all 29 tests with zero failures.
- Repository Supabase tooling is now pinned at CLI 2.111.0 instead of 2.101.0. The pnpm lockfile
  supply-chain policy check passed for all nine checked entries; linked migration inspection and
  deployment now work with the updated CLI. Hosted development migration
  `20260729170000_bounded_report_detail_windows.sql` was applied successfully after its dry run
  identified it as the only pending migration. Linked history now matches all 27 migrations and
  hosted lint reports no errors in `extensions`, `private`, or `public`.
- Local Supabase email-test configuration now uses the CLI 2.111.0 `local_smtp` section name,
  removing the deprecated `inbucket` warning without changing hosted Auth or email behavior.
- All three Edge handlers were deployed successfully to hosted development. Redacted hosted probes
  confirmed the public malformed-request correlation contract without exposing the publishable key.
  No secret, alert, backup, or billing setting changed in this increment.

### 2026-07-29 — Task 6.5 privacy-safe Edge correlation foundation

- Status: Partial; local code/tests/runbook pass, while `manage-users`, hosted alerts/backups, and a
  measured isolated restore drill remain pending.
- Local Deno 2.4.0 formatting and lint pass all checked files; all three Edge handlers type-check;
  the complete cached Edge suite passes 29 tests with zero failures, including four new correlation/
  header/log-redaction tests.
- `git diff --check` passes. `pin-login` and `manage-accounts` now return a no-store correlation
  header for every response and log only the allow-listed structured failure event.
- Final integrity scan passes again after the runbook/status changes: Deno formatting checks 17
  files, lint checks 13 files, remote selects/limits remain exactly 32/32, Room selects/limits remain
  exactly 19/19, and `git diff --check` is clean.
- Official Supabase plan/backup/log/metrics documentation was rechecked on 2026-07-29. The runbook
  treats Free as development-only unless daily encrypted off-site logical exports are operated, and
  blocks production until backup/alert configuration and a real restore drill are evidenced.

### 2026-07-29 — Task 6.3 automated accessibility and Nepal UX gate

- Status: Partial; all repository-automated acceptance checks pass, while physical TalkBack and
  OEM 200% display/font traversal remain pending because no ADB device is attached.
- `verifyReleaseAuthSafety verifyReleaseAccessibilitySafety verifyReleaseArtifactSafety
  testDebugUnitTest lint --no-daemon --max-workers=1
  -Pkotlin.compiler.execution.strategy=in-process` passed in 20m55s: 167 tests/45 suites,
  zero failures/errors/skips; all source/release-APK safety checks passed; release assembly
  succeeded; lint reported zero errors/16 warnings. The warning set is unchanged and contains
  only tool/dependency/version, launcher-shape, target-API, and unused-resource findings.
- The corrected focused Nepal clock/money/shared-state/login/report/checkout suite passed in
  2m22s, including 200% font-scale scroll-reachability tests for login, checkout, and reports.
- `build-apk.ps1` passed in 2m and rebuilt the debug-signed installable
  `GDAD-BAGS-test.apk` at 76,761,087 bytes, SHA-256
  `69B554D4D25B0DB28C4C6398D102656AFFEB8295E46E97B1C216F493E528C3B9`. The scanned unsigned
  release APK is 57,362,449 bytes, SHA-256
  `613654F46B240C020717E9CE53C8A60ACB074D6FFD96D44579CC4368261B9137`.

### 2026-07-29 — Task 6.2 security hardening review

- Status: Complete.
- `verifyReleaseAuthSafety testDebugUnitTest --tests
  com.gdad.bags.data.remote.SupabaseConfigTest processReleaseMainManifest --no-daemon
  --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` passed four configuration
  tests and the source/merged-manifest policy checks.
- `verifyReleaseAuthSafety verifyReleaseArtifactSafety testDebugUnitTest lint --no-daemon
  --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` passed in 7m33s after
  Robolectric's test runtime was made available: 160 tests/44 suites, zero failures/errors/
  skips; release source and APK scans passed; lint reported zero errors/16 warnings.
- `deno task check` passed formatting, lint, type-checking, and all 25 Edge tests. The exact
  Deno 2.9.0 CI gate then passed `deno ci`, repeated all 25 tests, and reported no known
  high/critical dependency vulnerability.
- Initial workflow run `30438410834` passed Deno 2.9 frozen install/check/audit, migration
  replay, deterministic seed, and database lint. pgTAP then exposed a test-discovery bug:
  dynamic-SQL inspection called `pg_get_functiondef` on `public.array_agg`. The query now
  restricts inspection to ordinary functions/procedures.
- Corrected workflow run `30438664328` passed in 2m1s: exact Deno install/check/audit, all 26
  migrations from zero, deterministic seed reset, database lint, 697 assertions across 20
  pgTAP files, and the real backend concurrency/invariant harness.
- Linked migration history matches all 26 migrations through `20260728110000`; linked
  `supabase db lint --level warning` reported no schema errors. The new pgTAP suite parsed
  successfully with PostgreSQL grammar tooling; local execution is unavailable without Docker.
- Hardened `pin-login`, `manage-users`, and `manage-accounts` deployments succeeded. Hosted
  HTTP probes returned `405` for GET, `400 INVALID_REQUEST` for malformed login, `401` for an
  invalid project key, `401 INVALID_CREDENTIALS` for an unknown user, and `401 UNAUTHORIZED`
  for missing user/account-administration JWTs. Handler responses carried the new CSP.
- `build-apk.ps1` passed and rebuilt the debug-signed installable `GDAD-BAGS-test.apk` at
  76,744,703 bytes, SHA-256
  `EE970B7B1E43A7FAFC72F6024E6D58F4FB144056D27687687C5D26264BDF1403`. The scanned unsigned
  release APK is 57,395,217 bytes, SHA-256
  `9CC43A7F5AEA8A09BA708EFA787004D8D7D48064ED064C7FA0E6E12994770B28`.

### 2026-07-29 — Task 6.1 automated coverage audit

- Status: Complete.
- Focused exact-money/FIFO/auth/sale/stock/vendor verification passed 24 tests with zero
  failures. A 38-test Room/Compose selection passed all pre-existing suites and isolated three
  new app-shell harness assumptions; the corrected four-test shell suite then passed.
- Initial `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1 -Pkotlin.compiler.execution.strategy=in-process` passed in 9m45s: 156
  tests/43 suites, zero failures/errors/skips; release authentication safety and release APK
  assembly passed; lint reported zero errors/17 warnings.
- A post-gate checked-arithmetic sweep removed all residual production `sumOf` and
  floating-point money conversions. The same full gate passed current HEAD in 7m56s with
  156 tests/43 suites, zero failures/errors, and zero lint errors/17 warnings.
- The final unsigned release APK is 57,377,221 bytes with SHA-256
  `96C0F51B4EF5DA5EC42D718EE78252166EA07AAD3EB2E1DDC7602325C6609178`.
- `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\build-apk.ps1` passed all
  cached tests and 49 packaging tasks in 1m23s. The final debug-signed installable
  `GDAD-BAGS-test.apk` is 76,994,780 bytes with SHA-256
  `B9A7F7B3C134D31EC157B0C7DFA79B54DB29449D9F1BA8DF79D765FD71F6FEAE`.

### 2026-07-29 — Task 5.10 notifications and Phase 5 exit gate

- Status: Complete.
- Room v6 notification data/schema compilation passed in 6m21s. The first UI compile found
  one missing Material `Badge` import; after correction the UI/navigation compile passed in
  1m52s and obsolete final placeholder helpers were removed.
- Focused notification/cache/navigation verification passed 27 tests/six suites in 2m7s,
  zero failures/errors.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 13m52s: 133 tests/40 suites, zero failures/errors; Room schema
  v6 exported; release APK 57,360,837 bytes; lint zero errors/17 warnings.
- Post-gate migration audit aligned Room entity defaults with `MIGRATION_5_6`. The first
  annotation patch targeted memberships; schema inspection caught it before commit. The
  corrected schema leaves memberships unchanged and declares notification `shop_id DEFAULT
  ''` and `record_type DEFAULT 'system'`. Five migration plus four notification repository
  tests pass.
- Final `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 7m16s: 133 tests/40 suites, zero failures/errors; release APK
  57,360,837 bytes; lint zero errors/17 warnings.
- Final `build-apk.ps1` passed 49 tasks in 27s and produced the 76,992,596-byte debug-signed
  installable `GDAD-BAGS-test.apk` (SHA-256
  `D2F537785CAEF010F14412188537BE2B1F721ED9DACDB164A5EF0A0BE04BF28C`).

### 2026-07-29 — Task 5.9 trusted dashboard and reporting workflow

- Status: Complete.
- Data/repository compilation passed in 1m14s; dashboard/report UI and navigation compilation
  passed in 48s.
- The first focused run found only nullable `Int?` fixture compilation; the second executed
  13 selected tests with one boxed JUnit `Int`/`Long` expectation mismatch. After correcting
  fixture/assertion types, all 13 report/navigation tests passed in 1m3s.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 8m57s: 122 tests/37 suites, zero failures/errors; release APK
  57,262,533 bytes; lint zero errors/17 warnings.
- `build-apk.ps1` passed 49 tasks in 2m37s and produced the 76,983,096-byte debug-signed
  installable `GDAD-BAGS-test.apk` (SHA-256
  `EC42EE35C3511F175D0B4EE739181CBA8D48B9CEDEB671997DA6915FFAFC8E49`).

### 2026-07-29 — Task 5.8 finance workflow

- Status: Complete.
- `:app:compileDebugKotlin --no-daemon` passed in 1m39s through the known fallback.
- A second compile after repository validation and DI binding passed in 1m2s.
- UI/navigation compilation passed in 1m16s. The first focused run compiled all production
  and test sources and passed six of seven tests; its only failure identified an unconstrained
  finance history list. The list now owns the remaining height and exact money parsing no
  longer uses floating-point arithmetic. After `:app:clean`, the corrected focused suite
  passed all seven tests in 3m14s with zero failures or errors.
- A post-gate wiring audit added a visible retry action backed by the retained logical
  operation UUID. The expanded focused suite passed all eight finance tests in 1m27s.
- Final `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 4m29s: 113 tests/34 suites, zero failures/errors; release
  APK 57,115,077 bytes; lint zero errors/17 warnings.
- Final `build-apk.ps1` passed 49 tasks in 35s and produced the 76,964,836-byte
  debug-signed installable `GDAD-BAGS-test.apk` (SHA-256
  `70A94E790326DD47E21A024CA796B14CC9959B2086C2790FCACD5BCEEFD215D1`).

### 2026-07-29 — Task 5.7 vendor bill, due, payment, and return workflow

- Status: Complete.
- `:app:compileDebugKotlin --no-daemon` passed in 3m20s through the in-process fallback.
- A second compile after adding exact-retry/conflict-refresh ViewModel state passed in
  1m40s (8 tasks; 2 executed and 6 up-to-date).
- UI integration compilation passed in 1m5s. Seven focused repository/ViewModel/Compose
  tests then passed in 1m9s with zero failures or errors.
- The only extra diagnostic is the known restricted Kotlin daemon marker access; no new
  Kotlin warning or source error was introduced.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` completed all outputs: 105 tests/31 suites, zero failures/errors;
  release APK 56,869,317 bytes; lint zero errors/17 warnings; `git diff --check` passed.
- `build-apk.ps1` passed 49 tasks and produced the 76,134,753-byte debug-signed
  installable `GDAD-BAGS-test.apk` (SHA-256
  `E541D06E82D6F1A43197A2CC0A7742ADFE683DE38FC94550D0468EFF2D8A4087`).

### 2026-07-28 — Task 5.6 sale history and return workflow

- Status: Complete.
- `:app:compileDebugKotlin --no-daemon` passed after compiling the sale history,
  return repository, ViewModel, Compose screen, navigation, and DI integration.
- The local Kotlin daemon cannot create its optional marker under the user profile in
  the restricted environment, but Gradle's in-process fallback completes successfully.
- Focused return verification produced 9 tests, zero failures/errors: four repository,
  two ViewModel, and three Robolectric Compose role/receipt tests. The command wrapper
  timed out after result XML was written; all three suites record zero failures.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` completed all outputs: 98 tests/28 suites, zero failures/errors;
  release APK 56,607,173 bytes; lint zero errors/17 warnings; `git diff --check` passed.
- `build-apk.ps1` passed 49 tasks and produced the 75,732,381-byte debug-signed
  installable `GDAD-BAGS-test.apk` (SHA-256
  `3D3CCA6C39A78DCBDABFA056C0DE2089BD2F491A28D2EB3E3790984FE6304B12`).

### 2026-07-28 — Task 5.5 atomic FIFO point of sale

- Status: Complete.
- Salesman uses configured prices, no discount/credit, and full payment. Owner may override,
  discount, and create identified credit with due date; server authorization remains final.
- Checkout is online-only, double-tap guarded, exact-key retryable, and displays only the
  returned total/paid/due/FIFO allocation result before refreshing stock.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 4m13s: 89 tests/25 suites, zero failures; release APK is
  56,426,949 bytes; lint has zero errors/17 warnings; `git diff --check` passed.
- `build-apk.ps1` passed and produced the 75,613,014-byte debug-signed installable APK.
  GitHub fresh-database run `30381148900` passed every backend stage in 1m54s.

### 2026-07-28 — Task 5.4 stock and inventory adjustments

- Status: Complete.
- Owner reads RLS-protected FIFO lots/movements/cost and posts only through the atomic
  adjustment RPC. Salesman uses the Room product projection without cost/history/actions.
- Search, low-stock filter, compatible reasons, positive quantity, source-lot availability,
  new-lot cost, Nepal date, note, exact-key retry, and authoritative outcome are covered.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 7m50s: 82 tests/22 suites, zero failures; release APK is
  56,328,645 bytes; lint has zero errors/17 warnings; `git diff --check` passed.

### 2026-07-28 — Task 5.3 vendor and purchase workflow

- Status: Complete.
- Hosted migration `20260728110000` deployed successfully; linked migration history matches
  and linked lint reports no errors in `extensions`, `private`, or `public`.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 5m55s: 76 tests across 19 suites, zero failures/errors; release
  APK is 56,181,189 bytes; lint has zero errors and 17 pre-existing warnings.
- `git diff --check` passed with only expected LF-to-CRLF worktree notices.
- GitHub Actions database run `30378177697` passed in 2m5s: Edge verification, fresh
  migration replay, deterministic seed, database lint, all pgTAP suites including the
  26 vendor assertions, and backend concurrency integration.
- `build-apk.ps1` passed all cached tests and 49 debug packaging tasks in 2m50s. The
  debug-signed installable `GDAD-BAGS-test.apk` is 75,038,733 bytes.

### 2026-07-28 — Task 5.2 product catalog vertical slice

- Status: Complete.
- Room schema v4 adds `low_stock_threshold` through explicit `MIGRATION_3_4`; product and
  stock snapshots remain user/tenant owned, transactionally replaced, and purged on
  identity change. Search covers name, SKU, and barcode while archived rows remain visible.
- Owner receives FIFO-lot stock value and create/edit/archive controls. Salesman does not
  query or render cost and cannot invoke mutations. Invalid/missing-tenant calls fail before
  networking; backend RLS/RPC remains authoritative.
- Every mutation carries one UUID across manual retry and the durable product outbox.
  PostgREST SQL states are classified before generic HTTP 400 so validation, permanent-code
  duplicate, archive lifecycle conflict, and unauthorized outcomes use actionable fixed text.
- `verifyReleaseAuthSafety testDebugUnitTest assembleRelease lint --no-daemon
  --max-workers=1` passed in 9m50s: 67 tests across 16 suites, zero failures/errors; release
  APK is 55,984,523 bytes; lint has zero errors and 17 pre-existing warnings.
- `git diff --check` passed; only Git's expected LF-to-CRLF worktree notices were emitted.

### 2026-07-28 — Task 5.1 account management vertical slice

- Status: Complete.
- `SupabaseAccountRemoteDataSource` reads only RLS-visible shops/profiles/memberships and
  invokes `manage-users`/`manage-accounts` with typed exact bodies and response checks.
  The repository repeats hierarchy, UUID, PIN, active-shop, and same-shop target checks
  before network calls; backend authorization remains authoritative.
- Room schema v3 adds owner/user-scoped managed-account and shop tables through explicit
  `MIGRATION_2_3`. Refresh replaces the directory transactionally; logout/identity switch
  purges it. PINs, actor PINs, tokens, response bodies, and backend messages are not stored.
- Super Admin sees shops/Owners and may create/manage Owners. Owner sees and manages only
  same-shop Salesmen. Salesman receives a denial state and no create/disable/reset control.
  Create, disable, re-enable, and reset use safe forms/confirmations; retry retains the
  exact request UUID. Success mentions immutable audit completion and refresh revocation.
- `verifyReleaseAuthSafety testDebugUnitTest --no-daemon --offline --max-workers=1`
  passed 56 tests across 13 suites with zero failures/errors. Coverage includes success,
  denial, invalid input, cross-shop, offline/retry, same-key ViewModel retry, role UI,
  cache purge, and v2-to-v3 migration.
- The first release assembly completed and produced a 55,869,835-byte unsigned APK; the
  clean cached rerun passed all 51 tasks. Full lint passed 30 tasks with zero errors and
  the same 17 pre-existing warnings.

### 2026-07-28 — Task 4.7 navigation and shared UI states

- Status: Complete; Phase 4 exit gate passed.
- Stable Navigation Compose/testing `2.9.8` provides serializable `DashboardRoute` and
  `FeatureRoute`. The authenticated graph is keyed by user/role/shop; both navigation
  requests and route rendering enforce `NavigationPolicy` before feature content appears.
- Super Admin, Owner, and Salesman visible destinations exactly match their allowlists.
  No deep link is registered, and the explicit PIN-only external-navigation policy rejects
  null, custom-scheme callback, and web feature URI attempts.
- NavController state save/restore returns to the typed Products route after simulated
  process recreation; back returns to Dashboard. Logout uses a consistent confirmation.
  Shared loading/empty/error states expose semantic labels and tested refresh/retry controls.
- `verifyReleaseAuthSafety testDebugUnitTest --no-daemon --offline --max-workers=1`
  passed 45 tests across 10 suites with zero failures/errors. `assembleRelease` passed
  51 tasks and produced a 55,673,227-byte unsigned APK. Full `lint` passed 30 tasks with
  zero errors and the same 17 pre-existing warnings.
- Navigation and Compose test artifacts were downloaded only into the ignored Gradle
  cache. Initial focused runs exposed one missing import, one incomplete test-runtime
  resolution, and test-harness state reuse; all were corrected before the full green gates.

### 2026-07-28 — Task 4.6 durable mutation outbox

- Status: Complete.
- Room schema v2 adds an indexed owner-scoped outbox with operation, JSON payload,
  stable UUID idempotency key, timestamps, attempt state, next attempt, and safe error kind.
  Explicit 1-to-2 migration SQL is registered; destructive migration remains forbidden.
- WorkManager `2.11.2` uses unique connected-network work and exponential 30-second
  backoff. The processor recovers stale claims, caps retries at five/six hours, stops on
  validation/conflict/authorization, and never stores exception or response messages.
- Product management reuses the durable key in the backend idempotency ledger;
  notification reads use idempotent upsert behavior. Risky financial/inventory/admin
  operations are rejected as online-only. Logout/identity changes purge old work, while
  same-owner read refresh preserves it. Permanent owner-scoped failures publish a safe
  dashboard notice.
- Fresh unit-test XML records 37 tests across eight suites with zero failures/errors.
  This includes file-backed close/reopen persistence, duplicate suppression, same-key
  retry, permanent failure, migration SQL, snapshot preservation, identity isolation,
  and WorkManager constraint/backoff coverage.
- `verifyReleaseAuthSafety assembleRelease --no-daemon --offline --max-workers=1`
  passed 51 tasks and produced a 55,048,640-byte unsigned APK. `lint --no-daemon
  --offline --max-workers=1` passed 30 tasks with zero errors and 17 pre-existing warnings.
- One initial release attempt required downloading WorkManager's official AndroidX
  transitive artifact into the ignored Gradle cache. An overlong in-process compiler
  daemon was stopped before the successful clean cached build; no binary was committed.

### 2026-07-28 — Task 4.5 Room cache and synchronization model

- Status: Complete.
- Added stable Room `2.8.4`, Room Gradle schema export, KSP `2.3.7`, and JVM Room tests.
  The initial schema contains `cache_identity` plus nine minimum offline read tables; all
  business primary keys and queries include owner user and tenant keys.
- `RoomCacheStore` replaces complete snapshots in one transaction, validates every row's
  owner after deletion so mismatch failures prove rollback, and exposes owner-filtered
  Flows. `CacheSynchronizer` retains the last good snapshot on classified remote failure.
- Production authentication activates the authoritative user/shop only after validation
  and purges cache state on logout, identity failure, user/shop change, or missing identity
  marker. Remote logout failure still performs one local cache purge.
- Final `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 29 tests across
  six suites with zero failures.
- Isolated `assembleRelease` passed 51 tasks including release Room generation, auth
  safety, and lint-vital; the unsigned APK is 54,408,036 bytes. Isolated `lint` passed 30
  tasks with zero errors and the same 17 pre-existing warnings.
- Gradle's first KSP download repeatedly stalled. The five exact missing artifacts were
  downloaded only into ignored Gradle cache paths; the 83,459,632-byte KSP engine was
  checked against its official SHA-1 before use. No dependency binary was committed.

### 2026-07-28 — Task 4.4 typed remote client and error mapping

- Status: Complete.
- Added typed PIN-login/profile/membership/shop DTOs with exact hosted names and
  serialization coverage, plus a reusable remote result/error/retry contract.
- Remote execution applies a 15-second bound, preserves caller cancellation, distinguishes
  validation, unauthorized, conflict, offline, timeout, rate limiting, and unknown errors,
  and retries an authenticated unauthorized operation exactly once after Supabase session
  refresh. Diagnostics contain no request, response, exception message, token, or PIN.
- Repositories expose fixed safe messages plus typed `OperationErrorKind`; transport DTOs
  and SDK exceptions remain inside the data layer.
- Final `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 21 tests across
  five suites with zero failures.
- `assembleRelease lint --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed 76 tasks,
  produced a 53,764,594-byte unsigned release APK, and reported zero lint errors with the
  same 17 pre-existing warnings.

### 2026-07-28 — Task 4.3 release authentication safety

- Status: Complete.
- Deleted the production-source preview repository and removed user-ID prefix role inference.
- Added `verifyReleaseAuthSafety`, automatically attached to `preReleaseBuild`, to require
  `ProductionAuthRepository` in the composition root and reject preview adapters, prefix-role
  inference, Supabase secret/service-role keys, and hard-coded numeric PIN assignments.
- `verifyReleaseAuthSafety testDebugUnitTest --offline --no-daemon
  -Pkotlin.compiler.execution.strategy=in-process --max-workers=1` passed all 12 tests.
- `assembleRelease --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process
  --max-workers=1` passed 50 tasks, including the safety gate and release lint-vital, and
  produced the unsigned release APK.
- `lint --offline --no-daemon -Pkotlin.compiler.execution.strategy=in-process
  --max-workers=1` passed full Android lint with zero errors.
- Initial combined verification attempts were interrupted by sandbox network/temp access and
  command timeouts; no project failure was hidden. The successful offline split runs above are
  the authoritative result.

### 2026-07-28 — Task 4.2 production authentication state machine

- Status: Complete.
- `AuthRepository` now owns login, restored-session validation, and logout. Injected use
  cases drive an initialization-aware `AuthViewModel`; Compose shows an explicit secure
  session loading state and receives callbacks only.
- Added an Android Keystore AES-GCM `SessionManager` with ciphertext-only preferences,
  SDK auto-load/save/refresh configuration, and a non-secret persisted installation UUID.
  Production repository wiring now invokes hosted `pin-login`, imports the returned
  token pair only into Supabase Auth, reloads the Auth subject, derives role/shop solely
  from RLS-protected profile/membership/shop rows, validates restored sessions, and
  clears local Auth state on identity failure or logout. Compilation remains pending.
- Added deterministic repository tests for normalized request construction, local input
  rejection, sanitized credential errors, cleanup after post-import identity failure,
  authoritative restore, and logout. Local Auth clearing now runs in a non-cancellable
  block after remote sign-out, with repository fallback coverage when sign-out fails.
  Installation-ID/storage failures also become safe unavailable results, and the
  dashboard exposes a disabled `Logging out…` state. Full verification remains pending.
- Repository tests use one explicitly synthetic PIN constant; no operator/user PIN was
  retained in source, generated artifacts, status, or documentation.
- Updated the authentication, Android architecture, and README handoff to reflect the
  production binding and encrypted session boundary; Task 4.3 retains only preview-class
  removal. Final build/lint and optional on-device hosted confirmation remain.
- The first complete gate passed 12 tests, APK assembly, and lint with zero errors.
  Encrypted/session preference writes now use the AndroidX KTX API with synchronous
  durability where Auth requires it. The final cleanup gate again passed all 12 tests;
  lint reports zero errors, 17 pre-existing warnings, and no Task 4.2 warning.
- After replacing all authentication test input with an explicitly synthetic PIN, the
  final offline unit suite again passed all 12 tests in 77 seconds.
- The debug build receives the expected hosted development URL and a valid publishable
  key without either value being printed. ADB did not return a connected-device list, so
  on-device UI confirmation was unavailable; the hosted correct-PIN subject/profile path
  remains independently verified and the Android success path is deterministic-test covered.

### 2026-07-28 — Task 4.1 explicit dependency-injection architecture

- Status: Complete.
- Added an application-owned dependency container, application-scoped lazy Supabase
  client construction, domain authentication interfaces/use case, injected
  `AuthViewModel`, lifecycle-aware UI state collection, and a deterministic fake
  repository unit test. The UI-facing container exposes use cases only, so fake graphs
  require no SDK client. No composable constructs or locates a repository.
- Preview authentication remains centralized in the production graph only as the
  temporary implementation scheduled for replacement in Task 4.2.
- Repository Gradle `testDebugUnitTest --no-daemon` compiled the debug application and
  passed all four unit tests, including deterministic fake injection. The final offline
  rerun after narrowing the fake container seam also passed in 68 seconds.
- Gradle `assembleDebug lintDebug --no-daemon` completed successfully. The APK is
  `71,687,841` bytes; lint reports zero errors and 17 non-blocking version/resource/icon
  warnings. The command exceeded the shell wait window only during teardown; both daemon
  logs record `BUILD SUCCESSFUL` and the APK/lint artifacts were written.

### 2026-07-27 — Task 3.9 and Phase 3 exit gate

- GitHub Actions run `30292372527` reached database testing after Edge checks,
  migration replay, deterministic seed/reset, lint, and all existing pgTAP suites passed.
- The run exposed a discovery conflict: `supabase test db` recursively executed the
  integration SQL files before the dedicated concurrency harness.
- The harness is now under `supabase/integration-tests/`, outside the pgTAP discovery
  tree. Both relocated SQL files parse with bundled `pglast`, the workflow parses with
  PyYAML, and `git diff --check` passes. Run `30292811757` then passed every standard
  gate and executed all parallel operations; its verifier exposed unsupported
  `min(uuid)` aggregation. The verifier now counts the retry row and selects its UUID in
  separate statements.
- GitHub Actions run `30293062132` passed Edge verification, all migrations from zero,
  deterministic seed/reset, database lint, every pgTAP suite, and the complete backend
  integration/concurrency harness on its first run for the final fix.
- Task 3.9 and the Phase 3 exit gate are complete. Hosted development schema remains
  unchanged; linked history matches every local migration through `20260728060000`, and
  linked lint reports `No schema errors found`.

### 2026-07-27 — Task 3.8 trusted business reports

- GitHub Actions run `30291547016` passed Edge checks, all migrations from zero,
  deterministic seed/reset, database lint, every prior pgTAP suite, and all 37 report
  reconciliation, role, tenant, Nepal-boundary, and query-plan assertions.
- `supabase db push --linked --dry-run` selected only
  `20260728060000_trusted_business_reports.sql`; the linked push applied it successfully.
- Hosted migration history matches through `20260728060000`; linked lint reports
  `No schema errors found`.

### 2026-07-27 — Task 3.7 atomic financial operations

- GitHub Actions run `30289339018` passed the initial financial migrations and 49
  assertions. Follow-up run `30289769906` passed the system-account bootstrap, expanded
  deterministic seed/reset, database lint, every prior suite, and all 52 final Task 3.7
  assertions.
- Linked dry runs selected only the expected Task 3.7 migrations before each push.
  Hosted history now matches through `20260728050000`.
- `supabase db lint --linked --level warning --fail-on error` reports
  `No schema errors found` after the final deployment.

### 2026-07-27 — Task 3.6 vendor financial operations

- GitHub Actions run `30288446946` applied every migration to fresh Postgres, verified
  the deterministic seed/reset, linted database functions, and passed all pgTAP suites,
  including all 59 vendor payment, return, due, and reversal assertions.
- `supabase db push --linked --dry-run` selected only
  `20260728010000_vendor_return_movement_types.sql` and
  `20260728020000_vendor_financial_operations.sql`; the subsequent linked push applied
  both successfully.
- `supabase db lint --linked --level warning --fail-on error` returned
  `No schema errors found`. `supabase migration list --linked` shows matching local and
  remote histories through `20260728020000`.

### 2026-07-24 — Task 2.5 balanced ledger implementation

- Added financial accounts, non-overlapping accounting periods, immutable journal
  transactions/entries, and expenses. Account natural sides and system-purpose identity
  are constrained; no writable balance exists.
- Deferred integrity requires at least two entries, exact debit/credit balance, valid
  same-shop typed business sources, expense amount equality, and exact reversal entries.
- Migration parses. A 28-assertion pgTAP suite now covers balance, derived amounts,
  sources, expense equality, reversal, period overlap, cross-shop inputs, RLS, and
  direct writes; count/parsing/fresh execution are pending. Migration is not deployed.
- First fresh run `30075992454` applied/linted the schema and reached assertion 9, where
  pgTAP required an explicit `numeric`-to-`bigint` cast for `sum(bigint)`. The fixture
  now casts the derived balance result; schema behavior is unchanged.
- Corrected run `30076199784` passed all migrations, lint, prior suites, and all 28
  ledger assertions. Hosted histories match through `20260724200000`; linked lint is clean.

### 2026-07-24 — Task 2.4 purchasing schema implementation

- Added Owner-only vendor, bill/line, receipt/line, vendor payment/allocation, and
  vendor return/line tables with same-shop composite foreign keys and idempotency.
- Deferred integrity checks reconcile posted bills, prevent over-receipt and overpayment,
  require exact receipt-to-FIFO-lot quantity/cost, fully allocate payments, and prevent
  cumulative vendor over-return. Normalized invoice references remain unique permanently
  per shop/vendor.
- Static parsing passed. Integrity review added bill-to-receipt cost equality, original-
  lot vendor-return cost equality, and payment revalidation after vendor credits.
  A 31-assertion pgTAP suite now covers structure, arithmetic, invoice normalization,
  receipt/lot reconciliation, limits, cross-shop inputs, RLS, and direct writes;
  assertion count matches and both SQL files parse. Fresh CI run `30075105709` passed
  all gates. Hosted histories match through `20260724170000`; linked lint is clean.

### 2026-07-24 — Task 2.3 sales schema implementation

- Added sales, immutable price/discount lines, exact FIFO cost allocations, payments,
  returns, return-to-original-allocation evidence, and refunds with same-shop composite
  foreign keys, idempotency, lifecycle/reversal metadata, RLS, and read-only client grants.
- Deferred integrity checks reconcile posted header/detail/allocation totals, full
  non-credit settlement, derived credit settlement, cumulative over-return limits, and
  refund caps. Approved D9 now restricts lot/movement/allocation cost evidence to Owners
  and Super Admins.
- SQL static parsing passed. Trigger operation handling and the authorization contract/
  baseline policy expectation were corrected for the new cost restriction. Executable
  31-assertion pgTAP coverage now exercises structure, reconciliation, constraints,
  role/shop visibility, and direct-write denial. First fresh run `30073969515` applied
  and linted the migration, then exposed nullable three-valued logic in the credit check;
  missing identity/due fields now fail closed. The two row-count failures shared that cause.
- Corrected run `30074252359` passed Edge checks, fresh migration application, database
  lint, all prior pgTAP, and all 31 Task 2.3 assertions. Hosted migration history matches
  through `20260724143000`; linked lint reports no schema errors.

### 2026-07-24 — Complete Task 2.2 policies D7–D11

- Product owner approved all remaining policy decisions. D7–D9 use the presented
  backdating/period, permanent product-code, and Salesman visibility rules. Conservative
  D10–D11 first-release defaults use whole-paisa/no-VAT posting, 90-day notifications,
  and no automatic audit deletion.
- Every D1–D11 section now records schema, RPC, permission, UI, and acceptance effects.
  Automated full-policy consistency and `git diff --check` passed; no migration changed.

### 2026-07-24 — Approve Task 2.2 policies D1–D3

- Product owner approved: no negative stock for any role; Owner-only price/discount
  override; and Owner-only identified credit sales with required due date and partial
  payments.
- The policy record maps each choice to schema constraints, atomic RPC behavior,
  permissions, UI behavior, and acceptance tests. D4–D11 remain explicitly pending;
  no migration changed.

### 2026-07-24 — Approve Task 2.2 policies D4–D6

- Product owner approved cash/bank-only settlement with split tender and no
  overpayment; Owner-only 30-day sale returns with sellable/damaged disposition and
  refund cap; and Owner-only purchasing with strict receipt/payment limits, unique
  vendor invoices, draft cancellation, and posted reversal/return.
- Schema, atomic RPC, authorization, UI, accounting, and acceptance consequences are
  recorded. D7–D11 remain pending; no migration changed.

### 2026-07-24 — Task 2.1 canonical model acceptance

- Added a single source-of-truth data dictionary covering identity, products,
  inventory, sales, returns, vendors, purchasing, payments, cash/bank journals,
  expenses, notifications, and audits.
- Each table records purpose, keys, tenant/RLS ownership, time/lifecycle, reversal,
  idempotency, mutation boundary, reconciliation, and expected query/index behavior.
- Balances and dues are explicitly derived; server-calculated posted totals have
  reconciliation rules. Eleven unresolved business-policy groups are isolated in the
  Task 2.2 decision register. Review added exact return-allocation evidence and the
  complete system control-account model required for balanced journals. Existing
  private security/control tables have an explicit lifecycle register; no schema changed.
- Deterministic coverage verification passed for 35 required objects, 29 unique
  detailed table headings, and decision groups D1–D11; `git diff --check` passed.

### 2026-07-24 — Task 1.6 authorization matrix implementation

- Added the canonical table/RPC permissions matrix and 42 executable assertions for
  anonymous, no-membership, active/disabled Salesman, correct/wrong-shop Owner, Super
  Admin, service-only RPCs, forged tenant writes, and immutable ledger rows.
- The audit identified and fixed stale-token self-profile/membership visibility for a
  disabled user through a forward-only RLS migration.
- Bundled `pglast` parsed the migration and pgTAP suite successfully. GitHub Actions
  run `30071770493` passed Edge checks, fresh migrations, database lint, and every
  pgTAP suite. Hosted migration history matches through `20260724101500`; linked lint
  reports no schema errors.

### 2026-07-24 — Task 1.5 hosted authentication acceptance

- Secure masked verification returned the exact managed Auth subject, proved the
  generated token was rejected on second redemption, refreshed the session to the same
  subject, and read the matching authenticated `super_admin` profile through RLS.
- PIN, generated token hash, access/refresh tokens, and Auth bodies remained memory-only.
  The ignored result stores only safe identifiers and four boolean checks.
- Hosted secret-name inspection confirmed `GDAD_LOGIN_DIAGNOSTIC_TOKEN` and
  `GDAD_BOOTSTRAP_TOKEN` are absent. CI runs `30070481674` and `30070939071` passed
  Edge verification, fresh migrations, lint, and all pgTAP suites.

### 2026-07-22 — Task 1.4 fresh CI and hosted deployment

- GitHub Actions run `29941998214` passed all Edge checks, applied all migrations to a
  fresh Supabase database, passed database lint, and passed every pgTAP suite including
  all 27 account-administration assertions.
- Linked dry-run selected only `20260722224500_account_administration.sql`; it applied
  successfully and local/remote migration histories match through that version.
- `manage-accounts` deployed successfully. A valid-shape request without a Bearer user
  returned `401 UNAUTHORIZED`; linked database lint reported no schema errors.

### 2026-07-22 — Identify hosted session-link response mismatch

- The masked diagnostic reached `auth-link-result`: hosted Auth returned success for
  admin link generation, but `pin-login` expected the client-library `properties`
  wrapper instead of the raw endpoint's top-level token hash.
- The temporary `GDAD_LOGIN_DIAGNOSTIC_TOKEN` was removed automatically. No PIN,
  token hash, session token, or Auth body was persisted.
- Current fix parses the raw response (with wrapper compatibility) and exchanges the
  token hash using the documented `email` verification type. Pinned Deno 2.4.0
  `deno task check` passed formatting, lint, both function type-checks, and all 20 tests.

### 2026-07-22 — Hosted Super Admin bootstrap and PIN-login diagnosis

- Hosted account creation completed with one Auth user, one profile, one private PIN
  credential, and one audit event; the one-time bootstrap secret was removed.
- Correct-PIN verification reached deployed `pin-login` but returned HTTP 503
  `SERVICE_UNAVAILABLE`; no PIN or session token was written to disk or logs.
- Current change adds safe, temporary-token-gated stage diagnostics. Pinned Deno 2.4.0
  `deno task check` passed formatting, lint, both function type-checks, and all 19 tests.
  The diagnostic `pin-login` build deployed successfully; the subsequent corrected
  masked verification succeeded as recorded above.

### 2026-07-22 — Hosted correct-PIN subject and profile verification

- The corrected `pin-login` returned a Supabase session for the exact managed Auth
  subject; an authenticated RLS read returned the matching `super_admin` profile.
- The secure helper persisted only safe identifiers and boolean verification evidence;
  the PIN and access/refresh tokens remained memory-only.
- A hosted secret-name check confirmed both one-time diagnostic/bootstrap secret names
  are absent. Task 1.3 is complete and Task 1.4 is now active.

### 2026-07-22 — Task 1.2

- Command: pinned Deno 2.4.0 `deno task check` from `supabase/functions`.
- Result: format and lint passed for nine files; `pin-login/index.ts` and
  `manage-users/core.ts` type-check passed; 11 tests passed, 0 failed.
- Coverage: valid/invalid normalized input, correct/wrong PIN, deterministic HMAC
  isolation, independent salts, pepper-version rejection, malformed PHC fail-closed,
  dummy/real shared verification, Argon2id parameters, and source fingerprint behavior.
- Hosted state was not changed; the shared helper remains local until the provisioning
  operation and its migration pass database/Edge verification.

### 2026-07-22 — Task 1.3 local verification

- Edge command: pinned Deno 2.4.0 `deno task check` from `supabase/functions`.
- Edge result: format/lint and both function type-checks passed; 16 tests passed and 0
  failed across managed-user parsing, shared PIN behavior, and PIN login behavior.
- SQL command: pglast parsed migration `20260721090000` and
  `account_provisioning.test.sql`; both parsed successfully. The pgTAP suite defines 27
  structure, privilege, idempotency, hierarchy, cross-shop, collision, finalization,
  credential, membership, and audit assertions.
- Linked dry run: `supabase db push --linked --dry-run` selected only migration
  `20260721090000_managed_user_provisioning.sql`; no hosted state changed.
- Local database runtime: Not run because Docker is unavailable. Fresh database CI is
  required before deployment or Task 1.3 completion.
- Repository review: `git diff --check` reported no whitespace errors; the scoped secret
  scan found no committed secret/service-role/pepper/bootstrap/access-token value.
- Publishing blocker: the required GitHub publishing workflow cannot proceed because
  GitHub CLI `gh` is not installed. No files were staged, committed, pushed, or deployed.
- Follow-up verification: after documenting the compensation contract and accepting
  empty responses from the failure-marking RPC, `deno task check` again passed all 16
  tests and the documentation contract assertion passed.

### 2026-07-22 — Task 1.1

- Documentation assertions confirmed the package, reserved exact callback, direct
  no-redirect PIN flow, Supabase `auth.importSession`, encrypted Android Keystore
  session manager requirement, process restoration, and logout policy are all present.
- UTF-8 validation passed for `docs/authentication.md` with no replacement characters.
- `supabase/config.toml` and hosted Auth redirects were intentionally not changed or
  pushed: PIN login needs no callback, and the reserved handler is disabled until Task
  4.7 implements and tests exact URI validation.
- Source/tests were not run because Task 1.1 changes documentation only; the Task 0.3
  Android, Deno, hosted-lint, and CI baseline remains current.

### 2026-07-22 — Tasks 0.1 and 0.2

- Repository: `main` at `1be9d33`, tracking `origin/main`; all expected runbook paths
  exist. Existing launcher and managed-user changes were preserved.
- Hosted migrations: `supabase migration list --linked` confirmed local/remote parity
  through `20260715151000`; local `20260721090000` is not deployed.
- Hosted Edge: `supabase functions list --project-ref zniqkuwktvincjndcgpu` confirmed
  `pin-login` ACTIVE at version 5.
- Encoding: a UTF-8 code-point scan of repository Markdown, Kotlin, SQL, and TypeScript
  found no replacement characters or mojibake sequences; six damaged status separators
  were repaired. The execution plan itself is valid UTF-8.
- External handoff: `gdad bag sales app.md` was not found on the Desktop; no claims were
  imported from an unavailable file.

### 2026-07-22 — Task 0.3

- Android: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\build-apk.ps1`
  completed `BUILD SUCCESSFUL` in 33 seconds; 44 tasks were up-to-date, unit tests
  passed, and `GDAD-BAGS-test.apk` was refreshed.
- Edge: pinned Deno 2.4.0 `deno task check` passed format, lint, `pin-login/index.ts`
  type-check, and five tests. One pre-existing untracked `manage-users/core.ts` format
  defect was repaired with `deno fmt` before the successful run.
- Database: Docker is not installed, so local reset/pgTAP was not run. Linked hosted
  lint completed with `No schema errors found`; GitHub Actions run `29423495797` for
  current commit `1be9d33` is completed/success.
- Tooling: portable Java 17.0.19, Android SDK/Build Tools 36, Gradle 9.4.1 build wrapper,
  Supabase CLI 2.101.0, and ignored Deno 2.4.0 are available.
- APK inspection: package `com.gdad.bags`, label `GDAD BAGS`, version `0.1.0`/1, and all
  five launcher densities are present. Size and SHA-256 remain 71,671,378 bytes and
  `193B0DAD57C939A804C0C7DE69AF2DFA58F51A88F80A2F0FC4FEC9FEE3EFE028`.

### 2026-07-21

- Command: `powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\\build-apk.ps1`.
- Result: `BUILD SUCCESSFUL` in 53 seconds; all Android unit tests passed and the debug
  APK was assembled and copied to `GDAD-BAGS-test.apk`.
- Packaged icon check: Android `aapt dump badging` reports `GDAD BAGS` and resolves
  `ic_launcher.png` for mdpi, hdpi, xhdpi, xxhdpi, and xxxhdpi densities.
- APK: 71,671,378 bytes; SHA-256
  `193B0DAD57C939A804C0C7DE69AF2DFA58F51A88F80A2F0FC4FEC9FEE3EFE028`.
- Build note: the Kotlin daemon could not create a marker under the user profile, so
  Gradle used its supported non-daemon fallback; compilation and packaging succeeded.
- Not verified: installation on a physical Android device, visual launcher rendering,
  production authentication, persistence, functional feature screens, or release
  signing. This APK is for testing only.

### 2026-07-15

- Command: local bundled Gradle `testDebugUnitTest --no-daemon` with `JAVA_HOME`,
  `ANDROID_HOME`, `ANDROID_SDK_ROOT`, and `GRADLE_USER_HOME` pointed at `.tooling`.
- Result: `BUILD SUCCESSFUL` in 30 seconds; 26 tasks up-to-date.
- Tests: 3 executed previously, 0 failures, 0 errors, 0 skipped.
- APK: existing debug APK present at `GDAD-BAGS-test.apk` and
  `app/build/outputs/apk/debug/app-debug.apk`.
- Supabase foundation verification: `testDebugUnitTest --no-daemon --offline` completed
  successfully after dependencies were resolved; 26 tasks, 1 executed and 25 up-to-date.
- Packaging verification: `assembleDebug --no-daemon --offline` completed successfully
  in 47 seconds; 38 tasks, 5 executed and 33 up-to-date. The generated app-module debug
  APK contains the Supabase foundation.
- Export note: the root `GDAD-BAGS-test.apk` was not refreshed during this change; use
  `app/build/outputs/apk/debug/app-debug.apk` for this build or rerun `build-apk.ps1`.
- Not verified: hosted Supabase initialization, Android network behavior, device UI
  behavior, persistence, production authentication, business RPCs, and release build.
- Backend static verification: Supabase CLI `2.101.0` initialized the workspace; the
  migration and pgTAP file passed PostgreSQL grammar parsing with `pglast 8.2`; the
  GitHub Actions workflow parsed successfully with PyYAML 6.0.3; and
  `pnpm install --offline --frozen-lockfile` reported the lockfile up to date.
- Backend runtime verification: GitHub Actions run `29403029753` completed successfully
  for commit `dd9803d`. A fresh Postgres database applied the migration, database lint
  passed, and all 24 pgTAP structure/RLS/privilege assertions passed. Local Docker
  execution remains unavailable on this machine.
- Hosted deployment verification: `supabase migration list --linked` showed local and
  remote migration `20260715084551`; `supabase db lint --linked --level warning
  --fail-on error` completed with `No schema errors found` for `extensions`, `private`,
  and `public`.
- Android client configuration verification: ignored
  `.tooling/gradle-user-home/gradle.properties` contains the expected hosted project
  URL and a correctly formatted publishable key. Generated `BuildConfig` checks passed
  without printing the key. The final `build-apk.ps1` run succeeded in 2m23s with 44
  tasks (10 executed, 34 up-to-date) and refreshed `GDAD-BAGS-test.apk`.
- Hosted client connectivity verification: the client-key-authenticated Auth health and
  settings endpoints returned HTTP `200`; an anonymous `shops` read returned HTTP
  `401`, confirming the initial database deny policy remains effective.
- B3.1 verification: `supabase db push --linked --dry-run` selected only migration
  `20260715121500`; the hosted push applied it successfully; migration history shows
  matching local/remote versions through `20260715121500`; and hosted lint found no
  errors in `extensions`, `private`, or `public`. `build-apk.ps1` then completed in
  1m4s with all 44 Android tasks up-to-date. GitHub Actions run `29410151903` then
  applied both migrations to fresh Postgres, passed database lint, and passed the core
  plus seven-assertion authentication pgTAP suites.

- B3.2/B3.3 Edge verification: from `supabase/functions`, temporary pinned Deno 2.4.0
  ran `deno task check`; formatting, lint, full `index.ts` type-check, and all five tests
  passed. `pglast 8.2` parsed all migrations/tests and PyYAML parsed the CI workflow.
- Hosted database: migrations match through `20260715151000`; linked lint reports no
  errors in `extensions`, `private`, or `public`.
- Hosted Edge: `pin-login` is ACTIVE at version 5. Random pepper/dummy values were
  generated in memory and uploaded as secrets without being written or displayed.
- Hosted HTTP checks using the ignored local publishable key: malformed body returned
  `400`, invalid publishable key returned `401`, and valid-key unknown user returned the
  generic `401` after dummy Argon/RPC work.
- Hosted SQL diagnosis found and the forward migration fixed an ambiguous PL/pgSQL
  conflict target. No request, PIN, key, verifier, one-time token, or session token was
  logged or committed. Fresh-Postgres pgTAP/CI is pending the next push.
## Recommended next task

Install/upgrade the checksum-pinned rc12 APK on one authorized phone, confirm the displayed version is
`0.2.0-rc12` (13), then retry deletion only against a clearly disposable empty shop. In parallel, place the
backup identity, production database password, and Android signing material in an independently
recoverable owner secret store and confirm failure notifications/daily backup approval handling.

## Change log

### 2026-09-05 — Add negotiated per-sale pricing with FIFO cost-based profit

- Status: Local implementation and Android verification complete; database CI, merge, production
  deployment, signed rc13 artifact, and physical smoke test remain.
- Changed: Sale UI/repository/tests, product price labels, forward Supabase migration and pgTAP/report
  fixtures, business-policy/data/report docs, rc13/code14 build/workflow naming, README, and
  `PROJECT_STATUS.md`.
- Behavior: Owner and Salesman Sale entry pre-fills the product suggestion and accepts a different
  actual price on each sale. The protected server stores both snapshots, calculates the sale from
  the negotiated price, and preserves separate Owner-only discount/credit authority. Owner receipt
  and end-day/period reporting show profit derived from actual revenue minus exact FIFO cost.
- Data/security impact: migration `20260905120000` replaces only the existing FIFO sale function's
  Salesman price-change predicate and its documentation comment. It does not update products,
  historical sales, users, shops, credentials, secrets, or other business rows. Cost/profit remains
  inaccessible to Salesmen.
- Verification: focused 8-test Android run passed; full 194-test/47-suite Android release gate passed
  all safety checks and build tasks with zero failures/errors and lint zero errors/10 warnings;
  `pglast` parsed all four migration statements and the exact rule-transform audit passed. Local
  Docker-backed database execution is unavailable and therefore remains mandatory in PR CI.
- Next: publish the branch, require green database and Android CI, merge exact head, deploy the
  migration through the protected production workflow, then produce/verify/install rc13.

### 2026-09-03 — Record successful Xiaomi HyperOS rc12 installation

- Status: Complete for installation, package integrity, launch, and trusted read; disposable-shop
  deletion retest remains.
- Changed: `docs/release-candidate-handoff.md` and `PROJECT_STATUS.md`.
- Behavior: records the working native-installer fallback when HyperOS blocks ADB installation and
  the exact device-side package/version/hash/launch evidence for rc12.
- Data/security impact: no source, backend, credential, account, shop, or business record changed.
  The exact temporary APK created by this task was removed from phone Download storage after install.
- Verification: authorized-device discovery, device/user/policy inspection, guarded APK verification,
  native HyperOS security scan/install, `pm path`, `dumpsys package`, device-side `sha256sum`,
  `am start -W`, role-appropriate UI/trusted-refresh inspection, process check, and cleanup check pass.
- Next: sign in as the active Super Admin and delete only a clearly disposable empty shop using the
  corrected rc12 dialog; never send the PIN.

### 2026-09-03 — Pin the protected production-signed rc12 APK

- Status: Complete for protected build, independent verification, and controlled handoff pinning;
  the disposable-shop physical deletion retest remains.
- Changed: `tools/install-release-candidate.ps1`, `README.md`, `docs/release-build.md`,
  `docs/release-candidate-handoff.md`, and `PROJECT_STATUS.md`.
- Behavior: the guarded verifier/installer now accepts only the exact protected rc12/code13 APK and
  fails closed on any other checksum, signer, package, version, SDK, or launcher. The handoff records
  exact merge/build provenance and safe fresh/upgrade instructions.
- Data/security impact: no hosted user, shop, business row, credential, signing material, or secret
  changed. No app-store publication occurred. The root generated APK was replaced with the exact
  cloud artifact whose bytes match the protected workflow sidecar.
- Verification: PR/run/artifact metadata reconciliation, sidecar and independent SHA-256, bundled
  `apksigner`, bundled `aapt`, production/development binding scan, forbidden-marker scan, guarded
  installer VerifyOnly, documentation consistency search, and `git diff --check` passed.
- Next: install or upgrade the pinned APK on one Android 12+ phone and delete only a clearly
  disposable empty shop using the PIN for the currently signed-in Super Admin account.

### 2026-08-30 — Advance the verified deletion fix to installable rc12/code13

- Status: Complete for source/release naming and local verification; publication remains.
- Changed: `app/build.gradle.kts`, `android-release.yml`, `build-production-apk.ps1`, `README.md`,
  `docs/release-build.md`, and `PROJECT_STATUS.md`.
- Behavior: the replacement APK will use `0.2.0-rc12`/13 and rc12-specific workflow/local artifact
  names, allowing installation over the checksum-pinned rc11/code12 baseline without reusing an
  Android version code.
- Data/security impact: none; no signing key, password, PIN, project secret, hosted row, or production
  service changed. The rc11 guarded installer remains deliberately pinned until independently
  verified rc12 signed bytes exist.
- Verification: the complete 109-task Android release-safety/test/lint/build gate passed after the
  version bump; tracked workflow/local build names consistently use rc12/code13, while the guarded
  installer intentionally remains pinned to independently verified rc11 bytes.
- Next: commit the version bump, publish the branch when GitHub connectivity returns, and let exact
  green main produce protected signed rc12 bytes.

### 2026-08-30 — Correct Super Admin shop-deletion preflight and rejected-PIN retry

- Status: Complete for local implementation and verification; PR publication remains.
- Changed: `AccountManagementScreen.kt`, `AccountManagementViewModel.kt`,
  `ProductionAccountManagementRepository.kt`, their account UI tests, and `PROJECT_STATUS.md`.
- Behavior: the deletion dialog remains open until authoritative success, shows which slug/reason/PIN
  requirement is unmet, normalizes slug case, explains that the signed-in Super Admin PIN is
  required, and displays server denial inside the dialog. Unauthorized/terminal requests discard
  the rejected PIN and idempotency key so a corrected submission is genuinely new; only backoff-safe
  failures retain exact retry state.
- Data/security impact: no production mutation, user/shop/business-data change, PIN reset, credential
  disclosure, or authorization weakening. A read-only aggregate production query returned counts
  only and showed one healthy Super Admin credential with no lockout/failures and no deletion request.
- Verification: production aggregate reconciliation, all three focused account suites, the complete
  109-task Android release-safety/test/lint/build gate, and `git diff --check` passed.
- Next: commit/push the branch, open the PR, merge exact green head, and produce the next protected
  signed APK for device retest.

### 2026-08-26 - Add privacy-safe Super Admin PIN rejection diagnostics

- Status: Partial; query implementation complete, protected production read-only run pending.
- Changed: `supabase/production-account-reconciliation.sql` and `PROJECT_STATUS.md`.
- Behavior: the established protected reconciliation now returns numeric-only counts for active
  Super Admin credentials, current credential locks, accumulated failed login attempts, and deletion
  request outcomes including reauthentication failure. It does not reveal which account/shop/request
  contributed to a count.
- Data/security impact: read-only aggregate query only; no migration, credential, identity, session,
  shop, deletion request, or business row is inserted, updated, or deleted.
- Verification: pinned local `pglast 8.2` parses the extended SQL and `git diff --check` passes. Manual
  shape review confirms every new JSON value is a numeric aggregate; the protected workflow also rejects
  nonnumeric JSON output and unexpected project refs. Production read-only execution remains pending.
- Next: parse/review the SQL, publish it through required review, run protected reconciliation, and use
  only its aggregate result to choose between install/version correction, lockout wait, or an explicitly
  authorized audited credential-recovery path.

### 2026-08-26 - Pin independently verified signed rc11 candidate

- Status: Complete for merge, production Function deployment, protected signing, independent
  inspection, and controlled direct-distribution handoff.
- Changed: `tools/install-release-candidate.ps1`, `docs/release-build.md`,
  `docs/release-candidate-handoff.md`, and `PROJECT_STATUS.md`; the signed APK is copied only to ignored
  local release/verification locations.
- Behavior: the guarded installer now accepts only rc11/code 12 bytes with the exact SHA-256, pinned
  signer, package, SDK, and launcher. Existing 4–8 digit credentials now work for login and privileged
  reauthentication; new/reset credentials remain 6–8 digits.
- Data/security impact: production Functions are updated, but no database row, shop, identity, session,
  PIN, or business record changed. Signing secrets remained inside the protected runner; no paid service
  or app-store publication ran.
- Verification: PR Database `32871787076`, PR Android `32871787441`, protected deployment
  `32872604166`, and protected signing `32872992204` all pass on the exact recorded commits. Artifact
  `9594853682` matches its checksum sidecar; signature/package/version/SDK/icons/binding/forbidden-marker
  inspection passes. Guarded VerifyOnly passes at the recorded timestamp with `Installed=false`.
- Next: upgrade one authorized phone and perform the first rc11 deletion only on a disposable empty shop.

### 2026-08-25 - Accept legacy PIN length only for existing-credential verification

- Status: Partial; implementation and complete local Edge/Android gates pass, with hosted review and
  release steps remaining.
- Changed: `supabase/functions/_shared/pin.ts`, `pin-login/core.ts`, `manage-accounts/core.ts`, their
  focused tests; Android production authentication/account repository/account dialogs and tests;
  `app/build.gradle.kts`, protected/local release artifact names, `README.md`,
  `docs/release-build.md`, `docs/account-administration.md`, and `PROJECT_STATUS.md`.
- Behavior: login and destructive/admin reauthentication accept an existing 4–8 digit PIN. New account
  and reset PIN creation remain 6–8 digits. Source advances to rc11/code 12; the installed-artifact
  guard remains on verified rc10 pending a signed replacement.
- Data/security impact: none hosted; no secrets, credentials, user/shop/business data, migration, or
  paid service changed. Authorization, lockout/rate, exact confirmation, idempotency, and audit remain.
- Verification: 36 Edge tests plus changed-file formatting, full lint, and all Function type checks pass.
  Focused Android tests pass 31/31; the complete 109-task Android gate passes all four safety scans,
  every debug unit test, release artifact inspection, lint (0 errors/9 warnings), and debug assembly in
  7m16s. `git diff --check` passes.
- Next: merge, deploy Functions, sign and inspect rc11, and repoint the guarded installer only to the
  independently verified bytes.

### 2026-08-25 - Pin independently verified signed rc10 candidate

- Status: Complete for merge, backend deployment, protected signing, independent inspection, and
  controlled direct-distribution handoff.
- Changed: `tools/install-release-candidate.ps1`, `docs/release-candidate-handoff.md`, and
  `PROJECT_STATUS.md`; the ignored signed APK and checksum sidecar are copied locally for controlled
  installation.
- Behavior: the guarded installer now accepts only rc10/code 11 bytes with exact SHA-256, pinned
  production signer, package, SDK, and launcher. The handoff documents the new Super-Admin-only
  deletion path and requires the first physical deletion to use a disposable empty shop.
- Data/security impact: production migration/Functions are live, but no shop, user, Auth identity,
  or business record was created or deleted. Signing secrets remained inside the protected runner;
  no paid service or app-store publication ran.
- Verification: PR Database run `32821256012`, PR Android run `32821256010`, protected deployment
  `32821910493`, and protected signing run `32822345644` all pass on the recorded exact commits.
  Downloaded artifact SHA-256 matches its sidecar; `apksigner` passes v2 with one pinned production
  signer; `aapt` passes package `com.gdad.bags`, rc10/code 11, SDK 31/36, launcher, label, and five
  icon densities. Extracted archive scan reports `production_binding_files=1`,
  `development_binding_files=0`, and `forbidden_marker_files=0`. Guarded installer `VerifyOnly`
  passes at `2026-08-25T14:15:13.8084016+05:45` with the same checksum, signer, package, and version;
  it reports `Installed=false` and no device serial.
- Next: run `tools/install-release-candidate.ps1 -InstallMode Upgrade` on exactly one authorized
  phone, then validate deletion only against a clearly disposable empty shop before using it on any
  live tenant.

### 2026-08-25 - Correct PR shop-deletion database compatibility guards

- Status: Partial; local SQL structure and grammar pass, while fresh-database CI rerun remains.
- Changed: `supabase/migrations/20260825120000_super_admin_shop_deletion.sql`,
  `supabase/tests/database/shop_deletion.test.sql`, and `PROJECT_STATUS.md`.
- Behavior: shop deletion now removes all 42 current `public`/`private` tenant tables in explicit
  restrictive-foreign-key order. A schema manifest stops the transaction if a future migration adds,
  removes, or renames a `shop_id` table without updating deletion coverage, and an explicit final
  check aborts if any current tenant row remains.
- Data/security impact: dynamic SQL is absent from the application functions, preserving the existing
  database hardening invariant. The controlled deletion-only audit bypass retains the established
  `business audit events are append-only` error for every other update/delete. No hosted change ran
  and no shop, user, Auth identity, or business row was read or changed.
- Verification: PR #62 first-head Database tests run `32820322031` applied migrations twice, passed
  deterministic seed and lint stages, and passed all 41 new shop-deletion assertions before the two
  compatibility failures. After correction, pinned `pglast 8.2` parses the changed migration and
  pgTAP file; static manifest comparison reports `manifest=42 checks=42 deletes=42` with zero
  differences; `git diff --check` passes. Local database runtime remains unavailable because this PC
  has no Docker/Podman, so the pushed PR rerun is required.
- Next: commit and push the correction, then require fresh-database and Android PR checks to pass
  before merge or production deployment.

### 2026-08-25 - Begin fail-closed Super Admin shop deletion backend

- Status: Partial; backend, Android, documentation, and local verification complete; fresh-database CI,
  review/merge, production deployment, and protected rc10 signing remain.
- Changed: `supabase/migrations/20260825120000_super_admin_shop_deletion.sql`,
  `supabase/functions/manage-accounts/core.ts`, and `PROJECT_STATUS.md`.
- Changed: `supabase/functions/manage-accounts/index.ts` now executes deletion reauthentication,
  transactional apply, identity validation, and resumable Auth cleanup;
  `supabase/functions/tests/manage-accounts/core.test.ts` covers the strict destructive request.
- Changed: `supabase/tests/database/shop_deletion.test.sql` adds the destructive backend regression
  suite, including a schema-wide assertion that no `shop_id` row survives for the deleted tenant.
- Changed: Android account domain/remote/repository/ViewModel and `RemoteOperation` now support
  Super-Admin-only deletion with exact cached-shop binding, strict local input checks, authenticated
  Edge invocation, safe response validation/messages, directory refresh, and exact-request retry.
- Changed: `AccountDirectoryStore` provides exact cached-shop lookup, and `AccountManagementScreen`
  adds a Super-Admin-only delete action plus high-friction, scrollable, keyboard-inset-aware destructive
  confirmation with stable test tags.
- Changed: `GdadApp` and `MainActivity` route the confirmed deletion to the account ViewModel; no
  preview-only or bypass callback is used.
- Changed: account repository, ViewModel, and Compose test suites now cover the new authorization,
  validation, retry, refresh, and role-visibility behavior.
- Changed: Android source and signing artifact names advance to `0.2.0-rc10`/11 in Gradle, the protected
  workflow, local production-build helper, README, and release-build guide. The installer deliberately
  remains pinned to verified rc9 bytes until a signed rc10 artifact exists and passes independent checks.
- Changed: `docs/account-administration.md` and `docs/authorization-matrix.md` document the exact
  destructive request, three-layer authorization, rollback-on-incomplete tenant deletion, independent
  audit retention, exclusive/shared identity rule, and resumable metadata-validated Auth cleanup.
- Behavior: service-role-only RPCs reserve, reauthenticate, atomically delete one shop's tenant graph,
  revoke exclusive managed-user sessions, retain immutable safe deletion evidence, and expose resumable
  managed Auth cleanup state. Exact slug, reason, active Super Admin, rate, and idempotency checks fail
  closed; direct client deletion remains unavailable.
- Behavior: the Edge parser accepts only the exact `delete_shop` body with valid UUIDs, lowercase shop
  slug, 6-8 digit reauthentication PIN, and a trimmed 8-500 character reason; extra fields fail closed.
- Data/security impact: new private deletion request/audit tables and privileged RPCs; the migration is
  not deployed and no live data was deleted.
- Verification: targeted `deno fmt --check`, `deno check manage-accounts/index.ts`, and all five
  `manage-accounts` parser tests pass. Pinned `pglast 8.2` parses the migration and 41-assertion pgTAP
  file, whose static assertion count matches its plan. Android production sources compile; the first
  focused test compile identified one missing JUnit assertion import, which is corrected for rerun.
  The focused Android repository/ViewModel/Compose suite passes after the import correction. The full
  Android gate (`verifyReleaseAuthSafety verifyReleaseAccessibilitySafety
  verifyReleasePerformanceSafety verifyReleaseArtifactSafety testDebugUnitTest lint assembleDebug`)
  passes in 13m25s; lint reports zero errors and 10 existing warnings. Edge lint/type-check and all 32
  Deno tests pass. SQL runtime verification remains unavailable locally because Docker/Podman is not
  installed; the pull-request database workflow must provide that proof before hosted deployment.
- Next: commit on a feature branch, open a pull request, and require both fresh-database and Android CI
  to pass before merge or any protected production deployment.

### 2026-08-23 - Pass and document physical signed-candidate performance budgets

- Status: Complete for five-run cold start, post-workflow warm PSS, and manual frame capture.
- Changed: `tools/measure-android-performance.ps1`, `docs/performance-reliability-audit.md`, and
  `PROJECT_STATUS.md`; ignored local Android platform-tools updated to 37.0.1 for capture only.
- Behavior: single-device detection and dash-prefixed ADB arguments are robust; the performance audit
  now contains representative physical results instead of pending placeholders.
- Data/security impact: measurement/tooling/docs only; no app/backend/business mutation and no device
  serial, credential, PIN, token, or business value committed.
- Verification: full run median 698 ms; current-only PSS 134,898 KiB; 51/3,051 janky frames (1.67%);
  all documented budgets pass and `git diff --check` passes.
- Next: run final automated release gates and publish the accumulated physical qualification evidence.

### 2026-08-23 - Harden physical performance capture for exactly one ADB device

- Status: Implemented; physical rerun pending.
- Changed: `tools/measure-android-performance.ps1` and `PROJECT_STATUS.md`.
- Behavior: a one-device ADB result is now an actual one-item array, so the full serial is selected;
  all ADB subcommands also use explicit argument arrays so Android dash-prefixed flags cannot be parsed as
  PowerShell parameters. Explicit serial validation and zero/multiple-device rejection are unchanged.
- Data/security impact: tooling-only change; no app/backend/business mutation and no serial, credential,
  PIN, token, or business value was committed.
- Verification: the pre-fix physical run reproduced `device '8' not found`; the first patched run reached
  the launch and reproduced ambiguous `-W` binding; the fully patched five-run capture is next.
- Next: capture and evaluate cold start, warm PSS, and janky frames on the authorized Redmi.

### 2026-08-23 - Accept physical non-touch focus and complete accessibility sign-off

- Status: Complete for non-touch focus order, modal containment, editing, and activation; the combined
  physical accessibility/slow-error procedure now passes.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records successful forward/back focus, dialog-contained editing, Cancel activation, and
  Report action focus using hardware keyboard or Switch Access, with no transaction posted.
- Data/security impact: accessibility verification only; no business value, identifier, credential,
  PIN, or token was collected or intentionally mutated.
- Verification: physical operator report `non touch focus dialog editing and activation correct`.
- Next: capture and evaluate the required physical performance metrics.

### 2026-08-23 - Accept physical 200% text and portrait-layout accessibility sign-off

- Status: Complete for large-text reachability, portrait layout, keyboard avoidance, and Login actions.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records successful traversal at approximately 200%/largest font across core screens,
  dialogs, one keyboard-visible form, Logout/Login, and restored Owner Dashboard.
- Data/security impact: accessibility/authentication verification only; no business value, identifier,
  credential, PIN, or token was collected or intentionally mutated.
- Verification: operator report interpreted as
  `200 percent text portrait keyboard and actions correct`.
- Next: complete hardware-keyboard or Switch Access focus/activation sign-off.

### 2026-08-23 - Accept physical TalkBack and offline-error accessibility sign-off

- Status: Complete for TalkBack order, labels, live-region failure, and credential-speech checks.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records successful TalkBack traversal of key Owner screens and one safe offline refresh,
  including one-time error announcement and preserved cached content.
- Data/security impact: read-only/safe-rejection verification; no business value, identifier,
  credential, PIN, or token was collected or intentionally mutated.
- Verification: operator report interpreted as
  `talkback order labels and offline announcement correct`.
- Next: complete physical 200% font/portrait reachability sign-off.

### 2026-08-23 - Accept controlled stock-adjustment cycle reconciliation

- Status: Complete for physical net-zero stock/value, movement-history, and report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that the quantity-one add/remove cycle returns stock and value to
  baseline, preserves exactly two opposite movements, and leaves unrelated business metrics unchanged.
- Data/security impact: read-only verification; no additional production mutation and no product,
  value, identifier, credential, PIN, or token collected.
- Verification: physical operator report
  `stock adjustment net quantity value history and report correct`.
- Next: complete physical accessibility and slow/error-state sign-off.

### 2026-08-23 - Accept the controlled quantity-one stock Manual remove

- Status: Complete for physical Owner adjustment-cleanup submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the successful quantity-one Manual remove/Data correction from the exact controlled lot.
- Data/security impact: one intentional production movement/journal/audit result was created to offset
  the controlled stock add. No product, cost, stock value, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `stock manual remove posted`.
- Next: verify net-zero stock/value effect and preserved +1/-1 movement history.

### 2026-08-23 - Accept the controlled quantity-one stock Manual add

- Status: Complete for physical Owner adjustment submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the successful quantity-one Manual add/Stock found operation at the product's
  displayed FIFO unit cost.
- Data/security impact: one intentional production lot/movement/journal/audit result was created. No
  product, cost, stock value, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `stock manual add posted`.
- Next: remove quantity one from that exact newly created lot to restore net stock.

### 2026-08-23 - Accept clean Owner-session restoration after Salesman logout

- Status: Complete for physical Owner reauthentication, navigation restoration, and cache repopulation.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that Owner login after Salesman logout restores all Owner screens and
  one authoritative copy of shop data without stale Salesman state or duplicates.
- Data/security impact: identity-scoped cache replacement only; no business value, login ID, PIN,
  identifier, credential, or token was collected or intentionally mutated.
- Verification: physical operator report `owner restored full screens data correct and no duplicates`.
- Next: complete and reconcile a controlled stock-adjustment add/remove cycle.

### 2026-08-23 - Accept Salesman identity-switch isolation and navigation restrictions

- Status: Complete for physical same-device Salesman login, cache isolation, and role navigation.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that a Salesman login after Owner logout sees the current-shop catalog
  but no Owner-only destination or cached vendor/finance/account data.
- Data/security impact: identity-scoped cache replacement only; no business value, login ID, PIN,
  identifier, credential, or token was collected or intentionally mutated.
- Verification: physical operator report `salesman login role screens correct and no owner cache`.
- Next: log out Salesman and restore/verify the Owner session.

### 2026-08-23 - Accept Owner logout and local session/cache purge

- Status: Complete for physical logout, protected-route closure, and process-restart verification.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that Owner logout returns to Login, prevents Back navigation into
  protected screens, clears identity-scoped local state, and remains logged out after process restart.
- Data/security impact: expected local session/cache/outbox deletion only; no authoritative business
  record or account was deleted and no login ID, PIN, value, credential, or token was collected.
- Verification: physical operator report `owner logout login screen and session not restored`.
- Next: verify Salesman identity-switch isolation and role-scoped navigation.

### 2026-08-23 - Retain the controlled Deposit after safe reversal rejection

- Status: Complete for fail-closed rejection and unchanged-state verification.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records that the original Deposit reversal was rejected without mutation after dependent
  finance test movements were reversed; no additional funding entry was created to bypass validation.
- Data/security impact: safe rejected attempt only. The original traceable controlled Deposit remains
  posted; no new receipt, reversal, amount, balance, identifier, credential, PIN, or token was created or collected.
- Verification: physical operator report interpreted as
  `deposit reversal safely blocked and state unchanged`.
- Next: verify Owner logout and local session/cache purge.

### 2026-08-23 - Reverse the controlled launch-test cash withdrawal

- Status: Complete for physical withdrawal cleanup reversal and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the successful immutable compensating reversal of the controlled Cash Withdrawal.
- Data/security impact: one intentional cleanup reversal/journal/audit result was created; the original
  remains preserved. No amount, balance, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `launch test withdrawal reversed`.
- Next: reverse the original controlled Cash Deposit.

### 2026-08-23 - Reverse the controlled Launch testing expense

- Status: Complete for physical launch-expense cleanup reversal and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the successful immutable compensating reversal of `Expense: Launch testing`.
- Data/security impact: one intentional cleanup reversal/journal/audit result was created; the original
  remains preserved. No amount, balance, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `launch test expense reversed`.
- Next: reverse the controlled cash Withdrawal.

### 2026-08-23 - Accept recovery-expense reversal reconciliation

- Status: Complete for physical Cash/expense-history/report cleanup read-back with no duplicate.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that the recovery-expense reversal restores Cash and effective
  Expenses exactly once, preserves immutable history/reason, and leaves Gross profit unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, identifier, credential, PIN, or token collected.
- Verification: physical operator report `recovery expense reversal cash history and report correct`.
- Next: reverse the remaining controlled finance movements in safe reverse order.

### 2026-08-23 - Accept reversal of the reconnect-recovery expense

- Status: Complete for physical recovery-expense reversal submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the successful immutable compensating reversal of the exact offline-recovery test expense.
- Data/security impact: one intentional production expense-reversal journal/audit result was created; the
  original remains preserved. No amount, balance, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `reconnect recovery expense reversed`.
- Next: verify restored Cash, reduced effective Expenses, preserved history/reason, and unchanged Gross profit.

### 2026-08-23 - Accept reconnect idempotency and single-effect reconciliation

- Status: Complete for physical one-expense/one-balance/one-report-effect read-back with no duplicate.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records confirmation that the preserved reconnect retry created exactly one Expense history
  row and one accounting effect despite the earlier offline attempt.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, identifier, credential, PIN, or token collected.
- Verification: physical operator report `reconnect expense single history balance and report correct`.
- Next: reverse and reconcile the reconnect-recovery expense.

### 2026-08-23 - Accept one authoritative finance result after reconnect retry

- Status: Complete for explicit connected retry and authoritative receipt; duplicate read-back pending.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported single Retry same operation action and resulting Expense receipt
  after reconnect, using the request preserved across the prior offline rejection.
- Data/security impact: one intentional production expense/evidence/journal/audit result was created. No
  amount, balance, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `reconnect retry expense posted once`.
- Next: verify exactly one history entry and one balance/report effect with no duplicate.

### 2026-08-23 - Accept safe offline rejection of a finance mutation

- Status: Complete for physical online-only enforcement and unchanged local/backend finance state.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported safe offline message plus confirmation that the expense did
  not post, balance/history stayed unchanged, and explicit same-operation retry remained available.
- Data/security impact: safe rejected attempt only; no amount, balance, identifier, credential, PIN,
  token, receipt, journal, expense, or outbox record was collected or intentionally created.
- Verification: physical operator report `offline finance blocked balance and history unchanged`.
- Next: reconnect and retry the same preserved finance operation once, then verify no duplicate.

### 2026-08-23 - Accept owner-scoped offline cached reads and safe refresh failure

- Status: Complete for physical cached-read availability and last-good-snapshot preservation.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that key Owner views remain available from the
  correct shop cache offline and a failed refresh does not erase authoritative cached data.
- Data/security impact: read-only offline verification; no business value, cache row, identifier,
  credential, PIN, or token was collected or intentionally mutated.
- Verification: physical operator report `offline cached reads remain and refresh failed safely`.
- Next: verify a risky finance mutation is rejected safely while offline.

### 2026-08-23 - Accept production notification/read-receipt/routing workflow

- Status: Complete for physical notification visibility, detail, read receipt, and related routing.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that an authorized Vendor return notification
  opens, marks read, updates unread state, shows Nepal-time/retention messaging, and routes to Vendors.
- Data/security impact: one intended notification read receipt was persisted. No notification body,
  amount, record identifier, credential, PIN, or token was collected.
- Verification: physical operator report `notification opened routed and read receipt correct`.
- Next: verify owner-scoped cached reads and safe offline refresh behavior.

### 2026-08-22 - Accept transfer-reversal reconciliation and reporting

- Status: Complete for physical Cash/Bank/reversal-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the transfer reversal restores both
  balances, preserves immutable history/reason, keeps the combined total constant, and leaves
  operating report metrics unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, account/journal identifier, credential, PIN, or token collected.
- Verification: physical operator report `transfer reversal balances history and report correct`.
- Next: verify authorized notifications, read receipts, and safe destination routing.

### 2026-08-22 - Accept the first controlled transfer reversal

- Status: Complete for physical Owner transfer-reversal submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported successful reversal after explicitly selecting the correct
  Transfer card; the earlier generic validation was a safe rejected attempt and created no mutation.
- Data/security impact: one intentional production compensating transfer journal/audit result was created;
  the original transfer remains preserved. No amount, balance, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `success` after correct Transfer selection.
- Next: verify restored Cash/Bank balances, reversal history, unchanged combined total, and report.

### 2026-08-22 - Accept first cash-to-bank transfer reconciliation and reporting

- Status: Complete for physical Cash/Bank/transfer-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the transfer produces exact opposing account
  changes, remains visible and reversible, preserves the combined balance, and leaves operating metrics unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, account/journal identifier, credential, PIN, or token collected.
- Verification: physical operator report `cash bank transfer balances history and report correct`.
- Next: reverse and reconcile the controlled cash-to-bank transfer.

### 2026-08-22 - Accept the first controlled production cash-to-bank transfer

- Status: Complete for physical Owner transfer submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `cash to bank transfer posted` result from main Cash to main Bank.
- Data/security impact: one intentional production transfer/journal/audit result was created. No amount,
  balance, account/journal identifier, credential, PIN, or token was collected; no external money moved.
- Verification: physical operator report `cash to bank transfer posted`.
- Next: verify opposing Cash/Bank changes, reversible history, unchanged combined total, and report.

### 2026-08-22 - Accept first cash-withdrawal reconciliation and reporting

- Status: Complete for physical cash-balance/withdrawal-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first withdrawal correctly lowers main
  Cash, remains visible and reversible, updates cash reporting, and leaves operating results unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, account/journal identifier, credential, PIN, or token collected.
- Verification: physical operator report `cash withdrawal balance history and report correct`.
- Next: post and reconcile the first controlled cash-to-bank transfer.

### 2026-08-22 - Accept the first controlled production cash withdrawal

- Status: Complete for physical Owner withdrawal submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator report `ash withdrawal posted`, interpreted as the requested
  `cash withdrawal posted`, for the main cash account.
- Data/security impact: one intentional production withdrawal/journal/audit result was created. No
  amount, balance, account/journal identifier, credential, PIN, or token was collected; no external
  money moved through the app.
- Verification: physical operator report `ash withdrawal posted`.
- Next: verify reduced cash, reversible withdrawal history, and unchanged operating report totals.

### 2026-08-22 - Accept first cash-expense reconciliation and reporting

- Status: Complete for physical cash-balance/expense-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first expense correctly lowers main
  Cash, remains visible and reversible, increases Expenses, and leaves Gross profit unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, account/journal identifier, credential, PIN, or token collected.
- Verification: physical operator report `cash expense balance history and report correct`.
- Next: post and reconcile the first controlled cash withdrawal.

### 2026-08-22 - Accept the first controlled production cash expense

- Status: Complete for physical Owner expense submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `cash expense posted` result for the main cash account and
  Launch testing category.
- Data/security impact: one intentional production expense/evidence/journal/audit result was created.
  No amount, balance, account/journal identifier, credential, PIN, or token was collected; no external
  money moved through the app.
- Verification: physical operator report `cash expense posted`.
- Next: verify reduced cash, reversible expense history, increased expenses, and unchanged gross profit.

### 2026-08-22 - Accept first cash-deposit reconciliation and reporting

- Status: Complete for physical cash-balance/deposit-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first deposit correctly raises main
  Cash, remains visible and reversible in history, updates cash reporting, and leaves operating results unchanged.
- Data/security impact: read-only verification; no additional production mutation and no amount,
  balance, account/journal identifier, credential, PIN, or token collected.
- Verification: physical operator report `cash deposit balance history and report correct`.
- Next: post and reconcile the first controlled cash expense.

### 2026-08-22 - Accept the first controlled production cash deposit

- Status: Complete for physical Owner deposit submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `cash deposit posted` result for the main cash account.
- Data/security impact: one intentional production deposit/journal/audit result was created. No amount,
  balance, account identifier, credential, PIN, or token was collected; no external money moved.
- Verification: physical operator report `cash deposit posted`.
- Next: verify increased cash, posted deposit history, and the bounded report.

### 2026-08-22 - Accept vendor-return reversal reconciliation and reporting

- Status: Complete for physical stock/vendor-due/reversal-history/cash/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the vendor-return reversal correctly
  restores FIFO stock and vendor due, preserves reversed history/reason, leaves cash unchanged, and
  adjusts the bounded report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, vendor, bill reference, identifier, credential, PIN, or token collected.
- Verification: physical operator report
  `vendor return reversal stock due history and report correct`.
- Next: post and reconcile the first controlled cash deposit.

### 2026-08-21 - Accept the first controlled vendor-return reversal

- Status: Complete for physical Owner vendor-return-reversal submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `vendor stock return reversed` result for the first
  controlled production vendor stock return using an immutable compensating reversal.
- Data/security impact: one intentional production reversal/inventory/payable/journal/audit result was
  created; the original return remains preserved. No business value, vendor, bill reference, event
  identifier, credential, PIN, or token was collected.
- Verification: physical operator report `vendor stock return reversed`.
- Next: verify restored stock, restored vendor due, reversal history, unchanged cash, and today's report.

### 2026-08-21 - Accept first vendor-return reconciliation and reporting

- Status: Complete for physical stock/vendor-due/return-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first vendor return correctly lowers
  FIFO stock and vendor due, remains visible as Posted, leaves cash unchanged, and adjusts the report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, vendor, bill reference, identifier, credential, PIN, or token collected.
- Verification: physical operator report `vendor return stock due history and report correct`.
- Next: reverse and reconcile the first controlled vendor stock return.

### 2026-08-21 - Accept the first controlled production vendor stock return

- Status: Complete for physical Owner vendor-return submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `vendor stock return posted` result for quantity one from
  the controlled purchase bill.
- Data/security impact: one intentional production vendor-return/inventory/payable/journal/audit
  result was created. No business value, vendor, bill reference, identifier, credential, PIN, or token
  was collected.
- Verification: physical operator report `vendor stock return posted`.
- Next: verify reduced stock, reduced vendor due, posted return history, and today's adjusted report.

### 2026-08-21 - Accept first vendor-payment reversal reconciliation

- Status: Complete for physical vendor-due/cash/reversal-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the compensating payment reversal
  correctly restores vendor due and cash, preserves reversed history/reason, and adjusts the report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, vendor, bill reference, identifier, credential, PIN, or token collected.
- Verification: physical operator report `vendor payment reversal due cash and report correct`.
- Next: post and reconcile one controlled vendor stock return.

### 2026-08-21 - Accept the first controlled vendor-payment reversal

- Status: Complete for physical Owner payment-reversal submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `vendor payment reversed` result for the first controlled
  production payment using an immutable compensating reversal.
- Data/security impact: one intentional production reversal/journal/audit result was created; the
  original payment remains preserved. No business value, vendor, bill reference, event identifier,
  credential, PIN, or token was collected.
- Verification: physical operator report `vendor payment reversed`.
- Next: verify restored vendor due, restored cash, reversal history, and today's adjusted report.

### 2026-08-21 - Accept first vendor-payment reconciliation and reporting

- Status: Complete for physical vendor-due/cash/payment-history/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first vendor payment correctly lowers
  vendor due and cash, remains visible in payment history, and adjusts the daily report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, vendor, bill reference, identifier, credential, PIN, or token collected.
- Verification: physical operator report `vendor due cash payment and report correct`.
- Next: reverse and reconcile the first controlled vendor payment.

### 2026-08-21 - Accept the first controlled production vendor payment

- Status: Complete for physical Owner vendor-payment submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `vendor payment posted` result for one valid cash allocation
  against the outstanding production vendor bill.
- Data/security impact: one intentional production vendor-payment/allocation/journal/audit result was
  created. No business value, vendor, bill reference, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `vendor payment posted`.
- Next: verify vendor due, cash, payment history, and today's adjusted report as Owner.

### 2026-08-21 - Accept first return reconciliation and reporting

- Status: Complete for physical stock/cash/return-state/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first sellable return correctly
  restores FIFO stock, refunds cash, closes its returnable quantity, and adjusts the daily report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, identifier, credential, PIN, or token collected.
- Verification: physical operator report `return stock cash and report correct`.
- Next: post and reconcile one controlled vendor payment.

### 2026-08-21 - Accept the first controlled production sale return

- Status: Complete for physical Owner sellable-return submission and authoritative receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `return posted` result for quantity one from the first
  controlled sale using Sellable disposition and Cash refund.
- Data/security impact: one intentional production return/refund/FIFO restoration was created. No
  business value, identifier, credential, PIN, or token was collected.
- Verification: physical operator report `return posted`.
- Next: verify restored stock, reduced cash, return status, and today's adjusted report as Owner.

### 2026-08-21 - Accept first sale reconciliation and reporting

- Status: Complete for physical stock/cash/dashboard/report read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first sale correctly affects stock,
  cash, dashboard totals, and the bounded sales/FIFO/gross-profit report.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, identifier, PIN, or token collected.
- Verification: physical operator report `sale stock cash and report correct`.
- Next: post and verify one controlled sellable sale return with cash refund.

### 2026-08-21 - Accept the first production Salesman sale

- Status: Complete for physical fully paid Salesman checkout and authoritative FIFO receipt.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `sale posted` result for the first controlled production
  sale from the purchased FIFO stock.
- Data/security impact: one intentional fully paid cash sale created sale/FIFO allocation/inventory/
  settlement/journal/audit records. No business value, identifier, PIN, or token was collected.
- Verification: physical operator report `sale posted`.
- Next: verify stock, cash, dashboard, and report read-back as Owner.

### 2026-08-21 - Accept post-purchase stock and vendor due

- Status: Complete for physical FIFO stock and vendor-due read-back.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported confirmation that the first unpaid purchase appears as
  available stock and vendor due in the production Owner views.
- Data/security impact: read-only verification; no additional production mutation and no business
  value, identifier, PIN, or token collected.
- Verification: physical operator report `stock and vendor due correct`.
- Next: post one controlled sale from the existing Salesman account.

### 2026-08-21 - Accept the first production purchase

- Status: Complete for physical purchase submission and authoritative backend completion.
- Changed: `PROJECT_STATUS.md` only; no source, schema, workflow, or configuration change.
- Behavior: records the operator-reported `Purchase posted with authoritative totals` result after
  the rc9 dialog correction and production accounting-period migration.
- Data/security impact: the intentional unpaid test purchase creates authoritative purchase/FIFO/
  inventory/payable/audit records and vendor due, with no immediate cash/bank payment. No business
  value, invoice reference, identifier, PIN, or token was collected by the agent.
- Verification: physical production success message reported by the operator; no direct row/value
  query was run.
- Next: verify stock and vendor due visually, then perform one controlled Salesman sale.

### 2026-08-21 - Correct missing initial accounting-period provisioning

- Status: Complete for implementation/CI/merge/production deployment; physical purchase retry pending.
- Changed: migration `20260821103000`, shop-provisioning pgTAP coverage, accounting/purchase policy
  documentation, and `PROJECT_STATUS.md`.
- Behavior: existing shops with no period history are backfilled once; future app-created shops
  atomically receive system accounts plus one current open accounting period. Existing configured
  periods are preserved exactly.
- Data/security impact: production now has an initial open period for every shop that previously had
  no period history. The helper is revoked from client roles; existing period history is untouched,
  and the failed physical purchase wrote no transaction records.
- Verification: pinned `pglast 8.2` parses all 50 SQL migration/test files; the changed pgTAP file
  has exactly 33 top-level tests matching `select plan(33)`; `git diff --check` passes. PR #56 run
  `32452378934` passes migration replay, deterministic seed, lint, all pgTAP suites, and concurrency
  tests in 2m1s on implementation commit `f077a181678a77af2642d733859225042134eaa7`.
  Final-head run `32452592593`, post-merge main run `32452802516`, and protected production run
  `32452999186` all pass; PR #56 merged as
  `1040316632a54765f3e40203f4a2d78d76612d25` and migration head is `20260821103000`.
- Next: retry the purchase in rc9 and verify authoritative stock/vendor-due results.

### 2026-08-20 - Generate and pin the protected rc9 Android candidate

- Status: Partial; exact-main CI/signing, artifact pinning, and guarded installer verification
  complete; physical qualification pending.
- Changed: `tools/install-release-candidate.ps1`, `docs/release-candidate-handoff.md`, and
  `PROJECT_STATUS.md`; ignored local APK and checksum sidecar refreshed to rc9.
- Behavior: the guarded installer now accepts only the exact signed rc9/code 10 candidate containing
  the keyboard-safe purchase dialog and refuses superseded or modified APK bytes.
- Data/security impact: none. No backend, account, business record, paid service, or app store was
  changed. Production credentials and the signing keystore were not displayed or copied.
- Verification: protected workflow `32395027694` passed on exact main
  `43ec93100967ff8eb3876734fac7e48f6dc75231`; independent checksum, signer, package, SDK, launcher,
  production binding, development-reference absence, and secret-marker checks pass. The pinned
  installer `VerifyOnly` command passed at `2026-08-20T23:03:17.7819568+05:45` without changing a
  device.
- Next: upgrade the physical device and retry one controlled unpaid purchase.

### 2026-08-20 - Publish the rc9 purchase-keyboard fix and pass Android CI

- Status: Partial; commit/push/PR and exact-head Android CI complete, merge/signing/device retest pending.
- Changed: `PROJECT_STATUS.md` only in this follow-up; implementation commit `97ed28e` contains the
  keyboard-safe dialog, test, version 10 release configuration, and documentation.
- Behavior: records authoritative CI acceptance of the purchase keyboard fix without changing app
  behavior or production data in this follow-up.
- Data/security impact: none. PR #55 is draft, no protected signing job ran, and no purchase was posted.
- Verification: GitHub Actions run `32393392860` passed `verify-android` in 6m47s on exact head
  `97ed28e9888e314a5b2a20af8086c2bca36277aa`; pull-request `production-release` correctly skipped.
- Next: merge exact PR #55 head, run protected rc9 signing, pin the artifact identity, then upgrade
  the physical phone and retry the controlled unpaid purchase.

### 2026-08-16 - Make purchase confirmation keyboard-safe in rc9

- Status: Partial; implementation/local gates/CI pass, merge/signing/device retest pending.
- Changed: Android manifest, shared business-date input, purchase dialog/UI test, version and release
  artifact configuration, release documentation, and `PROJECT_STATUS.md`.
- Behavior: uses a bounded keyboard-inset-aware purchase dialog with independently scrollable form,
  fixed actions, explicit **Hide keyboard**, IME Done dismissal, and activity resize behavior.
- Data/security impact: none. No purchase was submitted and no backend/schema/credential changed.
  Version advanced from rc8/code 9 to rc9/code 10; the old signed bytes are not reused.
- Verification: debug source/test compilation, debug APK assembly, three release safety scans, lint
  with zero errors/15 existing warnings, and exact-head CI run `32393392860` pass. Local Robolectric
  remains environment-limited, but the same four purchase screen tests pass in authoritative CI.
- Next: merge exact PR #55 head, generate/pin signed rc9, upgrade the physical phone, and retry the
  unpaid purchase once.

### 2026-08-16 - Record the first production test vendor

- Status: Complete for Owner vendor creation; controlled purchase/FIFO stock receipt pending.
- Changed: `PROJECT_STATUS.md` only.
- Behavior: records that the Owner vendor-creation form accepted the first clearly marked test
  vendor after the first test product was created.
- Data/security impact: one operator-requested test vendor and its protected audit evidence were
  intentionally created in production. No purchase, payment, credential, or private account value
  was collected or changed during this step.
- Verification: operator reported `Vendor created` from the physical rc8 Owner workflow. No
  independent vendor-detail query was performed.
- Next: as Owner, post one controlled unpaid purchase for the test product and vendor to establish
  its first FIFO lot without affecting cash or bank balances.

### 2026-08-16 - Record the first production test product

- Status: Complete for Owner product creation; vendor/FIFO stock setup pending.
- Changed: `PROJECT_STATUS.md` only.
- Behavior: records that the Owner product-creation form accepted the first clearly marked test
  product after the Salesman catalog correctly showed its initial empty state.
- Data/security impact: one operator-requested test product and its protected audit evidence were
  intentionally created in production. No credential or private account value was collected.
- Verification: operator reported `Product created` from the physical rc8 Owner workflow. No
  independent product-detail query was performed.
- Next: while signed in as Owner, create one clearly marked test vendor; do not post the purchase
  until the vendor creation result is confirmed.

### 2026-08-16 - Accept the empty Salesman product state

- Status: Complete for the initial Sales/New Sale zero-state.
- Changed: `PROJECT_STATUS.md` only.
- Behavior: records that the authenticated Salesman can open the sales workflow and receives an
  empty product list before the Owner creates catalog and stock records.
- Data/security impact: none. No sale or business record was submitted, and no demo, phantom, or
  cross-tenant product appeared.
- Verification: operator reported `empty product list` from Sales/New Sale on the physical rc8
  device.
- Next: log in as Owner and create one clearly marked test product; then establish stock through an
  Owner-only vendor purchase rather than a Salesman inventory mutation.

### 2026-08-16 - Accept physical Salesman role restrictions

- Status: Complete for Salesman role-navigation acceptance.
- Changed: `PROJECT_STATUS.md` only.
- Behavior: records that account and shop administration are hidden from the authenticated
  production Salesman on the physical rc8 device.
- Data/security impact: none. This was a visual authorization check; no credential, identifier,
  protected account data, or business value was requested or recorded.
- Verification: operator reported `admin options hidden` from the successful Salesman session.
- Next: open Sales/New Sale and report whether tenant-scoped products are shown or the safe empty
  state appears, without entering real customer or financial data.

### 2026-08-16 - Record successful first Salesman login

- Status: Complete for physical Salesman authentication; role-restriction acceptance pending.
- Changed: `PROJECT_STATUS.md` only.
- Behavior: records that the new production Salesman completed PIN login and Android session/role
  transition after the independently verified provisioning transaction.
- Data/security impact: none. The operator did not send, and the agent did not request, the Login ID
  or PIN. No production query or mutation was needed for this manual device milestone.
- Verification: operator reported `salesman login sucess`; prior protected run `31938915526` already
  proved the account/profile/credential/membership transaction complete with no failed/reserved row.
- Next: confirm Salesman navigation and that account/shop administration is unavailable.

### 2026-08-16 - Record verified first production Salesman

- Status: Complete for Salesman provisioning and aggregate production acceptance; Salesman login
  and role-restriction acceptance pending.
- Changed: production gained one operator-requested Salesman Auth/profile/credential/membership/
  request/audit transaction; `PROJECT_STATUS.md` records only aggregate evidence.
- Behavior: the rc8 error-classification correction now accompanies a successful physical
  Owner-to-Salesman provisioning transaction through the full idempotent server path.
- Data/security impact: one requested production Salesman was created. Read-only verification
  returned numeric counts only; no account field, PIN, token, verifier, hash, or business value.
- Verification: protected exact-main run `31938915526` passed with exactly one complete Salesman
  request/audit/active membership and zero failed/reserved Salesman requests.
- Next: sign in as the Salesman and verify the intended shop plus restricted role navigation.

### 2026-08-16 - Correct, deploy, and sign provisioning reservation classification

- Status: Complete through protected production deployment and signed artifact verification;
  physical installation/retry pending.
- Changed: `supabase/functions/manage-users/core.ts`, `index.ts`, Edge tests, Android account
  repository/test, Android release workflow, version/release scripts/docs, and
  `PROJECT_STATUS.md`.
- Behavior: uniqueness, validation, authorization, and unexpected reservation failures are no longer
  collapsed into the same 403. Android gives a specific safe next action for a duplicate Login ID.
- Data/security impact: no hosted schema/data/account mutation. Only SQLSTATE is parsed; database
  message/detail/hint values are discarded. No identity, PIN, token, verifier, or secret is logged.
- Verification: Edge format/lint/type-check and 30 tests pass; the complete Android release gate
  passes in 5m51s with 186 tests, no failures/errors/skips, zero lint errors/15 existing warnings,
  all release safety scans, and debug assembly. PR #46 CI, exact-head merge, production deployment,
  aggregate reconciliation, exact-main protected signing, sidecar/hash/signature/package/version/
  SDK/launcher/environment verification, and checksum-pinned installer `VerifyOnly` all pass.
- Next: deliver/install exact rc8 and retry Owner-to-Salesman creation with a unique Login ID.

### 2026-08-16 - Deliver verified rc7 through MTP

- Status: Complete for phone delivery; manual Android installation pending.
- Changed: added the exact signed rc7 APK to Redmi 15 Download and updated `PROJECT_STATUS.md`.
- Behavior: the replacement is available on the phone despite the missing ADB interface.
- Data/security impact: no overwrite, installation, app-data clearing, credential access, or
  production mutation. The temporary local read-back was removed after verification.
- Verification: phone-to-PC read-back matches 57,427,993 bytes and approved SHA-256
  `52BF20F3F83D7AD8E39A2B84CD1090DF7D2934E071032B011650A40A927992FC`.
- Next: manually install rc7, sign in as Owner, and retry Salesman creation once.

### 2026-08-16 - Bind protected account mutations to the expected Auth subject

- Status: Implementation, local/CI verification, merge, aggregate diagnosis, signing, and artifact
  verification complete; physical rc7 retry pending.
- Changed: remote refresh contract/executor, production dependency graph, account remote interface,
  repository/test fakes, reconciliation SQL, Android version/release workflow/scripts/docs, and
  `PROJECT_STATUS.md`.
- Behavior: protected account operations refresh and verify the hosted subject matches the Android
  actor before sending; mismatched prior-user sessions fail as re-login-required rather than reaching
  server authorization. Reconciliation can now prove Salesman authority and partial-request state.
- Data/security impact: no schema, Function, production user, or business-data mutation. Diagnostic
  output remains numeric aggregates only.
- Verification: focused account/remote tests pass; the complete release gate passes 185 tests,
  zero failures/errors/skips, zero lint errors/15 existing warnings, every release safety task, and
  debug assembly. PR #43 passes both CI gates; reconciliation run `31932921133` proves valid Owner
  authority and no Salesman request; protected run `31933084512` produces the verified exact APK.
  The checksum-pinned installer `VerifyOnly` passes against the root handoff copy.
- Next: verify/install rc7 and retry Salesman creation once from a fresh Owner session.

### 2026-08-16 - Record verified first production Owner

- Status: Complete for rc6 installation, Owner provisioning, and aggregate production acceptance;
  Owner login and tenant-scope acceptance pending.
- Changed: production gained one Owner Auth/profile/credential/membership/request/audit transaction;
  `PROJECT_STATUS.md` records only its aggregate outcome.
- Behavior: the rc6 protected-session correction now succeeds from the physical app through the
  complete idempotent Owner provisioning path.
- Data/security impact: one operator-requested production Owner was created. Read-only verification
  returned numeric counts only; no account field, credential, PIN, token, hash, or business value.
- Verification: protected exact-main workflow run `31930685338` passed with 2 Auth/profile/credential
  rows, 1 completed Owner request/audit/active membership, and zero failed/reserved requests.
- Next: verify Owner login and intended-shop isolation, then create/test one Salesman.

### 2026-08-15 - Refresh protected account sessions and sign rc6

- Status: Implementation, verification, merge, protected signing, artifact pinning complete;
  controlled phone installation and Owner retest pending.
- Changed: remote call/account repositories and tests, Android version/release workflow and scripts,
  account/release documentation, ignored signed rc6 APK, and `PROJECT_STATUS.md`.
- Behavior: Owner/Salesman administration establishes a fresh hosted session before the protected
  Edge call; session rejection and authority denial are distinct; committed mutations are not
  misreported as failed when only list refresh is pending. The installer accepts only the exact
  verified rc6 bytes and approved production signer.
- Data/security impact: no schema, Function, production user, Owner, shop, or business-data mutation.
  Reconciliation was aggregate-only; signing credentials remained protected.
- Verification: 184 local tests plus lint/safety/build gate passed; PR #40 and exact merged main CI
  passed; protected run `31870004171` passed; checksum, signer, package/version, SDK, launcher, and
  production-only Supabase binding independently pass. The pinned installer `VerifyOnly` also
  passes against the root handoff APK.
- Next: verify/install rc6 on the phone, establish a new Super Admin login session, retry Owner
  creation once, and reconcile the complete Auth/profile/credential/membership/request/audit state.

### 2026-08-12 - Align account form validation and visible submission state

- Status: Implementation, local/CI verification, merge, protected signing, and immutable artifact
  verification complete; physical retest in progress.
- Changed: account-management Compose form/tests, Android version/release references, and
  `PROJECT_STATUS.md`.
- Behavior: Login IDs outside the hosted 3–64-character lowercase contract cannot be submitted;
  the form displays the rule, remains open during the request, displays safe failures in context,
  and closes only after the audited creation success message. The account page exposes the installed
  app version for direct device confirmation.
- Data/security impact: no production account/business row changed. Aggregate diagnosis contained no
  private identity or credential field.
- Verification: diff check, focused account form/repository tests, and the full rc5 gate pass: 180
  tests, zero failures/errors/skips, zero lint errors/15 existing warnings, source/artifact safety,
  and debug assembly. PR/main CI and protected run `31617468852` pass; the verified rc5 identity is
  recorded above.
- Next: build/sign/install rc5 and repeat the Owner acceptance gate.

### 2026-08-12 - Correct Android account-management JSON transport

- Status: Implementation, clean verification, merge, protected signing, and immutable artifact
  verification complete; physical retest in progress.
- Changed: `AccountRemoteDataSource.kt`, its account repository regression test, Android version,
  release build/install/workflow references, release documentation, and `PROJECT_STATUS.md`.
- Behavior: `manage-users` and `manage-accounts` invocations now explicitly declare JSON, matching
  their strict hosted request contract and the already-correct rc3 PIN-login transport.
- Data/security impact: no Owner or business row was created by the failed phone submission. The
  diagnostic used aggregate counts only and did not read identity or credential values.
- Verification: focused account tests and the full local release-quality gate pass: 179 tests, zero
  failures/errors/skips, zero lint errors/15 existing warnings, release source/artifact safety, and
  debug assembly. PR/main CI passed; protected run `31592190757` produced the verified rc4 artifact
  identified above.
- Next: build/sign/verify/install rc4 and repeat the first Owner acceptance gate.

### 2026-08-12 - Confirm rc3 physical login and reconcile first production shop

- Status: Complete for production authentication and initial shop setup.
- Changed: production authentication/session state, one protected shop creation transaction with its
  11 system accounts/audit/request rows, and `PROJECT_STATUS.md`; no schema, Function, or source code
  changed.
- Behavior: the signed physical app can now authenticate the bootstrap Super Admin and create the
  authoritative first shop on production.
- Data/security impact: created the operator-entered shop and required accounting foundation. No
  credential value, session, shop field, or identifier was retrieved into status/log output.
- Verification: aggregate management reconciliation proves successful unlocked auth state, exactly
  one active/completed/audited shop, exactly 11 protected account purposes, and zero downstream
  membership/product/sale/journal data.
- Next: create and verify the first Owner account and continue the physical core-workflow matrix.

### 2026-08-12 - Correct Android PIN-login JSON transport and sign rc3

- Status: Complete for implementation/build/signing; physical installation/login retest pending.
- Changed: `app/build.gradle.kts`, `SupabaseAuthDataSources.kt`,
  `ProductionAuthRepository.kt`, `ProductionAuthRepositoryTest.kt`, `build-production-apk.ps1`,
  `tools/install-release-candidate.ps1`, Android release workflow/docs, `README.md`, signed ignored
  rc3 APK, and `PROJECT_STATUS.md`.
- Behavior: Android now satisfies the strict production JSON request contract; malformed-client
  HTTP 400 directs the operator to update instead of reporting incorrect credentials. Production
  signing consumes protected environment Supabase configuration even when ignored development
  Gradle properties exist.
- Data/security impact: no real account, PIN, session, schema, Function, shop, or business data
  changed. One random non-account valid-shape probe incremented only generic/source defensive state;
  all random values were discarded. Signing/key inputs stayed in Credential Manager/process memory
  and were cleared from the launcher environment.
- Verification: missing-header 400 versus explicit-JSON 401 production proof; focused auth tests;
  178-test clean gate; zero-error lint; signature/package/version/backend/hash verification; rc3
  installer `VerifyOnly` pass. Two MTP delivery attempts failed without changing phone contents.
- Next: place `GDAD-BAGS-0.2.0-rc3-4-release.apk` in the unlocked phone's Download folder, install
  version `0.2.0-rc3`, sign in with the same production values, then create/verify the initial shop.

### 2026-08-12 - Prove production credentials and isolate the phone package mismatch

- Status: Complete for backend authentication diagnosis; physical reinstall/smoke test pending.
- Changed: two bounded masked production logins, transient local diagnostic tooling (deleted), and
  `PROJECT_STATUS.md`; no application, database schema, Function, or business-data source changed.
- Behavior: the operator's exact remembered ID/PIN now has end-to-end evidence across PIN verifier,
  Supabase Auth, and authenticated RLS. The phone rejection is isolated to its installed app/build
  or device network path; the approved APK itself is production-configured and cryptographically
  intact.
- Data/security impact: successful authentication created short-lived server sessions that are no
  longer possessed locally; credential counters remain successful/unlocked. No secret or account
  value was printed, saved, or committed, and no shop/business data was created.
- Verification: complete masked flow HTTP 200/200/200; release `VerifyOnly` pass; ADB inventory empty;
  ignored verifier/result deletion confirmed before handoff.
- Next: replace the phone app with `GDAD-BAGS-0.2.0-rc3-4-release.apk`, sign in with the same values,
  then create and verify the initial production shop.

### 2026-08-12 — Isolate phone login validation from backend credentials

- Status: Diagnostic complete; real phone login remains pending correct operator input.
- Changed: one bounded unknown-user PIN-login diagnostic request, operational rate-limit work, and
  `PROJECT_STATUS.md`; no application/backend source changed.
- Behavior: production validates app-shaped requests and returns the intended generic credential
  denial. The real Super Admin remains enabled and untouched with zero failed attempts/no lockout.
- Data/security impact: no real login ID, PIN, session, account row, shop, or business data changed;
  random diagnostic values were discarded and no secret value was logged.
- Verification: diagnostic HTTP 401 `INVALID_CREDENTIALS`; aggregate failure/lockout snapshot above.
- Next: enter the exact 6–8-digit production PIN on the phone, then create/verify the initial shop.

### 2026-08-12 — Deliver signed APK through verified MTP fallback

- Status: Complete for artifact delivery; installation/physical acceptance still requires phone UI.
- Changed: Redmi 15 `Download/GDAD-BAGS-0.2.0-rc2-3-release.apk` and `PROJECT_STATUS.md`.
- Behavior: the exact signed rc2 candidate is available on the connected phone for manual package
  installation even though its debugging interface is not exposed.
- Data/security impact: added one verified APK file to the phone Download folder; did not install,
  replace, uninstall, or clear any app, and deleted the temporary local read-back copy.
- Verification: phone-to-PC MTP read-back matched 57,411,609 bytes and approved SHA-256 exactly.
- Next: tap/install the APK on the phone, enable ADB if possible, and execute the device matrix.

### 2026-08-12 — Fail closed when ADB discovery hangs

- Status: Complete and verified locally.
- Changed: `tools/install-release-candidate.ps1` and `PROJECT_STATUS.md`.
- Behavior: physical release installation now has explicit native-process timeouts and captured
  output; an unavailable/misconfigured debugging interface returns a safe actionable failure.
- Data/security impact: none. No device driver, APK, app data, or production data was changed.
- Verification: parser passes; exact APK `VerifyOnly` passes; MTP-only `Fresh` fails closed in 1.40
  seconds without installation. ADB discovery no longer hangs in this state.
- Next: enable USB debugging/authorization on the detected Redmi 15, then install and test the APK.

### 2026-08-12 — Reverify signed candidate and external launch blockers

- Status: Complete for all non-device checks; blocked only on owner-controlled external resources.
- Changed: `PROJECT_STATUS.md` only. No app/backend/configuration source changed.
- Behavior: the exact signed production APK remains approved for controlled device qualification;
  Windows currently exposes no Android phone, so installation and the physical matrix did not run.
- Data/security impact: none. Only file metadata, signature/package metadata, USB device names, and
  Credential Manager target names were inspected; no secret values or device/app data were accessed.
- Verification: fail-closed release-candidate `VerifyOnly` passed with the approved APK/certificate;
  connected-device enumeration returned no Android/ADB/MTP device; ADB daemon cleanup completed.
- Next: connect and authorize one Android 12+ phone, and designate an independently recoverable
  owner secret store/age public recipient for the recovery package.

### 2026-08-12 — Accept accessible full-phrase production confirmation

- Status: Complete; local tooling and controlled production bootstrap pass.
- Changed: `tools/bootstrap-production-superadmin.ps1` and `PROJECT_STATUS.md`.
- Behavior: the explicit confirmation remains mandatory, while harmless leading/trailing whitespace
  and capitalization differences no longer cause a mismatch. The full two-word phrase is still
  required and mismatches remain local/non-mutating.
- Data/security impact: created the sole intended production Auth/profile/PIN-credential identity and
  its immutable account audit event; no shop or business data was created. Confirmation, PIN,
  bootstrap token, session, and account fields were cleared/not logged.
- Verification: parser/static accessible-confirmation contract, no-bypass assertion,
  forbidden-development fail-closed child-process self-test, and `git diff --check` pass. Controlled
  bootstrap/PIN-login subject verification, independent exact counts, secret cleanup, and production
  health probes pass.
- Next: preserve recovery material off-PC and execute the physical-device release matrix.

### 2026-08-11 — Reject and remove automatic production confirmation

- Status: Complete safety reversion; production bootstrap remains pending explicit operator action.
- Changed: `tools/bootstrap-production-superadmin.ps1` was temporarily evaluated with an explicit
  launcher switch, then restored; `PROJECT_STATUS.md` records the decision.
- Behavior: the helper retains its secure masked input and exact interactive `CREATE PRODUCTION`
  confirmation loop. No launcher may automatically confirm production creation.
- Data/security impact: the proposed production command was rejected before execution. No hosted
  mutation, credential exposure, one-time secret, or account was created.
- Verification: final parser/static and `git diff --check` pass; earlier proposed-switch self-test
  touched only the known forbidden development ref and failed closed before network mutation.
- Next: receive explicit informed approval if the operator wants removal of the confirmation gate, or
  complete the exact phrase locally in the retained safer flow.

### 2026-08-11 — Retry incorrect production confirmation safely

- Status: Partial until local verification and the controlled production bootstrap complete.
- Changed: `tools/bootstrap-production-superadmin.ps1` and `PROJECT_STATUS.md`.
- Behavior: a mistyped or case-mismatched final confirmation clears the in-memory PIN, explains the
  exact required phrase, and restarts masked entry without contacting Supabase.
- Data/security impact: no hosted mutation; the previously reported cancellation happened before
  the bootstrap secret-install/request stages and logged no credential or session value.
- Verification: parser/static confirmation-recovery contract, fail-closed child-process self-test,
  and `git diff --check` pass. The controlled retry was externally closed/interrupted before a safe
  result was written; management reconciliation afterward confirmed zero production users/profiles/
  Super Admins/shops and no one-time bootstrap secret.
- Next: verify locally, complete the masked production bootstrap, and reconcile the initial account.

### 2026-08-11 — Harden and safely diagnose masked production bootstrap input

- Status: Tooling complete and verified; production account creation remains pending exact operator
  confirmation.
- Changed: `tools/bootstrap-production-superadmin.ps1` and `PROJECT_STATUS.md`.
- Behavior: invalid login ID, display name, or PIN now shows its safe local rule and restarts the
  masked entry loop; rejected PIN strings are cleared before retry. Hosted calls remain gated behind
  valid input and the exact `CREATE PRODUCTION` confirmation.
- Data/security impact: no hosted mutation from this source change. Read-only reconciliation proved
  production still has zero Auth users/profiles/Super Admins/shops, and the one-time bootstrap secret
  is absent after both earlier unsuccessful prompts.
- Verification: PowerShell parser and retry-loop/static security contract pass. The sanitized result
  schema contains no login, PIN, token, key, or session field. The controlled diagnostic run ended
  `Cancelled before hosted change.` and therefore made no bootstrap request. A final read-only
  management query and secret-name check confirmed zero users/profiles/Super Admins/shops and no
  one-time bootstrap secret.
- Local fail-closed runtime verification: a child PowerShell process targeting the known development
  ref exited 1 with the curated rejection, wrote only `status`, `stage`, `message`,
  `correlation_id`, and `recorded_at_utc`, and the temporary result was deleted. Final parser and
  `git diff --check` checks pass.
- Next: rerun the masked helper and type exact `CREATE PRODUCTION`, then reconcile one enabled Super
  Admin, verified PIN login, and absent one-time secret.

### 2026-08-11 — Prove uninterrupted production-backup RPO/RTO

- Status: Complete — functional recovery, 24-hour RPO, and four-hour operator RTO all pass.
- Changed: protected read-only backup run/artifact, disposable Supabase target/secrets/Functions/
  identity (all destroyed), development pause/resume state, `docs/operations-runbook.md`, and
  `PROJECT_STATUS.md`. Production was read-only and unchanged.
- Behavior: a fresh encrypted production export restores schema/data/history to an isolated target;
  full lint, 723-assertion pgTAP, reconciliation, concurrency, bootstrap, PIN-login, and authenticated
  RLS checks complete from clean start in `01:09:51.5740491`, with RPO `00:16:29.5651536`.
- Data/security impact: only random disposable fixtures/credentials/secrets were created. The target,
  credential, one-time secret, sessions, and plaintext are gone; only verified ciphertext remains.
- Verification: protected backup run `31428910379`; SHA-256/outer/inner manifests; PostgreSQL 17.10;
  Supabase CLI 2.111.0 lint; 21 plans/723 assertions; 74-table reconciliation; concurrency harness;
  bootstrap 201; login 200; one enabled RLS profile; normal-state audit with both persistent projects
  healthy and only development linked.
- Next: independently recoverable secret copies, owner notification/daily approval handling,
  production Super Admin bootstrap, physical-device acceptance, and staged-release approval.

### 2026-08-10 — Execute isolated encrypted production-backup restore drill

- Status: Functional recovery and 24-hour RPO pass; four-hour wall-clock RTO not proven because the
  operator run was interrupted for nearly seven days. A timed rerun remains.
- Changed: disposable Supabase project/secrets/Functions/test identity (all destroyed), development
  pause/resume state, `docs/operations-runbook.md`, and `PROJECT_STATUS.md`. Production was read-only.
- Behavior: the verified logical backup restores complete schema/data/history; target-default ACLs
  are normalized to source ACL evidence; full database/concurrency/application identity checks pass;
  cleanup leaves no target, drill credential, plaintext, bootstrap token, PIN, or session.
- Defects/retest: fixed native Credential Manager persistence, fresh-target ACL inheritance, TAP
  whitespace parsing, Windows SQL/JSON transport/result counting, concurrency-fixture bootstrap
  interference, body-bearing GET, and UTF-8 CLI-token decoding. Every backend defect candidate was
  isolated from a local drill-wrapper/procedure issue and retested to pass.
- Security/data impact: source production had zero cataloged rows and was never mutated. Only random
  disposable fixtures/identities/secrets were created; production secrets/users/PINs were not used.
- Verification: PostgreSQL client 17.10, Supabase CLI 2.111.0, 723 pgTAP assertions, lint, 13 named
  reconciliation/invariant outcomes, bootstrap 201, login 200, one RLS profile, target deletion,
  credential/plaintext deletion, development/production healthy, and `git diff --check`.
- Next: preserve off-PC recovery/signing material, repeat this corrected drill without interruption,
  bootstrap production with a private masked PIN, and finish physical-device/staged-release gates.

### 2026-08-03 — Verify post-migration encrypted recovery input
- Status: Complete for fresh encrypted export and local cryptographic/manifest validation; not a
  substitute for the required isolated-target restore drill.
- Changed: protected scheduled backup approval, ignored local ciphertext/tooling, and
  `PROJECT_STATUS.md` only.
- Behavior: the first backup after initial-shop migration is independently decryptable and binds
  to the current production project, migration head, and source commit.
- Data/security impact: read-only production export; only ciphertext was uploaded. The identity
  remained in Credential Manager/process memory, and temporary plaintext was deleted.
- Verification: run `30767417848`, artifact `8848237493`, ciphertext hash above, exact outer/inner
  allow-lists, five manifest hashes/sizes, current health run `30768291677`, and plaintext cleanup
  all pass.
- Next: place recovery material off-PC, confirm an exact disposable restore target, execute and
  destroy the isolated drill, then perform masked production bootstrap and physical-device tests.

### 2026-08-02 — Deploy initial-shop provisioning and sign rc2
- Status: Complete for reviewed source, development/production backend deployment, and signed APK
  creation/verification. Owner-held recovery, production bootstrap, physical-device qualification,
  and staged distribution remain external launch gates.
- Changed: hosted development and production schema, protected production Edge deployments,
  ignored local rc2 APK, release-candidate installer/handoff/build documentation, and status.
- Behavior: a clean production Super Admin can now create the initial shop through the protected
  exactly-idempotent RPC/UI path; successful creation atomically provisions 11 financial accounts.
- Data/security impact: no user, shop, or business data was created. Production deployment used the
  protected exact-commit workflow and secret-safe probes. The APK remains local/protected and was
  not published to a store or GitHub Release.
- Verification: development history/lint pass; production run `30758557549` passes every gate;
  Android run `30758725027` passes verification and protected signing; independent hash,
  signature/certificate, package/version/SDK/label/activity/icon checks and the fail-closed
  installer all pass for rc2 SHA-256
  `E63E96ACECFD7D410802E3D371101BD6BB4FBFDC1DDDBC0E29366803230327FC`.
- Next: preserve independent recovery copies, perform the isolated restore drill, bootstrap the
  production Super Admin with a private PIN, and execute the device acceptance matrix.

### 2026-08-02 — Implement protected initial-shop creation
- Status: Complete in source, clean CI, and hosted development. Production deployment and signed
  rc2 verification remain.
- Changed: forward migration and 25-assertion pgTAP; account domain/remote/repository/ViewModel/UI
  and tests; Android version/release tooling; account/data/authorization/release documentation;
  superseded-candidate guard; README and `PROJECT_STATUS.md`.
- Behavior: an active Super Admin with an empty directory can create a normalized shop through
  `create_shop`. The operation derives authority from `auth.uid()`, preserves one UUID across retry,
  creates the shop plus all 11 system financial accounts and one safe immutable audit atomically,
  refreshes Room, and then enables Owner creation. Owner, Salesman, disabled/unknown subjects,
  malformed fields, changed retries, duplicate slugs, and direct table writes fail closed.
- Data/security impact: repository/local build only. The new private request ledger is RLS-enabled
  and unreadable to clients; the public RPC is executable only by authenticated sessions and
  independently proves active Super Admin authority. No secret, hosted data, or user was created.
  rc1/version 2 is visibly superseded and its installer refuses it while source targets rc2/3.
- Verification: SQL parses with a matching 25-test plan; focused Android tests pass 13/13; the full
  Android suite passes 176/176 across 47 suites; all release safety/artifact gates and both APK
  assemblies pass; lint reports zero errors/10 warnings; debug and unsigned-release hashes are
  recorded above. Database run `30757887695` passed all pre-pgTAP gates and 722/723 assertions; the
  remaining failure was only the corrected private-table denial message expectation. Replacement
  runs `30758156914` and `30758156973` are green; hosted development history/lint are verified.
- Next: deploy the reviewed migration through the protected production workflow and build/verify
  signed rc2.

### 2026-08-02 — Add controlled release-candidate installation and handoff
- Status: Complete for verification/install tooling and handoff documentation; physical execution
  remains pending an attached supported phone and production test identities.
- Changed: `tools/install-release-candidate.ps1`, `docs/release-candidate-handoff.md`,
  `docs/release-build.md`, and `PROJECT_STATUS.md`.
- Behavior: verifies the immutable APK hash, signer certificate, package, version, SDK, and activity
  before any device operation. Verify-only is the default. Fresh mode refuses to overwrite an
  installed package; Upgrade mode refuses a missing predecessor; neither mode uninstalls or clears
  local data. The handoff defines role/core/offline/upgrade/accessibility/performance/tenant-purge
  evidence, incident response, forward-only rollback, and final staged-distribution requirements.
- Data/security impact: none. No device was attached, no APK or secret was added to Git, and no
  production user/data or distribution state changed.
- Verification: verify-only mode passed against the 57,395,229-byte signed candidate after the
  script selected the bundled JDK for `apksigner`; `git diff --check` passed. Bundled ADB reported no
  devices, confirming installation remains an external gate. PR #18 merged as `addfe9e2`; the
  subsequent current-main read-only production health run `30755813345` passed every probe.
- Next: connect and authorize one Android 12+ phone, run Fresh and Upgrade qualification using the
  exact candidate, then record the complete matrix and performance JSON.

### 2026-08-02 — Create and verify signed production APK
- Status: Complete for signing inputs and the signed clean gate; physical-device qualification and
  independent signing-key recovery remain.
- Changed: ignored local `.tooling/release` signing material, Windows Credential Manager entries,
  protected GitHub `production` environment secrets, generated signed APK/checksum, `.gitignore`,
  and `PROJECT_STATUS.md`. No signing value is recorded in tracked content.
- Behavior: production builds fail closed unless approved signing and non-development Supabase
  inputs exist. The protected workflow built `0.2.0-rc1`/2 from exact main and uploaded a signed
  artifact for controlled testing without creating a public release or store submission.
- Data/security impact: generated signing-key possession enables compatible future updates. The
  keystore is ignored under `.tooling/release`; passwords and alias are in Windows Credential
  Manager; protected GitHub environment secrets hold the CI copies. No user, business row, backend
  schema, paid service, GitHub Release, or app-store state changed. The root APK and checksum are
  local ignored handoff artifacts.
- Verification: protected run `30754590770` passed both jobs. Independent checksum, Signature
  Scheme v2, exact certificate, package/version/SDK/label/activity/icons, production-ref, and exact
  secret-scan checks passed for the 57,395,229-byte APK with SHA-256
  `DBDD6D9B079E41DFD03E332E6D163228A75D102DE99025017D2F4B6331339C82`.
- Next: create an independently recoverable owner-controlled copy of the keystore/passwords, then
  install this exact candidate on a supported physical Android device and complete Task 7.3.

### 2026-08-02 — Add protected encrypted production logical exports
- Status: Complete for workflow activation and first encrypted export; independent identity
  recovery and an isolated restore drill remain before Task 6.5 can close.
- Changed: production backup workflow, ignore rules, operations runbook, and `PROJECT_STATUS.md`.
- Behavior: a daily schedule or confirmed manual dispatch waits for the existing protected
  `production` environment approval, creates the five official roles/schema/data/migration-history
  dumps, authenticates them inside an `age` archive, removes plaintext, and retains only encrypted
  daily/weekly artifacts plus safe checksum/catalog metadata.
- Data/security impact: reads production through the existing access token/database password but
  performs no database, user, business, Auth, Function, plan, or add-on mutation. The decryption
  identity is structurally excluded from GitHub and artifacts. No paid backup/PITR/log/alert service
  is enabled.
- Verification: pinned age v1.3.1 Linux and Windows archives matched official SHA-256 values; Linux
  layout and Supabase CLI 2.111.0 dump flags pass. A fresh age identity passed private/public shape
  validation and was written only to Credential Manager target
  `GDAD_BACKUP_AGE_IDENTITY_skfxfbssfeetquteubcn`; GitHub stores only repository variable
  `GDAD_BACKUP_AGE_RECIPIENT`. After explicit owner risk acceptance, PR #15 merged as `5fdc7d7`.
  Protected run `30750734823` passed in 3m12s and uploaded artifact
  `gdad-production-daily-20260802T134937Z` (87,239 artifact bytes; expires
  `2026-08-10T13:52:41Z`). Independent verification accepted exactly ciphertext/checksum/catalog,
  matched the 86,087-byte ciphertext SHA-256, decrypted with the local-only identity, and matched
  all five SQL files to the authenticated manifest at migration head
  `20260729170000_bounded_report_detail_windows`. The first strict tar check omitted the harmless
  root `./` entry; adding only that directory entry passed. Both attempts deleted all temporary
  plaintext and identity files. No paid service or production mutation was enabled.
- Next: place an independently recoverable identity copy in the owner's separate secret store,
  confirm owner notifications, then perform and record the isolated non-production restore drill.

### 2026-08-02 — Add free scheduled production health checks
- Status: Complete for the zero-cost health-monitoring increment; the six-hour schedule is active.
- Changed: `tools/Test-ProductionHealth.ps1`, scheduled GitHub workflow, operations runbook, and
  `PROJECT_STATUS.md`.
- Behavior: every six hours or on demand, checks production Auth/Data API availability and the
  documented non-mutating error boundary for all three Edge Functions. GitHub workflow failure
  notification is the zero-cost alert signal.
- Data/security impact: read-only/non-mutating requests only. The workflow accepts only project
  `skfxfbssfeetquteubcn` and a client-safe `sb_publishable_` key; it has no database password,
  service-role key, access token, PIN material, user session, business payload, or paid dependency.
- Verification: PowerShell AST and `git diff --check` pass. The complete live run passed Auth health
  `200`, the intentional secret-key-only Data API boundary `401`, malformed `pin-login` and
  `manage-users` contracts `400 INVALID_REQUEST`, and valid-shaped unauthenticated
  `manage-accounts` `401 UNAUTHORIZED`, including no-store/correlation headers. No privileged
  credential or mutation was used. Repository secret listing confirms exactly the expected
  `SUPABASE_PRODUCTION_HEALTH_PUBLISHABLE_KEY` name without exposing its value. PR #13 merged the
  workflow as `ccc63df`. Runs `30739803119` and `30739876284` failed safely before any request
  because Windows-to-native secret transfer produced a noncanonical value. GitHub CLI direct secret
  encryption corrected the transfer; run `30739922795` then passed all hosted probes. The local
  guard also trims surrounding transport whitespace before enforcing the `sb_publishable_` shape.
- Next: implement the encrypted daily logical-export workflow and recovery-key handling without a
  paid service, then perform an isolated restore drill.

### 2026-08-02 — Correct production account-authentication smoke probe
- Status: Complete; merged through PR #11 and verified by a fully green protected production run.
- Changed: production deployment workflow and `PROJECT_STATUS.md`.
- Behavior: the protected account probe now reaches the authentication boundary with a valid-shaped
  request and proves missing Authorization returns `401 UNAUTHORIZED`, no-store, and a correlation
  ID. Malformed-request behavior remains separately verified as `400 INVALID_REQUEST`.
- Data/security impact: no migration, Function code, secret, user, PIN, token, or business row
  changes in this correction. Prior run `30738097255` already deployed the reviewed schema/secrets/
  Functions; the probe request cannot reach an RPC without an authenticated subject.
- Verification: direct redacted hosted probes pass the malformed contract for all three Functions;
  the exact corrected valid-shaped `manage-accounts` probe returns `401 UNAUTHORIZED`, no-store,
  and a valid correlation ID. `git diff --check` passes. Main commit `7b42263` protected run
  `30739060872` passed every step in 2m25s, including clean linked database verification and all
  final probes.
- Next: implement the approved zero-cost logical-export/notification operations gate and record an
  isolated restore drill before real business transactions.

### 2026-08-02 — Configure protected GitHub production environment
- Status: Complete for environment and encrypted secret registration.
- Changed: GitHub environment `production`, seven environment secret records, production deployment
  guide, and `PROJECT_STATUS.md`.
- Behavior: production workflows require `sanjubaba21` approval and can deploy only from `main`;
  the deployment workflow can now resolve its complete protected input contract.
- Data/security impact: seven encrypted secret values were transferred directly from secure local
  stores to GitHub. Values were not printed, committed, or returned by verification. No Supabase
  migration, Function, Auth identity, bootstrap token, or application row changed.
- Verification: environment API shows the required reviewer and custom branch restriction; secret
  listing returns exactly the seven expected names.
- Next: merge the reviewed branch to `main`, require fresh main Android/database gates, then dispatch
  and approve the production Supabase deployment workflow.

### 2026-08-02 — Create production Supabase project and secure deployment inputs
- Status: Partial for the production backend; project/input creation complete.
- Changed: hosted Supabase project inventory, Windows Credential Manager, production deployment
  guide, and `PROJECT_STATUS.md`.
- Behavior: production now has a distinct healthy Seoul target and project-scoped local deployment
  inputs. Development remains isolated and rejected by production tooling.
- Data/security impact: created potentially billable project `skfxfbssfeetquteubcn`; no migration,
  Edge Function, Auth identity, application row, bootstrap token, or real transaction was created.
  Five values are stored locally without disclosure; none is committed.
- Verification: Supabase creation/listing returned `ACTIVE_HEALTHY`; safe API-key metadata showed
  exactly one `sb_publishable_` key; the canonical Argon2id helper passed a non-secret probe; all
  five Credential Manager writes returned success.
- Next: create and protect the GitHub `production` environment, register these values as environment
  secrets, then run the reviewed deployment workflow.

### 2026-08-02 — Make the Gradle wrapper executable in Linux CI
- Status: Complete.
- Changed: tracked file mode for `gradlew` and `PROJECT_STATUS.md`.
- Behavior: Ubuntu GitHub Actions can invoke the same checked-in Gradle wrapper used by the Android
  release workflow. No Gradle command, dependency, or safety gate changed.
- Data/security impact: none; file-mode metadata and status evidence only.
- Verification: the tracked mode is `100755`. Replacement Android run `30733686252`, job
  `91458353947`, completed successfully through checkout, Java 17, Gradle setup, and the complete
  Android release gate.
- Next: retain executable wrapper metadata and require this gate for every release candidate.

### 2026-08-02 — Fix account pgTAP role cleanup without widening grants
- Status: Complete.
- Changed: account administration/provisioning pgTAP files and `PROJECT_STATUS.md`.
- Behavior: each test resets from `service_role` to the test administrator immediately after the
  protected RPC result, before direct state/audit inspection. Production RPC behavior is unchanged.
- Data/security impact: no migration, grant, RLS, hosted data, function, or Android change. The fix
  preserves the deliberate absence of direct `service_role` table privileges.
- Verification: run `30733270253` passed migration replay, deterministic seed, Edge checks, and lint;
  18/20 pgTAP files passed. The only errors were permission denial at the two corrected direct
  `user_profiles` reads; no pgTAP assertion failed before those statements. Replacement run
  `30733408305` passed account administration and 19/20 files/696 assertions, then identified the
  last equivalent `shop_memberships` inspection. Final replacement run `30733686244`, job
  `91458333358`, passed all Edge checks, migration/seed replay, database lint, every pgTAP suite,
  and backend integration/concurrency tests.
- Next: preserve the service-role block audit and direct-table privilege boundary in future tests.

### 2026-08-02 — Add fail-closed production Supabase deployment automation
- Status: Complete for repository automation and static verification; external production inputs
  and hosted execution remain.
- Changed: `.github/workflows/supabase-production-deploy.yml`,
  `tools/bootstrap-production-superadmin.ps1`, `docs/production-deployment.md`, README, and status.
- Behavior: a manually confirmed, protected `production` job can deploy only to the separately
  registered production ref after replaying the full backend gate. Development targeting, missing
  or malformed protected inputs, failed dry-run/migration/test/lint, or failed redacted probes stop
  the job. Account bootstrap remains a separate one-time reviewed operation.
- Data/security impact: none in this increment. Read-only CLI inspection found no production
  project; no project, migration, function, secret, Auth user, PIN, session, backup, or alert changed.
- Verification: pinned CLI help confirms the workflow's `db push --dry-run`, `secrets set`, and
  linked lint flags. PowerShell AST and diff checks pass. Three isolated negative helper runs reject
  the development ref, mismatched URL, and malformed publishable key before network/hosted changes.
  Initial YAML structure parsing passed; portable `grep` input guards replaced parser-dependent
  Bash conditionals/pattern cases. Final parsing reports valid workflow YAML and all 11 embedded
  Bash scripts valid. Secret-boundary hardening prevents malformed response/session JSON fragments
  from reaching terminal errors and clears live response/session objects during cleanup. Local Edge
  formatting/lint/type-check and 29/29 tests pass; `git diff --check` remains clean.
- Next: create/configure the operator-owned production project and protected environment, run the
  deployment/restore/bootstrap gates, then build and physically verify the signed candidate.

### 2026-08-01 — Add fail-closed production signing and CI foundation
- Status: Complete for source/configuration and automated verification; external production and
  physical-device launch gates remain.
- Changed: Android Gradle release configuration, version, ignore rules, Android/database workflows,
  local production build script, release guide, README, and status.
- Behavior: development builds remain unsigned; production assembly cannot target the known
  development Supabase project or proceed with missing/partial signing inputs. CI first runs the
  complete unsigned gate, then a separately approved production job may materialize an ephemeral
  keystore, build, checksum, and retain a signed candidate for 14 days.
- Data/security impact: no keystore, password, production key/URL, GitHub secret, signed APK, store
  release, hosted database, or billing setting was created or changed.
- Verification: Workflow YAML, the local PowerShell build script, and `git diff --check` pass
  parsing. After correcting the eager Android task lookup, three isolated Gradle invocations each
  produced the required failure: explicit production approval absent, production signing absent,
  and the known development Supabase project supplied. No signing task or APK ran in those checks.
  The full unsigned gate passed 173/173 tests, all four safety gates, release/debug assembly, and
  lint with zero errors. The refreshed debug and unsigned release hashes are recorded above.
- Next: configure a distinct production Supabase project and recoverability controls, provision the
  protected signing environment, build the signed candidate, and run the physical-device matrix.

### 2026-08-01 — Reconcile the canonical completion checklist
- Status: Complete.
- Changed: `PROJECT_STATUS.md`.
- Behavior: the current summary and legacy B2–B6 checklist now agree with the completed Phase 1–5
  evidence. Only B6.5 operations/recovery and B6.7 production release/rollout remain unchecked.
- Data/security impact: none; documentation reconciliation only.
- Verification: `rg -n "\[ \]" PROJECT_STATUS.md` returns only B6.5 and B6.7; `git diff --check`
  passes. The final Deno 2.9.0 gate remains 29/29 green and the frozen offline pnpm install is
  already up to date.
- Next: complete the external production operations and physical-device gates, then configure
  production signing/rollout.

### 2026-08-01 — Synchronize hosted development and pass the full Android gate
- Status: Complete for hosted development and automated release readiness; external production and
  device evidence remains.
- Changed: hosted migration/function deployments, Supabase CLI/config pins, refreshed ignored APK,
  operational documentation, and status.
- Behavior: hosted report detail windows and all three privacy-safe correlation handlers are live;
  the Android app passes its complete automated release safety/regression gate and a fresh
  installable debug-signed test APK is available.
- Data/security impact: hosted development now includes migration `20260729170000`; no production
  project, secret, test fixture, alert destination, backup policy, or billing setting was created or
  changed. The APK remains debug-signed and must not be represented as a production release.
- Verification: all 27 migration versions match; linked lint is clean; Edge versions 28/23/7 are
  ACTIVE; public malformed-request correlation probes pass; Deno has 29/29 passing tests; Android
  has 173/173 passing tests, zero lint errors, all four release safety gates pass, and both APKs were
  rebuilt and hashed as recorded above.
- Next: configure a distinct production Supabase project with recoverable backups/alerts and perform
  the physical-device accessibility/performance/core-workflow smoke test before production signing.

### 2026-08-01 — Complete local `manage-users` correlation migration
- Status: Complete locally and deployed to hosted development; public HTTP evidence passes.
- Changed: `supabase/functions/manage-users/index.ts`, shared operational tests, operations runbook,
  Supabase README/CLI/config package/lockfile, and status.
- Behavior: provisioning responses now carry the same request correlation contract as login and
  account administration; internal and compensation failures use only allow-listed structured
  fields and never exception messages or request data.
- Data/security impact: logging/response metadata only; no schema, credentials, or hosted state
  changed by the handler migration. The separately reviewed bounded-report migration was applied
  to hosted development; it preserves exact report totals while limiting detail arrays to the
  documented overflow sentinel.
- Verification: Pinned Deno 2.9.0 `deno task check` passed formatting (17 files), lint (13 files),
  all three handler type-checks, and all 29 tests with zero failures after canonical formatting.
  `pnpm add --save-dev --save-exact supabase@2.111.0` passed the package-manager supply-chain
  policy check and installed the updated pinned CLI. `supabase db push --linked --dry-run` identified
  only `20260729170000_bounded_report_detail_windows.sql`; the subsequent linked push applied it
  successfully. `supabase migration list --linked` then showed all 27 local/remote versions matched,
  and `supabase db lint --linked --level warning` returned no schema errors. Supabase CLI 2.111.0
  then deployed `pin-login`, `manage-users`, and `manage-accounts` successfully. Function listing
  reports all ACTIVE at versions 28, 23, and 7. Redacted `{}` probes to the two unauthenticated
  handlers returned `400 INVALID_REQUEST`, valid UUID correlation headers, and `no-store`; the
  protected administration handler correctly requires a user JWT before handler execution.
- Supabase configuration now uses `[local_smtp]` instead of the deprecated `[inbucket]`; validation
  with CLI 2.111.0 is pending the next CLI command.
- Next: configure/verify hosted operations controls and execute an isolated restore drill, then
  include the authenticated administration response in the device smoke test.

### 2026-07-29 — Start Task 6.5 operational safety and recovery controls
- Status: Partial; repository foundation complete for two Edge handlers, hosted operations pending.
- Changed: shared Edge operational helper/tests, `pin-login`, `manage-accounts`, operations runbook,
  README, and status.
- Behavior: validated request UUIDs (or opaque server UUIDs for malformed input) correlate responses
  and structured Edge failures without recording request data, credentials, PINs, or exception
  messages. The runbook defines severity/owners/thresholds, incident handling, plan-aware backups/
  retention, and an isolated restore-evidence procedure.
- Data/security impact: no database row/schema, hosted function, secret, alert, backup, or billing
  setting changed. Correlation is a response header/log shape only. Production remains blocked until
  hosted controls and restore evidence exist.
- Verification: Deno formatting/lint/type-check pass and all 29 cached Edge tests pass; diff check is
  clean. Browser policy blocked dashboard inspection, so no hosted state is claimed.
- Next: finish the `manage-users` correlation migration when permitted, then configure/verify hosted
  alerts and backups and execute the isolated restore drill.

### 2026-07-29 — Bound data windows and scope startup work to the visible feature
- Status: Partial; implementation is in progress and the full release gate is pending.
- Changed: remote data sources, Room DAO/store tests, report migration/pgTAP, Compose list/state
  paths, navigation activation policy/tests, MainActivity, Gradle release checks, README, and status.
- Behavior: remote and Room lists fail closed above documented limits; history joins avoid repeated
  full-list scans; report detail arrays use overflow sentinels; Compose derivations use stable keys;
  login no longer triggers all ten feature pipelines because data activation is loaded on demand
  from navigation and remains warm only for the current signed-in identity.
- Data/security impact: forward migration `20260729170000_bounded_report_detail_windows.sql` bounds
  report detail arrays without changing exact totals or counts. No hosted database change yet.
- Verification: corrected performance safety plus production/unit compilation passed in 3m5s. The
  destination activation increment then passed the performance gate, production/unit compilation,
  and all three focused policy tests offline in 3m10s. The bundled `adb devices -l` returned an
  empty authorized-device list; device measurements remain pending. PowerShell AST parsing passed
  for the capture tool. `git diff --check` and the expanded performance gate passed in 50s. The
  complete offline gate reached release assembly/artifact scanning successfully, then reported 102
  test entries with 20 `MavenArtifactFetcher`/socket class-start failures and no assertion failure.
  A local offline resolver attempt reached the cached SDK location but the sandboxed JVM cannot read
  that host artifact; it did not execute the Room assertion. Separate `:app:lint` passed in 2m50s
  with zero errors/16 unchanged warnings. The scanned unsigned release APK is 57,378,833 bytes,
  SHA-256 `0B77A68FA67D884046A41A45245E5A8F0D7732F9DA08A9E0C71BC53293A8F2A3`.
  `:app:assembleDebug` passed offline in 1m12s; the copied installable APK is 76,810,239
  bytes, SHA-256 `41A8B9C25F6EB051B76AB8110C0CA7A81DB6CD5FAC741AFC80E5BB8704C409B8`.
  `aapt dump badging` confirms the intended package/label/version/SDK/launcher and five icon
  densities; `apksigner verify --verbose --print-certs` passes v2 with one Android Debug signer.
- Next: run device measurements/Room runtime assertion when the required runtime is available, then
  execute hosted migration/pgTAP validation and the complete release gate.

### 2026-07-29 — Complete the automated Task 6.3 accessibility and Nepal UX gate
- Status: Partial; repository automation is complete, physical TalkBack/device traversal pending.
- Changed: shared Compose state/date components, Nepal clock and money formatting, first-release
  screens, accessibility/large-font tests, Gradle release checks, README, UX audit, and status.
- Behavior: transactions default to the Kathmandu business date; dates reject invalid ISO input;
  currency is explicitly `NPR`; state/errors announce through TalkBack live regions; headings,
  badges, filters, and login expose useful semantics; dense controls remain scroll-reachable at
  200% font scale; numeric/contact fields use appropriate keyboards.
- Data/security impact: no schema, hosted data, credential, or RLS change. The release build now
  fails on device-local transaction dates, ambiguous rupee labels, mojibake, raw click targets,
  missing shared live regions, or date-screen bypasses of the validated Nepal field.
- Verification: the full gate passed 167 tests/45 suites, all release safety checks, release APK
  assembly, and lint with zero errors/16 known warnings. The rebuilt 76,761,087-byte test APK has
  SHA-256 `69B554D4D25B0DB28C4C6398D102656AFFEB8295E46E97B1C216F493E528C3B9`.
- Next: physical TalkBack/200% device sign-off; Task 6.4 performance work can continue meanwhile.

### 2026-07-29 — Harden Android, Supabase, release artifacts, and CI
- Status: Complete.
- Changed: Android manifest/network/backup policy, strict Supabase client configuration and
  tests, release source/APK gates, all three Edge handlers/configuration, generalized database
  security pgTAP, immutable CI pins and dependency audit, Dependabot, wrapper checksum,
  security audit, README, and status.
- Behavior: malformed or unsafe client origins/keys fail before networking; Android allows no
  cleartext, cloud backup, or device transfer; packaged release entries are scanned for preview
  auth and secret/test markers; Edge upstream calls time out, responses carry restrictive
  headers, and account administration is rejected by the gateway without a JWT.
- Data/security impact: no database row/schema changed. The three hardened functions were
  deployed to the development project. No PIN, session, service key, pepper, request body, or
  diagnostic credential was printed, persisted, or committed.
- Verification: Android gate passed 160 tests/44 suites, artifact scan, release assembly, and
  lint with zero errors/16 warnings; Edge check passed 25 tests; linked lint/history and hosted
  HTTP smoke checks passed. Initial CI passed Edge/audit/migration/seed/lint and identified one
  aggregate-discovery error in the new pgTAP query. After correction, run `30438664328` passed
  697 pgTAP assertions, the concurrency harness, and the Deno 2.9 advisory gate with no known
  vulnerability.
- Next: Task 6.3 accessibility and Nepal-focused UX review.

### 2026-07-29 — Complete Task 6.1 automated coverage audit
- Status: Complete.
- Changed: exact `MoneyAmounts` input/format/arithmetic helpers; all transaction screens;
  checked sale/purchase/vendor/stock repository validation; stricter FIFO restoration;
  authentication/app-shell/FIFO/outbox/financial-boundary tests; coverage matrix, README,
  and status.
- Behavior: rupee input is converted to integer paisa without floating-point rounding;
  fractional-paisa, negative, and overflowing values fail closed. Combined financial totals
  use checked addition, and proportional return estimates avoid overflowing multiplication.
- Integrity: FIFO input must be one-shop/one-product; returns reject over-capacity, duplicate,
  missing, or cost-forged allocation evidence. Outbox retries have verified exponential delays,
  terminal cap, durable resolution, and stale-claim recovery.
- Coverage: every production ViewModel and first-release destination is mapped to deterministic
  unit/Room/Robolectric Compose evidence in `docs/test-coverage-matrix.md`; no arbitrary sleeps.
- Verification: the full gate passed 156 tests/43 suites, release safety/assembly, and lint with
  zero errors/17 warnings. The final 76,994,780-byte debug-signed APK was rebuilt successfully.
- Post-gate arithmetic sweep found residual unchecked read-side sums in purchase review and
  authoritative ledger projections. These now use checked signed addition/multiplication;
  the repeated full gate passed current HEAD in 7m56s.
- Product stock value, cash/bank account balance, sale return/refund/due projections, vendor
  paid/return/due projections, and returned quantities now all fail closed on numeric overflow.
- Next: Task 6.2 security hardening review.

### 2026-07-29 — Complete Task 5.10 authorized notifications and Phase 5
- Status: Complete.
- Changed: Room v6 notification cache/migration/DAO, notification domain/remote/store/
  repository/ViewModel/UI layers, dashboard badge, navigation/activity wiring, remote
  operation catalog, DI, cache tests, status.
- Behavior: RLS-visible active notifications and only the active user's read receipts can be
  cached; expired rows are excluded, offline mark-read is optimistic/idempotent and queued,
  and Owner/Salesman cross-shop responses fail closed.
- Data/security impact: No hosted change. Android deliberately does not select `safe_payload`;
  direct read-state writes remain impossible and the protected RPC stays behind the outbox.
- Verification: `:app:compileDebugKotlin --no-daemon --max-workers=1` passed in 6m21s,
  exported Room schema v6, and emitted only the known migration-parameter/product-Instant
  warnings.
- UI integration compile then stopped on one missing Material `Badge` import in `GdadApp`;
  after adding it, compilation passed in 1m52s. The now-exhaustive destination routing
  exposed the obsolete placeholder/summary helpers; both are removed so every authorized
  first-release destination is functional. A final compile will run with focused tests.
- Focused `testDebugUnitTest` selection passed 27 tests/six suites in 2m7s with zero
  failures/errors.
- Verification: focused notification/cache/navigation tests passed 27/27. After explicit
  migration-default parity correction, the final full gate passed 133 tests/40 suites,
  Room v6 schema export, release safety/assembly, lint with zero errors/17 warnings, and the
  APK script rebuilt the 76,992,596-byte installable artifact.
- Next: Task 6.1 complete unit and UI coverage audit.

### 2026-07-29 — Complete Task 5.9 trusted Android reporting
- Status: Complete.
- Changed: report domain/remote/cache/repository/ViewModel/UI layers, dashboard, navigation,
  remote operation catalog, DI, activity wiring, status.
- Behavior: Owner and Salesman report requests now target only the protected trusted-report
  RPCs; responses must match the active role/shop, and cached dashboard summaries remain
  owner-scoped with Salesman cost/vendor/finance fields removed defensively.
- Data/security impact: No schema or hosted change. Existing RLS/RPC role shaping remains
  authoritative; Android performs no direct report-table reconstruction or mutation.
- Verification: `:app:compileDebugKotlin --no-daemon --max-workers=1` passed in 1m14s;
  only the two pre-existing product `Instant` deprecation warnings were emitted.
- Verification note: the first focused run stopped at test compilation because five nullable
  money fixtures inferred `Int?`; they are corrected to `Long?`. No production failure ran.
- The second focused run executed all 13 selected report/navigation tests; 12 passed and one
  failed only because four JUnit expected values were boxed `Int` against actual `Long` paisa.
  The assertions now use explicit `Long` values; production behavior was correct.
- The corrected focused report/navigation suite passed all 13 tests in 1m3s with zero
  failures or errors.
- Verification: focused report/navigation tests passed 13/13; the full gate passed 122
  tests/37 suites, release safety/assembly, lint with zero errors/17 warnings, and the APK
  script rebuilt the 76,983,096-byte installable artifact.
- Next: Task 5.10 authorized notifications workflow.

### 2026-07-29 — Complete Task 5.8 finance workflow
- Status: Complete.
- Changed: finance domain/remote/repository/ViewModel/UI layers, remote operation catalog,
  DI/navigation, focused tests, `README.md`, `docs/financial-operations.md`, and status.
- Behavior: Owner-only typed code reads derived cash/bank balances and immutable journal
  history, calls protected expense, deposit/withdrawal, transfer, and reversal RPCs, retains
  exact retry keys with a visible same-operation retry action, displays authoritative
  receipts, constrains history to phone layouts, and converts decimal amounts to whole
  paisa without floating-point arithmetic.
- Data/security impact: No schema/hosted change and no direct financial table writes.
- Verification: focused finance tests passed 8/8; the final full gate passed 113 tests/34
  suites, release safety/assembly, lint with zero errors/17 warnings, and the APK script
  rebuilt the 76,964,836-byte installable artifact.
- Next: Task 5.9 authorized dashboard and reporting workflow.

### 2026-07-29 — Complete Task 5.7 vendor financial workflow
- Status: Complete.
- Changed: vendor-finance domain, Supabase data source, production repository, ViewModel,
  remote operation catalog, DI, and `PROJECT_STATUS.md`.
- Behavior: Owner-only code can load reconciled bills/events, post fully allocated
  cash/bank payments, return available original-lot stock, reverse posted events, retain
  exact retry UUIDs, and visibly refresh conflicts. Server results remain authoritative.
- Data/security impact: No schema or hosted change; existing RLS and the three protected
  vendor financial RPCs remain the only access/mutation boundary.
- Verification: 105 tests/31 suites, release safety, release APK, full lint, and diff
  checks pass. `build-apk.ps1` rebuilt the 76,134,753-byte installable debug APK.
- Next: Task 5.8 cash, bank, expense, transfer, and reversal workflow.

### 2026-07-28 — Complete Task 5.6 sale history and return UI
- Status: Complete.
- Changed: `app/src/main/java/com/gdad/bags/{domain,data,ui}/returning/`, remote operation
  contracts, application DI, `MainActivity`, `GdadApp`, and `PROJECT_STATUS.md`.
- Behavior: Owner and Salesman can load/search/filter RLS-scoped sale history and expand
  original line/payment/due/return detail. Only Owner receives FIFO allocation/cost data
  and can post validated partial sellable/damaged returns with the exact retry key and an
  optional cash/bank refund. The receipt uses only authoritative RPC totals.
- Data/security impact: No schema or hosted change. Reads remain RLS-scoped and returns
  remain online-only through existing `post_sale_return`; Android performs no direct write.
- Verification: 98 tests/28 suites, release safety, release APK, full lint, and diff
  checks pass. `build-apk.ps1` rebuilt the 75,732,381-byte installable debug APK.
- Next: Task 5.7 vendor bill, due, payment, and return workflow.

### 2026-07-28 — Complete Task 5.5 atomic FIFO point of sale
- Status: Complete.
- Changed: Sale domain/remote/repository/ViewModel/Compose layers; DI/Main/navigation;
  policy/retry/role/receipt tests; FIFO sale docs, README, and status.
- Behavior: Owner and Salesman post duplicate-proof atomic FIFO sales under backend policy;
  receipts show authoritative totals and allocations and refresh stock.
- Data/security impact: No schema/hosted change. Sales are online-only through the existing
  protected atomic RPC; direct writes/outbox remain forbidden.
- Verification: 89 tests/25 suites, release safety/APK/lint/diff, installable APK rebuild,
  and fresh-database CI run `30381148900` pass.
- Next: Task 5.6 sale history, detail, and return workflow.

### 2026-07-28 — Complete Task 5.4 stock and inventory adjustments
- Status: Complete.
- Changed: Stock domain/remote/repository/ViewModel/Compose layers; DI/Main/navigation;
  repository/retry/role UI tests; inventory docs, README, and status.
- Behavior: Owner filters stock and reviews FIFO lots/movements/cost before posting validated
  additions, removal, damage, or loss. Salesman sees safe on-hand/low-stock only.
- Data/security impact: No schema/hosted change. Adjustment is online-only through the
  existing Owner-only atomic RPC; direct writes/outbox remain forbidden.
- Verification: 82 tests/22 suites, release safety/APK/lint, and diff checks pass.
- Next: Task 5.5 point-of-sale workflow.

### 2026-07-28 — Complete Task 5.3 vendor and purchase workflow
- Status: Complete.
- Changed: Protected vendor migration/pgTAP; Room v5; purchase domain/remote/store/repository/
  ViewModel/Compose layers; DI/Main/navigation; SQL-state mapping; repository/ViewModel/UI/
  migration tests; README, architecture, purchase/vendor docs, and status.
- Behavior: Owner manages vendors and posts reviewed purchases with exact-key retry, active
  vendor/products, invoice/date, quantities/cost, cash/bank payment, authoritative receipt
  totals, FIFO count, and refreshed stock/dues/balances. Other roles reveal no workflow.
- Data/security impact: Hosted vendor management is Owner-only, audited, idempotent, and
  direct writes remain denied. Room migrates 4-to-5 without destructive fallback. Purchase
  posting stays online-only and never enters the outbox.
- Verification: Hosted deployment/history/lint and all 76 Android tests/release/lint/diff
  checks pass. GitHub fresh-database run `30378177697` passed every backend stage. The
  75,038,733-byte debug-signed test APK was rebuilt successfully.
- Next: Task 5.4 stock and inventory adjustment vertical slice.

### 2026-07-28 — Complete Task 5.2 product catalog vertical slice
- Status: Complete.
- Changed: Product domain/remote/store/repository/ViewModel/Compose layers; DI, Main and
  typed navigation integration; Room product entity/schema v4/migration; remote SQL-state
  classification; repository, migration, ViewModel and role-visibility tests; README,
  product/architecture docs, and status.
- Behavior: Owner and Salesman search/view product and stock history. Owner alone sees cost
  and can create/edit/archive. Exact-key retries and transient offline queuing cannot create
  a second request; archived rows remain visible without mutation controls.
- Data/security impact: Local Room migrates 3-to-4. No hosted change; Android still has no
  direct product writes. RLS and `manage_product` authorize server-side; cached rows/outbox
  remain identity scoped and no backend error detail or credential is persisted/rendered.
- Verification: 67 tests in 16 suites passed; release safety, release assembly, and full
  lint passed; unsigned APK is 55,984,523 bytes; lint reports zero errors/17 warnings;
  `git diff --check` passed.
- Next: Task 5.3 vendor and purchase receipt vertical slice.

### 2026-07-28 — Complete Task 5.1 account management vertical slice
- Status: Complete.
- Changed: Account domain/repository/remote/store/ViewModel/UI layers; Room managed-account
  and shop entities/DAO/schema v3/migration; Main/DI/navigation integration; repository,
  migration, ViewModel, and Robolectric role-visibility tests; account docs, README, status.
- Behavior: Super Admin manages Owners across visible active shops; Owner manages only
  same-shop Salesmen. Creation and disable/re-enable/PIN reset call protected Edge
  Functions, refresh the Room directory, retain exact retry IDs, and give safe audit/session
  feedback. Salesmen cannot reveal or trigger administration.
- Data/security impact: Local Room schema migrated 2-to-3. No hosted change or direct shop
  write. PIN fields are transient only; directory rows are owner-scoped and purged on
  logout/identity switch. Backend RLS/RPC hierarchy remains authoritative.
- Verification: 56 tests in 13 suites passed; release safety plus 51-task release assembly
  passed; 30-task lint passed with zero errors and 17 pre-existing warnings;
  `git diff --check` passed.
- Next: Task 5.2 product catalog vertical slice.

### 2026-07-28 — Complete Task 4.7 and Phase 4 exit gate
- Status: Complete.
- Changed: Navigation Compose/testing dependencies; typed route/role/external policy;
  authenticated NavHost and clickable dashboard; shared state/confirmation composables;
  navigation and Robolectric Compose tests; README, architecture docs, and status.
- Behavior: Authenticated users receive only role-authorized destinations. Forged direct
  routes return to Dashboard without rendering protected content; external URIs remain
  unsupported for the PIN-only release. Back stacks restore after recreation, and every
  feature shell uses accessible empty/refresh UI with shared loading/error/retry patterns.
- Data/security impact: No hosted or local schema change. Navigation authorization is
  defense in depth; backend RLS/RPC checks remain authoritative. No deep link or alternate
  authentication path was introduced.
- Verification: 45 tests in 10 suites passed; release safety and 51-task release assembly
  passed; 30-task full lint passed with zero errors and 17 pre-existing warnings;
  `git diff --check` passed.
- Next: Task 5.1 account and shop management vertical slice.

### 2026-07-28 — Complete Task 4.6 durable mutation outbox
- Status: Complete.
- Changed: WorkManager dependency; Room outbox entity/DAO/database v2 migration/schema;
  queue, processor, worker, Supabase dispatcher, DI and dashboard resolution notice;
  local/migration tests; README, Android/offline architecture docs; and `PROJECT_STATUS.md`.
- Behavior: Safe offline product and notification-read mutations persist before
  confirmation, execute only with network, reuse stable keys, retry with bounded backoff,
  and retain permanent safe failure states. Risky operations reject offline queueing.
- Data/security impact: Local Room schema migrated 1-to-2. Owner/user isolation and
  logout purge extend to outbox rows. Payload credential names and oversized payloads
  are rejected; response/exception text and secrets are never persisted. Hosted state
  was unchanged.
- Verification: 37 tests in eight suites passed with zero failures/errors; release safety
  plus 51-task release assembly passed; 30-task full lint passed with zero errors and 17
  pre-existing warnings; `git diff --check` passed.
- Next: Task 4.7 app navigation and shared UI states.

### 2026-07-28 — Complete Task 4.5 Room offline read cache
- Status: Complete.
- Changed: root/app Gradle plugins and dependencies, `data/local/` Room entities/DAOs/
  database/store/synchronizer, authentication and DI cache integration, Room/auth tests,
  exported schema v1, README, Android architecture/offline-cache docs, and
  `PROJECT_STATUS.md`.
- Behavior: Nine minimum read models persist as tenant/user-scoped Room Flows. Complete
  remote snapshots replace cache state atomically; failures retain the last good snapshot;
  logout and identity/tenant changes purge before another identity can publish.
- Data/security impact: Local-only schema addition; no hosted change. Cache contains no
  PIN, token, key, verifier, or service credential. Destructive migrations are forbidden.
- Verification: 29 tests across six suites passed; release assembly and release safety
  passed; full lint reports zero errors and 17 pre-existing warnings. Room schema v1 is
  committed. Dependency download workarounds affected ignored Gradle cache only.
- Next: Task 4.6 durable mutation outbox and retry rules.

### 2026-07-28 — Complete Task 4.4 typed remote boundary
- Status: Complete.
- Changed: `data/remote/RemoteContracts.kt`, `RemoteDtos.kt`, production authentication
  data sources/repository, DI composition, domain authentication errors, remote/auth tests,
  README, Android architecture documentation, and `PROJECT_STATUS.md`.
- Behavior: Current Supabase function/query calls use typed DTOs and one reusable bounded
  executor. Failures retain category/retry semantics through safe domain results; an expired
  authenticated request refreshes and retries once; caller cancellation remains cancellation.
- Data/security impact: No hosted state changed. Raw backend/exception messages and request
  bodies are excluded from diagnostics and UI; no credential value was added or printed.
- Verification: 21 tests in five suites passed with zero failures; release safety and APK
  assembly passed; full lint passed with zero errors and 17 pre-existing warnings.
- Next: Task 4.5 Room cache and tenant-safe synchronization model.

### 2026-07-28 — Complete Task 4.3 release authentication safety
- Status: Complete.
- Changed: deleted `app/src/main/java/com/gdad/bags/data/auth/AuthRepository.kt`; updated
  `app/build.gradle.kts`, `README.md`, and `PROJECT_STATUS.md`.
- Behavior: Release source now contains only production Supabase authentication. Every release
  pre-build verifies the production binding and blocks preview/prefix authentication or embedded
  secret/service-role key and numeric-PIN patterns.
- Data/security impact: No hosted data changed. The change removes an unsafe local bypass and
  adds a fail-closed release check; no credential value was added or printed.
- Verification: Auth safety plus all 12 unit tests passed; `assembleRelease` passed all 50
  tasks including lint-vital; full Android lint passed with zero errors.
- Next: Task 4.4 typed remote client and safe domain error mapping.

### 2026-07-28 — Implement Task 4.2 production authentication repository
- Status: Complete.
- Changed: domain authentication contracts/use cases, `AuthViewModel.kt`, `GdadApp.kt`,
  `ProductionAuthRepository.kt`, `SupabaseAuthDataSources.kt`,
  `EncryptedSessionManager.kt`, `InstallationIdProvider.kt`, Supabase client/DI wiring,
  repository tests, README/authentication/architecture docs, and `PROJECT_STATUS.md`.
- Behavior: Implements normalized hosted PIN login, standard session import, automatic
  refresh, encrypted restore, authoritative identity/role/shop loading, initialization,
  safe failure states, and local-first logout. Unconfigured builds fail safely rather
  than constructing preview authentication.
- Data/security impact: No backend change. Tokens remain inside Supabase Auth/data code
  and are stored only as AES-GCM ciphertext; the key is non-exportable from Android
  Keystore. No PIN, token, key value, raw backend error, or credential is logged or
  committed. Disabled/inactive/ambiguous identities fail closed and clear local Auth.
- Verification: Offline `testDebugUnitTest assembleDebug lintDebug` recorded
  `BUILD SUCCESSFUL` in 3m45s with 12 tests, zero failures/errors, APK size 71,802,840
  bytes, zero lint errors, and no new Task 4.2 warning. A cleanup test/lint rerun also
  passed; local BuildConfig checks confirmed the expected development project and a
  publishable-key shape without printing either value. A final synthetic-input-only
  unit rerun passed all 12 tests in 77 seconds.
- Next: Implement Task 4.3 preview-auth removal and release-binding assertions.

### 2026-07-28 — Implement Task 4.1 Android architecture and dependency injection
- Status: Complete.
- Changed: `GdadApplication.kt`, `di/AppContainer.kt`, `domain/auth/Authentication.kt`,
  `data/auth/AuthRepository.kt`, `data/remote/SupabaseClientFactory.kt`,
  `ui/auth/AuthViewModel.kt`, `MainActivity.kt`, `ui/GdadApp.kt`, Android manifest and
  Gradle dependencies, `LoginUseCaseTest.kt`, `docs/android-architecture.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Moves concrete construction to an application composition root, owns one
  lazy Supabase client for the process, injects a domain use-case interface into the
  authentication ViewModel, and renders lifecycle-aware immutable state through
  callback-only composables. Production and deterministic fake graphs share interfaces.
- Data/security impact: No Supabase or persisted-data change. Client configuration still
  accepts only the public URL/publishable key. Preview auth is isolated to one temporary
  binding that Tasks 4.2 and 4.3 must replace/remove before release.
- Verification: Final offline `testDebugUnitTest` passed all four tests in 68 seconds.
  `assembleDebug lintDebug` recorded `BUILD SUCCESSFUL`; lint has zero errors and 17
  non-blocking warnings.
- Next: Implement Task 4.2 production authentication and secure session lifecycle.

### 2026-07-27 — Complete Task 3.9 backend integration and concurrency gate
- Status: Complete.
- Changed: `backend_concurrency_setup.sql`, `backend_concurrency.sh`,
  `backend_concurrency_verify.sql`, `.github/workflows/database-tests.yml`,
  `docs/backend-phase3-exit-gate.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds real parallel database sessions for competing one-unit FIFO sales,
  exact duplicate sale retry, over-allocating vendor payments, and no-overdraft expense
  debits. It then posts a partial return and proves report reconciliation, balanced
  journals, rollback cleanup, disabled-user denial, and cross-shop isolation.
- Data/security impact: Test/CI only; no production schema or hosted state changes.
- Verification: Both SQL files parse with bundled `pglast`, workflow YAML parses with
  PyYAML, and `git diff --check` passes. No Bash executable is available locally for
  `bash -n`. GitHub run `30292372527` passed every earlier gate but confirmed that SQL
  beneath `supabase/tests` is recursively discovered; the harness was relocated to
  `supabase/integration-tests` and its workflow paths were updated. Run `30292811757`
  reached the dedicated harness and exposed a verifier-only `min(uuid)` error after the
  parallel operations; UUID retrieval is now a separate query. Final run `30293062132`
  passed every workflow step, including the full integration/concurrency harness.
- Next: Implement Task 4.1 Android architecture and dependency injection.

### 2026-07-27 — Implement and deploy Task 3.8 trusted business reports
- Status: Complete.
- Changed: migration `20260728060000_trusted_business_reports.sql` and
  test `trusted_business_reports.test.sql`, `docs/trusted-reports.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one parameterized period report and a Nepal-instant daily wrapper.
  Owners receive reconciled sales/returns, FIFO COGS/profit, stock quantity/value, low
  stock, vendor due, cash/bank balances, and effective expenses. Salesmen receive only
  permitted sales/returns/stock/low-stock fields; cost/vendor/finance keys are absent.
- Data/security impact: Read-only security-definer RPCs re-derive active tenant role.
  Adds one targeted sale-return shop/date/status index; existing sales, expenses,
  journal, product, lot, vendor, and account indexes cover the remaining access paths.
- Verification: Migration/test parsing and static count match 37. GitHub Actions run
  `30291547016` passed Edge checks, fresh migration, deterministic seed/reset, database
  lint, every prior suite, and all 37 report assertions. It proves exact reconciliation,
  role/tenant/disabled/range denial, Nepal midnight, empty periods, and indexed plans for
  sales, returns, expenses, and balances. The linked dry run selected only this migration;
  hosted history matches through `20260728060000` and lint reports no schema errors.
- Next: Implement Task 3.9 backend integration and concurrency suite.

### 2026-07-27 — Implement and deploy Task 3.7 atomic financial operations
- Status: Complete.
- Changed: migrations `20260728030000_cash_movement_journal_kinds.sql` and
  `20260728040000_atomic_financial_operations.sql`,
  `20260728050000_system_financial_account_bootstrap.sql`, `supabase/seed.sql`, test
  `atomic_financial_operations.test.sql`, `docs/financial-operations.md`,
  `docs/business-policies.md`, `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds protected Owner-only expense, deposit/withdrawal, transfer, and exact
  compensating reversal RPCs with request fingerprints and authoritative results.
  Cash/bank balances remain derived; debiting operations and reversals lock accounts and
  reject overdrafts. Transfers post as one journal and expenses reconcile to evidence.
- Data/security impact: Adds deposit/withdrawal journal kinds and private retry state;
  direct table writes remain denied. A forward bootstrap creates 11 protected system
  accounts for existing/future shops; deterministic seed mode preserves fixed fixture
  IDs and now includes every required purpose. The previously undocumented funds
  behavior is explicitly conservative for first release: balances cannot go below zero.
- Verification: All migrations, seed, and the final test parse; static count matches 52.
  GitHub Actions run `30289769906` passed Edge checks, fresh migration, expanded
  deterministic seed/reset, database lint, every prior suite, and all 52 assertions.
  Linked dry runs selected only expected migrations, hosted history matches through
  `20260728050000`, and final linked lint reports `No schema errors found`.
- Next: Implement Task 3.8 trusted dashboard and report queries.

### 2026-07-27 — Implement and deploy Task 3.6 vendor financial and return operations
- Status: Complete.
- Changed: migrations `20260728010000_vendor_return_movement_types.sql` and
  `20260728020000_vendor_financial_operations.sql`, test
  `vendor_financial_operations.test.sql`, `docs/vendor-operations.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds Owner-only fingerprinted RPCs for allocated cash/bank vendor payment,
  unpaid-value-capped original-lot vendor return, and payment/return reversal. Due is
  derived from posted bills minus effective allocations and returns; journals, stock,
  statuses, movements, notification, and audits commit atomically.
- Data/security impact: Bills, lots, vendors, periods, and request rows lock before
  consequences. Return/reversal movements are explicit; direct writes remain denied.
  Vendor returns cannot turn a bill into an overpayment—payments must be reversed first.
- Verification: Both migrations and the test parsed with bundled `pglast`; the suite
  declares 59 assertions covering multi-bill allocation, cash/bank, derived due,
  overpayment, original-lot and unpaid-value caps, payment/return reversals,
  compensating stock/journals, exact retries, role/tenant denial, notification, audit,
  rollback, and integrity helpers. Corrected GitHub Actions run `30288446946` passed
  Edge checks, fresh migration, deterministic seed/reset, database lint, all prior
  suites, and all 59 assertions. Linked dry run selected only both Task 3.6 migrations;
  hosted history now matches through `20260728020000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.7 expenses, deposits, withdrawals, and transfers.

### 2026-07-27 — Implement and deploy Task 3.5 atomic inventory adjustment operation
- Status: Complete.
- Changed: migrations `20260727230000_inventory_adjustment_journal_kind.sql` and
  `20260728000000_atomic_inventory_adjustment.sql`, test
  `atomic_inventory_adjustment.test.sql`, `docs/inventory-adjustment.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds an Owner-only reason-coded adjustment source and retry-safe RPC.
  Manual additions create new FIFO lots; damage, loss, and manual removal consume a
  specified lot without rewriting receipts. Every operation appends a movement and
  projection change, positive-cost operations post balanced inventory/clearing entries,
  and all successes create an Owner notification, safe audit, and authoritative result.
- Data/security impact: Explicit reason/type, direction, lot, quantity, cost, and journal
  constraints make reconciliation durable. Zero-cost stock creates no fabricated money
  entry. Direct writes and request internals remain denied; product/lot/request locks
  serialize conflicting or duplicate operations.
- Verification: Both migrations and the test parsed with bundled `pglast`; static
  counting corrected the declared plan to 51 assertions. The comprehensive pgTAP
  suite now covers new-lot addition, source-lot damage/loss/removal, exact cost journals,
  zero-cost treatment, immutable receipts, movement/projection reconciliation, retry,
  excessive rollback, reason/cost/source validation, missing accounts, role/RLS, tenant
  denial, notifications, and safe audits. CI run `30286240589` passed Edge checks, fresh
  migration, deterministic seed/reset, lint, all existing suites, and all 51 adjustment
  assertions. The linked dry run listed only both Task 3.5 migrations; hosted history now
  matches through `20260728000000`, and linked lint reports `No schema errors found`.
- Next: Implement Task 3.6 vendor payment, due, and vendor return operations.

### 2026-07-27 — Implement and deploy Task 3.4 atomic sale return and refund operation
- Status: Complete.
- Changed: migration `20260727220000_atomic_sale_return.sql`, test
  `atomic_sale_return.test.sql`, `docs/sale-return.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one Owner-only request-fingerprinted return RPC that enforces the
  30-day original-sale window, open Nepal business date, cumulative line/allocation
  limits, reverse allocation restoration, sellable/damaged disposition, server-derived
  return value, due-first credit reduction, mandatory paid-value refund, sale status,
  balanced journals, notification, audit, and authoritative result in one transaction.
- Data/security impact: The original sale row serializes concurrent returns; line,
  allocation, and lot locks are deterministic. Sellable quantities restore only their
  original lots, while damaged evidence never increases saleable stock. Direct writes
  remain denied and failed operations roll back all consequences.
- Verification: The migration and test parsed with bundled `pglast`; static counting
  corrected the declared plan to 53 assertions. The comprehensive pgTAP suite
  now uses genuine atomic-sale output to cover the 30-day boundary, repeated partial
  returns, damaged/sellable reverse allocation, exact restoration, due-first credit,
  paid-value refund cap, retry, over-return, rollback, window/role/tenant denial,
  journals, notifications, audits, and integrity helpers. Pre-CI review moved immutable
  request fingerprint locking/result replay ahead of mutable sale-state validation, so
  a completed retry cannot be rejected after its first call changed return quantities or
  sale status. CI run `30284003683` passed Edge checks, fresh migration, deterministic
  seed/reset, lint, all existing suites, and all 53 return/refund assertions. The linked
  dry run listed only this migration; hosted history now matches through
  `20260727220000`, and linked lint reports `No schema errors found`.
- Next: Implement Task 3.5 inventory adjustment, damage, and loss.

### 2026-07-27 — Implement and deploy Task 3.3 atomic FIFO sale operation
- Status: Complete.
- Changed: migration `20260727200000_atomic_fifo_sale.sql`, test
  `atomic_fifo_sale.test.sql`, `docs/fifo-sale.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Adds one authenticated, request-fingerprinted sale RPC that applies D1–D4
  and D7/D10: tenant/role-derived authority, configured Salesman pricing, Owner-only
  overrides/discounts/credit, exact whole-paisa totals, split cash/bank payments,
  deterministic FIFO allocation with no negative stock, stock projection/movements,
  low-stock notification, balanced sale/COGS/payment journals, and safe audit/result.
- Security/data impact: Direct table mutation remains denied. Product rows lock in
  stable ID order before FIFO lots lock by `received_at,id`; request locks and unique
  consequence keys prevent retry duplication. Failed shortages or configuration errors
  roll back all sale, stock, money, notification, and audit effects.
- Verification: The migration and test parsed with bundled `pglast`; static counting
  corrected the declared plan to 50 assertions. The pgTAP suite
  now covers permissions, Owner pricing/discount/partial credit, multi-lot FIFO and exact
  cost, due/payment/journals, low stock/audit, retry mismatch, shortage rollback,
  Salesman rules, zero total, overpayment, cross-shop denial, missing-account rollback,
  and negative-stock invariants. CI run `30282328392` passed Edge checks, fresh
  migration, and deterministic seed/reset, then lint identified one variable qualifier
  that PL/pgSQL could not resolve in the FIFO query; pgTAP did not run. The product
  variable is now explicitly named `v_product_id` throughout. CI run `30282596268`
  then passed fresh migration, deterministic reset, and lint. pgTAP exposed two test
  expectation/input defects before a private-state assertion ran under `authenticated`
  and stopped the file: allocation output ordering, a null payments argument, and the
  assertion role were corrected without changing production behavior. CI run
  `30282947284` passed Edge checks, fresh migration, deterministic seed/reset, lint, all
  existing suites, and all 50 FIFO-sale assertions. The linked dry run listed only this
  migration; hosted history now matches through `20260727200000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.4 atomic sale return and refund.

### 2026-07-27 — Implement and deploy Task 3.2 atomic purchase receipt operation
- Status: Complete.
- Changed: migration `20260727180000_atomic_purchase_receipt.sql`, test
  `atomic_purchase_receipt.test.sql`, `docs/purchase-receipt.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds one Owner-only idempotent RPC that validates Nepal date/open period,
  vendor, products, accounts, lines, and optional payment before creating the received
  bill, receipt, exact FIFO lots/movements, stock projection, balanced journals, due,
  and safe audit in one transaction.
- Data/security impact: Tenant and actor authority are re-derived; private request
  fingerprints prevent changed retries and duplicate stock/money. Direct writes remain
  denied. This first operation deliberately accepts zero purchase discount/tax so FIFO
  unit cost and billed unit cost remain identical under the approved schema.
- Verification: Bundled `pglast` parsed the migration and test; static counting found
  and corrected the plan to 41 assertions. Review also made deterministic product locks
  explicit and executes the void journal-integrity helper through `lives_ok` for both
  journals. CI run `30280756573` passed fresh migration, deterministic seed/reset, lint,
  all existing suites, and the first seven new assertions, then exposed two test-only
  expected queries that began with bare `VALUES`, which pgTAP cannot open as cursors.
  Both expected datasets now begin with `SELECT`. Corrected CI run `30281161087` passed
  Edge checks, fresh migration, two-reset deterministic fixture verification, lint, all
  existing suites, and all 41 purchase assertions. The linked dry run listed only this
  migration; hosted history now matches through `20260727180000`, and linked lint reports
  `No schema errors found`.
- Next: Implement Task 3.3 atomic FIFO sale.

### 2026-07-27 — Implement and deploy Task 3.1 atomic product management
- Status: Complete.
- Changed: migration `20260727163000_product_management.sql`, test
  `product_management.test.sql`, `docs/product-management.md`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds normalized SKU/barcode columns, permanent private code reservations,
  private request fingerprints, and one Owner-only create/update/archive RPC with
  archive guards, idempotent result replay, and safe business audit snapshots.
- Data/security impact: Shop intent is validated against active Owner membership;
  Salesman/cross-shop/direct writes remain denied. Old and archived codes stay reserved;
  no secret or credential metadata enters audit.
- Verification: Bundled `pglast` parsed the migration and test; static counting found
  and corrected the declared plan to 45 assertions. Review also corrected blank-barcode
  canonicalization and preserved a target UUID outside RLS for the cross-shop denial
  test. Run `30279053076` passed migration, two-reset seed determinism, cleanup, lint,
  and all prior suites, then stopped before assertion 1 because two pgTAP meta-functions
  were incorrectly combined as booleans. Direct catalog predicates now replace them;
  run `30279388635` then executed all 45 assertions with 44 passing. The only mismatch
  counted cross-shop storage under Owner B RLS (correctly seeing one row); that count is
  now performed in the test-administrator context. Corrected run `30279665131` passed
  Edge checks, fresh migrations, two-reset deterministic seed verification, database
  lint, all existing suites, and all 45 product assertions. Hosted migration history
  matches through `20260727163000`; linked lint reports `No schema errors found`.
- Next: Implement Task 3.2 atomic purchase receipt and FIFO creation.

### 2026-07-27 — Implement deterministic Task 2.7 development fixtures
- Status: Complete.
- Changed: `supabase/seed.sql`, `supabase/verify-dev-seed.sql`,
  `supabase/clear-dev-seed.sql`, `.github/workflows/database-tests.yml`, and
  `PROJECT_STATUS.md`.
- Behavior: Defines fixed two-shop fixtures for all roles, products/multiple FIFO lots,
  purchasing, a partially paid credit sale and return, stock movements, expense and
  transfer journals, and targeted/read notifications. CI resets twice and compares a
  canonical fixture hash before removing only dev rows for isolated pgTAP suites.
- Data/security impact: Local/reset only; Auth fixtures have empty passwords, reserved
  `.invalid` emails, and no PIN verifier. No hosted secret, PIN, hash, or customer data
  is present, and seed data is not being pushed to the hosted project.
- Verification: Bundled `pglast` parsed all seed tooling, PyYAML parsed the workflow,
  and `git diff --check` passed. GitHub run `30278308128` applied migrations from zero,
  loaded and integrity-checked the fixture graph, reset and reproduced the identical
  canonical hash, removed only fixture rows, passed database lint, and passed every
  pgTAP suite.
- Next: Implement Task 3.1 atomic product management operations.

### 2026-07-27 — Implement and deploy Task 2.6 notifications and immutable audit
- Status: Complete.
- Changed: migrations `20260724220000_notifications_audit.sql` and
  `20260727150000_notifications_audit_lint.sql`, test `notifications_audit.test.sql`,
  `docs/data-dictionary.md`, and `PROJECT_STATUS.md`.
- Behavior: Adds exact 90-day immutable notification sources, user/role targeting,
  per-user first-read state, typed source validation, bounded expiry cleanup, and an
  append-only business audit contract.
- Data/security impact: Safe JSON validation recursively rejects credential-bearing
  keys; authenticated clients receive only targeted RLS reads and the protected
  mark-read RPC, while audit append and cleanup remain backend-only.
- Verification: Bundled `pglast` parsed the migration and 39-assertion test; assertion
  count matches the plan and `git diff --check` passed. GitHub run `30276338069`
  passed Edge checks, fresh migration, database lint, all preceding suites, and the
  first 23 new assertions, then exposed a self-referential notification SELECT policy
  that terminated that test backend. Run `30276657008` proved its row-local correction
  through assertion 31, then exposed the same unsafe helper shape in receipt SELECT
  RLS; receipt visibility now uses a direct `EXISTS` against the safe notification
  policy. Corrected run `30276915806` passed all fresh migration, Edge, lint, existing
  suites, and 39 new assertions. Hosted migration `20260724220000` then deployed and
  parity matched, but hosted lint exposed two non-failing function warnings. Forward
  migration `20260727150000` corrected function volatility and exhaustive return flow;
  run `30277296630` passed every gate. Hosted histories now match through that forward
  migration and linked lint reports `No schema errors found`.
- Next: Implement Task 2.7 deterministic development seed fixtures.

### 2026-07-24 — Implement and deploy Task 2.5 balanced ledger schema
- Status: Complete.
- Changed: migration `20260724200000_balanced_ledger.sql` and `PROJECT_STATUS.md`.
- Behavior: Defines chart of accounts, accounting-period controls, balanced immutable
  journals, exact reversals, typed source validation, derived balances, and expenses.
- Data/security impact: Adds five hosted Owner-only tenant tables, four enums, deferred
  integrity helpers, RLS/read grants, and no direct Android writes.
- Verification: Bundled `pglast` parsed migration/test. Run `30075992454` passed
  migration/lint; pgTAP stopped on the derived-balance type mismatch after 8 assertions.
  Explicit fixture cast applied; corrected run `30076199784` passed fresh migration,
  lint, every suite, and all 28 assertions. Hosted histories match through
  `20260724200000`; linked lint is clean.
- Next: Implement Task 2.6 notifications and immutable business-audit migration/tests.

### 2026-07-24 — Implement and deploy Task 2.4 purchasing schema
- Status: Complete.
- Changed: migration `20260724170000_vendors_purchasing.sql` and `PROJECT_STATUS.md`.
- Behavior: Defines vendor masters, immutable purchasing/receiving/payment/return
  evidence, exact FIFO source linkage, and derivable vendor due.
- Data/security impact: Adds nine hosted tenant tables, two enums, lot receipt lineage,
  deferred integrity helpers, Owner-only RLS reads, and zero direct Android writes.
- Verification: Bundled `pglast` parsed migration/tests; plan matches 31. GitHub run
  `30075105709` passed fresh migration, lint, all existing suites, and all 31 purchasing
  assertions. Hosted histories match through `20260724170000`; linked lint is clean.
- Next: Implement Task 2.5 balanced cash/bank ledger migration/tests.

### 2026-07-24 — Implement and deploy Task 2.3 sales schema
- Status: Complete.
- Changed: migration `20260724143000_sales_payments_returns.sql`, authorization matrix,
  core-foundation pgTAP policy expectation, and `PROJECT_STATUS.md`.
- Behavior: Defines server-reconciled sale totals, full FIFO allocation evidence,
  append-only payment/refund events, cumulative-safe returns, and cost-restricted reads.
- Data/security impact: Adds eight hosted tenant tables, five enums, deferred security-
  definer integrity helpers, RLS/read grants, and Owner-only lot/movement/cost visibility.
- Verification: Bundled `pglast` parsed all SQL. Run `30073969515` identified one
  nullable credit check; fail-closed correction run `30074252359` passed fresh migration,
  lint, all prior suites, and all 31 new assertions. Hosted histories match through
  `20260724143000`; linked lint is clean.
- Next: Implement Task 2.4 vendor and purchasing migration/tests.

### 2026-07-24 — Complete schema-affecting business policies
- Status: Complete for Task 2.2 decisions D1–D11.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Finalizes backdating/period locks, permanent normalized product codes,
  Salesman financial-data restriction, whole-paisa/no-VAT first-release arithmetic,
  notification expiry, and indefinite first-release audit retention.
- Data/security impact: Documentation only. Future schemas/RPCs must enforce closed
  periods, restricted cost/finance projections, server arithmetic, safe retention, and
  forward-only policy change.
- Verification: Automated consistency passed for all D1–D11 approvals in policy and
  dictionary plus schema, RPC, permission, and UI/acceptance consequences per decision;
  `git diff --check` passed.
- Next: Implement Task 2.3 sales, payment, allocation, and return migration/tests.

### 2026-07-24 — Record payment, return, and purchasing policies
- Status: Complete for Task 2.2 decisions D4–D6; D7–D11 pending.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Defines cash/bank split tender without overpayment, Owner-only 30-day
  returns with exact lot disposition and paid-value refund cap, and strict Owner-only
  purchasing/receiving/payment/reversal rules.
- Data/security impact: Documentation only. Future operations must use same-shop
  accounts, balanced journals, immutable compensation, derived dues, and protected
  Owner authority.
- Verification: Automated consistency passed for D1–D6 approval in both policy and
  dictionary, all four consequence categories per approved decision, and D7–D11
  pending coverage; `git diff --check` passed.
- Next: Obtain D7–D9 backdating, product-code, and Salesman visibility policies.

### 2026-07-24 — Record stock, pricing, and credit-sale policies
- Status: Complete for Task 2.2 decisions D1–D3; D4–D11 pending.
- Changed: `docs/business-policies.md`, `docs/data-dictionary.md`, and
  `PROJECT_STATUS.md`.
- Behavior: First release blocks insufficient-stock sales, permits price/discount
  changes only for Owners, and permits identified due-dated credit sales only for
  Owners with append-only partial payments.
- Data/security impact: Documentation only. Policies require authoritative database
  roles, full FIFO allocation, protected Owner operations, and derived due values.
- Verification: Cross-checked each approved choice against its data-dictionary objects
  and execution-plan Tasks 2.2, 2.3, 3.3, and 3.4. Automated consistency passed for
  D1–D3 approval/consequence sections and D4–D11 pending coverage; `git diff --check`
  passed.
- Next: Obtain D4–D6 payment, return, and purchasing policies.

### 2026-07-24 — Publish canonical first-release data dictionary
- Status: Complete.
- Changed: `docs/data-dictionary.md` and `PROJECT_STATUS.md`.
- Behavior: Defines existing/planned authoritative tables, lifecycle, tenant/security
  ownership, atomic command boundaries, derived-value reconciliation, and expected
  indexes before transactional migrations begin.
- Data/security impact: Documentation only; no hosted or local schema changed. The
  model preserves service-only writes, same-shop composite references, append-only
  financial/stock/audit records, and forbids client-supplied authority or totals.
- Verification: Cross-checked against execution-plan Tasks 2.1–3.6 and current Phase 1
  schema. Deterministic coverage check passed for 35 required objects, 29 unique
  detailed table headings, and D1–D11; `git diff --check` passed.
- Next: Obtain and record Task 2.2 business-policy decisions, beginning with D1–D3.

### 2026-07-24 — Implement and deploy role and tenant authorization matrix
- Status: Complete.
- Changed: `docs/authorization-matrix.md`, migration
  `20260724101500_disabled_user_rls_lockdown.sql`,
  `supabase/tests/database/authorization_matrix.test.sql`, and `PROJECT_STATUS.md`.
- Behavior: Every exposed table and service RPC now has a documented permission rule
  and executable coverage. Disabled stale sessions are denied even self-profile and
  self-membership reads; direct/forged writes and immutable-row mutations are tested.
- Data/security impact: Adds `private.is_active_user()` and tightens profile/membership
  read policies without granting any new client table or RPC privilege.
- Verification: Bundled `pglast` parsed both new SQL files; assertion count equals the
  declared plan of 42; GitHub run `30071770493` passed Edge, fresh-migration, lint, and
  all pgTAP gates. Hosted histories match through `20260724101500`; linked lint is clean.
- Next: Begin Task 2.1 canonical data dictionary.

### 2026-07-24 — Close hosted PIN-login acceptance gate
- Status: Complete.
- Changed: hosted `pin-login`, ignored secure verifier, authentication contract, and
  `PROJECT_STATUS.md`.
- Behavior: Operator-only acceptance now proves exact subject, one-time exchange
  rejection, refresh continuity, and authenticated profile equality; normal login
  remains a single exchange with no diagnostic fields.
- Data/security impact: No PIN, token hash, access/refresh token, or Auth body was
  persisted or logged. Both temporary operator secret names are absent after cleanup.
- Verification: Local Deno passed 25 tests after safe-code refinement; CI runs
  `30070481674` and `30070939071` passed all gates; hosted safe result contains four
  true checks for subject, one-time rejection, refresh, and profile.
- Next: Execute Task 1.6 authorization matrix and fresh-database security tests.

### 2026-07-24 — Refine hosted Auth failure diagnosis
- Status: Partial; safe error-code parser implemented, verification/deployment pending.
- Changed: `pin-login` core/index/tests, authentication contract, and status.
- Behavior: Trusted diagnostics may append only a strictly sanitized upstream Auth
  machine code to the existing status stage; ordinary clients remain generic.
- Data/security impact: No Auth message/body, email, identifier, PIN, token, or secret
  is returned or logged. The prior temporary diagnostic secret was removed.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, all three
  function type-checks, and all 25 tests; `git diff --check` passed.
- Next: Verify/deploy and repeat the masked acceptance request once.

### 2026-07-24 — Add operator-only one-time exchange proof
- Status: Complete; deployed and proven against hosted Auth.
- Changed: `pin-login` core/index/tests, `docs/authentication.md`, and
  `PROJECT_STATUS.md`.
- Behavior: A trusted temporary diagnostic request performs a second redemption of the
  already-consumed generated email token and succeeds only when hosted Auth rejects it.
  Normal PIN login still performs exactly one token exchange.
- Data/security impact: Only a boolean proof may be returned to the trusted operator;
  token hashes, Auth bodies, PINs, and sessions remain server-only and unlogged.
- Verification: Local Deno passed 24 tests; CI run `30070481674` passed all gates;
  deployed function proved one-time rejection, refresh subject equality, and profile
  equality. Follow-up safe diagnostic CI run `30070939071` also passed.
- Next: Complete Task 1.6 authorization matrix and executable coverage.

### 2026-07-22 — Deploy and close Task 1.4 account administration
- Status: Complete.
- Changed: hosted project `zniqkuwktvincjndcgpu`, Task 1.4 migration/function/tests,
  administration contract, and `PROJECT_STATUS.md`.
- Behavior: Hosted backend now supports hierarchy-safe disable, re-enable, and PIN
  reset with reauthentication, rate limits, idempotency, session revocation, generic
  failures, immutable audit, and protected Super Admin targets.
- Data/security impact: Added private request/rate state and service-only RPCs; hosted
  smoke verification mutated no application row and anonymous access remains denied.
- Verification: Deno passed 23 tests; pglast parsed migration/tests; GitHub run
  `29941998214` passed fresh migration/lint/all pgTAP; hosted histories match, linked
  lint is clean, deployment succeeded, and anonymous smoke returned 401.
- Next: Execute Task 1.5 hosted PIN-login acceptance evidence, then replace Android
  preview authentication.

### 2026-07-22 — Implement Task 1.4 account-administration Edge handler
- Status: Partial; handler and local unit verification complete, database CI pending.
- Changed: `manage-accounts` core/index/config, function task config, parser tests, and
  `PROJECT_STATUS.md`.
- Behavior: Validates exact requests/project key/session, derives a source fingerprint,
  obtains a service-only reservation, verifies the actor PIN with the shared helper,
  hashes only reset PINs, atomically applies the action, and returns safe generic output.
- Data/security impact: No hosted change. PINs/verifiers/service credentials remain
  server-only; authorization and target role/shop remain database-derived.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, all three
  function type-checks, and all 23 tests.
- Next: Format/type-check/test Edge code, parse SQL, and run fresh-database CI.

### 2026-07-22 — Add Task 1.4 database acceptance suite
- Status: Partial; first CI run reached pgTAP and fixture-order correction is pending.
- Changed: `account_administration.test.sql` and `PROJECT_STATUS.md`.
- Behavior: Covers service-only privileges, hierarchy allow/deny paths, protected Super
  Admin targets, disable mutation, safe/unique audit, idempotent retry, same-shop Owner
  PIN reset, lock clearing, session-revocation statement, and atomic rate limiting.
- Data/security impact: Tests only; hosted state unchanged.
- Verification: pglast parsed the original suite. CI run `29941460301` applied and
  linted the migration successfully, then correctly denied a disabled Owner used by a
  later fixture. The corrected 27-assertion suite now re-enables that Owner first;
  CI run `29941756699` passed through that fix and all preceding assertions, then found
  the unavailable pgTAP `like` helper. It is replaced with supported `matches`; rerun
  pending.
- Next: Parse migration/tests, correct SQL issues, then implement `manage-accounts`.

### 2026-07-22 — Draft Task 1.4 account-administration database layer
- Status: Partial; forward migration drafted, verification and Edge integration pending.
- Changed: `20260722224500_account_administration.sql` and `PROJECT_STATUS.md`.
- Behavior: Added service-only idempotent prepare/fail/apply RPCs, authoritative
  Super-Admin-to-Owner and Owner-to-Salesman hierarchy derivation, per-actor/source
  reauthentication limits, disable/re-enable/PIN rotation, refresh-session deletion,
  last-Super-Admin protection, and append-only safe audit actions.
- Data/security impact: Migration not deployed. Private verifier/rate/request state is
  unavailable to Android; role/shop inputs remain server-derived; Super Admin targets
  are denied pending an explicit recovery path.
- Verification: pglast parsed the forward migration; runtime verification remains.
- Next: Add pgTAP coverage, parse SQL, then implement the Edge handler.

### 2026-07-22 — Define Task 1.4 account-administration contract
- Status: Partial; security/API design complete, implementation pending.
- Changed: `docs/account-administration.md` and `PROJECT_STATUS.md`.
- Behavior: Defined exact disable/re-enable/reset request shapes, authoritative
  hierarchy, actor-PIN reauthentication, rate limiting, idempotency, session revocation,
  last-Super-Admin protection, generic errors, audit content, and safe responses.
- Data/security impact: Documentation only. The design keeps role/shop derivation and
  verifier access server-side and forbids first-release Super Admin targets.
- Verification: Contract cross-checked against current profile, membership, login,
  verifier, RLS, session, and audit structures; executable verification is pending.
- Next: Add the administration state/RPC migration and pgTAP coverage.

### 2026-07-22 — Correct raw Auth session-link parsing
- Status: Complete.
- Changed: `pin-login` core/index/tests, `docs/authentication.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Correct-PIN session establishment now reads `hashed_token` from the raw
  GoTrue response rather than requiring the Supabase client wrapper and uses the
  documented email token-hash exchange contract.
- Data/security impact: Generated links and token hashes remain server-only. Normal
  failures stay generic, and the completed diagnostic secret was removed.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, both
  function type-checks, and all 20 tests; `git diff --check` passed; corrected
  `pin-login` deployed successfully; masked hosted login returned a subject-matched
  session and the authenticated profile matched the expected Super Admin.
- Next: Begin Task 1.4 account disable, re-enable, and PIN reset.

### 2026-07-22 — Add secure PIN-login stage diagnostics
- Status: Complete; the diagnostic identified the response-shape mismatch and its
  temporary hosted secret was removed.
- Changed: `supabase/functions/pin-login/core.ts`, `index.ts`, Edge tests,
  `docs/authentication.md`, ignored verifier helper, and `PROJECT_STATUS.md`.
- Behavior: A caller proving a temporary operator diagnostic secret may receive only a
  developer-authored failure stage; ordinary app callers retain the generic 503 body.
- Data/security impact: No PIN, token, Auth body, verifier, identifier, or database
  error is returned. The temporary hosted secret is designed for immediate removal.
- Verification: Pinned Deno 2.4.0 `deno task check` passed formatting, lint, both
  function type-checks, and all 19 tests. Supabase CLI deployed `pin-login`
  successfully to project `zniqkuwktvincjndcgpu`; the token-gated stage identified
  `auth-link-result`, and the corrected masked login later passed.
- Next: Retain the inactive token-gated mechanism for safe operator troubleshooting;
  proceed with Task 1.4.

### 2026-07-22 — Adopt hosted Auth-generated provisioning subjects
- Status: Partial; local static/Edge verification passes, fresh database CI pending.
- Changed: forward migration `20260722154500_auth_generated_provisioning_subjects.sql`,
  `manage-users`, pgTAP coverage, `docs/account-provisioning.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Replaced cloud-blocked custom-ID Auth creation with supported collection
  creation. The idempotency request remains stable while a service-role-only RPC
  validates and attaches the Auth-generated UUID. Retries reconcile an ambiguous Auth
  response by deterministic internal email plus immutable request marker before any
  compensation or finalization.
- Data/security impact: No direct `auth.users` insert is used. Attachment/finalization
  prove exact email, marker, and subject; compensation deletes only that marked user.
  Existing failed reservations are safely reset to an unattached placeholder on retry.
- Verification: Hosted version 3 proved the root cause as
  `auth-user-create-403`; pglast parsed the forward migration and updated pgTAP suite;
  `deno task check` passed formatting, lint, type-checking, and all 17 Edge tests;
  `git diff --check` passed; linked dry-run selected only migration
  `20260722154500`. Local database runtime remains unavailable without Docker.
- Next: Require fresh CI, deploy the forward migration and function, then complete the
  controlled bootstrap.

### 2026-07-22 — Refine hosted Auth bootstrap diagnostics
- Status: Partial; local verification passes, deployment pending.
- Changed: `supabase/functions/manage-users/index.ts`,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Token-gated bootstrap diagnostics now distinguish managed Auth lookup,
  create-response status, conflict reconciliation, and invalid safe result stages.
- Data/security impact: Only developer-authored stage names and an upstream HTTP status
  may be returned to a proven bootstrap operator. Auth response bodies, emails, user
  identifiers, credentials, tokens, and request data remain excluded.
- Verification: `deno task check` passed formatting, lint, type-checking, and all 17
  Edge tests; `git diff --check` passed.
- Next: Publish, require CI, deploy, and repeat the controlled bootstrap to identify the
  exact Auth operation failure before implementing a fix.

### 2026-07-22 — Add token-gated bootstrap stage diagnostics
- Status: Complete; diagnostic deployed for controlled bootstrap troubleshooting.
- Changed: `supabase/functions/manage-users/core.ts`, `index.ts`, tests,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: A bootstrap caller that has already proven the one-time operator token may
  receive only the developer-authored failing stage with a generic operation error.
  Normal, unauthenticated, and invalid-token callers receive no stage detail.
- Data/security impact: No PIN, token, header, request body, identifier, verifier, or
  database error is returned. The prior hosted attempt was compensated: table statistics
  show one failed reservation and zero profiles, credentials, memberships, and audits;
  secret-name inspection confirms the bootstrap token was removed.
- Verification: `deno task check` passed formatting, lint, type-checking, and all 17
  Edge tests; `git diff --check` passed; GitHub Actions run `29934652041` passed Edge,
  fresh migration, database lint, and all pgTAP gates in 1m39s; `manage-users` version
  2 deployed successfully.
- Next: Repeat the masked bootstrap once and use the safe stage to fix the underlying
  hosted failure.

### 2026-07-22 — Deploy Task 1.3 provisioning backend
- Status: Partial; deployment and unauthenticated denial checks pass, controlled
  bootstrap plus role-path verification pending.
- Changed: hosted Supabase project `zniqkuwktvincjndcgpu`, ignored local secure-input
  helper `.tooling/bootstrap-superadmin.ps1`, `docs/account-provisioning.md`, and
  `PROJECT_STATUS.md`.
- Behavior: Applied the managed-account provisioning migration and deployed
  `manage-users` version 1. The function now exposes the validated provisioning
  boundary while authenticated role operations remain unusable until the controlled
  first Super Admin bootstrap is completed.
- Data/security impact: Created server-only provisioning reservation/audit state and
  service-role-only RPCs. No application user, PIN verifier, bootstrap token, or audit
  event was created during deployment.
- Verification: Linked dry-run selected only migration `20260721090000`; push applied
  it; local/remote histories match through that migration; hosted lint found no schema
  errors; function list reports `manage-users` ACTIVE at version 1. Hosted HTTP checks
  returned `401` for an invalid project key and `401 UNAUTHORIZED` for a valid key
  without a user session. A masked local bootstrap prompt was cancelled because the
  operator is remote by phone; its process was terminated, no safe success record was
  produced, and hosted secret-name/digest inspection confirmed
  `GDAD_BOOTSTRAP_TOKEN` is absent. No credential value was printed or committed.
- Next: When secure local desktop input is available, run the masked helper, bootstrap
  once, confirm PIN-login subject equality, remove the one-time token, then verify
  authorized and denied hierarchy paths before Task 1.4.

### 2026-07-22 — Correct Task 1.3 pgTAP execution roles
- Status: Complete.
- Changed: `supabase/tests/database/account_provisioning.test.sql`,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Provisioning RPC acceptance checks still execute as `service_role`, while
  direct assertions against the intentionally protected `private` schema now execute
  only as the pgTAP test administrator.
- Data/security impact: None; production migration privileges are unchanged. The
  failure confirmed that `service_role` cannot directly inspect private credential or
  audit tables and must use the security-definer provisioning RPCs.
- Verification: GitHub Actions run `29921388428` applied all migrations and passed
  database lint, but stopped at assertion 21 because the test attempted a direct
  `private.login_credentials` read as `service_role`. After correction, pglast parsed
  the migration and test, `deno task check` passed formatting/lint/type-check plus all
  16 tests, and `build-apk.ps1` passed all 44 Android tasks. Replacement GitHub Actions
  run `29922170046` then applied all migrations to fresh Postgres, passed database
  lint, and passed all four pgTAP suites in 1m48s.
- Next: Deploy the verified migration and `manage-users` function to the linked
  development project, then complete hosted allowed/denied-path verification.

### 2026-07-22 — Implement execution-plan Task 1.3 account provisioning
- Status: Partial; repository and fresh-database checks pass, hosted deployment pending.
- Changed: local-only migration `20260721090000_managed_user_provisioning.sql`,
  `supabase/functions/manage-users/`, Edge/database tests and check configuration,
  `docs/account-provisioning.md`, and `PROJECT_STATUS.md`.
- Behavior: Added idempotent reservations/finalization for controlled Super Admin
  bootstrap, Super Admin→Owner, and Owner→Salesman creation, with authority rechecks,
  collision-safe managed Auth subjects, guarded compensation that never
  deletes unrelated Auth users, compensation state, and immutable safe audit.
- Failure handling: ambiguous finalization is reconciled before compensation, and the
  void failure-marking RPC accepts an empty response instead of falsely reporting a
  compensation failure.
- Data/security impact: Migration remains undeployed. Tables/RPCs are server-only;
  PIN/verifier/token/secret values are excluded from audit metadata.
- Verification: Deno checks passed 16 tests; SQL migration/test grammar passed; linked
  dry-run selected only the new migration; no secrets found. GitHub Actions run
  `29922170046` applied all migrations to fresh Postgres, passed database lint, and
  passed all pgTAP suites after the test-role correction.
- Next: Deploy the migration and `manage-users` function to development, verify hosted
  allowed/denied role paths, and only then begin Task 1.4.

### 2026-07-22 — Implement execution-plan Task 1.2 shared PIN helper
- Status: Complete.
- Changed: `supabase/functions/_shared/pin.ts`, `pin-login` and `manage-users` callers,
  `supabase/functions/deno.json`, Edge tests, and `PROJECT_STATUS.md`.
- Behavior: Centralized login-ID/PIN normalization, pepper version, HMAC material,
  Argon2id creation, random salting, and fail-closed verification for both login and
  provisioning.
- Data/security impact: Raw PINs and pepper remain function-memory-only; no hosted state
  changed and no secret/test PIN is intended for deployment.
- Verification: Deno format/lint/type-check passed; 11 tests passed and 0 failed.
- Next: Task 1.3 privileged idempotent account provisioning.

### 2026-07-22 — Complete execution-plan Task 1.1
- Status: Complete.
- Changed: `docs/authentication.md` and `PROJECT_STATUS.md`.
- Behavior: Selected one direct-token Android PIN session flow, specified secure import
  and encrypted persistence, and defined restore, refresh, expiry, revocation, logout,
  offline, concurrency, and future callback behavior.
- Data/security impact: No hosted state changed. The PIN-only flow exposes no browser or
  external intent; the future callback stays disabled and exact-match only.
- Verification: Documentation contract assertions and UTF-8 validation passed.
- Next: Task 1.2 shared PIN verifier helper and failure-path tests.

### 2026-07-22 — Complete execution-plan Task 0.3
- Status: Complete with local database-runtime delegation documented.
- Changed: formatted pre-existing `supabase/functions/manage-users/core.ts` and updated
  `PROJECT_STATUS.md`; installed ignored Deno 2.4.0 tooling.
- Behavior: Established a reproducible passing Android and Edge baseline before new
  feature work.
- Data/security impact: None; hosted database lint and GitHub CI inspection were
  read-only and no credential values were emitted.
- Verification: `build-apk.ps1` passed; `deno task check` passed five tests; hosted lint
  was clean; current-HEAD database CI run `29423495797` succeeded.
- Next: Task 1.1 Android Auth redirect and session strategy.

### 2026-07-22 — Complete execution-plan Tasks 0.1 and 0.2
- Status: Complete.
- Changed: `PROJECT_STATUS.md` only; unrelated launcher and provisioning work preserved.
- Behavior: Verified the canonical checkout and reconciled repository, hosted migration,
  Edge Function, code-map, and UTF-8 status against the supplied execution plan.
- Data/security impact: None; hosted checks were read-only and emitted no credentials.
- Verification: `git remote -v`, branch/log/path/status inspection, UTF-8 code-point
  scan, linked migration list, and hosted Edge Function list all completed.
- Next: Task 0.3 baseline Android, Deno, database-lint, and tooling verification.

### 2026-07-21 — Add branded Android launcher icon
- Status: Complete for launcher branding; app launch readiness remains blocked.
- Changed: `app/src/main/AndroidManifest.xml`, Android `drawable-nodpi`/`mipmap-*`
  launcher assets, `tools/generate_launcher_icons.py`, and `PROJECT_STATUS.md`.
- Behavior: Android launchers now display the supplied GDAD Bags branding for standard
  and round icon presentations.
- Data/security impact: None.
- Verification: `build-apk.ps1` passed all Android unit tests and assembled the debug
  APK. `aapt dump badging` confirmed the app label and all five packaged icon densities.
- Next: Install the test APK on an Android device and visually confirm both standard
  and round launcher treatments before replacing preview authentication.

### 2026-07-15 — Draft PIN login Edge Function and atomic lockout backend
- Status: Partial overall; B3.3 is implemented/hosted and awaits CI, while B3.2 lacks a happy-path fixture.
- Changed: `supabase/config.toml`, `supabase/functions/**`, migration
  `20260715143000_pin_login_lockout.sql`, forward fix `20260715151000`, pgTAP coverage,
  database CI, and this file.
- Behavior: Added strict request validation, publishable-key validation, HMAC-peppered
  Argon2id verification, timing-safe unknown-user work, Auth magic-link token exchange,
  per-source throttling, and atomic per-account failure/reset behavior.
- Data/security impact: Server credentials and peppers stay in Edge secrets; PIN hashes,
  source HMACs, and counters remain inaccessible to Android roles.
- Verification: Local Deno format/lint/type-check and five tests pass; SQL/YAML grammar pass.
  First hosted deploy exposed an import-map bundling mismatch; explicit pinned npm import applied.
- Diagnostic safety: Internal failures log only developer-authored stage messages; request/PIN
  values, server keys, one-time tokens, and session tokens are excluded.
- Hosted diagnostic: Deployed handler reaches PostgREST but `pin_login_prepare` fails; status-only
  logging added to distinguish RPC discovery/authorization/SQL failure without logging bodies.
- Root cause/fix: Hosted SQL verification found PL/pgSQL ambiguity in the conflict target.
  Forward migration `20260715151000_pin_login_prepare_conflict_fix.sql` now targets the
  named primary-key constraint and makes the unknown-user `source_limited` result non-null.
- Test expansion: pgTAP now provisions a synthetic Auth credential and asserts fifth-failure
  lockout plus success reset; fresh-Postgres execution is pending CI.
- CI run `29422667662` failed only at Deno lint because inline `npm:` imports are forbidden.
  Added function-local `pin-login/deno.json` so both repository lint and Supabase remote
  bundling resolve the pinned alias; local Deno and hosted bundling now pass.
- Hosted result: Both migrations deployed, remote lint is clean, function version 5 is
  ACTIVE, and malformed/invalid-key/unknown-user paths return `400`/`401`/generic `401`.
- CI run `29423183238` passed format/lint but current Deno rejected generic
  `Uint8Array<ArrayBufferLike>` Web Crypto key input. HMAC now copies identical bytes
  into an explicit `ArrayBuffer` for cross-Deno type compatibility; rerun pending.
- Verification gap: Correct-PIN Auth session establishment and one-time-token non-reuse
  require a managed user created by the upcoming privileged provisioning operation.
- Next: Push and pass fresh-Postgres/Deno CI, then implement B3.6 provisioning and use it
  for the B3.2 happy-path integration test before touching Android preview auth.

### 2026-07-15 — Finalize the production PIN authentication contract

- Status: Complete for B3.1; B3.2/B3.3 implementation pending.
- Changed: `docs/authentication.md`, `supabase/README.md`,
  `supabase/migrations/20260715121500_authentication_contract.sql`,
  `supabase/tests/database/authentication_contract.test.sql`, and `PROJECT_STATUS.md`.
- Behavior: Fixed the mapping from normalized app login IDs to immutable Supabase Auth
  subjects, selected a server-only one-time magic-link exchange for refreshable
  sessions, specified six-to-eight-digit PIN handling, HMAC peppering plus Argon2id,
  generic failures, session rules, and implementation acceptance tests.
- Data/security impact: PIN verifier rows now require Argon2id PHC format and store only
  the external pepper version. Pepper, PIN, one-time token, service credentials, and
  session tokens remain forbidden from client-readable storage and logs.
- Verification: Supabase dry-run selected only migration `20260715121500`; hosted push
  applied it; local/remote migration history matches; hosted lint found no schema
  errors; and the Android test/debug build passed in 1m4s. GitHub Actions run
  `29410151903` passed fresh migration application, database lint, and both pgTAP test
  files, including all seven new authentication assertions.
- Next: Implement B3.2/B3.3.

### 2026-07-15 — Configure and verify the Android Supabase development client

- Status: Complete for B1.7 client configuration and API-key connectivity verification.
- Changed: ignored `.tooling/gradle-user-home/gradle.properties`, `README.md`, generated
  debug APK, and `PROJECT_STATUS.md`.
- Behavior: Configured the Android debug build for hosted project
  `zniqkuwktvincjndcgpu` using its client-safe publishable key. The key value remains
  local and is not recorded in tracked files or this status document.
- Data/security impact: No hosted data changed. No secret/service-role credential was
  retrieved or stored. Row Level Security remains the security boundary for the
  publishable client key.
- Verification: Git confirmed the bundled Gradle user-home properties are ignored and
  zero tracked files contain the publishable key. Generated URL/key-format checks
  passed; `build-apk.ps1` succeeded in 2m23s; hosted Auth health/settings returned
  `200`; anonymous `shops` access returned `401` as intended.
- Next: Finalize the production PIN-to-Supabase-Auth identity design (B3.1).

### 2026-07-15 — Deploy and verify the hosted Supabase foundation

- Status: Complete for hosted project linking and the initial schema deployment.
- Changed: hosted project `zniqkuwktvincjndcgpu` and `PROJECT_STATUS.md`.
- Behavior: Linked the repository to the `Gdad Bags` development project in Northeast
  Asia (Seoul) and deployed migration `20260715084551_core_foundation.sql`.
- Data/security impact: Created the initial tenant, identity, product, FIFO inventory,
  movement, private PIN-verifier, privilege, and RLS structures in the hosted database.
  No credentials or tokens were committed. Hosted Auth config was deliberately not
  pushed while repository redirect URLs remain local-only.
- Verification: `supabase migration list --linked` showed matching local/remote
  migration `20260715084551`; remote database lint completed with no schema errors.
- Next: Supply the publishable key locally and complete B1.7 with a harmless
  authenticated client read.

### 2026-07-15 — Add Supabase database foundation and CI verification

- Status: Complete for the repository-side foundation; hosted project/link pending.
- Changed: `.gitignore`, `package.json`, `pnpm-lock.yaml`, `supabase/config.toml`,
  `supabase/seed.sql`, `supabase/README.md`,
  `supabase/migrations/20260715084551_core_foundation.sql`,
  `supabase/tests/database/core_foundation.test.sql`,
  `.github/workflows/database-tests.yml`, and `PROJECT_STATUS.md`.
- Behavior: Added the pinned CLI workspace, tenant/identity/product/FIFO schema,
  server-only PIN verifier storage, append-only inventory movement ledger, RLS helpers
  and policies, default-deny privileges, pgTAP coverage, and Docker-backed CI.
- Data/security impact: No hosted data changed. Anonymous access and direct Android
  mutations are denied; authenticated reads are tenant-scoped; PIN hashes remain in an
  unexposed private schema. Signup is disabled for admin-controlled account creation.
- Verification: SQL grammar parsed for the migration and test file; workflow YAML
  parsed; lockfile reinstall passed offline. GitHub Actions run `29403029753` then
  applied the migration to fresh Postgres, passed database lint, and passed all 24
  pgTAP assertions.
- Next: Create and link the hosted Supabase development project.

### 2026-07-15 — Connect workspace to canonical GitHub repository

- Status: Complete; canonical repository initialized and published on GitHub.
- Changed: `.gitignore`, Git repository metadata, and `PROJECT_STATUS.md`.
- Behavior: Initialized the workspace on `main`, configured
  `https://github.com/sanjubaba21/GDAD_APP.git` as `origin`, and excluded generated
  Kotlin state plus APK/AAB artifacts from source control.
- Data/security impact: No application data or credentials added. Generated binaries,
  local configuration, environment files, tooling, and build outputs remain ignored.
- Verification: The remote returned no refs before initialization. Initial commit
  `1d04635` was created after reviewing 22 staged source/configuration files and pushed
  successfully to `origin/main`; the local branch now tracks `origin/main`. Application
  tests were not rerun because this change only affects repository metadata and ignore
  rules.
- Next: Create the hosted Supabase development project (B1.1).

### 2026-07-15 — Select Supabase and add the Android client foundation

- Status: Partial; repository foundation complete, hosted project not yet created.
- Changed: `build.gradle.kts`, `app/build.gradle.kts`, `.gitignore`,
  `app/src/main/java/com/gdad/bags/data/remote/SupabaseClientProvider.kt`, `README.md`,
  and `PROJECT_STATUS.md`.
- Behavior: Replaced unused Firebase Auth/Firestore/Functions dependencies with
  Supabase Auth/PostgREST/Functions, Kotlin serialization, and Android Ktor. Added safe
  Gradle/environment configuration and guarded client initialization.
- Data/security impact: No database exists yet. Android accepts only the Supabase URL
  and publishable client key; secret and `service_role` keys are explicitly forbidden.
- Verification: Bundled Gradle `testDebugUnitTest --no-daemon --offline` succeeded in
  52 seconds and all 3 inventory tests remain passing. A subsequent
  `assembleDebug --no-daemon --offline` succeeded in 47 seconds. The first online
  resolution attempts timed out after downloading dependencies; orphan Gradle daemons
  were stopped before the successful offline verifications.
- Next: Create the hosted Supabase development project (B1.1), supply its URL and
  publishable key locally, and initialize the Supabase CLI workspace (B1.4).

### 2026-07-15 — Establish canonical project status and handoff

- Status: Complete
- Changed: `PROJECT_STATUS.md`
- Behavior: Added a verified implementation inventory, ordered backend/database
  backlog, agent update protocol, code map, risks, verification record, and next task.
- Data/security impact: None; documentation only.
- Verification: Content cross-checked against current source, Gradle configuration,
  README, test XML, build outputs, and a successful `testDebugUnitTest` invocation.
- Next: Originally Firestore schema design; superseded by the 2026-07-15 Supabase
  platform decision and the current Postgres backlog.
