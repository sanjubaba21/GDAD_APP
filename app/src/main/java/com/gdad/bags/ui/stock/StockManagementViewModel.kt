package com.gdad.bags.ui.stock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.stock.PostedAdjustment
import com.gdad.bags.domain.stock.StockAdjustmentDraft
import com.gdad.bags.domain.stock.StockHistory
import com.gdad.bags.domain.stock.StockManagementRepository
import com.gdad.bags.domain.stock.StockResult
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StockWorkspace(val products: List<CatalogProduct>, val history: StockHistory)
data class StockUiState(
    val content: ContentState<StockWorkspace> = ContentState.Loading,
    val query: String = "",
    val lowOnly: Boolean = false,
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
    val posted: PostedAdjustment? = null,
)
private data class PendingAdjustment(val requestId: String, val draft: StockAdjustmentDraft)

class StockManagementViewModel(
    private val repository: StockManagementRepository,
    private val products: ProductCatalogRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(StockUiState())
    val state: StateFlow<StockUiState> = mutable.asStateFlow()
    private var session: UserSession? = null
    private var history = StockHistory()
    private var catalog = emptyList<CatalogProduct>()
    private var job: Job? = null
    private var pending: PendingAdjustment? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        job?.cancel()
        pending = null
        if (active == null || active.role == UserRole.SUPER_ADMIN) {
            mutable.value = StockUiState(content = ContentState.Empty("No stock access."))
            return
        }
        mutable.value = StockUiState()
        job = viewModelScope.launch {
            products.observe(active).collect { values -> catalog = values; publish() }
        }
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            products.refresh(active)
            when (val result = repository.load(active)) {
                is StockResult.Success -> { history = result.value; publish() }
                is StockResult.Failure -> mutable.update { it.copy(content = ContentState.Error(result.safeMessage)) }
            }
        }
    }

    fun search(value: String) { mutable.update { it.copy(query = value) }; publish() }
    fun toggleLowOnly() { mutable.update { it.copy(lowOnly = !it.lowOnly) }; publish() }
    fun adjust(draft: StockAdjustmentDraft) {
        if (mutable.value.isMutating) return
        pending = PendingAdjustment(UUID.randomUUID().toString(), draft)
        execute()
    }
    fun retry() { if (pending == null) refresh() else execute() }
    fun dismissPosted() = mutable.update { it.copy(posted = null) }

    private fun publish() {
        val current = mutable.value
        val visible = catalog.filter { product ->
            (!current.lowOnly || product.quantityOnHand <= product.lowStockThreshold) &&
                (current.query.isBlank() || product.name.contains(current.query, true) || product.sku.contains(current.query, true))
        }
        mutable.update {
            it.copy(content = if (visible.isEmpty()) ContentState.Empty("No stock matches this filter.") else ContentState.Ready(StockWorkspace(visible, history)))
        }
    }

    private fun execute() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutable.value.isMutating) return
        mutable.update { it.copy(isMutating = true, safeMessage = null) }
        viewModelScope.launch {
            when (val result = repository.adjust(active, operation.requestId, operation.draft)) {
                is StockResult.Failure -> mutable.update { it.copy(isMutating = false, safeMessage = result.safeMessage) }
                is StockResult.Success -> {
                    pending = null
                    val reloaded = repository.load(active)
                    if (reloaded is StockResult.Success) history = reloaded.value
                    mutable.update { it.copy(isMutating = false, safeMessage = result.safeMessage, posted = result.value) }
                    publish()
                }
            }
        }
    }

    class Factory(
        private val repository: StockManagementRepository,
        private val products: ProductCatalogRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(StockManagementViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return StockManagementViewModel(repository, products) as T
        }
    }
}
