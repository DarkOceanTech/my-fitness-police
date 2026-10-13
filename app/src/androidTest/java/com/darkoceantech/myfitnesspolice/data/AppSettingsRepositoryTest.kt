package com.darkoceantech.myfitnesspolice.data

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.DayOfWeek
import java.util.UUID

class AppSettingsRepositoryTest {
    @Test fun mondayDefaultAndWeekStartPersistAndNotifyOtherObservers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "settings-test-${UUID.randomUUID()}"
        val first = AppSettingsRepository(context, preferencesName)
        val observer = AppSettingsRepository(context, preferencesName)
        try {
            assertEquals(DayOfWeek.MONDAY, first.weekStartsOn.value)
            first.setWeekStartsOn(DayOfWeek.SUNDAY)
            assertEquals(DayOfWeek.SUNDAY, first.weekStartsOn.value)
            assertEquals(DayOfWeek.SUNDAY, withTimeout(5000) { observer.weekStartsOn.first { it == DayOfWeek.SUNDAY } })
            AppSettingsRepository(context, preferencesName).use { reloaded ->
                assertEquals(DayOfWeek.SUNDAY, reloaded.weekStartsOn.value)
                reloaded.setWeekStartsOn(DayOfWeek.MONDAY)
                assertEquals(DayOfWeek.MONDAY, withTimeout(5000) { first.weekStartsOn.first { it == DayOfWeek.MONDAY } })
            }
            try { first.setWeekStartsOn(DayOfWeek.WEDNESDAY); fail("Unsupported week start accepted") }
            catch (_: IllegalArgumentException) { }
            assertEquals(DayOfWeek.MONDAY, first.weekStartsOn.value)
        } finally {
            first.close(); observer.close()
            context.getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
