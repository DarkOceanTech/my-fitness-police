package com.darkoceantech.myfitnesspolice.ui.screens

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.darkoceantech.myfitnesspolice.data.StretchPreferences

@Composable
internal fun MobilityTimerCues(model: StretchViewModel, preferences: StretchPreferences) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val current by rememberUpdatedState(preferences)
    LaunchedEffect(model, lifecycle) {
        model.cues.collect {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                if (current.sound) runCatching {
                    val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 65)
                    try { tone.startTone(ToneGenerator.TONE_PROP_BEEP, 150); kotlinx.coroutines.delay(180) }
                    finally { tone.release() }
                }
                if (current.vibration) runCatching {
                    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                    if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                    else @Suppress("DEPRECATION") vibrator.vibrate(120)
                }
            }
        }
    }
}
