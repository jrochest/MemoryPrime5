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

    class NotePart(
        var recorder: AudioRecorder? = null,
        var pendingRecorder: AudioRecorder? = null,
        val updateHasPart: (Boolean) -> Unit,
        val hasPart: () -> Boolean,
        val name: String
    )

    private val notePartQuestion = NotePart(updateHasPart = { value -> state.hasQuestion.value = value },
        hasPart = { state.hasQuestion.value },
        name = "question"
    )
    val notePartAnswer = NotePart(updateHasPart = { value -> state.hasAnswer.value = value },
        hasPart = { state.hasAnswer.value },
        name = "answer"
        )


    @Composable
    fun ComposeMode() {
        ShowUiForState(state)
    }

    @Composable
    fun ButtonText(text: String) {
        Text(text = text, style = MaterialTheme.typography.labelLarge)
    }

    @Composable
    fun ShowUiForState(state: State) {
        val buttonModifier = Modifier.fillMaxHeight()
        val firstButtonModifier = buttonModifier.fillMaxWidth(.5f)
        val secondButtonModifier = buttonModifier.fillMaxWidth(1f)

        Column {
            Row(Modifier.fillMaxHeight(.33f)) {
                AudioRecordAndPlayButtonForPart(firstButtonModifier, secondButtonModifier,
                     notePart = notePartQuestion)
            }
            Row(Modifier.fillMaxHeight(.5f)) {
                AudioRecordAndPlayButtonForPart(firstButtonModifier, secondButtonModifier,
                    notePart = notePartAnswer)
            }
            Row(Modifier.fillMaxHeight(1f)) {
                Button(modifier = firstButtonModifier,
                    onClick = {
                        notePartQuestion.updateHasPart(false)
                        notePartQuestion.pendingRecorder?.deleteFile()
                        notePartQuestion.pendingRecorder = null
                        notePartQuestion.recorder?.deleteFile()
                        notePartQuestion.recorder = null
                        notePartAnswer.pendingRecorder?.deleteFile()
                        notePartAnswer.pendingRecorder = null
                        notePartAnswer.recorder?.deleteFile()
                        notePartAnswer.recorder = null
                        notePartAnswer.updateHasPart(false)
                    }) {
                    ButtonText(text = "Reset")
                }
                if (  state.hasAnswer.value && state.hasQuestion.value) {
                    Button(modifier = secondButtonModifier,
                        onClick = {
                            val currentDeckReviewQueue = RevisionQueue.currentDeckReviewQueue
                            if (currentDeckReviewQueue == null) {
                                TtsSpeaker.error("No revision queue. Make and or select a deck.")
                                return@Button
                            }
                            val questionFile = checkNotNull(notePartQuestion.recorder).originalFile
                            val answerFile = checkNotNull(notePartAnswer.recorder).originalFile
                            val noteEditor = checkNotNull(DbNoteEditor.instance)
                            var note = Note(questionFile, answerFile)
                            note = noteEditor.insert(note) as Note
                            currentDeckReviewQueue.add(note)

                            notePartQuestion.pendingRecorder = null
                            notePartQuestion.recorder = null
                            notePartQuestion.updateHasPart(false)
                            notePartAnswer.pendingRecorder = null
                            notePartAnswer.recorder = null
                            notePartAnswer.updateHasPart(false)
                        }) {
                        ButtonText(text = "Save note")
                    }

                }
            }
        }
    }

    @Composable
    private fun AudioRecordAndPlayButtonForPart(
        firstButtonModifier: Modifier,
        secondButtonModifier: Modifier,
        notePart: NotePart,
    ) {
        val interactionSource = remember { MutableInteractionSource() }
        val isPressed by interactionSource.collectIsPressedAsState()
        if (isPressed) {
            if (notePart.pendingRecorder == null) {
                notePart.pendingRecorder = AudioRecorder().apply { start() }
            }
            DisposableEffect(Unit) {
                onDispose {
                    val newRecorder = notePart.pendingRecorder
                    if (newRecorder != null) {
                        newRecorder.stop()
                        if (newRecorder.isRecorded) {
                            notePart.updateHasPart(true)
                            val oldRecording = notePart.recorder
                            if (oldRecording != null) {
                                oldRecording.stop()
                                oldRecording.deleteFile()
                            }
                            notePart.recorder = newRecorder
                        } else {
                            TtsSpeaker.speak("Recording failed")
                            newRecorder.deleteFile()
                            notePart.recorder = null
                            notePart.updateHasPart(false)
                        }
                        notePart.pendingRecorder = null
                    }
                }
            }
        }

        Button(modifier = firstButtonModifier,
            interactionSource = interactionSource,
            onClick = {}) {
            ButtonText(
                text = if (isPressed) {
                    "Recording ${notePart.name}"
                } else {
                    "Record ${notePart.name} "
                }
            )
        }
        if (notePart.hasPart()) {
            Button(modifier = secondButtonModifier,
                onClick = {
                    notePart.recorder?.playFile()
                }) {
                ButtonText(text = "Play ${notePart.name}")
            }
        }
    }
}