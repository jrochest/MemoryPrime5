package com.md;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Vector;

import android.util.Log;

import com.md.provider.Note;

public class RevisionQueue {
	private static RevisionQueue instance = null;

	private HashMap<Integer, Note> noteIdToNoteToReview = new HashMap<Integer, Note>();

	public Object clone() {
		RevisionQueue revisionQueue2 = new RevisionQueue();
		revisionQueue2.makeThisLookLikeThat(this);
		return revisionQueue2;
	}

	@SuppressWarnings("unchecked")
	public void makeThisLookLikeThat(RevisionQueue revisionQueueThat) {
		this.noteIdToNoteToReview = (HashMap<Integer, Note>) revisionQueueThat.noteIdToNoteToReview.clone();
	}

	public static RevisionQueue getInstance() {
		if (instance == null) {
			instance = new RevisionQueue();
		}
		return instance;
	}

	public void remove(int id) {
		noteIdToNoteToReview.remove(id);
	}

	public void populate(final DbNoteEditor noteEditor, int category) {
		noteIdToNoteToReview.clear();
		Vector<Note> revisionQueueLocal = noteEditor.getOverdue(category);
		for (Note note : revisionQueueLocal) {
			if (note.is_overdue()) {
				noteIdToNoteToReview.put(note.getId(), note);
			} else {
				Log.wtf(this.getClass().toString(),
						"The overdue function is screwed up");
			}
		}

		revisionQueueLocal = noteEditor.getAquisitionReps(category);

		// TODO restore ability to have these go last.
		// Perhaps by never repeating ids
		for (Note note : revisionQueueLocal) {
			if (note.is_due_for_acquisition_rep()) {
				noteIdToNoteToReview.put(note.getId(), note);
			} else {
				Log.wtf(this.getClass().toString(),
						"The overdue function is screwed up");
			}
		}
	}

	public void add(Note newVal) {
      noteIdToNoteToReview.put(newVal.getId(), newVal);
	}

	public Note getFirst() {
		if (noteIdToNoteToReview.isEmpty()) {
			return null;
		}

		Iterator<Note> queueIterator = noteIdToNoteToReview.values().iterator();
		Note returnVal = queueIterator.next();
		queueIterator.remove();
		return returnVal;
	}

	public int getSize() {
		return noteIdToNoteToReview.size();
	}

	public void update(Note currentNote) {
		if (noteIdToNoteToReview.containsKey(currentNote.getId())) {
			noteIdToNoteToReview.put(currentNote.getId(), currentNote);
		}
	}
}
