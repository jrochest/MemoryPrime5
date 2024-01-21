package com.md.modesetters

import android.os.SystemClock
import android.view.View
import java.util.concurrent.TimeUnit

/**
 * Note: currently this is hardcoded to 3 clicks.
 */
abstract class MultiClickListener : View.OnClickListener {
    private var mClickWindowStartMillis = 0L
    private var mClickCount = 0
    abstract fun onMultiClick(v: View?)
    override fun onClick(v: View) {
        val currentTimeMillis = SystemClock.uptimeMillis()
        if (currentTimeMillis - mClickWindowStartMillis < TimeUnit.SECONDS.toMillis(1)) {
            mClickCount++
            if (mClickCount == 3) {
                onMultiClick(v)
            }
        } else {
            // Start new window.
            mClickWindowStartMillis = currentTimeMillis
            mClickCount = 1
        }
    }
}