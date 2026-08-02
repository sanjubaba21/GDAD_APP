# Trusted dashboard and period report contract

Migration `20260728060000_trusted_business_reports.sql` exposes read-only authenticated
RPCs. Android should render their server values directly and must not reconstruct cost,
profit, vendor due, or account balances from client-side caches.

## RPCs

- `public.get_business_report(p_shop_id, p_date_from, p_date_to)` returns an inclusive
  period report with a maximum difference of 366 days.
- `public.get_dashboard_report(p_shop_id, p_at default now())` derives the
  `Asia/Kathmandu` business date from the instant and returns a one-day report.

Both RPCs re-derive active membership and shop. Only Owners and Salesmen can call them
for their own shop. Disabled, unauthenticated, and cross-shop requests fail uniformly.

## Period values

- `sales_total_paisa`: non-draft, non-reversed sale totals.
- `returns_total_paisa`: posted sale-return value.
- `net_sales_paisa`: sales minus returns.
- `cost_of_goods_sold_paisa` (Owner): exact sale FIFO cost minus cost restored by posted
  return allocations.
- `gross_profit_paisa` (Owner): net sales minus net FIFO cost.
- `expenses_total_paisa` (Owner): expense evidence whose journal is not reversed.

## Point-in-time values

These are current snapshots at query time, not historical as-of balances:

- `stock_on_hand_quantity`: current product stock projections.
- `low_stock_count` and `low_stock_products`: active products at/below threshold.
- `stock_value_paisa` (Owner): remaining FIFO quantity × immutable cost.
- `vendor_due_total_paisa` and `vendor_dues` (Owner): obligations minus effective
  allocated payments and posted vendor returns; zero-due vendors are omitted.
- `account_balances` (Owner): active cash/bank debits minus credits, including reversals.

Empty totals are numeric zero and empty detail collections are JSON arrays, not null.

## Role-shaped response

Salesmen receive only shop/role/date, sales/returns/net sales, stock quantity, and
low-stock fields. COGS, profit, stock value, vendor due, account balance, and expense
keys are absent—not zero. Owners receive the complete response.

## Nepal boundary and plans

The tested boundary confirms `18:14:59Z` remains on the prior Nepal date and
`18:15:00Z` begins the next date. Fresh-database `EXPLAIN (FORMAT JSON)` checks verify
indexed paths for sales, returns, expenses, and account balances. Only
`sale_returns_shop_business_date_idx` was added; existing indexes cover other paths.

## Errors

- `42501` / `report is not available`: no active permitted membership or cross-shop.
- `22023` / `invalid report date range`: null, reversed, or overly broad range.

## Android implementation

The dashboard calls `get_dashboard_report` and stores only its trusted summary in the
existing user/tenant-owned Room cache. A cached summary remains visible when refresh is
offline or times out, with its age and refresh status shown explicitly. No demo fallback
values are used: a new or empty shop displays the server's numeric zeros and empty lists.

The Reports destination calls `get_business_report` for the selected inclusive Nepal
business-date range. Repository validation rejects a response whose shop or role does not
match the active session. Salesman navigation exposes only sales, returns, stock quantity,
and low-stock values; cost, profit, stock value, vendor due, cash/bank, and expense fields
are stripped defensively even if an unexpected response includes them. Owner detail lists
render the RPC's vendor and account values directly. Android does not reconstruct trusted
profit, cost, vendor due, expenses, or account balances from business-table caches.
