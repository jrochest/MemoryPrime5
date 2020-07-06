package com.md

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.CountDownTimer
import com.md.modesetters.TtsSpeaker.speak
import com.md.utils.ToastSingleton

class TimerManager {
    private var timer : CountDownTimer? = null
    private var roundCounter = 0

    fun cancelTimer() {
        timer?.cancel()
    }

    fun addTimer(numberOfRounds: Int, intervalSeconds: Int) {
        val intervalMillis = intervalSeconds * 1000L
        speak("starting")
        cancelTimer()
        roundCounter = numberOfRounds * 2 + 1
        timer = object : CountDownTimer(intervalMillis * roundCounter, intervalMillis) {
            override fun onTick(millisUntilFinished: Long) {
                if (roundCounter == 0) return
                speak("Rounds $roundCounter")
            }

            override fun onFinish() {
                speak("Timer finished")
                timer = null
            }
        }.start()
    }
}