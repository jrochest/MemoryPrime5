package com.md.workingMemory

import android.annotation.SuppressLint
import android.os.SystemClock
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.md.SpacedRepeaterActivity
import java.text.SimpleDateFormat
import java.util.Date

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
    ) {
        if (SystemClock.uptimeMillis() > (WorkingMemoryScreen.MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS + lastPressInstant)) {
            recentPressCount = 0
        }
        lastPressInstant = SystemClock.uptimeMillis()
        recentPressCount++

        if (recentPressCount == 3) {
            notes.remove(this)
        }
    }
}