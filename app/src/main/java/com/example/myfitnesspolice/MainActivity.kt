package com.example.myfitnesspolice

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.example.myfitnesspolice.ui.theme.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.remember
import com.example.myfitnesspolice.ui.screens.*
import com.example.myfitnesspolice.ui.theme.MyFitnessPoliceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        setContent {
            MyFitnessPoliceTheme {
                MyFitnessPoliceApp((application as FitnessApplication).repository)
            }
        }
    }
}

@Composable
fun MyFitnessPoliceApp(repository: com.example.myfitnesspolice.data.FitnessRepository) {
    val factory = remember(repository) { FitnessViewModelFactory(repository) }
    val sessionModel: SessionViewModel = viewModel(factory = factory)
    val exercisesModel: ExercisesViewModel = viewModel(factory = factory)
    var historySelection by rememberSaveable { mutableStateOf<String?>(null) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var armorySelection by rememberSaveable { mutableStateOf<ArmoryFeature?>(null) }
    var ptoSelection by rememberSaveable { mutableStateOf<PtoActivity?>(null) }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.DISPATCH) }

    MyFitnessPoliceTheme {
    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Icon(painterResource(it.icon), contentDescription = null,
                                tint = if (it == currentDestination) PoliceColors.Text else PoliceColors.Muted)
                            Box(Modifier.width(22.dp).height(2.dp).background(
                                if (it == currentDestination) PoliceColors.Red else PoliceColors.Blue.copy(alpha = .5f)))
                        }
                    },
                    label = { Text(stringResource(it.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                    selected = it == currentDestination,
                    onClick = {
                        if (it == AppDestinations.DOR) { historyOpen = false; historySelection = null }
                        if (it == AppDestinations.PTO) ptoSelection = null
                        if (it == AppDestinations.ARMORY) armorySelection = null
                        currentDestination = it
                    }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize().policeBackdrop(), containerColor = Color.Transparent) { innerPadding ->
            val screenModifier = Modifier.fillMaxSize().padding(innerPadding)
            when (currentDestination) {
                AppDestinations.DISPATCH -> {
                    val weekly by sessionModel.weeklyProgress.collectAsStateWithLifecycle()
                    DashboardScreen(weekly, screenModifier)
                }
                AppDestinations.ACADEMY -> {
                    WorkoutHomeRoute(sessionModel, exercisesModel, screenModifier, onFinished = { id ->
                        historySelection = id
                        historyOpen = true
                        currentDestination = AppDestinations.DOR
                    })
                }
                AppDestinations.PTO -> PtoRoute(ptoSelection, onSelect = { ptoSelection = it }, modifier = screenModifier)
                AppDestinations.DOR -> {
                    if (historyOpen) WorkoutHistoryScreen(sessionModel, historySelection, { historySelection = it }, screenModifier,
                        onBack = { historyOpen = false; historySelection = null })
                    else SectionLandingScreen("DOR", "Workout History",
                        "Review completed workouts, sets, reps, and notes.", R.drawable.ic_library, "progress-history",
                        onOpen = { historyOpen = true }, modifier = screenModifier, subtitle = "Daily Observation Report")
                }
                AppDestinations.ARMORY -> ArmoryRoute(armorySelection,
                    onSelect = { armorySelection = it }, modifier = screenModifier)
            }
        }
    }
}

}

enum class AppDestinations(
    val label: Int,
    val icon: Int,
) {
    DISPATCH(R.string.nav_dispatch, R.drawable.ic_dashboard),
    ACADEMY(R.string.nav_academy, R.drawable.ic_workout),
    PTO(R.string.nav_pto, R.drawable.ic_pto),
    DOR(R.string.nav_dor, R.drawable.ic_progress),
    ARMORY(R.string.nav_armory, R.drawable.ic_armory),
}
