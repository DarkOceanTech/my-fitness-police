package com.darkoceantech.myfitnesspolice.domain.dashboard

import java.time.*
import com.darkoceantech.myfitnesspolice.domain.formatting.formatWeight
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Pure, deterministic aggregations. All dates use local calendar boundaries, including DST. */
class DashboardMetricsCalculator {
    fun calculate(input: DashboardInput, now: Long, selected: LocalDate, dailyLift: String? = null,
        monthlyLift: String? = null, zone: ZoneId = ZoneId.systemDefault()): DashboardReport {
        fun date(millis: Long) = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        val today = date(now)
        val day = selected.coerceIn(today.minusDays(10), today.plusDays(10))
        val sessions = input.sessions.filter { it.startedAt <= now }.map { it.copy(sets = it.sets.filter { set -> set.completedAt <= now }) }
        val sets = sessions.flatMap { session -> session.sets.map { TimedSet(session, it, date(session.startedAt)) } }
            .sortedWith(compareBy<TimedSet> { it.set.completedAt }.thenBy { it.session.startedAt }.thenBy { it.set.order }.thenBy { it.set.id })
        val planById = input.plans.associateBy { it.id }
        val scheduleByDay = input.schedule.distinct().filter { it.planId in planById }.groupBy { it.date }
        fun scheduled(d: LocalDate) = scheduleByDay[d].orEmpty().mapNotNull { planById[it.planId] }
        fun recorded(d: LocalDate) = sessions.filter { date(it.startedAt) == d && it.isTrainingPlan &&
            it.finishedAt != null && it.finishedAt <= now }
        fun pending(d: LocalDate) = scheduled(d).filter { plan ->
            recorded(d).none { it.planId == plan.id && it.sets.isNotEmpty() }
        }
        val calendar = (-10L..10L).map { offset -> today.plusDays(offset).let {
            CalendarDay(it, pending(it).isNotEmpty(), recorded(it).isNotEmpty())
        } }
        val launches = buildList {
            pending(day).forEach { add(DayLaunchItem(it.id, it.name, "Scheduled routine · ${it.sets} sets · ${it.reps} planned reps", false)) }
            recorded(day).groupBy { it.groupId ?: it.id }.values.sortedBy { group -> group.minOf { it.startedAt } }.forEach { group ->
                val performed = group.flatMap { it.sets }
                add(DayLaunchItem(group.first().id, group.first().title,
                    "Completed plan · ${performed.size} sets · ${performed.sumOf { it.reps.toLong() }} reps", true))
            }
        }
        val monday = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekDates = (7 downTo 0).map { monday.minusWeeks(it.toLong()) }
        val weeks = weekDates.map { start -> sets.filter { it.date >= start && it.date < start.plusWeeks(1) && it.date <= day } }
        val labels = weekDates.map { it.format(DateTimeFormatter.ofPattern("M/d")) }
        val muscles = weeks.flatten().filter { !it.set.warmup }.map { it.set.muscle }.distinct().sorted()
        val weekActive = weeks.map { list -> list.sumOf { it.set.activeMillis } / 60000.0 }
        val weekBreak = weeks.map { list -> list.sumOf { it.set.breakMillis } / 60000.0 }
        // A prior-day estimate prevents a set from inflating its own reference ceiling.
        val exerciseHistory = sets.groupBy { it.set.exerciseId }
        val baselineCache = mutableMapOf<Pair<String, LocalDate>, Double?>()
        fun baseline(record: TimedSet): Double? = baselineCache.getOrPut(record.set.exerciseId to record.date) { exerciseHistory[record.set.exerciseId].orEmpty().asSequence().filter {
            it.set.exerciseId == record.set.exerciseId && it.date < record.date && it.date >= record.date.minusDays(90) && !it.set.warmup
        }.mapNotNull { epley(it.set.pounds, it.set.reps) }.maxOrNull() }
        fun intensity(records: List<TimedSet>): List<Double> {
            val counts = MutableList(4) { 0.0 }
            records.filter { !it.set.warmup }.forEach { record ->
                val ref = baseline(record)
                val ratio = if (ref != null && ref > 0 && record.set.pounds > 0) record.set.pounds / ref else null
                counts[when { ratio == null -> 3; ratio < .60 -> 0; ratio <= .85 -> 1; else -> 2 }]++
            }
            return counts
        }
        val zones = intensity(weeks.last())
        val weekly = listOf(
            chart("weekly-volume", "Weekly Volume Progress", formatWeight(weeks.last().sumOf { it.set.pounds * it.set.reps }) + " Lbs-reps",
                "Last 8 weeks · through ${day.format(DateTimeFormatter.ofPattern("MMM d"))}", ChartKind.LINE, labels,
                listOf(series("Volume", weeks.map { rows -> rows.sumOf { it.set.pounds * it.set.reps } })), "Lbs-reps",
                empty = if (weeks.all { it.isEmpty() }) "No recorded sets in these eight weeks." else null),
            chart("weekly-muscles", "Weekly Muscle Set Count", "${weeks.last().count { !it.set.warmup }} working sets",
                "Last 8 weeks · one main muscle group per set", ChartKind.STACKED_BAR, labels,
                muscles.map { muscle -> series(muscle, weeks.map { week -> week.count { !it.set.warmup && it.set.muscle == muscle }.toDouble() }) }, "sets"),
            chart("weekly-density", "Weekly Workout Density", number(weekActive.last() + weekBreak.last()) + " min",
                "Recorded active + break time · pauses excluded", ChartKind.COMBO, labels,
                listOf(series("Total time", weekActive.zip(weekBreak) { a, b -> a + b }),
                    ChartSeries("Active / break", weekActive.zip(weekBreak) { a, b -> if (b > 0) a / b else null })), "min", "ratio",
                empty = if (weekActive.sum() + weekBreak.sum() == 0.0) "No recorded timing in these eight weeks." else null),
            chart("weekly-intensity", "Weekly Intensity Distribution", "${zones.sum().toInt()} working sets",
                "Selected week · earlier estimated 1RM baseline", ChartKind.DONUT, listOf("Sets"),
                listOf("Low <60%", "Medium 60–85%", "High >85%", "Unknown baseline").mapIndexed { i, name -> series(name, listOf(zones[i])) }, "sets",
                empty = if (zones.sum() == 0.0) "No completed working sets this week." else null)
        )
        val daySets = sets.filter { it.date == day }
        val dailyChoices = choices(daySets)
        val dailyId = dailyLift?.takeIf { id -> dailyChoices.any { it.id == id } } ?: dailyChoices.firstOrNull()?.id
        val liftSets = daySets.filter { it.set.exerciseId == dailyId }
        val setLabels = liftSets.indices.map { "${it + 1}" }
        val dailyEmpty = "No recorded sets for this day. Complete a set in Academy to see this chart."
        val breaks = daySets.map { if (it.session.finishedAt != null || it.set.breakMillis > 0) it.set.breakMillis / 1000.0 else null }
        val daily = listOf(
            chart("daily-fatigue", "Set-by-Set Fatigue Drop-off", "${liftSets.size} recorded sets", "${dailyChoices.find { it.id == dailyId }?.name ?: "Choose a lift"} · chronological sets",
                ChartKind.DUAL_LINE, setLabels, listOf(series("Weight", liftSets.map { it.set.pounds }), series("Reps", liftSets.map { it.set.reps.toDouble() })), "Lbs", "reps", empty = if (liftSets.isEmpty()) dailyEmpty else null),
            chart("daily-time", "Session Time Breakdown", number(daySets.sumOf { it.set.activeMillis + it.set.breakMillis } / 60000.0) + " min",
                "All recorded sets on ${day.format(DateTimeFormatter.ofPattern("MMM d"))}", ChartKind.DONUT, listOf("Time"),
                listOf(series("Active lifting", listOf(daySets.sumOf { it.set.activeMillis } / 60000.0)),
                    series("Rest breaks", listOf(daySets.sumOf { it.set.breakMillis } / 60000.0))), "min",
                empty = if (daySets.sumOf { it.set.activeMillis + it.set.breakMillis } == 0L) "No recorded timing for this day." else null),
            chart("daily-intensity", "Intra-Workout Intensity", "${liftSets.size} sets", "Weight per set · dashed line = earlier estimated 1RM",
                ChartKind.BAR, setLabels, listOf(series("Weight", liftSets.map { it.set.pounds })), "Lbs",
                empty = if (liftSets.isEmpty()) dailyEmpty else null, reference = liftSets.firstOrNull()?.let(::baseline)),
            chart("daily-rest", "Rest Interval Consistency", number(breaks.filterNotNull().average().takeIf { it.isFinite() } ?: 0.0) + " sec avg",
                "Chronological recorded breaks · includes final cool-down", ChartKind.LINE, daySets.indices.map { "${it + 1}" },
                listOf(ChartSeries("Break", breaks)), "sec",
                empty = if (daySets.isEmpty()) dailyEmpty else null)
        )
        val monthStart = day.withDayOfMonth(1)
        val monthEnd = minOf(day, today)
        val monthSets = sets.filter { it.date >= monthStart && it.date <= monthEnd }
        val monthlyChoices = choices(sets.filter { it.date <= day })
        val monthlyId = monthlyLift?.takeIf { id -> monthlyChoices.any { it.id == id } } ?: monthlyChoices.firstOrNull()?.id
        val monthsLift = monthSets.filter { it.set.exerciseId == monthlyId && !it.set.warmup }
        val monthDays = (0 until day.lengthOfMonth()).map { monthStart.plusDays(it.toLong()) }
        val monthLabels = monthDays.map { "${it.dayOfMonth}" }
        val estimates = monthDays.map { date -> monthsLift.filter { it.date == date }.mapNotNull { epley(it.set.pounds, it.set.reps) }.maxOrNull() }
        var maxWeight = 0.0; var maxVolume = 0.0
        val milestones = mutableMapOf<LocalDate, Double>()
        sets.filter { it.set.exerciseId == monthlyId && !it.set.warmup && it.set.reps > 0 && it.date <= day }.forEach { row ->
            val volume = row.set.pounds * row.set.reps
            if ((row.set.pounds > maxWeight || volume > maxVolume) && row.date >= monthStart && (maxWeight > 0 || maxVolume > 0)) {
                milestones[row.date] = maxOf(milestones[row.date] ?: 0.0, row.set.pounds)
            }
            maxWeight = maxOf(maxWeight, row.set.pounds); maxVolume = maxOf(maxVolume, volume)
        }
        val scheduledSlots = monthDays.filter { it <= monthEnd }.flatMap { date -> scheduled(date).map { it.id to date } }.toSet()
        val fulfilled = sessions.filter { it.finishedAt != null && it.finishedAt <= now && it.sets.isNotEmpty() }
            .mapNotNull { session -> session.planId?.let { it to date(session.startedAt) } }.toSet().intersect(scheduledSlots).size
        val adherence = scheduledSlots.takeIf { it.isNotEmpty() }?.let { fulfilled * 100.0 / it.size }
        val ranges = listOf(1..5, 6..12, 13..Int.MAX_VALUE).map { range -> monthSets.count { !it.set.warmup && it.set.reps in range }.toDouble() }
        val monthly = listOf(
            chart("monthly-1rm", "Estimated 1-Rep Max Trend", estimates.filterNotNull().maxOrNull()?.let { formatWeight(it) + " Lbs e1RM" } ?: "No estimate",
                "${day.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} · ${monthlyChoices.find { it.id == monthlyId }?.name ?: "Choose a lift"}",
                ChartKind.LINE, monthLabels, listOf(ChartSeries("Estimated 1RM", estimates)), "Lbs",
                empty = if (estimates.all { it == null }) "Needs a weighted working set with 1–10 actual reps." else null),
            chart("monthly-pr", "Exercise PR Timeline", "${milestones.size} milestone days",
                "New max weight or set volume · same exercise", ChartKind.SCATTER, monthLabels,
                listOf(ChartSeries("PR weight", monthDays.map { milestones[it] })), "Lbs",
                empty = if (milestones.isEmpty()) "No new PRs for this lift this month. Your first log establishes the baseline." else null),
            chart("monthly-adherence", "Monthly Adherence Rate", adherence?.let { number(it) + "%" } ?: "No schedule",
                "$fulfilled / ${scheduledSlots.size} scheduled routines", ChartKind.GAUGE, listOf("Adherence"),
                listOf(ChartSeries("Completed", listOf(adherence))), "%",
                empty = if (adherence == null) "Use Schedule training on a plan in Academy to choose calendar dates." else null),
            chart("monthly-variety", "Rep Range Variety", "${ranges.sum().toInt()} working sets",
                "${day.format(DateTimeFormatter.ofPattern("MMMM yyyy"))} · actual reps", ChartKind.RADAR,
                listOf("Strength 1–5", "Hypertrophy 6–12", "Endurance 13+"), listOf(series("Working sets", ranges)), "sets",
                empty = if (ranges.sum() == 0.0) "No completed working sets for this month." else null)
        )
        return DashboardReport(today, day, calendar, launches, weekly, daily, monthly, dailyChoices, monthlyChoices, dailyId, monthlyId)
    }
    private data class TimedSet(val session: DashboardSession, val set: DashboardSet, val date: LocalDate)
    private fun choices(rows: List<TimedSet>) = rows.asReversed().distinctBy { it.set.exerciseId }.map { LiftChoice(it.set.exerciseId, it.set.exerciseName) }
    private fun chart(id: String, title: String, headline: String, caption: String, kind: ChartKind, labels: List<String>,
        series: List<ChartSeries>, unit: String, right: String? = null, empty: String? = null, reference: Double? = null) =
        DashboardChart(id, title, headline, caption, kind, labels, series, unit, right,
            empty ?: if (series.isEmpty() || series.all { s -> s.values.all { it == null } }) "No recorded data in this period." else null, reference)
    private fun series(name: String, values: List<Double>) = ChartSeries(name, values)
    companion object {
        fun epley(pounds: Double, reps: Int): Double? = if (pounds <= 0 || reps !in 1..10) null else if (reps == 1) pounds else pounds * (1 + reps / 30.0)
        fun number(value: Double): String = String.format(Locale.getDefault(), if (value % 1.0 == 0.0) "%,.0f" else "%,.1f", value)
    }
}
