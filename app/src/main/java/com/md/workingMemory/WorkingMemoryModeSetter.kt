package com.md.workingMemory

import android.app.Activity
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.ComposeView
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class WorkingMemoryModeSetter @Inject constructor(
    val activity: SpacedRepeaterActivity,
    private val modeHandler: ModeHandler
) : ModeSetter(), ItemDeletedHandler {
    init {
        parentSetup(activity, modeHandler)
    }

    override fun switchModeImpl(context: Activity) {
        modeHandler.add(this)
        context.setContentView(ComposeView(context).apply {
            setContent {
                val notes = SnapshotStateList<ShortTermNote>()
                WorkingMemoryScreenComposable(notes) {
                        note -> note.onTap(notes)
                }
            }
        })
    }

}