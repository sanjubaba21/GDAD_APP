# Managed account administration contract

Task 1.4 uses a dedicated `manage-accounts` Edge Function. It is separate from
idempotent identity provisioning so a retry can never accidentally create or delete an
Auth user.

## Exact request contract

Every request is `POST` JSON with a valid project publishable key, the actor's Bearer
session, a UUID `request_id`, UUID `target_user_id`, and the actor's 6–8 digit
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
