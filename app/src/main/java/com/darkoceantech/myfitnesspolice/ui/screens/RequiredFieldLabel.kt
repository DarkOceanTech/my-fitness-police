package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

/** The spoken label states required explicitly, independent of punctuation or color settings. */
@Composable
internal fun RequiredFieldLabel(label: String, required: Boolean = true) {
    Text(if (required) "$label *" else label,
        modifier = Modifier.semantics { contentDescription = if (required) "$label, required" else label })
}
@Composable
internal fun RequiredFieldsHint() {
    Text("* Required", style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
}