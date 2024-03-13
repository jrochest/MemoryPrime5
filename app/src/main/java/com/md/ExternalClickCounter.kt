package com.md

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.md.modesetters.PracticeModeStateHandler
import com.md.modesetters.TtsSpeaker
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.*
import javax.inject.Inject


@ActivityScoped
class ExternalClickCounter

    @Inject
    constructor(
       private val practiceModeHandler: PracticeModeStateHandler
    ) {

    @ActivityContext @Inject lateinit var context: Context
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    var mPressGroupLastPressMs: Long = 0
    var mPressGroupLastPressEventMs: Long = 0
    var mPressGroupCount: Int = 0
    var deleteMode = false

    var currentJob: Job? = null

    private var previousPressGroupGapMillis: Long = 0
    fun handleRhythmUiTaps(eventTimeMs: Long, pressGroupMaxGapMs: Long): Boolean {
        val handler = practiceModeHandler
        currentJob?.cancel()
        currentJob = null
        val currentTimeMs = SystemClock.uptimeMillis()
        if (mPressGroupLastPressMs == 0L) {
            mPressGroupCount = 1
            println("New Press group.")
        } else if (mPressGroupLastPressEventMs + previousPressGroupGapMillis < eventTimeMs) {
            // Large gap. Reset count.
            mPressGroupCount = 1
            println("New Press group. Expiring old one.")
        } else {
            println("Time diff: " + (currentTimeMs - mPressGroupLastPressMs))
            println("Time diff event time: " + (eventTimeMs - mPressGroupLastPressEventMs))
            mPressGroupCount++
            println("mPressGroupCount++. $mPressGroupCount")
        }
        mPressGroupLastPressEventMs = eventTimeMs
        mPressGroupLastPressMs = currentTimeMs

        // Allow larger gaps for larger counts.
       val pressGroupMaxGapMsOverride: Long = if (mPressGroupCount > 3) {
            TtsSpeaker.speak(mPressGroupCount.toString())
            1500L
        } else {
            pressGroupMaxGapMs
        }

        previousPressGroupGapMillis = pressGroupMaxGapMsOverride

        currentJob = activity.lifecycleScope.launch(Dispatchers.Main) {
            async {
                @Suppress("DeferredResultUnused")
                activity.lowVolumeClickTone()
            }

            delay(pressGroupMaxGapMsOverride)

            if (!isActive) {
                return@launch
            }

            val message: String?
            when (mPressGroupCount) {
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
                5 -> {
                    handler.postponeNote(true)
                    message = "$mPressGroupCount postpone"
                    deleteMode = false
                }
                6  -> {
                    handler.postponeNote(shouldQueue = false)
                    message = "$mPressGroupCount postpone without requeue"
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
                9, 10 -> {
                    AudioPlayer.instance.pause()
                    message = "repeat off"
                    deleteMode = false
                }
                else -> {
                    deleteMode = false
                    message = "unrecognized count"
                }
            }
            println("TEMP $message")
            message?.let { TtsSpeaker.speak(it) }
        }

        return true
    }
}