package com.gdad.bags.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.auth.LogoutUser
import com.gdad.bags.domain.auth.RestoreSession
import com.gdad.bags.domain.auth.SessionRestoreResult
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: UserSession? = null,
    val isInitializing: Boolean = true,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    private val authenticateUser: AuthenticateUser,
    private val restoreSession: RestoreSession,
    private val logoutUser: LogoutUser,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch {
            mutableUiState.value = when (val result = restoreSession()) {
                is SessionRestoreResult.Authenticated -> AuthUiState(
                    session = result.session,
                    isInitializing = false,
                )
                is SessionRestoreResult.SignedOut -> AuthUiState(
                    isInitializing = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun login(userId: String, pin: String) {
        if (mutableUiState.value.isLoading) return
        if (mutableUiState.value.isInitializing) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            mutableUiState.value = when (val result = authenticateUser(userId, pin)) {
                is LoginResult.Success -> AuthUiState(
                    session = result.session,
                    isInitializing = false,
                )
                is LoginResult.Failure -> AuthUiState(
                    isInitializing = false,
                    errorMessage = result.message,
                )
            }
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    fun logout() {
        if (mutableUiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            logoutUser()
            mutableUiState.value = AuthUiState(isInitializing = false)
        }
    }

    class Factory(
        private val authenticateUser: AuthenticateUser,
        private val restoreSession: RestoreSession,
        private val logoutUser: LogoutUser,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authenticateUser, restoreSession, logoutUser) as T
        }
    }
}
