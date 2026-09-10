package com.gdad.bags.desktop

import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.domain.report.BusinessReport
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleResult
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DesktopUiState(
    val isInitializing: Boolean = true,
    val isBusy: Boolean = false,
    val session: UserSession? = null,
    val dashboard: BusinessReport? = null,
    val products: List<CatalogProduct> = emptyList(),
    val postedSale: PostedSale? = null,
    val canRetryProductMutation: Boolean = false,
    val canRetrySale: Boolean = false,
    val selectedFeature: DesktopFeature = DesktopFeature.DASHBOARD,
    val errorMessage: String? = null,
    val statusMessage: String? = null,
    val configurationError: String? = null,
)

enum class DesktopFeature(val title: String) {
    DASHBOARD("Dashboard"),
    SALES("Sales"),
    PRODUCTS("Products and stock"),
    PURCHASES("Purchases"),
    VENDORS("Vendors"),
    FINANCE("Cash and bank"),
    REPORTS("Reports"),
    ACCOUNTS("Accounts and shops"),
}

internal fun featuresFor(role: UserRole): List<DesktopFeature> = when (role) {
    UserRole.SUPER_ADMIN -> listOf(DesktopFeature.ACCOUNTS)
    UserRole.OWNER -> DesktopFeature.entries
    UserRole.SALESMAN -> listOf(
        DesktopFeature.DASHBOARD,
        DesktopFeature.SALES,
        DesktopFeature.PRODUCTS,
        DesktopFeature.REPORTS,
    )
}

class DesktopController(
    private val dependencies: DesktopDependencies,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private data class PendingProduct(
        val requestId: String,
        val mutation: ProductMutation,
        val draft: ProductDraft,
    )

    private data class PendingSale(val requestId: String, val draft: SaleDraft)

    private var pendingProduct: PendingProduct? = null
    private var pendingSale: PendingSale? = null
    private val mutableState = MutableStateFlow(
        DesktopUiState(configurationError = dependencies.configurationError),
    )
    val state: StateFlow<DesktopUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            mutableState.value = when (val restored = dependencies.authRepository.restoreSession()) {
                is SessionRestoreResult.Authenticated -> DesktopUiState(
                    isInitializing = false,
                    session = restored.session,
                    configurationError = dependencies.configurationError,
                )
                is SessionRestoreResult.SignedOut -> DesktopUiState(
                    isInitializing = false,
                    errorMessage = restored.message,
                    configurationError = dependencies.configurationError,
                )
            }
            val restoredSession = mutableState.value.session
            if (restoredSession != null) {
                refreshDashboardInternal(restoredSession)
            }
        }
    }

    fun login(loginId: String, pin: String) {
        if (mutableState.value.isBusy || mutableState.value.isInitializing) return
        mutableState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
        scope.launch {
            when (val result = dependencies.authRepository.login(loginId, pin)) {
                is LoginResult.Success -> {
                    dependencies.clearBusinessState()
                    pendingProduct = null
                    pendingSale = null
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            session = result.session,
                            selectedFeature = featuresFor(result.session.role).first(),
                            statusMessage = "Signed in securely.",
                        )
                    }
                    refreshDashboardInternal(result.session)
                }
                is LoginResult.Failure -> mutableState.update {
                    it.copy(isBusy = false, errorMessage = result.message)
                }
            }
        }
    }

    fun select(feature: DesktopFeature) {
        val session = mutableState.value.session ?: return
        if (feature !in featuresFor(session.role)) return
        mutableState.update { it.copy(selectedFeature = feature, errorMessage = null) }
        if (feature == DesktopFeature.PRODUCTS || feature == DesktopFeature.SALES) {
            refreshProducts()
        }
    }

    fun refreshDashboard() {
        val session = mutableState.value.session ?: return
        if (session.shopId == null || mutableState.value.isBusy) return
        scope.launch { refreshDashboardInternal(session) }
    }

    fun refreshProducts() {
        val session = mutableState.value.session ?: return
        if (session.shopId == null || mutableState.value.isBusy) return
        scope.launch { refreshProductsInternal(session) }
    }

    fun mutateProduct(mutation: ProductMutation, draft: ProductDraft) {
        if (mutableState.value.isBusy) return
        pendingProduct = PendingProduct(UUID.randomUUID().toString(), mutation, draft)
        mutableState.update { it.copy(canRetryProductMutation = false) }
        executePendingProduct()
    }

    fun retryProductMutation() = executePendingProduct()

    fun postSale(draft: SaleDraft) {
        if (mutableState.value.isBusy) return
        pendingSale = PendingSale(UUID.randomUUID().toString(), draft)
        mutableState.update { it.copy(canRetrySale = false) }
        executePendingSale()
    }

    fun retrySale() = executePendingSale()

    fun dismissPostedSale() {
        mutableState.update { it.copy(postedSale = null) }
    }

    fun clearMessage() {
        mutableState.update { it.copy(errorMessage = null, statusMessage = null) }
    }

    fun logout() {
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
        scope.launch {
            dependencies.authRepository.logout()
            dependencies.clearBusinessState()
            pendingProduct = null
            pendingSale = null
            mutableState.value = DesktopUiState(
                isInitializing = false,
                configurationError = dependencies.configurationError,
                statusMessage = "Signed out. This desktop build does not persist sessions.",
            )
        }
    }

    fun close() = scope.cancel()

    private suspend fun refreshDashboardInternal(session: UserSession) {
        if (session.shopId == null) return
        mutableState.update { it.copy(isBusy = true, errorMessage = null) }
        when (val result = dependencies.loadDashboard(session)) {
            null -> mutableState.update { it.copy(isBusy = false) }
            is RemoteResult.Success -> mutableState.update {
                it.copy(
                    isBusy = false,
                    dashboard = result.value,
                    statusMessage = "Dashboard refreshed from the trusted report.",
                )
            }
            is RemoteResult.Failure -> mutableState.update {
                it.copy(
                    isBusy = false,
                    errorMessage = when (result.error.kind) {
                        RemoteErrorKind.OFFLINE -> "You appear to be offline. Cached desktop reads are not available yet."
                        RemoteErrorKind.TIMEOUT -> "Dashboard refresh timed out. Try again."
                        RemoteErrorKind.UNAUTHORIZED -> "Your session is no longer authorized. Sign in again."
                        RemoteErrorKind.RATE_LIMITED -> "Too many requests. Try again later."
                        else -> "Dashboard could not be refreshed safely."
                    },
                )
            }
        }
    }

    private suspend fun refreshProductsInternal(session: UserSession) {
        mutableState.update { it.copy(isBusy = true, errorMessage = null) }
        when (val result = dependencies.loadProducts(session)) {
            null -> mutableState.update {
                it.copy(isBusy = false, errorMessage = "Desktop product services are not configured.")
            }
            is ProductResult.Failure -> mutableState.update {
                it.copy(isBusy = false, errorMessage = result.safeMessage)
            }
            is ProductResult.Success -> mutableState.update {
                it.copy(
                    isBusy = false,
                    products = dependencies.products?.snapshot().orEmpty(),
                    statusMessage = result.safeMessage,
                )
            }
        }
    }

    private fun executePendingProduct() {
        val session = mutableState.value.session ?: return
        val operation = pendingProduct ?: return
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
        scope.launch {
            when (
                val result = dependencies.products?.mutate(
                    session,
                    operation.requestId,
                    operation.mutation,
                    operation.draft,
                )
            ) {
                null -> mutableState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "Desktop product services are not configured.",
                        canRetryProductMutation = true,
                    )
                }
                is ProductResult.Failure -> mutableState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = result.safeMessage,
                        canRetryProductMutation = true,
                    )
                }
                is ProductResult.Success -> {
                    pendingProduct = null
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            products = dependencies.products.snapshot(),
                            statusMessage = result.safeMessage,
                            canRetryProductMutation = false,
                        )
                    }
                }
            }
        }
    }

    private fun executePendingSale() {
        val session = mutableState.value.session ?: return
        val operation = pendingSale ?: return
        if (mutableState.value.isBusy) return
        mutableState.update { it.copy(isBusy = true, errorMessage = null, statusMessage = null) }
        scope.launch {
            when (val result = dependencies.postSale(session, operation.requestId, operation.draft)) {
                null -> mutableState.update {
                    it.copy(
                        isBusy = false,
                        errorMessage = "Desktop sale services are not configured.",
                        canRetrySale = true,
                    )
                }
                is SaleResult.Failure -> mutableState.update {
                    it.copy(isBusy = false, errorMessage = result.safeMessage, canRetrySale = true)
                }
                is SaleResult.Success -> {
                    pendingSale = null
                    mutableState.update {
                        it.copy(
                            isBusy = false,
                            products = dependencies.products?.snapshot().orEmpty(),
                            postedSale = result.value,
                            statusMessage = result.safeMessage,
                            canRetrySale = false,
                        )
                    }
                    refreshDashboardInternal(session)
                }
            }
        }
    }
}
