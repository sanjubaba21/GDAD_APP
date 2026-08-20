package com.gdad.bags.ui.purchase

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.NepalDateTime
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseLineDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.components.BusinessDateField
import com.gdad.bags.ui.components.StatusMessage

@Composable
fun PurchaseManagementScreen(
    session: UserSession,
    state: PurchaseManagementUiState,
    onRefresh: () -> Unit,
    onManageVendor: (VendorMutation, VendorDraft) -> Unit,
    onPostPurchase: (PurchaseDraft) -> Unit,
    onDismissReceipt: () -> Unit,
) {
    if (session.role != UserRole.OWNER) {
        ContentStateHost<Unit>(com.gdad.bags.ui.components.ContentState.Error("Owner purchasing access is required."), {}) { }
        return
    }
    var vendorDialog by remember { mutableStateOf<Vendor?>(null) }
    var createVendor by remember { mutableStateOf(false) }
    var archiveVendor by remember { mutableStateOf<Vendor?>(null) }
    var purchaseDialog by remember { mutableStateOf(false) }
    ContentStateHost(state.content, onRefresh) { workspace ->
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { purchaseDialog = true }, enabled = !state.isMutating && workspace.directory.vendors.any { it.active } && workspace.products.any { it.active }) { Text("New purchase") }
                OutlinedButton(onClick = { createVendor = true }, enabled = !state.isMutating) { Text("Create vendor") }
            }
            state.safeMessage?.let { StatusMessage(it) }
            Text("Vendors", style = MaterialTheme.typography.headlineSmall)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(workspace.directory.vendors, key = { it.id }) { vendor ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(vendor.name, style = MaterialTheme.typography.titleMedium)
                            vendor.phone?.let { Text(it) }
                            vendor.taxReference?.let { Text("Tax/PAN $it") }
                            Text("Due ${money(vendor.duePaisa)}")
                            if (!vendor.active) Text("Archived — historical use only", color = MaterialTheme.colorScheme.error)
                            if (vendor.active) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = { vendorDialog = vendor }) { Text("Edit") }
                                TextButton(onClick = { archiveVendor = vendor }) { Text("Archive") }
                            }
                        }
                    }
                }
            }
        }
        if (purchaseDialog) PurchaseDialog(workspace, { purchaseDialog = false }) { purchaseDialog = false; onPostPurchase(it) }
    }
    if (createVendor || vendorDialog != null) VendorDialog(vendorDialog, { createVendor = false; vendorDialog = null }) {
        val action = if (vendorDialog == null) VendorMutation.CREATE else VendorMutation.UPDATE
        createVendor = false; vendorDialog = null; onManageVendor(action, it)
    }
    archiveVendor?.let { vendor ->
        AlertDialog(
            onDismissRequest = { archiveVendor = null },
            title = { Text("Archive ${vendor.name}?") },
            text = { Text("History remains visible, but this vendor cannot be selected for new purchases.") },
            confirmButton = { Button(onClick = { archiveVendor = null; onManageVendor(VendorMutation.ARCHIVE, vendor.draft()) }) { Text("Archive") } },
            dismissButton = { TextButton(onClick = { archiveVendor = null }) { Text("Cancel") } },
        )
    }
    state.postedPurchase?.let { receipt ->
        AlertDialog(
            onDismissRequest = onDismissReceipt,
            title = { Text("Purchase posted") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Server-authoritative totals")
                Text("Total ${money(receipt.grandTotalPaisa)}")
                Text("Paid ${money(receipt.paidPaisa)}")
                Text("Due ${money(receipt.duePaisa)}")
                Text("${receipt.lineCount} FIFO lot(s) created")
                Text("Receipt ${receipt.purchaseReceiptId}")
            } },
            confirmButton = { Button(onClick = onDismissReceipt) { Text("Done") } },
        )
    }
}

@Composable private fun VendorDialog(vendor: Vendor?, onDismiss: () -> Unit, onSubmit: (VendorDraft) -> Unit) {
    var name by remember(vendor) { mutableStateOf(vendor?.name.orEmpty()) }
    var phone by remember(vendor) { mutableStateOf(vendor?.phone.orEmpty()) }
    var tax by remember(vendor) { mutableStateOf(vendor?.taxReference.orEmpty()) }
    var notes by remember(vendor) { mutableStateOf(vendor?.notes.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vendor == null) "Create vendor" else "Edit vendor") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Vendor name") })
            OutlinedTextField(
                phone,
                { phone = it },
                label = { Text("Phone") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(tax, { tax = it }, label = { Text("Tax/PAN reference") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
        } },
        confirmButton = { Button(enabled = name.isNotBlank(), onClick = { onSubmit(VendorDraft(vendor?.id, name.trim(), phone.clean(), tax.clean(), notes.clean())) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable private fun PurchaseDialog(workspace: PurchaseWorkspace, onDismiss: () -> Unit, onSubmit: (PurchaseDraft) -> Unit) {
    val vendors = remember(workspace.directory.vendors) {
        workspace.directory.vendors.filter { it.active }
    }
    val products = remember(workspace.products) { workspace.products.filter { it.active } }
    var vendorId by remember { mutableStateOf(vendors.firstOrNull()?.id.orEmpty()) }
    var invoice by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(NepalDateTime.todayIso()) }
    var payment by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<PurchasePaymentMethod?>(null) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val dismissKeyboard: () -> Unit = {
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }
    val doneKeyboardActions = KeyboardActions(onDone = { dismissKeyboard() })
    val quantities = remember { mutableStateMapOf<String, String>() }
    val costs = remember { mutableStateMapOf<String, String>() }
    val lines = products.mapNotNull { product ->
        val quantity = quantities[product.id]?.toIntOrNull() ?: 0
        val cost = costs[product.id]?.let(MoneyAmounts::parsePaisa)
        if (quantity > 0 && cost != null) PurchaseLineDraft(product.id, product.name, quantity, cost) else null
    }
    val total = runCatching { lines.map { it.lineTotalPaisa } }.getOrNull()
        ?.let(MoneyAmounts::sumPaisa) ?: -1
    val paid = MoneyAmounts.parsePaisa(payment) ?: 0
    val valid = vendorId.isNotBlank() && NepalDateTime.isValidIsoDate(date) &&
        lines.isNotEmpty() && total >= 0 && paid in 0..total && (paid == 0L || method != null)
    Dialog(
        onDismissRequest = {
            dismissKeyboard()
            onDismiss()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 24.dp)
                .imePadding(),
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Review purchase", style = MaterialTheme.typography.headlineSmall)
                Column(
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    Text("Vendor")
                    vendors.forEach { vendor ->
                        TextButton(onClick = { vendorId = vendor.id }) {
                            Text((if (vendor.id == vendorId) "Selected: " else "") + vendor.name)
                        }
                    }
                    OutlinedTextField(
                        invoice,
                        { invoice = it },
                        label = { Text("Invoice reference (optional)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = doneKeyboardActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    BusinessDateField(date, { date = it }, Modifier.fillMaxWidth())
                    Text("Products — enter quantity and unit cost")
                    products.forEach { product ->
                        Text("${product.name} (${product.sku})")
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(
                                quantities[product.id].orEmpty(),
                                { quantities[product.id] = it.filter(Char::isDigit) },
                                label = { Text("Quantity") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                costs[product.id].orEmpty(),
                                { costs[product.id] = it.filter { c -> c.isDigit() || c == '.' } },
                                label = { Text("Unit cost") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Decimal,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = doneKeyboardActions,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Text("Calculated review total ${money(total.coerceAtLeast(0))}; the server total is final.")
                    OutlinedTextField(
                        payment,
                        { payment = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Immediate payment") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = doneKeyboardActions,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row {
                        TextButton(onClick = { method = PurchasePaymentMethod.CASH }) {
                            Text(if (method == PurchasePaymentMethod.CASH) "Selected: Cash" else "Cash")
                        }
                        TextButton(onClick = { method = PurchasePaymentMethod.BANK }) {
                            Text(if (method == PurchasePaymentMethod.BANK) "Selected: Bank" else "Bank")
                        }
                    }
                    workspace.directory.accounts.forEach {
                        Text("${it.name}: ${money(it.balancePaisa)}")
                    }
                }
                TextButton(onClick = dismissKeyboard, modifier = Modifier.fillMaxWidth()) {
                    Text("Hide keyboard")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = {
                        dismissKeyboard()
                        onDismiss()
                    }) { Text("Cancel") }
                    Button(enabled = valid, onClick = {
                        dismissKeyboard()
                        onSubmit(PurchaseDraft(vendorId, invoice.clean(), date, lines, paid, method.takeIf { paid > 0 }))
                    }) { Text("Post purchase once") }
                }
            }
        }
    }
}

private fun Vendor.draft() = VendorDraft(id, name, phone, taxReference, notes)
private fun String.clean() = trim().ifEmpty { null }
private fun money(paisa: Long) = MoneyAmounts.formatNpr(paisa)
