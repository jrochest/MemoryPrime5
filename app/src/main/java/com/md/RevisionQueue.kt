package com.md

import android.content.Context
import android.util.Log
import com.md.modesetters.DeckInfo
import com.md.provider.Note
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject


class RevisionQueue
@Inject constructor(
    @ActivityContext val context: Context,
    private val audioPlayer: AudioPlayer,
) {
    private var notesToReview = mutableListOf<Note>()
    fun populate(noteEditor: DbNoteEditor, category: Int) {
        notesToReview.clear()
        for (note in noteEditor.getOverdue(category)) {
            if (note.is_overdue) {
                notesToReview.add(note)
            } else {
                Log.wtf(this.javaClass.toString(),
                        "The overdue function is screwed up")
            }
        }

        // TODO restore ability to have these go last.
        // Perhaps by never repeating ids
        for (note in noteEditor.getAquisitionReps(category)) {
            if (note.is_due_for_acquisition_rep) {
                notesToReview.add(note)
            } else {
                Log.wtf(this.javaClass.toString(),
                        "The overdue function is screwed up")
            }
        }

        // Sort mostly by interval and use priority to break ties.
        notesToReview.sortBy { note: Note -> (note.interval * 10000 + -note.priority) }
    }

    fun add(newVal: Note) {
        notesToReview.add(newVal)
    }

    fun addToFront(newVal: Note) {
        notesToReview.add(0, newVal)
    }

    fun peekQueue(): Note? {
        return notesToReview.firstOrNull()
    }

    fun popQueue(): Note? {
        return notesToReview.removeFirstOrNull()
     }

    fun getSize(): Int = notesToReview.size

    fun isEmpty(): Boolean = notesToReview.isEmpty()

    fun updateNote(currentNote: Note, keepQueueLocation: Boolean) {
        if (keepQueueLocation) {
            val originalIndex = notesToReview.indexOfFirst { it.id == currentNote.id }
            if (originalIndex < 0) return
            notesToReview.removeAt(originalIndex)
            notesToReview.add(originalIndex, currentNote)
        } else {
            notesToReview.removeIf { it.id == currentNote.id }
            notesToReview.add(currentNote)
        }
    }

    fun removeNote(id: Int) {
        notesToReview.removeIf { it.id == id }
    }

    fun hardPostpone(note: Note) {
        removeNote(note.id)

    }

    companion object {
        // use the FocusedQueueStateModel instead.
        @JvmStatic
		var currentDeckReviewQueueDeleteThisTODOJSOON: RevisionQueue? = null
    }

    suspend fun preload() {
        val preloadAble = notesToReview.getOrNull(1) ?: return
        println("TODOJ preloading question")
        audioPlayer.preload(preloadAble.question)
        println("TODOJ preloading answer")
        audioPlayer.preload(preloadAble.answer)
        println("TODOJ preloaded both!")
    }
}

@ActivityScoped
class FocusedQueueStateModel
    @Inject constructor() {

    val deck: MutableStateFlow<DeckInfo?> = MutableStateFlow(null)
}