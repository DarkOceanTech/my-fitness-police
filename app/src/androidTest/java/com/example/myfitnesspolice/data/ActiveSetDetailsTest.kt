package com.example.myfitnesspolice.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ActiveSetDetailsTest {
    @Test fun correctionsAndSetupPersistWithoutChangingTimersOrSourceWorkout() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "active-details-${java.util.UUID.randomUUID()}.db"
        fun open() = Room.databaseBuilder(context, FitnessDatabase::class.java, name).build()
        var db = open()
        suspend fun rejected(block: suspend () -> Unit) {
            try { block(); fail("Expected invalid edit to be rejected") } catch (_: IllegalArgumentException) { }
        }
        try {
            val repo = FitnessRepository(db)
            repo.addExercise("Machine row", "Machine")
            repo.chooseExercise(repo.observeExercises().first().single().id)
            val draft = repo.observeSessions().first().single()
            val planId = draft.workout.id
            val entryId = draft.exercises.single().workoutExercise.id
            repo.updateSetFields(planId, entryId, draft.orderedSets().single().id, grams = 12345)
            repo.saveEquipmentPositions(planId, entryId, listOf(EquipmentPosition("Seat", "4")))
            repo.savePlan(planId)
            val original = repo.observeWorkout(planId).first()!!
            val sessionId = repo.startPlan(planId)
            val initial = repo.observeWorkout(sessionId).first()!!
            val setId = initial.orderedSets().single().id
            val sessionEntry = initial.exercises.single().workoutExercise.id
            rejected { repo.correctActiveSet(sessionId, setId, null, 10, 8, 7) }
            rejected { repo.saveActiveSetNote(sessionId, setId, "Not completed") }
            repo.sessionProgress.completeSet(sessionId, setId)
            rejected { repo.correctActiveSet(sessionId, setId, null, 10, 8, 7) }
            repo.sessionProgress.recordActual(sessionId, setId, 8)
            val before = repo.observeWorkout(sessionId).first()!!
            rejected { repo.correctActiveSet(sessionId, original.orderedSets().single().id, null, 10, 8, 7) }
            rejected { repo.correctActiveSet(sessionId, setId, -1, 10, 8, 7) }
            rejected { repo.correctActiveSet(sessionId, setId, null, 0, 8, 7) }
            rejected { repo.correctActiveSet(sessionId, setId, null, 10, -1, 7) }
            rejected { repo.correctActiveSet(sessionId, setId, null, 10, 8, 11) }
            rejected { repo.saveEquipmentPositions(sessionId, entryId, listOf(EquipmentPosition("Seat", "6"))) }
            rejected { repo.saveEquipmentPositions(sessionId, sessionEntry, listOf(EquipmentPosition("Seat", ""))) }
            repo.correctActiveSet(sessionId, setId, 20000, 12, 9, 8)
            repo.correctActiveSet(sessionId, setId, null, 12, 9, 8)
            repo.saveActiveSetNote(sessionId, setId, "  Good control  ")
            repo.saveEquipmentPositions(sessionId, sessionEntry, listOf(EquipmentPosition(" Seat ", " 6 ")))
            val after = repo.observeWorkout(sessionId).first()!!
            assertEquals(before.sessionState, after.sessionState)
            assertEquals(before.orderedSets().single().activeMillis, after.orderedSets().single().activeMillis)
            assertEquals(before.orderedSets().single().restMillis, after.orderedSets().single().restMillis)
            assertEquals(before.orderedSets().single().completedAt, after.orderedSets().single().completedAt)
            assertEquals(original, repo.observeWorkout(planId).first())
            db.close()
            db = open()
            val loaded = db.workoutDao().getDetails(sessionId)!!
            assertEquals(20000L, loaded.orderedSets().single().weightGrams)
            assertEquals(12, loaded.orderedSets().single().reps)
            assertEquals(9, loaded.orderedSets().single().actualReps)
            assertEquals(8, loaded.orderedSets().single().rpe)
            assertEquals("Good control", loaded.orderedSets().single().notes)
            assertEquals(listOf(EquipmentPosition("Seat", "6")), loaded.exercises.single().workoutExercise.equipmentPositions)
            val reopened = FitnessRepository(db)
            reopened.finishWorkout(sessionId)
            rejected { reopened.correctActiveSet(sessionId, setId, null, 12, 9, 8) }
            rejected { reopened.saveActiveSetNote(sessionId, setId, "Finished") }
            rejected { reopened.saveEquipmentPositions(sessionId, sessionEntry, emptyList()) }
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
