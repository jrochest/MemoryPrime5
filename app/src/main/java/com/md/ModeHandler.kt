package com.md

import android.app.Activity
import com.md.modesetters.ModeSetter
import com.md.modesetters.MoveManager.cancelJobs
import dagger.hilt.android.scopes.ActivityScoped
import java.util.Stack
import javax.inject.Inject

// TODOJ change to view model scoped.
@ActivityScoped
class ModeHandler @Inject constructor(val context: Activity) {
    private val modeStack = Stack<ModeSetter>()

    fun whoseOnTop(): ModeSetter? {
        // Must have two.
        return if (!modeStack.empty()) {
            modeStack.peek()
        } else {
            null
        }
    }

    fun goBack(): Boolean {
        // Must have two.
        if (modeStack.size > 1) {
            val pop = modeStack.pop()
            modeStack.peek().switchMode(context)
            return true
        }
        return false
    }

    fun add(modeSetter: ModeSetter) {
        cancelJobs()
        // Don't put your self on!
        if (modeStack.empty() || modeStack.peek() !== modeSetter) {
            modeStack.push(modeSetter)
        }
    }
}