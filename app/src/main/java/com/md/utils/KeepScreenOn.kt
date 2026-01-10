package com.md.utils

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.WindowManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.SpacedRepeaterActivity
import com.md.composeModes.Mode
import com.md.viewmodel.TopModeFlowProvider
import com.md.modesetters.TtsSpeaker
import com.md.composeModes.CurrentNotePartManager
import dagger.hilt.android.qualifiers.ActivityContext

import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Duration

import javax.inject.Inject


@ActivityScoped
class ClickToKeepAwakeProvider @Inject constructor() {
    private val _oneClickToScreenOn = MutableStateFlow(false)

    val oneClickToScreenOn: StateFlow<Boolean> = _oneClickToScreenOn

    fun enableOneClickToStayAwakeMode() { _oneClickToScreenOn.value = true }

    fun disableOneClickToStayAwakeMode() { _oneClickToScreenOn.value = false }
}

// TODO: perhaps i don't need this or can make it less extreme in dimming
// because pocket mode exists.
// Keywords: Keep awake keep alive. Screen wake. Keywords.
@ActivityScoped
class KeepScreenOn @Inject constructor(
    @ActivityContext private val context: Context,
    private val clickToKeepAwakeProvider: ClickToKeepAwakeProvider,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val currentNotePartManager: CurrentNotePartManager,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    private var initialBrightness: Float? = null
    private var dimScreenAfterBriefDelayMode: Boolean = false

    private var screenOnThenDelayThenIfNotCancelledOff: Job? = null
    private val tone  = ToneGenerator(AudioManager.STREAM_MUSIC, 50)

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

                // In staging mode (no active note), we don't want to show the "stay awake"
                // alert, and we want to allow the screen to turn off naturally.
                if (currentNotePartManager.noteStateFlow.value == null) {
                    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    return@repeatOnLifecycle
                }

                TtsSpeaker.speak(
                    "Click to stay awake.",
                    lowVolume = false
                )

                clickToKeepAwakeProvider.enableOneClickToStayAwakeMode()

                for (i in 1..DURATION_AFTER_WARNING_TO_SCREEN_OFF.seconds) {
                    tone.startTone(ToneGenerator.TONE_PROP_PROMPT,  /* durationMs = */ 100)
                    delay(Duration.ofSeconds(1).toMillis())
                }

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
        private val DURATION_AFTER_WARNING_TO_SCREEN_OFF = Duration.ofSeconds(60)
    }
}