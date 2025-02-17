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


        fun incrementCount()  {
           pressGroupCount++
        }
    }

    private var pressGroupLastPressMs: Long = 0
    private var pressGroupLastPressEventMs: Long = 0
    private var pressGroupInstanceData: PressGroupInstanceData? = null

    private var deleteMode = false

    private var currentJob: Job? = null

    private var previousPressGroupGapMillis: Long = 0
    fun handleRhythmUiTaps(eventTimeMs: Long, pressGroupMaxGapMs: Long): Boolean {
        val handler = practiceModeHandler
        currentJob?.cancel()
        currentJob = null
        var pressGroupInstanceDataLocal = pressGroupInstanceData
        val currentTimeMs = SystemClock.uptimeMillis()
        if (pressGroupInstanceDataLocal == null) {
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
            println("New Press group.")
        } else if (pressGroupLastPressEventMs + previousPressGroupGapMillis < eventTimeMs) {
            // Large gap. Reset count.
            pressGroupInstanceDataLocal = PressGroupInstanceData()
            this.pressGroupInstanceData = pressGroupInstanceDataLocal
            println("New Press group. Expiring old one.")
        } else {
            pressGroupInstanceDataLocal.incrementCount()
        }
        pressGroupLastPressEventMs = eventTimeMs
        pressGroupLastPressMs = currentTimeMs

        val pressGroupCount = pressGroupInstanceDataLocal.pressGroupCount

        val maxClickValueBeforeCancel = 8

        // Allow larger gaps for larger counts.
       val pressGroupMaxGapMsOverride: Long = when {
           pressGroupCount == maxClickValueBeforeCancel -> {
               TtsSpeaker.speak("cancelling")
               2000L
           }
           pressGroupCount > maxClickValueBeforeCancel -> {
               // Just let the "cancelling message above continue to play"
               1000L
           }
           pressGroupCount > 4 -> {
               TtsSpeaker.speak(pressGroupCount.toString())
               2000L
           }
           pressGroupCount == 4 -> {
               // Let 4 be a staging area for the other presses.
               TtsSpeaker.speak("4 staging")
               5000L
           }
           pressGroupCount == 3 -> {
               TtsSpeaker.speak(pressGroupCount.toString())
               1000L
           }
           else -> {
               pressGroupMaxGapMs
           }
       }
        previousPressGroupGapMillis = pressGroupMaxGapMsOverride
        currentJob = activity.lifecycleScope.launch(Dispatchers.Main) {
            val unused = async {
                @Suppress("DeferredResultUnused")
                activity.lowVolumeClickTone()
            }

            delay(pressGroupMaxGapMsOverride)

            if (!isActive) {
                return@launch
            }

            // This mode is sticky. If the click group starts prior to
            // going into the activity level isInClickToKeepAwakeMode
            // that won't affect the clip group. And all items in the
            // click group are affected by a start group isInClickToKeepAwakeMode = true.
            if (pressGroupInstanceDataLocal.isInClickToKeepAwakeMode) {
                TtsSpeaker.speak("awake")
                clickToKeepAwakeProvider.disableOneClickToStayAwakeMode()
                return@launch
            }

            val message: String?
            when (pressGroupCount) {
                1 -> {
                    message = null
                    handler.proceed()
                    deleteMode = false
                }
                // This takes a different action based on whether it is a question or answer.
                2 -> {
                   handler.secondaryAction()
                    // Let the secondary action handle the appropriate speech
                    message = null
                    deleteMode = false
                }
                3 -> {
                    message = "undo"
                    handler.undo()
                    deleteMode = false
                }
                4 -> {
                    message = "cancel"
                    deleteMode = false
                }
                5 -> {
                    handler.postponeNote(true)
                    message = "$pressGroupCount postpone"
                    deleteMode = false
                }
                6  -> {
                    handler.postponeNote(shouldQueue = false)
                    message = "$pressGroupCount postpone without requeue"
                    deleteMode = false
                }
                7  -> {
                    if (deleteMode) {
                        handler.deleteNote()
                        message = "Delete"
                        deleteMode = false
                    } else {
                        deleteMode = true
                        message = "Delete mode"
                    }
                }
                else -> {
                    deleteMode = false
                    message = "cancelled"
                }
            }
            message?.let { TtsSpeaker.speak(it) }
        }

        return true
    }
}