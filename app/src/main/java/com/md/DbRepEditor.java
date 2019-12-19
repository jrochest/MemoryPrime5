package com.md;

import static com.md.NotesProvider.REPS_TABLE_NAME;

import com.md.provider.AbstractRep;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

/**
 * A generic activity for editing a note in a database. This can be used either
 * to simply view a note {@link Intent#ACTION_VIEW}, view and edit a note
 * {@link Intent#ACTION_EDIT}, or create a new note {@link Intent#ACTION_INSERT}
 * .
 */
public class DbRepEditor {
	Activity context;

	public void setContext(Activity context) {
		this.context = context;
	}

	private static DbRepEditor instance = null;

	protected DbRepEditor() {
	}

	public static DbRepEditor getInstance() {
		if (instance == null) {
			instance = new DbRepEditor();
		}
		return instance;
	}

	public void insert(AbstractRep rep) {
		ContentValues values = new ContentValues();

		values.put(AbstractRep.NOTE_ID, rep.getNoteId());

		values.put(AbstractRep.INTERVAL, rep.getInterval());

		values.put(AbstractRep.SCORE, rep.getScore());

		values.put(AbstractRep.TIME_STAMP_MS, rep.getTimeStampMs());

		SQLiteDatabase db = SQLiteDatabase
				.openDatabase(DbContants.getFullPath(), null,
						SQLiteDatabase.OPEN_READWRITE);

		long rowId = db.insert(REPS_TABLE_NAME, AbstractRep.INTERVAL, values);

		db.close();
		if (rowId > 0) {
			rep.setId(rowId);
			System.out.println("Successfully insert rep row " + rep + " id "+ rowId);
			return;
		}

		throw new SQLException("Failed to insert rep row: " + rep);
	}
}
