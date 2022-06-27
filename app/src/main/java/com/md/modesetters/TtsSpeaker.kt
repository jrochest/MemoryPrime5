package com.md.modesetters

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.QUEUE_ADD


object TtsSpeaker : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var statusResult: Int? = null

    fun setup(context: Context) {
        tts = TextToSpeech(context, this)
    }

    private val errors = mutableSetOf<String>()

    @JvmStatic
    fun error(message: String) {
        println(message)
        if (errors.contains(message)) return
        errors.add(message)

        speak("Error: $message")
    }

    @JvmStatic
    @JvmOverloads
    fun speak(message: String, lowVolume: Boolean = false) {
        if (statusResult != TextToSpeech.SUCCESS) return

        val params = Bundle()
        if (lowVolume) {
            params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 0.1f)
        }

        tts?.speak(message, QUEUE_ADD, params, "")
    }

    override fun onInit(status: Int) {
        statusResult = status
    }

}