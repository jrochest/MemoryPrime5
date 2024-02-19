package com.md.utils

import android.view.WindowManager
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity

import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Duration

import javax.inject.Inject

// Keywords: Keep awake keep alive. Screen wake. Keywords.
@ActivityScoped
class KeepScreenOn @Inject constructor(private val activity: SpacedRepeaterActivity) {

    private var screenOnThenDelayThenIfNotCancelledOff: Job? = null
    fun keepScreenOn() {
        screenOnThenDelayThenIfNotCancelledOff?.cancel()
        screenOnThenDelayThenIfNotCancelledOff = activity.lifecycleScope.launch {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            delay(TIMEOUT_MILLIS.toMillis())
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        private val TIMEOUT_MILLIS = Duration.ofMinutes(2)
    }
}