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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gdad.bags.data.auth.AuthRepository
import com.gdad.bags.data.auth.LoginResult
import com.gdad.bags.data.auth.PreviewAuthRepository
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import kotlinx.coroutines.launch

private val GdadColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)

@Composable
fun GdadApp() {
    MaterialTheme(colorScheme = GdadColors) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val auth = remember { PreviewAuthRepository() }
            var session by remember { mutableStateOf<UserSession?>(null) }
            if (session == null) LoginScreen(auth) { session = it }
            else Dashboard(checkNotNull(session)) { session = null }
        }
    }
}

@Composable
private fun LoginScreen(auth: AuthRepository, onLoggedIn: (UserSession) -> Unit) {
    var userId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
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
                        onValueChange = { userId = it; error = null },
                        label = { Text("User ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = pin,
                        onValueChange = { pin = it.filter(Char::isDigit).take(8); error = null },
                        label = { Text("PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = error != null,
                        supportingText = { error?.let { Text(it) } },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Button(
                        onClick = {
                            loading = true
                            scope.launch {
                                when (val result = auth.login(userId, pin)) {
                                    is LoginResult.Success -> onLoggedIn(result.session)
                                    is LoginResult.Failure -> error = result.message
                                }
                                loading = false
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (loading) "Signing in…" else "Sign in") }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("Your previous secure login will remain available offline.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Dashboard(session: UserSession, onLogout: () -> Unit) {
    val actions = actionsFor(session.role)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GDAD BAGS") },
                actions = { OutlinedButton(onClick = onLogout) { Text("Log out") } },
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
            if (session.role != UserRole.SUPER_ADMIN) {
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Summary("Today's sales", "रु 0", Modifier.weight(1f))
                        Summary(if (session.role == UserRole.OWNER) "Low stock" else "My sales", "0", Modifier.weight(1f))
                    }
                }
            }
            items(actions) { action ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp)) {
                        Text(action.first, fontWeight = FontWeight.Bold)
                        Text(action.second, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
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

private fun actionsFor(role: UserRole): List<Pair<String, String>> = when (role) {
    UserRole.SUPER_ADMIN -> listOf(
        "Owners" to "Create, disable or reset an Owner PIN",
        "Create Owner" to "Set up a new independent shop",
    )
    UserRole.OWNER -> listOf(
        "New sale" to "Walk-in or online sale",
        "Stock" to "Products, FIFO batches and movements",
        "Vendors" to "Bills, payments, dues and returns",
        "Cash & bank" to "Balances, expenses and transfers",
        "Salesmen" to "Accounts, access and PIN reset",
        "Reports" to "Sales, stock, profit and vendor reports",
        "Notifications" to "Negative stock, damage and manual stock",
    )
    UserRole.SALESMAN -> listOf(
        "New sale" to "Enter selling price and view unit cost",
        "Stock" to "View or add stock",
        "Damage or loss" to "Record an entry and notify the Owner",
        "Product return" to "Return items from an original sale",
    )
}
