package com.darkoceantech.myfitnesspolice.data

import androidx.room.withTransaction
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.util.UUID

class StretchRepository(private val database: FitnessDatabase) {
    private val dao = database.stretchDao()
    val catalog = dao.catalog().map { rows -> rows.map { StretchJson.movement(org.json.JSONObject(it.payload)) }.sortedBy { it.name.lowercase() } }
    val routines = dao.routines().map { rows -> rows.map { StretchJson.routine(it.payload) }.sortedBy { it.name.lowercase() } }
    val sessions = dao.sessions()
    val preferences = dao.preferences().map { it ?: StretchPreferences() }
    suspend fun saveMovement(value: StretchMovement) {
        require(value.name.isNotBlank()) { "Enter a stretch name." }
        dao.save(StretchCatalogRow(value.id, StretchJson.movement(value.copy(name = value.name.trim())).toString()))
    }
    suspend fun saveRoutine(value: StretchRoutine) {
        StretchTiming.validate(value)
        dao.save(StretchRoutineRow(value.id, StretchJson.routine(value.copy(name = value.name.trim()))))
    }
    suspend fun savePreferences(value: StretchPreferences) {
        require(value.work in 1..3600 && value.rest in 0..3600 && value.switch in 0..3600)
        dao.save(value)
    }
    suspend fun start(id: String, now: Long = System.currentTimeMillis()): String = database.withTransaction {
        require(dao.active() == null) { "Return to your active stretch routine before starting another." }
        require(database.workoutDao().observeUnfinished().first().isEmpty()) { "Finish or cancel your current strength session first." }
        val routine = StretchJson.routine(requireNotNull(dao.routine(id)) { "Save this routine before starting." }.payload)
        StretchTiming.validate(routine)
        val session = StretchSessionRow(UUID.randomUUID().toString(), now, snapshot = StretchJson.routine(routine),
            progress = StretchJson.run(StretchRun(StretchTiming.phases(routine, prepare = true), anchor = now)))
        dao.insert(session)
        session.id
    }
    suspend fun tick(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.active() ?: return@withTransaction
        val run = StretchJson.run(session.progress)
        val updated = StretchTiming.advance(run, now)
        if (updated.index != run.index || updated.status != run.status) {
            val end = if (updated.status == "completed") (run.anchor ?: now) + run.phases.drop(run.index).sumOf { it.plannedMillis } - run.elapsed else null
            dao.update(session.copy(progress = StretchJson.run(updated), endedAt = end))
        }
    }
    suspend fun control(id: String, command: String, notes: String? = null, now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = requireNotNull(dao.session(id)) { "This session is no longer available." }
        require(session.endedAt == null) { "This session has already ended." }
        val persisted = StretchJson.run(session.progress)
        val run = StretchTiming.advance(persisted, now)
        if (command == "cancel") {
            require(run.results.none { it.phase.kind == "WORK" } && run.phases.getOrNull(run.index)?.kind == "PREPARE") { "Use Finish routine once stretching has started." }
            dao.cancel(id); return@withTransaction
        }
        val updated = when (command) {
            "pause" -> StretchTiming.pause(run, now)
            "resume" -> StretchTiming.resume(run, now)
            "skip" -> StretchTiming.skip(run, now)
            "next" -> {
                val entry = run.phases.getOrNull(run.index)?.entryId
                var result = run
                while (result.index < result.phases.size && result.phases[result.index].entryId == entry) result = StretchTiming.skip(result, now)
                result
            }
            "finish" -> StretchTiming.finish(run, now)
            else -> run
        }
        dao.update(session.copy(progress = StretchJson.run(updated), notes = notes ?: session.notes,
            endedAt = when {
                run.status == "completed" -> (persisted.anchor ?: now) + persisted.phases.drop(persisted.index).sumOf { it.plannedMillis } - persisted.elapsed
                updated.status in listOf("completed", "finished early") -> now
                else -> null
            }))
    }
    suspend fun saveSessionNote(id: String, note: String) = database.withTransaction {
        dao.session(id)?.let { dao.update(it.copy(notes = note)) }
    }
}
