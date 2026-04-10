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
    
    // Accumulates slow clicks to determine which command to execute
    val pendingCommandCountFlow = kotlinx.coroutines.flow.MutableStateFlow(0)
    var pendingCommandCount: Int
        get() = pendingCommandCountFlow.value
        set(value) { pendingCommandCountFlow.value = value }

    // Using 500ms for mapping rapid clicks, based on user input
    private val rapidClickGapMs = 500L

    fun handleRhythmUiTaps(eventTimeMs: Long, ignoredMaxGapMs: Long): Boolean {
        val handler = practiceModeHandler
        currentJob?.cancel()
        currentJob = null
        var pressGroupInstanceDataLocal = pressGroupInstanceData
        val currentTimeMs = SystemClock.uptimeMillis()
        if (pressGroupInstanceDataLocal == null) {
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
        } else if (pressGroupLastPressEventMs + rapidClickGapMs < eventTimeMs) {
            // Gap larger than 500ms means previous tap group ended.
            // Reset tap count for the new rapid click group.
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
        } else {
            pressGroupInstanceDataLocal.incrementCount()
        }
        pressGroupLastPressEventMs = eventTimeMs
        pressGroupLastPressMs = currentTimeMs

        val pressGroupCount = pressGroupInstanceDataLocal.pressGroupCount

        currentJob = activity.lifecycleScope.launch(Dispatchers.Main) {
            @Suppress("DeferredResultUnused")
            async { activity.lowVolumeClickTone() }

            delay(rapidClickGapMs)

            if (!isActive) return@launch

            // Handle click-to-keep-awake mode first.
            if (pressGroupInstanceDataLocal.isInClickToKeepAwakeMode) {
                TtsSpeaker.speak("awake")
                clickToKeepAwakeProvider.disableOneClickToStayAwakeMode()
                return@launch
            }

            processTapGroup(pressGroupCount, handler)
        }

        return true
    }

    private fun processTapGroup(tapCount: Int, handler: PracticeModeStateHandler) {
        when {
            tapCount == 1 -> {
                // Single tap -> increment current slow click count, and voice it
                pendingCommandCount++
                announcePendingCommand()
            }
            tapCount == 2 -> {
                // Double tap -> enact command
                if (pendingCommandCount == 0) {
                    pendingCommandCount = 1
                }
                executePendingCommand(handler)
            }
            tapCount >= 3 -> {
                // Triple tap -> reset
                pendingCommandCount = 0
                TtsSpeaker.speak("cancelled")
            }
        }
    }

    private fun announcePendingCommand() {
        val message = when (pendingCommandCount) {
            1 -> "good"
            2 -> "again"
            3 -> "back"
            4 -> "postpone"
            5 -> "delete"
            6 -> "easy"
            7 -> "hard"
            else -> "unknown command"
        }
        TtsSpeaker.speak(message)
    }

    private fun executePendingCommand(handler: PracticeModeStateHandler) {
        val count = pendingCommandCount
        pendingCommandCount = 0 // Reset after execution

        val message: String? = when (count) {
            1 -> {
                handler.proceedWithGrade(4)
                null
            }
            2 -> {
                handler.proceedWithGrade(1)
                "again"
            }
            3 -> {
                handler.undo()
                "back"
            }
            4 -> {
                handler.postponeNote(true)
                "postponed"
            }
            5 -> {
                handler.deleteNote()
                "deleted, double tap to continue"
            }
            6 -> {
                handler.proceedWithGrade(5)
                "easy"
            }
            7 -> {
                handler.proceedWithGrade(2)
                "hard"
            }
            else -> {
                null
            }
        }
        message?.let { TtsSpeaker.speak(it) }
    }
}