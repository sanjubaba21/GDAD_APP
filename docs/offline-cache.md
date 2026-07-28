# Android offline read cache

This is the Task 4.5 contract for local read persistence. Room is a cache of
authoritative Supabase state, never an independent source of truth and never a mutation
queue. Task 4.6 defines mutation/outbox behavior separately.

## Ownership and isolation

Every cached business row includes `owner_user_id` and `owner_tenant_key` in its primary
key. `owner_tenant_key` is the authenticated shop UUID, or the reserved `__platform__`
value for a Super Admin with no shop. Queries always require both values.

`cache_identity` records the one identity/tenant allowed to own the database contents.
Successful login and restored-session validation activate that identity. A different
user or a different shop causes all cached rows to be deleted transactionally before the
new identity is recorded. Logout and rejected/ambiguous identity validation purge both
the rows and `cache_identity`, even when remote sign-out fails.

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

Room schema export is enabled and committed under `app/schemas/`. Database version 1 is
the initial schema and therefore has no incoming migration. Every future version increase
must include:

1. an explicit `Migration` in `RoomCacheDatabase.MIGRATIONS`;
2. the newly exported schema JSON committed with the source change;
3. a migration test from every supported prior version;
4. tenant/isolation and refresh rollback regression tests.

`fallbackToDestructiveMigration` and its variants are forbidden. If no valid path exists,
the application must fail to open the cache rather than silently erase user data. A
deliberate cache reset requires a separately reviewed product/security decision and an
explicit user-safe recovery path.

The JVM Room tests open the generated version 1 schema and verify the empty initial
migration registry. When version 2 is introduced, use Room's migration test helper and
the committed version 1 JSON to validate the real transition.
