package com.md

import android.util.Log
import com.md.provider.Note
import java.util.*

class RevisionQueue {
    private var noteIdToNoteToReview = mutableMapOf<Int, Note>()

    fun populate(noteEditor: DbNoteEditor, category: Int) {
        noteIdToNoteToReview.clear()
        var revisionQueueLocal = noteEditor.getOverdue(category)
        for (note in revisionQueueLocal) {
            if (note.is_overdue) {
                noteIdToNoteToReview[note.id] = note
            } else {
                Log.wtf(this.javaClass.toString(),
                        "The overdue function is screwed up")
            }
        }
        revisionQueueLocal = noteEditor.getAquisitionReps(category)

        // TODO restore ability to have these go last.
        // Perhaps by never repeating ids
        for (note in revisionQueueLocal) {
            if (note.is_due_for_acquisition_rep) {
                noteIdToNoteToReview[note.id] = note
            } else {
                Log.wtf(this.javaClass.toString(),
                        "The overdue function is screwed up")
            }
        }
    }

    fun add(newVal: Note) {
        noteIdToNoteToReview[newVal.id] = newVal
    }

    fun getFirst(): Note? {
        if (noteIdToNoteToReview.isEmpty()) {
            return null
        }
        val queueIterator = noteIdToNoteToReview.values.iterator()
        val returnVal = queueIterator.next()
        queueIterator.remove()
        return returnVal
    }

    fun getSize(): Int = noteIdToNoteToReview.size

    fun updateNote(currentNote: Note) {
        if (noteIdToNoteToReview.containsKey(currentNote.id)) {
            noteIdToNoteToReview[currentNote.id] = currentNote
        }
    }

    fun removeNote(id: Int) {
        noteIdToNoteToReview.remove(id)
    }

    companion object {
        @JvmStatic
		var currentDeckReviewQueue: RevisionQueue? = null
    }
}