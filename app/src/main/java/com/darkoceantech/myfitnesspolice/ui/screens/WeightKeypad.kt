package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.darkoceantech.myfitnesspolice.ui.theme.*

/** A local draft: dismissing the sheet never commits a weight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WeightKeypad(value: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var draft by rememberSaveable { mutableStateOf(value) }
    var replaceOnDigit by rememberSaveable { mutableStateOf(true) }
    val valid = draft.toBigDecimalOrNull()?.let { it.signum() >= 0 } == true
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(save: Boolean) {
        scope.launch {
            sheetState.hide()
            if (save) onSave(draft.toBigDecimal().stripTrailingZeros().toPlainString())
            else onDismiss()
        }
    }
    fun handleKey(key: String) {
        when (key) {
            "⌫" -> { draft = draft.dropLast(1); replaceOnDigit = false }
            "." -> {
                if (replaceOnDigit || draft.isEmpty()) draft = "0."
                else if (!draft.contains('.')) draft += "."
                replaceOnDigit = false
            }
            else -> {
                val next = if (replaceOnDigit || draft == "0") key else draft + key
                // Pounds are entered to hundredths; storage remains integer grams.
                if (next.substringBefore('.').length <= 7 && next.substringAfter('.', "").length <= 2) draft = next
                replaceOnDigit = false
            }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState,
        containerColor = PoliceColors.Card, contentColor = PoliceColors.Text,
        modifier = Modifier.testTag("weight-keypad")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
            val wide = maxWidth > 600.dp
            val details: @Composable ColumnScope.() -> Unit = {
                Text("Weight (Lbs)", style = MaterialTheme.typography.titleLarge)
                Surface(color = PoliceColors.Background, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().testTag("weight-keypad-value").semantics(mergeDescendants = true) {}) {
                    Text(draft.ifEmpty { "—" }, Modifier.padding(16.dp), style = MaterialTheme.typography.headlineMedium)
                }
                Text("Use 0 for bodyweight. Up to 2 decimal places.", style = MaterialTheme.typography.bodySmall,
                    color = PoliceColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PoliceOutlinedButton(onClick = { close(false) }, modifier = Modifier.weight(1f).testTag("weight-keypad-cancel")) { Text("Cancel") }
                    PoliceButton(onClick = { close(true) }, enabled = valid,
                        modifier = Modifier.weight(1f).testTag("weight-keypad-save")) { Text("Save") }
                }
            }
            val keys: @Composable () -> Unit = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf(".", "0", "⌫")).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { key ->
                                OutlinedButton(onClick = { handleKey(key) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                    .testTag("weight-key-$key").semantics {
                                        contentDescription = when (key) { "⌫" -> "Backspace"; "." -> "Decimal point"; else -> key }
                                    }) { Text(key, style = MaterialTheme.typography.titleLarge) }
                            }
                        }
                    }
                }
            }
            if (wide) Row(Modifier.verticalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp), content = details)
                Box(Modifier.weight(1f)) { keys() }
            } else Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                details()
                keys()
            }
        }
    }
}
