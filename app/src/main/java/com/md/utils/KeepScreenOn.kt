package com.md.utils

import android.content.Context
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.SpacedRepeaterActivity
import com.md.composeModes.Mode
import com.md.viewmodel.TopModeFlowProvider
import com.md.modesetters.TtsSpeaker
import dagger.hilt.android.qualifiers.ActivityContext

import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

import javax.inject.Inject

// TODO: perhaps i don't need this or can make it less extreme in dimming
// because pocket mode exists.
// Keywords: Keep awake keep alive. Screen wake. Keywords.. we did it?
@ActivityScoped
class KeepScreenOn @Inject constructor(
    @ActivityContext private val context: Context,
    private val topModeFlowProvider: TopModeFlowProvider,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    private var initialBrightness: Float? = null
    private var dimScreenAfterBriefDelayMode: Boolean = false

    private var screenOnThenDelayThenIfNotCancelledOff: Job? = null

    init {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                topModeFlowProvider.modeModel.collect { mode ->
                    val job = screenOnThenDelayThenIfNotCancelledOff
                    if (mode != Mode.Practice && job != null) {
                        job.cancel()
                        screenOnThenDelayThenIfNotCancelledOff = null
                        if (ENABLE_BRIGHTNESS_CONTROL) {
                            restoreInitialBrightness()
                        }

                        // Avoid: BT press -> different top level mode -> Practice -> Dim
                        dimScreenAfterBriefDelayMode = false
                    }
                }
            }
        }
    }

    fun keepScreenOn(updatedDimScreenAfterBriefDelay: Boolean = false, extraScreenOnDuration: Duration? = null) {
        dimScreenAfterBriefDelayMode = updatedDimScreenAfterBriefDelay
        screenOnThenDelayThenIfNotCancelledOff?.cancel()
        screenOnThenDelayThenIfNotCancelledOff = activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (topModeFlowProvider.modeModel.value != Mode.Practice) {
                    return@repeatOnLifecycle
                }
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

                if (extraScreenOnDuration != null) {
                    delay(extraScreenOnDuration.toMillis())
                }

                if (ENABLE_BRIGHTNESS_CONTROL) {
                    restoreInitialBrightness()
                    if (dimScreenAfterBriefDelayMode) {
                        delay(DURATION_UNTIL_DARK.toMillis())
                        activity.window.attributes = activity.window.attributes.also {
                            it.screenBrightness = 0f
                        }
                    }
                }


                delay(DURATION_UNTIL_WARNING.toMillis())
                TtsSpeaker.speak(
                    "screen off in ${DURATION_AFTER_WARNING_TO_SCREEN_OFF.seconds} seconds",
                    lowVolume = false
                )
                delay(DURATION_AFTER_WARNING_TO_SCREEN_OFF.toMillis())
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                cancel()
            }
        }
    }

    private fun restoreInitialBrightness() {
        if (ENABLE_BRIGHTNESS_CONTROL) {
            activity.window.attributes = activity.window.attributes.also {
                val brightness = initialBrightness
                if (brightness == null) {
                    initialBrightness = it.screenBrightness
                } else {
                    it.screenBrightness = brightness
                }
            }
        }
    }

    companion object {
        private const val ENABLE_BRIGHTNESS_CONTROL = false
        private val DURATION_UNTIL_DARK = Duration.ofSeconds(3)
        private val DURATION_UNTIL_WARNING = Duration.ofSeconds(60)
        private val DURATION_AFTER_WARNING_TO_SCREEN_OFF = Duration.ofSeconds(30)
    }
}