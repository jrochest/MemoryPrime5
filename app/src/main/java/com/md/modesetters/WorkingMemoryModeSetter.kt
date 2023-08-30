package com.md.modesetters

import android.annotation.SuppressLint
import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity
import java.text.SimpleDateFormat
import java.util.Date

class WorkingMemoryModeSetter : ModeSetter(), ItemDeletedHandler {

    private var activity: SpacedRepeaterActivity? = null
    fun setup(activity: SpacedRepeaterActivity, modeHand: ModeHandler?) {
        parentSetup(activity, modeHand)
        this.activity = activity
    }

    override fun switchModeImpl(context: Activity) {
        modeHand!!.add(this)
        context.setContentView(ComposeView(context).apply {
            setContent {
                WorkMemoryUi()
            }
        })
    }

    @Composable
    fun WorkMemoryUi() {
        val shortTermNotes = remember { mutableStateListOf<ShortTermNote>() }
        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

                AddMemoryButton(shortTermNotes)
                LazyColumn {
                    itemsIndexed(shortTermNotes) { index: Int, memory: ShortTermNote ->
                        ExistingMemoryButton(memories = shortTermNotes, index = index, memory)
                    }
                }
            }
        }
    }


    @Composable
    private fun AddMemoryButton(notes: SnapshotStateList<ShortTermNote>) {
        WorkingMemoryButton(onClick = {
            notes.add(0, ShortTermNote())
        }, label = "Tap tap hold to record", MAX_FONT_SIZE)
    }

    @Composable
    private fun ExistingMemoryButton(
        memories: SnapshotStateList<ShortTermNote>,
        index: Int,
        note: ShortTermNote
    ) {
        val fontSize = (MAX_FONT_SIZE - (index * 2)).coerceAtLeast(10)
        Spacer(modifier = Modifier.width(4.dp))
        val onClick = {
            note.onTap(memories, activity)
        }
        WorkingMemoryButton(onClick, note.name, fontSize)
    }

    @Composable
    private fun WorkingMemoryButton(
        onClick: () -> Unit,
        label: String,
        fontSize: Int
    ) {
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(4.dp),
            onClick = onClick,
        ) {
            Text(text = label, style = TextStyle(fontSize = fontSize.sp, textAlign = TextAlign.Center))
        }
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
            memories: SnapshotStateList<ShortTermNote>,
            activity: SpacedRepeaterActivity?
        ) {
            if (SystemClock.uptimeMillis() > (MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS + lastPressInstant)) {
                recentPressCount = 0
            }
            lastPressInstant = SystemClock.uptimeMillis()
            recentPressCount++


            if (recentPressCount == 2) {
                activity!!.clickTone()
            }

            if (recentPressCount == 3) {
                memories.remove(this)
            }
        }
    }

    // TODOJ perhaps use mutableState to refresh.
    companion object {

        private const val MAX_FONT_SIZE = 36
        private const val MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS = 300

        private var singleton: WorkingMemoryModeSetter? = null

        fun getInstance(): WorkingMemoryModeSetter {
            val singletonLocal = singleton
            if (singletonLocal == null) {
                val instance = WorkingMemoryModeSetter()
                singleton = instance
                return instance
            }
            return singletonLocal
        }
    }

}