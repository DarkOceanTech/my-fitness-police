package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myfitnesspolice.R
import com.example.myfitnesspolice.ui.theme.*

@Composable
internal fun SectionPageHeader(
    title: String,
    location: String? = null,
    onBack: (() -> Unit)? = null,
    backLabel: String = "Back",
    enabled: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(Modifier.fillMaxWidth().padding(start = if (onBack == null) 20.dp else 4.dp, end = 8.dp, top = 8.dp)
        .heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_back), contentDescription = backLabel)
        }
        Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
            SirenRule(Modifier.width(56.dp).padding(top = 8.dp))
            if (location != null) Text(location, style = MaterialTheme.typography.labelMedium,
                color = PoliceColors.Muted, modifier = Modifier.padding(top = 8.dp))
        }
        actions()
    }
}

@Composable
fun SectionLandingScreen(
    title: String, tileTitle: String, description: String, icon: Int, tag: String,
    onOpen: () -> Unit, modifier: Modifier = Modifier, subtitle: String? = null,
) {
    Column(modifier.fillMaxSize().testTag("$tag-home")) {
        SectionPageHeader(title, location = subtitle)
        LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = Modifier.weight(1f).testTag("$tag-grid"),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                SectionLaunchCard(tileTitle, description, icon, "$tag-tile", onOpen)
            }
        }
    }
}

@Composable
internal fun SectionLaunchCard(title: String, description: String, icon: Int, tag: String, onOpen: () -> Unit) {
    Surface(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp).testTag(tag),
        shape = PoliceCardShape, color = PoliceColors.Card, border = BorderStroke(1.dp, PoliceColors.Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                Box(Modifier.size(44.dp).background(PoliceColors.Raised, RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center) {
                    Icon(painterResource(icon), null, Modifier.size(26.dp), tint = PoliceColors.LightBlue)
                }
                Text("→", color = PoliceColors.LightBlue, fontSize = 20.sp)
            }
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = PoliceColors.Muted)
            SirenRule(Modifier.width(40.dp))
        }
    }
}
