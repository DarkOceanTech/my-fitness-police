package com.darkoceantech.myfitnesspolice.data

/** Only prescription fields can be changed; recorded results and timing are never submitted. */
data class PlannedSetUpdate(val id: String, val reps: Int, val weightGrams: Long,
    val isWarmup: Boolean, val modifier: String)
