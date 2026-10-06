package com.darkoceantech.myfitnesspolice.domain.dashboard

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class DashboardMetricsCalculatorTest {
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
        selected: LocalDate = day, schedule: List<DashboardSchedule> = emptyList()) = DashboardMetricsCalculator().calculate(DashboardInput(sessions, plans, schedule), time(day, 23), selected, dailyLift = "row", zone = zone)
    private fun DashboardReport.chart(id: String) = (weekly + daily + monthly).single { it.id == id }

    @Test fun volumeUsesActualRepsOncePerRowAndWorkingCountExcludesWarmups() {
        val r = report(listOf(session("s", rows = listOf(row("a", reps = 5), row("b", reps = 0),
            row("c", weight = 50.0, reps = 10, warmup = true), row("d", weight = 0.0, reps = 15, muscle = "Abdominals")))))
        assertEquals(1000.0, r.chart("weekly-volume").series.single().values.last()!!, .001)
        assertEquals(3.0, r.chart("weekly-muscles").series.sumOf { it.values.last()!! }, .001)
        assertEquals(listOf(1.0, 0.0, 1.0), r.chart("monthly-variety").series.single().values)
    }

    @Test fun intensityUsesOnlyPriorDaySameLiftWithin90DaysAndRetainsUnknowns() {
        val prior = day.minusDays(7)
        val old = day.minusDays(91)
        val current = listOf(row("low", weight = 59.0), row("medium", weight = 60.0),
            row("edge", weight = 85.0), row("high", weight = 86.0), row("futureBaseline", weight = 1000.0, reps = 1),
            row("bodyweight", weight = 0.0), row("other", weight = 80.0).copy(exerciseId = "other"),
            row("wu", weight = 10.0, warmup = true))
        val r = report(listOf(session("old", old, listOf(row("old", old, weight = 10000.0, reps = 1))),
            session("prior", prior, listOf(row("base", prior, reps = 1))), session("today", rows = current)))
        assertEquals(listOf(1.0, 2.0, 2.0, 2.0), r.chart("weekly-intensity").series.map { it.values.single() })
        assertEquals(100.0, r.chart("daily-intensity").reference!!, .001)
    }

    @Test fun epleyRejectsUnsupportedSetsAndUsesBestEligibleEstimatePerDay() {
        assertNull(DashboardMetricsCalculator.epley(100.0, 0))
        assertNull(DashboardMetricsCalculator.epley(100.0, 11))
        assertNull(DashboardMetricsCalculator.epley(0.0, 10))
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

    @Test fun calendarIsBoundedAndLocalMondayWeekWorksAcrossDst() {
        val monday = LocalDate.of(2026, 11, 2)
        val sunday = monday.minusDays(1)
        val records = listOf(session("sunday", sunday, listOf(row("s", sunday))),
            session("monday", monday, listOf(row("m", monday))))
        val r = DashboardMetricsCalculator().calculate(DashboardInput(records, emptyList()), time(monday, 23), monday, zone = zone)
        assertEquals(21, r.calendar.size)
        assertEquals(monday.minusDays(10), r.calendar.first().date)
        assertEquals(monday.plusDays(10), r.calendar.last().date)
        assertEquals(listOf(1000.0, 1000.0), r.chart("weekly-volume").series.single().values.takeLast(2))
        assertEquals(day.plusDays(10), report(selected = day.plusMonths(1)).selectedDay)
    }

    @Test fun restRatioDoesNotDivideByZeroAndLiveUnresolvedBreakIsMissing() {
        val r = report(listOf(session("s", rows = listOf(row("a", rest = 30_000), row("b", rest = 0)), finished = false)))
        assertEquals(listOf(30.0, null), r.chart("daily-rest").series.single().values)
        assertEquals("30 sec avg", r.chart("daily-rest").headline)
        assertEquals(2.0, r.chart("weekly-density").series[1].values.last()!!, .001)
        val noRest = report(listOf(session("z", rows = listOf(row("z", rest = 0)))))
        assertNull(noRest.chart("weekly-density").series[1].values.last())
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

    @Test fun launchpadUsesOnlyExplicitAppointmentsAndCompletedPlansWithoutChangingChartTotals() {
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
