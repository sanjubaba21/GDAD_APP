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

- `data/remote`: API client configuration/factories and, in later tasks, transport DTOs.
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

## Transitional boundary

`PreviewAuthRepository` remains selected only inside `ProductionAppContainer` so the
uploaded first-release UI continues to run while Task 4.2 implements hosted PIN login.
Task 4.2 must replace that one binding with the production repository. Task 4.3 must then
delete the preview implementation or restrict it to a debug-only source set. Preview
authentication is not acceptable in a release APK.
