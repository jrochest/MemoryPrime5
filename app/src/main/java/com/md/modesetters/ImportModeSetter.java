package com.md.modesetters;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.AsyncTask;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.md.CategorySingleton;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.R;
import com.md.provider.MemoreasyType;
import com.md.utils.ToastSingleton;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ImportModeSetter extends ModeSetter implements
		android.os.Handler.Callback {
	/**
	 * 
	 */
	private final Activity memoryDroid;
	private TextView findItem;

	/**
	 * @param memoryDroid
	 */
	public ImportModeSetter(Activity memoryDroid,
			ModeHandler modeHand) {

		parentSetup(memoryDroid, modeHand);
		this.memoryDroid = memoryDroid;
	}

	public void onSwitchToMode(@NotNull final Activity context) {

		commonSetup(context, R.layout.debug);
		setupBrowsing(context);

	}

	private void setupBrowsing(final Activity memoryDroid) {

		findItem = (TextView) memoryDroid.findViewById(R.id.debugText);

		findItem.setText("Hell yeah");
		final ImportModeSetter debugModeSetter = this;

		Button insertButton = (Button) memoryDroid.findViewById(R.id.runIt);

		insertButton.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				new TestInserter(debugModeSetter).execute(debugModeSetter);
			}
		});
	}

	public class TestInserter extends
			AsyncTask<ImportModeSetter, String, String> {
		private ImportModeSetter debugModeSetter;

		public TestInserter(ImportModeSetter debugModeSetter2) {
			// TODO Auto-generated constructor stub
		}

		protected String doInBackground(ImportModeSetter... debugModeSetter) {

			this.debugModeSetter = debugModeSetter[0];

			this.debugModeSetter.testInsertLots(this);
			return "rock2";
		}

		public void publishProgessVisible(String state) {
			publishProgress(state);
		}

		protected void onProgressUpdate(String... state) {
			debugModeSetter.setState(state[0]);
		}

	}

	public void testInsertLots(TestInserter testInserter) {

		testInserter.publishProgessVisible("day");

		// try {
		// copyDataBase(memoryDroid);
		// } catch (IOException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }

		DataBaseHelper myDbHelper = new DataBaseHelper(memoryDroid);

		try {

			myDbHelper.createDataBase();

		} catch (IOException ioe) {

			throw new Error("Unable to create database");

		}

		try {

			myDbHelper.openDataBase();

			myDbHelper.addSomething(testInserter);

		} catch (SQLException sqle) {

			throw sqle;

		}

	}

	public void setState(String string) {
		findItem.setText(string);
	}

	@Override
	public boolean handleMessage(Message msg) {
		// TODO Auto-generated method stub
		return false;
	}

	class DataBaseHelper extends SQLiteOpenHelper {

		public void addSomething(TestInserter testInserter) {

			DbNoteEditor.getInstance().debugDeleteAll();

			String filename = DB_PATH + DB_NAME;

			File file = new File(filename);

			long length = file.length();
			;

			System.out.println("length of the file is: " + length);

			// testInserter.publishProgessVisible("created db");

			SQLiteDatabase db = getReadableDatabase();

			String where = "_id < 503 AND _id > 500";

			where = null;

			Cursor query = db.query("items", null, where, null, null, null,
					null);

			ProcessMemoryEasyNote processMemoryEasyNote = new ProcessMemoryEasyNote(
					memoryDroid);

			String keyword = "";
			while (query.moveToNext()) {
				keyword += "\n";

				int _id = query.getInt(query.getColumnIndex("_id"));

				String qfile = query.getString(query.getColumnIndex("qfile"));
				String afile = query.getString(query.getColumnIndex("afile"));

				String baseID = query.getString(query.getColumnIndex("baseID"));

				int passes = query.getInt(query.getColumnIndex("passes"));

				int fails = query.getInt(query.getColumnIndex("fails"));

				double lastTesting = query.getDouble(query
						.getColumnIndex("lastTesting"));

				int points = query.getInt(query.getColumnIndex("points"));

				MemoreasyType memoreasyType = new MemoreasyType(_id, qfile,
						afile, baseID, passes, fails, lastTesting, points);

				// testInserter.publishProgessVisible("Processing: " +
				// memoreasyType.toString());

				processMemoryEasyNote.processNew(memoreasyType, testInserter);

				try {
					Thread.sleep(1);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			CategorySingleton.getInstance().setDaysSinceStart(
					CategorySingleton.getTodayInDaysSinceStart());
		}

		private SQLiteDatabase myDataBase;

		/**
		 * Constructor Takes and keeps a reference of the passed mActivity in
		 * order to access to the application assets and resources.
		 * 
		 * @param context
		 */
		public DataBaseHelper(Context context) {

			super(context, DB_PATH + DB_NAME, null, 1);
		}

		/**
		 * Creates a empty database on the system and rewrites it with your own
		 * database.
		 * */
		public void createDataBase() throws IOException {

			boolean dbExist = checkDataBase();

			if (dbExist) {
				// do nothing - database already exist
			} else {

				// By calling this method and empty database will be created
				// into the default system path
				// of your application so we are gonna be able to overwrite that
				// database with our database.
				this.getReadableDatabase();

				try {

					copyDataBase(memoryDroid);

				} catch (IOException e) {

					throw new Error("Error copying database");

				}
			}

		}

		/**
		 * Check if the database already exist to avoid re-copying the file each
		 * time you open the application.
		 * 
		 * @return true if it exists, false if it doesn't
		 */
		private boolean checkDataBase() {




				String myPath = DB_PATH + DB_NAME;
			try (SQLiteDatabase checkDB = SQLiteDatabase.openDatabase(myPath, null,
					SQLiteDatabase.OPEN_READONLY)) {
				// database does't exist yet.
				System.out.println("DbDoesn't exist");
				ToastSingleton.getInstance().error("Database does not exist yet.");
				return checkDB != null;
			}
		}

		public void openDataBase() throws SQLException {

			// Open the database
			String myPath = DB_PATH + DB_NAME;
			myDataBase = SQLiteDatabase.openDatabase(myPath, null,
					SQLiteDatabase.OPEN_READONLY);

		}

		@Override
		public synchronized void close() {

			if (myDataBase != null)
				myDataBase.close();

			super.close();

		}

		@Override
		public void onCreate(SQLiteDatabase db) {

		}

		@Override
		public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

		}

		// Add your public helper methods to access and get content from the
		// database.
		// You could return cursors by doing "return myDataBase.query(....)" so
		// it'd be easy
		// to you to create adapters for your views.

	}

	/**
	 * Copies your database from your local assets-folder to the just created
	 * empty database in the system folder, from where it can be accessed and
	 * handled. This is done by transfering bytestream.
	 * */
	public static void copyDataBase(Context myContext) throws IOException {

		// Open your local db as the input stream
		InputStream myInput = myContext.getAssets().open("base.mp3");

		// Path to the just created empty db
		String outFileName = DB_PATH + DB_NAME;

		OutputStream myOutput = null;
		try {
			// Open the empty db as the output stream
			myOutput = new FileOutputStream(outFileName);
		} catch (FileNotFoundException ex) {
			System.out.println(ex.getMessage());
			return;

		}

		// transfer bytes from the inputfile to the outputfile
		byte[] buffer = new byte[1024];
		int length;

		int totalSize = 0;
		while ((length = myInput.read(buffer)) > 0) {
			myOutput.write(buffer, 0, length);
			totalSize += length;
		}

		System.out.println("totalSize: " + totalSize);

		// Close the streams
		myOutput.flush();
		myOutput.close();
		myInput.close();

	}

	// The Android's default system path of your application database.
	private static final String DB_PATH = "/sdcard/";

	private static final String DB_NAME = "items4.db";

}