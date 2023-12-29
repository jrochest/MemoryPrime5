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
import com.md.AudioPlayer
import com.md.AudioRecorder
import com.md.SpacedRepeaterActivity
import com.md.modesetters.TtsSpeaker
import dagger.hilt.android.scopes.ActivityScoped
import javax.inject.Inject

@ActivityScoped
class AddNoteComposeManager @Inject constructor(
    val activity: SpacedRepeaterActivity,
    val recordButtonController: RecordButtonController
) {


    @Stable
    class State(val hasQuestion: MutableState<Boolean> = mutableStateOf(false))

    val state=  State()


    var questionRecorder: AudioRecorder? = null
    var pendingQuestionRecorder: AudioRecorder? = null
    var answerRecorder: AudioRecorder? = null


    @Composable
    fun ComposeMode() {
        ShowUiForState(state)
    }

    @Composable
    fun ShowUiForState(state: State) {
        val buttonModifier = Modifier.fillMaxHeight()
        val firstButtonModifier = buttonModifier.fillMaxWidth(.5f)
        val secondButtonModifier = buttonModifier.fillMaxWidth(1f)
        @Composable
        fun ButtonText(text: String,) {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }

        Column {
            Row(Modifier.fillMaxHeight(.33f)) {
                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                if (isPressed) {
                    if (pendingQuestionRecorder == null) {
                        pendingQuestionRecorder = AudioRecorder().apply { start() }
                    }
                    DisposableEffect(Unit) {
                        onDispose {
                            val newRecorder = pendingQuestionRecorder
                            if (newRecorder != null) {
                                newRecorder.stop()
                                if (newRecorder.isRecorded) {
                                    state.hasQuestion.value = true
                                    val oldRecording = questionRecorder
                                    if (oldRecording != null) {
                                        oldRecording.stop()
                                        oldRecording.deleteFile()
                                    }
                                    questionRecorder = newRecorder
                                } else {
                                    TtsSpeaker.speak("Recording failed")
                                    newRecorder.deleteFile()
                                    questionRecorder = null
                                    state.hasQuestion.value = false
                                }
                                pendingQuestionRecorder = null
                            }
                        }
                    }
                }

                Button(modifier = firstButtonModifier,
                    interactionSource = interactionSource,
                    onClick = {

                    }) {
                    ButtonText(text = if (isPressed) { "Recording" } else { "Record question "})
                }
                if (state.hasQuestion.value) {
                    Button(modifier = secondButtonModifier,
                        onClick = {
                            questionRecorder?.playFile()
                        }) {
                        ButtonText(text = "Play question")
                    }
                }

            }
            Row(Modifier.fillMaxHeight(.5f)) {
                Button(modifier = firstButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Record answer")
                }
                Button(modifier = secondButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Save answer")
                }
            }
            Row(Modifier.fillMaxHeight(1f)) {
                Button(modifier = firstButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Reset")
                }
                Button(modifier = secondButtonModifier,
                    onClick = { /*TODO*/ }) {
                    ButtonText(text = "Save note")
                }
            }
        }
    }
}