package com.darkoceantech.myfitnesspolice.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.darkoceantech.myfitnesspolice.domain.stretch.StretchMovement

internal object StretchCatalog {
    val starter = listOf(
        StretchMovement("stretch-hamstring", "Seated hamstring stretch", "Sit tall with one leg extended. Hinge gently at the hips toward that leg without rounding or bouncing. Keep the sensation comfortable.", "Biceps femoris; Semitendinosus; Semimembranosus", separateSides = true),
        StretchMovement("stretch-calf", "Standing calf stretch", "Place your hands on a wall. Step one foot back, keep that knee straight and heel down, and lean forward gently. Repeat on the other side.", "Gastrocnemius", "Wall", separateSides = true),
        StretchMovement("stretch-chest", "Doorway chest stretch", "Rest both forearms on the doorway at a comfortable height. Step forward a little until you feel a gentle stretch across the chest. Do not force the shoulders.", "Pectoralis major; Pectoralis minor", "Doorway"),
        StretchMovement("stretch-triceps", "Overhead triceps stretch", "Raise one arm and bend the elbow so the hand reaches behind your head. Guide the elbow gently with the other hand; keep your neck relaxed.", "Triceps brachii", separateSides = true),
        StretchMovement("stretch-glute", "Supine figure-four stretch", "Lie on your back. Place one ankle over the opposite thigh and gently bring that thigh toward you. Keep the crossed knee comfortable.", "Gluteus maximus; Piriformis", "Mat", separateSides = true),
        StretchMovement("stretch-cat-cow", "Cat–cow mobility", "On hands and knees, slowly alternate gentle spinal rounding and extension. Move within a comfortable range and keep breathing.", "Erector spinae; Rectus abdominis", "Mat", "Moving mobility"),
        StretchMovement("stretch-shoulder", "Shoulder circles", "Stand tall with relaxed arms. Make small, controlled shoulder circles in both directions without forcing the range.", "Deltoid; Trapezius", movementType = "Moving mobility")
    )
    fun seed(db: SupportSQLiteDatabase) {
        starter.forEach { db.execSQL("INSERT OR IGNORE INTO stretch_catalog (id, payload) VALUES (?, ?)",
            arrayOf(it.id, StretchJson.movement(it).toString())) }
    }
}
