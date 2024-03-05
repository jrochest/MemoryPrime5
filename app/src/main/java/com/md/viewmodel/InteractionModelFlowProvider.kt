package com.md.viewmodel

import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
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
class InteractionModelFlowProvider @Inject constructor() {
    val mostRecentInteraction = MutableStateFlow(InteractionType.TouchScreen)
}