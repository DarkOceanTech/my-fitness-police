package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.darkoceantech.myfitnesspolice.data.WorkoutSet
import com.darkoceantech.myfitnesspolice.ui.theme.PoliceColors

/** Virtual pages allow either direction to wrap without changing the exercise. */
@Composable
internal fun SetDetailsPager(sets: List<WorkoutSet>, initialSetId: String, enabled: Boolean, modifier: Modifier,
    tagPrefix: String, onSelect: (String) -> Unit,
    onNavigationLocked: (Boolean) -> Unit = {},
    content: @Composable (WorkoutSet, (Boolean) -> Unit) -> Unit) {
    if (sets.isEmpty()) return
    val start = remember { Int.MAX_VALUE / 2 - (Int.MAX_VALUE / 2) % sets.size + sets.indexOfFirst { it.id == initialSetId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = if (sets.size == 1) 0 else start) { if (sets.size == 1) 1 else Int.MAX_VALUE }
    var locked by remember { mutableStateOf(false) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(pager.settledPage) { currentOnSelect(sets[pager.settledPage % sets.size].id) }
    Column(modifier.fillMaxSize()) {
        Text("Set ${pager.currentPage % sets.size + 1} of ${sets.size}" + if (sets.size > 1) " · Swipe left or right" else "",
            Modifier.fillMaxWidth().padding(top = 8.dp).testTag("$tagPrefix-set-position"), textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelMedium, color = PoliceColors.Muted)
        HorizontalPager(pager, modifier = Modifier.weight(1f).fillMaxWidth().testTag("$tagPrefix-set-pager"),
            userScrollEnabled = enabled && !locked, pageSpacing = 12.dp) { page ->
            // Preloaded neighboring pages must not expose off-screen edit controls to accessibility.
            Box(Modifier.fillMaxSize().then(if (page == pager.currentPage) Modifier else Modifier.clearAndSetSemantics { })) {
                content(sets[page % sets.size]) { value ->
                    if (page == pager.currentPage) { locked = value; onNavigationLocked(value) }
                }
            }
        }
    }
}
