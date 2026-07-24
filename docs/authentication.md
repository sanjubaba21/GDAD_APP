# GDAD BAGS authentication contract

Status: **B3.1 approved design**. B3.2 and B3.3 must implement and test this contract
before `PreviewAuthRepository` can be replaced.

## Security boundary

- `auth.users.id` is the immutable authenticated subject used by `auth.uid()` and RLS.
- `public.user_profiles.login_id` is the normalized, globally unique app login handle.
  It may be renamed by a privileged account-management operation without changing the
  Auth subject or tenant relationships.
- `public.user_profiles` and `public.shop_memberships` are authoritative for enabled
  state, platform role, shop access, and shop role. Role/shop claims are not copied into
  JWT user metadata for authorization.
- `private.login_credentials` is the only PIN-verifier store. It is never exposed to
  Android, `anon`, or `authenticated` PostgREST callers.

## Managed Auth identity

Account-provisioning functions create one Supabase Auth user per application user. The
Auth email is an internal, confirmed, non-deliverable identifier such as
`acct.<random-128-bit>@auth.gdad.invalid`; it is not derived from `login_id`, displayed
to the user, or accepted by the Android login form. The managed Auth user has no shared
or client-known password. `app_metadata` may contain only a contract marker such as
`managed_by = gdad_pin_v1`; authorization remains database-backed.

Creation is a privileged workflow: create the Auth user, then create its profile,
membership, and PIN verifier. A partial failure must be compensated by deleting the new
Auth user, and the request must be idempotent so retries cannot create duplicates.

## Login input and normalization

Request body:

```json
{
  "login_id": "owner.kathmandu",
  "pin": "123456",
  "request_id": "uuid",
  "device_id": "opaque-install-id"
}
```

- Trim and lowercase `login_id`; require `^[a-z0-9][a-z0-9._-]{2,63}$`.
- Require a 6–8 digit PIN. Reject common values and sequences when setting/resetting a
  PIN; never reveal this blocklist during login.
- Treat `request_id` as an idempotency/correlation value and `device_id` as untrusted
  rate-limit input, never as identity proof.
- Cap request size and reject unknown fields before any database work.

## PIN verifier

The Edge Function reads `GDAD_PIN_PEPPER_V1` from hosted function secrets. The pepper
must be randomly generated, must never enter Postgres, Android, logs, Git, or an API
response, and must be independently backed up in the approved secret manager.

Verifier version 1:

1. Compute `HMAC-SHA-256(pepper, "gdad-pin-v1\0" || user_id || "\0" || pin)`.
2. Encode the 32-byte result as unpadded base64.
3. Hash it with Argon2id using a unique random salt and at least `m=19456, t=2, p=1`.
4. Store only the Argon2id PHC string in `pin_hash` and `1` in `pepper_version`.
5. Compare in constant time through the vetted Argon2 implementation.

The PHC string carries salt and work parameters. Increasing work factors uses
rehash-on-success. Pepper rotation introduces a new versioned secret and forces PIN
reset for accounts whose old pepper can no longer be retained.

## Session establishment

The `pin-login` Edge Function accepts only a valid project publishable key in the
`apikey` header. Because `sb_publishable_` keys are not JWTs, its platform
`verify_jwt` setting is disabled and the handler validates publishable-key mode itself.
The function must never accept a secret/service-role key from Android.

After atomic PIN verification and lockout checks:

1. Load the expected Auth user and internal email through trusted server credentials.
2. Call Auth Admin `generateLink` with `type: "magiclink"`. This generates but does not
   email a single-use link/token.
3. Exchange `properties.hashed_token` inside the function with Auth `verifyOtp`, using
   the verification type returned by Auth.
4. Confirm the returned session subject equals the expected `auth.users.id`.
5. Return only the standard access token, refresh token, expiry, and token type over
   TLS. Never log the request, PIN, one-time token, or session tokens.
6. Android imports the returned values as a Supabase `UserSession`, then reads its
   profile and memberships under RLS. It does not trust role/shop values from login.

This produces a normal Supabase session with refresh-token rotation. It avoids hidden
shared passwords, custom JWT signing, and service credentials on the device.

## Failure and abuse contract

- Unknown login, wrong PIN, disabled profile, inactive shop/membership, and account
  lockout return the same generic authentication failure body. Perform a dummy Argon2id
  verification for unknown users to reduce timing disclosure.
- Malformed requests return `400 INVALID_REQUEST`; authentication failures return
  `401 INVALID_CREDENTIALS`; source/account throttling returns `429 TRY_AGAIN_LATER`.
- B3.3 must atomically record failures and implement per-account plus per-source limits.
  Lock after five consecutive failures for at least 15 minutes; successful verification
  resets the consecutive counter. Never rely only on the caller-provided device ID.
- Session and audit logs contain request ID, coarse source fingerprint, result class,
  and server timestamp, but never login ID in plaintext, PIN material, or tokens.
- Disabling a profile immediately blocks protected data through RLS. The account
  workflow also revokes existing Auth sessions; access-token expiry remains a bounded
  residual window for endpoints that do not re-check enabled state.

## Android session behavior

- Persist sessions only through the Supabase Auth storage mechanism; do not put tokens
  in UI state, logs, analytics, saved-state bundles, or crash reports.
- Allow automatic refresh and serialize refresh attempts. Refresh-token replay outside
  Supabase's reuse interval terminates the session.
- Logout uses Supabase Auth sign-out and clears app caches. PIN change/reset revokes
  existing sessions according to the account-management operation.
- Offline mode may show previously authorized cached data after explicit product policy
  approval, but must not allow sensitive queued mutations without a fresh valid session.

### Authoritative Android flow

The first-release user-ID/PIN flow is a direct TLS API exchange and does **not** open a
browser or redirect through Android. This is intentional: `pin-login` generates and
consumes the magic-link token entirely inside the trusted Edge Function, then returns
only the resulting Supabase access/refresh token pair to the calling repository.

1. Android generates a UUID `request_id` and sends the normalized login ID, PIN, and
   opaque installation ID to `pin-login` using the configured publishable key.
2. The repository keeps the response body in a method-local value; tokens must not be
   placed in Compose state, navigation arguments, `SavedStateHandle`, logs, analytics,
   screenshots, clipboard data, or crash reports.
3. On success, construct the Supabase Kotlin `UserSession` from the returned access
   token, refresh token, expiry, and token type, then call `auth.importSession(...,
   autoRefresh = true)`. The library version pinned by this repository exposes this API.
4. Retrieve the authenticated user through Auth and load `user_profiles` plus active
   `shop_memberships` under RLS. The dashboard role and shop come only from those rows;
   no login response field or decoded JWT role is authoritative.
5. Publish authenticated UI state only after the profile/membership load succeeds. If
   identity loading fails, clear the imported session and return a sanitized error.

The Auth plugin must use an application-scoped client, `autoLoadFromStorage = true`,
`autoSaveToStorage = true`, and automatic refresh. Before production integration, its
`SessionManager` must be replaced with a GDAD implementation that encrypts the serialized
session using a non-exportable Android Keystore AES-GCM key. Plain SharedPreferences,
Room, saved-state bundles, and application logs are not approved token stores.

### Startup, refresh, revocation, and logout state machine

- **Cold start/process recreation:** show an authentication-loading state, await Auth
  storage initialization, and validate the restored session by retrieving the user and
  authoritative profile/membership. Navigate only after validation; otherwise clear the
  session and cache and show login.
- **Refresh:** allow the Auth plugin to serialize automatic refresh. A request that
  receives an authentication failure may trigger at most one coordinated refresh and
  retry. Reuse detection or an invalid refresh token transitions once to signed-out and
  clears tenant cache/outbox ownership; it must not loop.
- **Expiry/offline:** expired access with no successful refresh cannot authorize a
  mutation. A future approved cache may expose clearly stale read-only data, but no
  sensitive mutation is confirmed or queued merely because a stale session exists.
- **Disabled/revoked account:** protected backend policies must re-check active profile
  and membership state. Any `401`/`403` caused by disablement or session revocation
  clears local Auth state and tenant data and returns to login with a generic message.
- **User logout:** call Supabase sign-out for the current session, then clear local Auth
  storage, Room/cache data, pending tenant work, and in-memory UI state even if the
  network request fails. Account disable and PIN reset revoke server sessions through
  the privileged account-management operation; access JWTs remain bounded by expiry.

### Reserved callback contract

PIN login requires no redirect URL. To avoid inventing a second authentication flow,
the app will not register or accept Auth deep links until Task 4.7 implements and tests
the handler. If a future approved OAuth/passwordless flow is added, the reserved exact
callback is:

```text
com.gdad.bags://auth/callback
scheme = com.gdad.bags
host = auth
path = /callback
```

The matching manifest filter will use `VIEW`, `DEFAULT`, and `BROWSABLE` with the exact
scheme/host/path above, and startup will call `supabase.handleDeeplinks(intent)` only
after validating that exact URI. No wildcard redirect is permitted. The hosted Redirect
URLs allow-list must contain only the exact callback when the handler is implemented;
the current local-only `site_url`/redirects in `supabase/config.toml` must not be pushed.
Custom-scheme interception is an accepted reason to keep this callback disabled for the
PIN-only first release; an HTTPS Android App Link requires an owned domain and verified
`assetlinks.json` before it can replace the reserved scheme.

### Android threat and failure decisions

- A malicious app must not receive PIN-login tokens because the production PIN flow has
  no external intent, browser, or callback.
- The Edge response uses `Cache-Control: no-store`; Android additionally avoids HTTP
  body logging and redacts Auth headers in release diagnostics.
- A hosted PIN-login failure stage is returned only during an operator-controlled
  diagnostic request whose one-time `x-gdad-diagnostic-token` matches the temporary
  `GDAD_LOGIN_DIAGNOSTIC_TOKEN` Edge secret. Normal clients continue to receive only
  the generic `SERVICE_UNAVAILABLE` response, and the temporary secret must be removed
  immediately after verification.
- For an upstream Auth failure, that trusted stage may append only a machine-readable
  error identifier matching `[a-z0-9_]{3,64}`. Human messages, response bodies, emails,
  identifiers, and request content remain excluded.
- The same temporary diagnostic mode may redeem the generated email token hash a second
  time solely to prove that hosted Auth rejects reuse. Only the boolean
  `single_use_verified` evidence is returned to the trusted operator; the token hash and
  second Auth response remain server-only. Normal app logins perform one exchange.
- Session establishment parses the raw GoTrue admin `generate_link` response, whose
  token hash is top-level (while retaining compatibility with the client-library
  wrapper), and exchanges that hash through the documented email-token verification
  type. No action link or token hash is returned to Android.
- The publishable key is client-safe but identifies only the Supabase project. It does
  not replace RLS, user authentication, or server-derived authorization.
- Concurrent login/refresh/logout actions are serialized by the repository. Logout wins
  over an in-flight refresh and prevents a late response from restoring cleared state.
- A process death between receiving and importing tokens may require login again; tokens
  are never temporarily persisted outside the encrypted Auth session manager.

## B3.2/B3.3 acceptance tests

- Correct PIN returns a refreshable session whose JWT subject is the mapped user ID.
- Unknown, wrong, disabled, inactive, and locked cases have generic bodies and similar
  timing envelopes.
- The one-time token cannot be reused, and a returned session for the wrong subject is
  rejected.
- PIN hashes differ for identical PINs; database-only compromise is insufficient
  without the function pepper; no secret appears in logs or responses.
- Five consecutive failures lock the account; concurrent failures cannot lose updates;
  a valid login after expiry resets the counter.
- Cross-shop reads fail under the resulting JWT, while the mapped user's permitted RLS
  reads succeed.
- Android imports, refreshes, restores, and signs out the returned session without
  exposing tokens.

## References

- [Supabase Auth Admin generateLink](https://supabase.com/docs/reference/javascript/auth-admin-generatelink)
- [Supabase verifyOtp token-hash exchange](https://supabase.com/docs/reference/javascript/auth-verifyotp)
- [Supabase Edge Function authentication](https://supabase.com/docs/guides/functions/auth)
- [Supabase authorization headers](https://supabase.com/docs/guides/functions/auth-headers)
- [Supabase user sessions and refresh rotation](https://supabase.com/docs/guides/auth/sessions)
- [Kotlin importSession](https://supabase.com/docs/reference/kotlin/auth-setsession)
- [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html)
