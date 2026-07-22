# GDAD BAGS managed-account provisioning contract

Status: Task 1.3 repository implementation verified by fresh-database CI. Deployment
and hosted allowed/denied-path verification remain pending.

## Security boundary

Android calls the `manage-users` Edge Function with the project publishable key and the
current Supabase user access token. The function validates that token with Supabase Auth
and passes only its authenticated subject to server-only database RPCs. The caller may
select a target shop, but the database independently proves the caller's current active
role in that exact shop. Client-supplied role, actor, tenant, Auth subject, timestamps,
or verifier values are never accepted.

The Edge Function alone receives `SUPABASE_SERVICE_ROLE_KEY`, `GDAD_PIN_PEPPER_V1`, and
the controlled bootstrap secret. These values must remain hosted secrets and must never
appear in Android, Postgres rows, responses, logs, audit metadata, fixtures, or Git.

## Supported operations and hierarchy

| Action | Authenticated authority | Result |
| --- | --- | --- |
| `bootstrap_super_admin` | Controlled operator bootstrap token; allowed only while no profile exists | One platform Super Admin without shop membership |
| `create_owner` | Active, non-disabled Super Admin | Standard profile plus active Owner membership in the selected active shop |
| `create_salesman` | Active, non-disabled Owner in the selected active shop | Standard profile plus active Salesman membership in that same shop |

Salesmen cannot provision users. Owners cannot create Owners, target another shop, or
grant a higher role. A disabled actor, inactive membership, or inactive shop is denied
again during both reservation and finalization, so losing authority during the external
Auth call cannot complete the operation.

## Client request and response

The POST body contains exactly:

```json
{
  "action": "create_salesman",
  "request_id": "client-generated UUID",
  "login_id": "normalized.user",
  "display_name": "Display Name",
  "pin": "six-to-eight digits",
  "shop_id": "target shop UUID"
}
```

Unknown fields, weak/invalid PINs, malformed identifiers, oversized bodies, invalid
publishable keys, and missing/invalid user sessions fail before provisioning. A normal
success response contains only status, user ID, normalized login ID, application role,
and shop ID. It never returns the internal Auth email, PIN hash, pepper version, audit
metadata, service credential, one-time token, or session token.

## Idempotency and Auth identity

`request_id` is both the idempotency key and the reserved managed Auth subject. The
database serializes each request with a transaction advisory lock and stores an exact
payload reservation. Reusing the same key and payload returns the same subject/result;
reusing it with different fields fails. A new reservation is rejected if its login ID
or Auth subject is already active or present.

The internal email is deterministic from the reserved subject:

```text
acct.<uuid-without-hyphens>@auth.gdad.invalid
```

The Edge Function creates that exact Auth user through the Admin endpoint and requires
`app_metadata.managed_by = gdad_pin_v1`. Existing users are reused only when ID, email,
and marker all match. Compensation explicitly refuses to delete any unrelated identity.

## Transaction and compensation sequence

1. `account_provision_start` validates hierarchy, reserves the exact payload, and
   returns the deterministic Auth subject.
2. The Edge Function creates or validates the managed Auth user.
3. The shared PIN helper HMACs the PIN with the subject and versioned pepper, then
   creates a randomly salted Argon2id verifier.
4. `account_provision_finalize` rechecks authority and atomically inserts profile,
   membership, private verifier, completed reservation, and one immutable audit event.
5. A repeated finalized request returns the original result without duplicating rows.

If Auth creation or finalization fails, the handler first reruns the reservation RPC to
reconcile an ambiguous network result. A completed database transaction wins and is
returned. Otherwise, it deletes only the exact marked managed Auth identity and marks
the reservation failed so the same idempotency key can retry safely. A failed
compensation is logged only as a developer-authored stage and requires operator repair;
request bodies, IDs, login IDs, PINs, hashes, headers, and tokens are never logged.

## Audit and client access

Successful provisioning creates one append-only `private.account_audit_events` row per
request with actor, target, shop, action, and server timestamp. Its safe metadata is an
empty object for this operation. The private request, credential, and audit tables plus
all provisioning RPCs are unavailable to `anon` and `authenticated`; only the trusted
Edge service role can execute the RPCs.

## Deployment and verification gate

Before deployment:

1. Deno format, lint, type-check, and function tests must pass.
2. A fresh database must apply all migrations, pass lint, and pass the 27 provisioning
   pgTAP assertions including hierarchy, cross-shop, collision, retry, audit, and
   privilege cases.
3. The bootstrap token must be generated directly into hosted Edge secrets and backed
   up in the approved secret manager without being printed or written to disk.
4. Bootstrap is used once, then the token is rotated or removed. Normal account
   provisioning always requires an authenticated Supabase session.
5. Hosted integration tests must prove allowed and denied operations and retain only
   redacted status/subject-equality evidence.
