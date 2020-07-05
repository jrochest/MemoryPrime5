package com.md

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import com.md.utils.ToastSingleton

class TimerManager {
    private var timer : CountDownTimer? = null

    private val intervalMillis = 30000L
    private var roundCounter = 0

    val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 95)

    fun cancelTimer() {
        timer?.cancel()
    }

    fun addTimer(numberOfRounds: Int) {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING, 300)
        cancelTimer()
        roundCounter = numberOfRounds * 2 + 1
        timer = object : CountDownTimer(intervalMillis * roundCounter, intervalMillis) {
            override fun onTick(millisUntilFinished: Long) {
                if (roundCounter == 0) return

                toneGenerator.startTone(ToneGenerator.TONE_CDMA_NETWORK_CALLWAITING,300)
                ToastSingleton.getInstance().msg("Rounds: " + roundCounter--)
            }

            override fun onFinish() {
                toneGenerator.startTone(ToneGenerator.TONE_SUP_INTERCEPT,800)
                ToastSingleton.getInstance().msg("Timer finished")
                timer = null
            }
        }.start()
    }
}