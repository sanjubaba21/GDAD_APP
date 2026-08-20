package com.gdad.bags.ui.components

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import com.gdad.bags.domain.model.NepalDateTime

@Composable
fun BusinessDateField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Business date (Nepal) — YYYY-MM-DD",
) {
    val valid = NepalDateTime.isValidIsoDate(value)
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    OutlinedTextField(
        value = value,
        onValueChange = { candidate ->
            onValueChange(candidate.filter { it.isDigit() || it == '-' }.take(10))
        },
        label = { Text(label) },
        singleLine = true,
        isError = !valid,
        supportingText = {
            if (!valid) {
                Text(
                    "Use a real Nepal business date in YYYY-MM-DD format.",
                    Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
                )
            }
        },
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }),
        modifier = modifier,
    )
}
