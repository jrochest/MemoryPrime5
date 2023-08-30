package com.md.workingMemory

import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.platform.ComposeView
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import com.md.modesetters.ItemDeletedHandler
import com.md.modesetters.ModeSetter
import dagger.hilt.android.scopes.ActivityScoped
import java.text.SimpleDateFormat
import java.util.Date
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
                        note -> note.onTap(notes, activity)
                }
            }
        })
    }


    data class ShortTermNote(private val creationInstantMillis: Long = System.currentTimeMillis()) {
        val name: String = "Note " + dateAndTime()

        @SuppressLint("SimpleDateFormat")
        private fun dateAndTime(): String {
            val format = SimpleDateFormat("MM.dd HH:mm:ss")
            return format.format(Date(creationInstantMillis))
        }

        private var recentPressCount: Int = 0

        private var lastPressInstant: Long = 0

        fun onTap(
            notes: SnapshotStateList<ShortTermNote>,
            activity: SpacedRepeaterActivity?
        ) {
            if (SystemClock.uptimeMillis() > (WorkingMemoryScreen.MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS + lastPressInstant)) {
                recentPressCount = 0
            }
            lastPressInstant = SystemClock.uptimeMillis()
            recentPressCount++


            if (recentPressCount == 2) {
                activity!!.clickTone()
            }

            if (recentPressCount == 3) {
                notes.remove(this)
            }
        }
    }
}