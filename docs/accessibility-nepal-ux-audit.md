# GDAD BAGS accessibility and Nepal UX audit

Audit date: 2026-07-29 (Asia/Kathmandu)

## Result

The automated accessibility and Nepal UX gate passes. The first-release UI now uses one
Kathmandu business clock, explicit `NPR` money labels, validated ISO dates, correct soft-keyboard
types, scroll-safe high-text layouts, semantic headings, selected filters, labelled unread state,
and TalkBack live regions for asynchronous status and errors.

A physical Android device was not attached during this audit. Final approval still requires one
manual device pass with TalkBack and 200% system font; automated semantics and Robolectric font
tests do not prove OEM rendering, spoken order, or hardware-keyboard focus behavior.

## Workflow matrix

| Workflow | Large-text/layout evidence | TalkBack/status evidence | Input and Nepal behavior |
| --- | --- | --- | --- |
| Restore/login | Full-height login is vertically scrollable; 200% font test reaches the sign-in action. | Loading and login errors are live regions; title/sign-in are headings; PIN remains obscured. | PIN uses `NumberPassword`; no credential is announced or persisted as plain text. |
| Dashboard/navigation | `LazyColumn` keeps every role action reachable. | App/identity headings, labelled unread count, assertive offline-failure notice, text-labelled cards/buttons. | Dashboard/report times are explicitly Nepal time. |
| Accounts/products | Lazy lists and scrollable dialogs; short action pairs use Material minimum targets. | Shared safe operation messages announce politely; protected errors announce assertively. | PIN fields use `NumberPassword`; price/threshold fields use decimal/number keyboards. |
| Purchasing/POS | Product quantity/price pairs are stacked instead of compressed side by side; checkout 200% font test reaches confirmation. | Server-result dialogs receive normal dialog focus; safe operation messages are live regions. | Quantity is numeric, money is decimal, contact is phone, and business/due dates use the shared Nepal ISO field. |
| Returns/stock | History filters are horizontally scrollable; mutation dialogs are vertically scrollable. | Selected options remain spoken; errors/status use the shared announcement components. | Quantity/cost keyboards are typed; invalid Nepal dates are explained and cannot be submitted. |
| Vendor finance/cash-bank | Ledger/history use lazy lists; every long form is vertically scrollable. | Loading, retry, mutation status, and immutable receipt dialogs have discoverable text labels. | Every operation defaults from Kathmandu time and validates its Nepal business date; amounts use decimal keyboards. |
| Reports/notifications | Report date fields are stacked; 200% font test keeps both dates and load action visible. Notification lists/categories scroll independently. | Report failures are assertive; notification filters expose selected-state semantics; detail navigation is text-labelled. | Reports use ISO Nepal dates; notification timestamps use `Asia/Kathmandu`; all money is explicit `NPR`. |

## Cross-cutting evidence

- No production `Icon`, `Image`, raw `clickable`, `toggleable`, or custom gesture target exists;
  interactive elements use Material buttons, cards, fields, chips, or navigation components with
  platform minimum touch-target semantics.
- Source order is the focus/read order. No manual focus override, invisible action, color-only
  status, or unlabeled icon control was found.
- `ContentStateHost` exposes polite loading/empty announcements and assertive error announcements.
  `StatusMessage` provides the same behavior for operation feedback across all feature screens.
- `BusinessDateField` labels the date as Nepal time, accepts only real `YYYY-MM-DD` calendar
  dates, uses an ASCII keyboard so the hyphens are enterable, and announces its corrective text.
- `NepalDateTime` derives business dates from `Asia/Kathmandu` regardless of phone time zone;
  tests cover the UTC instant immediately before and at Kathmandu midnight.
- `MoneyAmounts.formatNpr` formats integer paisa without floating point and prefixes `NPR`, not
  the regionally ambiguous `Rs` label.
- Theme contrast calculations are 7.10:1 for white on primary brown, 6.76:1 for primary brown on
  the warm background, and 19.99:1 for black on that background. These exceed WCAG AA normal-text
  contrast; other status text uses Material light-scheme semantic colors.
- Static scans found no replacement character or common mojibake sequence in production Kotlin/
  XML. Visible bullet, dash, and ellipsis characters are valid UTF-8 source characters.
- Every remote screen has an explicit loading, empty, error/retry, refresh, disabled-mutation, or
  safe operation state appropriate to slow/intermittent connections. Offline mutation remains
  limited to the separately documented safe outbox operations.
- `verifyReleaseAccessibilitySafety` runs before release builds and rejects device-local business
  clocks, ambiguous currency labels, mojibake, raw click targets, missing shared live regions, or
  date-owning screens that bypass the Nepal date field.

## Automated verification

- Focused domain/Compose suite: Kathmandu boundary/format validation, exact NPR formatting,
  loading/error/status live regions, invalid-date explanation, login/checkout/report at 200% font,
  and all existing role/receipt screen assertions.
- Release gate: `verifyReleaseAccessibilitySafety`, `verifyReleaseAuthSafety`,
  `verifyReleaseArtifactSafety`, full unit/Robolectric suite, release assembly, and Android lint.
- Android lint currently reports no accessibility issue; remaining warnings are dependency/tooling,
  launcher-shape, target/SDK availability, and unused-resource maintenance notices.

## Required physical-device sign-off

1. Enable TalkBack and traverse login, dashboard, one sale, one return, one report, and one error/
   retry path in spoken source order; verify no duplicate, silent, or credential-bearing utterance.
2. Set system font to 200%, repeat those workflows in portrait, and confirm every final action is
   reachable by scrolling without clipped labels or overlapping controls.
3. Connect a hardware keyboard or Switch Access and confirm forward/back focus order, dialog focus
   containment, text editing, and action activation.
4. Exercise a throttled/disconnected network and verify progress, retry, offline limitation, and
   restored-connection messages are announced exactly once.

Record the device model, Android version, app commit/APK SHA-256, tester, and result in
`PROJECT_STATUS.md` before closing Task 6.3.
