package com.md

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

public fun LifecycleOwner?.isAtLeastResumed() : Boolean {
    if (this == null) return false

    return this.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
}