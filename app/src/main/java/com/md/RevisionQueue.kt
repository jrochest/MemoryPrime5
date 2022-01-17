package com.md

import android.util.Log
import com.md.provider.Note

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

        notesToReview.sortBy { note: Note -> note.interval }
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

    companion object {
        @JvmStatic
		var currentDeckReviewQueue: RevisionQueue? = null
    }
}