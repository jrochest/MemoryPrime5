package com.md;

import static com.md.provider.AbstractNote.PRIORITY;

import java.util.HashMap;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.LiveFolders;
import android.text.TextUtils;

import com.md.provider.AbstractDeck;
import com.md.provider.AbstractNote;
import com.md.provider.AbstractRep;
import com.md.provider.Deck;
import com.md.provider.Note;

public class NotesProvider extends ContentProvider {

	public static final String AUTHORITY = BuildConfig.APPLICATION_ID;

	private static final String TAG = "DbInteraction";

	private static final int DATABASE_VERSION = 26;
	public static final String NOTES_TABLE_NAME = "notes";
	public static final String DECKS_TABLE_NAME = "decks";
	public static final String REPS_TABLE_NAME = "reps";

	private static HashMap<String, String> sMDProjectionMap;
	private static HashMap<String, String> sLiveFolderProjectionMap;

	private static final int NOTES = 1;
	private static final int NOTE_ID = 2;
	private static final int LIVE_FOLDER_NOTES = 3;

	private static final UriMatcher sUriMatcher;


	/**
	 * This class helps open, create, and upgrade the database file.
	 */
	public static class DatabaseHelper extends SQLiteOpenHelper {

		DatabaseHelper(Context context) {
			super(context, DbContants.getDatabasePath(context), null, DATABASE_VERSION);

		}

		@Override
		public void onCreate(SQLiteDatabase db) {

			try {
				db.execSQL("CREATE TABLE " + NOTES_TABLE_NAME + " (" + Note._ID
						+ " INTEGER PRIMARY KEY," + AbstractNote.GRADE
						+ " TEXT," + AbstractNote.QUESTION + " TEXT,"
						+ AbstractNote.ANSWER + " TEXT,"
						+ AbstractNote.CATEGORY + " TEXT,"
						+ AbstractNote.UNSEEN + " BOOLEAN,"
						+ AbstractNote.MARKED + " BOOLEAN, "
						+ AbstractNote.EASINESS + " FLOAT, "
						+ AbstractNote.ACQ_REPS + " INTEGER, "
						+ AbstractNote.RET_REPS + " INTEGER, "
						+ AbstractNote.LAPSES + " INTEGER, "
						+ AbstractNote.ACQ_REPS_SINCE_LAPSE + " INTEGER, "
						+ AbstractNote.RET_REPS_SINCE_LAPSE + " INTEGER, "
						+ AbstractNote.NEXT_REP + " INTEGER, "
						+ AbstractNote.LAST_REP + " INTEGER, "
						+ PRIORITY + " INTEGER DEFAULT 100 "
						+ ");");
			} catch (Exception e) {
				String message = e.getMessage();
				System.out.println(message);

			}

			try {
				db.execSQL("CREATE TABLE " + DECKS_TABLE_NAME + " (" + Deck._ID
						+ " INTEGER PRIMARY KEY," + AbstractDeck.NAME + " TEXT"
						+ " );");

			} catch (Exception e) {
				String message = e.getMessage();
				System.out.println(message);

			}
			try {
				db.execSQL("CREATE TABLE " + REPS_TABLE_NAME + " ("
						+ AbstractRep._ID + " INTEGER PRIMARY KEY,"
						+ AbstractRep.NOTE_ID + " INTEGER, "
						+ AbstractRep.SCORE + " INTEGER, "
						+ AbstractRep.INTERVAL + " INTEGER, "
						+ AbstractRep.TIME_STAMP_MS + " INTEGER "
						+ " );");

			} catch (Exception e) {
				String message = e.getMessage();
				System.out.println(message);
			}
		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
			if (oldVersion == 23) {
				oldVersion++;
				try {
					db.execSQL("CREATE TABLE " + REPS_TABLE_NAME + " ("
							+ AbstractRep._ID + " INTEGER PRIMARY KEY,"
							+ AbstractRep.SCORE + " INTEGER, "
							+ AbstractRep.INTERVAL + " INTEGER, "
							+ AbstractRep.TIME_STAMP_MS + " INTEGER "
							+ " );");

				} catch (Exception e) {
					String message = e.getMessage();
					System.out.println(message);
				}
			}

			if (oldVersion == 24) {
				db.execSQL("DROP TABLE IF EXISTS " + REPS_TABLE_NAME);
				oldVersion++;
				try {
					db.execSQL("CREATE TABLE " + REPS_TABLE_NAME + " ("
							+ AbstractRep._ID + " INTEGER PRIMARY KEY, "
							+ AbstractRep.NOTE_ID + " INTEGER, "
							+ AbstractRep.SCORE + " INTEGER, "
							+ AbstractRep.INTERVAL + " INTEGER, "
							+ AbstractRep.TIME_STAMP_MS + " INTEGER "
							+ " );");

				} catch (Exception e) {
					String message = e.getMessage();
					System.out.println(message);
				}
			}

			if (oldVersion == 25) {


				oldVersion++;
				try {
					// TODOJ change tablet and field of this.
					db.execSQL("ALTER TABLE " + NOTES_TABLE_NAME + " ADD COLUMN "  + PRIORITY +  " INTEGER DEFAULT 100");
				} catch (Exception e) {
					String message = e.getMessage();
					System.out.println(message);
				}
			}
		}
	}

	public static DatabaseHelper mOpenHelper;

	DatabaseHelper getOpenHelper() {
		if (mOpenHelper == null) {
			Context context = getContext();
			if (context != null) {
				mOpenHelper = new DatabaseHelper(context);
			}
		}
		return mOpenHelper;
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	@Override
	public Cursor query(Uri uri, String[] projection, String selection,
			String[] selectionArgs, String sortOrder) {

		// Get the database and run the query
		SQLiteDatabase db = getOpenHelper().getReadableDatabase();
		SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
		qb.setTables(NOTES_TABLE_NAME);

		switch (sUriMatcher.match(uri)) {
		case NOTES:
			qb.setProjectionMap(sMDProjectionMap);
			break;

		case NOTE_ID:
			qb.setProjectionMap(sMDProjectionMap);
			qb.appendWhere(Note._ID + "=" + uri.getPathSegments().get(1));
			break;

		case LIVE_FOLDER_NOTES:
			qb.setProjectionMap(sLiveFolderProjectionMap);
			break;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		// If no sort order is specified use the default
		String orderBy;
		if (TextUtils.isEmpty(sortOrder)) {
			orderBy = AbstractNote.DEFAULT_SORT_ORDER;
		} else {
			orderBy = sortOrder;
		}

		Cursor c = qb.query(db, projection, selection, selectionArgs, null,
				null, orderBy);

		// Tell the cursor what uri to watch, so it knows when its source data
		// changes
		c.setNotificationUri(getContext().getContentResolver(), uri);
		return c;
	}

	@Override
	public String getType(Uri uri) {
		switch (sUriMatcher.match(uri)) {
		case NOTES:
		case LIVE_FOLDER_NOTES:
			return AbstractNote.CONTENT_TYPE;

		case NOTE_ID:
			return AbstractNote.CONTENT_ITEM_TYPE;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}
	}

	@Override
	public Uri insert(Uri uri, ContentValues initialValues) {
		// Validate the requested uri
		if (sUriMatcher.match(uri) != NOTES) {
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		ContentValues values;
		if (initialValues != null) {
			values = new ContentValues(initialValues);
		} else {
			values = new ContentValues();
		}
		
		if (values.containsKey(AbstractNote.GRADE) == false) {
			Resources r = Resources.getSystem();
			values.put(AbstractNote.GRADE, r
					.getString(android.R.string.untitled));
		}

		if (values.containsKey(AbstractNote.EASINESS) == false) {
			values.put(AbstractNote.EASINESS, 1.0);
		}

		if (values.containsKey(AbstractNote.ACQ_REPS) == false) {
			values.put(AbstractNote.ACQ_REPS, 0);
		}

		if (values.containsKey(AbstractNote.RET_REPS) == false) {
			values.put(AbstractNote.RET_REPS, 0);
		}

		if (values.containsKey(AbstractNote.ACQ_REPS_SINCE_LAPSE) == false) {
			values.put(AbstractNote.ACQ_REPS_SINCE_LAPSE, 0);
		}

		if (values.containsKey(AbstractNote.RET_REPS_SINCE_LAPSE) == false) {
			values.put(AbstractNote.RET_REPS_SINCE_LAPSE, 0);
		}

		if (values.containsKey(AbstractNote.LAPSES) == false) {
			values.put(AbstractNote.LAPSES, 0);
		}

		if (values.containsKey(AbstractNote.LAST_REP) == false) {
			values.put(AbstractNote.LAST_REP, 0);
		}

		if (values.containsKey(AbstractNote.NEXT_REP) == false) {
			values.put(AbstractNote.NEXT_REP, 0);
		}

		if (values.containsKey(AbstractNote.UNSEEN) == false) {
			values.put(AbstractNote.UNSEEN, true);
		}

		if (values.containsKey(AbstractNote.MARKED) == false) {
			values.put(AbstractNote.MARKED, false);
		}

		SQLiteDatabase db = getOpenHelper().getWritableDatabase();
		long rowId = db.insert(NOTES_TABLE_NAME, AbstractNote.QUESTION, values);
		if (rowId > 0) {
			Uri noteUri = ContentUris.withAppendedId(AbstractNote.CONTENT_URI,
					rowId);
			getContext().getContentResolver().notifyChange(noteUri, null);
			return noteUri;
		}

		throw new SQLException("Failed to insert row into " + uri);
	}

	@Override
	public int delete(Uri uri, String where, String[] whereArgs) {
		SQLiteDatabase db = getOpenHelper().getWritableDatabase();
		int count;
		switch (sUriMatcher.match(uri)) {
		case NOTES:
			count = db.delete(NOTES_TABLE_NAME, where, whereArgs);
			break;

		case NOTE_ID:
			String noteId = uri.getPathSegments().get(1);
			count = db.delete(NOTES_TABLE_NAME,
					Note._ID
							+ "="
							+ noteId
							+ (!TextUtils.isEmpty(where) ? " AND (" + where
									+ ')' : ""), whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	@Override
	public int update(Uri uri, ContentValues values, String where,
			String[] whereArgs) {
		SQLiteDatabase db = getOpenHelper().getWritableDatabase();
		int count;
		switch (sUriMatcher.match(uri)) {
		case NOTES:
			count = db.update(NOTES_TABLE_NAME, values, where, whereArgs);
			break;

		case NOTE_ID:
			String noteId = uri.getPathSegments().get(1);
			count = db.update(NOTES_TABLE_NAME, values,
					Note._ID
							+ "="
							+ noteId
							+ (!TextUtils.isEmpty(where) ? " AND (" + where
									+ ')' : ""), whereArgs);
			break;

		default:
			throw new IllegalArgumentException("Unknown URI " + uri);
		}

		getContext().getContentResolver().notifyChange(uri, null);
		return count;
	}

	static {
		sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		sUriMatcher.addURI(AUTHORITY, "notes", NOTES);
		sUriMatcher.addURI(AUTHORITY, "notes/#", NOTE_ID);
		sUriMatcher.addURI(AUTHORITY, "live_folders/notes", LIVE_FOLDER_NOTES);

		sMDProjectionMap = new HashMap<String, String>();
		sMDProjectionMap.put(Note._ID, Note._ID);
		sMDProjectionMap.put(AbstractNote.GRADE, AbstractNote.GRADE);
		sMDProjectionMap.put(AbstractNote.QUESTION, AbstractNote.QUESTION);
		sMDProjectionMap.put(AbstractNote.ANSWER, AbstractNote.ANSWER);
		sMDProjectionMap.put(AbstractNote.CATEGORY, AbstractNote.CATEGORY);
		sMDProjectionMap.put(AbstractNote.UNSEEN, AbstractNote.UNSEEN);
		sMDProjectionMap.put(AbstractNote.MARKED, AbstractNote.MARKED);
		sMDProjectionMap.put(AbstractNote.EASINESS, AbstractNote.EASINESS);
		sMDProjectionMap.put(AbstractNote.ACQ_REPS, AbstractNote.ACQ_REPS);
		sMDProjectionMap.put(AbstractNote.RET_REPS, AbstractNote.RET_REPS);
		sMDProjectionMap.put(AbstractNote.ACQ_REPS_SINCE_LAPSE,
				AbstractNote.ACQ_REPS_SINCE_LAPSE);
		sMDProjectionMap.put(AbstractNote.RET_REPS_SINCE_LAPSE,
				AbstractNote.RET_REPS_SINCE_LAPSE);
		sMDProjectionMap.put(AbstractNote.LAPSES, AbstractNote.LAPSES);
		sMDProjectionMap.put(AbstractNote.LAST_REP, AbstractNote.LAST_REP);
		sMDProjectionMap.put(AbstractNote.NEXT_REP, AbstractNote.NEXT_REP);
		sMDProjectionMap.put(PRIORITY, AbstractNote.PRIORITY);

		// Support for Live Folders.
		sLiveFolderProjectionMap = new HashMap<String, String>();
		sLiveFolderProjectionMap.put(LiveFolders._ID, Note._ID + " AS "
				+ LiveFolders._ID);
		sLiveFolderProjectionMap.put(LiveFolders.NAME, AbstractNote.GRADE
				+ " AS " + LiveFolders.NAME);
		// Add more columns here for more robust Live Folders.
	}

}
