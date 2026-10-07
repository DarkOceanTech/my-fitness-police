package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import com.darkoceantech.myfitnesspolice.domain.stretch.*
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "stretch_catalog")
data class StretchCatalogRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "stretch_routines")
data class StretchRoutineRow(@PrimaryKey val id: String, val payload: String)
@Entity(tableName = "stretch_sessions", indices = [Index("startedAt")])
data class StretchSessionRow(@PrimaryKey val id: String, val startedAt: Long, val endedAt: Long? = null,
    val snapshot: String, val progress: String, val notes: String = "")
@Entity(tableName = "stretch_preferences")
data class StretchPreferences(@PrimaryKey val id: Int = 1, val work: Int = 30, val rest: Int = 10,
    val switch: Int = 5, val sound: Boolean = false, val vibration: Boolean = false)

@Dao
interface StretchDao {
    @Query("SELECT * FROM stretch_catalog") fun catalog(): Flow<List<StretchCatalogRow>>
    @Query("SELECT * FROM stretch_routines") fun routines(): Flow<List<StretchRoutineRow>>
    @Query("SELECT * FROM stretch_sessions ORDER BY startedAt DESC") fun sessions(): Flow<List<StretchSessionRow>>
    @Query("SELECT * FROM stretch_preferences WHERE id = 1") fun preferences(): Flow<StretchPreferences?>
    @Query("SELECT * FROM stretch_routines WHERE id = :id") suspend fun routine(id: String): StretchRoutineRow?
    @Query("SELECT * FROM stretch_sessions WHERE endedAt IS NULL LIMIT 1") suspend fun active(): StretchSessionRow?
    @Query("SELECT * FROM stretch_sessions WHERE id = :id") suspend fun session(id: String): StretchSessionRow?
    @Query("SELECT * FROM stretch_preferences WHERE id = 1") suspend fun getPreferences(): StretchPreferences?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(row: StretchCatalogRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(row: StretchRoutineRow)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun save(row: StretchPreferences)
    @Insert suspend fun insert(row: StretchSessionRow)
    @Update suspend fun update(row: StretchSessionRow)
    @Query("DELETE FROM stretch_sessions WHERE id = :id AND endedAt IS NULL") suspend fun cancel(id: String)
}

object StretchJson {
    fun movement(value: StretchMovement): JSONObject = JSONObject().put("id", value.id).put("name", value.name)
        .put("instructions", value.instructions).put("muscles", value.muscles).put("equipment", value.equipment)
        .put("type", value.movementType).put("separate", value.separateSides).put("side", value.startingSide)
    fun movement(json: JSONObject) = StretchMovement(json.getString("id"), json.getString("name"), json.optString("instructions"),
        json.optString("muscles"), json.optString("equipment", "No equipment"), json.optString("type", "Held stretch"),
        json.optBoolean("separate"), json.optString("side", "Left"))
    fun routine(value: StretchRoutine): String = JSONObject().put("id", value.id).put("name", value.name)
        .put("purpose", value.purpose).put("description", value.description).put("work", value.defaultWork).put("rest", value.defaultRest)
        .put("switch", value.defaultSwitch).put("finalRest", value.finalRest).put("entries", JSONArray().apply {
            value.stretches.forEach { entry -> put(JSONObject().put("id", entry.id).put("movement", movement(entry.movement))
                .put("note", entry.note).put("side", entry.startingSide).put("sets", JSONArray().apply {
                    entry.sets.forEach { put(JSONObject().put("id", it.id).put("work", it.work).put("rest", it.rest).put("switch", it.switch)) }
                })) }
        }).toString()
    fun routine(text: String): StretchRoutine {
        val json = JSONObject(text)
        val entries = json.getJSONArray("entries")
        return StretchRoutine(json.getString("id"), json.getString("name"), json.getString("purpose"), json.optString("description"),
            json.getInt("work"), json.getInt("rest"), json.getInt("switch"), json.getBoolean("finalRest"),
            (0 until entries.length()).map { i -> val e = entries.getJSONObject(i); val sets = e.getJSONArray("sets")
                RoutineStretch(e.getString("id"), movement(e.getJSONObject("movement")), (0 until sets.length()).map { n ->
                    val s = sets.getJSONObject(n); StretchSet(s.getString("id"), s.getInt("work"), s.getInt("rest"), s.getInt("switch"))
                }, e.optString("note"), e.getString("side")) })
    }
    private fun phase(p: StretchPhase) = JSONObject().put("entry", p.entryId).put("set", p.setId).put("kind", p.kind)
        .put("side", p.side).put("planned", p.plannedMillis)
    private fun phase(j: JSONObject) = StretchPhase(j.getString("entry"), j.getString("set"), j.getString("kind"), j.getString("side"), j.getLong("planned"))
    fun run(run: StretchRun): String = JSONObject().put("index", run.index).put("elapsed", run.elapsed)
        .put("anchor", run.anchor ?: JSONObject.NULL).put("status", run.status)
        .put("phases", JSONArray().apply { run.phases.forEach { put(phase(it)) } })
        .put("results", JSONArray().apply { run.results.forEach { put(JSONObject().put("phase", phase(it.phase)).put("actual", it.actualMillis).put("skipped", it.skipped)) } }).toString()
    fun run(text: String): StretchRun {
        val j = JSONObject(text); val phases = j.getJSONArray("phases"); val results = j.getJSONArray("results")
        return StretchRun((0 until phases.length()).map { phase(phases.getJSONObject(it)) }, j.getInt("index"), j.getLong("elapsed"),
            if (j.isNull("anchor")) null else j.getLong("anchor"), j.getString("status"),
            (0 until results.length()).map { val r = results.getJSONObject(it); StretchPhaseResult(phase(r.getJSONObject("phase")), r.getLong("actual"), r.getBoolean("skipped")) })
    }
}
