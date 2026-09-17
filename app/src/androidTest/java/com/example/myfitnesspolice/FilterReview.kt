package com.example.myfitnesspolice

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.test.platform.app.InstrumentationRegistry

internal fun captureFilterReview(compose: ComposeContentTestRule, name: String) {
    compose.waitForIdle()
    android.os.SystemClock.sleep(500)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    val folder = java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "filter-keyboard-review").apply { mkdirs() }
    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    try { java.io.File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
    finally { bitmap.recycle() }
}
