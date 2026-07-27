package com.gdad.bags

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdad.bags.ui.GdadApp
import com.gdad.bags.ui.auth.AuthViewModel

class MainActivity : ComponentActivity() {
    private val authViewModel: AuthViewModel by viewModels {
        val container = (application as GdadApplication).appContainer
        AuthViewModel.Factory(
            authenticateUser = container.authenticateUser,
            restoreSession = container.restoreSession,
            logoutUser = container.logoutUser,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val authUiState by authViewModel.uiState.collectAsStateWithLifecycle()
            GdadApp(
                authUiState = authUiState,
                onLogin = authViewModel::login,
                onInputChanged = authViewModel::clearError,
                onLogout = authViewModel::logout,
            )
        }
    }
}
