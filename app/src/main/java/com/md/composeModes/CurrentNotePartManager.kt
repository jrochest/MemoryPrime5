package com.md.composeModes

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.md.DbNoteEditor
import com.md.SpacedRepeaterActivity
import com.md.provider.Note
import dagger.Lazy
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

@ActivityScoped
class CurrentNotePartManager @Inject constructor(
    @ActivityContext val context: Context,
    val practiceModeComposerManager: Lazy<PracticeModeComposerManager>,
) {
    val activity: SpacedRepeaterActivity by lazy {
        context as SpacedRepeaterActivity
    }

    data class NoteState(
        val currentNote: Note,
        val notePart: NotePart
    )

    // TODOJ this should be the source of true!
    val noteStateFlow = MutableStateFlow<NoteState?>(null)

    val hasNote = MutableStateFlow(false)
    val hasSavable = mutableStateOf(false)
    val notePart = NotePart(updateHasPart = { value: Boolean -> hasSavable.value = value })
    init {
        changeCurrentNotePart(null, null)
    }

    fun onDelete() {
        noteStateFlow.value = null
        notePart.clearRecordings()
        hasNote.value = false
        hasSavable.value = false
    }

    fun clearPending() {
        notePart.clearRecordings()
        hasSavable.value = false
    }

    fun changeToAnswerForCurrent() {
        val noteState = noteStateFlow.value ?: return
        noteStateFlow.value = noteState.copy(notePart = noteState.notePart.copy(partIsAnswer = true))
    }

    fun changeToQuestionForCurrent() {
        val noteState = noteStateFlow.value ?: return
        noteStateFlow.value = noteState.copy(notePart = noteState.notePart.copy(partIsAnswer = false))
    }

    fun changeCurrentNotePart(note: Note?, partIsAnswer: Boolean?) {
        if (note == null || partIsAnswer == null) {
            notePart.clearRecordings()
            hasNote.value = false
            hasSavable.value = false
            return
        }
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
        val noteState = checkNotNull(noteStateFlow.value)
        val note = checkNotNull(noteState.currentNote)
       val partIsAnswer = checkNotNull(notePart.partIsAnswer)
        if (partIsAnswer) {
            note.answer = filename
        } else {
            note.question = filename
        }
        DbNoteEditor.instance!!.update(note)
    }
}