package com.gdad.bags.desktop

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import com.gdad.bags.domain.model.MoneyAmounts
import com.gdad.bags.domain.model.UserRole
import com.gdad.bags.domain.model.UserSession
import com.gdad.bags.domain.report.BusinessReport

private val GdadColors = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF8B4513),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFFFFF8F4),
    surface = Color(0xFFFFF8F4),
)

fun main() = application {
    val controller = remember { DesktopController(DesktopDependencies(DesktopConfig.load())) }
    DisposableEffect(Unit) { onDispose(controller::close) }
    Window(
        onCloseRequest = ::exitApplication,
        title = "GDAD BAGS",
        state = WindowState(size = DpSize(1280.dp, 820.dp)),
    ) {
        val state by controller.state.collectAsState()
        MaterialTheme(colorScheme = GdadColors) {
            Surface(Modifier.fillMaxSize()) {
                when {
                    state.isInitializing -> LoadingScreen()
                    state.session == null -> LoginScreen(state, controller)
                    else -> AuthenticatedDesktop(state, controller, checkNotNull(state.session))
                }
            }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Checking secure session…")
        }
    }
}

@Composable
private fun LoginScreen(state: DesktopUiState, controller: DesktopController) {
    var loginId by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    val canSubmit = loginId.isNotBlank() && pin.length in 4..8 && pin.all(Char::isDigit) &&
        !state.isBusy && state.configurationError == null

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.width(480.dp)) {
            Column(
                Modifier.padding(32.dp).onPreviewKeyEvent {
                    if (it.key == Key.Enter && canSubmit) {
                        controller.login(loginId, pin)
                        true
                    } else {
                        false
                    }
                },
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("GDAD BAGS", fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("Windows desktop • Production account login")
                state.configurationError?.let {
                    StatusCard("Desktop setup is incomplete. Contact the administrator.", isError = true)
                }
                state.errorMessage?.let { StatusCard(it, isError = true) }
                state.statusMessage?.let { StatusCard(it, isError = false) }
                OutlinedTextField(
                    value = loginId,
                    onValueChange = {
                        loginId = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch in "._-" }
                        controller.clearMessage()
                    },
                    label = { Text("User ID") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = {
                        pin = it.filter(Char::isDigit).take(8)
                        controller.clearMessage()
                    },
                    label = { Text("PIN") },
                    supportingText = { Text("Enter your existing 4–8 digit account PIN.") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { controller.login(loginId, pin) },
                    enabled = canSubmit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (state.isBusy) CircularProgressIndicator(Modifier.height(20.dp))
                    else Text("Sign in")
                }
                Text(
                    "For security, this first desktop build signs out when the application closes.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedDesktop(
    state: DesktopUiState,
    controller: DesktopController,
    session: UserSession,
) {
    Row(Modifier.fillMaxSize()) {
        NavigationRail(Modifier.fillMaxHeight().width(250.dp)) {
            Text(
                "GDAD BAGS",
                modifier = Modifier.padding(20.dp),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
            )
            HorizontalDivider()
            featuresFor(session.role).forEach { feature ->
                NavigationRailItem(
                    selected = state.selectedFeature == feature,
                    onClick = { controller.select(feature) },
                    icon = { Text(feature.title.take(1), fontWeight = FontWeight.Bold) },
                    label = { Text(feature.title) },
                    alwaysShowLabel = true,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = controller::logout, enabled = !state.isBusy) {
                Text("Log out")
            }
            Spacer(Modifier.height(16.dp))
        }
        Column(Modifier.fillMaxSize().padding(28.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(
                        state.selectedFeature.title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text("${session.displayName} • ${session.role.displayName()}")
                }
                Spacer(Modifier.weight(1f))
                if (state.selectedFeature == DesktopFeature.DASHBOARD && session.shopId != null) {
                    OutlinedButton(onClick = controller::refreshDashboard, enabled = !state.isBusy) {
                        Text("Refresh")
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            state.errorMessage?.let { StatusCard(it, isError = true) }
            state.statusMessage?.let { StatusCard(it, isError = false) }
            Spacer(Modifier.height(12.dp))
            if (state.isBusy) CircularProgressIndicator()
            when (state.selectedFeature) {
                DesktopFeature.DASHBOARD -> DashboardContent(state.dashboard)
                DesktopFeature.PRODUCTS -> DesktopProductsContent(session, state, controller)
                DesktopFeature.SALES -> DesktopSalesContent(session, state, controller)
                DesktopFeature.PURCHASES -> DesktopPurchasesContent(state, controller)
                DesktopFeature.VENDORS -> DesktopVendorsContent(state, controller)
                else -> PendingFeature(state.selectedFeature)
            }
        }
    }
}

@Composable
private fun DashboardContent(report: BusinessReport?) {
    if (report == null) {
        StatusCard("Dashboard data is not available for this role yet.", isError = false)
        return
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MetricCard("Today's sales", MoneyAmounts.formatNpr(report.salesPaisa))
            MetricCard("Net sales", MoneyAmounts.formatNpr(report.netSalesPaisa))
            MetricCard("Low stock", report.lowStockCount.toString())
        }
        if (report.role == UserRole.OWNER) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                MetricCard("Gross profit", MoneyAmounts.formatNpr(report.grossProfitPaisa ?: 0))
                MetricCard("Vendor due", MoneyAmounts.formatNpr(report.vendorDueTotalPaisa ?: 0))
                MetricCard(
                    "Cash and bank",
                    MoneyAmounts.formatNpr(report.accountBalances.sumOf { it.balancePaisa }),
                )
            }
        }
        Text("Stock on hand: ${report.stockQuantity}")
        Text("Trusted report date: ${report.dateFrom}")
    }
}

@Composable
private fun MetricCard(label: String, value: String) {
    Card(Modifier.width(250.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PendingFeature(feature: DesktopFeature) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(24.dp)) {
            Text(feature.title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "This workflow remains available in the Android app and is being ported to the " +
                    "Windows keyboard-and-mouse layout. No placeholder action can change production data.",
            )
        }
    }
}

@Composable
private fun StatusCard(message: String, isError: Boolean) {
    Card(Modifier.fillMaxWidth()) {
        Text(
            message,
            modifier = Modifier.padding(14.dp),
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        )
    }
}

private fun UserRole.displayName(): String = when (this) {
    UserRole.SUPER_ADMIN -> "Super Admin"
    UserRole.OWNER -> "Owner"
    UserRole.SALESMAN -> "Salesman"
}
