package com.example.myfitnesspolice

import android.app.Application
import com.example.myfitnesspolice.data.FitnessDatabase

class FitnessApplication : Application() {
    val database by lazy { FitnessDatabase.getInstance(this) }
    val repository by lazy { com.example.myfitnesspolice.data.FitnessRepository(database) }
    // Room opens and seeds on the first Flow collection, off the UI thread.
}
