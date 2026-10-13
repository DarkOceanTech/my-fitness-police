package com.darkoceantech.myfitnesspolice.domain.dashboard

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** Calendar arithmetic stays in local dates, so week navigation is unaffected by DST. */
fun startOfWeek(date: LocalDate, weekStartsOn: DayOfWeek = DayOfWeek.MONDAY): LocalDate =
    date.with(TemporalAdjusters.previousOrSame(weekStartsOn))

fun calendarWeek(date: LocalDate, weekStartsOn: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate> {
    val start = startOfWeek(date, weekStartsOn)
    return (0L..6L).map(start::plusDays)
}

/** Retains the selected weekday rather than resetting a swipe to the week's first day. */
fun moveToWeek(date: LocalDate, offset: Long): LocalDate = date.plusWeeks(offset)
