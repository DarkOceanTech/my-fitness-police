package com.darkoceantech.myfitnesspolice.domain.stretch

import java.util.UUID

data class StretchMovement(val id: String = UUID.randomUUID().toString(), val name: String,
    val instructions: String = "", val muscles: String = "", val equipment: String = "No equipment",
    val movementType: String = "Held stretch", val separateSides: Boolean = false, val startingSide: String = "Left")
data class StretchSet(val id: String = UUID.randomUUID().toString(), val work: Int = 30, val rest: Int = 10, val switch: Int = 5)
data class RoutineStretch(val id: String = UUID.randomUUID().toString(), val movement: StretchMovement,
    val sets: List<StretchSet> = listOf(StretchSet()), val note: String = "", val startingSide: String = movement.startingSide)
data class StretchRoutine(val id: String = UUID.randomUUID().toString(), val name: String = "",
    val purpose: String = "General mobility", val description: String = "", val defaultWork: Int = 30,
    val defaultRest: Int = 10, val defaultSwitch: Int = 5, val finalRest: Boolean = true,
    val stretches: List<RoutineStretch> = emptyList())
data class StretchPhase(val entryId: String, val setId: String, val kind: String, val side: String = "", val plannedMillis: Long)
data class StretchPhaseResult(val phase: StretchPhase, val actualMillis: Long, val skipped: Boolean = false)
data class StretchRun(val phases: List<StretchPhase>, val index: Int = 0, val elapsed: Long = 0,
    val anchor: Long? = null, val status: String = "running", val results: List<StretchPhaseResult> = emptyList())

object StretchTiming {
    fun validate(routine: StretchRoutine) {
        require(routine.name.isNotBlank()) { "Enter a routine name." }
        require(routine.defaultWork in 1..3600 && routine.defaultRest in 0..3600 && routine.defaultSwitch in 0..3600) { "Work must be 1–3,600 seconds. Rest and switch time must be 0–3,600 seconds." }
        require(routine.stretches.isNotEmpty()) { "Add at least one stretch." }
        routine.stretches.forEach { entry ->
            require(entry.sets.isNotEmpty()) { "Add a set to every stretch." }
            entry.sets.forEach { require(it.work in 1..3600 && it.rest in 0..3600 && it.switch in 0..3600) { "Check the set durations." } }
        }
    }
    fun phases(routine: StretchRoutine, prepare: Boolean = false): List<StretchPhase> = buildList {
        if (prepare) add(StretchPhase("", "", "PREPARE", plannedMillis = 5000))
        routine.stretches.forEachIndexed { entryIndex, entry ->
            entry.sets.forEachIndexed { setIndex, set ->
                fun phase(kind: String, seconds: Int, side: String = "") {
                    if (seconds > 0) add(StretchPhase(entry.id, set.id, kind, side, seconds * 1000L))
                }
                if (entry.movement.separateSides) {
                    phase("WORK", set.work, entry.startingSide)
                    phase("SWITCH SIDES", set.switch)
                    phase("WORK", set.work, if (entry.startingSide == "Left") "Right" else "Left")
                } else phase("WORK", set.work)
                val final = entryIndex == routine.stretches.lastIndex && setIndex == entry.sets.lastIndex
                if (!final || routine.finalRest) phase("REST", set.rest)
            }
        }
    }
    fun estimate(routine: StretchRoutine) = phases(routine).sumOf { it.plannedMillis }
    /** Reconcile elapsed time across arbitrarily many phases after a background/restore gap. */
    fun advance(run: StretchRun, now: Long): StretchRun {
        if (run.status != "running" || run.anchor == null) return run
        var elapsed = run.elapsed + (now - run.anchor).coerceAtLeast(0)
        var index = run.index
        val results = run.results.toMutableList()
        while (index < run.phases.size && elapsed >= run.phases[index].plannedMillis) {
            val phase = run.phases[index++]
            elapsed -= phase.plannedMillis
            results.add(StretchPhaseResult(phase, phase.plannedMillis))
        }
        return run.copy(index = index, elapsed = if (index == run.phases.size) 0 else elapsed,
            anchor = if (index == run.phases.size) null else now,
            status = if (index == run.phases.size) "completed" else "running", results = results)
    }
    fun pause(run: StretchRun, now: Long) = advance(run, now).let {
        if (it.status == "running") it.copy(status = "paused", anchor = null) else it
    }
    fun resume(run: StretchRun, now: Long) = if (run.status == "paused") run.copy(status = "running", anchor = now) else run
    fun skip(run: StretchRun, now: Long): StretchRun {
        val current = advance(run, now)
        if (current.index >= current.phases.size) return current
        val next = current.index + 1
        return current.copy(index = next, elapsed = 0,
            results = current.results + StretchPhaseResult(current.phases[current.index], current.elapsed, skipped = true),
            anchor = if (current.status == "running" && next < current.phases.size) now else null,
            status = if (next == current.phases.size) "completed" else current.status)
    }
    fun finish(run: StretchRun, now: Long): StretchRun {
        val current = advance(run, now)
        if (current.status == "completed") return current
        return current.copy(status = "finished early", anchor = null,
            results = current.results + if (current.index < current.phases.size)
                listOf(StretchPhaseResult(current.phases[current.index], current.elapsed, skipped = true)) else emptyList())
    }
    fun completedSets(run: StretchRun): Int = run.phases.filter { it.kind != "PREPARE" }.groupBy { it.setId }.count { (_, phases) ->
        phases.all { phase -> run.results.any { it.phase == phase && !it.skipped && it.actualMillis >= phase.plannedMillis } }
    }
    fun actual(run: StretchRun, kind: String): Long = run.results.filter { it.phase.kind == kind }.sumOf { it.actualMillis } +
        if (run.phases.getOrNull(run.index)?.kind == kind && run.status in listOf("running", "paused")) run.elapsed else 0L
}
