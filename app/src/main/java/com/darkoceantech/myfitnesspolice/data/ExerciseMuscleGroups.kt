package com.darkoceantech.myfitnesspolice.data

import java.util.Locale

/** One main browsing group per movement. Detailed anatomical roles remain on Exercise. */
enum class MuscleGroup(val label: String) {
    CHEST("Chest"), BACK("Back"), SHOULDERS("Shoulders"), BICEPS("Biceps"), TRICEPS("Triceps"), FOREARMS("Forearms"),
    QUADS("Quads"), HAMSTRINGS("Hamstrings"), GLUTES("Glutes"), CALVES("Calves"),
    ABDOMINAL("Abdominal"), LOWER_BACK("Lower Back"), OTHER("Other"),
}

private val catalogGroups: Map<Int, MuscleGroup> = buildMap {
    fun group(value: MuscleGroup, vararg ids: Int) { ids.forEach { put(it, value) } }
    group(MuscleGroup.CHEST, 2, 15, 16, 17, 18, 19, 20)
    group(MuscleGroup.BACK, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 25)
    group(MuscleGroup.SHOULDERS, 4, 21, 22, 23, 24)
    group(MuscleGroup.BICEPS, 26, 27, 28, 29)
    group(MuscleGroup.TRICEPS, 30, 31, 32)
    group(MuscleGroup.QUADS, 1, 33, 34, 35, 36, 37, 38, 39, 43)
    group(MuscleGroup.HAMSTRINGS, 3, 40, 44)
    group(MuscleGroup.GLUTES, 41, 42)
    group(MuscleGroup.CALVES, 45, 46)
    group(MuscleGroup.ABDOMINAL, 47, 48)
}

fun Exercise.mainMuscleGroup(): MuscleGroup {
    // Stable seed IDs classify compound movements consistently (for example, close-grip bench = Triceps).
    val prefix = "e12d7b3e-588b-4fc2-8d3f-"
    if (id.startsWith(prefix)) catalogGroups[id.removePrefix(prefix).toIntOrNull()]?.let { return it }
    // Custom exercises use the first recognized primary muscle; missing data stays accessible under Other.
    for (part in primaryMuscles.lowercase(Locale.ROOT).split(';', ',')) {
        val muscle = part.trim()
        when {
            listOf("biceps femoris", "semitendinosus", "semimembranosus", "hamstring").any { it in muscle } -> return MuscleGroup.HAMSTRINGS
            listOf("pectoralis", "chest").any { it in muscle } -> return MuscleGroup.CHEST
            listOf("erector spinae", "iliocostalis", "longissimus", "spinalis", "multifidus", "quadratus lumborum", "lower back").any { it in muscle } -> return MuscleGroup.LOWER_BACK
            listOf("latissimus", "rhomboid", "trapezius", "teres major", "back").any { it in muscle } -> return MuscleGroup.BACK
            listOf("deltoid", "shoulder", "supraspinatus", "infraspinatus", "teres minor", "subscapularis").any { it in muscle } -> return MuscleGroup.SHOULDERS
            "triceps" in muscle -> return MuscleGroup.TRICEPS
            listOf("forearm", "brachioradialis", "flexor carpi", "extensor carpi", "flexor digitorum", "extensor digitorum", "pronator", "supinator").any { it in muscle } -> return MuscleGroup.FOREARMS
            listOf("biceps brachii", "brachialis").any { it in muscle } || muscle == "biceps" -> return MuscleGroup.BICEPS
            listOf("quadriceps", "quads", "vastus", "rectus femoris").any { it in muscle } -> return MuscleGroup.QUADS
            "glute" in muscle -> return MuscleGroup.GLUTES
            listOf("gastrocnemius", "soleus", "calves", "calf").any { it in muscle } -> return MuscleGroup.CALVES
            listOf("abdominis", "abdominal", "oblique", "core").any { it in muscle } -> return MuscleGroup.ABDOMINAL
        }
    }
    return MuscleGroup.OTHER
}

fun filterByMuscleGroups(exercises: List<Exercise>, selected: Set<MuscleGroup>): List<Exercise> =
    exercises.filter { it.mainMuscleGroup() in selected }
