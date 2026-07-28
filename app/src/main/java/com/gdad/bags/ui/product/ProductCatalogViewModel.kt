package com.gdad.bags.ui.product

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductCatalogRepository
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.product.ProductResult
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProductCatalogUiState(
    val role: UserRole? = null,
    val content: ContentState<List<CatalogProduct>> = ContentState.Loading,
    val query: String = "",
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
)

private data class PendingProductOperation(
    val requestId: String,
    val mutation: ProductMutation,
    val draft: ProductDraft,
)

class ProductCatalogViewModel(private val repository: ProductCatalogRepository) : ViewModel() {
    private val mutableState = MutableStateFlow(ProductCatalogUiState())
    val state: StateFlow<ProductCatalogUiState> = mutableState.asStateFlow()
    private var session: UserSession? = null
    private var allProducts: List<CatalogProduct> = emptyList()
    private var observeJob: Job? = null
    private var pending: PendingProductOperation? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        observeJob?.cancel()
        pending = null
        allProducts = emptyList()
        if (active == null || active.role == UserRole.SUPER_ADMIN) {
            mutableState.value = ProductCatalogUiState(
                role = active?.role,
                content = ContentState.Empty("No product catalog access."),
            )
            return
        }
        mutableState.value = ProductCatalogUiState(role = active.role)
        observeJob = viewModelScope.launch {
            repository.observe(active).collect { products ->
                allProducts = products
                publishProducts()
            }
        }
        refresh()
    }

    fun search(query: String) {
        mutableState.update { it.copy(query = query) }
        publishProducts()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            when (val result = repository.refresh(active)) {
                is ProductResult.Success -> Unit
                is ProductResult.Failure -> mutableState.update {
                    it.copy(content = ContentState.Error(result.safeMessage))
                }
            }
        }
    }

    fun mutate(mutation: ProductMutation, draft: ProductDraft) {
        pending = PendingProductOperation(UUID.randomUUID().toString(), mutation, draft)
        executePending()
    }

    fun retry() {
        if (pending == null) refresh() else executePending()
    }

    fun clearMessage() = mutableState.update { it.copy(safeMessage = null) }

    private fun publishProducts() {
        val query = mutableState.value.query.trim()
        val visible = if (query.isEmpty()) allProducts else allProducts.filter { product ->
            product.name.contains(query, ignoreCase = true) ||
                product.sku.contains(query, ignoreCase = true) ||
                product.barcode?.contains(query, ignoreCase = true) == true
        }
        mutableState.update {
            it.copy(
                content = if (visible.isEmpty()) {
                    ContentState.Empty(if (query.isEmpty()) "No products are available." else "No products match your search.")
                } else ContentState.Ready(visible),
            )
        }
    }

    private fun executePending() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutableState.value.isMutating) return
        mutableState.update { it.copy(isMutating = true, safeMessage = null) }
        viewModelScope.launch {
            when (val result = repository.mutate(active, operation.requestId, operation.mutation, operation.draft)) {
                is ProductResult.Success -> {
                    pending = null
                    mutableState.update { it.copy(isMutating = false, safeMessage = result.safeMessage) }
                }
                is ProductResult.Failure -> mutableState.update {
                    it.copy(isMutating = false, safeMessage = result.safeMessage)
                }
            }
        }
    }

    class Factory(private val repository: ProductCatalogRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ProductCatalogViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return ProductCatalogViewModel(repository) as T
        }
    }
}
