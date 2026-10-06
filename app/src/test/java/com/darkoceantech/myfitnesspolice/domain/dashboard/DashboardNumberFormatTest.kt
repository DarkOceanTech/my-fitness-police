package com.darkoceantech.myfitnesspolice.domain.dashboard

import org.junit.Assert.*
import org.junit.Test

class DashboardNumberFormatTest {
    @Test fun formatsWeightAxesButKeepsSecondaryRepsAndRatiosAsNumbers() {
        val chart = DashboardChart("fatigue", "Fatigue", "", "", ChartKind.DUAL_LINE, emptyList(), emptyList(), "Lbs", rightUnit = "Reps")
        assertEquals("1,235", chart.displayNumber(1234.5))
        assertEquals(DashboardMetricsCalculator.number(12.5), chart.displayNumber(12.5, 1))
        assertEquals("1,235", chart.copy(unit = "Lbs-reps", rightUnit = null).displayNumber(1234.5))
        assertEquals(DashboardMetricsCalculator.number(1.25), chart.copy(unit = "minutes").displayNumber(1.25))
    }
}
