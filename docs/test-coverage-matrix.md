# First-release automated test coverage

This matrix is the Task 6.1 audit record for the Android client. It maps each critical
first-release behavior to deterministic automated evidence. Tests use coroutine virtual time,
in-memory/on-disk Room fixtures, or Robolectric Compose; they do not use arbitrary sleeps.

| Risk / workflow | Unit or integration evidence | UI evidence | Required failure boundaries |
|---|---|---|---|
| Exact money and rounding | `MoneyAmountsTest`, purchase/sale/stock repository tests | Transaction screen suites | Fractional paisa, negative/overflow input, checked sums/products, proportional half-up rounding |
| FIFO allocation and restoration | `FifoAllocatorTest`; database `atomic_fifo_sale` and `atomic_sale_return` pgTAP suites | Sale, return, and stock screen suites | Shortage, deterministic tie, tenant/product mixing, over-return, forged/missing/duplicate allocation, lot-capacity breach |
| Authentication and session restore | `ProductionAuthRepositoryTest`, `LoginUseCaseTest`, `AuthViewModelTest` | `GdadAppAuthScreenTest` | Invalid input, hosted error mapping, token-import compensation, authoritative restore, initialization gate, duplicate login, logout purge |
| Remote error mapping and retry | `RemoteCallExecutorTest` plus each production repository suite | Retry controls in account/product/sale/return/stock/vendor/finance suites | Offline, timeout, unauthorized, validation, conflict, rate limit, cancellation, one refresh retry |
| Room migration and owner isolation | `RoomCacheMigrationTest`, `RoomCacheStoreTest` | Cached/offline states exercised by feature ViewModels | Every v1→v6 migration, user/shop switch purge, orphan marker, transaction rollback, last-good cache retention |
| Durable outbox | `MutationOutboxTest` | Product and notification optimistic/offline behavior | Secret/risky rejection, duplicate key, exact exponential delay, attempt cap, terminal resolution, stale claim recovery, logout purge |
| Role navigation and process recreation | `AppNavigationTest` | App-shell dashboard and every role-sensitive feature screen | Direct unauthorized target, external/deep link rejection, typed back-stack restoration |
| Account administration | `ProductionAccountManagementRepositoryTest`, `AccountManagementViewModelTest` | `AccountManagementScreenTest` | Same-shop scope, role denial, invalid request, exact-key retry, session revocation message |
| Product catalog | `ProductionProductCatalogRepositoryTest`, `ProductCatalogViewModelTest` | `ProductCatalogScreenTest` | Cost redaction, archive history, role denial, invalid draft, queued transient change, conflict |
| Purchasing | `ProductionPurchaseManagementRepositoryTest`, `PurchaseManagementViewModelTest` | `PurchaseManagementScreenTest` | Owner-only, line/total overflow, payment validation, live-only purchase retry, authoritative receipt |
| Stock adjustments | `ProductionStockManagementRepositoryTest`, `StockManagementViewModelTest` | `StockManagementScreenTest` | Owner-only mutation, salesman cost redaction, reason/lot rules, value overflow, remote validation, retry |
| FIFO sale checkout | `ProductionSaleCheckoutRepositoryTest`, `SaleCheckoutViewModelTest` | `SaleCheckoutScreenTest` | Salesman price/credit denial, exact-key retry, price/payment overflow, stock conflict, authoritative totals |
| Sale returns | `ProductionSaleReturnRepositoryTest`, `SaleReturnViewModelTest` | `SaleReturnScreenTest` | Role denial, invalid draft, conflict refresh, exact retry, cost redaction, safe proportional estimates |
| Vendor finance | `ProductionVendorFinanceRepositoryTest`, `VendorFinanceViewModelTest` | `VendorFinanceScreenTest` | Owner-only, duplicate/overflow allocation, invalid return, live retry, authoritative due/stock refresh |
| Cash and bank finance | `ProductionFinanceRepositoryTest`, `FinanceViewModelTest` | `FinanceScreenTest` | Owner-only, account/date/amount/reversal validation, exact retry, authoritative post-operation balances |
| Trusted reports | `ProductionReportRepositoryTest`, `ReportViewModelTest` | `ReportScreenTest` | Nepal period validation, role redaction, cached dashboard on offline refresh, truthful zero/empty state |
| Notifications | `ProductionNotificationRepositoryTest`, `NotificationViewModelTest` | `NotificationScreenTest` and dashboard badge test | Owner/user/shop isolation, 90-day retention, offline cache, optimistic idempotent mark-read, related-route authorization |
| Shared states and destructive confirmation | `ContentState` consumers in ViewModel suites | `SharedStatesTest`, app-shell logout confirmation | Accessible loading/empty/error, retry, cancel, explicit confirmation |

Backend security and transactional invariants remain independently covered by Deno Edge tests,
fresh-database pgTAP suites, and the multi-session integration harness documented in
`docs/backend-phase3-exit-gate.md`. A physical-device test is intentionally not represented as
automated coverage; it is a Phase 7 release-candidate gate.

## Audit result

- Every production ViewModel has a focused test suite.
- Every first-release destination has Compose or app-shell coverage.
- Critical authorization, validation, offline, retry, cache-isolation, and error-mapping paths
  have negative-path evidence.
- Exact money input never uses floating-point conversion, and local derived totals fail closed
  on overflow.
- No test relies on an arbitrary delay; coroutine tests use virtual time.
