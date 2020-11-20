package com.md;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.Vector;

import android.util.Log;

import com.md.provider.Note;

public class RevisionQueue {
	private static RevisionQueue instance = null;

	private HashMap<Integer, Note> revisionQueue = new HashMap<Integer, Note>();

	public Object clone() {
		RevisionQueue revisionQueue2 = new RevisionQueue();
		revisionQueue2.makeThisLookLikeThat(this);
		return revisionQueue2;
	}

	@SuppressWarnings("unchecked")
	public void makeThisLookLikeThat(RevisionQueue revisionQueueThat) {
		this.revisionQueue = (HashMap<Integer, Note>) revisionQueueThat.revisionQueue.clone();
	}

	public static RevisionQueue getInstance() {
		if (instance == null) {
			instance = new RevisionQueue();
		}
		return instance;
	}

	public void remove(int id) {
		revisionQueue.remove(id);
	}

	public void populate(final DbNoteEditor noteEditor, int category) {
		revisionQueue.clear();
		Vector<Note> revisionQueueLocal = noteEditor.getOverdue(category);
		for (Note note : revisionQueueLocal) {
			if (note.is_overdue()) {
				revisionQueue.put(note.getId(), note);
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
				revisionQueue.put(note.getId(), note);
			} else {
				Log.wtf(this.getClass().toString(),
						"The overdue function is screwed up");
			}
		}
	}

	public void add(Note newVal) {
      revisionQueue.put(newVal.getId(), newVal);
	}

	public Note getFirst() {
		if (revisionQueue.isEmpty()) {
			return null;
		}

		Iterator<Note> queueIterator = revisionQueue.values().iterator();
		Note returnVal = queueIterator.next();
		queueIterator.remove();
		return returnVal;
	}

	public int getSize() {
		return revisionQueue.size();
	}

	public void update(Note currentNote) {
		if (revisionQueue.containsKey(currentNote.getId())) {
			revisionQueue.put(currentNote.getId(), currentNote);
		}
	}
}
