package com.md.composeModes

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.md.SpacedRepeaterActivity
import com.md.modesetters.PracticeModeStateHandler
import com.md.composeModes.WorkingMemoryScreen.LARGE_TAP_AREA_LABEL
import com.md.composeStyles.ButtonStyles
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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

enum class PracticeMode {
    Recording,
    Deleting,
    Practicing
}

@ActivityScoped
class PracticeModeViewModel @Inject constructor() {
    val practiceStateFlow = MutableStateFlow(PracticeMode.Practicing)
    val hasPlayedCurrentNotePartOrIgnoredAProceed = MutableStateFlow(false)

    data class Metrics(val notesPracticed: Int,
        val remainingInQueue: Int)

    val metricsFlow = MutableStateFlow(Metrics(0,0))
}

@ActivityScoped
class PracticeModeComposerManager @Inject constructor(
    @ActivityContext val context: Context,
    val practiceModeViewModel: PracticeModeViewModel,
    val stateModel: PracticeModeStateHandler,
    val model: ModeViewModel,
    val currentNotePartManager: CurrentNotePartManager,
) {

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

    @Composable
    fun compose() {
        PracticeModeComposable(
            onAudioRecorderTripleTap = {
                activity.lifecycleScope.launch {
                    activity.lowVolumeClickTone()
                    // It's common to receive 4 taps instead of 3 so delay switching.
                    delay(500)
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Recording
                }
            },
            onDeleteTap = {
                stateModel.deleteNote()
            },
            onMiddleButtonTapInPracticeMode = {
                activity.handleRhythmUiTaps(
                    SystemClock.uptimeMillis(),
                    SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_SCREEN
                )
            },
            practiceMode = practiceModeViewModel.practiceStateFlow.collectAsState().value,
            currentNotePartManager = currentNotePartManager
        )
    }



    @Composable
    fun PracticeModeComposable(
        onAudioRecorderTripleTap: () -> Unit = { },
        onDeleteTap: () -> Unit = {},
        onMiddleButtonTapInPracticeMode: () -> Unit = {},
        practiceMode: PracticeMode,
        currentNotePartManager: CurrentNotePartManager
    ) {
        val hasNote = currentNotePartManager.hasNote.collectAsState()

        if (!hasNote.value) {
            Text(text = "Nothing more to study")
            return
        }
        val notePart = checkNotNull(currentNotePartManager.notePart)

        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            val largeButtonModifier = Modifier
                .fillMaxHeight(fraction = .85f)
                .heightIn(min = 48.dp)
                .padding(4.dp)
            // LARGE BUTTON.
            when (practiceMode) {
                PracticeMode.Practicing -> {
                    MiddlePracticeButton(
                        largeButtonModifier, onMiddleButtonTapInPracticeMode,
                        colors = ButtonStyles.MediumImportanceButtonColor()
                    ) {
                        val metrics = practiceModeViewModel.metricsFlow.collectAsState().value
                        val isAnswer = currentNotePartManager.noteStateFlow.collectAsState().value?.notePart?.partIsAnswer
                        Text(
                            text = "Reps: ${metrics.notesPracticed}",
                            style = MaterialTheme.typography.displayMedium
                        )
                        Text(
                            text = "Remaining: ${metrics.remainingInQueue}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = if (isAnswer == true) "Answer" else "Question",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = LARGE_TAP_AREA_LABEL,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }

                PracticeMode.Recording -> {
                    var modifier = largeButtonModifier
                    val text =
                        if (currentNotePartManager.hasSavable.value) {
                            "Play pending recording"
                        } else {
                            "Waiting..."
                        }
                    MiddlePracticeButton(modifier,
                        onMiddleButtonTap = {
                            notePart.savableRecorder?.playFile()
                        }) {
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                PracticeMode.Deleting ->  {
                    MiddlePracticeButton(
                        largeButtonModifier,
                        {
                          practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                        },
                        colors = ButtonStyles.MediumImportanceButtonColor()
                    ) {
                        Text(
                            text = "Exit delete mode",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // END LARGE BUTTON.

            // START Medium button row.
            val bottomButtonHeight = 180.dp
            val bottomButtonModifier = Modifier
                .heightIn(min = bottomButtonHeight)
                .padding(4.dp)

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                // BOTTOM LEFT BUTTON
                val bottomLeftButtonModifier = bottomButtonModifier.fillMaxWidth(fraction = .5f)
                when (practiceMode) {
                    PracticeMode.Recording -> {
                        AudioRecordButton(
                            bottomLeftButtonModifier, notePart,
                            hasSavable = currentNotePartManager.hasSavable
                        )
                    }
                    PracticeMode.Deleting -> {
                        OutlinedButton(modifier = bottomLeftButtonModifier,
                            colors = ButtonStyles.MediumImportanceButtonColor(),
                            onClick = {
                                practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                            }) {
                            Text(text = "Exit delete mode")
                        }
                    }
                    PracticeMode.Practicing -> {
                        UnlockRecordButton(
                            modifier = bottomLeftButtonModifier,
                            unlock = onAudioRecorderTripleTap
                        )
                    }
                }
                // BOTTOM RIGHT BUTTON
                val bottomRightButtonModifier = bottomButtonModifier.fillMaxWidth(fraction = 1f)
                when (practiceMode) {
                    PracticeMode.Recording -> {
                        SaveButtonForPendingNotePartRecording(
                            modifier = bottomRightButtonModifier,
                            onSaveTap = {
                                currentNotePartManager.saveNewAudio()
                                practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                            },
                            hasSavable = currentNotePartManager.hasSavable
                        )
                    }
                    PracticeMode.Deleting,
                    PracticeMode.Practicing -> {
                        DeleteButton(bottomRightButtonModifier, onDeleteTap, practiceMode)
                    }
                }
            }
        }
    }

    @Composable
    private fun DeleteButton(
        bottomRightButtonModifier: Modifier,
        onDeleteTap: () -> Unit,
        mode: PracticeMode
    ) {
        TripleTapButton(
            modifier = bottomRightButtonModifier,
            onTripleTap = {
                if (mode == PracticeMode.Deleting) {
                    onDeleteTap()
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                } else {
                    activity.lowVolumeClickTone()
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Deleting
                }
            },
            colors = if (mode == PracticeMode.Deleting) ButtonStyles.ImportantButtonColor() else ButtonStyles.MediumImportanceButtonColor()
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


@Composable
private fun MiddlePracticeButton(
    modifier: Modifier,
    onMiddleButtonTap: () -> Unit,
    colors: ButtonColors = ButtonStyles.MediumImportanceButtonColor(),
    content: @Composable () -> Unit,
) {
    OutlinedButton(
        modifier = modifier,
        onClick = { onMiddleButtonTap() },
        colors = colors,
    ) {
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content()
        }
    }
}


@Composable
fun TripleTapButton(
    onTripleTap: () -> Unit,
    modifier: Modifier = Modifier,
    colors: ButtonColors = ButtonStyles.MediumImportanceButtonColor(),
    content: @Composable RowScope.() -> Unit
) {
    var tapCount = 0
    var previousTapTimeMillis = 0L
    val maxTimeBetweenTapsMillis = 1000
    OutlinedButton(
        modifier = modifier, onClick = {
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

        }, content = content,
        colors = colors
    )
}






