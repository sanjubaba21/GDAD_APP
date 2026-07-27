# FIFO sale backend contract

Migration `20260727200000_atomic_fifo_sale.sql` exposes the authenticated RPC
`public.post_fifo_sale`. Android checkout must call this operation and treat its JSON as
authoritative. Direct mutation of sales, FIFO, inventory, payments, journals,
notifications, and audits remains denied.

## Request

- `p_idempotency_key`: stable caller-generated retry key, 1–160 trimmed characters.
- `p_shop_id`: untrusted intent; the database re-derives active membership and role.
- `p_business_date`: Salesmen may use Nepal today only; Owners may use today through
  seven days back, always inside an open accounting period.
- `p_lines`: array of 1–100 unique products with `product_id`, positive integer
  `quantity`, optional `effective_unit_price_paisa`, and optional
  `line_discount_paisa`. Money is non-negative whole paisa.
- `p_sale_discount_paisa`: optional absolute sale discount, default zero.
- `p_is_credit`, `p_customer_name`, `p_customer_contact`, `p_due_date`: credit intent.
  Only Owners may use it; identity is required and due date cannot precede business date.
- `p_payments`: up to ten positive cash/bank payment objects, permitting split tender.

Salesmen cannot override configured prices or apply any discount and must fully settle
the sale at posting. Owners may override/discount and may post zero-, partial-, or
fully-paid identified credit sales. Overpayment and a negative total are rejected.

## Atomic effects and response

Products lock in stable ID order, then eligible lots lock by `received_at,id`. The
operation rejects the entire command unless it can allocate every requested unit; stock
never becomes negative. A success creates the posted sale and immutable price snapshots,
exact FIFO cost allocations, negative movements and stock projection, optional low-stock
notifications, settlement rows, balanced revenue/receivable, COGS/inventory, and
cash/bank journals, a secret-free audit, and the stored retry result.

The response contains `sale_id`, `grand_total_paisa`, `paid_paisa`, `due_paisa`,
`cost_total_paisa`, `line_count`, and `allocation_count`. The UI must not announce
success or calculate the receipt from stale cart values before this response arrives.
An exact retry returns the original JSON without consuming stock again; a changed
payload under the same key fails.

## Failure categories

- `42501`: inactive/unauthorized actor, unauthorized pricing/credit, or unavailable
  cross-shop/inactive product.
- `22023`: invalid key/date/lines/payment shape/credit fields/discount or changed retry.
- `22003`: exact-arithmetic overflow.
- `23514`: insufficient stock, overpayment, or incomplete non-credit settlement.
- `55000`: closed period or missing required financial account.

Clients should retain the cart for retryable failures and show stable generic messages;
they must not infer hidden tenant or inventory details beyond the returned category.
