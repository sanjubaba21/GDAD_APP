package com.gdad.bags.ui.finance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.data.remote.RemoteErrorKind
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.finance.*
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface FinanceReceipt {
    data class Expense(val value: PostedExpense) : FinanceReceipt
    data class Movement(val value: PostedCashMovement) : FinanceReceipt
    data class Transfer(val value: PostedTransfer) : FinanceReceipt
    data class Reversal(val value: PostedFinancialReversal) : FinanceReceipt
}

data class FinanceUiState(
    val content: ContentState<FinanceLedger> = ContentState.Loading,
    val isMutating: Boolean = false,
    val canRetry: Boolean = false,
    val safeMessage: String? = null,
    val receipt: FinanceReceipt? = null,
)

private sealed interface PendingFinance {
    val id: String

    data class Expense(override val id: String, val draft: ExpenseDraft) : PendingFinance
    data class Movement(override val id: String, val draft: CashMovementDraft) : PendingFinance
    data class Transfer(override val id: String, val draft: TransferDraft) : PendingFinance
    data class Reversal(
        override val id: String,
        val draft: FinancialReversalDraft,
    ) : PendingFinance
}

class FinanceViewModel(private val repository: FinanceRepository) : ViewModel() {
    private val mutable = MutableStateFlow(FinanceUiState())
    val state: StateFlow<FinanceUiState> = mutable.asStateFlow()
    private var session: UserSession? = null
    private var pending: PendingFinance? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        pending = null
        if (active == null || active.role != UserRole.OWNER) {
            mutable.value = FinanceUiState(
                content = ContentState.Empty("Owner finance access is required."),
            )
            return
        }
        mutable.value = FinanceUiState()
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            when (val result = repository.load(active)) {
                is FinanceResult.Success -> mutable.update {
                    it.copy(
                        content = if (
                            result.value.accounts.isEmpty() && result.value.transactions.isEmpty()
                        ) {
                            ContentState.Empty("No cash or bank activity yet.")
                        } else {
                            ContentState.Ready(result.value)
                        },
                        canRetry = false,
                        safeMessage = result.safeMessage,
                    )
                }
                is FinanceResult.Failure -> mutable.update {
                    it.copy(
                        content = ContentState.Error(result.safeMessage),
                        canRetry = false,
                    )
                }
            }
        }
    }

    fun postExpense(draft: ExpenseDraft) =
        start(PendingFinance.Expense(UUID.randomUUID().toString(), draft))

    fun postMovement(draft: CashMovementDraft) =
        start(PendingFinance.Movement(UUID.randomUUID().toString(), draft))

    fun postTransfer(draft: TransferDraft) =
        start(PendingFinance.Transfer(UUID.randomUUID().toString(), draft))

    fun reverse(draft: FinancialReversalDraft) =
        start(PendingFinance.Reversal(UUID.randomUUID().toString(), draft))

    fun retry() {
        if (pending == null) refresh() else execute()
    }

    fun dismissReceipt() = mutable.update { it.copy(receipt = null) }

    private fun start(operation: PendingFinance) {
        if (mutable.value.isMutating) return
        pending = operation
        execute()
    }

    private fun execute() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutable.value.isMutating) return
        mutable.update { it.copy(isMutating = true, canRetry = false, safeMessage = null) }
        viewModelScope.launch {
            val result = when (operation) {
                is PendingFinance.Expense ->
                    repository.postExpense(active, operation.id, operation.draft)
                is PendingFinance.Movement ->
                    repository.postMovement(active, operation.id, operation.draft)
                is PendingFinance.Transfer ->
                    repository.postTransfer(active, operation.id, operation.draft)
                is PendingFinance.Reversal ->
                    repository.reverse(active, operation.id, operation.draft)
            }
            when (result) {
                is FinanceResult.Failure -> handleFailure(active, result)
                is FinanceResult.Success<*> -> handleSuccess(active, result)
            }
        }
    }

    private suspend fun handleFailure(active: UserSession, result: FinanceResult.Failure) {
        if (result.error?.kind in setOf(RemoteErrorKind.VALIDATION, RemoteErrorKind.CONFLICT)) {
            val loaded = repository.load(active)
            if (loaded is FinanceResult.Success) {
                mutable.update { it.copy(content = ContentState.Ready(loaded.value)) }
            }
        }
        val retryable = result.error?.retry?.let { it != RetryDisposition.NEVER } == true
        if (!retryable) pending = null
        mutable.update {
            it.copy(
                isMutating = false,
                canRetry = retryable,
                safeMessage = result.safeMessage,
            )
        }
    }

    private suspend fun handleSuccess(
        active: UserSession,
        result: FinanceResult.Success<*>,
    ) {
        pending = null
        val loaded = repository.load(active)
        val content = if (loaded is FinanceResult.Success) {
            ContentState.Ready(loaded.value)
        } else {
            mutable.value.content
        }
        val receipt = when (val value = result.value) {
            is PostedExpense -> FinanceReceipt.Expense(value)
            is PostedCashMovement -> FinanceReceipt.Movement(value)
            is PostedTransfer -> FinanceReceipt.Transfer(value)
            is PostedFinancialReversal -> FinanceReceipt.Reversal(value)
            else -> null
        }
        mutable.update {
            it.copy(
                content = content,
                isMutating = false,
                canRetry = false,
                safeMessage = result.safeMessage,
                receipt = receipt,
            )
        }
    }

    class Factory(private val repository: FinanceRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FinanceViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return FinanceViewModel(repository) as T
        }
    }
}
