package com.gdad.bags.ui.product

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
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.product.CatalogProduct
import com.gdad.bags.domain.product.ProductDraft
import com.gdad.bags.domain.product.ProductMutation
import com.gdad.bags.ui.components.ContentStateHost

@Composable
fun ProductCatalogScreen(
    session: UserSession,
    state: ProductCatalogUiState,
    onSearch: (String) -> Unit,
    onRefresh: () -> Unit,
    onMutate: (ProductMutation, ProductDraft) -> Unit,
) {
    var editing by remember { mutableStateOf<CatalogProduct?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var archiving by remember { mutableStateOf<CatalogProduct?>(null) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onSearch,
            label = { Text("Search name, SKU or barcode") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (session.role == UserRole.OWNER) {
            Button(onClick = { showCreate = true }, enabled = !state.isMutating) { Text("Create product") }
        }
        state.safeMessage?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        ContentStateHost(state.content, onRetry = onRefresh) { products ->
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(products, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        canManage = session.role == UserRole.OWNER && product.active,
                        canSeeCost = session.role == UserRole.OWNER,
                        isMutating = state.isMutating,
                        onEdit = { editing = product },
                        onArchive = { archiving = product },
                    )
                }
            }
        }
    }
    if (showCreate || editing != null) {
        ProductDialog(
            product = editing,
            onDismiss = { showCreate = false; editing = null },
            onSubmit = { draft ->
                val mutation = if (editing == null) ProductMutation.CREATE else ProductMutation.UPDATE
                showCreate = false
                editing = null
                onMutate(mutation, draft)
            },
        )
    }
    archiving?.let { product ->
        AlertDialog(
            onDismissRequest = { archiving = null },
            title = { Text("Archive ${product.name}?") },
            text = { Text("The product remains visible in historical records and cannot be used for new operations.") },
            confirmButton = {
                Button(onClick = {
                    archiving = null
                    onMutate(ProductMutation.ARCHIVE, product.toDraft())
                }) { Text("Archive") }
            },
            dismissButton = { TextButton(onClick = { archiving = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ProductCard(
    product: CatalogProduct,
    canManage: Boolean,
    canSeeCost: Boolean,
    isMutating: Boolean,
    onEdit: () -> Unit,
    onArchive: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text("SKU ${product.sku}" + (product.barcode?.let { " • Barcode $it" } ?: ""))
            Text("Selling price ${money(product.sellingPricePaisa)}")
            Text("On hand ${product.quantityOnHand} • Low-stock threshold ${product.lowStockThreshold}")
            if (canSeeCost) Text("Stock value ${money(product.stockValuePaisa ?: 0)}")
            if (!product.active) Text("Archived — historical use only", color = MaterialTheme.colorScheme.error)
            if (canManage) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onEdit, enabled = !isMutating) { Text("Edit") }
                    OutlinedButton(onClick = onArchive, enabled = !isMutating) { Text("Archive") }
                }
            }
        }
    }
}

@Composable
private fun ProductDialog(
    product: CatalogProduct?,
    onDismiss: () -> Unit,
    onSubmit: (ProductDraft) -> Unit,
) {
    var name by remember(product) { mutableStateOf(product?.name.orEmpty()) }
    var sku by remember(product) { mutableStateOf(product?.sku.orEmpty()) }
    var barcode by remember(product) { mutableStateOf(product?.barcode.orEmpty()) }
    var price by remember(product) { mutableStateOf(product?.sellingPricePaisa?.let { "%.2f".format(it / 100.0) }.orEmpty()) }
    var threshold by remember(product) { mutableStateOf(product?.lowStockThreshold?.toString().orEmpty()) }
    val pricePaisa = price.toDoubleOrNull()?.times(100)?.toLong()
    val thresholdValue = threshold.toIntOrNull()
    val valid = name.isNotBlank() && sku.isNotBlank() && pricePaisa != null && pricePaisa >= 0 && thresholdValue != null && thresholdValue >= 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Create product" else "Edit product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Product name") })
                OutlinedTextField(sku, { sku = it.trim() }, label = { Text("SKU") })
                OutlinedTextField(barcode, { barcode = it.trim() }, label = { Text("Barcode (optional)") })
                OutlinedTextField(price, { price = it.filter { char -> char.isDigit() || char == '.' } }, label = { Text("Selling price") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(threshold, { threshold = it.filter(Char::isDigit) }, label = { Text("Low-stock threshold") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        },
        confirmButton = {
            Button(enabled = valid, onClick = {
                onSubmit(ProductDraft(product?.id, name.trim(), sku.trim(), barcode.trim().ifEmpty { null }, requireNotNull(pricePaisa), requireNotNull(thresholdValue)))
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun CatalogProduct.toDraft() = ProductDraft(id, name, sku, barcode, sellingPricePaisa, lowStockThreshold)
private fun money(paisa: Long) = "Rs %.2f".format(paisa / 100.0)
