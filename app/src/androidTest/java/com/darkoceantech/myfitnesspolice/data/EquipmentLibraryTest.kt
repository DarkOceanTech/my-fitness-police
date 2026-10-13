package com.darkoceantech.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class EquipmentLibraryTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun open(name: String) = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
    private suspend fun fixture(db: FitnessDatabase): Pair<Exercise, EquipmentConfiguration> {
        val lib = EquipmentLibraryRepository(db)
        val gymId = lib.saveGym(null, "Gym A")
        val equipmentId = lib.saveEquipment(null, "Cable machine", "Row station", "Model A", gymId)
        val config = EquipmentConfiguration(equipment = listOf(EquipmentIdentity(equipmentId, "Cable machine", "Row station", "Model A", gymId, "Gym A")),
            positions = listOf(EquipmentPosition("Seat", "4"), EquipmentPosition("Angle", "30°")))
        val named = lib.saveSetup(null, "My row setup", "Cable machine", config)
        val exercise = Exercise(name = "Cable row", equipment = "Cable; dual handles", equipmentDefault = named)
        db.exerciseDao().insert(exercise)
        return exercise to named
    }
    private suspend fun workout(db: FitnessDatabase, exercise: Exercise, config: EquipmentConfiguration? = null): Workout {
        val w = Workout(kind = "plan", name = "Back")
        db.workoutDao().insert(w)
        val e = WorkoutExercise(workoutId = w.id, exerciseId = exercise.id, position = 0, equipmentOverride = config)
        db.workoutExerciseDao().insert(e)
        db.workoutSetDao().insert(WorkoutSet(workoutExerciseId = e.id, position = 0, reps = 10, weightGrams = 10000))
        return w
    }
    @Test fun gymAssignmentWithoutMachinePersistsAndSnapshotsItsName() = runBlocking {
        val name = "gym-only-" + UUID.randomUUID() + ".db"
        var db = open(name)
        try {
            val repo = FitnessRepository(db)
            val gym = repo.equipmentLibrary.saveGym(null, "Bodyweight room")
            val config = EquipmentConfiguration(gymId = gym, gymName = "Bodyweight room")
            val e = Exercise(name = "Push-up", equipment = "Bodyweight", equipmentDefault = config)
            db.exerciseDao().insert(e)
            val w = workout(db, e)
            val planId = repo.trainingPlans.save(null, "Bodyweight", listOf(w.id))
            db.close(); db = open(name)
            val sessionId = FitnessRepository(db).startTraining(planId)
            val snap = db.workoutDao().getDetails(sessionId)!!.exercises.single().workoutExercise.equipmentSnapshot!!
            assertEquals(gym, snap.gymId); assertEquals("Bodyweight room", snap.gymName)
            FitnessRepository(db).equipmentLibrary.saveGym(gym, "Renamed room")
            assertEquals(snap, db.workoutDao().getDetails(sessionId)!!.exercises.single().workoutExercise.equipmentSnapshot)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun recordsAndNamedSetupsSurviveReopenWithMultipleEquipment() = runBlocking {
        val name = "equipment-library-" + UUID.randomUUID() + ".db"
        var db = open(name)
        try {
            val (exercise, config) = fixture(db)
            val lib = EquipmentLibraryRepository(db)
            val second = lib.saveEquipment(null, "Handle", "Row station", "", null)
            val state = lib.observe().first()
            val full = config.copy(equipment = config.equipment + state.identity(state.equipment.single { it.id == second }))
            lib.saveDefault(exercise.id, full)
            db.close(); db = open(name)
            assertEquals(full, db.exerciseDao().get(exercise.id)!!.equipmentDefault)
            assertEquals("Cable; dual handles", db.exerciseDao().get(exercise.id)!!.equipment)
            assertEquals(2, EquipmentLibraryRepository(db).observe().first().equipment.size)
            assertEquals(config, db.equipmentLibraryDao().setup(config.setupId!!)!!.configuration)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun sessionSnapshotsIgnoreLibraryRenamesDefaultsEditsAndArchives() = runBlocking {
        val db = open("equipment-snapshot-" + UUID.randomUUID() + ".db")
        try {
            val (exercise, config) = fixture(db)
            val w = workout(db, exercise)
            val repo = FitnessRepository(db)
            val planId = repo.trainingPlans.save(null, "Back day", listOf(w.id))
            val sessionId = repo.startTraining(planId)
            val entry = db.workoutDao().getDetails(sessionId)!!.exercises.single()
            val original = entry.workoutExercise.equipmentSnapshot!!
            repo.sessionProgress.startSet(sessionId, entry.sets.single().id)
            repo.sessionProgress.pause(sessionId)
            assertTrue(db.workoutDao().getDetails(sessionId)!!.sessionState!!.isPaused)
            assertEquals("Gym A", original.equipment.single().gymName)
            assertEquals("Cable; dual handles", original.legacyDescription)
            repo.equipmentLibrary.saveGym(config.equipment.single().gymId, "Renamed gym")
            repo.equipmentLibrary.saveEquipment(config.equipment.single().id, "Cable machine", "Renamed station", "Model B", null)
            val changed = config.copy(positions = listOf(EquipmentPosition("Seat", "9")))
            repo.equipmentLibrary.saveSetup(config.setupId, "Updated row", "Cable machine", changed)
            repo.equipmentLibrary.saveDefault(exercise.id, changed)
            assertEquals(original, db.workoutDao().getDetails(sessionId)!!.exercises.single().workoutExercise.equipmentSnapshot)
            repo.equipmentLibrary.saveEntry(sessionId, entry.workoutExercise.id, original.copy(positions = listOf(EquipmentPosition("Seat", "6"))))
            assertEquals(changed, db.exerciseDao().get(exercise.id)!!.equipmentDefault)
            val corrected = db.workoutDao().getDetails(sessionId)!!.exercises.single().workoutExercise.equipmentSnapshot
            db.workoutDao().update(db.workoutDao().getDetails(sessionId)!!.workout.copy(finishedAt = System.currentTimeMillis()))
            repo.equipmentLibrary.archiveEquipment(config.equipment.single().id)
            repo.equipmentLibrary.archiveGym(config.equipment.single().gymId!!)
            repo.equipmentLibrary.archiveSetup(config.setupId!!)
            assertEquals(corrected, db.workoutDao().getDetails(sessionId)!!.exercises.single().workoutExercise.equipmentSnapshot)
            val count = repo.observeSessions().first().size
            try { repo.startTraining(planId); fail("Archived machine must require replacement") } catch (_: IllegalArgumentException) { }
            assertEquals(count, repo.observeSessions().first().size)
        } finally { val file = db.openHelper.writableDatabase.path; db.close(); context.deleteDatabase(java.io.File(requireNotNull(file)).name) }
    }
    @Test fun overrideAndExplicitEmptyBeatDefaultAndDirectPlanUsesSameResolution() = runBlocking {
        val name = "equipment-precedence-" + UUID.randomUUID() + ".db"
        val db = open(name)
        try {
            val (exercise, config) = fixture(db)
            val override = config.copy(positions = listOf(EquipmentPosition("Seat", "8")))
            val w = workout(db, exercise, override)
            val repo = FitnessRepository(db)
            val plan = repo.trainingPlans.saveItems(null, "Mixed", listOf(TrainingPlanItem.WorkoutItem(w.id),
                TrainingPlanItem.ExerciseItem(exercise.id, equipmentOverride = EquipmentConfiguration())))
            val session = db.workoutDao().getDetails(repo.startTraining(plan))!!
            assertEquals(override.positions, session.orderedExercises()[0].workoutExercise.equipmentSnapshot!!.positions)
            assertTrue(session.orderedExercises()[1].workoutExercise.equipmentSnapshot!!.positions.isEmpty())
            assertTrue(session.orderedExercises()[1].workoutExercise.equipmentSnapshot!!.equipment.isEmpty())
            db.workoutDao().update(session.workout.copy(finishedAt = System.currentTimeMillis()))
            repo.trainingPlans.saveItems(plan, "Mixed", listOf(TrainingPlanItem.ExerciseItem(exercise.id)))
            val next = db.workoutDao().getDetails(repo.startTraining(plan))!!.exercises.single()
            assertEquals(config.positions, next.workoutExercise.equipmentSnapshot!!.positions)
        } finally { db.close(); context.deleteDatabase(name) }
    }
    @Test fun editorCancelDoesNotChangeWorkoutAndSetImportDoesNotReplaceEquipment() = runBlocking {
        val name = "equipment-draft-" + UUID.randomUUID() + ".db"
        val db = open(name)
        try {
            val (exercise, config) = fixture(db)
            val w = workout(db, exercise, config)
            val repo = FitnessRepository(db)
            val edit = repo.workoutEditor.begin(w.id)
            val entry = db.workoutDao().getDetails(edit)!!.exercises.single()
            repo.equipmentLibrary.saveEntry(edit, entry.workoutExercise.id, EquipmentConfiguration())
            assertEquals(config, db.workoutDao().getDetails(w.id)!!.exercises.single().workoutExercise.equipmentOverride)
            repo.workoutEditor.discard(w.id)
            val session = repo.startTraining(repo.trainingPlans.save(null, "Back", listOf(w.id)))
            val recorded = db.workoutDao().getDetails(session)!!
            db.workoutSetDao().update(recorded.orderedSets().single().copy(completedAt = System.currentTimeMillis(), actualReps = 8))
            db.workoutDao().update(recorded.workout.copy(finishedAt = System.currentTimeMillis()))
            val target = db.workoutDao().getDetails(w.id)!!.exercises.single()
            repo.importLastSession(w.id, target.workoutExercise.id)
            assertEquals(config, db.workoutDao().getDetails(w.id)!!.exercises.single().workoutExercise.equipmentOverride)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
