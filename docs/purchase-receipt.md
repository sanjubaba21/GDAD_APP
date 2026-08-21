# Purchase receipt backend contract

Migration `20260727180000_atomic_purchase_receipt.sql` exposes the authenticated RPC
`public.post_purchase_receipt`. Android must call this RPC for first-release purchase
entry; direct mutation of purchasing, FIFO, inventory, payment, journal, and audit
tables remains denied.

## Request

- `p_idempotency_key`: caller-generated retry key, 1–160 trimmed characters. Persist it
  with the pending client operation and reuse it until the authoritative result arrives.
- `p_shop_id`: untrusted shop intent. The database re-derives the actor and requires an
  active Owner membership in the active shop.
- `p_vendor_id`: active vendor in the same shop.
- `p_invoice_reference`: optional vendor reference. Whitespace-only input is invalid;
  normalized duplicates for the same vendor are rejected.
- `p_business_date`: Nepal business date, not in the future, no more than seven days
  old, and inside an open accounting period.
- `p_lines`: JSON array of 1–100 unique products. Every item contains `product_id`, a
  positive integer `quantity`, and a non-negative whole-paisa `unit_cost_paisa`.
- `p_payment_amount_paisa`: optional immediate payment, default zero. It cannot exceed
  the server-calculated total.
- `p_payment_method`: required exactly when payment is positive; `cash` or `bank`.

Example line payload:

```json
[
  {"product_id":"00000000-0000-0000-0000-000000000000","quantity":2,"unit_cost_paisa":600}
]
```

## Authoritative result and atomic effects

The JSON result contains `purchase_bill_id`, `purchase_receipt_id`, optional
`vendor_payment_id`, `grand_total_paisa`, `paid_paisa`, `due_paisa`, and `line_count`.
The client must display these server values rather than recomputing success locally.

One successful transaction creates the received bill and immutable snapshot lines,
receipt and lines, one exact FIFO lot and positive inventory movement per line, the
stock projection, balanced inventory/payable journal, optional allocated vendor
payment and balanced cash/bank journal, a request result, and a secret-free audit event.
The initial operation intentionally has zero purchase discount and tax so billed cost,
receipt cost, and FIFO unit cost remain identical.

Products are locked in stable ID order. The request row is locked by shop/key, so an
exact concurrent or later retry cannot duplicate stock or money. An exact retry returns
the original JSON; reuse with a changed normalized payload fails.

## Failure categories

- `42501`: unauthenticated/non-Owner, inactive membership/shop, or cross-shop/inactive
  vendor or product.
- `22023`: invalid key, date, invoice reference, payment intent, lines, or changed retry.
- `22003`: exact-arithmetic overflow.
- `23505`: normalized duplicate vendor invoice.
- `23514`: payment exceeds the purchase total.
- `55000`: closed period, unavailable ledger account, or conflicting in-progress state.

All failures roll back the complete operation. Clients should map SQL states to stable
user-facing categories without exposing hidden tenant details.

Application-created shops receive protected system accounts and one initial open accounting
period atomically. Migration `20260821103000_initial_accounting_period_provisioning.sql` also
backfills only existing shops with no period history; it does not modify or reopen a configured
period.

## Android workflow

The Owner selects an active vendor and active catalog products, enters quantities and
whole-paisa unit costs, chooses the Nepal business date and optional invoice, and may
split immediate payment to the server-selected main cash or bank account. The review
screen's local total is advisory; success displays only the RPC's authoritative total,
paid, due, line count, and receipt identifier.

Purchase submission is deliberately online-only and never enters the mutation outbox.
The ViewModel creates one UUID, ignores double taps while it is in flight, and retains
that UUID after timeout/offline/unknown outcomes so Retry safely resolves the original
request. Success refreshes vendor dues, cash/bank balances, products, stock, and cost.
