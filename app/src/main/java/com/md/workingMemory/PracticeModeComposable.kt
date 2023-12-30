package com.md.workingMemory

import android.content.Context
import android.os.SystemClock
import android.util.Log
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
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity
import com.md.modesetters.PracticeModeStateModel
import com.md.workingMemory.WorkingMemoryScreen.LARGE_TAP_AREA_LABEL
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@ActivityScoped
class PracticeModeComposerManager @Inject constructor(
    @ActivityContext val context: Context,
    val stateModel: PracticeModeStateModel,
    val model: ModeViewModel,
    val currentNotePartManager: CurrentNotePartManager,
    private val recordButtonController: RecordButtonController,) {

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }
    init {
        activity.lifecycleScope.launch {
            model.modeModel.collect { mode ->
                if (mode == Mode.Practice) {
                    stateModel.onSwitchToMode()
                }
            }
        }
    }

    val viewState = ViewState()
    class ViewState(val recordUnlocked: MutableState<Boolean> = mutableStateOf(false))
    @Composable
    fun compose() {
        PracticeModeComposable(
            viewState = viewState,
            onAudioRecorderTripleTap = {
                viewState.recordUnlocked.value = true
            },
            currentNotePartManager = currentNotePartManager,
            onMiddleButtonTap = {
                activity.handleRhythmUiTaps(stateModel,
                SystemClock.uptimeMillis(),
                SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_SCREEN)
            }
        )
    }
}

@Composable
fun PracticeModeComposable(
    onAudioRecorderTripleTap: () -> Unit = { },
    onDeleteTap: () -> Unit = {},
    onMiddleButtonTap: () -> Unit = {},
    viewState: PracticeModeComposerManager.ViewState,
    currentNotePartManager: CurrentNotePartManager
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
            onClick = { onMiddleButtonTap() }
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
                viewState = viewState,
                modifier = bottomButtonModifier.fillMaxWidth(fraction = .5f),
                onTripleTapToUnlock = onAudioRecorderTripleTap,
                currentNotePartManager = currentNotePartManager
            )
            // Why hide the delete button while recording a note.
            val hasNote = currentNotePartManager.hasNote.collectAsState()
            if (!viewState.recordUnlocked.value) {
                Button(
                    modifier = bottomButtonModifier.fillMaxWidth(fraction = 1f),
                    onClick = { onDeleteTap() }
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
}


@Composable
fun TripleTapButton(
    onTripleTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    var tapCount = 0
    var previousTapTimeMillis = 0L
    val maxTimeBetweenTapsMillis = 1000
    Button(modifier = modifier, onClick = {
        val currentTime = SystemClock.uptimeMillis()
        if (currentTime - previousTapTimeMillis <= maxTimeBetweenTapsMillis) {
            if (tapCount >= 2) {
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






