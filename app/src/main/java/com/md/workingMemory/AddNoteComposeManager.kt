package com.md.workingMemory

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.md.AudioRecorder
import com.md.DbNoteEditor
import com.md.RecordingTooSmallException
import com.md.RevisionQueue
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import com.md.provider.Note
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class AddNoteComposeManager @Inject constructor(
    val activity: SpacedRepeaterActivity
) {
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
                AudioRecordAndPlayButtonForPart(
                    firstButtonModifier, secondButtonModifier,
                    notePart = notePartQuestion,
                    hasSavable = state.hasQuestion)
            }
            Row(Modifier.fillMaxHeight(.5f)) {
                AudioRecordAndPlayButtonForPart(
                    firstButtonModifier, secondButtonModifier,
                    notePart = notePartAnswer,
                    hasSavable = state.hasAnswer)
            }
            Row(Modifier.fillMaxHeight(1f)) {
                Button(modifier = firstButtonModifier,
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
                if ( state.hasAnswer.value && state.hasQuestion.value) {
                    Button(modifier = secondButtonModifier,
                        onClick = {
                            val currentDeckReviewQueue = RevisionQueue.currentDeckReviewQueue
                            if (currentDeckReviewQueue == null) {
                                TtsSpeaker.error("No revision queue. Make and or select a deck.")
                                return@Button
                            }
                            val questionFile = checkNotNull(notePartQuestion.savableRecorder).originalFile
                            val answerFile = checkNotNull(notePartAnswer.savableRecorder).originalFile
                            val noteEditor = checkNotNull(DbNoteEditor.instance)
                            var note = Note(questionFile, answerFile)
                            note = noteEditor.insert(note) as Note
                            currentDeckReviewQueue.add(note)

                            notePartQuestion.pendingRecorder = null
                            notePartQuestion.savableRecorder = null
                            notePartAnswer.pendingRecorder = null
                            notePartAnswer.savableRecorder = null
                        }) {
                        RecorderButtonText(text = "Save note")
                    }

                }
            }
        }
    }
}


@Composable
fun AudioRecordAndPlayButtonForPart(
    firstButtonModifier: Modifier,
    secondButtonModifier: Modifier,
    notePart: NotePart,
    showSaveButton: Boolean = false,
    onSaveTap: () -> Unit = {},
    hasSavable: MutableState<Boolean> = mutableStateOf(false)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    if (isPressed) {
        if (notePart.pendingRecorder == null) {
            notePart.pendingRecorder = AudioRecorder().apply { start() }
        }
        DisposableEffect(Unit) {
            onDispose {
                fun handleFailedPendingRecording() {
                    val recorder = checkNotNull(notePart.pendingRecorder)
                    notePart.pendingRecorder = null
                    recorder.deleteFile()
                }
                val pendingRecorder = notePart.pendingRecorder
                if (pendingRecorder != null) {
                    try {
                        pendingRecorder.stop()
                        if (pendingRecorder.isRecorded) {
                            val oldRecording = notePart.savableRecorder
                            if (oldRecording != null) {
                                oldRecording.stop()
                                oldRecording.deleteFile()
                            }
                            notePart.savableRecorder = pendingRecorder
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
            }
        }
    }

    Button(modifier = firstButtonModifier,
        interactionSource = interactionSource,
        onClick = {}) {
        RecorderButtonText(
            text = if (isPressed) {
                "Recording ${notePart.name}"
            } else {
                "Record ${notePart.name} "
            }
        )
    }
    if (hasSavable.value) {
        Button(modifier = secondButtonModifier,
            onClick = {
                notePart.savableRecorder?.playFile()
            }) {
            RecorderButtonText(text = "Play ${notePart.name}")
        }
        if (showSaveButton) {
            Button(modifier = secondButtonModifier,
                onClick = onSaveTap) {
                RecorderButtonText(text = "Save")
            }
        }
    }
}

@Composable
fun RecorderButtonText(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge)
}