package com.gdad.bags.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.data.remote.RetryDisposition
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AccountManagementRepository
import com.gdad.bags.domain.account.AccountOperationResult
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.DeleteManagedShop
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.ui.components.ContentState
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountManagementUiState(
    val role: UserRole? = null,
    val content: ContentState<AccountDirectory> = ContentState.Loading,
    val isMutating: Boolean = false,
    val safeMessage: String? = null,
)

private sealed interface PendingOperation {
    val requestId: String
    data class CreateShop(override val requestId: String, val input: CreateManagedShop) : PendingOperation
    data class DeleteShop(override val requestId: String, val input: DeleteManagedShop) : PendingOperation
    data class Create(override val requestId: String, val input: CreateManagedAccount) : PendingOperation
    data class Administer(
        override val requestId: String,
        val input: AdministerManagedAccount,
    ) : PendingOperation
}

class AccountManagementViewModel(
    private val repository: AccountManagementRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(AccountManagementUiState())
    val state: StateFlow<AccountManagementUiState> = mutableState.asStateFlow()
    private var session: UserSession? = null
    private var observeJob: Job? = null
    private var pending: PendingOperation? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        observeJob?.cancel()
        pending = null
        if (active == null || active.role == UserRole.SALESMAN) {
            mutableState.value = AccountManagementUiState(role = active?.role, content = ContentState.Empty("No account administration access."))
            return
        }
        mutableState.value = AccountManagementUiState(role = active.role)
        observeJob = viewModelScope.launch {
            repository.observe(active).collect { directory ->
                mutableState.update { current ->
                    current.copy(
                        content = if (
                            directory.accounts.isEmpty() && directory.shops.isEmpty() &&
                            active.role != UserRole.SUPER_ADMIN
                        ) {
                            ContentState.Empty("No managed accounts are available.")
                        } else ContentState.Ready(directory),
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        viewModelScope.launch {
            when (val result = repository.refresh(active)) {
                is AccountOperationResult.Success -> Unit
                is AccountOperationResult.Failure -> mutableState.update {
                    it.copy(content = ContentState.Error(result.safeMessage))
                }
            }
        }
    }

    fun create(input: CreateManagedAccount) {
        if (mutableState.value.isMutating) return
        pending = PendingOperation.Create(UUID.randomUUID().toString(), input)
        executePending()
    }

    fun createShop(input: CreateManagedShop) {
        if (mutableState.value.isMutating) return
        pending = PendingOperation.CreateShop(UUID.randomUUID().toString(), input)
        executePending()
    }

    fun deleteShop(input: DeleteManagedShop) {
        if (mutableState.value.isMutating) return
        pending = PendingOperation.DeleteShop(UUID.randomUUID().toString(), input)
        executePending()
    }

    fun administer(input: AdministerManagedAccount) {
        if (mutableState.value.isMutating) return
        pending = PendingOperation.Administer(UUID.randomUUID().toString(), input)
        executePending()
    }

    fun retry() {
        if (pending == null) refresh() else executePending()
    }

    fun clearMessage() = mutableState.update { it.copy(safeMessage = null) }

    private fun executePending() {
        val active = session ?: return
        val operation = pending ?: return
        if (mutableState.value.isMutating) return
        mutableState.update { it.copy(isMutating = true, safeMessage = null) }
        viewModelScope.launch {
            val result = when (operation) {
                is PendingOperation.CreateShop -> repository.createShop(active, operation.requestId, operation.input)
                is PendingOperation.DeleteShop -> repository.deleteShop(active, operation.requestId, operation.input)
                is PendingOperation.Create -> repository.create(active, operation.requestId, operation.input)
                is PendingOperation.Administer -> repository.administer(active, operation.requestId, operation.input)
            }
            when (result) {
                is AccountOperationResult.Success -> {
                    pending = null
                    mutableState.update { it.copy(isMutating = false, safeMessage = result.safeMessage) }
                }
                is AccountOperationResult.Failure -> {
                    // Auth refresh is already attempted inside RemoteCallExecutor. Retaining an
                    // unauthorized request would replay the rejected PIN and permanently failed
                    // idempotency key instead of allowing the operator to correct the form.
                    if (result.error?.retry != RetryDisposition.WITH_BACKOFF) pending = null
                    mutableState.update {
                        it.copy(isMutating = false, safeMessage = result.safeMessage)
                    }
                }
            }
        }
    }

    class Factory(private val repository: AccountManagementRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AccountManagementViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return AccountManagementViewModel(repository) as T
        }
    }
}
