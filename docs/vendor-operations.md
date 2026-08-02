# Vendor financial operations backend contract

Migration `20260728110000_vendor_management.sql` additionally exposes Owner-only
`public.manage_vendor` for idempotent create, update, and archive. It shares the private
vendor operation ledger, records immutable business audits, rejects Salesman/Super Admin/
cross-shop use, and leaves direct vendor writes denied. Archived vendors remain readable
for history but cannot be edited or selected for new purchases.

Migrations `20260728010000_vendor_return_movement_types.sql` and
`20260728020000_vendor_financial_operations.sql` expose three authenticated,
Owner-only RPCs. Android must use these operations for vendor payments, purchase
returns, and reversals. Direct table mutation remains denied.

## Shared rules

- `p_idempotency_key` is a caller-generated retry key of 1–160 trimmed characters.
  Persist it with the pending client operation and reuse it until the authoritative
  result arrives. An exact retry returns the original result; reuse with changed input
  fails.
- `p_shop_id` is untrusted intent. Each RPC derives the authenticated actor and requires
  an active Owner membership in the active shop.
- `p_business_date` is a Nepal business date, cannot be in the future or more than seven
  days old, and must fall inside an open accounting period.
- Amounts are positive whole paisa. Quantities are positive whole units.
- Successful operations create secret-free immutable audit evidence. Failures roll back
  every stock, payment, ledger, notification, and request-state consequence.

## Post a vendor payment

Call `public.post_vendor_payment` with:

- `p_vendor_id`: active vendor in the same shop.
- `p_method`: `cash` or `bank`; the server selects the corresponding active main account.
- `p_allocations`: JSON array of 1–100 unique bills. Each item contains
  `purchase_bill_id` and `amount_paisa`.

Example allocation:

```json
[
  {"purchase_bill_id":"00000000-0000-0000-0000-000000000000","amount_paisa":150000}
]
```

The server locks the vendor and bills, derives each bill's remaining due, and rejects
overpayment. One successful transaction creates a posted payment, exact bill
allocations, and a balanced journal that debits payable and credits cash or bank.

The authoritative JSON result contains `vendor_payment_id`, `vendor_id`,
`amount_paisa`, `allocation_count`, and `vendor_due_after_paisa`.

## Post a vendor return

Call `public.post_vendor_return` with:

- `p_purchase_bill_id`: same-shop received or partially returned bill.
- `p_reason`: 1–500 trimmed characters.
- `p_lines`: JSON array of 1–100 unique original receipt lines. Each item contains
  `purchase_receipt_line_id` and `quantity`.

Example return line:

```json
[
  {"purchase_receipt_line_id":"00000000-0000-0000-0000-000000000000","quantity":1}
]
```

Cost is always taken from the original purchase lot. The return cannot exceed either
the lot's currently available quantity or the bill's unpaid due. If existing payments
would make the return an over-credit, reverse the relevant payment first. A successful
return decreases the exact lot and product projection, appends a `vendor_return`
movement, updates bill status, posts a balanced payable/inventory journal, and creates
an Owner notification.

The authoritative JSON result contains `vendor_return_id`, `purchase_bill_id`,
`return_value_paisa`, `bill_due_after_paisa`, and `line_count`.

## Reverse a payment or return

Call `public.reverse_vendor_event` with:

- `p_event_type`: `payment` or `return`.
- `p_event_id`: the same-shop posted event to reverse.
- `p_reason`: 1–500 trimmed characters.

The server marks the original event reversed and creates a new journal with the
original debits and credits exchanged. Reversing a vendor return also restores the
exact original lots and product projections and appends explicit
`vendor_return_reversal` movements. Original journal entries and inventory movements
are never rewritten.

The result contains `event_type`, `event_id`, and `reversal_journal_id`. Exact retry
returns the same result without repeating stock or ledger effects.

## Failure categories

- `42501`: unauthenticated/non-Owner, inactive membership/shop, or unavailable
  cross-shop vendor, bill, receipt line, lot, or event.
- `22023`: invalid key, payload, method, date, reason, quantity/amount, duplicate input,
  or changed retry payload.
- `22003`: exact-arithmetic overflow.
- `23514`: payment exceeds bill due, or return exceeds available stock/unpaid bill due.
- `55000`: closed accounting period or unavailable required financial account.

Clients should map these SQL states to stable user-facing categories and display only
the server-returned values after success.

## Android first-release behavior

- The Owner Vendors destination separates purchasing from a ledger-and-dues workspace.
  Salesman and Super Admin sessions cannot load or render vendor financial data/actions.
- Bill due is derived from the original total less posted payment allocations and posted
  returns. Returnable quantity is capped by both prior posted returns and current stock in
  the exact receipt lot. Archived/reversed events remain visible as history.
- Payments allocate positive whole-paisa amounts across unique open bills and cannot
  exceed displayed due. Returns use original receipt-line IDs, available quantities,
  original costs, a required reason, and cannot exceed unpaid bill due.
- Payment, return, and reversal are online-only, double-submit guarded, and retain one
  UUID/draft for exact timeout retry. Validation or conflict reloads visible balances.
- Success dialogs display only authoritative RPC amount/due/count/journal values. Vendor
  directory balances refresh after every mutation; returns and return reversals also
  refresh product stock. Android performs no direct financial or inventory table write.
