package com.md

import android.os.Handler
import android.os.SystemClock
import com.md.modesetters.ModeSetter
import com.md.modesetters.TtsSpeaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

interface ToneManager {
    fun backupTone()
    fun errorTone()
}