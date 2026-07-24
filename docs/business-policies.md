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

## Pending decisions

| ID | Policy still required | Blocks |
|---|---|---|
| D7 | Backdating roles and accounting-period locks | Every posting RPC and business-date constraint |
| D8 | SKU/barcode normalization, uniqueness, and archived-code reuse | Product extension and product-management RPC |
| D9 | Salesman cost/profit/vendor visibility | RLS views and API response contracts |
| D10 | Rounding order and VAT/tax first-release scope | Sale/purchase money constraints and reporting |
| D11 | Notification and audit retention | Retention metadata, cleanup, and export operations |

## Change control

Changing an approved policy after its migration exists requires a forward migration,
updated protected operations, permission tests, UI acceptance changes, data repair or
reconciliation where applicable, and an appended decision-history entry. Never rewrite
an applied migration or silently reinterpret posted history.
