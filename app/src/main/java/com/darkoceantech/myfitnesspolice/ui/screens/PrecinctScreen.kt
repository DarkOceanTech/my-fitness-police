package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.R
import com.darkoceantech.myfitnesspolice.ui.theme.*

enum class PrecinctCategory(val title: String, val tag: String) {
    SUPPORT("Support", "support"),
}

enum class PrecinctFeature(val title: String, val description: String, val icon: Int, val tag: String,
    val constructionMessage: String, val category: PrecinctCategory) {
    CONTACT("Contact", "Call for a little backup.", R.drawable.ic_support, "contact",
        "Dispatch is untangling the radio cables. The contact desk is still being assembled. Please hold your donuts.", PrecinctCategory.SUPPORT),
    SUPPORT_REQUEST("New Request", "Report an issue or request a feature.", R.drawable.ic_support_ticket, "support-request",
        "The paperwork patrol is still assembling the filing cabinet. No tickets issued yet — not even for excessive reps.", PrecinctCategory.SUPPORT),
    COMMUNITY("Community", "Meet your fellow fitness officers.", R.drawable.ic_community, "community",
        "The squad is still putting chairs in the briefing room. Community roll call starts once someone locates the donuts.", PrecinctCategory.SUPPORT),
}

@Composable
fun PrecinctRoute(selected: PrecinctFeature?, onSelect: (PrecinctFeature?) -> Unit,
    modifier: Modifier = Modifier) {
    PrecinctScreen(onSelect = onSelect, modifier = modifier)
    if (selected != null) FeatureConstructionDialog(selected.title, selected.icon, selected.constructionMessage,
        homeTitle = "Precinct", tagPrefix = "precinct", onDismiss = { onSelect(null) })
}

@Composable
private fun PrecinctScreen(onSelect: (PrecinctFeature) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().testTag("precinct-home")) {
        SectionPageHeader("Precinct")
        LazyVerticalGrid(columns = GridCells.Adaptive(152.dp), modifier = Modifier.weight(1f).testTag("precinct-grid"),
            contentPadding = PaddingValues(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Support and community", style = MaterialTheme.typography.titleMedium,
                        color = PoliceColors.LightBlue, modifier = Modifier.semantics { heading() })
                    Text("Call for backup and gear up.", style = MaterialTheme.typography.bodyMedium, color = PoliceColors.Muted)
                }
            }
            PrecinctCategory.entries.forEach { category ->
                item(key = "category-${category.tag}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(category.title, style = MaterialTheme.typography.titleLarge, color = PoliceColors.LightBlue,
                        modifier = Modifier.padding(top = 8.dp).testTag("precinct-category-${category.tag}").semantics { heading() })
                }
                items(PrecinctFeature.entries.filter { it.category == category }, key = { it.tag }) { feature ->
                    SectionLaunchCard(feature.title, feature.description, feature.icon, "precinct-${feature.tag}-tile") { onSelect(feature) }
                }
            }
        }
    }
}
