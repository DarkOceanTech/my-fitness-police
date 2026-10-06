package com.darkoceantech.myfitnesspolice.data

import org.junit.Assert.*
import org.junit.Test

class ExerciseSwapRecommendationsTest {
    @Test fun recommendsRelatedMusclesAndRanksMatchingEquipmentFirst() {
        val row = Exercise("row", "Row", "Cable", primaryMuscles = "Latissimus dorsi")
        val cable = Exercise("cable", "Z cable row", "cable", primaryMuscles = "Rhomboids")
        val barbell = Exercise("barbell", "A barbell row", "Barbell", primaryMuscles = "Latissimus dorsi")
        val bench = Exercise("bench", "Bench press", "Barbell", primaryMuscles = "Pectoralis major")
        assertEquals(listOf(cable, barbell), recommendedExerciseSwaps(row, listOf(barbell, bench, row, cable, cable.copy(id = "archived", isArchived = true))))
        assertTrue(recommendedExerciseSwaps(row.copy(primaryMuscles = ""), listOf(row)).isEmpty())
    }
}
