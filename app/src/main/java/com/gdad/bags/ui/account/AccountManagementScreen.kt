package com.gdad.bags.ui.account

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.gdad.bags.BuildConfig
import com.gdad.bags.domain.account.AccountAction
import com.gdad.bags.domain.account.AccountDirectory
import com.gdad.bags.domain.account.AdministerManagedAccount
import com.gdad.bags.domain.account.CreateManagedAccount
import com.gdad.bags.domain.account.CreateManagedShop
import com.gdad.bags.domain.account.DeleteManagedShop
import com.gdad.bags.domain.account.ManagedAccount
import com.gdad.bags.domain.account.ManagedShop
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
    onDeleteShop: (DeleteManagedShop) -> Unit,
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
    var createSubmitted by remember { mutableStateOf(false) }
    var showCreateShop by remember { mutableStateOf(false) }
    var selectedShop by remember { mutableStateOf<ManagedShop?>(null) }
    var selectedAccount by remember { mutableStateOf<ManagedAccount?>(null) }
    var selectedAction by remember { mutableStateOf<AccountAction?>(null) }

    ContentStateHost(state.content, onRetry = onRefresh) { directory ->
        DirectoryContent(
            session = session,
            directory = directory,
            isMutating = state.isMutating,
            safeMessage = state.safeMessage,
            onAddShop = { showCreateShop = true },
            onDeleteShop = { selectedShop = it },
            onAddAccount = { createSubmitted = false; showCreate = true },
            onAction = { account, action -> selectedAccount = account; selectedAction = action },
        )
    }

    if (showCreateShop) {
        CreateShopDialog(
            onDismiss = { showCreateShop = false },
            onSubmit = { showCreateShop = false; onCreateShop(it) },
        )
    }

    selectedShop?.let { shop ->
        DeleteShopDialog(
            shop = shop,
            onDismiss = { selectedShop = null },
            onSubmit = {
                selectedShop = null
                onDeleteShop(it)
            },
        )
    }

    if (showCreate) {
        CreateAccountDialog(
            session = session,
            directory = (state.content as? com.gdad.bags.ui.components.ContentState.Ready)?.value
                ?: AccountDirectory(),
            isSubmitting = state.isMutating,
            submissionMessage = state.safeMessage.takeIf { createSubmitted },
            onDismiss = { if (!state.isMutating) { showCreate = false; createSubmitted = false } },
            onSubmit = { createSubmitted = true; onCreate(it) },
        )
    }
    LaunchedEffect(showCreate, createSubmitted, state.isMutating, state.safeMessage) {
        if (
            showCreate && createSubmitted && !state.isMutating &&
            state.safeMessage?.endsWith("account created and audited.") == true
        ) {
            showCreate = false
            createSubmitted = false
        }
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
    onDeleteShop: (ManagedShop) -> Unit,
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
            Text("App version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
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
                        OutlinedButton(
                            onClick = { onDeleteShop(shop) },
                            enabled = !isMutating && shop.active,
                            modifier = Modifier.testTag("shop-delete-${shop.id}"),
                        ) { Text("Delete shop") }
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
private fun DeleteShopDialog(
    shop: ManagedShop,
    onDismiss: () -> Unit,
    onSubmit: (DeleteManagedShop) -> Unit,
) {
    var confirmationSlug by remember(shop.id) { mutableStateOf("") }
    var reason by remember(shop.id) { mutableStateOf("") }
    var reauthPin by remember(shop.id) { mutableStateOf("") }
    val valid = confirmationSlug == shop.slug && reason.trim().length in 8..500 &&
        reauthPin.matches(MANAGED_PIN)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permanently delete ${shop.displayName}?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).imePadding(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "This permanently deletes this shop's products, stock, purchases, sales, " +
                        "finance history, reports, and shop-only managed accounts. It cannot be undone.",
                )
                Text("Type the exact shop slug: ${shop.slug}")
                OutlinedTextField(
                    value = confirmationSlug,
                    onValueChange = { confirmationSlug = it.take(63) },
                    modifier = Modifier.fillMaxWidth().testTag("shop-delete-confirmation"),
                    label = { Text("Exact shop slug") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    modifier = Modifier.fillMaxWidth().testTag("shop-delete-reason"),
                    label = { Text("Reason (required)") },
                    supportingText = { Text("8–500 characters; retained in the deletion audit.") },
                )
                OutlinedTextField(
                    value = reauthPin,
                    onValueChange = { reauthPin = it.filter(Char::isDigit).take(8) },
                    modifier = Modifier.fillMaxWidth().testTag("shop-delete-pin"),
                    label = { Text("Your Super Admin PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                modifier = Modifier.testTag("shop-delete-confirm"),
                onClick = {
                    onSubmit(
                        DeleteManagedShop(
                            shopId = shop.id,
                            confirmationSlug = confirmationSlug,
                            reason = reason.trim(),
                            reauthPin = reauthPin,
                        ),
                    )
                },
            ) { Text("Permanently delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
    isSubmitting: Boolean,
    submissionMessage: String?,
    onDismiss: () -> Unit,
    onSubmit: (CreateManagedAccount) -> Unit,
) {
    var loginId by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var shopId by remember { mutableStateOf(session.shopId ?: directory.shops.firstOrNull { it.active }?.id.orEmpty()) }
    val loginIdValid = loginId.matches(MANAGED_LOGIN_ID)
    val valid = loginIdValid && displayName.trim().length in 1..120 &&
        pin.matches(MANAGED_PIN) && shopId.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (session.role == UserRole.SUPER_ADMIN) "Create Owner" else "Create Salesman") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    loginId,
                    { loginId = it.trim().lowercase().take(64) },
                    modifier = Modifier.testTag("account-login-id"),
                    label = { Text("Login ID") },
                    supportingText = {
                        Text("3–64 lowercase letters, numbers, dots, underscores, or hyphens; start with a letter or number.")
                    },
                    isError = loginId.isNotEmpty() && !loginIdValid,
                    singleLine = true,
                )
                OutlinedTextField(
                    displayName,
                    { displayName = it.take(120) },
                    modifier = Modifier.testTag("account-display-name"),
                    label = { Text("Display name") },
                )
                OutlinedTextField(
                    pin,
                    { pin = it.filter(Char::isDigit).take(8) },
                    modifier = Modifier.testTag("account-new-pin"),
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
                submissionMessage?.let { message ->
                    StatusMessage(
                        message,
                        isError = !message.endsWith("account created and audited."),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                modifier = Modifier.testTag("account-create-confirm"),
                enabled = valid && !isSubmitting,
                onClick = { onSubmit(CreateManagedAccount(loginId, displayName.trim(), pin, shopId)) },
            ) { Text(if (isSubmitting) "Submitting…" else "Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !isSubmitting) { Text("Cancel") } },
    )
}

internal val MANAGED_LOGIN_ID = Regex("^[a-z0-9][a-z0-9._-]{2,63}$")
private val MANAGED_PIN = Regex("^\\d{6,8}$")

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
