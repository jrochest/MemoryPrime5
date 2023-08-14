package com.md.modesetters

import android.app.Activity
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.md.ModeHandler
import com.md.SpacedRepeaterActivity

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
                HelloWorld()
            }
        })
    }


    @Composable
    fun HelloWorld() {
        val memories = remember { mutableStateListOf<Memory>() }

        Surface(color = MaterialTheme.colorScheme.background) {
            Column(
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.Start
            ) {

                AddMemoryButton(memories)
                LazyColumn {
                    itemsIndexed(memories) { index: Int, memory: Memory ->
                        ExistingMemoryButton(memories = memories, index = index, memory)
                    }
                }
            }
        }
    }

    @Composable
    private fun AddMemoryButton(memories: SnapshotStateList<Memory>) {
        Button(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            onClick = {
                memories.add(0, Memory((100*Math.random()).toInt().toString()))
            },
        ) {
            Text(text = "Record", fontSize = MAX_FONT_SIZE.sp, modifier = Modifier.padding(MAX_FONT_SIZE.dp))
        }
    }

    @Composable
    private fun ExistingMemoryButton(
        memories: SnapshotStateList<Memory>,
        index: Int,
        memory: Memory
    ) {
        val fontSize = (MAX_FONT_SIZE - (index * 5)).coerceAtLeast(10)
        Spacer(modifier = Modifier.width(10.dp))
        Button(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
             onClick = {
                memory.onTap(memories, index, activity)
            },
        ) {
            Text(text = "Note ${memory.name}", fontSize = fontSize.sp, modifier = Modifier.padding(fontSize.dp))
        }
    }


    data class Memory(val name: String) {
        private var recentPressCount: Int = 0

           fun getRecentPressCount() : Int {
                if (SystemClock.uptimeMillis() > (MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS + lastPressInstant)) {
                    recentPressCount = 0
                }
                return recentPressCount
            }

        var lastPressInstant: Long = 0

        fun onTap(
            memories: SnapshotStateList<Memory>,
            index: Int,
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

        private const val MAX_FONT_SIZE = 50
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