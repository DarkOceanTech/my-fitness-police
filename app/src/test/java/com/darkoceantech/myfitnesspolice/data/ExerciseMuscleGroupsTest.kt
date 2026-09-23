package com.darkoceantech.myfitnesspolice.data

import org.junit.Assert.*
import org.junit.Test

class ExerciseMuscleGroupsTest {
    @Test fun catalogHasExactlyOneUsefulGroupPerExercise() {
        val catalog = ExerciseCatalog.exercises
        assertEquals(48, catalog.size)
        assertTrue(catalog.none { it.mainMuscleGroup() == MuscleGroup.OTHER })
        // The original catalog has no dedicated forearm or lower-back movement. Keep those filters available for custom entries.
        assertEquals(MuscleGroup.entries.toSet() - setOf(MuscleGroup.OTHER, MuscleGroup.FOREARMS, MuscleGroup.LOWER_BACK),
            catalog.map { it.mainMuscleGroup() }.toSet())
        assertEquals(2, catalog.count { it.mainMuscleGroup() == MuscleGroup.ABDOMINAL })
        assertEquals(48, catalog.groupBy { it.mainMuscleGroup() }.values.sumOf { it.size })
        assertEquals(11, catalog.count { it.mainMuscleGroup() == MuscleGroup.BACK })
    }
    @Test fun allThenExcludeAndMultipleGroupSelectionsHaveNoDuplicateOrLeakingResults() {
        val catalog = ExerciseCatalog.exercises
        assertEquals(catalog, filterByMuscleGroups(catalog, MuscleGroup.entries.toSet()))
        assertTrue(filterByMuscleGroups(catalog, emptySet()).isEmpty())
        val noChest = filterByMuscleGroups(catalog, MuscleGroup.entries.toSet() - MuscleGroup.CHEST)
        assertEquals(41, noChest.size)
        assertTrue(noChest.none { it.mainMuscleGroup() == MuscleGroup.CHEST })
        val arms = filterByMuscleGroups(catalog, setOf(MuscleGroup.BICEPS, MuscleGroup.TRICEPS))
        assertEquals(7, arms.size)
        assertEquals(7, arms.map { it.id }.toSet().size)
    }
    @Test fun customAnatomicalFieldsAndMissingMetadataRemainBrowsable() {
        fun exercise(primary: String) = Exercise(name = "Custom", equipment = "", primaryMuscles = primary)
        assertEquals(MuscleGroup.HAMSTRINGS, exercise("Biceps femoris; semitendinosus").mainMuscleGroup())
        assertEquals(MuscleGroup.BICEPS, exercise("Biceps brachii; brachialis").mainMuscleGroup())
        assertEquals(MuscleGroup.BACK, exercise("Latissimus dorsi").mainMuscleGroup())
        assertEquals(MuscleGroup.FOREARMS, exercise("Brachioradialis; extensor carpi radialis longus").mainMuscleGroup())
        assertEquals(MuscleGroup.FOREARMS, exercise("Flexor carpi radialis; flexor carpi ulnaris").mainMuscleGroup())
        assertEquals(MuscleGroup.LOWER_BACK, exercise("Erector spinae (iliocostalis, longissimus, spinalis)").mainMuscleGroup())
        assertEquals(MuscleGroup.LOWER_BACK, exercise("Lower back").mainMuscleGroup())
        assertEquals(MuscleGroup.ABDOMINAL, exercise("Rectus abdominis; external oblique").mainMuscleGroup())
        val entries = listOf(exercise("Forearms"), exercise("Multifidus"), exercise("Transversus abdominis"))
        assertEquals(entries, filterByMuscleGroups(entries, setOf(MuscleGroup.FOREARMS, MuscleGroup.LOWER_BACK, MuscleGroup.ABDOMINAL)))
        assertEquals(entries.drop(1), filterByMuscleGroups(entries, MuscleGroup.entries.toSet() - MuscleGroup.FOREARMS))
        val unknown = exercise("")
        assertEquals(MuscleGroup.OTHER, unknown.mainMuscleGroup())
        assertEquals(listOf(unknown), filterByMuscleGroups(listOf(unknown), MuscleGroup.entries.toSet()))
    }
}
