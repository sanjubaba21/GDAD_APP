package com.gdad.bags.ui.navigation

import androidx.annotation.Keep
import com.gdad.bags.domain.model.UserRole
import kotlinx.serialization.Serializable

@Serializable
data object DashboardRoute

@Keep
@Serializable
enum class FeatureDestination {
    ACCOUNTS,
    PRODUCTS,
    SALES,
    RETURNS,
    VENDORS,
    FINANCE,
    REPORTS,
    NOTIFICATIONS,
    STOCK_ADJUSTMENTS,
}

@Serializable
data class FeatureRoute(val destination: FeatureDestination)

data class NavigationItem(
    val destination: FeatureDestination,
    val title: String,
    val description: String,
)

object NavigationPolicy {
    private val superAdmin = setOf(FeatureDestination.ACCOUNTS, FeatureDestination.NOTIFICATIONS)
    private val owner = FeatureDestination.entries.toSet()
    private val salesman = setOf(
        FeatureDestination.PRODUCTS,
        FeatureDestination.SALES,
        FeatureDestination.RETURNS,
        FeatureDestination.STOCK_ADJUSTMENTS,
        FeatureDestination.REPORTS,
        FeatureDestination.NOTIFICATIONS,
    )

    fun canOpen(role: UserRole, destination: FeatureDestination): Boolean = when (role) {
        UserRole.SUPER_ADMIN -> destination in superAdmin
        UserRole.OWNER -> destination in owner
        UserRole.SALESMAN -> destination in salesman
    }

    fun visibleItems(role: UserRole): List<NavigationItem> = when (role) {
        UserRole.SUPER_ADMIN -> listOf(
            NavigationItem(FeatureDestination.ACCOUNTS, "Owners and shops", "Create, disable, or reset an Owner PIN"),
            NavigationItem(FeatureDestination.NOTIFICATIONS, "Notifications", "Account and system alerts"),
        )
        UserRole.OWNER -> listOf(
            NavigationItem(FeatureDestination.SALES, "New sale", "Walk-in or online sale"),
            NavigationItem(FeatureDestination.PRODUCTS, "Products and stock", "Catalog, FIFO batches, and stock"),
            NavigationItem(FeatureDestination.VENDORS, "Vendors", "Bills, payments, dues, and returns"),
            NavigationItem(FeatureDestination.FINANCE, "Cash and bank", "Balances, expenses, and transfers"),
            NavigationItem(FeatureDestination.ACCOUNTS, "Salesmen", "Accounts, access, and PIN reset"),
            NavigationItem(FeatureDestination.RETURNS, "Returns", "Original-sale returns and refunds"),
            NavigationItem(FeatureDestination.STOCK_ADJUSTMENTS, "Stock adjustments", "Damage, loss, and corrections"),
            NavigationItem(FeatureDestination.REPORTS, "Reports", "Sales, stock, profit, and vendor reports"),
            NavigationItem(FeatureDestination.NOTIFICATIONS, "Notifications", "Low stock and business alerts"),
        )
        UserRole.SALESMAN -> listOf(
            NavigationItem(FeatureDestination.SALES, "New sale", "Enter selling price and complete a sale"),
            NavigationItem(FeatureDestination.PRODUCTS, "Products and stock", "View products and available stock"),
            NavigationItem(FeatureDestination.STOCK_ADJUSTMENTS, "Damage or loss", "Record an entry for Owner review"),
            NavigationItem(FeatureDestination.RETURNS, "Product return", "Return items from an original sale"),
            NavigationItem(FeatureDestination.REPORTS, "Reports", "Sales, returns, stock, and low-stock reports"),
            NavigationItem(FeatureDestination.NOTIFICATIONS, "Notifications", "Sales and business alerts"),
        )
    }.also { items ->
        check(items.all { canOpen(role, it.destination) })
    }
}

enum class FeatureDataSlice {
    ACCOUNTS,
    PRODUCTS,
    PURCHASES,
    STOCK,
    SALES,
    RETURNS,
    VENDOR_FINANCE,
    FINANCE,
    REPORTS,
    NOTIFICATIONS,
}

/**
 * Keeps feature data work scoped to the visible destination. The dashboard needs its summary and
 * unread-notification count; vendor management is the only destination backed by two independent
 * feature repositories. All other destinations activate exactly one data slice.
 */
object FeatureActivationPolicy {
    fun requiredData(role: UserRole, destination: FeatureDestination?): Set<FeatureDataSlice> {
        if (destination != null && !NavigationPolicy.canOpen(role, destination)) return emptySet()
        return when (destination) {
            null -> buildSet {
                add(FeatureDataSlice.NOTIFICATIONS)
                if (role != UserRole.SUPER_ADMIN) add(FeatureDataSlice.REPORTS)
            }
            FeatureDestination.ACCOUNTS -> setOf(FeatureDataSlice.ACCOUNTS)
            FeatureDestination.PRODUCTS -> setOf(FeatureDataSlice.PRODUCTS)
            FeatureDestination.VENDORS -> setOf(
                FeatureDataSlice.PURCHASES,
                FeatureDataSlice.VENDOR_FINANCE,
            )
            FeatureDestination.STOCK_ADJUSTMENTS -> setOf(FeatureDataSlice.STOCK)
            FeatureDestination.SALES -> setOf(FeatureDataSlice.SALES)
            FeatureDestination.RETURNS -> setOf(FeatureDataSlice.RETURNS)
            FeatureDestination.FINANCE -> setOf(FeatureDataSlice.FINANCE)
            FeatureDestination.REPORTS -> setOf(FeatureDataSlice.REPORTS)
            FeatureDestination.NOTIFICATIONS -> setOf(FeatureDataSlice.NOTIFICATIONS)
        }
    }
}

enum class ExternalNavigationDecision { REJECT }

/** PIN-only release: there is no browser callback or externally reachable app route. */
object ExternalNavigationPolicy {
    fun decide(uri: String?): ExternalNavigationDecision = ExternalNavigationDecision.REJECT
}

fun FeatureDestination.title(): String = when (this) {
    FeatureDestination.ACCOUNTS -> "Accounts and shops"
    FeatureDestination.PRODUCTS -> "Products and stock"
    FeatureDestination.SALES -> "Sales"
    FeatureDestination.RETURNS -> "Returns"
    FeatureDestination.VENDORS -> "Vendors"
    FeatureDestination.FINANCE -> "Cash and bank"
    FeatureDestination.REPORTS -> "Reports"
    FeatureDestination.NOTIFICATIONS -> "Notifications"
    FeatureDestination.STOCK_ADJUSTMENTS -> "Stock adjustments"
}
