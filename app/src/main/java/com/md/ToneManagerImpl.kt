package com.md

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import javax.inject.Inject

class ToneManagerImpl @Inject constructor() : ToneManager {
    private var keepAliveTone: ToneGenerator? = null

    override fun maybeStartTone(context: Context) {
        // Tone this preference is not being set. Delete or add an option for it.
       // if (!TonePreference.get(context)) return

        keepAliveTone = ToneGenerator(AudioManager.STREAM_MUSIC, 10)
    }

    override fun maybeStopTone() {
        keepAliveTone?.release()
        keepAliveTone = null
    }

    override fun backupTone() {
        // keep the headphones turned on by playing an almost silent sound n seconds.
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD,  /* half second */500)
    }

    override fun lowVolumeClickTone() {
        val tone = keepAliveTone ?: return
        tone.startTone(ToneGenerator.TONE_DTMF_0,  /* durationMs = */ 100)
    }

    override fun lowVolumePrimeSpeakerTone() {
        val tone = keepAliveTone ?: return
        tone.startTone(ToneGenerator.TONE_DTMF_1,  /* durationMs = */ 100)
    }

    override fun errorTone() {
        // keep the headphones turned on by playing an almost silent sound n seconds.
        ToneGenerator(AudioManager.STREAM_MUSIC, 80).startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 1000)
    }
}