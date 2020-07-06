package com.md.modesetters

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD


object TtsSpeaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var statusResult: Int? = null

    public fun setup(context: Context) {
        tts = TextToSpeech(context, this)
    }

    @JvmStatic
    fun speak(message: String) {
        if (statusResult != TextToSpeech.SUCCESS) return
        tts?.speak(message, QUEUE_ADD, Bundle(), "")
    }

    override fun onInit(status: Int) {
        statusResult = status
    }

}