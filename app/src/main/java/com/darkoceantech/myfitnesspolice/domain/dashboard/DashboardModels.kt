package com.darkoceantech.myfitnesspolice.domain.dashboard

import java.time.LocalDate

data class DashboardSet(val id: String, val exerciseId: String, val exerciseName: String,
    val muscle: String, val order: Int, val completedAt: Long, val reps: Int, val pounds: Double,
    val warmup: Boolean, val activeMillis: Long, val breakMillis: Long)
data class DashboardSession(val id: String, val title: String, val startedAt: Long, val finishedAt: Long?,
    val planId: String?, val groupId: String?, val sets: List<DashboardSet>, val isTrainingPlan: Boolean = planId != null)
data class DashboardPlan(val id: String, val name: String, val weekday: Int?, val createdAt: Long,
    val sets: Int, val reps: Long)
data class DashboardSchedule(val planId: String, val date: LocalDate)
data class DashboardInput(val sessions: List<DashboardSession>, val plans: List<DashboardPlan>,
    val schedule: List<DashboardSchedule> = emptyList())
data class DayLaunchItem(val id: String, val title: String, val detail: String, val recorded: Boolean)
data class CalendarDay(val date: LocalDate, val scheduled: Boolean, val recorded: Boolean)
enum class ChartKind { LINE, STACKED_BAR, COMBO, DONUT, DUAL_LINE, BAR, SCATTER, GAUGE, RADAR }
data class ChartSeries(val name: String, val values: List<Double?>)
data class DashboardChart(val id: String, val title: String, val headline: String, val caption: String,
    val kind: ChartKind, val labels: List<String>, val series: List<ChartSeries>, val unit: String,
    val rightUnit: String? = null, val emptyReason: String? = null, val reference: Double? = null)
data class LiftChoice(val id: String, val name: String)
data class DashboardReport(val today: LocalDate, val selectedDay: LocalDate, val calendar: List<CalendarDay>,
    val launches: List<DayLaunchItem>, val weekly: List<DashboardChart>, val daily: List<DashboardChart>,
    val monthly: List<DashboardChart>, val dailyLifts: List<LiftChoice>, val monthlyLifts: List<LiftChoice>,
    val dailyLiftId: String?, val monthlyLiftId: String?)
