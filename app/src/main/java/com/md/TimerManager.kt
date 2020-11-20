package com.md

import android.os.CountDownTimer
import com.md.modesetters.TtsSpeaker.speak

class TimerManager {
    private var timer : CountDownTimer? = null
    private var roundCounter = 0

    fun cancelTimer() {
        timer?.cancel()
    }

    fun addTimer(numberOfRounds: Int, intervalSeconds: Int) {
        val intervalMillis = intervalSeconds * 1000L
        cancelTimer()
        roundCounter = numberOfRounds * 2 + 1
        timer = object : CountDownTimer(intervalMillis * roundCounter, intervalMillis) {
            override fun onTick(millisUntilFinished: Long) {
                if (roundCounter == 0) return
                roundCounter--
                speak("$roundCounter, $roundCounter, $roundCounter", rate=3.5f, pitch = .8f)
            }

            override fun onFinish() {
                speak("Finished", rate=2.0f, pitch = .8f)
                timer = null
            }
        }.start()
    }
}