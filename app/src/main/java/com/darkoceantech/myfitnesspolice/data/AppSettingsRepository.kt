package com.darkoceantech.myfitnesspolice.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.DayOfWeek

/** App preferences are independent of workout data and observable across settings/dashboard. */
class AppSettingsRepository(context: Context, preferenceName: String = "app_settings") : AutoCloseable {
    private val preferences = context.applicationContext.getSharedPreferences(preferenceName, Context.MODE_PRIVATE)
    private val mutableWeekStartsOn = MutableStateFlow(readWeekStartsOn())
    val weekStartsOn: StateFlow<DayOfWeek> = mutableWeekStartsOn.asStateFlow()
    private val mutableDefaultGymId = MutableStateFlow(preferences.getString(DEFAULT_GYM_ID, null))
    val defaultGymId: StateFlow<String?> = mutableDefaultGymId.asStateFlow()
    fun setDefaultGym(id: String?) {
        preferences.edit().putString(DEFAULT_GYM_ID, id).apply()
        mutableDefaultGymId.value = id
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == DEFAULT_GYM_ID) mutableDefaultGymId.value = preferences.getString(DEFAULT_GYM_ID, null)
        if (key == WEEK_STARTS_ON) mutableWeekStartsOn.value = readWeekStartsOn()
    }

    init { preferences.registerOnSharedPreferenceChangeListener(listener) }

    fun setWeekStartsOn(day: DayOfWeek) {
        require(day == DayOfWeek.MONDAY || day == DayOfWeek.SUNDAY) { "Choose Monday or Sunday." }
        preferences.edit().putString(WEEK_STARTS_ON, day.name).apply()
        mutableWeekStartsOn.value = day
    }

    private fun readWeekStartsOn(): DayOfWeek =
        if (preferences.getString(WEEK_STARTS_ON, null) == DayOfWeek.SUNDAY.name) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

    override fun close() { preferences.unregisterOnSharedPreferenceChangeListener(listener) }

    companion object {
        private const val DEFAULT_GYM_ID = "default_gym_id"
        private const val WEEK_STARTS_ON = "week_starts_on" }
}
