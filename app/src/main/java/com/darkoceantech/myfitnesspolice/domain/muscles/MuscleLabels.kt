package com.darkoceantech.myfitnesspolice.domain.muscles

/** Display names are separate from persisted enum identifiers (including BACK). */
object MuscleLabels {
    val canonical = listOf("Abdominal", "Biceps", "Calves", "Chest", "Forearms", "Glutes",
        "Hamstrings", "Lower Back", "Other", "Quads", "Shoulders", "Trapezius", "Triceps", "Upper Back")

    fun full(value: String): String {
        val trimmed = value.trim()
        if (trimmed.equals("Back", true)) return "Upper Back"
        if (trimmed.equals("Traps", true)) return "Trapezius"
        return canonical.firstOrNull { it.equals(trimmed, true) } ?: trimmed
    }

    fun abbreviated(value: String): String = when (val name = full(value)) {
        "Abdominal" -> "Abs"
        "Hamstrings" -> "Hams"
        "Shoulders" -> "Delts"
        "Upper Back" -> "U. Back"
        "Lower Back" -> "L. Back"
        "Trapezius" -> "Traps"
        else -> name
    }

    /** Only replace whole group tags; anatomical descriptions and exercise names are untouched. */
    fun displayTags(value: String): String = Regex("(?i)(^|[,;]\\s*)Back(?=\\s*([,;]|$))")
        .replace(value) { it.groupValues[1] + "Upper Back" }
}
