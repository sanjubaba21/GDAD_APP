# GDAD Bags canonical data dictionary

Status: Task 2.1 model contract. Existing objects are marked **Existing**; all others
are **Planned** and must not be migrated until the blocking Task 2.2 decisions at the
end of this document are resolved.

## Global invariants

- PostgreSQL is authoritative. Android sends intent and idempotency keys, never trusted
  totals, roles, actor IDs, stock, balances, or tenant ownership.
- Every business row is owned by one `shop_id`. Composite foreign keys include
  `shop_id` wherever that prevents cross-shop references.
- Auth identity is the immutable `auth.users.id`. Roles and disabled state come only
  from `user_profiles` and active `shop_memberships`.
- Money is signed `bigint` Nepalese paisa. Quantities are constrained integers. Posted
  totals are server-calculated immutable snapshots and must reconcile to their detail
  rows in the posting transaction; balances and dues are derived, never writable.
- Authoritative event time is `timestamptz`. `business_date` is a stored Nepal date
  derived under `Asia/Kathmandu`, except an authorized backdate may supply it under the
  period-lock policy selected in Task 2.2.
- Mutable master/draft rows have `created_at` and `updated_at`. Posted records add
  `posted_at`; reversal records reference the original instead of editing/deleting it.
- Business mutation RPCs derive actor and shop, lock affected rows deterministically,
  validate authoritative state, write every domain/stock/ledger/audit consequence in
  one transaction, and return the committed representation.
- Every externally retryable command has `(shop_id, idempotency_key)` uniqueness.
  Reusing the key with a different normalized payload fails; an identical retry returns
  the original result.
- Client roles receive RLS-scoped reads only. Protected writes use security-definer
  RPCs behind authenticated Edge/API operations. Posted ledgers, allocations, lots,
  movements, payments, returns, and audits are append-only.

## Identity and tenant tables

### `shops` — Existing

- **Purpose/source:** tenant master; source of shop identity and active state.
- **Keys:** PK `id`; unique normalized `slug`.
- **Ownership/RLS:** global tenant root; active members see their shop, Super Admin sees
  all. Direct client writes denied.
- **Time/lifecycle:** `created_at`, `updated_at`; `active` toggles active/inactive.
  Never delete a shop with history.
- **Mutation/audit:** future Super Admin shop-management RPC; activation changes audited.
- **Indexes/queries:** unique lowercase slug; lookup by active membership/shop ID.

### `user_profiles` — Existing

- **Purpose/source:** authoritative application identity mapped 1:1 to `auth.users`.
- **Keys:** PK/FK `user_id -> auth.users.id`; unique normalized `login_id`.
- **Ownership/RLS:** self, owned-shop users, or Super Admin under the authorization
  matrix; disabled stale sessions see nothing.
- **Time/lifecycle:** `created_at`, `updated_at`; `disabled` is reversible. Auth subject
  and login ID are immutable after provisioning unless a later audited rename policy is
  explicitly added.
- **Mutation/audit:** only managed provisioning/administration operations; every state
  change writes account audit and revokes sessions when required.
- **Indexes/queries:** unique lowercase login ID; lookup by Auth subject.

### `shop_memberships` — Existing

- **Purpose/source:** authoritative Owner/Salesman role assignment per shop.
- **Keys:** PK `(shop_id, user_id)`; FKs to shop/profile.
- **Ownership/RLS:** self, Owner for owned shop, Super Admin; disabled actor sees none.
- **Time/lifecycle:** `created_at`, `updated_at`, `active`; deactivate instead of delete
  once referenced by history. First release provisioning permits one managed shop per
  standard user; enforce in operation until a global unique rule is approved.
- **Mutation/audit:** managed account operations only; actor cannot forge role/shop.
- **Indexes/queries:** `(user_id, active, shop_id)` for login/RLS; shop/role roster.

## Product and inventory tables

### `products` — Existing, extension planned

- **Purpose/source:** shop product master and server-maintained stock projection.
- **Keys:** PK `id`; composite unique `(shop_id,id)`; unique normalized
  `(shop_id,sku_code)`. Planned optional barcode uniqueness is blocked on D8.
- **Ownership/RLS:** tenant row; members read own shop, Super Admin reads all.
- **Fields:** name, SKU, optional future barcode, selling-price snapshot source,
  low-stock threshold, `current_stock`, `active`.
- **Time/lifecycle:** `created_at`, `updated_at`; archive with `active=false`; historical
  lines retain snapshots and references.
- **Reconciliation:** `current_stock` is a transactionally maintained projection equal
  to sum of accepted inventory movements and remaining lot quantities under the chosen
  shortage model. A reconciliation query/job must detect divergence; clients never edit it.
- **Mutation/audit:** product management RPC; stock changes only in inventory commands.
- **Indexes/queries:** shop/active/name, unique shop/SKU, optional shop/barcode, low-stock.

### `inventory_lots` — Existing

- **Purpose/source:** immutable FIFO cost layers created by receipt/authorized addition.
- **Keys:** PK `id`; composite unique `(shop_id,id)`; composite FK to product.
- **Ownership/RLS:** tenant-scoped read; no direct client mutation.
- **Fields:** source type/ID, `received_at`, immutable unit cost/original quantity,
  transactionally maintained `remaining_quantity`.
- **Time/lifecycle:** `created_at`; never archive/delete. Remaining quantity changes only
  through allocation, restoration, vendor return, damage/loss, or correction RPC.
- **Reconciliation:** original quantity equals remaining plus net accepted lot-linked
  allocations/removals/restorations. Source must resolve to a same-shop posted record.
- **Indexes/queries:** partial FIFO `(shop_id,product_id,received_at,id)` where remaining
  is positive; source lookup and reconciliation indexes added with source FKs.

### `inventory_movements` — Existing

- **Purpose/source:** append-only stock event ledger and product projection evidence.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop product/lot FKs.
- **Ownership/RLS:** tenant-scoped read; direct insert/update/delete denied.
- **Fields:** movement type, signed delta, cost snapshot, typed source reference,
  reason, business date/time, actor.
- **Time/lifecycle:** immutable `occurred_at`, `business_date`, `created_at`; corrections
  are compensating movements linked to originals, never edits/deletes.
- **Mutation/audit:** written in the same transaction as purchase, sale, return, or
  adjustment; typed sources become explicit FKs where practical.
- **Indexes/queries:** product timeline, business-date report, source reconciliation.

## Sales and customer money tables

### `sales` — Planned

- **Purpose/source:** one server-priced sale header and lifecycle source of truth.
- **Keys:** PK `id`; unique `(shop_id,id)` and `(shop_id,idempotency_key)`; actor FK.
- **Ownership/RLS:** shop members read subject to cost/profit decision D9; protected
  posting/reversal only.
- **Fields:** status (`draft|posted|partially_returned|returned|reversed`), optional
  customer name/phone snapshots for credit identification, subtotal, line/sale discount,
  tax, grand total, business date/time, actor. Paid, refunded, and due values are
  derived from effective payment/refund events rather than stored on the header.
- **Lifecycle:** only draft may be abandoned; posted content is immutable. Returns
  advance derived return state; full sale reversal uses a linked compensating operation.
- **Reconciliation:** subtotal/discount/tax/total are calculated from lines under D2/D10;
  paid and refunded values reconcile to posted payment/refund rows, not client edits.
- **Mutation/audit:** atomic sale RPC writes header, lines, allocations, inventory,
  payment/ledger, notification, and audit consequences.
- **Indexes/queries:** shop/business date/status, customer phone, actor/time, idempotency.

### `sale_lines` — Planned

- **Purpose/source:** immutable quantity, product, description, price, discount, and tax
  snapshot for each posted sale line.
- **Keys:** PK `id`; unique `(shop_id,sale_id,line_number)`; same-shop sale/product FKs.
- **Lifecycle/reconciliation:** created with sale, never independently updated/deleted;
  positive quantity; server-calculated line net equals price × quantity minus discount
  plus tax with checked `bigint` arithmetic.
- **Indexes/queries:** sale order, product sales history, business reporting via sale.

### `sale_lot_allocations` — Planned

- **Purpose/source:** exact FIFO quantity and unit-cost snapshot consumed per sale line.
- **Keys:** PK `id`; unique `(shop_id,sale_line_id,lot_id)`; same-shop line/lot FKs.
- **Lifecycle/reconciliation:** immutable; positive quantity; allocations sum to line
  fulfilled quantity and never exceed locked lot availability. Cost is hidden from
  Salesman if D9 requires it.
- **Indexes/queries:** line allocation, lot consumption/remaining reconciliation.

### `sale_payments` — Planned

- **Purpose/source:** immutable receipt events applied to a sale, including later
  partial payments; not a balance field.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop sale and account FKs;
  optional reversal-of payment unique reference.
- **Fields/lifecycle:** positive amount, method, received/business time, actor, status
  (`posted|reversed`). A reversal creates a compensating payment/ledger transaction.
- **Reconciliation:** sale due = posted sale total − non-reversed receipts + refunds or
  other adjustments selected in D3/D5. Each payment maps 1:1 to a balanced journal.
- **Indexes/queries:** sale/time, account/business date, outstanding-sale derivation.

### `sale_returns` — Planned

- **Purpose/source:** return header tied to one posted same-shop sale.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop sale FK.
- **Fields/lifecycle:** status `posted|reversed`, reason, disposition, total/refund
  snapshots, actor, business date/time. No destructive edits.
- **Mutation/audit:** atomic return RPC validates D5, writes lines/allocation restoration,
  movements, refund/credit, ledger, sale state, notification, and audit.
- **Indexes/queries:** sale return history, business date/status.

### `sale_return_lines` — Planned

- **Purpose/source:** quantity returned against one original sale line.
- **Keys:** PK `id`; unique `(shop_id,return_id,sale_line_id)`; same-shop FKs.
- **Lifecycle/reconciliation:** immutable positive quantity; cumulative non-reversed
  returned quantity cannot exceed the original line. Restock quantities are detailed
  by `sale_return_allocations`; damaged/non-restocked handling awaits D5.
- **Indexes/queries:** return detail and cumulative return by original line.

### `sale_return_allocations` — Planned

- **Purpose/source:** exact returned/restocked quantity against one original
  `sale_lot_allocation`, preserving the source FIFO layer for partial returns.
- **Keys:** PK `id`; unique `(shop_id,return_line_id,sale_lot_allocation_id)`;
  same-shop composite FKs to return line and original allocation.
- **Lifecycle/reconciliation:** immutable positive quantity; cumulative effective
  returns against an original allocation cannot exceed its allocated quantity. The
  return RPC restores eligible lots in reverse original-allocation order and records
  every restoration here; non-restocked disposition remains explicit and creates no
  lot restoration.
- **Indexes/queries:** return-line detail, cumulative returned quantity per original
  allocation, lot restoration reconciliation.

### `refunds` — Planned

- **Purpose/source:** money returned for a sale return; separate from stock disposition.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop return/account FKs;
  unique optional reversal link.
- **Lifecycle/reconciliation:** positive immutable posted/refunded amount; every cash/bank
  refund has one balanced journal. Credit handling is blocked on D3/D5.
- **Indexes/queries:** return, sale via return, account/business date.

## Vendor and purchasing tables

### `vendors` — Planned

- **Purpose/source:** shop vendor master; not the source of balance.
- **Keys:** PK `id`; unique `(shop_id,id)`; normalized optional phone/tax/reference
  uniqueness only where Task 2.2 approves.
- **Lifecycle:** mutable name/contact/notes; archive with `active=false`; never delete
  when referenced.
- **Ownership/mutation:** Owner/Super Admin read and protected management; Salesman
  access decided with purchasing UI scope. Changes audited.
- **Indexes/queries:** shop/active/name, normalized phone/reference.

### `purchase_bills` — Planned

- **Purpose/source:** vendor invoice obligation and immutable posted commercial snapshot.
- **Keys:** PK `id`; unique `(shop_id,id)` and idempotency key; same-shop vendor FK;
  external invoice uniqueness per D6.
- **Fields/lifecycle:** `draft|posted|partially_returned|returned|reversed`, invoice
  reference/date, subtotal/discount/tax/total, actor and Nepal business time. Posted
  values are immutable; reversal is compensating.
- **Reconciliation:** total derives from lines; outstanding vendor due derives from
  bill, allocated payments, returns, and adjustments—never a vendor balance column.
- **Indexes/queries:** vendor/date/status, due derivation, external reference, idempotency.

### `purchase_bill_lines` — Planned

- **Purpose/source:** immutable billed product quantity and unit-cost/tax/discount snapshot.
- **Keys:** PK `id`; unique `(shop_id,bill_id,line_number)`; same-shop product FK.
- **Reconciliation:** positive quantity; checked line total; receipts and returns cannot
  cumulatively exceed permitted quantities.
- **Indexes/queries:** bill order, product purchase history.

### `purchase_receipts` — Planned

- **Purpose/source:** one physical receipt event against a purchase bill; supports
  partial receipts without changing the bill.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop bill FK.
- **Lifecycle/transaction:** immutable posted/reversed event with actor/business time;
  posting atomically creates receipt lines, FIFO lots, inventory movements, projection,
  ledger/audit consequences as applicable.
- **Indexes/queries:** bill receipt history, business date.

### `purchase_receipt_lines` — Planned

- **Purpose/source:** quantity received against a bill line and direct source for one or
  more FIFO lots.
- **Keys:** PK `id`; unique `(shop_id,receipt_id,bill_line_id)`; same-shop FKs.
- **Reconciliation:** positive quantity; cumulative receipts cannot exceed ordered
  quantity unless an explicit over-receipt rule is approved; created lot cost/quantity
  exactly matches receipt lines.
- **Indexes/queries:** receipt detail, outstanding quantities, lot source.

### `vendor_payments` — Planned

- **Purpose/source:** immutable payment event to a vendor; not a balance.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop vendor/account FKs.
- **Lifecycle/reconciliation:** posted/reversed, positive amount, actor/business time;
  allocations cannot exceed payment amount and every posted event maps to a balanced
  journal.
- **Indexes/queries:** vendor/time, account/business date, unapplied amount.

### `vendor_payment_allocations` — Planned

- **Purpose/source:** many-to-many allocation of a vendor payment to purchase bills.
- **Keys:** PK `(shop_id,vendor_payment_id,purchase_bill_id)`; same-shop/vendor checks.
- **Reconciliation:** positive amount; allocations sum no higher than payment and no
  bill may be overpaid under D6. Immutable; corrections reverse/repost.
- **Indexes/queries:** bill payments and vendor payment application.

### `vendor_returns` — Planned

- **Purpose/source:** goods/credit returned to a vendor against original bill/receipt.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; same-shop vendor/bill FKs.
- **Lifecycle/transaction:** immutable posted/reversed header; atomically writes lines,
  lot-linked removals, movements, vendor credit/refund, journal, and audit.
- **Indexes/queries:** vendor/bill return history, business date.

### `vendor_return_lines` — Planned

- **Purpose/source:** returned quantity tied to original receipt line and FIFO lot.
- **Keys:** PK `id`; unique per return/receipt-line/lot; same-shop composite FKs.
- **Reconciliation:** positive quantity; cumulative returns cannot exceed received and
  available eligible quantities. Cost comes from original lot, never client input.
- **Indexes/queries:** original receipt/lot return totals and return detail.

## Cash, bank, expense, and balanced ledger tables

### `financial_accounts` — Planned

- **Purpose/source:** shop chart of accounts. User-visible cash drawers/bank accounts
  coexist with protected system control accounts needed for balanced posting; no row
  stores a balance.
- **Keys:** PK `id`; unique `(shop_id,id)` and normalized `(shop_id,name)`; type
  `cash|bank` (future types require migration).
- **Types:** `cash`, `bank`, `receivable`, `payable`, `inventory`, `revenue`, `cogs`,
  `expense`, `equity`, and `clearing`, each with its accounting natural side. Posting
  operations select system control accounts by stable purpose code, never client ID.
- **Lifecycle:** active/archive; referenced accounts are never deleted. Required system
  accounts cannot be archived while the shop is active. Opening balance is a journal
  transaction against equity/clearing, not a column.
- **Mutation/audit:** Owner/Super Admin may manage permitted cash/bank presentation;
  system account purpose/type changes require a privileged migration/operation. All
  changes are audited.
- **Indexes/queries:** unique shop/purpose for system accounts; shop/type/active/name.

### `journal_transactions` — Planned

- **Purpose/source:** immutable header grouping one balanced financial event.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)`; optional unique
  `(shop_id,reversal_of_id)`; typed source reference unique per posted business event.
- **Fields/lifecycle:** type, description, status `posted|reversed`, actor, business
  date, occurred/created time. A correction posts a reversing transaction plus replacement.
- **Reconciliation:** every posted transaction has at least two entries and total debits
  equal total credits exactly in paisa, enforced inside the posting transaction with a
  deferred constraint/validated posting RPC.
- **Indexes/queries:** business-date ledger, source lookup, reversal chain, actor/time.

### `journal_entries` — Planned

- **Purpose/source:** immutable debit/credit lines that derive account balances.
- **Keys:** PK `id`; unique `(shop_id,transaction_id,line_number)`; same-shop account FK.
- **Fields/reconciliation:** exactly one positive debit or credit amount; balance for an
  account is sum(debit − credit) using the chosen sign convention, filtered to posted
  non-reversed effect. No direct writes.
- **Indexes/queries:** account/business timeline, transaction balance check, statements.

### `expenses` — Planned

- **Purpose/source:** business description/category/evidence for an expense; monetary
  effect is the linked journal transaction.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)` and unique linked journal ID.
- **Lifecycle:** posted/reversed; positive amount snapshot, category, payee/note, actor,
  business time. Correction reverses and reposts.
- **Indexes/queries:** shop/business date/category, actor, journal source.

Deposits, withdrawals, opening balances, and transfers do not need separate balance
tables. They are typed `journal_transactions`; transfers have entries against both
same-shop financial accounts. Sale receipts/refunds and vendor payments link their
domain row to exactly one journal transaction.

## Notification and audit tables

### `notifications` — Planned

- **Purpose/source:** safe tenant event for a concrete recipient or role target.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key)` for generated events; optional
  recipient FK and constrained target role.
- **Fields:** category, safe title/body, typed record reference, `created_at`, optional
  `read_at`, `expires_at`, actor/source operation. Payload must exclude PINs, hashes,
  tokens, keys, raw credentials, and unnecessary personal/financial detail.
- **RLS/lifecycle:** recipient/targeted role reads; recipient marks read through a
  protected operation; retention job may delete after `expires_at` only when policy
  permits. Business source remains authoritative even if notification expires.
- **Indexes/queries:** recipient unread timeline, role/shop unread timeline, expiry.

### `business_audit_events` — Planned, private

- **Purpose/source:** append-only evidence for privileged and business mutations beyond
  the existing account-specific audit table.
- **Keys:** PK `id`; unique `(shop_id,idempotency_key,operation)` or source operation ID.
- **Fields:** actor, shop, operation, record type/ID, occurred/created time, safe
  before/after JSON metadata, request correlation ID.
- **Security/lifecycle:** clients cannot insert/update/delete; service operations append.
  Retention/export policy must preserve required history. Secrets and credential
  material are forbidden by contract and payload validation.
- **Indexes/queries:** shop/time, actor/time, record type/ID, operation/correlation.

Existing private authentication/operation tables (`login_credentials`, login rate
limits, provisioning/admin requests and rate limits, `account_audit_events`) remain
backend-only. Request/rate rows follow bounded operational retention; account audits
are immutable and must ultimately align with the business-audit retention policy.

### Existing private security/operation table register

| Table | Purpose and key | Lifecycle, access, and reconciliation |
|---|---|---|
| `login_credentials` | One PIN verifier per Auth user; PK/FK `user_id` | Backend-only; verifier replaced by audited reset, counters changed only by login RPC; delete cascades only with exact managed Auth subject. |
| `pin_login_rate_limits` | HMAC source fingerprint/window PK | Backend-only bounded throttle state; expires by window and may be pruned after operational retention. |
| `account_provisioning_requests` | Idempotent provisioning reservation PK `request_id` | Backend-only state machine; payload fingerprint prevents key reuse; terminal result/compensation must reconcile to profile, membership, credential, Auth subject, and audit. |
| `account_audit_events` | Immutable account-operation evidence PK `id` | Backend-only append; no PIN/verifier/token/secret metadata; retained/exported under D11. |
| `account_admin_requests` | Idempotent disable/enable/reset reservation PK `request_id` | Backend-only state machine; terminal result reconciles to target profile/credential/session and audit event. |
| `account_admin_rate_limits` | Actor/source/action throttle key | Backend-only bounded state; expires by window and may be pruned after operational retention. |

These tables use operation time in `timestamptz`, not Nepal business dates, because
they are security/control-plane events rather than shop financial events. Their exact
constraints and RPC transitions remain defined in the Phase 1 migrations and account
operation contracts.

## Transaction boundaries

| Command | Rows committed atomically |
|---|---|
| Provision/administer account | Auth orchestration reservation, profile/membership/credential state, session revocation where applicable, audit; compensation is request-scoped. |
| Manage product | Product state and audit. |
| Receive purchase | Bill/receipt and lines, lots, inventory movements/projection, payment/due and journal effects, notification, audit. |
| Post sale | Sale/lines, FIFO allocations, lots, movements/projection, receipts/due and journals, notification, audit. |
| Return sale | Return/lines, restored lot quantities, movements/projection, refund/credit and journal, sale derived state, notification, audit. |
| Adjust stock | Lot effect, movement/projection, any financial consequence, notification, audit. |
| Pay/return vendor goods | Payment/allocation or return/lines, lot/movement effects where relevant, balanced journal, bill/vendor due derivation, audit. |
| Expense/opening/transfer/correction | Domain source where applicable, balanced journal transaction/entries, audit; correction is reverse plus replacement. |

No command may commit only part of its listed consequences. Row locks use deterministic
order (shop, product, FIFO received time, UUID) to limit deadlocks and overselling.

## Derived values and reconciliation rules

| Value | Derivation/source | Reconciliation |
|---|---|---|
| Product current stock | Accepted stock movements and eligible lot remaining quantities | Protected posting updates projection; scheduled query compares both derivations and alerts on mismatch. |
| Sale total | Immutable server-calculated sale-line snapshots and approved discounts/tax | Posting rejects mismatch; no client total is accepted. |
| Sale due | Posted sale total minus effective receipts, plus/minus approved return/refund treatment | Query/view only; never a writable `due` column. |
| Vendor balance | Posted bills minus effective payment allocations and vendor returns/credits | Query/view only; vendor master has no balance. |
| Financial account balance | Posted journal entries interpreted by debit/credit and account natural side | Query/view only; transaction posting requires exact balance. |
| Profit | Posted sale revenue minus allocation cost snapshots and approved adjustments | Reporting query; visibility follows D9. |

Materialized summaries may be introduced only after measured need. They must name their
source query, refresh transaction, freshness indicator, and rebuild procedure.

## Task 2.2 decision register

Approved selections and their schema/RPC/permission/UI consequences are recorded in
`docs/business-policies.md`. This table remains the dependency map.

These choices are intentionally unresolved and block only the listed migrations/RPCs.

| ID | Required decision | Affected model/work |
|---|---|---|
| D1 — Approved | Negative stock is forbidden for every role | Lots, movements, sale allocation, product reconciliation, sale UI |
| D2 — Approved | Only Owners may discount or override configured selling price | Sale/line fields, constraints, posting authorization |
| D3 — Approved | Only Owners may create identified, due-dated credit sales; partial payments are allowed | Sales, payments, due views, notifications |
| D4 — Approved | Cash/bank only, split rows allowed, and overpayment rejected | Payments, refunds, vendor payments, accounts, journals |
| D5 — Approved | Owner-only within 30 days; sellable restores original lots, damaged does not; refund capped by effective payment | Sale returns, lot restoration, refunds, journals |
| D6 — Approved | Owner-only, no over-receipt/payment, unique vendor invoice, draft cancel and posted reversal/return | Purchasing and vendor balance derivation |
| D7 | Backdating roles, maximum age, accounting period close/reopen | Every business date, posting RPC, reporting |
| D8 | SKU/barcode normalization, uniqueness, reuse after archive | Product master and import/scanning UI |
| D9 | Whether Salesmen see unit cost, allocation cost, gross profit, or vendor data | RLS/views/API response shaping |
| D10 | Paisa rounding order and whether VAT/tax is in first-release scope | Sale/purchase lines and totals, reports |
| D11 | Notification retention duration and audit retention/export requirements | Notifications, audit, scheduled cleanup |

No affected Phase 2 migration proceeds until its decision row has an explicit selected
policy, approver/date, and corresponding schema/RPC/UI acceptance consequences in the
business-policy record.
