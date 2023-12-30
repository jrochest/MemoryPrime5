package com.md.composeModes

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.md.AudioRecorder
import com.md.DbNoteEditor
import com.md.SpacedRepeaterActivity
import com.md.provider.Note
import dagger.Lazy
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


@ActivityScoped
class RecordButtonController @Inject constructor(
    @ActivityContext val context: Context,
    private val currentNotePartManager: CurrentNotePartManager
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }
    private var audioRecorder: AudioRecorder? = null

    fun onTripleTap() {
        val recorder = audioRecorder
        if (recorder != null) {
            recorder.stop()
            if (recorder.isRecorded) {
                val filename = recorder.originalFile
                currentNotePartManager.updateAudioFilename(filename)
            }

        } else {
            val recorder = AudioRecorder()
            this.audioRecorder = recorder
            recorder.start()
        }

    }
}

@ActivityScoped
class CurrentNotePartManager @Inject constructor(
    @ActivityContext val context: Context,
    val practiceModeComposerManager: Lazy<PracticeModeComposerManager>,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    class NoteState(
        val currentNote: Note,
        val notePart: NotePart
    )

    val noteStateFlow = MutableStateFlow<NoteState?>(null)


    var currentNote: Note? = null
    private var partIsAnswer: Boolean? = null
    val hasNote = MutableStateFlow(false)
    val hasSavable = mutableStateOf(false)
    val notePart = NotePart(updateHasPart = { value: Boolean -> hasSavable.value = value })
    init {
        changeCurrentNotePart(null, null)
    }

    fun onDelete() {
        noteStateFlow.value = null
        notePart.clearRecordings()
        currentNote = null
        this.partIsAnswer = null
        hasNote.value = false
        hasSavable.value = false
    }

    fun changeCurrentNotePart(note: Note?, partIsAnswer: Boolean?) {
        if (note == null || partIsAnswer == null) {
            notePart.clearRecordings()
            currentNote = null
            this.partIsAnswer = null
            hasNote.value = false
            hasSavable.value = false
            return
        }

        currentNote = note
        this.partIsAnswer = partIsAnswer
        // Create a note part that has all the parts already.
        notePart.partIsAnswer = partIsAnswer
        hasNote.value = true
        noteStateFlow.value = NoteState(note, notePart)
    }

    fun saveNewAudio() {
       val savableRecorder = checkNotNull(checkNotNull(notePart, {"note part null"}).consumeSavableRecorder())
        updateAudioFilename(savableRecorder.originalFile)
        notePart.clearRecordings()
     }

    fun updateAudioFilename(filename: String) {
       val note = checkNotNull(currentNote)
       val partIsAnswer = checkNotNull(partIsAnswer)
        if (partIsAnswer) {
            note.answer = filename
        } else {
            note.question = filename
        }
        DbNoteEditor.instance!!.update(note)
    }
}

@Composable
fun UnlockRecordButton(
    modifier: Modifier,
    unlock: () -> Unit,
) {
        TripleTapButton(
            modifier = modifier,
            onTripleTap = unlock

        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Record (Locked)",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "Triple Tap and then tap hold to record",
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
}