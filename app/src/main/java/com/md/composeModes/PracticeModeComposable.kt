package com.md.composeModes

import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.md.FocusedQueueStateModel
import com.md.SpacedRepeaterActivity
import com.md.modesetters.PracticeModeStateHandler
import com.md.composeModes.WorkingMemoryScreen.LARGE_TAP_AREA_LABEL
import com.md.composeStyles.ButtonStyles
import com.md.modesetters.DeckLoadManager
import com.md.modesetters.TtsSpeaker
import com.md.utils.KeepScreenOn
import com.md.viewmodel.InteractionModelFlowProvider
import com.md.viewmodel.InteractionType
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
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

    data class Metrics(
        val notesPracticed: Int,
        val remainingInQueue: Int
    )

    val metricsFlow = MutableStateFlow(Metrics(0, 0))
}

@ActivityScoped
class PracticeModeComposerManager @Inject constructor(
    @ActivityContext val context: Context,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val stateModel: PracticeModeStateHandler,
    private val topModeProvider: TopModeFlowProvider,
    private val interactionProvider: InteractionModelFlowProvider,
    val currentNotePartManager: CurrentNotePartManager,
    val focusedQueueStateModel: FocusedQueueStateModel,
    private val deckLoadManager: DeckLoadManager,
    private val keepScreenOn: KeepScreenOn,
) {

    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    init {
        activity.lifecycleScope.launch {
            topModeProvider.modeModel.collect { mode ->
                if (mode == Mode.Practice) {
                    stateModel.onSwitchToMode()
                }
            }
        }
    }

    @Composable
    fun compose() {
        PracticeModeComposable(
            onEnableRecordMode = {
                activity.lifecycleScope.launch {
                    interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
                    keepScreenOn.keepScreenOn()
                    activity.lowVolumeClickTone()
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Recording
                }
            },
            onDisabledRecordMode = {
                activity.lifecycleScope.launch {
                    interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
                    keepScreenOn.keepScreenOn()
                    activity.lowVolumeClickTone()
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                }
            },
            onDeleteTap = {
                interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
                keepScreenOn.keepScreenOn()
                stateModel.deleteNote()
            },
            onMiddleButtonTapInPracticeMode = {
                interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
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
        onEnableRecordMode: () -> Unit = { },
        onDisabledRecordMode: () -> Unit = { },
        onDeleteTap: () -> Unit = {},
        onMiddleButtonTapInPracticeMode: () -> Unit = {},
        practiceMode: PracticeMode,
        currentNotePartManager: CurrentNotePartManager
    ) {

        val interactionType = interactionProvider.mostRecentInteraction.collectAsState().value
        val noteState = currentNotePartManager.noteStateFlow.collectAsState()
        val notePart = noteState.value?.notePart


        Column(
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {

            val largeButtonModifier = Modifier
                .fillMaxHeight(fraction = .85f)
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(4.dp)

            // LARGE BUTTON.
            @Composable
            fun showRepState() {
                val metrics = practiceModeViewModel.metricsFlow.collectAsState().value
                Text(
                    text = "Reps: ${metrics.notesPracticed}",
                    style = MaterialTheme.typography.displayMedium
                )
                Text(
                    text = "Remaining: ${metrics.remainingInQueue}",
                    style = MaterialTheme.typography.headlineSmall
                )
            }

            if (notePart == null) {
                MiddlePracticeButton(
                    largeButtonModifier, onMiddleButtonTapInPracticeMode,
                    colors = ButtonStyles.MediumImportanceButtonColor()
                ) {
                    showRepState()
                    Text(
                        text = "Tap for next deck",
                        style = MaterialTheme.typography.displayLarge
                    )
                }
                return
            }


            when (practiceMode) {
                PracticeMode.Practicing -> {
                    MiddlePracticeButton(
                        largeButtonModifier, onMiddleButtonTapInPracticeMode,
                        colors = ButtonStyles.MediumImportanceButtonColor()
                    ) {
                        showRepState()
                        val isAnswer =
                            currentNotePartManager.noteStateFlow.collectAsState().value?.notePart?.partIsAnswer
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
                    AudioRecordForPart(modifier = modifier, notePart = notePart)
                }

                PracticeMode.Deleting -> {
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
                    PracticeMode.Deleting -> {
                        OutlinedButton(modifier = bottomLeftButtonModifier,
                            colors = ButtonStyles.MediumImportanceButtonColor(),
                            onClick = {
                                practiceModeViewModel.practiceStateFlow.value =
                                    PracticeMode.Practicing
                            }) {
                            Text(text = "Exit delete mode")
                        }
                    }

                    PracticeMode.Recording -> {
                        UnlockRecordButton(
                            modifier = bottomLeftButtonModifier,
                            onModeChange = {
                                notePart.pendingRecorder?.stop()
                                notePart.pendingRecorder?.deleteFile()
                                notePart.pendingRecorder = null
                                onDisabledRecordMode()
                            },
                            modeDescription = "(Unlocked) - Double tap to disable",
                        )
                    }

                    PracticeMode.Practicing -> {
                        UnlockRecordButton(
                            modifier = bottomLeftButtonModifier,
                            onModeChange = onEnableRecordMode,
                            modeDescription = "(Locked) - Double tap to record",
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
                                practiceModeViewModel.practiceStateFlow.value =
                                    PracticeMode.Practicing
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
    internal fun PocketLowEnergyPracticeComposition() {
        val mostRecentPocketModeTapInstant =
            interactionProvider.mostRecentPocketModeTapInstant.collectAsState()
        val unlockUi: State<Boolean> = remember {
            derivedStateOf {
                ((mostRecentPocketModeTapInstant.value + 5_000) > SystemClock.uptimeMillis())
            }
        }
        if (unlockUi.value) {
            NTapButton(
                requiredTaps = 5,
                onNTap = {
                    interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
                }
            ) {
                Text(
                    modifier = Modifier.fillMaxHeight(fraction = .3f).fillMaxWidth(),
                    text = "5 quick taps to unlock",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            Surface(color = MaterialTheme.colorScheme.background,
                content = {
                    // No content. The screen is black to save power.
                }, modifier = Modifier
                    .fillMaxSize()
                    .clickable {
                        keepScreenOn.keepScreenOn(updatedDimScreenAfterBriefDelay = true)
                        interactionProvider.updateMostRecentPocketModeTap()
                    })
        }
    }

    @Composable
    private fun DeleteButton(
        bottomRightButtonModifier: Modifier,
        onDeleteTap: () -> Unit,
        mode: PracticeMode
    ) {
        NTapButton(
            modifier = bottomRightButtonModifier,
            onNTap = {
                interactionProvider.mostRecentInteraction.value = InteractionType.TouchScreen
                if (mode == PracticeMode.Deleting) {
                    TtsSpeaker.speak("Deleted", lowVolume = true)
                    onDeleteTap()
                    practiceModeViewModel.practiceStateFlow.value = PracticeMode.Practicing
                } else {
                    TtsSpeaker.speak("Delete mode", lowVolume = true)
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
                    text = if (mode == PracticeMode.Deleting) "Double tap for delete mode" else "Double tap to delete!",
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
fun NTapButton(
    requiredTaps: Int = 2,
    onNTap: () -> Unit,
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
                tapCount++
                if (tapCount >= requiredTaps) {
                    onNTap()
                    tapCount = 0
                }
            } else {
                // Else fresh set of taps.
                tapCount = 1
            }
            previousTapTimeMillis = currentTime

        }, content = content,
        colors = colors
    )
}






