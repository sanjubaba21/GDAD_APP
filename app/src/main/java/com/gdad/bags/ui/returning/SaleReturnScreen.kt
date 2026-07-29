package com.gdad.bags.ui.returning

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.NepalDateTime
import com.gdad.bags.domain.returning.RefundMethod
import com.gdad.bags.domain.returning.ReturnDisposition
import com.gdad.bags.domain.returning.ReturnLineDraft
import com.gdad.bags.domain.returning.SaleHistoryEntry
import com.gdad.bags.domain.returning.SaleHistoryLine
import com.gdad.bags.domain.returning.SaleReturnDraft
import com.gdad.bags.ui.components.BusinessDateField
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.components.StatusMessage

@Composable
fun SaleReturnScreen(
    session: UserSession,
    state: SaleReturnUiState,
    onSearch: (String) -> Unit,
    onFilter: (SaleHistoryFilter) -> Unit,
    onRefresh: () -> Unit,
    onPost: (SaleReturnDraft) -> Unit,
    onDismissPosted: () -> Unit,
) {
    var expandedSaleId by remember { mutableStateOf<String?>(null) }
    var returning by remember { mutableStateOf<SaleHistoryEntry?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            label = { Text("Search sale, customer, product or SKU") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(SaleHistoryFilter.entries) { filter ->
                TextButton(onClick = { onFilter(filter) }) {
                    Text(
                        if (state.filter == filter) "Selected: ${filter.label}" else filter.label,
                    )
                }
            }
        }
        state.safeMessage?.let { StatusMessage(it) }
        ContentStateHost(state.content, onRefresh) { history ->
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(history.sales, key = { it.id }) { sale ->
                    SaleCard(
                        session = session,
                        sale = sale,
                        expanded = expandedSaleId == sale.id,
                        isMutating = state.isMutating,
                        onToggle = {
                            expandedSaleId = if (expandedSaleId == sale.id) null else sale.id
                        },
                        onReturn = { returning = sale },
                    )
                }
            }
        }
    }

    returning?.let { sale ->
        ReturnDialog(
            sale = sale,
            isPosting = state.isMutating,
            onDismiss = { if (!state.isMutating) returning = null },
            onSubmit = {
                returning = null
                onPost(it)
            },
        )
    }

    state.posted?.let { receipt ->
        AlertDialog(
            onDismissRequest = onDismissPosted,
            title = { Text("Return posted") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Server-authoritative return receipt")
                    Text("Return value ${money(receipt.returnValuePaisa)}")
                    Text("Refund ${money(receipt.refundPaisa)}")
                    Text("Due after ${money(receipt.dueAfterPaisa)}")
                    Text("Restored quantity ${receipt.restoredQuantity}")
                    if (session.role == UserRole.OWNER) {
                        Text("Restored FIFO cost ${money(receipt.restoredCostPaisa)}")
                    }
                    Text("Sale status ${receipt.saleStatus.humanize()}")
                    Text("Return ${receipt.returnId}")
                }
            },
            confirmButton = {
                Button(onClick = onDismissPosted) { Text("Done") }
            },
        )
    }
}

@Composable
private fun SaleCard(
    session: UserSession,
    sale: SaleHistoryEntry,
    expanded: Boolean,
    isMutating: Boolean,
    onToggle: () -> Unit,
    onReturn: () -> Unit,
) {
    val returnable = sale.status in setOf("posted", "partially_returned") &&
        sale.lines.any { it.returnableQuantity > 0 }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                "${sale.businessDate} • ${sale.status.humanize()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text("Total ${money(sale.grandTotalPaisa)} • Paid ${money(sale.paidPaisa)}")
            Text("Returned ${money(sale.returnedPaisa)} • Due ${money(sale.duePaisa)}")
            if (sale.isCredit) Text("Credit sale")
            sale.customerName?.let { name ->
                Text("Customer $name${sale.customerContact?.let { " • $it" }.orEmpty()}")
            }
            TextButton(onClick = onToggle) {
                Text(if (expanded) "Hide details" else "View details")
            }
            if (expanded) {
                sale.lines.forEach { line ->
                    SaleLineDetail(session, line)
                }
                if (session.role == UserRole.OWNER && returnable) {
                    Button(onClick = onReturn, enabled = !isMutating) {
                        Text("Return items")
                    }
                }
            }
        }
    }
}

@Composable
private fun SaleLineDetail(session: UserSession, line: SaleHistoryLine) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text("${line.productName} • SKU ${line.sku}", fontWeight = FontWeight.SemiBold)
        Text(
            "Sold ${line.quantity} • Returned ${line.returnedQuantity} • " +
                "Returnable ${line.returnableQuantity}",
        )
        Text("${money(line.unitPricePaisa)} each • Line ${money(line.lineTotalPaisa)}")
        if (session.role == UserRole.OWNER && line.allocations.isNotEmpty()) {
            Text("FIFO allocations", style = MaterialTheme.typography.labelLarge)
            line.allocations.forEach { allocation ->
                Text("${allocation.quantity} @ ${money(allocation.unitCostPaisa)}")
            }
        }
    }
}

@Composable
private fun ReturnDialog(
    sale: SaleHistoryEntry,
    isPosting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (SaleReturnDraft) -> Unit,
) {
    val quantities = remember(sale.id) { mutableStateMapOf<String, String>() }
    val dispositions = remember(sale.id) {
        mutableStateMapOf<String, ReturnDisposition>()
    }
    var date by remember(sale.id) { mutableStateOf(NepalDateTime.todayIso()) }
    var reason by remember(sale.id) { mutableStateOf("") }
    var refundMethod by remember(sale.id) { mutableStateOf(RefundMethod.CASH) }
    val selectedLines = sale.lines.mapNotNull { line ->
        val quantity = quantities[line.id]?.toIntOrNull() ?: 0
        if (quantity > 0) {
            ReturnLineDraft(
                saleLineId = line.id,
                quantity = quantity,
                disposition = dispositions[line.id] ?: ReturnDisposition.SELLABLE,
            )
        } else {
            null
        }
    }
    val quantitiesValid = selectedLines.isNotEmpty() && selectedLines.all { selected ->
        sale.lines.single { it.id == selected.saleLineId }.returnableQuantity >= selected.quantity
    }
    val estimatedValues = selectedLines.map { selected ->
        sale.lines.single { it.id == selected.saleLineId }.estimatedValue(selected.quantity)
    }
    val estimatedReturn = estimatedValues.takeIf { values -> values.all { it != null } }
        ?.let { MoneyAmounts.sumPaisa(it.filterNotNull()) }
    val estimatedRefund = estimatedReturn
        ?.let { MoneyAmounts.subtractPaisa(it, sale.duePaisa) }
        ?.coerceAtLeast(0)
    val valid = quantitiesValid && estimatedReturn != null && estimatedRefund != null &&
        reason.trim().isNotEmpty() &&
        NepalDateTime.isValidIsoDate(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Return sale items") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Choose quantities from the original sale. Server totals are final.")
                sale.lines.filter { it.returnableQuantity > 0 }.forEach { line ->
                    Text("${line.productName} • ${line.returnableQuantity} returnable")
                    OutlinedTextField(
                        value = quantities[line.id].orEmpty(),
                        onValueChange = { quantities[line.id] = it.filter(Char::isDigit) },
                        label = { Text("Return quantity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        ReturnDisposition.entries.forEach { disposition ->
                            TextButton(
                                onClick = { dispositions[line.id] = disposition },
                            ) {
                                val selected = (dispositions[line.id]
                                    ?: ReturnDisposition.SELLABLE) == disposition
                                Text(
                                    (if (selected) "Selected: " else "") +
                                        disposition.name.humanize(),
                                )
                            }
                        }
                    }
                }
                BusinessDateField(
                    value = date,
                    onValueChange = { date = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it.take(500) },
                    label = { Text("Required return reason") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Estimated return ${money(estimatedReturn ?: 0)}")
                if ((estimatedRefund ?: 0) > 0) {
                    Text("Estimated refund ${money(estimatedRefund ?: 0)}")
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RefundMethod.entries.forEach { method ->
                            OutlinedButton(onClick = { refundMethod = method }) {
                                Text(
                                    (if (refundMethod == method) "Selected: " else "") +
                                        method.name.humanize(),
                                )
                            }
                        }
                    }
                } else {
                    Text("No cash/bank refund expected; the return reduces outstanding due.")
                }
            }
        },
        confirmButton = {
            Button(
                enabled = valid && !isPosting,
                onClick = {
                    onSubmit(
                        SaleReturnDraft(
                            saleId = sale.id,
                            businessDate = date,
                            reason = reason.trim(),
                            lines = selectedLines,
                            refundMethod = refundMethod.takeIf { (estimatedRefund ?: 0) > 0 },
                        ),
                    )
                },
            ) {
                Text(if (isPosting) "Posting…" else "Post return once")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isPosting) { Text("Cancel") }
        },
    )
}

private fun SaleHistoryLine.estimatedValue(returnQuantity: Int): Long? {
    val remainingValue = (lineTotalPaisa - returnedValuePaisa).coerceAtLeast(0)
    if (returnQuantity >= returnableQuantity) return remainingValue
    return MoneyAmounts.proratePaisa(lineTotalPaisa, returnQuantity, quantity)
}

private fun String.humanize() = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
private fun money(paisa: Long) = MoneyAmounts.formatNpr(paisa)
