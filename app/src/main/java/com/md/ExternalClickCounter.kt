package com.md

import android.os.SystemClock
import com.md.modesetters.MoveManager
import com.md.modesetters.ModeSetter
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.*

class ExternalClickCounter {
    var mPressGroupLastPressMs: Long = 0
    var mPressGroupLastPressEventMs: Long = 0
    var mPressGroupCount: Int = 0
    var mPressSequenceNumber: Int = 0

    var currentJob: Job? = null
    var pendingGreedyTap = false

    fun handleRhythmUiTaps(modeSetter: ModeSetter, eventTimeMs: Long, pressGroupMaxGapMs: Long, tapCount: Int): Boolean {
        currentJob?.cancel()
        val currentTimeMs = SystemClock.uptimeMillis()
        if (mPressGroupLastPressMs == 0L) {
            mPressGroupCount = tapCount
            pendingGreedyTap = false
            println("New Press group.")
        } else if (mPressGroupLastPressEventMs + pressGroupMaxGapMs < eventTimeMs) {
            // Large gap. Reset count.
            mPressGroupCount = tapCount
            pendingGreedyTap = false
            println("New Press group. Expiring old one.")
        } else {
            println("Time diff: " + (currentTimeMs - mPressGroupLastPressMs))
            println("Time diff event time: " + (eventTimeMs - mPressGroupLastPressEventMs))
            mPressGroupCount++
            println("mPressGroupCount++. $mPressGroupCount")
        }
        mPressGroupLastPressEventMs = eventTimeMs
        mPressGroupLastPressMs = currentTimeMs

        MoveManager.cancelJobs()

        currentJob = GlobalScope.launch(Dispatchers.Main) {
            if (mPressGroupCount == 1) {
                modeSetter.proceed()
                pendingGreedyTap = true
                return@launch
            } else if (pendingGreedyTap) {
                // If count taps by 1 undo the greedy proceed.
                modeSetter.undo()
                pendingGreedyTap = false
            }
            delay(pressGroupMaxGapMs)

            val message: String?
            when (mPressGroupCount) {
                1 -> {
                    message = null
                    modeSetter.proceed()
                }
                // This takes a different action based on whether it is a question or answer.
                2 -> {
                    message = modeSetter.secondaryAction()
                }
                3, 4 -> {
                    message = "undo"
                    modeSetter.undo()
                }
                5 -> {
                    modeSetter.postponeNote()
                    message = "${mPressGroupCount} postpone"
                }
                6  -> {
                    modeSetter.postponeNote(shouldQueue = false)
                    message = "${mPressGroupCount} postpone without requeue"
                }
                9, 10 -> {
                    AudioPlayer.instance.pause()
                    message = "repeat off"
                }
                else -> {
                    message = "unrecognized count"
                }
            }
            println("TEMP $message")
            message?.let { TtsSpeaker.speak(it) }
        }

        return true
    }
}