package com.gdad.bags.ui.sale

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.NepalDateTime
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleLineDraft
import com.gdad.bags.domain.sale.SalePaymentDraft
import com.gdad.bags.domain.sale.SalePaymentMethod
import com.gdad.bags.ui.components.BusinessDateField
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.components.StatusMessage

@Composable
fun SaleCheckoutScreen(
    session: UserSession,
    state: SaleUiState,
    onRefresh: () -> Unit,
    onPost: (SaleDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    ContentStateHost(state.content, onRefresh) { products ->
        SaleForm(session, products, state.isPosting, onPost)
    }
    state.safeMessage?.let { StatusMessage(it, Modifier.padding(16.dp)) }
    state.posted?.let { sale -> SaleReceipt(session, sale, onDismiss) }
}

@Composable
private fun SaleForm(
    session: UserSession,
    products: List<CatalogProduct>,
    posting: Boolean,
    onPost: (SaleDraft) -> Unit,
) {
    val quantities = remember { mutableStateMapOf<String, String>() }
    val prices = remember(products) {
        mutableStateMapOf<String, String>().apply {
            products.forEach { product ->
                put(product.id, editableMoney(product.sellingPricePaisa))
            }
        }
    }
    var date by remember { mutableStateOf(NepalDateTime.todayIso()) }
    var discount by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(SalePaymentMethod.CASH) }
    var credit by remember { mutableStateOf(false) }
    var customer by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(NepalDateTime.todayIso()) }

    val priceInputsValid = products.all { product ->
        val quantity = quantities[product.id]?.toIntOrNull() ?: 0
        quantity <= 0 || MoneyAmounts.parsePaisa(prices[product.id].orEmpty()) != null
    }
    val lines = products.mapNotNull { product ->
        val quantity = quantities[product.id]?.toIntOrNull() ?: 0
        if (quantity <= 0) return@mapNotNull null
        SaleLineDraft(
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            effectiveUnitPricePaisa = MoneyAmounts.parsePaisa(prices[product.id].orEmpty()),
        )
    }
    val lineTotals = lines.map { line ->
        val product = products.single { it.id == line.productId }
        MoneyAmounts.multiplyPaisa(
            line.effectiveUnitPricePaisa ?: product.sellingPricePaisa,
            line.quantity,
        )
    }
    val subtotal = lineTotals.takeIf { totals -> totals.all { it != null } }
        ?.let { MoneyAmounts.sumPaisa(it.filterNotNull()) }
    val discountPaisa = if (discount.isBlank()) 0L else MoneyAmounts.parsePaisa(discount)
    val localTotal = if (subtotal != null && discountPaisa != null) {
        MoneyAmounts.subtractPaisa(subtotal, discountPaisa)
    } else {
        null
    }
    val paid = if (payment.isBlank()) 0L else MoneyAmounts.parsePaisa(payment)
    val validDates = NepalDateTime.isValidIsoDate(date) &&
        (!credit || NepalDateTime.isValidIsoDate(dueDate))
    val valid = validDates && priceInputsValid && lines.isNotEmpty() &&
        lines.all { line ->
            line.quantity <= products.single { it.id == line.productId }.quantityOnHand
        } && localTotal != null && localTotal >= 0 && paid != null && when {
        session.role == UserRole.SALESMAN -> paid == localTotal
        credit -> paid <= localTotal && customer.isNotBlank() && contact.isNotBlank()
        else -> paid == localTotal
    }

    Column(
        Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("New sale", style = MaterialTheme.typography.headlineSmall)
        BusinessDateField(date, { date = it }, Modifier.fillMaxWidth())
        products.forEach { product ->
            Text("${product.name} • ${product.quantityOnHand} available")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    quantities[product.id].orEmpty(),
                    { quantities[product.id] = it.filter(Char::isDigit) },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    prices[product.id].orEmpty(),
                    { prices[product.id] = it.filter { character -> character.isDigit() || character == '.' } },
                    label = { Text("Actual selling price") },
                    supportingText = {
                        Text("Suggested ${money(product.sellingPricePaisa)}; edit for the negotiated price")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (session.role == UserRole.OWNER) {
            OutlinedTextField(
                discount,
                { discount = it.filter { character -> character.isDigit() || character == '.' } },
                label = { Text("Sale discount") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = { credit = !credit }) {
                Text(if (credit) "Credit sale selected" else "Make credit sale")
            }
            if (credit) {
                OutlinedTextField(customer, { customer = it }, label = { Text("Customer name") })
                OutlinedTextField(
                    contact,
                    { contact = it },
                    label = { Text("Customer contact") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                )
                BusinessDateField(
                    dueDate,
                    { dueDate = it },
                    Modifier.fillMaxWidth(),
                    label = "Due date (Nepal) — YYYY-MM-DD",
                )
            }
        }
        Text("Review estimate ${money(localTotal?.coerceAtLeast(0) ?: 0)}; server total is final.")
        OutlinedTextField(
            payment,
            { payment = it.filter { character -> character.isDigit() || character == '.' } },
            label = { Text(if (credit) "Payment now" else "Full payment") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Row {
            TextButton(onClick = { method = SalePaymentMethod.CASH }) {
                Text(if (method == SalePaymentMethod.CASH) "Selected: Cash" else "Cash")
            }
            TextButton(onClick = { method = SalePaymentMethod.BANK }) {
                Text(if (method == SalePaymentMethod.BANK) "Selected: Bank" else "Bank")
            }
        }
        Button(
            enabled = valid && !posting,
            onClick = {
                onPost(
                    SaleDraft(
                        businessDate = date,
                        lines = lines,
                        saleDiscountPaisa = requireNotNull(discountPaisa),
                        isCredit = credit,
                        customerName = customer.trim().ifEmpty { null },
                        customerContact = contact.trim().ifEmpty { null },
                        dueDate = dueDate.takeIf { credit },
                        payments = requireNotNull(paid).takeIf { it > 0 }
                            ?.let { listOf(SalePaymentDraft(method, it)) }.orEmpty(),
                    ),
                )
            },
        ) {
            Text(if (posting) "Posting…" else "Confirm and post once")
        }
    }
}

@Composable
private fun SaleReceipt(session: UserSession, sale: PostedSale, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sale posted") },
        text = {
            Column {
                Text("Server-authoritative FIFO receipt")
                Text("Total ${money(sale.grandTotalPaisa)}")
                Text("Paid ${money(sale.paidPaisa)}")
                Text("Due ${money(sale.duePaisa)}")
                if (session.role == UserRole.OWNER) {
                    sale.costTotalPaisa?.let { cost ->
                        Text("FIFO cost ${money(cost)}")
                        MoneyAmounts.subtractPaisa(sale.grandTotalPaisa, cost)?.let { profit ->
                            Text("Gross profit ${money(profit)}")
                        }
                    }
                }
                Text("${sale.lineCount} line(s) • ${sale.allocationCount} FIFO allocation(s)")
                Text("Sale ${sale.saleId}")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

private fun money(paisa: Long) = MoneyAmounts.formatNpr(paisa)

private fun editableMoney(paisa: Long) =
    "${paisa / 100}.${(paisa % 100).toString().padStart(2, '0')}"
