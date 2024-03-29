package com.md

import android.content.Context

interface ToneManager {
    fun backupTone()
    fun errorTone()
    fun lowVolumeClickTone()

    fun lowVolumePrimeSpeakerTone()

    fun maybeStartTone(context: Context)
    fun maybeStopTone()
}