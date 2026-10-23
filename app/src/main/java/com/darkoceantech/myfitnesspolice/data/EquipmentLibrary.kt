package com.darkoceantech.myfitnesspolice.data

import androidx.room.*
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

@Entity(tableName = "gyms")
data class Gym(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String, val isArchived: Boolean = false,
    @ColumnInfo(defaultValue = "''") val addressLine1: String = "",
    @ColumnInfo(defaultValue = "''") val addressLine2: String = "",
    @ColumnInfo(defaultValue = "''") val city: String = "",
    @ColumnInfo(defaultValue = "''") val region: String = "",
    @ColumnInfo(defaultValue = "''") val postalCode: String = "",
    @ColumnInfo(defaultValue = "''") val country: String = "")
fun Gym.compactAddressLines() = listOf(
    listOf(addressLine1, addressLine2).map(String::trim).filter(String::isNotBlank).joinToString("; "),
    listOf(city, region, postalCode, country).map(String::trim).filter(String::isNotBlank).joinToString(", ")
).filter(String::isNotBlank)
fun Gym.addressLines() = listOf(addressLine1, addressLine2, city, region, postalCode, country).filter { it.isNotBlank() }
@Entity(tableName = "equipment", foreignKeys = [ForeignKey(entity = Gym::class, parentColumns = ["id"], childColumns = ["gymId"], onDelete = ForeignKey.RESTRICT)], indices = [Index("gymId")])
data class Equipment(@PrimaryKey val id: String = UUID.randomUUID().toString(), val type: String, val name: String,
    val makeModel: String = "", val gymId: String? = null, val isArchived: Boolean = false)
@Entity(tableName = "equipment_setups")
data class NamedEquipmentSetup(@PrimaryKey val id: String = UUID.randomUUID().toString(), val name: String,
    val compatibleType: String, val configuration: EquipmentConfiguration, val isArchived: Boolean = false)

/** Labels and positions are copies. IDs are provenance, never required to display recorded data. */
data class EquipmentIdentity(val id: String, val type: String, val name: String, val makeModel: String = "",
    val gymId: String? = null, val gymName: String = "")
data class EquipmentConfiguration(val equipment: List<EquipmentIdentity> = emptyList(), val setupId: String? = null,
    val setupName: String = "", val positions: List<EquipmentPosition> = emptyList(), val legacyDescription: String = "", val gymId: String? = null, val gymName: String = "",
    // Alternatives are not a list of equipment that must be used together.
    val compatibleEquipment: List<EquipmentIdentity> = emptyList(),
    // Each child owns a setup copy for exactly one alternative. Shared records stay independent.
    val equipmentSetups: List<EquipmentConfiguration> = emptyList())
class EquipmentConfigurationConverters {
    @TypeConverter fun encode(value: EquipmentConfiguration?): String? = value?.let { c -> JSONObject()
        .put("equipment", JSONArray().apply { c.equipment.forEach { e -> put(JSONObject().put("id", e.id).put("type", e.type)
            .put("name", e.name).put("makeModel", e.makeModel).put("gymId", e.gymId ?: JSONObject.NULL).put("gymName", e.gymName)) } })
        .put("compatibleEquipment", if (c.compatibleEquipment.isEmpty()) JSONObject.NULL else encode(EquipmentConfiguration(equipment = c.compatibleEquipment)))
        .put("equipmentSetups", JSONArray().apply { c.equipmentSetups.forEach { put(encode(it)) } })
        .put("setupId", c.setupId ?: JSONObject.NULL).put("setupName", c.setupName)
        .put("gymId", c.gymId ?: JSONObject.NULL).put("gymName", c.gymName)
        .put("positions", EquipmentPositionConverters().encode(c.positions)).put("legacyDescription", c.legacyDescription).toString() }
    @TypeConverter fun decode(value: String?): EquipmentConfiguration? = value?.let { raw -> JSONObject(raw).let { c ->
        val array = c.getJSONArray("equipment")
        EquipmentConfiguration(List(array.length()) { index -> array.getJSONObject(index).let { e -> EquipmentIdentity(
            e.getString("id"), e.getString("type"), e.getString("name"), e.optString("makeModel"),
            if (e.isNull("gymId")) null else e.getString("gymId"), e.optString("gymName")) } },
            if (c.isNull("setupId")) null else c.getString("setupId"), c.optString("setupName"),
            EquipmentPositionConverters().decode(c.getString("positions")), c.optString("legacyDescription"), if (c.isNull("gymId")) null else c.getString("gymId"), c.optString("gymName"),
            if (c.isNull("compatibleEquipment")) emptyList() else decode(c.getString("compatibleEquipment"))?.equipment.orEmpty(),
            c.optJSONArray("equipmentSetups")?.let { a -> List(a.length()) { decode(a.getString(it))!! } }.orEmpty())
    } }
}

data class EquipmentLibraryState(val gyms: List<Gym> = emptyList(), val equipment: List<Equipment> = emptyList(),
    val setups: List<NamedEquipmentSetup> = emptyList()) {
    fun identity(e: Equipment) = EquipmentIdentity(e.id, e.type, e.name, e.makeModel, e.gymId,
        gyms.find { it.id == e.gymId }?.name.orEmpty())
}
@Dao interface EquipmentLibraryDao {
    @Query("SELECT * FROM gyms ORDER BY name COLLATE NOCASE") fun gyms(): kotlinx.coroutines.flow.Flow<List<Gym>>
    @Query("SELECT * FROM equipment ORDER BY name COLLATE NOCASE") fun equipment(): kotlinx.coroutines.flow.Flow<List<Equipment>>
    @Query("SELECT * FROM equipment_setups ORDER BY name COLLATE NOCASE") fun setups(): kotlinx.coroutines.flow.Flow<List<NamedEquipmentSetup>>
    @Query("SELECT * FROM gyms WHERE id = :id") suspend fun gym(id: String): Gym?
    @Query("SELECT * FROM equipment WHERE id = :id") suspend fun equipment(id: String): Equipment?
    @Query("SELECT * FROM equipment_setups WHERE id = :id") suspend fun setup(id: String): NamedEquipmentSetup?
    @Upsert suspend fun save(gym: Gym)
    @Upsert suspend fun save(equipment: Equipment)
    @Upsert suspend fun save(setup: NamedEquipmentSetup)
}

/** Entire bundles override defaults, including deliberately empty bundles. */
fun ExerciseWithSets.equipmentConfiguration(): EquipmentConfiguration? = workoutExercise.equipmentSnapshot
    ?: workoutExercise.equipmentOverride ?: exercise.equipmentDefault
    ?: workoutExercise.equipmentPositions.takeIf { it.isNotEmpty() }?.let { EquipmentConfiguration(positions = it) }

class EquipmentLibraryRepository(private val database: FitnessDatabase) {
    private val dao get() = database.equipmentLibraryDao()
    fun observe() = combine(dao.gyms(), dao.equipment(), dao.setups(), ::EquipmentLibraryState)
    suspend fun saveGym(id: String?, name: String, address: List<String>? = null): String {
        require(name.isNotBlank()) { "Enter a gym name." }
        val previous = id?.let { requireNotNull(dao.gym(it)) }
        val base = previous?.copy(name = name.trim()) ?: Gym(name = name.trim())
        require(address == null || address.size == 6)
        val record = if (address == null) base else base.copy(addressLine1 = address[0].trim(),
            addressLine2 = address[1].trim(), city = address[2].trim(), region = address[3].trim(),
            postalCode = address[4].trim(), country = address[5].trim())
        dao.save(record); return record.id
    }
    suspend fun saveEquipment(id: String?, type: String, name: String, model: String, gymId: String?): String {
        require(type.isNotBlank() && name.isNotBlank()) { "Enter equipment type and name." }
        gymId?.let { require(dao.gym(it)?.isArchived == false) { "Choose an available gym." } }
        val previous = id?.let { requireNotNull(dao.equipment(it)) }
        val record = previous?.copy(type = type.trim(), name = name.trim(), makeModel = model.trim(), gymId = gymId)
            ?: Equipment(type = type.trim(), name = name.trim(), makeModel = model.trim(), gymId = gymId)
        dao.save(record); return record.id
    }
    suspend fun archiveGym(id: String) { dao.save(requireNotNull(dao.gym(id)).copy(isArchived = true)) }
    suspend fun archiveEquipment(id: String) { dao.save(requireNotNull(dao.equipment(id)).copy(isArchived = true)) }
    suspend fun archiveSetup(id: String) { dao.save(requireNotNull(dao.setup(id)).copy(isArchived = true)) }
    fun clean(configuration: EquipmentConfiguration): EquipmentConfiguration {
        val positions = configuration.positions.map { EquipmentPosition(it.name.trim(), it.position.trim()) }
            .filterNot { it.name.isEmpty() && it.position.isEmpty() }
        require(positions.all { it.name.isNotEmpty() && it.position.isNotEmpty() }) { "Enter both a name and position, or remove the unfinished position." }
        require(configuration.equipment.map { it.id }.distinct().size == configuration.equipment.size)
        require(configuration.equipmentSetups.all { it.equipment.size == 1 }) { "Each equipment option needs its own explicit setup association." }
        require(configuration.equipmentSetups.map { it.equipment.single().id }.distinct().size == configuration.equipmentSetups.size)
        return configuration.copy(positions = positions, setupName = configuration.setupName.trim(),
            equipmentSetups = configuration.equipmentSetups.map(::clean))
    }
    /** Refresh labels only when preparing a NEW session, never when reading a snapshot. */
    suspend fun snapshot(configuration: EquipmentConfiguration?, legacy: String): EquipmentConfiguration {
        val c = clean(configuration ?: EquipmentConfiguration())
        val identities = c.equipment.map { selected ->
            val e = requireNotNull(dao.equipment(selected.id)) { "Selected equipment is unavailable. Choose replacement equipment." }
            require(!e.isArchived) { "Selected equipment is archived. Choose replacement equipment." }
            val gym = e.gymId?.let { requireNotNull(dao.gym(it)) }
            require(gym?.isArchived != true) { "Selected gym is archived. Choose replacement equipment." }
            EquipmentIdentity(e.id, e.type, e.name, e.makeModel, e.gymId, gym?.name.orEmpty())
        }
        val selectedGym = c.gymId?.let { requireNotNull(dao.gym(it)) { "Selected gym is unavailable." } }
        require(selectedGym?.isArchived != true) { "Selected gym is archived. Choose replacement equipment or gym." }
        require(identities.all { it.gymId == null || c.gymId == null || it.gymId == c.gymId }) { "Choose equipment at the selected gym or portable equipment." }
        return c.copy(equipment = identities, legacyDescription = legacy, gymName = selectedGym?.name.orEmpty(), equipmentSetups = c.equipmentSetups.map { snapshot(it, legacy) })
    }
    suspend fun saveSetup(id: String?, name: String, compatibleType: String, config: EquipmentConfiguration): EquipmentConfiguration = database.withTransaction {
        require(name.isNotBlank()) { "Enter a setup name." }
        require(compatibleType.isNotBlank() || config.equipment.isNotEmpty()) { "Select equipment or enter a compatible equipment type." }
        val old = id?.let { requireNotNull(dao.setup(it)) }
        val setupId = old?.id ?: UUID.randomUUID().toString()
        val c = clean(config).copy(setupId = setupId, setupName = name.trim())
        dao.save(NamedEquipmentSetup(setupId, name.trim(), compatibleType.trim(), c, old?.isArchived ?: false)); c
    }
    suspend fun saveDefault(exerciseId: String, configuration: EquipmentConfiguration?) = database.withTransaction {
        val exercise = requireNotNull(database.exerciseDao().get(exerciseId))
        database.exerciseDao().update(exercise.copy(equipmentDefault = configuration?.let(::clean)))
    }
    suspend fun saveEntry(workoutId: String, entryId: String, configuration: EquipmentConfiguration?, asDefault: Boolean = false) = database.withTransaction {
        val workout = requireNotNull(database.workoutDao().getDetails(workoutId))
        require(workout.workout.kind in listOf("draft", "edit", "plan", "session"))
        require(!asDefault || workout.workout.kind != "session") { "Session corrections cannot change exercise defaults." }
        val entry = requireNotNull(workout.exercises.find { it.workoutExercise.id == entryId })
        val c = configuration?.let(::clean)
        if (asDefault) {
            saveDefault(entry.exercise.id, c)
            database.workoutExerciseDao().update(entry.workoutExercise.copy(equipmentOverride = null, equipmentPositions = emptyList()))
        } else if (workout.workout.kind == "session") {
            require(c != null) { "Session corrections need an explicit configuration." }
            database.workoutExerciseDao().update(entry.workoutExercise.copy(equipmentSnapshot = c, equipmentPositions = c.positions))
        } else database.workoutExerciseDao().update(entry.workoutExercise.copy(equipmentOverride = c, equipmentPositions = c?.positions.orEmpty()))
    }
    suspend fun saveEntryWithSetup(workoutId: String, entryId: String, config: EquipmentConfiguration?, asDefault: Boolean,
        namedSetup: String, compatibleType: String) = database.withTransaction {
        val result = if (namedSetup.isBlank()) config else saveSetup(null, namedSetup, compatibleType, requireNotNull(config))
        saveEntry(workoutId, entryId, result, asDefault)
    }
    suspend fun sessionCopy(entry: WorkoutExercise, exercise: Exercise): WorkoutExercise {
        val resolved = entry.equipmentOverride ?: exercise.equipmentDefault
            ?: entry.equipmentPositions.takeIf { it.isNotEmpty() }?.let { EquipmentConfiguration(positions = it) }
        val snapshot = snapshot(resolved, exercise.equipment)
        return entry.copy(equipmentSnapshot = snapshot, equipmentPositions = snapshot.positions)
    }
}
