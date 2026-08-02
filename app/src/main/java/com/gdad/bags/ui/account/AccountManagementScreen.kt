package com.gdad.bags.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.components.StatusMessage

@Composable
fun AccountManagementScreen(
    session: UserSession,
    state: AccountManagementUiState,
    onRefresh: () -> Unit,
    onCreateShop: (CreateManagedShop) -> Unit,
    onCreate: (CreateManagedAccount) -> Unit,
    onAdminister: (AdministerManagedAccount) -> Unit,
) {
    if (session.role == UserRole.SALESMAN) {
        ContentStateHost<Unit>(
            state = com.gdad.bags.ui.components.ContentState.Error("You are not allowed to manage accounts."),
            onRetry = {},
        ) { }
        return
    }
    var showCreate by remember { mutableStateOf(false) }
    var showCreateShop by remember { mutableStateOf(false) }
    var selectedAccount by remember { mutableStateOf<ManagedAccount?>(null) }
    var selectedAction by remember { mutableStateOf<AccountAction?>(null) }

    ContentStateHost(state.content, onRetry = onRefresh) { directory ->
        DirectoryContent(
            session = session,
            directory = directory,
            isMutating = state.isMutating,
            safeMessage = state.safeMessage,
            onAddShop = { showCreateShop = true },
            onAddAccount = { showCreate = true },
            onAction = { account, action -> selectedAccount = account; selectedAction = action },
        )
    }

    if (showCreateShop) {
        CreateShopDialog(
            onDismiss = { showCreateShop = false },
            onSubmit = { showCreateShop = false; onCreateShop(it) },
        )
    }

    if (showCreate) {
        CreateAccountDialog(
            session = session,
            directory = (state.content as? com.gdad.bags.ui.components.ContentState.Ready)?.value
                ?: AccountDirectory(),
            onDismiss = { showCreate = false },
            onSubmit = { showCreate = false; onCreate(it) },
        )
    }
    val account = selectedAccount
    val action = selectedAction
    if (account != null && action != null) {
        AdministerAccountDialog(
            account = account,
            action = action,
            onDismiss = { selectedAccount = null; selectedAction = null },
            onSubmit = {
                selectedAccount = null
                selectedAction = null
                onAdminister(it)
            },
        )
    }
}

@Composable
private fun DirectoryContent(
    session: UserSession,
    directory: AccountDirectory,
    isMutating: Boolean,
    safeMessage: String?,
    onAddShop: () -> Unit,
    onAddAccount: () -> Unit,
    onAction: (ManagedAccount, AccountAction) -> Unit,
) {
    val visibleAccounts = remember(directory.accounts, session.role, session.shopId) {
        directory.accounts.filter { account ->
            when (session.role) {
                UserRole.SUPER_ADMIN -> account.role == UserRole.OWNER
                UserRole.OWNER -> account.role == UserRole.SALESMAN && account.shopId == session.shopId
                UserRole.SALESMAN -> false
            }
        }
    }
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                if (session.role == UserRole.SUPER_ADMIN) "Owners and shops" else "Salesmen",
                style = MaterialTheme.typography.headlineSmall,
            )
            Text("Changes use protected server operations and immutable audit records.")
            safeMessage?.let { StatusMessage(it) }
            if (session.role == UserRole.SUPER_ADMIN) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAddShop, enabled = !isMutating) { Text("Create Shop") }
                    Button(
                        onClick = onAddAccount,
                        enabled = !isMutating && directory.shops.any { it.active },
                    ) { Text("Create Owner") }
                }
                if (directory.shops.none { it.active }) {
                    Text("Create an active shop before creating an Owner.")
                }
            } else {
                Button(onClick = onAddAccount, enabled = !isMutating) { Text("Create Salesman") }
            }
        }
        if (session.role == UserRole.SUPER_ADMIN) {
            items(directory.shops, key = { "shop-${it.id}" }) { shop ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(shop.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(shop.slug)
                        Text(if (shop.active) "Active shop" else "Archived shop")
                    }
                }
            }
        }
        items(visibleAccounts, key = { it.userId }) { account ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                    Text("${account.loginId} • ${account.role.name.lowercase()}")
                    Text(if (account.disabled) "Disabled" else "Active")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                onAction(account, if (account.disabled) AccountAction.ENABLE else AccountAction.DISABLE)
                            },
                            enabled = !isMutating,
                        ) { Text(if (account.disabled) "Re-enable" else "Disable") }
                        OutlinedButton(
                            onClick = { onAction(account, AccountAction.RESET_PIN) },
                            enabled = !isMutating,
                        ) { Text("Reset PIN") }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateShopDialog(
    onDismiss: () -> Unit,
    onSubmit: (CreateManagedShop) -> Unit,
) {
    var slug by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    val valid = slug.matches(Regex("^[a-z0-9][a-z0-9-]{2,62}$")) &&
        displayName.trim().length in 1..120
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Shop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("The shop and its protected financial system accounts are created atomically.")
                OutlinedTextField(
                    slug,
                    { value -> slug = value.lowercase().filter { it.isLetterOrDigit() || it == '-' }.take(63) },
                    label = { Text("Shop slug") },
                )
                OutlinedTextField(
                    displayName,
                    { displayName = it.take(120) },
                    label = { Text("Shop display name") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSubmit(CreateManagedShop(slug, displayName.trim())) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun CreateAccountDialog(
    session: UserSession,
    directory: AccountDirectory,
    onDismiss: () -> Unit,
    onSubmit: (CreateManagedAccount) -> Unit,
) {
    var loginId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var shopId by remember { mutableStateOf(session.shopId ?: directory.shops.firstOrNull { it.active }?.id.orEmpty()) }
    val valid = loginId.isNotBlank() && displayName.isNotBlank() && pin.length in 6..8 && shopId.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session.role == UserRole.SUPER_ADMIN) "Create Owner" else "Create Salesman") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(loginId, { loginId = it.trim().lowercase() }, label = { Text("Login ID") })
                OutlinedTextField(displayName, { displayName = it }, label = { Text("Display name") })
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(8) },
                    label = { Text("New PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (session.role == UserRole.SUPER_ADMIN) {
                    Text("Target shop")
                    directory.shops.filter { it.active }.forEach { shop ->
                        TextButton(onClick = { shopId = shop.id }) {
                            Text((if (shopId == shop.id) "Selected: " else "") + shop.displayName)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = { onSubmit(CreateManagedAccount(loginId, displayName.trim(), pin, shopId)) },
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AdministerAccountDialog(
    account: ManagedAccount,
    action: AccountAction,
    onDismiss: () -> Unit,
    onSubmit: (AdministerManagedAccount) -> Unit,
) {
    var reauthPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    val valid = reauthPin.length in 6..8 && (action != AccountAction.RESET_PIN || newPin.length in 6..8)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${action.name.lowercase().replace('_', ' ')} ${account.displayName}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Confirm with your own PIN. The action is audited and revokes refresh sessions when required.")
                OutlinedTextField(
                    reauthPin,
                    { reauthPin = it.filter(Char::isDigit).take(8) },
                    label = { Text("Your PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                )
                if (action == AccountAction.RESET_PIN) {
                    OutlinedTextField(
                        newPin,
                        { newPin = it.filter(Char::isDigit).take(8) },
                        label = { Text("New user PIN") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSubmit(
                        AdministerManagedAccount(
                            account.userId,
                            action,
                            reauthPin,
                            newPin.takeIf { action == AccountAction.RESET_PIN },
                        ),
                    )
                },
            ) { Text("Confirm") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
