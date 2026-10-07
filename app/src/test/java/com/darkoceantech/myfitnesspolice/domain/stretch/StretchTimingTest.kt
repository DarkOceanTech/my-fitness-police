package com.darkoceantech.myfitnesspolice.domain.stretch

import org.junit.Assert.*
import org.junit.Test

class StretchTimingTest {
    private fun routine(separate: Boolean = false, finalRest: Boolean = true, sets: Int = 1) = StretchRoutine(name = "Mobility",
        finalRest = finalRest, stretches = listOf(RoutineStretch(movement = StretchMovement(name = "Stretch", separateSides = separate),
            sets = List(sets) { StretchSet(work = 30, rest = 10, switch = 5) })))
    @Test fun durationUsesWorkForEachSideAndSwitchOnlyForSeparateSides() {
        assertEquals(40_000L, StretchTiming.estimate(routine()))
        assertEquals(75_000L, StretchTiming.estimate(routine(true)))
        assertEquals(listOf("Left", "Right"), StretchTiming.phases(routine(true)).filter { it.kind == "WORK" }.map { it.side })
        val reversed = routine(true).let { it.copy(stretches = it.stretches.map { entry -> entry.copy(startingSide = "Right") }) }
        assertEquals(listOf("Right", "Left"), StretchTiming.phases(reversed).filter { it.kind == "WORK" }.map { it.side })
    }
    @Test fun omitOnlyLastRestAcrossTheEntireRoutine() {
        assertEquals(70_000L, StretchTiming.estimate(routine(finalRest = false, sets = 2)))
        val one = routine(true, false)
        val both = one.copy(stretches = one.stretches + routine().stretches)
        assertEquals(105_000L, StretchTiming.estimate(both))
        assertEquals(0, StretchTiming.phases(routine().copy(stretches = routine().stretches.map { it.copy(sets = listOf(StretchSet(rest = 0, switch = 0))) })).count { it.kind != "WORK" })
    }
    @Test fun pauseExcludesTimeAndRestorationAdvancesAcrossPhases() {
        val start = StretchRun(StretchTiming.phases(routine(true), true), anchor = 1000)
        val paused = StretchTiming.pause(start, 16000)
        assertEquals(1, paused.index)
        assertEquals(10000L, paused.elapsed)
        assertEquals(paused, StretchTiming.advance(paused, 1_000_000))
        val resumed = StretchTiming.resume(paused, 1_000_000)
        val finished = StretchTiming.advance(resumed, 1_065_000)
        assertEquals("completed", finished.status)
        assertEquals(60_000L, StretchTiming.actual(finished, "WORK"))
        assertEquals(10_000L, StretchTiming.actual(finished, "REST"))
        assertEquals(5000L, StretchTiming.actual(finished, "SWITCH SIDES"))
        assertEquals(1, StretchTiming.completedSets(finished))
    }
    @Test fun skipsRecordOnlyPerformedTimeAndEarlyFinishDoesNotCompleteRemainingSets() {
        val start = StretchRun(StretchTiming.phases(routine()), anchor = 1000)
        val skipped = StretchTiming.skip(start, 11000)
        assertEquals(10000L, skipped.results.single().actualMillis)
        assertTrue(skipped.results.single().skipped)
        assertEquals(0, StretchTiming.completedSets(skipped))
        val finished = StretchTiming.finish(skipped, 14000)
        assertEquals("finished early", finished.status)
        assertEquals(10000L, StretchTiming.actual(finished, "WORK"))
        assertEquals(3000L, StretchTiming.actual(finished, "REST"))
        assertEquals(0, StretchTiming.completedSets(finished))
    }
    @Test fun invalidDurationsAreRejected() {
        try { StretchTiming.validate(routine().copy(stretches = routine().stretches.map { it.copy(sets = listOf(StretchSet(work = 0))) })); fail() }
        catch (_: IllegalArgumentException) { }
        StretchTiming.validate(routine().copy(stretches = routine().stretches.map { it.copy(sets = listOf(StretchSet(rest = 0, switch = 0))) }))
    }
}
