package com.gdad.bags.ui.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDirectory
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseManagementRepository
import com.gdad.bags.domain.purchase.PurchaseResult
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PurchaseManagementUiState(
    val content: ContentState<PurchaseWorkspace> = ContentState.Loading,
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
    val postedPurchase: PostedPurchase? = null,
)

data class PurchaseWorkspace(val directory: PurchaseDirectory, val products: List<CatalogProduct>)

private sealed interface PendingPurchaseOperation {
    val requestId: String
    data class Vendor(override val requestId: String, val mutation: VendorMutation, val draft: VendorDraft) : PendingPurchaseOperation
    data class Receipt(override val requestId: String, val draft: PurchaseDraft) : PendingPurchaseOperation
}

class PurchaseManagementViewModel(
    private val repository: PurchaseManagementRepository,
    private val products: ProductCatalogRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(PurchaseManagementUiState())
    val state: StateFlow<PurchaseManagementUiState> = mutableState.asStateFlow()
    private var session: UserSession? = null
    private var observeJob: Job? = null
    private var pending: PendingPurchaseOperation? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        observeJob?.cancel()
        pending = null
        if (active == null || active.role != UserRole.OWNER) {
            mutableState.value = PurchaseManagementUiState(content = ContentState.Empty("Owner purchasing access is required."))
            return
        }
        mutableState.value = PurchaseManagementUiState()
        observeJob = viewModelScope.launch {
            combine(repository.observe(active), products.observe(active)) { directory, catalog ->
                PurchaseWorkspace(directory, catalog)
            }.collect { workspace ->
                mutableState.update { it.copy(content = if (workspace.directory.vendors.isEmpty() && workspace.products.isEmpty()) {
                    ContentState.Empty("No vendors or products are available.")
                } else ContentState.Ready(workspace)) }
            }
        }
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            val vendorResult = repository.refresh(active)
            products.refresh(active)
            if (vendorResult is PurchaseResult.Failure) mutableState.update { it.copy(content = ContentState.Error(vendorResult.safeMessage)) }
        }
    }

    fun manageVendor(mutation: VendorMutation, draft: VendorDraft) {
        if (mutableState.value.isMutating) return
        pending = PendingPurchaseOperation.Vendor(UUID.randomUUID().toString(), mutation, draft)
        executePending()
    }

    fun postPurchase(draft: PurchaseDraft) {
        if (mutableState.value.isMutating) return
        pending = PendingPurchaseOperation.Receipt(UUID.randomUUID().toString(), draft)
        executePending()
    }

    fun retry() = if (pending == null) refresh() else executePending()
    fun dismissReceipt() = mutableState.update { it.copy(postedPurchase = null) }

    private fun executePending() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutableState.value.isMutating) return
        mutableState.update { it.copy(isMutating = true, safeMessage = null) }
        viewModelScope.launch {
            val result = when (operation) {
                is PendingPurchaseOperation.Vendor -> repository.manageVendor(active, operation.requestId, operation.mutation, operation.draft)
                is PendingPurchaseOperation.Receipt -> repository.postPurchase(active, operation.requestId, operation.draft)
            }
            when (result) {
                is PurchaseResult.Failure -> mutableState.update { it.copy(isMutating = false, safeMessage = result.safeMessage) }
                is PurchaseResult.Success<*> -> {
                    pending = null
                    mutableState.update { it.copy(
                        isMutating = false,
                        safeMessage = result.safeMessage,
                        postedPurchase = result.value as? PostedPurchase,
                    ) }
                }
            }
        }
    }

    class Factory(
        private val repository: PurchaseManagementRepository,
        private val products: ProductCatalogRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PurchaseManagementViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return PurchaseManagementViewModel(repository, products) as T
        }
    }
}
