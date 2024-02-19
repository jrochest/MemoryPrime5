package com.md.utils

import android.os.Handler
import android.view.WindowManager
import com.md.SpacedRepeaterActivity

class KeepScreenOn private constructor() {
    private var sequenceNumber = 0
    fun keepScreenOn(activity: SpacedRepeaterActivity) {
        sequenceNumber++
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val requestId = sequenceNumber
        Handler().postDelayed({
            if (activity.isDestroyed || activity.isFinishing) {
                return@postDelayed
            }
            if (requestId == sequenceNumber) {
                // TIMEOUT_MILLIS without a new command so remove the screen on flag.
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }, TIMEOUT_MILLIS.toLong())
    }

    companion object {
        var instance: KeepScreenOn? = null
            get() {
                if (field == null) {
                    field = KeepScreenOn()
                }
                return field
            }
            private set
        private const val TIMEOUT_MILLIS = 15 * 60 * 1000
    }
}