package com.gdad.bags.ui.report

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.BusinessReport
import com.gdad.bags.domain.report.DashboardSummary
import com.gdad.bags.ui.components.BusinessDateField
import com.gdad.bags.ui.components.ContentState
import com.gdad.bags.ui.components.StatusMessage

@Composable
fun DashboardReportSection(
    role: UserRole,
    state: ReportUiState,
    onRefresh: () -> Unit,
) {
    if (role == UserRole.SUPER_ADMIN) return
    state.dashboardMessage?.let { StatusMessage(it) }
    when (val content = state.dashboard) {
        ContentState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
        is ContentState.Empty -> Text(content.message)
        is ContentState.Error -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            StatusMessage(content.safeMessage, isError = true)
            Button(onClick = onRefresh) { Text("Retry dashboard") }
        }
        is ContentState.Ready -> DashboardCards(role, content.value, state.isDashboardRefreshing, onRefresh)
    }
}

@Composable
private fun DashboardCards(
    role: UserRole,
    summary: DashboardSummary,
    refreshing: Boolean,
    onRefresh: () -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        item { ReportMetric("Today's sales", money(summary.salesPaisa)) }
        item { ReportMetric("Low stock", summary.lowStockCount.toString()) }
        if (role == UserRole.OWNER) {
            summary.profitPaisa?.let { item { ReportMetric("Gross profit", money(it)) } }
            summary.cashBankPaisa?.let { item { ReportMetric("Cash and bank", money(it)) } }
            summary.vendorDuePaisa?.let { item { ReportMetric("Vendor due", money(it)) } }
        }
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            cacheAge(summary.generatedAtEpochMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        TextButton(onClick = onRefresh, enabled = !refreshing) {
            Text(if (refreshing) "Refreshing..." else "Refresh dashboard")
        }
    }
}

@Composable
fun ReportScreen(
    session: UserSession,
    state: ReportUiState,
    onDateFrom: (String) -> Unit,
    onDateTo: (String) -> Unit,
    onLoad: () -> Unit,
) {
    if (session.role == UserRole.SUPER_ADMIN) {
        Text("Business reports are not available for this account.")
        return
    }
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            BusinessDateField(
                value = state.dateFrom,
                onValueChange = onDateFrom,
                label = "From date (Nepal) — YYYY-MM-DD",
                modifier = Modifier.fillMaxWidth(),
            )
            BusinessDateField(
                value = state.dateTo,
                onValueChange = onDateTo,
                label = "To date (Nepal) — YYYY-MM-DD",
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(onClick = onLoad, enabled = !state.isPeriodLoading) {
            Text(if (state.isPeriodLoading) "Loading report..." else "Load report")
        }
        state.periodMessage?.let { StatusMessage(it) }
        when (val content = state.period) {
            ContentState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            is ContentState.Empty -> Text(content.message)
            is ContentState.Error -> StatusMessage(content.safeMessage, isError = true)
            is ContentState.Ready -> ReportContent(
                report = content.value,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ReportContent(report: BusinessReport, modifier: Modifier = Modifier) {
    LazyColumn(modifier.testTag("report-content"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text(
                "${report.dateFrom} to ${report.dateTo}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ReportMetric("Sales", money(report.salesPaisa)) }
                item { ReportMetric("Returns", money(report.returnsPaisa)) }
                item { ReportMetric("Net sales", money(report.netSalesPaisa)) }
                item { ReportMetric("Stock quantity", report.stockQuantity.toString()) }
                item { ReportMetric("Low stock", report.lowStockCount.toString()) }
                report.costOfGoodsSoldPaisa?.let { item { ReportMetric("FIFO cost", money(it)) } }
                report.grossProfitPaisa?.let { item { ReportMetric("Gross profit", money(it)) } }
                report.stockValuePaisa?.let { item { ReportMetric("Stock value", money(it)) } }
                report.expensesPaisa?.let { item { ReportMetric("Expenses", money(it)) } }
                report.vendorDueTotalPaisa?.let { item { ReportMetric("Vendor due", money(it)) } }
            }
        }
        item { Text("Low-stock products", style = MaterialTheme.typography.titleMedium) }
        if (report.lowStockProducts.isEmpty()) item { Text("No low-stock products.") }
        items(report.lowStockProducts, key = { "low-${it.productId}" }) { product ->
            Text("${product.sku} - ${product.name}: ${product.currentStock} / ${product.threshold}")
        }
        if (report.role == UserRole.OWNER) {
            item { Text("Vendor dues", style = MaterialTheme.typography.titleMedium) }
            if (report.vendorDues.isEmpty()) item { Text("No vendor dues.") }
            items(report.vendorDues, key = { "vendor-${it.vendorId}" }) { vendor ->
                Text("${vendor.name}: ${money(vendor.duePaisa)}")
            }
            item { Text("Cash and bank", style = MaterialTheme.typography.titleMedium) }
            if (report.accountBalances.isEmpty()) item { Text("No active cash or bank accounts.") }
            items(report.accountBalances, key = { "account-${it.accountId}" }) { account ->
                Text("${account.name} (${account.type}): ${money(account.balancePaisa)}")
            }
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

private fun money(paisa: Long): String = MoneyAmounts.formatNpr(paisa)

private fun cacheAge(generatedAt: Long, now: Long = System.currentTimeMillis()): String {
    val minutes = ((now - generatedAt).coerceAtLeast(0) / 60_000)
    return when {
        minutes < 1 -> "Updated just now"
        minutes == 1L -> "Updated 1 minute ago"
        minutes < 60 -> "Updated $minutes minutes ago"
        else -> "Updated ${minutes / 60} hours ago"
    }
}
