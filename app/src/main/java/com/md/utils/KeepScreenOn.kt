package com.md.utils

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import dagger.hilt.android.qualifiers.ActivityContext

import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

import javax.inject.Inject

// Keywords: Keep awake keep alive. Screen wake. Keywords.. we did it?
@ActivityScoped
class KeepScreenOn @Inject constructor(
    @ActivityContext private val context: Context
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    var firstSavedBrightness: Float? = null

    private var screenOnThenDelayThenIfNotCancelledOff: Job? = null
    fun keepScreenOn(dimScreen: Boolean = false) {
        screenOnThenDelayThenIfNotCancelledOff?.cancel()
        screenOnThenDelayThenIfNotCancelledOff = activity.lifecycleScope.launch {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

            activity.window.attributes = activity.window.attributes.also {
               val brightness = firstSavedBrightness
                if (brightness == null) {
                   firstSavedBrightness = it.screenBrightness
               } else {
                   it.screenBrightness = brightness
               }
            }

            delay(DURATION_UNTIL_DARK.toMillis())

            if (dimScreen) {
                activity.window.attributes = activity.window.attributes.also {
                    it.screenBrightness = 0f
                }
            }


            delay(DURATION_UNTIL_WARNING.toMillis())
            TtsSpeaker.speak("screen off soon.", lowVolume = false)
            delay(DURATION_AFTER_WARNING_TO_SCREEN_OFF.toMillis())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        private val DURATION_UNTIL_DARK = Duration.ofSeconds(5)
        private val DURATION_UNTIL_WARNING = Duration.ofSeconds(60)
        private val DURATION_AFTER_WARNING_TO_SCREEN_OFF = Duration.ofSeconds(10)
    }
}