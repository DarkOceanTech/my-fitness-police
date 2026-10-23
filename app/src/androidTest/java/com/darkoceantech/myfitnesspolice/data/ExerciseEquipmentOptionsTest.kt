package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.darkoceantech.myfitnesspolice.ui.screens.compatibleExerciseSetups
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class ExerciseEquipmentOptionsTest {
    @Test fun optionsHaveIndependentSetupsAndSnapshotsSurviveLibraryEdits() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "equipment-options-" + UUID.randomUUID() + ".db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        try {
            var repo = EquipmentLibraryRepository(db)
            val gym = repo.saveGym(null, "North gym")
            val ids = (1..3).map { repo.saveEquipment(null, "Cable", "Station $it", "Model $it", gym) }
            val library = repo.observe().first()
            val options = ids.map { id -> library.identity(library.equipment.single { it.id == id }) }
            val setups = options.mapIndexed { i, e -> repo.saveSetup(null, "Setup $i", "Cable",
                EquipmentConfiguration(equipment = listOf(e), gymId = gym, gymName = "North gym",
                    positions = listOf(EquipmentPosition("Seat", (i + 2).toString())))) }
            val exercise = Exercise(name = "Row", equipment = "Cable alternatives")
            db.exerciseDao().insert(exercise)
            for (count in 1..3) {
                val config = EquipmentConfiguration(compatibleEquipment = options.take(count), equipmentSetups = setups.take(count))
                repo.saveDefault(exercise.id, config)
                db.close(); db = open(); repo = EquipmentLibraryRepository(db)
                assertEquals(config, db.exerciseDao().get(exercise.id)!!.equipmentDefault)
                val matching = compatibleExerciseSetups(config, repo.observe().first())
                assertEquals(count, matching.size) // A second alternative no longer hides the first setup.
                assertEquals(count, config.equipmentSetups.map { it.positions }.distinct().size)
            }
            val full = db.exerciseDao().get(exercise.id)!!.equipmentDefault!!
            val secondExercise = exercise.copy(id = UUID.randomUUID().toString(), name = "Another row")
            db.exerciseDao().insert(secondExercise)
            repo.saveDefault(secondExercise.id, full)
            val snapshot = repo.snapshot(full, exercise.equipment)
            val workout = Workout(kind = "session", name = "History", startedAt = 1000, finishedAt = 2000)
            db.workoutDao().insert(workout)
            val entry = WorkoutExercise(workoutId = workout.id, exerciseId = exercise.id, position = 0, equipmentSnapshot = snapshot)
            db.workoutExerciseDao().insert(entry)
            repo.saveGym(gym, "Renamed gym")
            repo.saveEquipment(ids[0], "Cable", "Renamed station", "New model", gym)
            repo.saveSetup(setups[0].setupId, "Changed setup", "Cable",
                setups[0].copy(positions = listOf(EquipmentPosition("Seat", "99"))))
            repo.saveDefault(exercise.id, full.copy(equipmentSetups = listOf(setups[2])))
            db.close(); db = open()
            assertEquals(snapshot, db.workoutDao().getDetails(workout.id)!!.exercises.single().workoutExercise.equipmentSnapshot)
            assertEquals(full, db.exerciseDao().get(secondExercise.id)!!.equipmentDefault)
            assertEquals(3, EquipmentLibraryRepository(db).observe().first().equipment.size)
        } finally { db.close(); context.deleteDatabase(name) }
    }

    @Test fun oldConfigurationJsonRemainsReadableWithoutGuessingAssociations() {
        val c = EquipmentConfiguration(equipment = listOf(EquipmentIdentity("old", "Cable", "Old machine")),
            positions = listOf(EquipmentPosition("Seat", "4")))
        val codec = EquipmentConfigurationConverters()
        val json = JSONObject(codec.encode(c)!!)
        json.remove("compatibleEquipment"); json.remove("equipmentSetups")
        assertEquals(c, codec.decode(json.toString()))
        assertTrue(codec.decode(json.toString())!!.equipmentSetups.isEmpty())
    }
}
