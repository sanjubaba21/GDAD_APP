package com.gdad.bags

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.OutboxState
import com.gdad.bags.ui.GdadApp
import com.gdad.bags.ui.OutboxResolutionNotice
import com.gdad.bags.ui.auth.AuthViewModel
import com.gdad.bags.ui.account.AccountManagementViewModel
import com.gdad.bags.ui.product.ProductCatalogViewModel
import com.gdad.bags.ui.purchase.PurchaseManagementViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        val container = (application as GdadApplication).appContainer
        AuthViewModel.Factory(
            authenticateUser = container.authenticateUser,
            restoreSession = container.restoreSession,
            logoutUser = container.logoutUser,
        )
    }
    private val accountViewModel: AccountManagementViewModel by viewModels {
        val container = (application as GdadApplication).appContainer
        AccountManagementViewModel.Factory(container.accountManagementRepository)
    }
    private val productViewModel: ProductCatalogViewModel by viewModels {
        val container = (application as GdadApplication).appContainer
        ProductCatalogViewModel.Factory(container.productCatalogRepository)
    }
    private val purchaseViewModel: PurchaseManagementViewModel by viewModels {
        val container = (application as GdadApplication).appContainer
        PurchaseManagementViewModel.Factory(container.purchaseManagementRepository, container.productCatalogRepository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            val accountUiState by accountViewModel.state.collectAsStateWithLifecycle()
            val productUiState by productViewModel.state.collectAsStateWithLifecycle()
            val purchaseUiState by purchaseViewModel.state.collectAsStateWithLifecycle()
            val session = authUiState.session
            LaunchedEffect(session) { accountViewModel.activate(session) }
            LaunchedEffect(session) { productViewModel.activate(session) }
            LaunchedEffect(session) { purchaseViewModel.activate(session) }
            val container = (application as GdadApplication).appContainer
            val noticesFlow = remember(session) {
                session?.let { active ->
                    container.mutationOutbox.observe(CacheOwner(active.userId, active.shopId)).map { rows ->
                        rows.filter { it.state == OutboxState.PERMANENT_FAILURE.name }.map { row ->
                            OutboxResolutionNotice(row.operation, row.lastErrorKind ?: "UNKNOWN")
                        }
                    }
                } ?: flowOf(emptyList())
            }
            val outboxNotices by noticesFlow.collectAsStateWithLifecycle(emptyList())
            GdadApp(
                authUiState = authUiState,
                outboxNotices = outboxNotices,
                accountUiState = accountUiState,
                onRefreshAccounts = accountViewModel::refresh,
                onCreateAccount = accountViewModel::create,
                onAdministerAccount = accountViewModel::administer,
                productUiState = productUiState,
                onSearchProducts = productViewModel::search,
                onRefreshProducts = productViewModel::refresh,
                onMutateProduct = productViewModel::mutate,
                purchaseUiState = purchaseUiState,
                onRefreshPurchases = purchaseViewModel::refresh,
                onManageVendor = purchaseViewModel::manageVendor,
                onPostPurchase = purchaseViewModel::postPurchase,
                onDismissPurchaseReceipt = purchaseViewModel::dismissReceipt,
                onLogin = authViewModel::login,
                onInputChanged = authViewModel::clearError,
                onLogout = authViewModel::logout,
            )
        }
    }
}
