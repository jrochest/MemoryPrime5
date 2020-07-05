package com.md;

import java.util.Objects;
import java.util.Vector;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;

import com.md.provider.AbstractDeck;
import com.md.provider.AbstractNote;
import com.md.provider.Deck;
import com.md.provider.Note;
import com.md.utils.ToastSingleton;

/**
 * A generic activity for editing a note in a database. This can be used either
 * to simply view a note {@link Intent#ACTION_VIEW}, view and edit a note
 * {@link Intent#ACTION_EDIT}, or create a new note {@link Intent#ACTION_INSERT}
 * .
 */
public class DbNoteEditor {

	private String currentId;

	Activity context;

	public void setContext(Activity context) {
		this.context = context;

		// TODO Do this to init the database.
		getOverdue(0);
	}

	private static DbNoteEditor instance = null;
	private Vector<NoteEditorListener> listeners = new Vector<NoteEditorListener>();

	public void addListener(NoteEditorListener listener) {
		listeners.add(listener);
	}

	protected DbNoteEditor() {

	}

	// TODO make the DbNoteEditor not use a TextView to update BrowseMode
	// Make it a callback!!!

	public static DbNoteEditor getInstance() {
		if (instance == null) {
			instance = new DbNoteEditor();
		}
		return instance;
	}

	public void update(Activity activity, AbstractNote note) {

		// If it's in there update it.
		RevisionQueue.getInstance().update((Note) note);
		ContentValues values = new ContentValues();

		// Bump the modification time to now.
		noteToContentValues(note, values);

		// TODO figure out how to get the ID URI
		try {
			activity.getContentResolver().update(AbstractNote.CONTENT_URI,
					values, Note._ID + "=" + note.getId(), null);

		} catch (Exception e) {
			String message = e.getMessage();
			System.out.println(message);
		}
	}

	public AbstractNote insert(Activity activity, AbstractNote note) {

		ContentValues values = new ContentValues();

		// Bump the modification time to now.
		noteToContentValues(note, values);

		try {
			Uri uri = activity.getContentResolver().insert(
					AbstractNote.CONTENT_URI, values);

			String noteId = uri.getPathSegments().get(1);

			note.setId(Integer.parseInt(noteId));

		} catch (Exception e) {

			// TODO Log this.
			String message = e.getMessage();
			System.out.println(message);
		}

		return note;
	}

	private void noteToContentValues(AbstractNote note, ContentValues values) {
		values.put(AbstractNote.GRADE, note.getGrade());
		values.put(AbstractNote.QUESTION, note.getQuestion());
		values.put(AbstractNote.ANSWER, note.getAnswer());
		values.put(AbstractNote.CATEGORY, note.getCategory());

		values.put(AbstractNote.EASINESS, note.getEasiness());
		values.put(AbstractNote.ACQ_REPS, note.getAcq_reps());
		values.put(AbstractNote.RET_REPS, note.getRet_reps());
		values.put(AbstractNote.ACQ_REPS_SINCE_LAPSE, note
				.getAcq_reps_since_lapse());
		values.put(AbstractNote.RET_REPS_SINCE_LAPSE, note
				.getRet_reps_since_lapse());
		values.put(AbstractNote.LAPSES, note.getLapses());
		values.put(AbstractNote.LAST_REP, note.getLast_rep());
		values.put(AbstractNote.NEXT_REP, note.getNext_rep());
		values.put(AbstractNote.UNSEEN, note.isUnseen());
		values.put(AbstractNote.MARKED, note.isMarked());
	}

	public Note getFirst() {

		Note returnValue = null;

		Cursor query = null;

		String queryString = "SELECT MIN(" + Note._ID + ") FROM "
				+ NotesProvider.NOTES_TABLE_NAME + " WHERE "
				+ categoryCriteria();

		if (markedMode) {
			queryString += " AND " + Note.MARKED + " = 1";
		}

		query = rawQuery(queryString);

		if (query != null && query.moveToNext()) {
			if (query.getInt(0) != 0) {
				currentId = "" + query.getInt(0);
				returnValue = loadDataCurrentId();
			}
		}

		if (query != null) {
			query.close();
		}

		return returnValue;

	}

	public Vector<Deck> queryDeck() {
		Cursor query = rawQuery("SELECT * FROM "
				+ NotesProvider.DECKS_TABLE_NAME);



		Vector<Deck> vector = new Vector<Deck>();
		if (query == null) {
			return vector;
		}

		String keyword = "";
		while (query.moveToNext()) {
			keyword += "\n";

			int _id = query.getInt(query.getColumnIndex("_id"));
			String name = query.getString(query
					.getColumnIndex(AbstractDeck.NAME));

			System.out.println("name: " + name);

			vector.add(new Deck(_id, name));
		}

		return vector;
	}

	public Cursor rawQuery(String queryString) {
		Cursor query = null;

		try {
			SQLiteDatabase checkDB = SQLiteDatabase
					.openDatabase(DbContants.getDatabasePath(), null,
							SQLiteDatabase.OPEN_READWRITE);

			query = checkDB.rawQuery(queryString, null);
		} catch (Exception e) {
			ToastSingleton.getInstance().error(e.getMessage());
		}

		return query;
	}

	public Vector<Integer> getUpcomingReps() {

		Vector<Integer> reps = new Vector<Integer>();
		Cursor query = null;

		final int NUMBER_TO_COUNT = 12;

		for (int idx = 0; idx < NUMBER_TO_COUNT; idx++) {

			int currentDay = idx
					+ CategorySingleton.getInstance().getDaysSinceStart();
			String queryString = "SELECT COUNT(" + Note._ID + ") FROM "
					+ NotesProvider.NOTES_TABLE_NAME + " WHERE " + currentDay
					+ " = " + Note.NEXT_REP;

			try {

				query = rawQuery(queryString);
			} catch (Exception e) {
				String getMsg = e.getMessage();
				System.out.println(getMsg);
			}

			Vector<Note> notes = new Vector<Note>();
			while (query != null && query.moveToNext()) {
				reps.add(query.getInt(0));
			}

			if (query != null) {
				query.close();
			}

		}
		return reps;

	}

	public Vector<Note> getOverdue(int category) {

		// TODO make this look for ones that actually need to be reviewed.
		Cursor query = null;

		final CategorySingleton catSingleton = CategorySingleton.getInstance();

		// If it's not a note that we just failed on.
		String selection = Note.GRADE + " >= " + 2
				// The right category name
				+ " AND " + categoryCriteria(category)
				// Normal overdue
				+ " AND ((" + catSingleton.getDaysSinceStart() + " > " + Note.NEXT_REP + ") ";
		        // Note this needs an end paren.

		if (catSingleton.getLookAheadDays() == 0) {
			// End paren
			selection += ")";
		} else {
			int oldNoteDays = 20;
			int overDueDate = catSingleton.getDaysSinceStart() + catSingleton.getLookAheadDays();
			// Over due with look ahead.
			selection += " OR (" + overDueDate + " > " + Note.NEXT_REP
					// Is mature.
					+ " AND " + Note.NEXT_REP + " - " + Note.LAST_REP + " > " + oldNoteDays + ")"
					// End paran
			        + ")";
		}

		try {
			query = context.getContentResolver().query(
					AbstractNote.CONTENT_URI, null, selection, null,
					AbstractNote.DEFAULT_SORT_ORDER);
		} catch (Exception e) {
			String getMsg = e.getMessage();
			System.out.println(getMsg);
		}

		Vector<Note> notes = new Vector<>();
		while (query != null && query.moveToNext()) {
			notes.add(queryGetOneNote(query));
		}

		if (query != null) {
			query.close();
		}

		return notes;
	}

	final String[] JUST_ID_PROJECTION = new String[] { Note._ID };

	private Note mNote;

	private boolean markedMode;

	public Note getNext() {
		return getNext(0);
	}

	public Note getNext(int howFarToGo) {

		Note returnVal = null;

		Cursor query = null;

		if (currentId == null) {
			return null;
		}

		String queryString = "SELECT MIN(" + Note._ID + ") FROM "
				+ NotesProvider.NOTES_TABLE_NAME + " WHERE " + Note._ID + " > "
				+ (Integer.parseInt(currentId) + howFarToGo) + " AND "
				+ categoryCriteria();

		if (markedMode) {
			queryString += " AND " + Note.MARKED + " = 1";
		}

		query = rawQuery(queryString);

		// If we found nothing just go to the max.
		if (!query.moveToFirst() || query.getInt(0) == 0) {

			queryString = "SELECT MAX(" + Note._ID + ") FROM "
					+ NotesProvider.NOTES_TABLE_NAME + " WHERE "
					+ categoryCriteria();

			if (markedMode) {
				queryString += " AND " + Note.MARKED + " = 1";
			}

			query = rawQuery(queryString);
		}

		if (query.moveToFirst()) {
			if (query.getInt(0) != 0) {
				currentId = "" + query.getInt(0);
				returnVal = loadDataCurrentId();
			}
		}

		if (query != null) {
			query.close();
		}

		return returnVal;

	}

	private Note loadDataCurrentId() {

		if (currentId != null) {
			mNote = loadNote(Integer.parseInt(currentId));
		} else {
			// The listener need to be able to handle null notes.
			mNote = null;
		}

		for (NoteEditorListener listener : listeners) {
			listener.onNoteUpdate(mNote);
		}

		return mNote;
	}

	private Note loadNote(int currentId) {

		Cursor query;
		query = context.getContentResolver().query(AbstractNote.CONTENT_URI,
				null, Note._ID + " = " + currentId, null,
				AbstractNote.DEFAULT_SORT_ORDER);

		if (query.moveToNext()) {
			mNote = queryGetOneNote(query);
		} else {
			mNote = null;
		}

		if (query != null) {
			query.close();
		}

		return mNote;
	}

	private Note queryGetOneNote(Cursor query) {

		String question = query.getString(query.getColumnIndex(Note.QUESTION));
		String answer = query.getString(query.getColumnIndex(Note.ANSWER));

		mNote = new Note(question, answer);

		int id = query.getInt(query.getColumnIndex(Note._ID));

		mNote.setId(id);

		int grade = query.getInt(query.getColumnIndex(Note.GRADE));

		mNote.setGrade(grade);

		String category = query.getString(query.getColumnIndex(Note.CATEGORY));

		mNote.setCategory(Integer.parseInt(category));

		String unseenString = query
				.getString(query.getColumnIndex(Note.UNSEEN));

		Boolean unseen = false;

		if (unseenString.equals("1")) {
			unseen = true;
		}

		mNote.setUnseen(unseen);

		String markedString = query
				.getString(query.getColumnIndex(Note.MARKED));

		Boolean marked = false;

		if (markedString != null && markedString.equals("1")) {
			marked = true;
		}

		mNote.setMarked(marked);

		float easiness = query.getFloat(query.getColumnIndex(Note.EASINESS));

		mNote.setEasiness(easiness);

		int acq_reps = query.getInt(query.getColumnIndex(Note.ACQ_REPS));

		mNote.setAcq_reps(acq_reps);

		int ret_reps = query.getInt(query.getColumnIndex(Note.RET_REPS));

		mNote.setRet_reps(ret_reps);

		int lapses = query.getInt(query.getColumnIndex(Note.LAPSES));

		mNote.setLapses(lapses);

		int acq_reps_since_lapse = query.getInt(query
				.getColumnIndex(Note.ACQ_REPS_SINCE_LAPSE));

		mNote.setAcq_reps_since_lapse(acq_reps_since_lapse);

		int ret_reps_since_lapse = query.getInt(query
				.getColumnIndex(Note.RET_REPS_SINCE_LAPSE));

		mNote.setRet_reps_since_lapse(ret_reps_since_lapse);

		int next_rep = query.getInt(query.getColumnIndex(Note.NEXT_REP));

		mNote.setNext_rep(next_rep);

		int last_rep = query.getInt(query.getColumnIndex(Note.LAST_REP));

		mNote.setLast_rep(last_rep);
		return mNote;
	}

	public Note getLast() {
		return getLast(0);
	}

	public Note getLast(int howFarToGo) {

		Note returnVal = null;

		Cursor query = null;

		if (currentId == null) {
			return null;
		}

		String queryString = "SELECT MAX(" + Note._ID + ") FROM "
				+ NotesProvider.NOTES_TABLE_NAME + " WHERE " + Note._ID + " < "
				+ (Integer.parseInt(currentId) - howFarToGo) + " AND "
				+ categoryCriteria();

		if (markedMode) {
			queryString += " AND " + Note.MARKED + " = 1";
		}

		query = rawQuery(queryString);

		// If we found nothing just go to the max.
		if (!query.moveToFirst() || query.getInt(0) == 0) {

			queryString = "SELECT MIN(" + Note._ID + ") FROM "
					+ NotesProvider.NOTES_TABLE_NAME + " WHERE "
					+ categoryCriteria();

			if (markedMode) {
				queryString += " AND " + Note.MARKED + " = 1";
			}

			query = rawQuery(queryString);
		}

		if (query.moveToFirst()) {
			if (query.getInt(0) != 0) {
				currentId = "" + query.getInt(0);
				returnVal = loadDataCurrentId();
			}
		}

		if (query != null) {
			query.close();
		}

		return returnVal;
	}

	private String categoryCriteria() {
		return categoryCriteria(CategorySingleton.getInstance()
				.getCurrentDeck());
	}

	private String categoryCriteria(int category) {
		return " " + Note.CATEGORY + " = '" + category + "' ";
	}

	public Note deleteCurrent(Activity context, Note note) {
		return deleteNote(context, note);
	}

	public Note deleteNote(Activity context, Note note) {

		Note returnVal = null;

		if (note == null) {
			return null;
		}

		context.getContentResolver().delete(AbstractNote.CONTENT_URI,
				Note._ID + " = " + note.getId(), null);

		if (note != null) {
			if (!AudioRecorder.deleteFile(note.getAnswer())) {
				Log.e(this.getClass().toString(), "Couldn't answer question "
						+ note.getAnswer());
			}

			if (!AudioRecorder.deleteFile(note.getQuestion())) {
				Log.e(this.getClass().toString(), "Couldn't delete question "
						+ note.getQuestion());
			}
		}
		if (currentId != null) {
			int intCurrentId = Integer.parseInt(currentId);
			RevisionQueue.getInstance().remove(intCurrentId);
		}

		if (Objects.equals(this.mNote, note)) {
			returnVal = getNext();

			// If double fail then clear the data.
			if (returnVal == null) {
				returnVal = getLast();

				// Double fail no more items.
				if (returnVal == null) {
					currentId = null;
					loadDataCurrentId();
				}
			}
		}

		return returnVal;
	}

	public Note getNote() {
		return mNote;

	}

	public void debugDeleteAll() {
		context.getContentResolver().delete(AbstractNote.CONTENT_URI, null,
				null);

		if (currentId != null) {
			int intCurrentId = Integer.parseInt(currentId);
			RevisionQueue.getInstance().remove(intCurrentId);
		}

	}

	public Vector<Note> getAquisitionReps(int category) {

		// TODO make this look for ones that actually need to be reviewed.
		Cursor query;

		String selection = Note.GRADE + " < " + 2 + " AND "
				+ categoryCriteria(category);

		query = context.getContentResolver().query(AbstractNote.CONTENT_URI,
				null, selection, null, AbstractNote.DEFAULT_SORT_ORDER);

		Vector<Note> notes = new Vector<Note>();
		while (query != null && query.moveToNext()) {
			notes.add(queryGetOneNote(query));
		}

		if (query != null) {
			query.close();
		}

		return notes;
	}

	public void setMarkedMode(boolean markedMode) {
		this.markedMode = markedMode;
	}

	public boolean isMarkedMode() {

		return markedMode;
	}

	public void setNullNote() {
		currentId = null;
		loadDataCurrentId();
	}

	public void insertDeck(Deck deck) {

		ContentValues values = new ContentValues();

		// Bump the modification time to now.
		DeckDb.deckToContentValues(deck, values);

		try {
			SQLiteDatabase checkDB = SQLiteDatabase.openDatabase(
					DbContants.getDatabasePath(), null,
					SQLiteDatabase.OPEN_READWRITE);

			checkDB.insertOrThrow(NotesProvider.DECKS_TABLE_NAME, null, values);

		} catch (Exception e) {
			ToastSingleton.getInstance().error(e.getMessage());
		}

	}

	public void deleteDeck(Deck deck) {

		try {
			SQLiteDatabase checkDB = SQLiteDatabase.openDatabase(
					DbContants.getDatabasePath(), null,
					SQLiteDatabase.OPEN_READWRITE);

			String cause = Deck._ID + " = " + deck.getId();
			checkDB.delete(NotesProvider.DECKS_TABLE_NAME, cause, null);

		} catch (Exception e) {
			System.out.println(e.getMessage());
			ToastSingleton.getInstance().error(e.getMessage());
		}
	}

	public void updateDeck(Deck deck) {

		ContentValues values = new ContentValues();

		// Bump the modification time to now.
		DeckDb.deckToContentValues(deck, values);

		try {
			SQLiteDatabase checkDB = SQLiteDatabase.openDatabase(
					DbContants.getDatabasePath(), null,
					SQLiteDatabase.OPEN_READWRITE);

			String cause = Deck._ID + " = " + deck.getId();

			checkDB.update(NotesProvider.DECKS_TABLE_NAME, values, cause, null);
		} catch (Exception e) {
			System.out.println(e.getMessage());
			ToastSingleton.getInstance().error(e.getMessage());
		}
	}

	public int getDeckCount(int id) {

		int deckCount = 0;

		Cursor query = null;

		String COUNT_COLUMN = "MyCount";
		String queryString = "SELECT COUNT(" + Note._ID + ") AS "
				+ COUNT_COLUMN + " FROM " + NotesProvider.NOTES_TABLE_NAME
				+ " WHERE " + Note.CATEGORY + " = '" + id + "'";

		query = rawQuery(queryString);

		if (query != null) {
			// If we found nothing just go to the max.
			if (query.moveToFirst()) {
				deckCount = query.getInt(query.getColumnIndex(COUNT_COLUMN));
			}
			query.close();
		}

		return deckCount;
	}
}
