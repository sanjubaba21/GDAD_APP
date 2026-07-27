# Product management backend contract

Migration `20260727163000_product_management.sql` exposes one authenticated RPC:
`public.manage_product`. Android and imports must use it for product create, update, and
archive; direct table mutation remains denied.

## Request and response

- `p_idempotency_key`: caller-generated, stable across retries, 1–160 characters.
- `p_action`: `create`, `update`, or `archive`.
- `p_shop_id`: untrusted shop intent; the database verifies an active Owner membership.
- `p_product_id`: omitted for create and required for update/archive. Create UUIDs are
  generated server-side.
- Product fields are the full desired state for create/update. Barcode is optional and
  whitespace-only input becomes `NULL`.

The JSON response is the committed product snapshot. An exact retry returns the stored
snapshot without duplicating product, reservation, or audit state. Reusing a key with a
different actor, action, or normalized payload fails.

## Code identity and lifecycle

SKU is required and barcode is optional. Comparison uses trim, collapsed whitespace,
Unicode NFKC normalization, and lowercase folding. Uniqueness is per shop.
`private.product_code_reservations` permanently retains every code ever assigned,
including changed and archived codes.

Archive sets `active=false` and never deletes history. It is rejected while a draft
sale or purchase bill references the product. Archived products remain readable but
cannot be edited. Every success appends one secret-free business audit snapshot.

## Failure categories

- `42501`: unauthenticated, inactive/non-Owner, forged shop, or cross-shop product.
- `22023`: invalid operation, idempotency key, fields, or changed retry payload.
- `23505`: current or permanently reserved normalized code collision.
- `55000`: in-progress/archive lifecycle conflict.

Clients must map these to stable user-facing categories and not infer hidden tenant
state from error details.
