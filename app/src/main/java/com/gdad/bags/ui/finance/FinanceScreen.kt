package com.gdad.bags.ui.finance

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.finance.*
import com.gdad.bags.domain.model.*
import com.gdad.bags.ui.components.ContentState
import com.gdad.bags.ui.components.ContentStateHost
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Composable
fun FinanceScreen(
    session: UserSession,
    state: FinanceUiState,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onExpense: (ExpenseDraft) -> Unit,
    onMovement: (CashMovementDraft) -> Unit,
    onTransfer: (TransferDraft) -> Unit,
    onReverse: (FinancialReversalDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    if (session.role != UserRole.OWNER) {
        ContentStateHost<Unit>(
            state = ContentState.Error("Owner finance access is required."),
            onRetry = {},
        ) {}
        return
    }

    var action by remember { mutableStateOf<String?>(null) }
    var reversing by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.safeMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (state.canRetry) {
            OutlinedButton(onClick = onRetry, enabled = !state.isMutating) {
                Text("Retry same operation")
            }
        }
        if (state.isMutating) LinearProgressIndicator(Modifier.fillMaxWidth())
        Box(Modifier.weight(1f)) {
            ContentStateHost(state.content, onRefresh) { ledger ->
                Column(Modifier.fillMaxSize()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item {
                            Button(
                                onClick = { action = "expense" },
                                enabled = !state.isMutating && ledger.accounts.any { it.active },
                            ) { Text("New expense") }
                        }
                        item {
                            OutlinedButton(
                                onClick = { action = "movement" },
                                enabled = !state.isMutating && ledger.accounts.any { it.active },
                            ) { Text("Deposit / withdraw") }
                        }
                        item {
                            OutlinedButton(
                                onClick = { action = "transfer" },
                                enabled = !state.isMutating && ledger.accounts.count { it.active } >= 2,
                            ) { Text("Transfer") }
                        }
                    }
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Text("Cash and bank", style = MaterialTheme.typography.headlineSmall) }
                        items(ledger.accounts, key = { "account-${it.id}" }) { account ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(account.name, fontWeight = FontWeight.Bold)
                                    Text("${account.type.human()} - ${money(account.balancePaisa)}")
                                    if (!account.active) Text("Inactive")
                                }
                            }
                        }
                        item {
                            Text("Transaction history", style = MaterialTheme.typography.headlineSmall)
                        }
                        items(ledger.transactions, key = { "tx-${it.id}" }) { transaction ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(transaction.kind.human(), fontWeight = FontWeight.Bold)
                                    Text("${transaction.businessDate} - ${transaction.description}")
                                    transaction.effects.forEach { effect ->
                                        ledger.accounts.firstOrNull { it.id == effect.accountId }?.let { account ->
                                            Text(
                                                "${account.name}: debit ${money(effect.debitPaisa)}, " +
                                                    "credit ${money(effect.creditPaisa)}",
                                            )
                                        }
                                    }
                                    if (transaction.reversalOfId != null) {
                                        Text("Reverses ${transaction.reversalOfId}")
                                    }
                                    if (transaction.reversed) Text("Reversed")
                                    if (!transaction.reversed && transaction.kind in REVERSIBLE_KINDS) {
                                        TextButton(onClick = { reversing = transaction.id }) {
                                            Text("Reverse transaction")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        when (action) {
            "expense" -> ExpenseDialog(state.readyAccounts(), { action = null }) {
                action = null
                onExpense(it)
            }
            "movement" -> MovementDialog(state.readyAccounts(), { action = null }) {
                action = null
                onMovement(it)
            }
            "transfer" -> TransferDialog(state.readyAccounts(), { action = null }) {
                action = null
                onTransfer(it)
            }
        }
        reversing?.let { journalId ->
            ReversalDialog(journalId, { reversing = null }) {
                reversing = null
                onReverse(it)
            }
        }
    }

    state.receipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Finance operation posted") },
            text = {
                Column {
                    Text("Server-authoritative result")
                    when (receipt) {
                        is FinanceReceipt.Expense -> {
                            Text("Expense ${money(receipt.value.amountPaisa)}")
                            Text("Source balance ${money(receipt.value.sourceBalanceAfterPaisa)}")
                        }
                        is FinanceReceipt.Movement -> {
                            Text("${receipt.value.type.human()} ${money(receipt.value.amountPaisa)}")
                            Text("Account balance ${money(receipt.value.accountBalanceAfterPaisa)}")
                        }
                        is FinanceReceipt.Transfer -> {
                            Text("Transfer ${money(receipt.value.amountPaisa)}")
                            Text(
                                "From ${money(receipt.value.fromBalanceAfterPaisa)} - " +
                                    "To ${money(receipt.value.toBalanceAfterPaisa)}",
                            )
                        }
                        is FinanceReceipt.Reversal -> {
                            Text("${receipt.value.originalKind.human()} reversed")
                            Text("Reversal journal ${receipt.value.reversalJournalId}")
                        }
                    }
                }
            },
            confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
        )
    }
}

@Composable
private fun MoneyFields(
    accounts: List<FinanceAccount>,
    selected: String,
    onSelect: (String) -> Unit,
    amount: String,
    onAmount: (String) -> Unit,
    date: String,
    onDate: (String) -> Unit,
) {
    accounts.filter { it.active }.forEach { account ->
        TextButton(onClick = { onSelect(account.id) }) {
            Text(
                (if (selected == account.id) "Selected: " else "") +
                    "${account.name} ${money(account.balancePaisa)}",
            )
        }
    }
    OutlinedTextField(
        value = amount,
        onValueChange = { onAmount(it.filterAmountInput()) },
        label = { Text("Amount") },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
    OutlinedTextField(
        value = date,
        onValueChange = onDate,
        label = { Text("Business date YYYY-MM-DD") },
    )
}

@Composable
private fun ExpenseDialog(
    accounts: List<FinanceAccount>,
    dismiss: () -> Unit,
    submit: (ExpenseDraft) -> Unit,
) {
    var id by remember { mutableStateOf(accounts.firstOrNull { it.active }?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val paisa = amount.toPaisaOrNull()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("New expense") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                MoneyFields(accounts, id, { id = it }, amount, { amount = it }, date, { date = it })
                OutlinedTextField(category, { category = it }, label = { Text("Category") })
                OutlinedTextField(payee, { payee = it }, label = { Text("Payee (optional)") })
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") })
            }
        },
        confirmButton = {
            Button(
                enabled = paisa != null && category.isNotBlank() && id.isNotBlank(),
                onClick = {
                    submit(
                        ExpenseDraft(
                            id,
                            requireNotNull(paisa),
                            date,
                            category.trim(),
                            payee.clean(),
                            note.clean(),
                        ),
                    )
                },
            ) { Text("Post expense once") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MovementDialog(
    accounts: List<FinanceAccount>,
    dismiss: () -> Unit,
    submit: (CashMovementDraft) -> Unit,
) {
    var id by remember { mutableStateOf(accounts.firstOrNull { it.active }?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var description by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(CashMovementType.DEPOSIT) }
    val paisa = amount.toPaisaOrNull()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Deposit or withdrawal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Row {
                    CashMovementType.entries.forEach { value ->
                        TextButton(onClick = { type = value }) {
                            Text((if (type == value) "Selected: " else "") + value.name.human())
                        }
                    }
                }
                MoneyFields(accounts, id, { id = it }, amount, { amount = it }, date, { date = it })
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Required description") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = paisa != null && description.isNotBlank() && id.isNotBlank(),
                onClick = {
                    submit(CashMovementDraft(type, id, requireNotNull(paisa), date, description.trim()))
                },
            ) { Text("Post movement once") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun TransferDialog(
    accounts: List<FinanceAccount>,
    dismiss: () -> Unit,
    submit: (TransferDraft) -> Unit,
) {
    val activeAccounts = accounts.filter { it.active }
    var from by remember { mutableStateOf(activeAccounts.firstOrNull()?.id.orEmpty()) }
    var to by remember { mutableStateOf(activeAccounts.drop(1).firstOrNull()?.id.orEmpty()) }
    var amount by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var description by remember { mutableStateOf("") }
    val paisa = amount.toPaisaOrNull()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Transfer accounts") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("From")
                activeAccounts.forEach { account ->
                    TextButton(onClick = { from = account.id }) {
                        Text((if (from == account.id) "Selected: " else "") + account.name)
                    }
                }
                Text("To")
                activeAccounts.forEach { account ->
                    TextButton(onClick = { to = account.id }) {
                        Text((if (to == account.id) "Selected: " else "") + account.name)
                    }
                }
                OutlinedTextField(
                    amount,
                    { amount = it.filterAmountInput() },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(date, { date = it }, label = { Text("Business date YYYY-MM-DD") })
                OutlinedTextField(
                    description,
                    { description = it },
                    label = { Text("Required description") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = paisa != null && from != to && to.isNotBlank() && description.isNotBlank(),
                onClick = {
                    submit(TransferDraft(from, to, requireNotNull(paisa), date, description.trim()))
                },
            ) { Text("Transfer once") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ReversalDialog(
    journalId: String,
    dismiss: () -> Unit,
    submit: (FinancialReversalDraft) -> Unit,
) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("Reverse transaction") },
        text = {
            Column {
                Text("The original remains immutable; a compensating journal will be posted.")
                OutlinedTextField(date, { date = it }, label = { Text("Business date YYYY-MM-DD") })
                OutlinedTextField(reason, { reason = it }, label = { Text("Required reason") })
            }
        },
        confirmButton = {
            Button(
                enabled = reason.isNotBlank(),
                onClick = { submit(FinancialReversalDraft(journalId, date, reason.trim())) },
            ) { Text("Reverse once") }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

private fun FinanceUiState.readyAccounts(): List<FinanceAccount> =
    (content as? ContentState.Ready)?.value?.accounts.orEmpty()

private fun String.clean(): String? = trim().ifEmpty { null }

private fun String.human(): String =
    lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)

private fun String.filterAmountInput(): String {
    var decimalSeen = false
    return filter { character ->
        when {
            character.isDigit() -> true
            character == '.' && !decimalSeen -> {
                decimalSeen = true
                true
            }
            else -> false
        }
    }
}

private fun String.toPaisaOrNull(): Long? = runCatching {
    BigDecimal(trim())
        .setScale(2, RoundingMode.UNNECESSARY)
        .movePointRight(2)
        .longValueExact()
        .takeIf { it > 0 }
}.getOrNull()

private fun money(paisa: Long): String =
    "Rs ${BigDecimal.valueOf(paisa, 2).setScale(2).toPlainString()}"

private val REVERSIBLE_KINDS = setOf("expense", "deposit", "withdrawal", "transfer")
