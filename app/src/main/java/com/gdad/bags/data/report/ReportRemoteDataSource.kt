package com.gdad.bags.data.report

import com.gdad.bags.data.local.CacheOwner
import com.gdad.bags.data.remote.RemoteCallExecutor
import com.gdad.bags.data.remote.RemoteOperation
import com.gdad.bags.data.remote.RemoteResult
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.report.*
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface ReportRemoteDataSource {
    suspend fun dashboard(owner: CacheOwner): RemoteResult<BusinessReport>
    suspend fun period(owner: CacheOwner, dateFrom: String, dateTo: String): RemoteResult<BusinessReport>
}

class SupabaseReportRemoteDataSource(
    private val client: SupabaseClient,
    private val calls: RemoteCallExecutor,
    private val now: () -> Long = System::currentTimeMillis,
) : ReportRemoteDataSource {
    override suspend fun dashboard(owner: CacheOwner) = calls.execute(
        RemoteOperation.LOAD_DASHBOARD_REPORT,
        requiresAuth = true,
    ) {
        client.postgrest.rpc(
            "get_dashboard_report",
            JsonObject(mapOf("p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)))),
        ).decodeAs<ReportRow>().domain(now())
    }

    override suspend fun period(owner: CacheOwner, dateFrom: String, dateTo: String) = calls.execute(
        RemoteOperation.LOAD_BUSINESS_REPORT,
        requiresAuth = true,
    ) {
        client.postgrest.rpc(
            "get_business_report",
            JsonObject(
                mapOf(
                    "p_shop_id" to JsonPrimitive(requireNotNull(owner.shopId)),
                    "p_date_from" to JsonPrimitive(dateFrom),
                    "p_date_to" to JsonPrimitive(dateTo),
                ),
            ),
        ).decodeAs<ReportRow>().domain(now())
    }
}

@Serializable
private data class ReportRow(
    @SerialName("shop_id") val shopId: String,
    val role: String,
    @SerialName("date_from") val dateFrom: String,
    @SerialName("date_to") val dateTo: String,
    @SerialName("sales_total_paisa") val sales: Long,
    @SerialName("returns_total_paisa") val returns: Long,
    @SerialName("net_sales_paisa") val netSales: Long,
    @SerialName("stock_on_hand_quantity") val stockQuantity: Long,
    @SerialName("low_stock_count") val lowStockCount: Int,
    @SerialName("low_stock_products") val lowStockProducts: List<LowStockRow> = emptyList(),
    @SerialName("cost_of_goods_sold_paisa") val costOfGoodsSold: Long? = null,
    @SerialName("gross_profit_paisa") val grossProfit: Long? = null,
    @SerialName("stock_value_paisa") val stockValue: Long? = null,
    @SerialName("vendor_due_total_paisa") val vendorDueTotal: Long? = null,
    @SerialName("vendor_dues") val vendorDues: List<VendorDueRow>? = null,
    @SerialName("account_balances") val accountBalances: List<AccountBalanceRow>? = null,
    @SerialName("expenses_total_paisa") val expenses: Long? = null,
) {
    fun domain(generatedAt: Long): BusinessReport {
        val parsedRole = UserRole.valueOf(role.uppercase())
        if (parsedRole == UserRole.OWNER) {
            requireNotNull(costOfGoodsSold)
            requireNotNull(grossProfit)
            requireNotNull(stockValue)
            requireNotNull(vendorDueTotal)
            requireNotNull(vendorDues)
            requireNotNull(accountBalances)
            requireNotNull(expenses)
        }
        return BusinessReport(
            shopId = shopId,
            role = parsedRole,
            dateFrom = dateFrom,
            dateTo = dateTo,
            salesPaisa = sales,
            returnsPaisa = returns,
            netSalesPaisa = netSales,
            stockQuantity = stockQuantity,
            lowStockCount = lowStockCount,
            lowStockProducts = lowStockProducts.map { it.domain() },
            costOfGoodsSoldPaisa = costOfGoodsSold,
            grossProfitPaisa = grossProfit,
            stockValuePaisa = stockValue,
            vendorDueTotalPaisa = vendorDueTotal,
            vendorDues = vendorDues.orEmpty().map { it.domain() },
            accountBalances = accountBalances.orEmpty().map { it.domain() },
            expensesPaisa = expenses,
            generatedAtEpochMillis = generatedAt,
        )
    }
}

@Serializable
private data class LowStockRow(
    @SerialName("product_id") val id: String,
    @SerialName("sku_code") val sku: String,
    val name: String,
    @SerialName("current_stock") val stock: Long,
    @SerialName("low_stock_threshold") val threshold: Int,
) {
    fun domain() = LowStockReportItem(id, sku, name, stock, threshold)
}

@Serializable
private data class VendorDueRow(
    @SerialName("vendor_id") val id: String,
    @SerialName("vendor_name") val name: String,
    @SerialName("due_paisa") val due: Long,
) {
    fun domain() = VendorDueReportItem(id, name, due)
}

@Serializable
private data class AccountBalanceRow(
    @SerialName("account_id") val id: String,
    @SerialName("display_name") val name: String,
    @SerialName("account_type") val type: String,
    @SerialName("balance_paisa") val balance: Long,
) {
    fun domain() = AccountBalanceReportItem(id, name, type, balance)
}
