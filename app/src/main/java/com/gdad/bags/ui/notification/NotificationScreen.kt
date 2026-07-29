package com.gdad.bags.ui.notification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.notification.AppNotification
import com.gdad.bags.ui.components.ContentState
import com.gdad.bags.ui.components.ContentStateHost
import com.gdad.bags.ui.navigation.FeatureDestination
import com.gdad.bags.ui.navigation.NavigationPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun NotificationScreen(
    session: UserSession,
    state: NotificationUiState,
    onRefresh: () -> Unit,
    onCategory: (String?) -> Unit,
    onSelect: (String) -> Unit,
    onCloseDetail: () -> Unit,
    onOpenRelated: (FeatureDestination) -> Unit,
) {
    val center = (state.content as? ContentState.Ready)?.value
    val selected = state.selectedId?.let { id -> center?.items?.firstOrNull { it.id == id } }
    if (selected != null) {
        NotificationDetail(
            session,
            selected,
            state.safeMessage,
            onCloseDetail,
            onOpenRelated,
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Notifications are retained for a rolling 90-day window.")
        state.safeMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.isRefreshing) LinearProgressIndicator(Modifier.fillMaxWidth())
        ContentStateHost(state.content, onRefresh) { ready ->
            val categories = ready.items.map { it.category }.distinct().sorted()
            val filtered = ready.items.filter { state.category == null || it.category == state.category }
            Column(Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "${ready.unreadCount} unread",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    TextButton(onClick = onRefresh, enabled = !state.isRefreshing) {
                        Text("Refresh")
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    item {
                        FilterButton("All", state.category == null) { onCategory(null) }
                    }
                    items(categories, key = { "category-$it" }) { category ->
                        FilterButton(category.human(), state.category == category) {
                            onCategory(category)
                        }
                    }
                }
                if (filtered.isEmpty()) {
                    Text("No notifications in this category.")
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filtered, key = { "notification-${it.id}" }) { notification ->
                            Card(
                                onClick = { onSelect(notification.id) },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(notification.title, fontWeight = FontWeight.Bold)
                                        if (!notification.isRead) Badge { Text("Unread") }
                                    }
                                    Text(notification.category.human())
                                    Text(notification.body, maxLines = 2)
                                    Text(
                                        notification.createdLabel(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) Button(onClick = onClick) { Text(label) }
    else OutlinedButton(onClick = onClick) { Text(label) }
}

@Composable
private fun NotificationDetail(
    session: UserSession,
    notification: AppNotification,
    safeMessage: String?,
    onBack: () -> Unit,
    onOpenRelated: (FeatureDestination) -> Unit,
) {
    val destination = notification.destination()
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        TextButton(onClick = onBack) { Text("Back to notifications") }
        Text(notification.category.human(), color = MaterialTheme.colorScheme.primary)
        Text(notification.title, style = MaterialTheme.typography.headlineSmall)
        Text(notification.body)
        Text(notification.createdLabel(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("This notification expires after the 90-day rolling history window.")
        safeMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (destination != null && NavigationPolicy.canOpen(session.role, destination)) {
            Button(onClick = { onOpenRelated(destination) }) { Text("Open related record") }
        }
    }
}

private fun AppNotification.destination(): FeatureDestination? = when (recordType) {
    "product" -> FeatureDestination.PRODUCTS
    "sale", "sale_return" -> FeatureDestination.RETURNS
    "purchase_bill", "purchase_receipt", "vendor", "vendor_return" -> FeatureDestination.VENDORS
    "expense", "journal_transaction" -> FeatureDestination.FINANCE
    "user_profile" -> FeatureDestination.ACCOUNTS
    else -> null
}

private fun AppNotification.createdLabel(): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
    .withZone(ZoneId.of("Asia/Kathmandu"))
    .format(Instant.ofEpochMilli(createdAtEpochMillis)) + " Nepal time"

private fun String.human() = lowercase().replace('_', ' ').replaceFirstChar(Char::uppercase)
