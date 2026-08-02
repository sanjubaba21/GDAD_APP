# Inventory adjustment backend contract

Migrations `20260727230000_inventory_adjustment_journal_kind.sql` and
`20260728000000_atomic_inventory_adjustment.sql` expose the authenticated RPC
`public.post_inventory_adjustment`. It is the only first-release path for stock found,
opening/correction additions, damage, loss, and count/correction removals.

## Request

- `p_idempotency_key`: stable caller-generated retry key, 1–160 trimmed characters.
- `p_shop_id` and `p_product_id`: untrusted intent; the database requires an active
  Owner membership and active product in the same active shop.
- `p_movement_type`: `manual_add`, `manual_remove`, `damage`, or `loss`.
- `p_reason_code`: must match the movement: `damaged`; `lost`; `count_shortage` or
  `data_correction` for removal; `stock_found`, `opening_balance`, or `data_correction`
  for addition.
- `p_quantity`: positive whole-unit quantity.
- `p_source_lot_id`: required for every reduction and omitted for addition.
- `p_unit_cost_paisa`: non-negative whole-paisa cost required for addition and omitted
  for reduction, whose cost is derived from the locked source lot.
- `p_business_date`: Nepal date from today through seven days back in an open period.
- `p_note`: optional trimmed explanation, up to 1,000 characters.

## Atomic behavior

Manual additions create a new immutable FIFO layer; they never enlarge or rewrite an
old receipt lot. Damage, loss, and removal lock and decrement the specified same-product
lot and reject quantity above its remaining balance. Every success appends an inventory
movement and updates the product projection.

Positive-cost changes post a balanced inventory/adjustment-clearing journal linked to
the immutable adjustment source. Zero-cost additions remain valid without a fabricated
money journal. Every success also creates an Owner notification, secret-free audit, and
stored authoritative response. The response includes adjustment/product IDs, movement
and reason, signed quantity, exact cost, resulting stock, and source/created lot IDs.

The request row serializes identical retries. Exact retry returns the original JSON
without duplicating stock; changed payload reuse fails. Any validation, availability,
period, or account error rolls the whole command back.

## Failure categories

- `42501`: unauthenticated/non-Owner, inactive membership/shop/product, or cross-shop lot.
- `22023`: invalid key/date/type/reason/quantity/note or incompatible lot/cost intent.
- `22003`: exact-arithmetic overflow.
- `23514`: requested reduction exceeds remaining source-lot quantity.
- `55000`: closed period or unavailable inventory/adjustment account.

Direct table mutation remains denied. Adjustment records and costs are Owner-only; the
UI must preserve input after failure and display stable generic categories.

## Android workflow

Owner stock screens combine the Room-backed product projection with RLS-protected FIFO
lots and recent movement history. Owner sees cost and may post additions, removal,
damage, or loss with compatible reason, positive quantity, Nepal business date, source
lot or new-lot cost, and an explanation. Salesman sees only product/on-hand/low-stock
summary and receives no cost, lot, movement-history, or adjustment control.

Adjustments are online-only and never enter the outbox. One UUID survives a visible
retry, double taps are ignored, and success displays the authoritative resulting stock,
quantity delta, and cost before refreshing the product projection and history.
