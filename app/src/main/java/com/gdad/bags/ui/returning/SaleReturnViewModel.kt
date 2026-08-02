package com.gdad.bags.ui.returning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.returning.PostedSaleReturn
import com.gdad.bags.domain.returning.ReturnResult
import com.gdad.bags.domain.returning.SaleHistory
import com.gdad.bags.domain.returning.SaleHistoryEntry
import com.gdad.bags.domain.returning.SaleReturnDraft
import com.gdad.bags.domain.returning.SaleReturnRepository
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SaleHistoryFilter(val label: String) {
    ALL("All sales"),
    RETURNABLE("Returnable"),
    CREDIT("Credit"),
    RETURNED("With returns"),
}

data class SaleReturnUiState(
    val content: ContentState<SaleHistory> = ContentState.Loading,
    val query: String = "",
    val filter: SaleHistoryFilter = SaleHistoryFilter.ALL,
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
    val posted: PostedSaleReturn? = null,
)

private data class PendingReturn(
    val requestId: String,
    val draft: SaleReturnDraft,
)

class SaleReturnViewModel(
    private val repository: SaleReturnRepository,
) : ViewModel() {
    private val mutable = MutableStateFlow(SaleReturnUiState())
    val state: StateFlow<SaleReturnUiState> = mutable.asStateFlow()

    private var session: UserSession? = null
    private var history = SaleHistory()
    private var pending: PendingReturn? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        pending = null
        if (active == null || active.role == UserRole.SUPER_ADMIN) {
            mutable.value = SaleReturnUiState(
                content = ContentState.Empty("No sale-history access."),
            )
            return
        }
        mutable.value = SaleReturnUiState()
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            when (val result = repository.load(active)) {
                is ReturnResult.Success -> {
                    history = result.value
                    mutable.update { it.copy(safeMessage = result.safeMessage) }
                    publish()
                }
                is ReturnResult.Failure -> mutable.update {
                    it.copy(content = ContentState.Error(result.safeMessage))
                }
            }
        }
    }

    fun search(value: String) {
        mutable.update { it.copy(query = value) }
        publish()
    }

    fun filter(value: SaleHistoryFilter) {
        mutable.update { it.copy(filter = value) }
        publish()
    }

    fun post(draft: SaleReturnDraft) {
        if (mutable.value.isMutating) return
        pending = PendingReturn(UUID.randomUUID().toString(), draft)
        execute()
    }

    fun retry() {
        if (pending == null) refresh() else execute()
    }

    fun dismissPosted() = mutable.update { it.copy(posted = null) }

    private fun publish() {
        val current = mutable.value
        val visible = history.sales.filter { sale ->
            sale.matches(current.query) && when (current.filter) {
                SaleHistoryFilter.ALL -> true
                SaleHistoryFilter.RETURNABLE -> sale.isReturnable
                SaleHistoryFilter.CREDIT -> sale.isCredit
                SaleHistoryFilter.RETURNED -> sale.returnedPaisa > 0
            }
        }
        mutable.update {
            it.copy(
                content = if (visible.isEmpty()) {
                    ContentState.Empty("No sales match this filter.")
                } else {
                    ContentState.Ready(SaleHistory(visible))
                },
            )
        }
    }

    private fun execute() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutable.value.isMutating) return
        mutable.update { it.copy(isMutating = true, safeMessage = null) }
        viewModelScope.launch {
            when (
                val result = repository.post(
                    active,
                    operation.requestId,
                    operation.draft,
                )
            ) {
                is ReturnResult.Failure -> {
                    if (result.error?.kind in setOf(
                            RemoteErrorKind.VALIDATION,
                            RemoteErrorKind.CONFLICT,
                        )
                    ) {
                        val reloaded = repository.load(active)
                        if (reloaded is ReturnResult.Success) history = reloaded.value
                    }
                    mutable.update {
                        it.copy(isMutating = false, safeMessage = result.safeMessage)
                    }
                    publish()
                }
                is ReturnResult.Success -> {
                    pending = null
                    val reloaded = repository.load(active)
                    if (reloaded is ReturnResult.Success) history = reloaded.value
                    mutable.update {
                        it.copy(
                            isMutating = false,
                            safeMessage = result.safeMessage,
                            posted = result.value,
                        )
                    }
                    publish()
                }
            }
        }
    }

    class Factory(
        private val repository: SaleReturnRepository,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SaleReturnViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return SaleReturnViewModel(repository) as T
        }
    }
}

private val SaleHistoryEntry.isReturnable: Boolean
    get() = status in setOf("posted", "partially_returned") &&
        lines.any { it.returnableQuantity > 0 }

private fun SaleHistoryEntry.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return id.contains(query, ignoreCase = true) ||
        customerName.orEmpty().contains(query, ignoreCase = true) ||
        customerContact.orEmpty().contains(query, ignoreCase = true) ||
        lines.any {
            it.productName.contains(query, ignoreCase = true) ||
                it.sku.contains(query, ignoreCase = true)
        }
}
