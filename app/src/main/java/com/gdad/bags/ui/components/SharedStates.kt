package com.gdad.bags.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data class Empty(val message: String) : ContentState<Nothing>
    data class Error(val safeMessage: String) : ContentState<Nothing>
    data class Ready<T>(val value: T) : ContentState<T>
}

@Composable
fun <T> ContentStateHost(
    state: ContentState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when (state) {
        ContentState.Loading -> StateColumn("Loading state") {
            CircularProgressIndicator(Modifier.semantics { contentDescription = "Loading" })
            Text("Loading…")
        }
        is ContentState.Empty -> StateColumn("Empty state") {
            Text(state.message)
            Button(onClick = onRetry) { Text("Refresh") }
        }
        is ContentState.Error -> StateColumn("Error state") {
            Text(state.safeMessage, color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text("Retry") }
        }
        is ContentState.Ready -> content(state.value)
    }
}

@Composable
private fun StateColumn(label: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp).semantics { contentDescription = label },
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) { content() }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
