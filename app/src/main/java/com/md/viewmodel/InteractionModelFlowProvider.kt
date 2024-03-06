package com.md.viewmodel

import android.os.SystemClock
import com.md.testing.TestingMode
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

enum class InteractionType {
    TouchScreen,
    /**
     * For instance a bluetooth shutter. That looks like a keyboard to
     * the Android system.
     */
    RemoteHumanInterfaceDevice,
}

@ActivityScoped
class InteractionModelFlowProvider @Inject constructor(private val testingMode: TestingMode,) {


    val mostRecentInteraction = MutableStateFlow(
        InteractionType.TouchScreen
    )

    private val _mostRecentPocketModeTapInstant = MutableStateFlow<Long>(0)

    val mostRecentPocketModeTapInstant: StateFlow<Long> = _mostRecentPocketModeTapInstant

    fun updateMostRecentPocketModeTap() {
        _mostRecentPocketModeTapInstant.value = SystemClock.uptimeMillis()
    }

    fun clearMostRecentPocketModeTap() {
        _mostRecentPocketModeTapInstant.value = 0
    }
}