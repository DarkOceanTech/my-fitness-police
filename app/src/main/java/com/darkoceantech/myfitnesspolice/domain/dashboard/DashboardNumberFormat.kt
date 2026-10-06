package com.darkoceantech.myfitnesspolice.domain.dashboard

import com.darkoceantech.myfitnesspolice.domain.formatting.formatWeight

fun DashboardChart.displayNumber(value: Double, seriesIndex: Int = 0): String =
    if (unit in listOf("Lbs", "Lbs-reps") && (rightUnit == null || seriesIndex == 0)) formatWeight(value)
    else DashboardMetricsCalculator.number(value)
