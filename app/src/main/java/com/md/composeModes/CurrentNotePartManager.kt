package com.md.composeModes

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import com.md.DbNoteEditor
import com.md.SpacedRepeaterActivity
import com.md.modesetters.DeckLoadManager
import com.md.provider.Note
import dagger.Lazy
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject

@ActivityScoped
class CurrentNotePartManager @Inject constructor(
    private val deckLoadManager: Lazy<DeckLoadManager>,
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
    // Perhaps delete?
    val hasSavable = mutableStateOf(false)
    init {
        transitionToDeckStagingMode()
    }

    fun transitionToDeckStagingMode() {
        changeCurrentNotePart(null, null)
    }

    fun onDelete() {
        noteStateFlow.value?.notePart?.clearRecordings()
        noteStateFlow.value = null
        hasSavable.value = false
    }

    fun clearPending() {
        noteStateFlow.value?.notePart?.clearRecordings()
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
            noteStateFlow.value?.notePart?.clearRecordings()
            noteStateFlow.value = null
            hasSavable.value = false
            return
        }
        // Create a note part that has all the parts already.
        noteStateFlow.value = NoteState(note, NotePart(partIsAnswer, { value: Boolean -> hasSavable.value = value }))
    }

    fun saveNewAudio() {
       val notePart = noteStateFlow.value?.notePart
       val savableRecorder = checkNotNull(checkNotNull(notePart, {"note part null"}).consumeSavableRecorder())
        updateAudioFilename(savableRecorder.generatedAudioFileNameWithExtension)
        notePart.clearRecordings()
     }

    private fun updateAudioFilename(filename: String) {
        val noteState = checkNotNull(noteStateFlow.value)
        val note = checkNotNull(noteState.currentNote)
       val partIsAnswer = checkNotNull(noteState.notePart.partIsAnswer)
        if (partIsAnswer) {
            note.answer = filename
        } else {
            note.question = filename
        }
        deckLoadManager.get().updateNote(note, keepQueueLocation = true)
        DbNoteEditor.instance!!.update(note)
    }
}