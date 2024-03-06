package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.md.AudioRecorder
import com.md.DbNoteEditor
import com.md.RecordingTooSmallException
import com.md.RevisionQueue
import com.md.SpacedRepeaterActivity
import com.md.composeStyles.ButtonStyles.ImportantButtonColor
import com.md.composeStyles.ButtonStyles.MediumImportanceButtonColor
import com.md.modesetters.TtsSpeaker
import com.md.provider.Note
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject


@ActivityScoped
class AddNoteComposeManager @Inject constructor(
    @ActivityContext val context: Context,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    @Stable
    class State(
        val hasQuestion: MutableState<Boolean> = mutableStateOf(false),
        val hasAnswer: MutableState<Boolean> = mutableStateOf(false)
    )

    val state = State()

    private val notePartQuestion = NotePart(
        updateHasPart = { value: Boolean -> state.hasQuestion.value = value },
        partIsAnswer = false
    )
    private val notePartAnswer = NotePart(
        partIsAnswer = true,
        updateHasPart = { value: Boolean -> state.hasAnswer.value = value }
    )

    @Composable
    fun compose() {
        ShowUiForState(state)
    }

    @Composable
    fun ShowUiForState(state: State) {
        val buttonModifier = Modifier.fillMaxHeight()
        val firstButtonModifier = buttonModifier.fillMaxWidth(.5f)
        val secondButtonModifier = buttonModifier.fillMaxWidth(1f)

        Column {
            Row(Modifier.fillMaxHeight(.33f)) {
                AudioRecordForPart(
                    firstButtonModifier, notePart = notePartQuestion
                )
                PlayButtonForRecorderIfPending(
                    secondButtonModifier, notePart = notePartQuestion,
                    hasSavable = state.hasQuestion
                )
            }
            Row(Modifier.fillMaxHeight(.5f)) {
                AudioRecordForPart(
                    firstButtonModifier, notePart = notePartAnswer
                )
                PlayButtonForRecorderIfPending(
                    secondButtonModifier, notePart = notePartAnswer,
                    hasSavable = state.hasAnswer
                )
            }
            Row(Modifier.fillMaxHeight(1f)) {
                OutlinedButton(modifier = firstButtonModifier,
                    onClick = {
                        notePartQuestion.pendingRecorder?.deleteFile()
                        notePartQuestion.pendingRecorder = null
                        notePartQuestion.savableRecorder?.deleteFile()
                        notePartQuestion.savableRecorder = null
                        notePartAnswer.pendingRecorder?.deleteFile()
                        notePartAnswer.pendingRecorder = null
                        notePartAnswer.savableRecorder?.deleteFile()
                        notePartAnswer.savableRecorder = null
                    }) {
                    RecorderButtonText(text = "Reset")
                }
                if (state.hasAnswer.value && state.hasQuestion.value) {
                    val text = "Save note"
                    OutlinedButton(modifier = secondButtonModifier.semantics {
                                                                             contentDescription = text
                    },
                        onClick = {
                            val currentDeckReviewQueue = RevisionQueue.currentDeckReviewQueueDeleteThisTODO
                            if (currentDeckReviewQueue == null) {
                                TtsSpeaker.error("No revision queue. Make and or select a deck.")
                                return@OutlinedButton
                            }
                            val questionFile =
                                checkNotNull(notePartQuestion.savableRecorder).originalFile
                            val answerFile =
                                checkNotNull(notePartAnswer.savableRecorder).originalFile
                            val noteEditor = checkNotNull(DbNoteEditor.instance)
                            var note = Note(questionFile, answerFile)
                            note = noteEditor.insert(note) as Note
                            currentDeckReviewQueue.add(note)

                            notePartQuestion.pendingRecorder = null
                            notePartQuestion.savableRecorder = null
                            notePartAnswer.pendingRecorder = null
                            notePartAnswer.savableRecorder = null
                        }) {
                        RecorderButtonText(text = text)
                    }

                }
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AudioRecordForPart(
    modifier: Modifier,
    notePart: NotePart
) {
    val isRecording = remember { mutableStateOf(false) }

    if (!isRecording.value) {
        val text = "Tap to record ${notePart.name}"
        OutlinedButton(modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = text
        },
            colors = MediumImportanceButtonColor(),
            onClick = {
                if (notePart.pendingRecorder == null) {
                    notePart.pendingRecorder = AudioRecorder().apply { start() }
                }
                isRecording.value = true
            }) {
            RecorderButtonText(
                text =
                "Tap to record ${notePart.name}"
            )
        }
    } else {
        val text = "Tap to stop recording ${notePart.name}"
        OutlinedButton(modifier = modifier
            .semantics(mergeDescendants = true) {
                                                contentDescription = text
            },
            colors = ImportantButtonColor(),
            onClick = {
                fun handleFailedPendingRecording() {
                    val recorder = checkNotNull(notePart.pendingRecorder)
                    notePart.pendingRecorder = null
                    recorder.deleteFile()
                }

                val recorder = notePart.pendingRecorder
                if (recorder != null) {
                    try {
                        recorder.stop()
                        if (recorder.isRecorded) {
                            val oldRecording = notePart.savableRecorder
                            if (oldRecording != null) {
                                oldRecording.stop()
                                oldRecording.deleteFile()
                            }
                            notePart.savableRecorder = recorder
                        } else {
                            TtsSpeaker.speak("Recording failed")
                            handleFailedPendingRecording()
                        }
                        notePart.pendingRecorder = null
                    } catch (e: RecordingTooSmallException) {
                        TtsSpeaker.speak("Recording too short")
                        handleFailedPendingRecording()
                    }
                }
                isRecording.value = false
            }) {
            RecorderButtonText(
                text = text
            )
        }
    }


}

@Composable
fun PlayButtonForRecorderIfPending(
    modifier: Modifier,
    notePart: NotePart,
    hasSavable: MutableState<Boolean> = mutableStateOf(false)
) {
    if (hasSavable.value) {
        OutlinedButton(modifier = modifier,
            onClick = {
                notePart.savableRecorder?.playFile()
            }) {
            RecorderButtonText(text = "Play ${notePart.name}")
        }
    }
}


@Composable
fun SaveButtonForPendingNotePartRecording(
    modifier: Modifier,
    onSaveTap: () -> Unit = {},
    hasSavable: MutableState<Boolean> = mutableStateOf(false),
) {
    if (hasSavable.value) {
        OutlinedButton(modifier = modifier,
            colors = ImportantButtonColor(),
            onClick = { onSaveTap() }) {
            RecorderButtonText(text = "Save")
        }
    }
}


@Composable
fun RecorderButtonText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}