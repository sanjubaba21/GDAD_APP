package com.gdad.bags

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.local.OutboxState
import com.gdad.bags.ui.GdadApp
import com.gdad.bags.ui.OutboxResolutionNotice
import com.gdad.bags.ui.auth.AuthViewModel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

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
            val session = authUiState.session
            val container = (application as GdadApplication).appContainer
            val noticesFlow = remember(session) {
                session?.let { active ->
                    container.mutationOutbox.observe(CacheOwner(active.userId, active.shopId)).map { rows ->
                        rows.filter { it.state == OutboxState.PERMANENT_FAILURE.name }.map { row ->
                            OutboxResolutionNotice(row.operation, row.lastErrorKind ?: "UNKNOWN")
                        }
                    }
                } ?: flowOf(emptyList())
            }
            val outboxNotices by noticesFlow.collectAsStateWithLifecycle(emptyList())
            GdadApp(
                authUiState = authUiState,
                outboxNotices = outboxNotices,
                onLogin = authViewModel::login,
                onInputChanged = authViewModel::clearError,
                onLogout = authViewModel::logout,
            )
        }
    }
}
