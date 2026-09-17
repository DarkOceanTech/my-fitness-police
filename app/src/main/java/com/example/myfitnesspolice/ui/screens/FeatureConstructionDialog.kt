package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.ui.theme.*

@Composable
internal fun FeatureConstructionDialog(title: String, icon: Int, message: String, homeTitle: String,
    tagPrefix: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.widthIn(max = 560.dp).fillMaxWidth().safeDrawingPadding().padding(20.dp)
            .heightIn(max = 760.dp).testTag("$tagPrefix-construction"), shape = PoliceCardShape,
            color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(painterResource(icon), null, Modifier.size(28.dp), tint = PoliceColors.LightBlue)
                    Text(title, Modifier.weight(1f).testTag("$tagPrefix-construction-title").semantics { heading() },
                        style = MaterialTheme.typography.titleLarge)
                }
                SirenRule(Modifier.fillMaxWidth())
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).testTag("$tagPrefix-construction-content"),
                    verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Image(painterResource(R.drawable.armory_under_construction),
                        contentDescription = "A sheepish officer in a hard hat repairs a dumbbell behind a tiny construction barrier.",
                        contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().aspectRatio(1.5f)
                            .clip(PoliceCardShape).testTag("$tagPrefix-construction-image"))
                    Text("HOLD IT RIGHT THERE!", style = MaterialTheme.typography.headlineSmall, color = PoliceColors.Text)
                    Text("UNDER CONSTRUCTION", style = MaterialTheme.typography.labelLarge,
                        color = PoliceColors.LightBlue, letterSpacing = 1.sp)
                    Text(message, style = MaterialTheme.typography.bodyLarge, color = PoliceColors.Text)
                    Text("No citation. Just construction. Our crew has this page surrounded.",
                        style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
                PoliceButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().testTag("$tagPrefix-construction-dismiss")) {
                    Text("Back to $homeTitle")
                }
            }
        }
    }
}
