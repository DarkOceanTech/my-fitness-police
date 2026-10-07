package com.darkoceantech.myfitnesspolice

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.darkoceantech.myfitnesspolice.ui.screens.*
import com.darkoceantech.myfitnesspolice.ui.theme.*

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
fun MyFitnessPoliceApp(repository: com.darkoceantech.myfitnesspolice.data.FitnessRepository) {
    val factory = remember(repository) { FitnessViewModelFactory(repository) }
    val sessionModel: SessionViewModel = viewModel(factory = factory)
    val exercisesModel: ExercisesViewModel = viewModel(factory = factory)
    val dashboardModel: DashboardViewModel = viewModel(factory = factory)
    val stretchModel: StretchViewModel = viewModel(factory = factory)
    val mobility by stretchModel.state.collectAsStateWithLifecycle()
    val mobilitySession = mobility.sessions.firstOrNull { it.endedAt == null }
    MobilityTimerCues(stretchModel, mobility.preferences)
    var requestedMobilitySession by rememberSaveable { mutableStateOf<String?>(null) }
    var launchPlanId by rememberSaveable { mutableStateOf<String?>(null) }
    var dispatchDay by rememberSaveable { mutableStateOf(java.time.LocalDate.now().toString()) }
    var historyFromLaunchpad by rememberSaveable { mutableStateOf(false) }
    val sessions by sessionModel.state.collectAsStateWithLifecycle()
    val currentSession = sessions.sessions.firstOrNull { it.workout.kind == "session" && it.workout.finishedAt == null }
    var historySelection by rememberSaveable { mutableStateOf<String?>(null) }
    var historyOpen by rememberSaveable { mutableStateOf(false) }
    var historyGrouping by rememberSaveable { mutableStateOf(false) }
    var precinctSelection by rememberSaveable { mutableStateOf<PrecinctFeature?>(null) }
    var ptoSelection by rememberSaveable { mutableStateOf<PtoActivity?>(null) }
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.DISPATCH) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var trainingVisible by rememberSaveable { mutableStateOf(false) }
    val automaticallyFinished by sessionModel.automaticallyFinishedWorkout.collectAsStateWithLifecycle()
    LaunchedEffect(automaticallyFinished) {
        automaticallyFinished?.let { id ->
            // The active route retains its final summary, including the final rest time.
            if (!trainingVisible || currentDestination != AppDestinations.ACADEMY) {
                historyFromLaunchpad = false
                historySelection = id
                historyOpen = true
                settingsOpen = false
                currentDestination = AppDestinations.DOR
            }
            sessionModel.acknowledgeAutomaticFinish(id)
        }
    }
    fun selectDestination(destination: AppDestinations) {
        historyFromLaunchpad = false
        historyGrouping = false
        settingsOpen = false
        trainingVisible = false
        currentDestination = destination
    }
    fun returnToSession() {
        settingsOpen = false
        currentDestination = AppDestinations.ACADEMY
        trainingVisible = true
    }
    val landscapeSession = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE &&
        currentDestination == AppDestinations.ACADEMY && trainingVisible

    MyFitnessPoliceTheme {
        Scaffold(modifier = Modifier.fillMaxSize().policeBackdrop(), containerColor = Color.Transparent,
            contentColor = PoliceColors.Text) { innerPadding ->
            Box(Modifier.fillMaxSize().padding(innerPadding)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        val screenModifier = Modifier.fillMaxSize()
                        when (currentDestination) {
                            AppDestinations.DISPATCH -> {
                                if (settingsOpen) DemoSettingsScreen(screenModifier, onBack = { settingsOpen = false })
                                else DashboardRoute(dashboardModel, screenModifier, onSettings = { settingsOpen = true },
                                    selectedDay = java.time.LocalDate.parse(dispatchDay), onDayChanged = { dispatchDay = it.toString() },
                                    onPlan = { id -> launchPlanId = id; selectDestination(AppDestinations.ACADEMY) },
                                    onLog = { id -> historySelection = id; historyOpen = true; selectDestination(AppDestinations.DOR); historyFromLaunchpad = true })
                            }
                            AppDestinations.ACADEMY -> WorkoutHomeRoute(sessionModel, exercisesModel, screenModifier,
                                requestedPlanId = launchPlanId, requestedScheduleDate = dispatchDay, onPlanRequestHandled = { launchPlanId = null },
                                onReturnToLaunchpad = { selectDestination(AppDestinations.DISPATCH) },
                                trainingVisible = trainingVisible, onTrainingVisibleChange = { trainingVisible = it },
                                timedSessionActive = mobilitySession != null,
                                onFinished = { id ->
                                    historyFromLaunchpad = false
                                    trainingVisible = false
                                    historySelection = id
                                    historyOpen = true
                                    currentDestination = AppDestinations.DOR
                                })
                            AppDestinations.PTO -> if (ptoSelection == PtoActivity.TIMER) StretchTrackerRoute(stretchModel,
                                onBack = { ptoSelection = null }, requestedSession = requestedMobilitySession,
                                onRequestHandled = { requestedMobilitySession = null }, strengthActive = currentSession != null)
                            else PtoRoute(ptoSelection, onSelect = { ptoSelection = it }, modifier = screenModifier)
                            AppDestinations.DOR -> {
                                if (historyOpen) WorkoutHistoryScreen(sessionModel, historySelection, { historySelection = it }, screenModifier,
                                    onBack = { historyOpen = false; historySelection = null },
                                    selectedBackLabel = if (historyFromLaunchpad) "Back to Launchpad" else null,
                                    onSelectedBack = if (historyFromLaunchpad) ({
                                        historyOpen = false; historySelection = null; selectDestination(AppDestinations.DISPATCH)
                                    }) else null)
                                else ReportsScreen(onHistory = { historyOpen = true }, modifier = screenModifier,
                                    onGroupWorkouts = { sessionModel.clearError(); historyGrouping = true })
                            }
                            AppDestinations.PRECINCT -> PrecinctRoute(precinctSelection, onSelect = { precinctSelection = it }, modifier = screenModifier)
                        }
                    }
                    if (currentSession != null && !(currentDestination == AppDestinations.ACADEMY && trainingVisible)) {
                        CurrentSessionShortcut(currentSession, onOpen = ::returnToSession)
                    }
                    if (mobilitySession != null && !(currentDestination == AppDestinations.PTO && ptoSelection == PtoActivity.TIMER)) {
                        MobilitySessionShortcut(mobilitySession, stretchModel, onOpen = {
                            selectDestination(AppDestinations.PTO); ptoSelection = PtoActivity.TIMER; requestedMobilitySession = mobilitySession.id
                        })
                    }
                    if (!landscapeSession) PoliceBottomNavigation(currentDestination, ::selectDestination)
                }
                if (landscapeSession) LandscapeWorkoutNavigation(currentDestination, ::selectDestination,
                    Modifier.align(Alignment.BottomStart))
            }
        }
        if (historyGrouping) HistoryPlanGroupingDialog(sessionModel,
            onDismiss = { historyGrouping = false; sessionModel.clearError() },
            onGrouped = { historyGrouping = false; historyOpen = true; historySelection = null })
    }
}

enum class AppDestinations(val label: Int, val icon: Int) {
    DISPATCH(R.string.nav_dispatch, R.drawable.ic_dashboard),
    ACADEMY(R.string.nav_academy, R.drawable.ic_workout),
    PTO(R.string.nav_pto, R.drawable.ic_pto),
    DOR(R.string.nav_dor, R.drawable.ic_progress),
    PRECINCT(R.string.nav_precinct, R.drawable.ic_precinct),
}
