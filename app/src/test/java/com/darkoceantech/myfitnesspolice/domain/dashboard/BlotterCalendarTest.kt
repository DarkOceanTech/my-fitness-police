package com.darkoceantech.myfitnesspolice.domain.dashboard

import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class BlotterCalendarTest {
    @Test fun mondayIsDefaultAndSundaySettingReordersTheEntireWeek() {
        val date = LocalDate.of(2026, 10, 12)
        assertEquals(date, startOfWeek(date))
        assertEquals(DayOfWeek.values().toList(), calendarWeek(date).map { it.dayOfWeek })
        val sunday = calendarWeek(date, DayOfWeek.SUNDAY)
        assertEquals(date.minusDays(1), sunday.first())
        assertEquals(listOf(DayOfWeek.SUNDAY) + DayOfWeek.values().take(6), sunday.map { it.dayOfWeek })
        assertTrue(date in sunday)
    }

    @Test fun swipingKeepsCorrespondingWeekdayAcrossMonthYearAndLeapDayBoundaries() {
        for (selected in listOf(LocalDate.of(2026, 12, 31), LocalDate.of(2026, 10, 31), LocalDate.of(2028, 2, 29))) {
            for (offset in listOf(-100L, -1L, 1L, 100L)) {
                val destination = moveToWeek(selected, offset)
                assertEquals(selected.dayOfWeek, destination.dayOfWeek)
                assertEquals(selected.plusDays(offset * 7), destination)
                assertEquals(selected, moveToWeek(destination, -offset))
                for (firstDay in listOf(DayOfWeek.MONDAY, DayOfWeek.SUNDAY)) {
                    val week = calendarWeek(destination, firstDay)
                    assertEquals(7, week.distinct().size)
                    assertEquals(firstDay, week.first().dayOfWeek)
                    assertTrue(destination in week)
                    assertEquals(week.first().plusDays(6), week.last())
                }
            }
        }
    }

    @Test fun localWeeksContainSevenDatesEvenWhenElapsedTimeIncludesDstChange() {
        val zone = ZoneId.of("America/Los_Angeles")
        for ((selected, elapsedHours) in listOf(LocalDate.of(2026, 3, 8) to 167L, LocalDate.of(2026, 11, 1) to 169L)) {
            val start = startOfWeek(selected)
            val hours = java.time.Duration.between(start.atStartOfDay(zone), start.plusWeeks(1).atStartOfDay(zone)).toHours()
            assertEquals(elapsedHours, hours)
            assertEquals(selected.plusDays(7), moveToWeek(selected, 1))
            assertEquals(7, calendarWeek(selected).size)
        }
    }

    @Test fun weeksSpanningNewYearKeepTheCorrectLocalDates() {
        val week = calendarWeek(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
    }
}
