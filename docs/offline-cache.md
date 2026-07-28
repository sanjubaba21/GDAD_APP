# Android offline read cache

This is the Task 4.5/4.6 contract for local read persistence and supported offline
mutations. Cached reads remain derived from authoritative Supabase state. The outbox is
a durable delivery ledger, not an independent business source of truth.

## Ownership and isolation

Every cached business row includes `owner_user_id` and `owner_tenant_key` in its primary
key. `owner_tenant_key` is the authenticated shop UUID, or the reserved `__platform__`
value for a Super Admin with no shop. Queries always require both values.

`cache_identity` records the one identity/tenant allowed to own the database contents.
Successful login and restored-session validation activate that identity. A different
user or a different shop causes all cached rows to be deleted transactionally before the
new identity is recorded. Logout and rejected/ambiguous identity validation purge the
read rows, outbox, and `cache_identity`, even when remote sign-out fails. Snapshot
refresh replaces read rows without deleting confirmed outbox work.

The database is held in the Android application sandbox. It contains no PIN, access
token, refresh token, Supabase key, verifier, or service credential. Auth tokens remain
inside the separate Android Keystore-backed session manager.

## Version 1 read set

The initial schema caches only the minimum offline read models:

- authenticated profile and shop membership;
- products and stock summaries;
- vendors and derived vendor due;
- recent sales;
- cash/bank/ledger account summaries;
- dashboard summary;
- user-visible notifications.

All monetary values are integer paisa, quantities are integer units, and timestamps are
epoch milliseconds. Nullable financial fields represent values the authenticated role is
not authorized to see; they must not be guessed locally.

DAOs expose owner-filtered `Flow` values so feature ViewModels can observe Room rather
than hold demo values. Task 5 feature slices will map these entities into domain/UI models.

## Version 2 mutation outbox

Each confirmed queued mutation stores its operation, JSON object payload, UUID
idempotency key, owner user/tenant, creation/update times, attempt count, state, next
attempt time, and the last safe error category. Payloads over 64 KiB or containing
credential-like field names are rejected before persistence.

Only `MANAGE_PRODUCT` and `MARK_NOTIFICATION_READ` can queue offline. Product dispatch
injects the durable key as `p_idempotency_key` and the active owner tenant as
`p_shop_id`; the backend request ledger makes retries transactionally idempotent.
Notification read uses its database upsert semantics. Sales, purchases, returns,
inventory adjustments, vendor payments/returns, financial entries, and account
administration are explicitly rejected with a live-connection message.

One unique WorkManager job requires `NetworkType.CONNECTED` and uses exponential
backoff beginning at 30 seconds. Room also records bounded retry time, capped at six
hours and five attempts, so process death cannot reset delivery state. Validation,
conflict, and unresolved authorization failures become `PERMANENT_FAILURE`; the
dashboard observes those owner-scoped rows and shows a generic resolution notice.
Response bodies and exception messages are never persisted.

## Refresh semantics

`CacheSynchronizer` serializes refreshes. A typed remote source returns either a complete
`CacheSnapshot` or a classified `RemoteFailure` from the Task 4.4 boundary.

On success, `RoomCacheStore.replaceSnapshot` deletes the old read set, verifies every
incoming row belongs to the requested owner, inserts all nine models, and records the
identity in one Room transaction. Observers cannot see a partially refreshed snapshot.
An owner mismatch throws inside that transaction, rolling the deletion back. A remote
failure never opens a write transaction, so the last complete snapshot remains readable
with its original cache age.

## Schema and migration policy

Room schema export is enabled and committed under `app/schemas/`. Database version 2
adds `mutation_outbox` through explicit `MIGRATION_1_2`; no destructive fallback is
configured. Every future version increase
must include:

1. an explicit `Migration` in `RoomCacheDatabase.MIGRATIONS`;
2. the newly exported schema JSON committed with the source change;
3. a migration test from every supported prior version;
4. tenant/isolation and refresh rollback regression tests.

`fallbackToDestructiveMigration` and its variants are forbidden. If no valid path exists,
the application must fail to open the cache rather than silently erase user data. A
deliberate cache reset requires a separately reviewed product/security decision and an
explicit user-safe recovery path.

The JVM tests execute the real 1-to-2 migration SQL against SQLite and verify the
outbox columns and indexes. They also close and reopen a file-backed Room database to
prove confirmed work persists, and cover duplicate keys, retry success, permanent
failure, snapshot preservation, and identity/logout isolation.
