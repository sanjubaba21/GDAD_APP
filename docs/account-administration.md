# Managed account administration contract

Task 1.4 uses a dedicated `manage-accounts` Edge Function. It is separate from
idempotent identity provisioning. Ordinary account actions cannot create or delete an
Auth user; the separately discriminated shop-deletion operation may remove only
deterministically managed identities that belong exclusively to the deleted shop.

## Exact request contract

Every request is `POST` JSON with a valid project publishable key, the actor's Bearer
session, a UUID `request_id`, UUID `target_user_id`, and the actor's existing 4–8 digit
`reauth_pin`.

- `disable_user`: no additional fields.
- `enable_user`: no additional fields.
- `reset_pin`: adds a non-weak 6–8 digit `new_pin`.

Unknown fields, malformed identifiers, weak new PINs, missing sessions, and expanded
bodies fail closed. Client failures use generic codes and never expose verifier,
membership, existence, or database details.

## Authoritative hierarchy

- An active, non-disabled Super Admin may manage an Owner in an active shop.
- An active, non-disabled Owner may manage a Salesman only in the same active shop.
- Owners cannot manage Owners, any cross-shop user, or grant roles.
- Salesmen cannot administer accounts.
- Super Admin targets are not managed through this first-release API. This prevents
  disabling the only recovery authority; an explicit, separately designed recovery
  path is required before that can change.

The database derives all roles and shop relationships from profiles and memberships.
The client never supplies a shop or role.

## Reauthentication and rate limits

The server retrieves only the authenticated actor's private verifier through a
service-role-only RPC and verifies `reauth_pin` with the shared versioned HMAC plus
Argon2id helper. Per-actor and per-source attempt windows are atomic. Incorrect,
unknown, locked, or unauthorized cases return a generic denial and cannot mutate the
target.

Compatibility is deliberately asymmetric: verification accepts an existing 4–8 digit
credential so legacy managed accounts can reauthenticate, but account creation and PIN
reset continue to require a new 6–8 digit PIN. The compatibility path does not create
or downgrade credentials.

## Atomic mutation, idempotency, and audit

`request_id` binds one actor, action, and target. Reusing it with different input is
denied; repeating a completed request returns the same client-safe result without a
second mutation or audit event.

- Disable sets `user_profiles.disabled = true`.
- Re-enable sets it to `false`; inactive shops or memberships still prevent login.
- PIN reset replaces the target's verifier using the current pepper version and clears
  failed-attempt/lock state.

Each success deletes the target's Auth refresh-session rows in the same database
transaction and writes one append-only `private.account_audit_events` record containing
only action and safe state metadata. Existing access JWTs remain bounded by expiry and
cannot authorize application data after disable because RLS rechecks profile and
membership state. PIN-reset access JWTs remain bounded by expiry, while deleted Auth
sessions prevent refresh; Android must clear local state when refresh is rejected.

## Client-safe response

Success returns only `code`, `status`, `request_id`, `target_user_id`, `action`, and
`disabled`. PINs, hashes, pepper versions, tokens, emails, role internals, and Auth
response bodies are never returned or logged.

## Android integration

Task 5.1 exposes disable, re-enable, and PIN reset only from the role-filtered cached
directory: Super Admin targets Owners; an Owner targets Salesmen in the same shop;
Salesmen render no administration controls. The repository repeats these checks before
invoking `manage-accounts`, while the backend remains authoritative.

Every action requires the actor PIN in a confirmation dialog. The PIN and replacement
PIN remain transient and are excluded from Room, saved navigation state, diagnostics,
and success text. A successful disable or reset explicitly reports that refresh sessions
were revoked. Validation, denial, conflict, offline, timeout, and rate-limit failures use
fixed client-safe messages; retry retains the original request UUID.

## Super Admin shop deletion

`delete_shop` is a separate destructive request shape. It requires a UUID `request_id`,
UUID `target_shop_id`, the exact lowercase `confirmation_slug`, a trimmed 8–500 character
`reason`, and the authenticated Super Admin's existing 4–8 digit `reauth_pin`. No other fields are
accepted. Owners and Salesmen have no UI control, repository authorization, Function
authorization, or RPC grant for this operation.

The active Super Admin must type the exact cached slug, record a reason, and enter their
own PIN in the destructive dialog. The service-role preparation RPC independently checks
the current active Super Admin profile and exact active shop, binds the request payload,
and consumes narrower actor/source rate limits. A wrong PIN permanently fails that request
UUID; a retry after a transport failure preserves the original UUID.

After reauthentication, one database transaction marks the shop inactive, revokes sessions
for exclusive shop identities, removes every current public/private row carrying that
`shop_id` in foreign-key-safe passes, removes exclusive local profiles/PIN verifiers, and
deletes the shop root. A schema-wide postcondition aborts and rolls back the transaction if
any tenant-owned row remains. Identities with membership in another shop are preserved.

The shop's normal business audit is part of its deleted tenant graph. One independent,
immutable `private.shop_deletion_audit_events` row survives outside that graph with the
request/actor/shop identifiers, entered reason, and safe aggregate counts only. It contains
no PIN, verifier, token, email, or business values. Request/recovery and audit tables are
private with RLS and no anonymous/authenticated table privileges; prepare/apply/fail/cleanup
RPCs are executable only by `service_role`.

After the transaction commits, the Function fetches each exclusive Auth identity and deletes
it only when its deterministic internal email plus `managed_by` and provisioning-request
metadata all match. Missing identities are already clean; mismatches fail closed. A temporary
Auth Admin failure returns a safe retryable failure while preserving `auth_cleanup_pending`.
Replaying the exact request skips tenant deletion, resumes only validated Auth cleanup, and
then returns `SHOP_DELETED`. This operation has no offline/outbox path and cannot run without
an authenticated network connection.
