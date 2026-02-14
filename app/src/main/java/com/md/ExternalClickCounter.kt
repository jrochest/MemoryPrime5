package com.md

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.md.modesetters.PracticeModeStateHandler
import com.md.modesetters.TtsSpeaker
import com.md.utils.ClickToKeepAwakeProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.*
import javax.inject.Inject

/**
 * Click interaction modes for the two-mode system.
 *
 * Default: Normal practice mode with FSRS-style ratings.
 * Secondary: Dangerous actions (postpone, delete) requiring confirmation.
 * PendingPostpone: Awaiting triple-click confirmation to postpone.
 * PendingDelete: Awaiting triple-click confirmation to delete.
 */
enum class ClickMode {
    Default,
    Secondary,
    PendingPostpone,
    PendingDelete
}

@ActivityScoped
class ExternalClickCounter

    @Inject
    constructor(
        private val practiceModeHandler: PracticeModeStateHandler,
        private val clickToKeepAwakeProvider: ClickToKeepAwakeProvider,
    ) {

    @ActivityContext @Inject lateinit var context: Context
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    inner class PressGroupInstanceData {
        val isInClickToKeepAwakeMode = clickToKeepAwakeProvider.oneClickToScreenOn.value
        var pressGroupCount = 1
            private set

        fun incrementCount() {
            pressGroupCount++
        }
    }

    private var pressGroupLastPressMs: Long = 0
    private var pressGroupLastPressEventMs: Long = 0
    private var pressGroupInstanceData: PressGroupInstanceData? = null

    private var currentJob: Job? = null
    private var previousPressGroupGapMillis: Long = 0

    /** Current interaction mode. */
    private var clickMode: ClickMode = ClickMode.Default

    /**
     * Rapid-succession gap for triple-click confirmation in pending modes.
     * Clicks must be within this window to count as a rapid group.
     */
    private val confirmationRapidClickGapMs = 300L

    fun handleRhythmUiTaps(eventTimeMs: Long, pressGroupMaxGapMs: Long): Boolean {
        val handler = practiceModeHandler
        currentJob?.cancel()
        currentJob = null
        var pressGroupInstanceDataLocal = pressGroupInstanceData
        val currentTimeMs = SystemClock.uptimeMillis()
        if (pressGroupInstanceDataLocal == null) {
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
        } else if (pressGroupLastPressEventMs + previousPressGroupGapMillis < eventTimeMs) {
            // Large gap. Reset count.
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
        } else {
            pressGroupInstanceDataLocal.incrementCount()
        }
        pressGroupLastPressEventMs = eventTimeMs
        pressGroupLastPressMs = currentTimeMs

        val pressGroupCount = pressGroupInstanceDataLocal.pressGroupCount

        // Determine gap based on mode and click count.
        val pressGroupMaxGapMsOverride: Long = when (clickMode) {
            ClickMode.Default -> getDefaultModeGap(pressGroupCount, pressGroupMaxGapMs)
            ClickMode.Secondary -> getSecondaryModeGap(pressGroupCount)
            ClickMode.PendingPostpone, ClickMode.PendingDelete -> confirmationRapidClickGapMs
        }

        previousPressGroupGapMillis = pressGroupMaxGapMsOverride
        currentJob = activity.lifecycleScope.launch(Dispatchers.Main) {
            @Suppress("DeferredResultUnused")
            async { activity.lowVolumeClickTone() }

            delay(pressGroupMaxGapMsOverride)

            if (!isActive) return@launch

            // Handle click-to-keep-awake mode first.
            if (pressGroupInstanceDataLocal.isInClickToKeepAwakeMode) {
                TtsSpeaker.speak("awake")
                clickToKeepAwakeProvider.disableOneClickToStayAwakeMode()
                return@launch
            }

            when (clickMode) {
                ClickMode.Default -> handleDefaultMode(pressGroupCount, handler)
                ClickMode.Secondary -> handleSecondaryMode(pressGroupCount)
                ClickMode.PendingPostpone -> handlePendingConfirmation(
                    pressGroupCount, handler,
                    onConfirm = {
                        handler.postponeNote(true)
                        TtsSpeaker.speak("postponed")
                    },
                    actionName = "postpone"
                )
                ClickMode.PendingDelete -> handlePendingConfirmation(
                    pressGroupCount, handler,
                    onConfirm = {
                        handler.deleteNote()
                        TtsSpeaker.speak("deleted, tap to continue")
                    },
                    actionName = "delete"
                )
            }
        }

        return true
    }

    /**
     * Default mode: FSRS-style ratings.
     * 1=Good, 2=Again, 3=Back, 4=Easy, 5=Hard, 6+=Secondary
     */
    private fun handleDefaultMode(pressGroupCount: Int, handler: PracticeModeStateHandler) {
        val message: String?
        when (pressGroupCount) {
            1 -> {
                // Good — normal recall (grade 4)
                message = null
                handler.proceedWithGrade(4)
            }
            2 -> {
                // Again — failed to recall (grade 1)
                message = "again"
                handler.proceedWithGrade(1)
            }
            3 -> {
                // Back/Undo
                message = "back"
                handler.undo()
            }
            4 -> {
                // Easy — effortless recall (grade 5)
                message = "easy"
                handler.proceedWithGrade(5)
            }
            5 -> {
                // Hard — difficult recall (grade 2)
                message = "hard"
                handler.proceedWithGrade(2)
            }
            else -> {
                // 6 or more clicks: enter secondary mode
                clickMode = ClickMode.Secondary
                message = "secondary mode"
            }
        }
        message?.let { TtsSpeaker.speak(it) }
    }

    /**
     * Secondary mode: dangerous actions requiring confirmation.
     * 1=Pending Postpone, 2=Pending Delete, others=no-op
     */
    private fun handleSecondaryMode(pressGroupCount: Int) {
        when (pressGroupCount) {
            1 -> {
                clickMode = ClickMode.PendingPostpone
                TtsSpeaker.speak("postpone pending triple tap to confirm")
            }
            2 -> {
                clickMode = ClickMode.PendingDelete
                TtsSpeaker.speak("delete pending triple tap to confirm")
            }
            else -> {
                // 3 or more clicks: back to default mode
                clickMode = ClickMode.Default
                TtsSpeaker.speak("back to default")
            }
        }
    }

    /**
     * Pending confirmation mode.
     * 1 click = cancel and return to default.
     * 3 rapid clicks = confirm and execute.
     * Other counts = no-op, stay in pending state.
     */
    private fun handlePendingConfirmation(
        pressGroupCount: Int,
        handler: PracticeModeStateHandler,
        onConfirm: () -> Unit,
        actionName: String
    ) {
        when (pressGroupCount) {
            1 -> {
                // Cancel — return to default mode
                clickMode = ClickMode.Default
                TtsSpeaker.speak("cancelled")
            }
            3 -> {
                // Triple click confirms the action
                onConfirm()
                clickMode = ClickMode.Default
            }
            else -> {
                // 2, 4, 5 clicks do nothing in pending mode.
                // Stay in pending state.
            }
        }
    }

    /** Gap timing for default mode. Larger counts get longer windows. */
    private fun getDefaultModeGap(pressGroupCount: Int, baseGapMs: Long): Long {
        return when {
            pressGroupCount >= 6 -> {
                TtsSpeaker.speak(pressGroupCount.toString())
                2000L
            }
            pressGroupCount >= 4 -> {
                TtsSpeaker.speak(pressGroupCount.toString())
                1500L
            }
            pressGroupCount == 3 -> {
                TtsSpeaker.speak(pressGroupCount.toString())
                1000L
            }
            else -> baseGapMs
        }
    }

    /** Gap timing for secondary mode. */
    private fun getSecondaryModeGap(pressGroupCount: Int): Long {
        return when {
            pressGroupCount <= 2 -> 1000L
            else -> 500L
        }
    }
}