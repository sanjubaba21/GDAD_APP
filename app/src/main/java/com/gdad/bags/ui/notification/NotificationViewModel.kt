package com.gdad.bags.ui.notification

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.NotificationCenter
import com.gdad.bags.domain.notification.NotificationRepository
import com.gdad.bags.domain.notification.NotificationResult
import com.gdad.bags.ui.components.ContentState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationUiState(
    val content: ContentState<NotificationCenter> = ContentState.Loading,
    val isRefreshing: Boolean = false,
    val selectedId: String? = null,
    val category: String? = null,
    val safeMessage: String? = null,
)

class NotificationViewModel(private val repository: NotificationRepository) : ViewModel() {
    private val mutable = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = mutable.asStateFlow()
    private var session: UserSession? = null
    private var observeJob: Job? = null

    fun activate(active: UserSession?) {
        if (session == active) return
        session = active
        observeJob?.cancel()
        if (active == null) {
            mutable.value = NotificationUiState(
                content = ContentState.Empty("Sign in to view notifications."),
            )
            return
        }
        mutable.value = NotificationUiState()
        observeJob = viewModelScope.launch {
            repository.observe(active).collect { center ->
                mutable.update { current ->
                    current.copy(
                        content = if (center.items.isEmpty()) {
                            ContentState.Empty("No current notifications.")
                        } else {
                            ContentState.Ready(center)
                        },
                        selectedId = current.selectedId?.takeIf { id ->
                            center.items.any { it.id == id }
                        },
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        val active = session ?: return
        if (mutable.value.isRefreshing) return
        mutable.update { it.copy(isRefreshing = true, safeMessage = null) }
        viewModelScope.launch {
            when (val result = repository.refresh(active)) {
                is NotificationResult.Success -> mutable.update {
                    it.copy(isRefreshing = false, safeMessage = result.safeMessage)
                }
                is NotificationResult.Failure -> mutable.update {
                    it.copy(
                        content = if (it.content is ContentState.Ready) {
                            it.content
                        } else {
                            ContentState.Error(result.safeMessage)
                        },
                        isRefreshing = false,
                        safeMessage = result.safeMessage,
                    )
                }
            }
        }
    }

    fun select(notificationId: String) {
        val center = (mutable.value.content as? ContentState.Ready)?.value ?: return
        val notification = center.items.firstOrNull { it.id == notificationId } ?: return
        mutable.update { it.copy(selectedId = notificationId, safeMessage = null) }
        if (notification.isRead) return
        val active = session ?: return
        viewModelScope.launch {
            when (val result = repository.markRead(active, notificationId)) {
                is NotificationResult.Success -> Unit
                is NotificationResult.Failure -> mutable.update {
                    it.copy(safeMessage = result.safeMessage)
                }
            }
        }
    }

    fun closeDetail() = mutable.update { it.copy(selectedId = null) }
    fun setCategory(category: String?) = mutable.update { it.copy(category = category) }

    class Factory(private val repository: NotificationRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(NotificationViewModel::class.java))
            @Suppress("UNCHECKED_CAST")
            return NotificationViewModel(repository) as T
        }
    }
}
