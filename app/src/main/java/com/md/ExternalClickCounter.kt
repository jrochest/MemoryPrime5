package com.md

import android.os.SystemClock
import com.md.modesetters.ModeSetter
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.*

class ExternalClickCounter {
    var mPressGroupLastPressMs: Long = 0
    var mPressGroupLastPressEventMs: Long = 0
    var mPressGroupCount: Int = 0
    var mPressSequenceNumber: Int = 0

    var currentJob: Job? = null

    fun handleRhythmUiTaps(modeSetter: ModeSetter, eventTimeMs: Long, pressGroupMaxGapMs: Long, tapCount: Int): Boolean {
        currentJob?.cancel()
        val currentTimeMs = SystemClock.uptimeMillis()
        if (mPressGroupLastPressMs == 0L) {
            mPressGroupCount = tapCount
            println("New Press group.")
        } else if (mPressGroupLastPressEventMs + pressGroupMaxGapMs < eventTimeMs) {
            // Large gap. Reset count.
            mPressGroupCount = tapCount
            println("New Press group. Expiring old one.")
        } else {
            println("Time diff: " + (currentTimeMs - mPressGroupLastPressMs))
            println("Time diff event time: " + (eventTimeMs - mPressGroupLastPressEventMs))
            mPressGroupCount++
            println("mPressGroupCount++. $mPressGroupCount")
        }
        mPressGroupLastPressEventMs = eventTimeMs
        mPressGroupLastPressMs = currentTimeMs

        currentJob = GlobalScope.launch(Dispatchers.Main) {
            delay(pressGroupMaxGapMs)

            val message: String?
            when (mPressGroupCount) {
                1 -> {
                    message = "go"
                    modeSetter.proceed()
                }
                // This takes a different action based on whether it is a question or answer.
                2 -> {
                    message = modeSetter.secondaryAction()
                }
                3 -> {
                    message = "undo"
                    modeSetter.undo()
                }
                4 -> {
                    AudioPlayer.instance.shouldRepeat = false
                    message = "repeat off"
                }
                5 -> {
                    modeSetter.resetActivity()
                    message = "reset"
                }
                else -> {
                    message = "unrecognized count"
                }
            }
            message?.let { TtsSpeaker.speak(it, 3.5f, pitch = 1.5f) }
        }

        return true
    }
}