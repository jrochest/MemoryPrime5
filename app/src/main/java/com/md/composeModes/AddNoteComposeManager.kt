package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.md.AudioRecorder
import com.md.DbNoteEditor
import com.md.DefaultDeckToAddNewNotesToSharedPreference
import com.md.RecordingTooQuiet
import com.md.RecordingTooSmallException
import com.md.SpacedRepeaterActivity
import com.md.composeStyles.ButtonStyles.ImportantButtonColor
import com.md.composeStyles.ButtonStyles.MediumImportanceButtonColor
import com.md.modesetters.DeckLoadManager
import com.md.modesetters.TtsSpeaker
import com.md.provider.Note
import com.md.viewmodel.TopModeFlowProvider
import dagger.Lazy
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider


@ActivityScoped
class AddNoteComposeManager @Inject constructor(
    @ActivityContext val context: Context,
    private val audioRecorderProvider: Provider<AudioRecorder>,
    private val topModeFlowProvider: TopModeFlowProvider,
    private val workingModeSetter: Lazy<ComposeModeSetter>,
    private val deckModeStateModel: DeckModeStateModel,
    private val deckLoadManager: DeckLoadManager,
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
        val edges = remember { mutableStateListOf<UserDoodleArea.Edge>() }
        val buttonModifier = Modifier.fillMaxHeight()
        val firstButtonModifier = buttonModifier.fillMaxWidth(.5f)
        val secondButtonModifier = buttonModifier.fillMaxWidth(1f)
        Column {
            Row(Modifier.fillMaxHeight(.25f).padding(top = 8.dp)) {
                UserDoodleArea.DrawingCanvas(edges = edges)
            }
            Row(Modifier.fillMaxHeight(.35f)) {
                AudioRecordForPart(
                    firstButtonModifier, notePart = notePartQuestion,
                    audioRecorderProvider
                )
                PlayButtonForRecorderIfPending(
                    secondButtonModifier, notePart = notePartQuestion,
                    hasSavable = state.hasQuestion
                )
            }
            Row(Modifier.fillMaxHeight(.60f)) {
                AudioRecordForPart(
                    firstButtonModifier, notePart = notePartAnswer, audioRecorderProvider
                )
                PlayButtonForRecorderIfPending(
                    secondButtonModifier, notePart = notePartAnswer,
                    hasSavable = state.hasAnswer
                )
            }
            Row(Modifier.fillMaxHeight(100f)) {
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
                        edges.clear()
                    }) {
                    RecorderButtonText(text = "Reset")
                }
                if (state.hasAnswer.value && state.hasQuestion.value) {
                    val text = "Save note"
                    OutlinedButton(modifier = secondButtonModifier.semantics {
                                                                             contentDescription = text
                    },
                        onClick = {
                            val deck = DefaultDeckToAddNewNotesToSharedPreference.getDeck(activity)
                            if (deck == null) {
                                TtsSpeaker.error("Choose a default deck to save to")
                                deckModeStateModel.modeModel.value = DeckMode.ChooseDeckToAddNewItemsTo
                                topModeFlowProvider.modeModel.value = Mode.DeckChooser
                                workingModeSetter.get().switchMode(context = activity)
                                return@OutlinedButton
                            }

                            val questionFile =
                                checkNotNull(notePartQuestion.savableRecorder).generatedAudioFileNameWithExtension
                            val answerFile =
                                checkNotNull(notePartAnswer.savableRecorder).generatedAudioFileNameWithExtension
                            val noteEditor = checkNotNull(DbNoteEditor.instance)
                            var note = Note(questionFile, answerFile, deck.id)

                            note = noteEditor.insert(note) as Note

                            val decks = deckLoadManager.decks.value
                            decks?.firstOrNull { it.id == deck.id }?.let {
                                it.revisionQueue.add(note)
                            }

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


@Composable
fun AudioRecordForPart(
    modifier: Modifier,
    notePart: NotePart,
    audioRecorderProvider: Provider<AudioRecorder>
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
                    notePart.pendingRecorder = audioRecorderProvider.get().apply { start() }
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
                    }  catch (e: RecordingTooQuiet) {
                        TtsSpeaker.speak("Recording too quiet")
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
    // https://developer.android.com/jetpack/compose/kotlin#coroutines
    // Create a CoroutineScope that follows this composable's lifecycle
    val coroutineScope = rememberCoroutineScope()
    if (hasSavable.value) {
        OutlinedButton(modifier = modifier,
            onClick = {
                coroutineScope.launch(context = Dispatchers.Main) {
                    notePart.savableRecorder?.playFile()
                }
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