package com.gdad.bags.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.ui.auth.AuthUiState
import com.gdad.bags.ui.account.AccountManagementScreen
import com.gdad.bags.ui.account.AccountManagementUiState
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.ui.components.ConfirmationDialog
import com.gdad.bags.ui.components.ContentState
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.navigation.DashboardRoute
import com.gdad.bags.ui.navigation.FeatureDestination
import com.gdad.bags.ui.navigation.FeatureRoute
import com.gdad.bags.ui.navigation.NavigationPolicy
import com.gdad.bags.ui.navigation.title
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute

private val GdadColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)

@Composable
fun GdadApp(
    authUiState: AuthUiState,
    outboxNotices: List<OutboxResolutionNotice> = emptyList(),
    accountUiState: AccountManagementUiState = AccountManagementUiState(),
    onRefreshAccounts: () -> Unit = {},
    onCreateAccount: (CreateManagedAccount) -> Unit = {},
    onAdministerAccount: (AdministerManagedAccount) -> Unit = {},
    onLogin: (String, String) -> Unit,
    onInputChanged: () -> Unit,
    onLogout: () -> Unit,
) {
    MaterialTheme(colorScheme = GdadColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val session = authUiState.session
            if (authUiState.isInitializing) {
                AuthenticationLoadingScreen()
            } else if (session == null) {
                LoginScreen(
                    isLoading = authUiState.isLoading,
                    errorMessage = authUiState.errorMessage,
                    onLogin = onLogin,
                    onInputChanged = onInputChanged,
                )
            } else {
                key(session.userId, session.role, session.shopId) {
                    AuthenticatedApp(
                        session,
                        authUiState.isLoading,
                        outboxNotices,
                        accountUiState,
                        onRefreshAccounts,
                        onCreateAccount,
                        onAdministerAccount,
                        onLogout,
                    )
                }
            }
        }
    }
}

data class OutboxResolutionNotice(val operation: String, val errorKind: String)

@Composable
private fun AuthenticatedApp(
    session: UserSession,
    isLoggingOut: Boolean,
    outboxNotices: List<OutboxResolutionNotice>,
    accountUiState: AccountManagementUiState,
    onRefreshAccounts: () -> Unit,
    onCreateAccount: (CreateManagedAccount) -> Unit,
    onAdministerAccount: (AdministerManagedAccount) -> Unit,
    onLogout: () -> Unit,
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = DashboardRoute) {
        composable<DashboardRoute> {
            Dashboard(
                session = session,
                isLoggingOut = isLoggingOut,
                outboxNotices = outboxNotices,
                onNavigate = { destination ->
                    if (NavigationPolicy.canOpen(session.role, destination)) {
                        navController.navigate(FeatureRoute(destination)) { launchSingleTop = true }
                    }
                },
                onLogout = onLogout,
            )
        }
        composable<FeatureRoute> { entry ->
            val route = entry.toRoute<FeatureRoute>()
            if (NavigationPolicy.canOpen(session.role, route.destination)) {
                if (route.destination == FeatureDestination.ACCOUNTS) {
                    AccountFeature(
                        session,
                        accountUiState,
                        onRefreshAccounts,
                        onCreateAccount,
                        onAdministerAccount,
                        navController::popBackStack,
                    )
                } else {
                    FeaturePlaceholder(route.destination, navController::popBackStack)
                }
            } else {
                LaunchedEffect(route.destination) {
                    navController.popBackStack<DashboardRoute>(inclusive = false)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountFeature(
    session: UserSession,
    state: AccountManagementUiState,
    onRefresh: () -> Unit,
    onCreate: (CreateManagedAccount) -> Unit,
    onAdminister: (AdministerManagedAccount) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(FeatureDestination.ACCOUNTS.title()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            AccountManagementScreen(session, state, onRefresh, onCreate, onAdminister)
        }
    }
}

@Composable
private fun AuthenticationLoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text("Checking your secure session…")
    }
}

@Composable
private fun LoginScreen(
    isLoading: Boolean,
    errorMessage: String?,
    onLogin: (String, String) -> Unit,
    onInputChanged: () -> Unit,
) {
    var userId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    Scaffold { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("GDAD BAGS", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Sales, stock and vendor management")
            Spacer(Modifier.height(32.dp))
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Sign in", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = userId,
                        onValueChange = { userId = it; onInputChanged() },
                        label = { Text("User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = {
                            pin = it.filter(Char::isDigit).take(8)
                            onInputChanged()
                        },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = errorMessage != null,
                        supportingText = { errorMessage?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = { onLogin(userId, pin) },
                        enabled = !isLoading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (isLoading) "Signing in…" else "Sign in") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Your previous secure login will remain available offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(
    session: UserSession,
    isLoggingOut: Boolean,
    outboxNotices: List<OutboxResolutionNotice>,
    onNavigate: (FeatureDestination) -> Unit,
    onLogout: () -> Unit,
) {
    val actions = NavigationPolicy.visibleItems(session.role)
    var confirmLogout by remember { mutableStateOf(false) }
    if (confirmLogout) {
        ConfirmationDialog(
            title = "Log out?",
            message = "Offline data for this account will be removed from this device.",
            confirmLabel = "Log out",
            onConfirm = { confirmLogout = false; onLogout() },
            onDismiss = { confirmLogout = false },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GDAD BAGS") },
                actions = {
                    OutlinedButton(onClick = { confirmLogout = true }, enabled = !isLoggingOut) {
                        Text(if (isLoggingOut) "Logging out…" else "Log out")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Namaste, " + session.displayName, style = MaterialTheme.typography.headlineSmall)
                Text(roleLabel(session.role), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (outboxNotices.isNotEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                "Offline change needs attention",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "${outboxNotices.size} saved change(s) could not be completed. " +
                                    "Open the related feature and review the values before trying again.",
                            )
                        }
                    }
                }
            }
            if (session.role != UserRole.SUPER_ADMIN) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Summary("Today's sales", "रु 0", Modifier.weight(1f))
                        Summary(if (session.role == UserRole.OWNER) "Low stock" else "My sales", "0", Modifier.weight(1f))
                    }
                }
            }
            items(actions) { action ->
                Card(
                    onClick = { onNavigate(action.destination) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text(action.title, fontWeight = FontWeight.Bold)
                        Text(action.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturePlaceholder(destination: FeatureDestination, onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(destination.title()) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            ContentStateHost<Unit>(
                state = ContentState.Empty("No records are available yet."),
                onRetry = {},
            ) { }
        }
    }
}

@Composable
private fun Summary(label: String, value: String, modifier: Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

private fun roleLabel(role: UserRole) = when (role) {
    UserRole.SUPER_ADMIN -> "Super Admin"
    UserRole.OWNER -> "Owner dashboard • Nepal time"
    UserRole.SALESMAN -> "Sales dashboard • Nepal time"
}
