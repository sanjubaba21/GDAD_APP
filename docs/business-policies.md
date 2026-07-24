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

## Pending decisions

| ID | Policy still required | Blocks |
|---|---|---|
| D4 | Payment methods and cash/bank allocation | Payment/refund/vendor-payment and journal migrations |
| D5 | Sale return window, condition/restocking, and refund method | Return/refund migration and RPC |
| D6 | Purchase cancellation, receiving tolerance, vendor returns/credit, payment limits, duplicate invoice | Purchasing migration and RPC |
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
