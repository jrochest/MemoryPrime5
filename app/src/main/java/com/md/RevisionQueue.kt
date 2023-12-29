package com.md

import android.util.Log
import com.md.provider.Note
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class RevisionQueue {
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


    fun preload() {
        val preloadAble = notesToReview.getOrNull(1) ?: return
        AudioPlayer.instance.preload(preloadAble.question)
        AudioPlayer.instance.preload(preloadAble.answer)
    }

    companion object {
        @JvmStatic
		var currentDeckReviewQueue: RevisionQueue? = null
    }
}

@ActivityScoped
class RevisionQueueStateModel
    @Inject constructor() {
        val queue: MutableStateFlow<RevisionQueue?> = MutableStateFlow(null)
}