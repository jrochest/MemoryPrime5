package com.md.workingMemory

import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.md.workingMemory.WorkingMemoryScreen.LARGE_TAP_AREA_LABEL

object WorkingMemoryScreen {
    const val MAX_FONT_SIZE = 36
    const val MAX_TAP_GAP_DURATION_TO_DELETE_MILLIS = 300
    val LARGE_TAP_AREA_LABEL = """
Tap counts
1: Remembered
(User remembered note / proceed)
2: Forgot
(User forgot note / proceed)
3: Back
(Back, to previous question or previous answer. )
5: Short Postpone
Postpone note to later in the queue.
6: Long Postpone
(Postpone note to next time app is opened)
7: Archive.
Remove note from review queue. must be done twice.
8: Delete.
Remove note from storage. Must be done twice.

""".trimMargin()
}


@Composable
fun WorkingMemoryScreenComposable(
    onAudioRecorderTripleTap: () -> Unit = { },
) {
    Column(
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.Start
    ) {
        Button(
            modifier = Modifier
                .fillMaxHeight(fraction = .85f)
                .heightIn(min = 48.dp)
                .padding(4.dp),
            onClick = { }
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = LARGE_TAP_AREA_LABEL,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        val bottomButtonHeight = 180.dp
        val bottomButtonModifier = Modifier
            .heightIn(min = bottomButtonHeight)
            .padding(4.dp)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RecordAgainButton(
                modifier = bottomButtonModifier.fillMaxWidth(fraction = .5f),
                onAudioRecorderTripleTap
            )
            Button(
                modifier = bottomButtonModifier.fillMaxWidth(fraction = 1f),
                onClick = { }
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Delete",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Triple tap quickly",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

        }
    }
}


@Composable
fun TripleTapButton(
    onTripleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    var tapCount = 0
    var previousTapTimeMillis = 0L
    val maxTimeBetweenTapsMillis = 500
    Button(modifier = modifier, onClick = {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - previousTapTimeMillis <= maxTimeBetweenTapsMillis) {
            if (tapCount > 2) {
                onTripleTap()
                tapCount = 0
            } else {
                tapCount++
            }
        } else {
            tapCount = 1
        }
        previousTapTimeMillis = currentTime

    }, content = content)
}






