# GDAD Bags business policies

This is the authoritative Task 2.2 product-policy record. A policy is usable by schema,
RPC, permission, and UI work only after its status is **Approved**. Unapproved choices
block only the affected implementation.

## Approved decisions

### D1 — Stock availability

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Negative stock is forbidden for every role. A sale cannot post unless
  deterministic FIFO locking can allocate the full requested quantity from eligible
  positive-remaining lots.
- **Schema:** sale allocations must equal each sale-line quantity; lot remaining
  quantity stays nonnegative; no shortage/negative-stock record is required in the
  first-release schema.
- **RPC:** the atomic sale operation locks FIFO lots, rejects insufficient stock before
  writing any sale consequence, and rolls back the entire command.
- **Permissions:** neither Owner nor Super Admin receives a bypass through client input;
  a future override requires a new reviewed policy and migration.
- **UI/acceptance:** show available stock and a generic insufficient-stock result; keep
  the draft/cart for correction. Tests cover exact depletion, multi-lot allocation,
  insufficient quantity, concurrency, rollback, and retry.

### D2 — Price and discount authority

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Salesmen sell at the active configured product price and cannot apply a
  line discount, sale discount, or price override. Owners may apply audited nonnegative
  discounts or override unit price, but the final line and sale total cannot be negative.
- **Schema:** posted sale lines retain configured-price, effective-price, and discount
  snapshots; sale-level discount is represented separately. Checked server arithmetic
  reconciles subtotal, discounts, tax, and grand total.
- **RPC:** derive role and configured price from authoritative rows. Ignore/reject
  Salesman override fields. Owner changes require an explicit intent and safe audit
  metadata containing old/effective monetary values but no credentials.
- **Permissions:** Super Admin may inspect but does not implicitly transact for a shop;
  shop operation authority remains tied to an active Owner membership.
- **UI/acceptance:** Salesman price/discount controls are absent or read-only. Owner
  controls require confirmation. Tests cover forged role, Salesman override denial,
  Owner discount/override, zero total, negative-total rejection, overflow, and retry.

### D3 — Credit sales and partial payments

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Only an active Owner may post a credit sale. Credit sales require a
  normalized customer name, customer phone/contact identifier, and Nepal due date.
  Partial payments are allowed. Salesmen may post only sales fully paid at posting.
- **Schema:** sale stores immutable customer identity snapshots and due date when any
  amount remains due. Payment events are append-only; due is derived from the posted
  sale total and effective receipts/refunds, never freely editable.
- **RPC:** derive role; calculate tendered total and remaining due atomically; reject a
  Salesman sale unless effective posting payment equals the grand total; reject missing
  credit identity/due date or due dates earlier than the authorized business date.
- **Permissions:** later Owner-entered partial payments use a protected, idempotent
  receipt operation. Salesmen cannot create or alter credit terms.
- **UI/acceptance:** Salesman checkout requires full payment. Owner checkout may select
  credit, capture customer identity/due date, and later record partial payments. Tests
  cover full/partial/zero initial payment, due derivation, invalid dates, Salesman
  denial, overpayment handling after D4, and duplicate retries.

### D4 — Payment methods and allocation

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** First-release monetary settlement uses active same-shop cash or bank
  accounts only. Split tender is allowed by recording multiple payment rows, each
  allocated wholly to one account. A sale receipt or vendor payment cannot exceed the
  remaining payable amount; unapplied receipts and overpayment are rejected.
- **Schema:** each sale payment, refund, and vendor payment references exactly one
  active `cash` or `bank` account and one balanced journal transaction. Multiple rows
  may share the same source sale/bill command. Method must agree with account type.
- **RPC:** lock the source obligation and target accounts, derive remaining due, reject
  zero/negative or excess payment, and post all split rows plus balanced journal entries
  atomically. Reversal posts compensating rows; it never edits the original payment.
- **Permissions:** active Owners may record sale credit receipts and vendor payments;
  Salesmen may record only the payment rows included in their fully paid sale command.
  Client-supplied account IDs must belong to the authoritative shop and be permitted
  for the operation.
- **UI/acceptance:** checkout supports one or more cash/bank tenders whose exact sum is
  shown before posting. Tests cover cash, bank, split tender, type/account mismatch,
  cross-shop account, overpayment, concurrent payment, reversal, and retry.

### D5 — Sale returns, disposition, and refunds

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Only an active Owner may post a sale return, within 30 calendar days of
  the original Nepal business date. Sellable items restore their exact original FIFO
  lots; damaged items do not restore saleable stock. A cash/bank refund cannot exceed
  the amount effectively paid for the returned value.
- **Schema:** return header stores original sale, business date, reason, and disposition;
  lines/allocation rows enforce cumulative quantity limits. `sellable` restoration is
  lot-linked. `damaged` quantity is recorded as non-restocked return evidence with no
  positive saleable-lot restoration. Refunds reference a cash/bank account and journal.
- **RPC:** verify Owner/shop/window and cumulative returned quantities. For a credit
  sale, returned value first reduces outstanding due; only value already effectively
  paid is eligible for cash/bank refund. Restore sellable allocations, append all stock
  and journal consequences, update derived sale state, and audit atomically.
- **Permissions:** Salesmen may view returns allowed by shop policy but cannot create,
  approve, reverse, or refund them. No role bypasses the 30-day window in first release.
- **UI/acceptance:** Owner selects original sale lines, quantity, sellable/damaged
  condition, reason, and refund account when money is due. Tests cover day 30/day 31,
  partial/repeated returns, mixed disposition, exact lot restoration, credit due
  reduction, refund cap, over-return, cross-shop, reversal, concurrency, and retry.

### D6 — Purchasing, receiving, vendor returns, and invoice uniqueness

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Only an active Owner may manage purchasing. Received quantity cannot
  exceed ordered quantity, vendor payment cannot exceed outstanding vendor due, and a
  normalized external invoice reference is unique per shop/vendor. Only drafts may be
  cancelled; posted purchases require a linked reversal or vendor return.
- **Schema:** unique `(shop_id,vendor_id,normalized_invoice_reference)` for nonblank
  references; receipt and return allocations enforce cumulative ordered/received/lot
  limits. Posted bills/receipts/payments/lots are immutable and retain reversal/source
  links. Vendor due remains derived.
- **RPC:** lock vendor, bill lines, receipts/lots, and obligations deterministically;
  reject over-receipt/overpayment/duplicate invoice and unavailable return quantities.
  Receiving, FIFO lots, movements, payment/due, balanced journal, and audit commit in
  one transaction. Reversal/return posts compensating records.
- **Permissions:** Salesmen cannot create/edit/cancel/receive/pay/return purchases.
  Owner operations derive shop and vendor authority; forged cross-shop identifiers fail.
- **UI/acceptance:** draft editing/cancellation is separate from posted history. Tests
  cover partial/full receipts, exact lot cost/quantity, duplicate normalized invoice,
  over-receipt/payment, vendor return, reversal, cross-shop input, rollback, and retry.

### D7 — Backdating and accounting-period locks

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Salesmen may post only on the current `Asia/Kathmandu` business date.
  Owners may backdate by at most seven calendar days, never future-date, and only inside
  an open accounting period. Owners may close a period; reopening is an audited Super
  Admin recovery action and does not modify existing records.
- **Initial provisioning:** A shop with no accounting-period history receives one open
  operating period beginning seven Nepal calendar days before provisioning. The period
  remains open until an explicit close workflow; existing open or closed period history
  is never replaced or reopened automatically.
- **Schema:** shop accounting periods have non-overlapping date bounds, state
  `open|closed`, close actor/time, and optional recovery reopen actor/time/reason.
  Business records store both authoritative `occurred_at` and approved `business_date`.
- **RPC:** derive Nepal today server-side; validate role, seven-day limit, and open
  period while holding the period row lock. Period close checks unresolved drafts/jobs;
  recovery reopen requires Super Admin reauthentication and audit.
- **Permissions:** clients cannot edit business dates or period state directly.
  Super Admin recovery changes only the period control state, not shop transactions.
- **UI/acceptance:** Salesman date is fixed to today; Owner date picker shows the valid
  open seven-day window. Tests cover Nepal midnight, today/day 7/day 8, future date,
  closed period, concurrent close/post, unauthorized reopen, audit, and retry.

### D8 — SKU and barcode identity

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** SKU is required and unique per shop. Barcode is optional and unique per
  shop when present. Both are normalized by trimming, Unicode normalization, collapsing
  internal whitespace, and case-folding for comparison. A code is never reusable after
  product archival.
- **Schema:** retain display value plus normalized comparison value; unique indexes on
  `(shop_id,normalized_sku)` and non-null `(shop_id,normalized_barcode)` include archived
  products. Blank normalized codes fail; explicit length/character bounds are shared
  with Android and import validation.
- **RPC:** server normalizes and checks codes inside product create/update/archive;
  imports use the same helper. Changing a code is audited and cannot collide with any
  active or archived product.
- **Permissions:** only active Owners may create/update/archive products in first
  release; Salesmen cannot forge code changes.
- **UI/acceptance:** preserve friendly display form while showing normalized collision
  errors. Tests cover case/space/Unicode equivalents, optional blank barcode, archived
  collision, cross-shop reuse, concurrent create, import, and retry.

### D9 — Salesman cost, profit, vendor, and finance visibility

- **Status:** Approved by product owner on 2026-07-24.
- **Policy:** Salesmen may read own-shop products, selling prices, stock quantities, and
  shop sales needed for work. They cannot read unit costs, FIFO allocation costs,
  profit, vendors, purchasing, financial account balances, journal entries, or expense
  details. Owners see those domains for owned shops; Super Admin access follows the
  administrative/reporting contract and remains audited where sensitive.
- **Schema:** cost/profit/vendor/finance data is never placed in Salesman-readable base
  responses. Use restricted tables/views/RPC projections rather than UI-only hiding.
- **RPC:** role-shaped sale/product responses omit restricted fields; report and vendor/
  finance operations require Owner authority and derive shop server-side.
- **Permissions:** RLS plus column/view/function grants deny restricted domains to
  Salesmen even with crafted PostgREST queries.
- **UI/acceptance:** Salesman screens contain no cost/profit/vendor/account controls or
  cached values. Tests cover direct table/column/view/RPC attempts, forged role/shop,
  response serialization, offline cache cleanup after role change, and Owner access.

### D10 — Rounding and tax scope

- **Status:** Approved by product owner on 2026-07-24 under “approve everything.”
- **Policy:** First release has no VAT/tax module. All prices and discounts are absolute
  whole Nepalese paisa; percentage discount input, if shown, is converted to an explicit
  paisa amount before posting using half-up rounding and the user confirms the result.
- **Schema:** tax fields are fixed to zero or omitted from first-release write models;
  price × quantity uses checked `bigint`, line discount applies per line, then an
  optional absolute sale discount applies to the subtotal. Grand total cannot be
  negative. Posted snapshots are immutable.
- **RPC:** calculate each line gross/net, sum checked line nets, apply sale discount,
  and reject overflow, negative totals, or nonzero tax. Never accept a client total.
- **Permissions:** only D2-authorized Owners submit discount intent; tax behavior cannot
  be enabled through payload fields.
- **UI/acceptance:** display two-decimal rupee values backed by paisa and label the bill
  non-VAT. Tests cover half-paisa percentage conversion at UI boundary, zero/maximum
  discounts, overflow, nonzero tax rejection, and server/client formatting parity.

### D11 — Notification and audit retention

- **Status:** Approved by product owner on 2026-07-24 under “approve everything.”
- **Policy:** Notifications expire 90 days after creation and may be purged after that
  point; their source business record remains authoritative. Audit events have no
  automatic deletion in first release. A production legal/retention review is required
  before introducing any audit purge policy.
- **Schema:** notifications require `expires_at = created_at + interval '90 days'`
  unless a stricter system category rule is versioned. Audits are append-only with no
  expiry column driving deletion; safe export metadata may be added later.
- **RPC:** notification cleanup deletes only expired notification rows in bounded
  batches. No application RPC deletes audit events. Payload validation rejects secrets
  and unnecessary sensitive data.
- **Permissions:** recipients may mark notifications read but cannot change expiry or
  delete source/audit evidence. Audit append is backend-only.
- **UI/acceptance:** notification history communicates its rolling window; audit is an
  Owner/Super Admin administrative view if exposed. Tests cover expiry boundary,
  unread expiry, source survival, forged cleanup, audit immutability, safe payloads,
  and bounded cleanup retries.

## Pending decisions

None. D1–D11 are approved. Any change follows the forward-only change-control process.

## Task 3.7 cash-availability rule

- **Status:** Conservative first-release implementation decision recorded 2026-07-27.
- **Policy:** Cash and bank accounts cannot be overdrawn. Expenses, withdrawals, transfer
  sources, and any reversal that removes previously credited funds require sufficient
  derived balance while the affected accounts are locked. Deposits add funds. Balances
  are always derived from immutable balanced journal entries; there is no writable
  balance column.
- **Acceptance:** exact available balance succeeds; one paisa above fails atomically;
  concurrent debits serialize; failed and duplicate requests create no partial journal,
  expense, request result, or audit evidence.

## Change control

Changing an approved policy after its migration exists requires a forward migration,
updated protected operations, permission tests, UI acceptance changes, data repair or
reconciliation where applicable, and an appended decision-history entry. Never rewrite
an applied migration or silently reinterpret posted history.
