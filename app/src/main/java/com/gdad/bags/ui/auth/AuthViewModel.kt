package com.gdad.bags.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.gdad.bags.domain.auth.AuthenticateUser
import com.gdad.bags.domain.auth.LoginResult
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val session: UserSession? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class AuthViewModel(
    private val authenticateUser: AuthenticateUser,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = mutableUiState.asStateFlow()

    fun login(userId: String, pin: String) {
        if (mutableUiState.value.isLoading) return
        mutableUiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            mutableUiState.value = when (val result = authenticateUser(userId, pin)) {
                is LoginResult.Success -> AuthUiState(session = result.session)
                is LoginResult.Failure -> AuthUiState(errorMessage = result.message)
            }
        }
    }

    fun clearError() {
        mutableUiState.update { it.copy(errorMessage = null) }
    }

    fun logout() {
        mutableUiState.value = AuthUiState()
    }

    class Factory(
        private val authenticateUser: AuthenticateUser,
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(AuthViewModel::class.java)) {
                "Unknown ViewModel class: ${modelClass.name}"
            }
            @Suppress("UNCHECKED_CAST")
            return AuthViewModel(authenticateUser) as T
        }
    }
}
