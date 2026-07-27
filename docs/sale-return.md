# Sale return and refund backend contract

Migration `20260727220000_atomic_sale_return.sql` exposes the authenticated RPC
`public.post_sale_return`. Android must use this operation for first-release returns;
direct mutation of return, allocation, lot, movement, refund, journal, notification,
and audit records remains denied.

## Request

- `p_idempotency_key`: stable caller-generated retry key, 1–160 trimmed characters.
- `p_shop_id` and `p_sale_id`: untrusted intent. The database requires an active Owner
  membership and a returnable sale in the same active shop.
- `p_business_date`: Nepal date within the Owner seven-day posting window, in an open
  accounting period, and no more than 30 calendar days after the original sale date.
- `p_reason`: required trimmed reason, 1–500 characters.
- `p_lines`: array of 1–100 unique original `sale_line_id` values, each with a positive
  `quantity` and `disposition` of `sellable` or `damaged`.
- `p_refund_method`: `cash` or `bank` exactly when the server determines money must be
  refunded. Omit it when returned value is fully absorbed by outstanding credit due.

## Server calculation and effects

Return value is calculated from immutable sale-line net values using whole-paisa
rounding; a final partial return receives the exact remaining line value. The operation
subtracts returned value from outstanding due first. If returned value exceeds current
due, the already-paid remainder must be refunded through the supplied method. Clients
cannot supply or inflate the refund amount.

The sale row serializes concurrent returns. Original allocations and lots are processed
in reverse FIFO-allocation order. Both dispositions create immutable return-allocation
evidence. Sellable units restore their exact original lots, product projection, positive
movements, and inventory/COGS entries; damaged units do not increase saleable stock.
The same transaction updates derived sale status, posts revenue/receivable and optional
refund journals, creates notification/audit evidence, and stores the authoritative
result. Any error rolls everything back.

The response contains `sale_return_id`, `sale_id`, `return_value_paisa`, `refund_paisa`,
`due_after_paisa`, `restored_quantity`, `restored_cost_paisa`, and `sale_status`. Exact
retries return this stored JSON even after the first call changes sale state; changed
payload reuse fails without duplicating restoration or money.

## Failure categories

- `42501`: unauthenticated/non-Owner, inactive membership/shop, cross-shop sale/line, or
  sale state that is not returnable.
- `22023`: invalid key/date/window/reason/lines/disposition/refund intent or changed retry.
- `23514`: cumulative line or allocation over-return.
- `55000`: closed period or unavailable return/refund financial account.

Clients should keep return input available after failure and show stable generic error
categories without exposing hidden shop or customer information.
