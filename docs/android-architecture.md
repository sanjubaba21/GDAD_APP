# Android application architecture

This document is the Task 4.1 contract for production data integration. The project uses
explicit constructor injection rather than a reflection or code-generation DI framework.
The dependency graph is small, visible, and replaceable in local unit and UI tests.

## Dependency ownership

`GdadApplication` owns exactly one `AppContainer` for the Android process.
`ProductionAppContainer` is the composition root: it constructs repository
implementations, use cases, and the lazy application-scoped `SupabaseClient`.

`MainActivity` obtains the container from the application and passes the
`AuthenticateUser` interface into `AuthViewModel.Factory`. Composables receive immutable
UI state and event callbacks; they never construct repositories, clients, use cases, or
ViewModels and do not query a service locator.

```text
GdadApplication
  -> AppContainer
     -> application-scoped SupabaseClient
     -> AuthRepository implementation
        -> AuthenticateUser use-case interface
           -> AuthViewModel
              -> AuthUiState and UI events
                 -> GdadApp composables
```

## Layer rules

- `data/remote`: API client configuration/factories, transport DTOs, timeout/error
  classification, retry policy, and sanitized diagnostics.
- `data/auth`: authentication repository implementations only.
- `domain/auth`: repository and use-case interfaces plus domain results.
- `domain/model`: backend-independent business models.
- `ui/auth`: ViewModel and immutable Compose-facing UI state.
- `ui`: stateless screen composition driven by state and callbacks.
- `di`: the production composition root; it is the only place allowed to select concrete
  implementations.

Transport DTOs must not leak into domain or UI packages. Future Room entities belong in
`data/local` and must be mapped to domain models by repository implementations.

## Production and test graphs

Production uses `GdadApplication` and `ProductionAppContainer`. A deterministic test can
implement `AppContainer`, or inject a fake `AuthRepository` into `LoginUseCase` and pass
the resulting `AuthenticateUser` interface to `AuthViewModel.Factory`. The unit test
`LoginUseCaseTest` demonstrates this override without networking or Android state.

The UI-facing `AppContainer` exposes use-case interfaces only; it does not force a fake
graph to construct SDK clients. `ProductionAppContainer` additionally owns the concrete
Supabase client for injection into production repositories.

The Supabase client is created lazily and cached by the application container. The
factory is stateless; repositories added in Tasks 4.2 onward must receive the container's
single client through their constructors and must not create another client.

## Remote boundary

Task 4.2 replaced the production binding with `ProductionAuthRepository`; Task 4.3
removed preview authentication from production source. Task 4.4 centralizes transport
behavior under `data/remote`:

- serializable request/response DTOs mirror hosted snake-case contracts and never leak
  into domain or UI packages;
- `RemoteCallExecutor` applies one bounded timeout, classifies validation,
  unauthorized, conflict, offline, timeout, rate-limit, and unknown failures, and
  preserves caller cancellation;
- an authenticated `401`/`403` triggers exactly one Supabase Auth refresh and one retry;
- retry disposition is explicit (`NEVER`, `AFTER_AUTH_REFRESH`, or `WITH_BACKOFF`);
- diagnostics retain only operation, category, numeric status, and exception type. Raw
  response/exception messages and request bodies are never recorded;
- repositories map remote categories to `OperationErrorKind` and fixed user-safe text.

Future feature repositories must use this boundary rather than catching SDK exceptions
or displaying backend messages directly.

## Local read boundary

Task 4.5 adds an application-scoped Room database and `RoomCacheStore`. The cache owns
only remote-derived read models and exposes tenant/user-filtered Flows. Authentication
activates the authoritative identity and purges Room on user/shop change, failed identity
validation, or logout. `CacheSynchronizer` publishes complete remote snapshots in one
transaction and retains the last good snapshot on classified remote failure. See
`docs/offline-cache.md` for the schema, ownership, refresh, and migration contract.

Task 4.6 extends the same database with a durable mutation outbox. Only product
management (protected by the backend request ledger) and notification read state
(naturally idempotent) may queue offline. WorkManager runs unique connected-network
work with exponential backoff. Financial, inventory, return, payment, and account
administration mutations remain online-only. Terminal safe error categories remain in
Room and publish a generic dashboard resolution notice; backend response text is never
shown or stored.

## Navigation and shared UI state

Task 4.7 uses stable AndroidX Navigation Compose type-safe serializable routes. The
authenticated graph is created only after authoritative session restoration and is keyed
by user, role, and shop so an identity change cannot retain another user's back stack.
Both dashboard clicks and destination rendering call `NavigationPolicy`; a forged/direct
route for a disallowed role renders no protected content and returns to the dashboard.

The PIN-only first release registers no external app links or authentication callback.
`ExternalNavigationPolicy` rejects every external URI, preserving the single hosted
PIN-session path. `NavController` owns back-stack save/restore; JVM navigation tests save
and restore a typed feature route to cover process recreation and back behavior.

`ContentStateHost` is the shared loading/empty/error/ready boundary. Loading, empty, and
error states expose semantic labels; empty/error provide refresh/retry controls using
safe app text. `ConfirmationDialog` provides consistent confirm/cancel behavior and is
used for logout. Feature slices must reuse these components rather than inventing
transport-specific loading or error UI.
