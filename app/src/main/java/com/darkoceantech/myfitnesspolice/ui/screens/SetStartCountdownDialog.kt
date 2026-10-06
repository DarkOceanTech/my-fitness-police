package com.darkoceantech.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darkoceantech.myfitnesspolice.ui.theme.*
import kotlinx.coroutines.delay

@Composable
internal fun SetStartCountdownDialog(exerciseName: String, setNumber: Int, action: SessionAction,
    onCancel: () -> Unit, onStart: () -> Unit) {
    var seconds by remember { mutableIntStateOf(5) }
    var attempt by remember { mutableIntStateOf(0) }
    var starting by remember { mutableStateOf(false) }
    val start by rememberUpdatedState(onStart)
    val cancel by rememberUpdatedState(onCancel)
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        // Leaving the foreground cancels preparation; it must never start a set in the background.
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE && !starting) cancel()
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(attempt) {
        seconds = 5; starting = false
        repeat(5) { delay(1000); seconds-- }
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            starting = true
            start()
        } else cancel()
    }
    AlertDialog(onDismissRequest = { if (!action.saving) onCancel() }, containerColor = PoliceColors.Card,
        modifier = Modifier.testTag("set-start-countdown"), title = { Text("Start countdown") },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("$exerciseName · Set $setNumber", color = PoliceColors.LightBlue)
                Text(if (starting) "Go!" else seconds.toString(), style = StatTypography.copy(fontSize = 56.sp),
                    color = PoliceColors.Red, modifier = Modifier.testTag("countdown-seconds"))
                Text("Get into position. Your set timer starts after five seconds.", style = MaterialTheme.typography.bodyMedium)
                action.error?.let { Text(it, color = PoliceColors.Error) }
            }
        }, confirmButton = {
            if (starting && action.error != null) TextButton(onClick = { attempt++ }, enabled = !action.saving) { Text("Try again") }
        }, dismissButton = {
            TextButton(onClick = onCancel, enabled = !action.saving,
                modifier = Modifier.testTag("cancel-set-countdown")) { Text("Cancel") }
        })
}
