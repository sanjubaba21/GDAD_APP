package com.gdad.bags.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.NepalDateTime
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.domain.purchase.PostedPurchase
import com.gdad.bags.domain.purchase.PurchaseDraft
import com.gdad.bags.domain.purchase.PurchaseLineDraft
import com.gdad.bags.domain.purchase.PurchasePaymentMethod
import com.gdad.bags.domain.purchase.Vendor
import com.gdad.bags.domain.purchase.VendorDraft
import com.gdad.bags.domain.purchase.VendorMutation
import com.gdad.bags.domain.sale.PostedSale
import com.gdad.bags.domain.sale.SaleDraft
import com.gdad.bags.domain.sale.SaleLineDraft
import com.gdad.bags.domain.sale.SalePaymentDraft
import com.gdad.bags.domain.sale.SalePaymentMethod

@Composable
fun DesktopProductsContent(
    session: UserSession,
    state: DesktopUiState,
    controller: DesktopController,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CatalogProduct?>(null) }
    var creating by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<CatalogProduct?>(null) }
    val visible = state.products.filter { product ->
        query.isBlank() || product.name.contains(query, true) || product.sku.contains(query, true) ||
            product.barcode?.contains(query, true) == true
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search name, SKU or barcode") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = controller::refreshProducts, enabled = !state.isBusy) {
                Text("Refresh")
            }
            if (session.role == UserRole.OWNER) {
                Spacer(Modifier.width(12.dp))
                Button(onClick = { creating = true }, enabled = !state.isBusy) {
                    Text("Create product")
                }
            }
        }
        if (visible.isEmpty() && !state.isBusy) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (query.isBlank()) "No products are available." else "No products match the search.",
                    Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visible, key = { it.id }) { product ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(product.name, style = MaterialTheme.typography.titleMedium)
                                Text("SKU ${product.sku}" + (product.barcode?.let { " • Barcode $it" } ?: ""))
                                Text("Suggested price ${money(product.sellingPricePaisa)}")
                                Text("Minimum price ${money(product.minimumSellingPricePaisa)}")
                                Text("On hand ${product.quantityOnHand} • Low-stock at ${product.lowStockThreshold}")
                                if (session.role == UserRole.OWNER) {
                                    Text("Stock value ${money(product.stockValuePaisa ?: 0)}")
                                }
                                if (!product.active) {
                                    Text("Archived — historical use only", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (session.role == UserRole.OWNER && product.active) {
                                OutlinedButton(onClick = { editing = product }, enabled = !state.isBusy) {
                                    Text("Edit")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { archiving = product }, enabled = !state.isBusy) {
                                    Text("Archive")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.canRetryProductMutation) {
            OutlinedButton(onClick = controller::retryProductMutation, enabled = !state.isBusy) {
                Text("Retry the same product change safely")
            }
        }
    }

    if (creating || editing != null) {
        ProductEditor(
            product = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { draft ->
                val mutation = if (editing == null) ProductMutation.CREATE else ProductMutation.UPDATE
                creating = false
                editing = null
                controller.mutateProduct(mutation, draft)
            },
        )
    }
    archiving?.let { product ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Archive ${product.name}?") },
            text = { Text("New transactions will no longer use this product. History remains available.") },
            confirmButton = {
                Button(onClick = {
                    archiving = null
                    controller.mutateProduct(ProductMutation.ARCHIVE, product.toDraft())
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProductEditor(
    product: CatalogProduct?,
    onDismiss: () -> Unit,
    onSave: (ProductDraft) -> Unit,
) {
    var name by remember(product) { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember(product) { mutableStateOf(product?.sku.orEmpty()) }
    var barcode by remember(product) { mutableStateOf(product?.barcode.orEmpty()) }
    var price by remember(product) { mutableStateOf(product?.sellingPricePaisa?.let(::editableMoney).orEmpty()) }
    var minimumPrice by remember(product) {
        mutableStateOf(product?.minimumSellingPricePaisa?.let(::editableMoney).orEmpty())
    }
    var threshold by remember(product) { mutableStateOf(product?.lowStockThreshold?.toString().orEmpty()) }
    val pricePaisa = MoneyAmounts.parsePaisa(price)
    val minimumPricePaisa = MoneyAmounts.parsePaisa(minimumPrice)
    val thresholdValue = threshold.toIntOrNull()
    val valid = name.trim().length in 1..160 && sku.trim().length in 1..64 &&
        (barcode.isBlank() || barcode.trim().length in 3..64) && pricePaisa != null &&
        minimumPricePaisa != null && minimumPricePaisa <= pricePaisa &&
        thresholdValue != null && thresholdValue >= 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Create product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Product name") })
                OutlinedTextField(sku, { sku = it.trim() }, label = { Text("SKU") })
                OutlinedTextField(barcode, { barcode = it.trim() }, label = { Text("Barcode (optional)") })
                OutlinedTextField(
                    price,
                    { price = decimalInput(it) },
                    label = { Text("Suggested selling price") },
                )
                OutlinedTextField(
                    minimumPrice,
                    { minimumPrice = decimalInput(it) },
                    label = { Text("Minimum selling price") },
                )
                OutlinedTextField(
                    threshold,
                    { threshold = it.filter(Char::isDigit).take(9) },
                    label = { Text("Low-stock threshold") },
                )
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        ProductDraft(
                            productId = product?.id,
                            name = name.trim(),
                            sku = sku.trim(),
                            barcode = barcode.trim().ifEmpty { null },
                            sellingPricePaisa = requireNotNull(pricePaisa),
                            lowStockThreshold = requireNotNull(thresholdValue),
                            minimumSellingPricePaisa = requireNotNull(minimumPricePaisa),
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DesktopSalesContent(
    session: UserSession,
    state: DesktopUiState,
    controller: DesktopController,
) {
    val products = state.products.filter { it.active && it.quantityOnHand > 0 }
    val quantities = remember(products.map { it.id }) { mutableStateMapOf<String, String>() }
    val prices = remember(products.map { it.id }) {
        mutableStateMapOf<String, String>().apply {
            products.forEach { put(it.id, editableMoney(it.sellingPricePaisa)) }
        }
    }
    var businessDate by remember { mutableStateOf(NepalDateTime.todayIso()) }
    var discount by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(SalePaymentMethod.CASH) }
    var credit by remember { mutableStateOf(false) }
    var customer by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(NepalDateTime.todayIso()) }

    val lines = products.mapNotNull { product ->
        val quantity = quantities[product.id]?.toIntOrNull() ?: 0
        if (quantity <= 0) null else SaleLineDraft(
            productId = product.id,
            productName = product.name,
            quantity = quantity,
            effectiveUnitPricePaisa = MoneyAmounts.parsePaisa(prices[product.id].orEmpty()),
        )
    }
    val subtotal = calculateSaleSubtotal(lines)
    val discountPaisa = if (discount.isBlank()) 0L else MoneyAmounts.parsePaisa(discount)
    val total = calculateSaleTotal(subtotal, discountPaisa)

    LaunchedEffect(total, credit) {
        if (!credit) payment = total?.let(::editableMoney).orEmpty()
    }

    val paid = if (payment.isBlank()) 0L else MoneyAmounts.parsePaisa(payment)
    val valid = NepalDateTime.isValidIsoDate(businessDate) && lines.isNotEmpty() &&
        lines.all { line ->
            val product = products.single { it.id == line.productId }
            line.effectiveUnitPricePaisa != null &&
                line.effectiveUnitPricePaisa >= product.minimumSellingPricePaisa &&
                line.quantity <= product.quantityOnHand
        } && total != null && paid != null && when {
        session.role == UserRole.SALESMAN -> paid == total
        credit -> paid <= total && customer.isNotBlank() && contact.isNotBlank() &&
            NepalDateTime.isValidIsoDate(dueDate)
        else -> paid == total
    }

    if (products.isEmpty() && !state.isBusy) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("No active products are currently in stock.")
                OutlinedButton(onClick = controller::refreshProducts) { Text("Refresh products") }
            }
        }
    } else {
        Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            LazyColumn(
                Modifier.weight(1.25f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(products, key = { it.id }) { product ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(product.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${product.quantityOnHand} available • Suggested ${money(product.sellingPricePaisa)} • " +
                                    "Minimum ${money(product.minimumSellingPricePaisa)}",
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    quantities[product.id].orEmpty(),
                                    { quantities[product.id] = it.filter(Char::isDigit).take(9) },
                                    label = { Text("Quantity") },
                                    singleLine = true,
                                    modifier = Modifier.width(150.dp),
                                )
                                OutlinedTextField(
                                    prices[product.id].orEmpty(),
                                    { prices[product.id] = decimalInput(it) },
                                    label = { Text("Actual selling price") },
                                    supportingText = { Text("Edit for the bargained price") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
            Card(Modifier.weight(0.75f).fillMaxSize()) {
                Column(
                    Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("Sale summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        businessDate,
                        { businessDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                        label = { Text("Business date — YYYY-MM-DD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (session.role == UserRole.OWNER) {
                        OutlinedTextField(
                            discount,
                            { discount = decimalInput(it) },
                            label = { Text("Sale discount") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(credit, { credit = it })
                            Text("Credit sale")
                        }
                    }
                    HorizontalDivider()
                    Text("Subtotal ${money(subtotal ?: 0)}")
                    Text("Final estimate ${money(total?.coerceAtLeast(0) ?: 0)}", fontWeight = FontWeight.Bold)
                    Text("The server-authoritative FIFO total is final.")
                    OutlinedTextField(
                        payment,
                        { payment = decimalInput(it) },
                        label = { Text(if (credit) "Payment now" else "Full payment") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { paymentMethod = SalePaymentMethod.CASH }) {
                            Text(if (paymentMethod == SalePaymentMethod.CASH) "✓ Cash" else "Cash")
                        }
                        OutlinedButton(onClick = { paymentMethod = SalePaymentMethod.BANK }) {
                            Text(if (paymentMethod == SalePaymentMethod.BANK) "✓ Bank" else "Bank")
                        }
                    }
                    if (credit && session.role == UserRole.OWNER) {
                        OutlinedTextField(customer, { customer = it }, label = { Text("Customer name") })
                        OutlinedTextField(contact, { contact = it }, label = { Text("Customer contact") })
                        OutlinedTextField(
                            dueDate,
                            { dueDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                            label = { Text("Due date — YYYY-MM-DD") },
                        )
                    }
                    Button(
                        enabled = valid && !state.isBusy,
                        onClick = {
                            controller.postSale(
                                SaleDraft(
                                    businessDate = businessDate,
                                    lines = lines,
                                    saleDiscountPaisa = requireNotNull(discountPaisa),
                                    isCredit = credit,
                                    customerName = customer.trim().ifEmpty { null },
                                    customerContact = contact.trim().ifEmpty { null },
                                    dueDate = dueDate.takeIf { credit },
                                    payments = requireNotNull(paid).takeIf { it > 0 }
                                        ?.let { listOf(SalePaymentDraft(paymentMethod, it)) }.orEmpty(),
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (state.isBusy) "Posting…" else "Confirm and post once") }
                    if (state.canRetrySale) {
                        OutlinedButton(onClick = controller::retrySale, enabled = !state.isBusy) {
                            Text("Retry the same sale safely")
                        }
                    }
                }
            }
        }
    }

    state.postedSale?.let { receipt -> SaleReceipt(session, receipt, controller::dismissPostedSale) }
}

@Composable
private fun SaleReceipt(session: UserSession, sale: PostedSale, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sale posted") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
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

@Composable
fun DesktopVendorsContent(state: DesktopUiState, controller: DesktopController) {
    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Vendor?>(null) }
    var archiving by remember { mutableStateOf<Vendor?>(null) }
    val vendors = state.purchaseDirectory.vendors.filter { vendor ->
        query.isBlank() || vendor.name.contains(query, true) ||
            vendor.phone?.contains(query, true) == true ||
            vendor.taxReference?.contains(query, true) == true
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                query,
                { query = it },
                label = { Text("Search vendors") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedButton(
                onClick = { controller.refreshPurchasing(includeProducts = false) },
                enabled = !state.isBusy,
            ) { Text("Refresh") }
            Spacer(Modifier.width(12.dp))
            Button(onClick = { creating = true }, enabled = !state.isBusy) { Text("Create vendor") }
        }

        if (vendors.isEmpty() && !state.isBusy) {
            Card(Modifier.fillMaxWidth()) {
                Text(
                    if (query.isBlank()) "No vendors are available." else "No vendors match the search.",
                    Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(vendors, key = { it.id }) { vendor ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(vendor.name, style = MaterialTheme.typography.titleMedium)
                                vendor.phone?.let { Text("Phone $it") }
                                vendor.taxReference?.let { Text("Tax reference $it") }
                                Text("Outstanding due ${money(vendor.duePaisa)}")
                                if (!vendor.active) {
                                    Text("Archived — history retained", color = MaterialTheme.colorScheme.error)
                                }
                            }
                            if (vendor.active) {
                                OutlinedButton(onClick = { editing = vendor }, enabled = !state.isBusy) {
                                    Text("Edit")
                                }
                                Spacer(Modifier.width(8.dp))
                                OutlinedButton(onClick = { archiving = vendor }, enabled = !state.isBusy) {
                                    Text("Archive")
                                }
                            }
                        }
                    }
                }
            }
        }
        if (state.canRetryVendorMutation) {
            OutlinedButton(onClick = controller::retryVendorMutation, enabled = !state.isBusy) {
                Text("Retry the same vendor change safely")
            }
        }
    }

    if (creating || editing != null) {
        VendorEditor(
            vendor = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { draft ->
                val mutation = if (editing == null) VendorMutation.CREATE else VendorMutation.UPDATE
                creating = false
                editing = null
                controller.mutateVendor(mutation, draft)
            },
        )
    }
    archiving?.let { vendor ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Archive ${vendor.name}?") },
            text = { Text("New purchases cannot use this vendor. Existing bills and history remain available.") },
            confirmButton = {
                Button(onClick = {
                    archiving = null
                    controller.mutateVendor(VendorMutation.ARCHIVE, vendor.toDraft())
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun VendorEditor(vendor: Vendor?, onDismiss: () -> Unit, onSave: (VendorDraft) -> Unit) {
    var name by remember(vendor) { mutableStateOf(vendor?.name.orEmpty()) }
    var phone by remember(vendor) { mutableStateOf(vendor?.phone.orEmpty()) }
    var taxReference by remember(vendor) { mutableStateOf(vendor?.taxReference.orEmpty()) }
    var notes by remember(vendor) { mutableStateOf(vendor?.notes.orEmpty()) }
    val valid = name.trim().length in 1..160 && phone.trim().length <= 40 &&
        taxReference.trim().length <= 80 && notes.trim().length <= 1000

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (vendor == null) "Create vendor" else "Edit vendor") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(160) }, label = { Text("Vendor name") })
                OutlinedTextField(phone, { phone = it.take(40) }, label = { Text("Phone (optional)") })
                OutlinedTextField(
                    taxReference,
                    { taxReference = it.take(80) },
                    label = { Text("Tax reference (optional)") },
                )
                OutlinedTextField(notes, { notes = it.take(1000) }, label = { Text("Notes (optional)") })
            }
        },
        confirmButton = {
            Button(
                enabled = valid,
                onClick = {
                    onSave(
                        VendorDraft(
                            vendorId = vendor?.id,
                            name = name.trim(),
                            phone = phone.trim().ifEmpty { null },
                            taxReference = taxReference.trim().ifEmpty { null },
                            notes = notes.trim().ifEmpty { null },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun DesktopPurchasesContent(state: DesktopUiState, controller: DesktopController) {
    val products = state.products.filter { it.active }
    val vendors = state.purchaseDirectory.vendors.filter { it.active }
    val quantities = remember(products.map { it.id }) { mutableStateMapOf<String, String>() }
    val unitCosts = remember(products.map { it.id }) { mutableStateMapOf<String, String>() }
    var selectedVendorId by remember(vendors.map { it.id }) { mutableStateOf<String?>(null) }
    var invoiceReference by remember { mutableStateOf("") }
    var businessDate by remember { mutableStateOf(NepalDateTime.todayIso()) }
    var payment by remember { mutableStateOf("") }
    var paymentMethod by remember { mutableStateOf(PurchasePaymentMethod.CASH) }

    val selectedProducts = products.filter { (quantities[it.id]?.toIntOrNull() ?: 0) > 0 }
    val lines = selectedProducts.mapNotNull { product ->
        val quantity = quantities[product.id]?.toIntOrNull() ?: return@mapNotNull null
        val cost = MoneyAmounts.parsePaisa(unitCosts[product.id].orEmpty()) ?: return@mapNotNull null
        PurchaseLineDraft(product.id, product.name, quantity, cost)
    }
    val total = calculatePurchaseTotal(lines)
    val paid = if (payment.isBlank()) 0L else MoneyAmounts.parsePaisa(payment)
    val valid = selectedVendorId != null && NepalDateTime.isValidIsoDate(businessDate) &&
        selectedProducts.isNotEmpty() && lines.size == selectedProducts.size && total != null &&
        paid != null && paid <= total

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        Card(Modifier.weight(1.2f).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Purchase lines", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(
                        onClick = {
                            choosePurchaseTemplateDestination(controller.defaultPurchaseTemplateFileName())
                                ?.let(controller::savePurchaseTemplate)
                        },
                        enabled = !state.isBusy,
                    ) { Text("Save Excel template") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = { choosePurchaseWorkbook()?.let(controller::loadPurchaseWorkbook) },
                        enabled = !state.isBusy,
                    ) { Text("Upload Excel bill") }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = { controller.refreshPurchasing(includeProducts = true) },
                        enabled = !state.isBusy,
                    ) { Text("Refresh") }
                }
                if (products.isEmpty() && !state.isBusy) {
                    Text("Create or reactivate a product before posting a purchase.")
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(products, key = { it.id }) { product ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Text(product.name, style = MaterialTheme.typography.titleMedium)
                                    Text("SKU ${product.sku} • Currently ${product.quantityOnHand} on hand")
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        OutlinedTextField(
                                            quantities[product.id].orEmpty(),
                                            { quantities[product.id] = it.filter(Char::isDigit).take(9) },
                                            label = { Text("Quantity received") },
                                            singleLine = true,
                                            modifier = Modifier.width(170.dp),
                                        )
                                        OutlinedTextField(
                                            unitCosts[product.id].orEmpty(),
                                            { unitCosts[product.id] = decimalInput(it) },
                                            label = { Text("Unit cost") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Card(Modifier.weight(0.8f).fillMaxSize()) {
            Column(
                Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Purchase summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text("Vendor", style = MaterialTheme.typography.labelLarge)
                if (vendors.isEmpty()) {
                    Text("Create an active vendor from the Vendors screen first.")
                } else {
                    LazyColumn(Modifier.heightIn(max = 180.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(vendors, key = { it.id }) { vendor ->
                            OutlinedButton(
                                onClick = { selectedVendorId = vendor.id },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(if (selectedVendorId == vendor.id) "✓ ${vendor.name}" else vendor.name)
                            }
                        }
                    }
                }
                OutlinedTextField(
                    invoiceReference,
                    { invoiceReference = it.take(120) },
                    label = { Text("Invoice reference (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    businessDate,
                    { businessDate = it.filter { ch -> ch.isDigit() || ch == '-' }.take(10) },
                    label = { Text("Business date — YYYY-MM-DD") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                HorizontalDivider()
                Text("Purchase total ${money(total ?: 0)}", fontWeight = FontWeight.Bold)
                Text("The server-authoritative receipt total is final.")
                OutlinedTextField(
                    payment,
                    { payment = decimalInput(it) },
                    label = { Text("Payment now — blank for full due") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if ((paid ?: 0L) > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { paymentMethod = PurchasePaymentMethod.CASH }) {
                            Text(if (paymentMethod == PurchasePaymentMethod.CASH) "✓ Cash" else "Cash")
                        }
                        OutlinedButton(onClick = { paymentMethod = PurchasePaymentMethod.BANK }) {
                            Text(if (paymentMethod == PurchasePaymentMethod.BANK) "✓ Bank" else "Bank")
                        }
                    }
                }
                Button(
                    enabled = valid && !state.isBusy,
                    onClick = {
                        controller.postPurchase(
                            PurchaseDraft(
                                vendorId = requireNotNull(selectedVendorId),
                                invoiceReference = invoiceReference.trim().ifEmpty { null },
                                businessDate = businessDate,
                                lines = lines,
                                paymentAmountPaisa = requireNotNull(paid),
                                paymentMethod = paymentMethod.takeIf { requireNotNull(paid) > 0 },
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (state.isBusy) "Posting…" else "Confirm and post once") }
                if (state.canRetryPurchase) {
                    OutlinedButton(onClick = controller::retryPurchase, enabled = !state.isBusy) {
                        Text("Retry the same purchase safely")
                    }
                }
            }
        }
    }

    state.purchaseImportPreview?.let { preview ->
        PurchaseImportPreviewDialog(
            preview = preview,
            products = state.products,
            isBusy = state.isBusy,
            canRetry = state.canRetryPurchaseImport,
            onDismiss = controller::clearPurchaseImport,
            onConfirm = controller::confirmPurchaseImport,
        )
    }

    state.postedPurchase?.let { receipt ->
        PurchaseReceipt(receipt, controller::dismissPostedPurchase)
    }
}

@Composable
private fun PurchaseImportPreviewDialog(
    preview: PurchaseImportPreview,
    products: List<CatalogProduct>,
    isBusy: Boolean,
    canRetry: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text("Review Excel purchase") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(preview.bill.sourceFileName)
                Text("Vendor ${preview.bill.vendorName}")
                Text("Invoice ${preview.bill.invoiceReference ?: "Not supplied"}")
                Text("Business date ${preview.bill.businessDate}")
                Text(
                    "${preview.bill.lines.size} line(s) • ${preview.existingProductCount} existing • " +
                        "${preview.newProductCount} new",
                )
                Text("Purchase total ${money(preview.bill.totalPaisa)}", fontWeight = FontWeight.Bold)
                Text("Payment now ${money(preview.bill.paymentAmountPaisa)}")
                Text(
                    if (preview.vendorWillBeCreated) "The vendor will be created."
                    else "The existing active vendor will be used.",
                )
                HorizontalDivider()
                LazyColumn(Modifier.heightIn(max = 260.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    items(preview.bill.lines, key = { it.sku.lowercase() }) { line ->
                        val existing = products.singleOrNull { it.sku.equals(line.sku, true) }?.active == true
                        Text(
                            "${line.sku} — ${line.productName}: ${line.quantity} × " +
                                "${money(line.unitCostPaisa)} = " +
                                money(requireNotNull(MoneyAmounts.multiplyPaisa(line.unitCostPaisa, line.quantity))) +
                                " • min ${money(line.minimumSellingPricePaisa)}" +
                                (if (existing) " • existing product" else " • create product"),
                        )
                    }
                }
                preview.blockingMessage?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
                if (canRetry) {
                    Text("Retry uses the same vendor, product, and purchase request IDs.")
                } else {
                    Text("Nothing changes until you confirm. Confirming posts one purchase bill automatically.")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isBusy && preview.blockingMessage == null,
            ) { Text(if (canRetry) "Retry same import" else if (isBusy) "Posting…" else "Confirm and post once") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isBusy) { Text("Cancel") }
        },
    )
}

@Composable
private fun PurchaseReceipt(purchase: PostedPurchase, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Purchase posted") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Server-authoritative purchase receipt")
                Text("Total ${money(purchase.grandTotalPaisa)}")
                Text("Paid ${money(purchase.paidPaisa)}")
                Text("Vendor due ${money(purchase.duePaisa)}")
                Text("${purchase.lineCount} line(s)")
                Text("Bill ${purchase.purchaseBillId}")
                Text("Receipt ${purchase.purchaseReceiptId}")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

private fun Vendor.toDraft() = VendorDraft(
    vendorId = id,
    name = name,
    phone = phone,
    taxReference = taxReference,
    notes = notes,
)

internal fun calculatePurchaseTotal(lines: List<PurchaseLineDraft>): Long? =
    runCatching { lines.map { it.lineTotalPaisa } }.getOrNull()?.let(MoneyAmounts::sumPaisa)

private fun CatalogProduct.toDraft() = ProductDraft(
    id,
    name,
    sku,
    barcode,
    sellingPricePaisa,
    lowStockThreshold,
    minimumSellingPricePaisa,
)

private fun decimalInput(value: String): String = value
    .filter { it.isDigit() || it == '.' }
    .take(18)

private fun money(paisa: Long): String = MoneyAmounts.formatNpr(paisa)

private fun editableMoney(paisa: Long): String =
    "${paisa / 100}.${(paisa % 100).toString().padStart(2, '0')}"

internal fun calculateSaleSubtotal(lines: List<SaleLineDraft>): Long? {
    val totals = lines.map { line ->
        line.effectiveUnitPricePaisa?.let { price -> MoneyAmounts.multiplyPaisa(price, line.quantity) }
    }
    return totals.takeIf { values -> values.all { it != null } }
        ?.let { MoneyAmounts.sumPaisa(it.filterNotNull()) }
}

internal fun calculateSaleTotal(subtotalPaisa: Long?, discountPaisa: Long?): Long? =
    if (subtotalPaisa != null && discountPaisa != null) {
        MoneyAmounts.subtractPaisa(subtotalPaisa, discountPaisa)?.takeIf { it >= 0 }
    } else {
        null
    }
