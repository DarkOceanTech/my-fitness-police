package com.example.myfitnesspolice.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.Alignment
import com.example.myfitnesspolice.ui.theme.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.myfitnesspolice.data.Exercise
import com.example.myfitnesspolice.data.Workout
import java.text.DateFormat
import java.util.Date

@Composable
fun WorkoutScreen(state: ListState<Workout>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    WorkoutList("Workout", state, "No workout in progress",
        "Workout logging is coming next.", onRetry, modifier)
}

@Composable
fun HistoryScreen(state: ListState<Workout>, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    WorkoutList("History", state, "No completed workouts yet",
        "Your completed sessions will appear here.", onRetry, modifier)
}

@Composable
private fun WorkoutList(
    title: String, state: ListState<Workout>, emptyTitle: String,
    emptyMessage: String, onRetry: () -> Unit, modifier: Modifier,
) {
    LazyColumn(modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { ScreenTitle(title) }
        when (state) {
            ListState.Loading -> item { CircularProgressIndicator() }
            ListState.Failed -> item { LoadError(onRetry) }
            is ListState.Ready -> {
                if (state.items.isEmpty()) item {
                    Text(emptyTitle, style = MaterialTheme.typography.titleLarge)
                    Text(emptyMessage)
                }
                items(state.items, key = { it.id }) { workout ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                                .format(Date(workout.startedAt)), style = MaterialTheme.typography.titleMedium)
                            Text(if (workout.finishedAt == null) "In progress" else "Completed")
                            if (workout.notes.isNotBlank()) Text(workout.notes)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenTitle(title: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineLarge,
            modifier = Modifier.semantics { heading() })
        SirenRule(Modifier.width(56.dp))
    }
}

@Composable
private fun LoadError(onRetry: () -> Unit) {
    Column {
        Text("Could not load your data.")
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

