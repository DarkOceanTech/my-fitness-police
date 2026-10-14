package com.darkoceantech.myfitnesspolice.domain.dashboard

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DashboardMetricsCalculatorTest {
    @Test fun intensityEightWeeksRespectBoundariesAndIncludeWarmupsOnlyInSummary() {
        val sunday = LocalDate.of(2026, 9, 27)
        val monday = sunday.plusDays(1)
        val input = listOf(
            session("old", day.minusYears(1), listOf(row("ref", day.minusYears(1), weight = 100.0, reps = 1))),
            session("sun", sunday, listOf(row("low", sunday, weight = 50.0, reps = 1))),
            session("mon", monday, listOf(row("zero", monday, weight = 0.0), row("warmup", monday, warmup = true))),
            session("warmups-only", day.minusWeeks(2), listOf(row("wu", day.minusWeeks(2), warmup = true))))
        for (start in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val chart = report(input, weekStartsOn = start).chart("weekly-intensity")
            assertEquals(ChartKind.HORIZONTAL_PERCENT_BAR, chart.kind)
            assertEquals(listOf("Low", "Moderate", "High", "Unknown"), chart.series.map { it.name })
            val current = startOfWeek(day, start)
            assertEquals((7 downTo 0).map { current.minusWeeks(it.toLong()).format(java.time.format.DateTimeFormatter.ofPattern("MM/dd")) }, chart.labels)
            assertEquals(if (start == DayOfWeek.SUNDAY) 3 else 2, chart.weeklySetTotals.last())
            assertEquals(if (start == DayOfWeek.SUNDAY) listOf(50.0, 0.0, 0.0, 50.0) else listOf(0.0, 0.0, 0.0, 100.0), chart.intensityPercentages(7))
            assertEquals(1, chart.weeklySetTotals[5])
            assertEquals(0.0, chart.intensityPercentages(5).sum(), 0.0)
            chart.labels.indices.forEach { week ->
                assertEquals(if (chart.weeklyTotal(week) > 0) 100.0 else 0.0, chart.intensityPercentages(week).sum(), 1e-10)
            }
            assertNull(chart.emptyReason)
        }
        val empty = report().chart("weekly-intensity")
        assertEquals(8, empty.labels.size)
        assertTrue(empty.weeklySetTotals.all { it == 0 })
        assertNull(empty.emptyReason)
        val newYear = report(selected = LocalDate.of(2026, 1, 1)).chart("weekly-intensity")
        assertEquals("12/29", newYear.labels.last())
        assertEquals("11/10", newYear.labels.first())
    }
    @Test fun densityKeepsSeparateFullPrecisionTimesAndNeverAddsRatioToDuration() {
        val chart = report(listOf(session("s", rows = listOf(
            row("a", active = 15_100, rest = 30_200), row("b", active = 20_300, rest = 10_400))))).chart("weekly-density")
        assertEquals("Training Density", chart.title)
        assertEquals(listOf("Work", "Rest", "Work / Rest"), chart.series.map { it.name })
        assertEquals(2, chart.barSeriesCount)
        assertEquals(35_400 / 60000.0, chart.series[0].values.last()!!, 1e-12)
        assertEquals(40_600 / 60000.0, chart.series[1].values.last()!!, 1e-12)
        assertEquals(35_400 / 40_600.0, chart.series[2].values.last()!!, 1e-12)
        assertEquals(76_000 / 60000.0, chart.weeklyTotal(7), 1e-12)
        assertEquals(0.0, chart.weeklyTotal(0), 1e-12)
        assertNull(chart.series[2].values.first())
    }
    @Test fun legacyBackAndNewCategoriesCountEveryWorkingSetOnlyOnce() {
        val chart = report(listOf(session("s", rows = listOf(
            row("a", muscle = "Back"), row("b", muscle = "Upper Back"),
            row("c", muscle = "Trapezius"), row("d", muscle = "Lower Back"),
            row("e", muscle = "Trapezius", warmup = true))))).chart("weekly-muscles")
        assertEquals(4.0, chart.series.sumOf { it.values.last()!! }, .001)
        assertEquals(2.0, chart.series.single { it.name == "Upper Back" }.values.last()!!, .001)
        assertEquals(1.0, chart.series.single { it.name == "Trapezius" }.values.last()!!, .001)
        assertEquals(1.0, chart.series.single { it.name == "Lower Back" }.values.last()!!, .001)
        assertEquals(0.0, chart.series.single { it.name == "Chest" }.values.last()!!, .001)
        assertFalse(chart.series.any { it.name == "Back" })
    }
    @Test fun weeklyDailyVolumeFollowsExistingWeekStartAndSumsToTotal() {
        val sunday = LocalDate.of(2026, 9, 27)
        val monday = sunday.plusDays(1)
        val sessions = listOf(
            session("sun", sunday, listOf(row("sun", sunday, weight = 12.5, reps = 3))),
            session("mon", monday, listOf(row("mon", monday, weight = 100.0, reps = 10),
                row("bad", monday, weight = Double.NaN))))
        for (start in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
            val chart = report(sessions, weekStartsOn = start).chart("weekly-volume")
            assertEquals(8, chart.weeklyDays.size)
            chart.weeklyDays.forEachIndexed { index, days ->
                assertEquals(7, days.size)
                assertEquals(start, days.first().date.dayOfWeek)
                assertEquals(chart.series.single().values[index]!!, days.sumOf { it.volume }, .001)
                days.zipWithNext().forEach { (a, b) -> assertEquals(a.date.plusDays(1), b.date) }
            }
            val current = chart.weeklyDays.last()
            assertEquals(if (start == DayOfWeek.SUNDAY) 1037.5 else 1000.0, current.sumOf { it.volume }, .001)
            assertEquals(0.0, current.last().volume, .001)
        }
    }
    @Test fun completedScheduledPlanBecomesOnlyALogAndOtherDatesStayScheduled() {
        val p = DashboardPlan("p", "Back patrol", null, time(day.minusDays(1)), 1, 10)
        val dates = listOf(DashboardSchedule("p", day), DashboardSchedule("p", day.plusDays(1)))
        val active = session("s", rows = listOf(row("a")), plan = "p", finished = false)
        assertEquals(listOf(false), report(listOf(active), listOf(p), schedule = dates).launches.map { it.recorded })
        val completed = active.copy(finishedAt = time(day, 14))
        val finishedReport = report(listOf(completed), listOf(p), schedule = dates)
        assertEquals(listOf(true), finishedReport.launches.map { it.recorded })
        assertFalse(finishedReport.calendar.single { it.date == day }.scheduled)
        assertTrue(finishedReport.calendar.single { it.date == day.plusDays(1) }.scheduled)
        assertEquals(100.0, finishedReport.chart("monthly-adherence").series.single().values.single()!!, .001)
        assertEquals(listOf(false), report(listOf(completed), listOf(p), selected = day.plusDays(1), schedule = dates)
            .launches.map { it.recorded })
        assertEquals(listOf(false, true), report(listOf(completed.copy(sets = emptyList())), listOf(p), schedule = dates)
            .launches.map { it.recorded })
    }
    private val zone = ZoneId.of("America/Los_Angeles")
    private val day = LocalDate.of(2026, 9, 30)
    private fun time(date: LocalDate, hour: Int = 12) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private fun row(id: String, date: LocalDate = day, weight: Double = 100.0, reps: Int = 10,
        warmup: Boolean = false, muscle: String = "Back", active: Long = 30_000, rest: Long = 60_000) =
        DashboardSet(id, "row", "Cable row", muscle, id.hashCode(), time(date) + id.hashCode().toLong().and(1023),
            reps, weight, warmup, active, rest)
    private fun session(id: String, date: LocalDate = day, rows: List<DashboardSet>, plan: String? = null,
        group: String? = null, finished: Boolean = true) = DashboardSession(id, "Back day", time(date),
            if (finished) time(date, 14) else null, plan, group, rows)
    private fun report(sessions: List<DashboardSession> = emptyList(), plans: List<DashboardPlan> = emptyList(),
        selected: LocalDate = day, schedule: List<DashboardSchedule> = emptyList(),
        weekStartsOn: DayOfWeek = DayOfWeek.MONDAY) = DashboardMetricsCalculator().calculate(DashboardInput(sessions, plans, schedule),
            time(day, 23), selected, dailyLift = "row", zone = zone, weekStartsOn = weekStartsOn)
    private fun DashboardReport.chart(id: String) = (weekly + daily + monthly).single { it.id == id }

    @Test fun volumeUsesActualRepsOncePerRowAndWorkingCountExcludesWarmups() {
        val r = report(listOf(session("s", rows = listOf(row("a", reps = 5), row("b", reps = 0),
            row("c", weight = 50.0, reps = 10, warmup = true), row("d", weight = 0.0, reps = 15, muscle = "Abdominals")))))
        assertEquals(1000.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(3.0, r.chart("weekly-muscles").series.sumOf { it.values.last()!! }, .001)
        assertEquals(listOf(1.0, 0.0, 1.0), r.chart("monthly-variety").series.single().values)
    }

    @Test fun weeklyIntensityKeepsExerciseSpecificThresholdsAndUnknownsWithoutUsingLaterSets() {
        val prior = day.minusDays(7)
        val current = listOf(row("low", weight = 59.0), row("medium", weight = 60.0),
            row("edge", weight = 85.0), row("high", weight = 86.0), row("futureBaseline", weight = 1000.0, reps = 1),
            row("bodyweight", weight = 0.0), row("other", weight = 80.0).copy(exerciseId = "other"),
            row("wu", weight = 10.0, warmup = true)).mapIndexed { index, set ->
                set.copy(completedAt = time(day) + (index + 1) * 1000L, order = index, reps = 1)
            }
        val r = report(listOf(session("prior", prior, listOf(row("base", prior, reps = 1))), session("today", rows = current)))
        // The prior-week baseline also appears in the selected week only when dates overlap;
        // here it is outside this week's donut and never supplies another exercise's ceiling.
        assertEquals(listOf(1.0, 2.0, 2.0, 2.0), r.chart("weekly-intensity").series.map { it.values.last() })
        assertEquals(100.0, r.chart("daily-intensity").reference!!, .001)
    }

    @Test fun weeklyIntensityUsesBestHistoricalEstimateOlderThan90DaysWithoutExtendingChartRange() {
        val old = day.minusDays(180)
        val recent = day.minusDays(7)
        val records = listOf(session("old", old, listOf(row("best", old, weight = 200.0, reps = 1))),
            session("recent", recent, listOf(row("recent", recent, weight = 100.0, reps = 1))),
            session("today", rows = listOf(row("current", weight = 150.0, reps = 10))))
        val r = report(records)
        assertEquals(listOf(0.0, 1.0, 0.0, 0.0), r.chart("weekly-intensity").series.map { it.values.last() })
        assertEquals(8, r.chart("weekly-volume").labels.size)
        assertEquals("8/10", r.chart("weekly-volume").labels.first())
        assertEquals("9/28", r.chart("weekly-volume").labels.last())
        assertEquals(1500.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(1600.0, r.chart("weekly-volume").series.single().values.filterNotNull().sum(), .001)
        // Scope stays Weekly: the existing daily reference contract and monthly window remain unchanged.
        assertEquals(100.0, r.chart("daily-intensity").reference!!, .001)
        assertEquals(30, r.chart("monthly-1rm").labels.size)
        assertTrue(DashboardInsights.forChart("weekly-intensity").limitations.contains("all recorded history"))
        assertFalse(DashboardInsights.forChart("weekly-intensity").limitations.contains("90 days"))
    }

    @Test fun sameDayEarlierSetsQualifyButCurrentSimultaneousAndLaterPerformancesDoNot() {
        val rows = listOf(row("first", weight = 100.0, reps = 1).copy(completedAt = time(day) + 1000),
            row("medium", weight = 70.0, reps = 1).copy(completedAt = time(day) + 2000),
            row("simultaneousPeak", weight = 1000.0, reps = 1).copy(completedAt = time(day) + 2000),
            row("afterPeak", weight = 70.0, reps = 1).copy(completedAt = time(day) + 3000))
        val r = report(listOf(session("same-day", rows = rows)))
        // First has no baseline, the simultaneous pair both use 100, the final set uses 1000.
        assertEquals(listOf(1.0, 1.0, 1.0, 1.0), r.chart("weekly-intensity").series.map { it.values.last() })
        assertNull(r.chart("daily-intensity").reference)
    }

    @Test fun invalidWarmupAndUnfinishedRecordsNeverEstablishWeeklyIntensityReferences() {
        val old = day.minusDays(180)
        val invalid = listOf(row("nan", old, weight = Double.NaN),
            row("positiveInfinity", old, weight = Double.POSITIVE_INFINITY),
            row("negativeInfinity", old, weight = Double.NEGATIVE_INFINITY),
            row("overflow", old, weight = Double.MAX_VALUE),
            row("negativeWeight", old, weight = -1000.0), row("zeroWeight", old, weight = 0.0),
            row("negativeReps", old, weight = 1000.0, reps = -1), row("zeroReps", old, weight = 1000.0, reps = 0),
            row("tooManyReps", old, weight = 1000.0, reps = 11), row("warmup", old, weight = 1000.0, warmup = true),
            row("unfinished", old, weight = 1000.0).copy(completedAt = 0),
            row("beforeSession", old, weight = 1000.0).copy(completedAt = time(old) - 1000))
        val afterNow = row("future", old, weight = 1000.0).copy(completedAt = time(day.plusDays(1)))
        val r = report(listOf(session("invalid", old, invalid + afterNow),
            session("current", rows = listOf(row("unknown", weight = 80.0)))))
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), r.chart("weekly-intensity").series.map { it.values.last() })
        assertEquals(800.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
    }

    @Test fun invalidCurrentWeeklyRecordsAreExcludedInsteadOfProducingInventedStatistics() {
        val rows = listOf(row("valid", weight = 100.0, reps = 1),
            row("nan", weight = Double.NaN), row("infinite", weight = Double.POSITIVE_INFINITY),
            row("overflow", weight = Double.MAX_VALUE),
            row("negative", weight = -1.0), row("negativeReps", reps = -1),
            row("unfinished").copy(completedAt = 0), row("negativeTiming", active = -1))
        val r = report(listOf(session("invalid-current", rows = rows)))
        assertEquals(listOf(0.0, 0.0, 0.0, 1.0), r.chart("weekly-intensity").series.map { it.values.last() })
        assertEquals(100.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(1.0, r.chart("weekly-muscles").series.sumOf { it.values.last()!! }, .001)
    }

    @Test fun epleyRejectsUnsupportedSetsAndUsesBestEligibleEstimatePerDay() {
        assertNull(DashboardMetricsCalculator.epley(100.0, 0))
        assertNull(DashboardMetricsCalculator.epley(100.0, 11))
        assertNull(DashboardMetricsCalculator.epley(0.0, 10))
        assertNull(DashboardMetricsCalculator.epley(Double.NaN, 5))
        assertNull(DashboardMetricsCalculator.epley(Double.POSITIVE_INFINITY, 1))
        assertNull(DashboardMetricsCalculator.epley(Double.MAX_VALUE, 10))
        assertEquals(100.0, DashboardMetricsCalculator.epley(100.0, 1)!!, .001)
        val r = report(listOf(session("s", rows = listOf(row("ten"), row("single", weight = 120.0, reps = 1),
            row("highreps", weight = 200.0, reps = 20), row("wu", weight = 300.0, warmup = true)))))
        assertEquals(133.33333, r.chart("monthly-1rm").series.single().values[29]!!, .001)
    }

    @Test fun adherenceMatchesPlanAndDateCountsDuplicatesOnceAndShowsValidZero() {
        val created = LocalDate.of(2026, 9, 23)
        val p = DashboardPlan("p", "Back plan", 3, time(created, 0), 1, 10)
        val dates = listOf(DashboardSchedule("p", created), DashboardSchedule("p", day))
        val zero = report(plans = listOf(p), schedule = dates).chart("monthly-adherence")
        assertEquals(0.0, zero.series.single().values.single()!!, .001)
        assertNull(zero.emptyReason)
        val r = report(listOf(session("one", rows = listOf(row("a")), plan = "p"),
            session("duplicate", rows = listOf(row("b")), plan = "p"),
            session("unscheduled", day.minusDays(1), listOf(row("c", day.minusDays(1))), plan = "p"),
            session("wrongPlan", rows = listOf(row("d")), plan = "other")), listOf(p), schedule = dates)
        assertEquals(50.0, r.chart("monthly-adherence").series.single().values.single()!!, .001)
        assertNull(report().chart("monthly-adherence").series.single().values.single())
    }

    @Test fun groupsProduceOneLaunchCardWithoutDuplicatingMetrics() {
        val r = report(listOf(session("one", rows = listOf(row("a")), plan = "p", group = "g"),
            session("two", rows = listOf(row("b")), plan = "p", group = "g")))
        assertEquals(1, r.launches.size)
        assertTrue(r.launches.single().detail.contains("2 sets"))
        assertEquals(2000.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
    }

    @Test fun calendarDisplaysCompleteLocalWeekAcrossDstWithoutRestrictingSelectedDates() {
        val monday = LocalDate.of(2026, 11, 2)
        val sunday = monday.minusDays(1)
        val records = listOf(session("sunday", sunday, listOf(row("s", sunday))),
            session("monday", monday, listOf(row("m", monday))))
        val r = DashboardMetricsCalculator().calculate(DashboardInput(records, emptyList()), time(monday, 23), monday, zone = zone)
        assertEquals(7, r.calendar.size)
        assertEquals(monday, r.calendar.first().date)
        assertEquals(monday.plusDays(6), r.calendar.last().date)
        assertEquals(listOf(1000.0, 1000.0), r.chart("weekly-volume").series.single().values.takeLast(2))
        assertEquals(day.plusMonths(1), report(selected = day.plusMonths(1)).selectedDay)
        assertEquals(day.minusYears(5), report(selected = day.minusYears(5)).selectedDay)
        val sundayFirst = DashboardMetricsCalculator().calculate(DashboardInput(records, emptyList()),
            time(monday, 23), monday, zone = zone, weekStartsOn = DayOfWeek.SUNDAY)
        assertEquals(sunday, sundayFirst.calendar.first().date)
        assertEquals(listOf(0.0, 2000.0), sundayFirst.chart("weekly-volume").series.single().values.takeLast(2))
    }

    @Test fun selectedDayRebasesDailyWeeklyAndMonthlyDataWithoutInventingFutureStatistics() {
        val prior = LocalDate.of(2026, 8, 31)
        val records = listOf(session("august", prior, listOf(row("a", prior, weight = 80.0))),
            session("september", rows = listOf(row("b", weight = 100.0))))
        val august = report(records, selected = prior)
        val september = report(records)
        assertEquals(800.0, august.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(1000.0, september.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(listOf(80.0), august.chart("daily-fatigue").series.first().values)
        assertEquals(listOf(100.0), september.chart("daily-fatigue").series.first().values)
        assertEquals(31, august.chart("monthly-1rm").labels.size)
        assertEquals(30, september.chart("monthly-1rm").labels.size)
        assertTrue(august.chart("monthly-1rm").caption.contains("August 2026"))
        assertTrue(september.chart("monthly-1rm").caption.contains("September 2026"))
        val future = report(records, selected = day.plusYears(1))
        assertTrue(future.chart("daily-fatigue").series.first().values.isEmpty())
        assertEquals(0.0, future.chart("weekly-volume").series.single().values.last()!!, .001)
        assertNotNull(future.chart("weekly-volume").emptyReason)
        assertNotNull(future.chart("monthly-1rm").emptyReason)
    }

    @Test fun distantScheduledPlanAndCompletedPlanRetainTheirBlotterBehavior() {
        val distant = day.plusMonths(4)
        val plan = DashboardPlan("p", "Back patrol", null, time(day.minusDays(1)), 1, 10)
        val appointment = listOf(DashboardSchedule("p", distant))
        val future = report(plans = listOf(plan), selected = distant, schedule = appointment)
        assertEquals(listOf("p"), future.launches.map { it.id })
        assertTrue(future.calendar.single { it.date == distant }.scheduled)
        assertFalse(future.calendar.single { it.date == distant }.recorded)
        assertEquals(0.0, future.chart("weekly-volume").series.single().values.last()!!, .001)
        val historical = day.minusMonths(4)
        val completed = report(listOf(session("old", historical, listOf(row("a", historical)), plan = "p")),
            listOf(plan), selected = historical, schedule = listOf(DashboardSchedule("p", historical)))
        assertEquals(listOf(true), completed.launches.map { it.recorded })
        assertFalse(completed.calendar.single { it.date == historical }.scheduled)
        assertTrue(completed.calendar.single { it.date == historical }.recorded)
    }

    @Test fun restRatioDoesNotDivideByZeroAndLiveUnresolvedBreakIsMissing() {
        val r = report(listOf(session("s", rows = listOf(row("a", rest = 30_000), row("b", rest = 0)), finished = false)))
        assertEquals(listOf(30.0, null), r.chart("daily-rest").series.single().values)
        assertEquals("30 sec avg", r.chart("daily-rest").headline)
        assertEquals(2.0, r.chart("weekly-density").series[2].values.last()!!, .001)
        val noRest = report(listOf(session("z", rows = listOf(row("z", rest = 0)))))
        assertNull(noRest.chart("weekly-density").series[2].values.last())
        assertEquals(0.0, noRest.chart("daily-rest").series.single().values.single()!!, .001)
        assertNull(noRest.chart("daily-rest").emptyReason)
    }

    @Test fun firstRecordIsBaselineAndFutureSetsCannotCreatePrOrVolume() {
        val prior = day.minusDays(7)
        val future = day.plusDays(1)
        val r = report(listOf(session("baseline", prior, listOf(row("a", prior))),
            session("new", rows = listOf(row("b", weight = 110.0))),
            session("future", future, listOf(row("c", future, weight = 1000.0)))))
        assertEquals(1, r.chart("monthly-pr").series.single().values.count { it != null })
        assertEquals(110.0, r.chart("monthly-pr").series.single().values[29]!!, .001)
        assertEquals(1100.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(12, r.weekly.size + r.daily.size + r.monthly.size)
        (r.weekly + r.daily + r.monthly).forEach { assertTrue(DashboardInsights.forChart(it.id).formula.isNotBlank()) }
    }

    @Test fun dailyBlotterUsesOnlyExplicitAppointmentsAndCompletedPlansWithoutChangingChartTotals() {
        val plan = DashboardPlan("p", "Back patrol", 3, time(day.minusMonths(1)), 1, 10)
        val records = listOf(session("completed", rows = listOf(row("a")), plan = "p"),
            session("active", rows = listOf(row("b")), plan = "p", finished = false),
            session("standalone", rows = listOf(row("c"))))
        val r = report(records, listOf(plan), schedule = listOf(DashboardSchedule("p", day.plusDays(1))))
        assertEquals(listOf("completed"), r.launches.map { it.id })
        assertFalse(r.calendar.single { it.date == day }.scheduled)
        assertTrue(r.calendar.single { it.date == day }.recorded)
        assertTrue(r.calendar.single { it.date == day.plusDays(1) }.scheduled)
        assertEquals(3000.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertNull(report(plans = listOf(plan)).chart("monthly-adherence").series.single().values.single())
    }
}
