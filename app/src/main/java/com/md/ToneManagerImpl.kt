package com.md

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator

class ToneManagerImpl : ToneManager {
    private var keepAliveTone: ToneGenerator? = null

    override fun maybeStartTone(context: Context) {
        // Tone this preference is not being set. Delete or add an option for it.
        if (!TonePreference.get(context)) return

        keepAliveTone = ToneGenerator(AudioManager.STREAM_MUSIC, 1)
    }

    override fun maybeStopTone() {
        keepAliveTone?.release()
        keepAliveTone = null
    }

    override fun backupTone() {
        // keep the headphones turned on by playing an almost silent sound n seconds.
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,  /* half second */500)
    }

    override fun errorTone() {
        // keep the headphones turned on by playing an almost silent sound n seconds.
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 1000)
    }


    override fun keepHeadphoneAlive() {
        val tone = keepAliveTone ?: return

        // keep the headphones turned on by playing an almost silent sound n seconds.
        tone.startTone(ToneGenerator.TONE_CDMA_DIAL_TONE_LITE,  /* Two minutes */1000 * 60 * 2)
    }
}