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

	private HashMap<Integer, Note> repQueue = new HashMap<Integer, Note>();

	public RevisionQueue() {

	}

	public Object clone() {
		RevisionQueue revisionQueue2 = new RevisionQueue();

		revisionQueue2.makeThisLookLikeThat(this);

		return revisionQueue2;
	}

	@SuppressWarnings("unchecked")
	public void makeThisLookLikeThat(RevisionQueue revisionQueueThat) {

		this.revisionQueue = (HashMap<Integer, Note>) revisionQueueThat.revisionQueue
				.clone();
		
		this.repQueue = (HashMap<Integer, Note>) revisionQueueThat.repQueue
		.clone();
	}

	public static RevisionQueue getInstance() {
		if (instance == null) {
			instance = new RevisionQueue();
		}
		return instance;
	}

	public void remove(int id) {
		revisionQueue.remove(id);
		repQueue.remove(id);
	}

	public void populate(final DbNoteEditor noteEditor, int category) {

		revisionQueue.clear();
		repQueue.clear();

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
		if (repQueue.containsKey(newVal.getId())) {
			repQueue.put(newVal.getId(), newVal);
		}
		revisionQueue.put(newVal.getId(), newVal);
	}

	final int SHORT_TERM_MEMORY = 5000;

	public Note getFirst() {

		Note returnVal = null;

		if (repQueue.isEmpty()) {
			int revQueueSize = revisionQueue.size();
			int repQueueSize = 0;

			Iterator<Note> iterator = revisionQueue.values().iterator();

			while (repQueueSize < SHORT_TERM_MEMORY && revQueueSize > 0) {
				Note next = iterator.next();
				repQueue.put(next.getId(), next);
				iterator.remove();

				repQueueSize++;
				revQueueSize--;
			}
		}

		// If this is empty here it means we are done.
		// Because it should have been filled.
		if (!repQueue.isEmpty()) {
			// Get the first
			Note note = getRandomValue(repQueue);

			revisionQueue.put(note.getId(), note);
			repQueue.remove(note.getId());

			returnVal = note;
		}

		return returnVal;
	}

	private Note getRandomValue(HashMap<Integer, Note> queue) {

		// Get a random value.
		Random generator = new Random();
		Object[] array = queue.values().toArray();
		Object randomValue = array[generator.nextInt(array.length)];

		return (Note) randomValue;
	}

	public int getSize() {
		return revisionQueue.size() + repQueue.size();
	}

	public void update(Note currentNote) {
		if (repQueue.containsKey(currentNote.getId())) {
			repQueue.put(currentNote.getId(), currentNote);
		}

		if (revisionQueue.containsKey(currentNote.getId())) {
			revisionQueue.put(currentNote.getId(), currentNote);
		}
	}
}
