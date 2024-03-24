package com.md.modesetters;

import com.md.AudioPlayer;
import com.md.DbContants;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.NotesProvider;
import com.md.R;
import com.md.provider.AbstractNote;

import android.app.Activity;
import android.database.Cursor;
import android.os.AsyncTask;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;

// Keep this.
public class CleanUpAudioFilesModeSetter extends ModeSetter {

	private TextView mStatusMessage;

	private TextView mMissingFilesMessage;

	private static CleanUpAudioFilesModeSetter sInstance = null;
	private ArrayList<String> mGoodFiles;
	private Button mRunItButton;
	private ArrayList<File> mBadFiles;
	private volatile int mMissingFileCountBefore = -1;
	private volatile int mMissingFileCountAfter = -1;

	public static CleanUpAudioFilesModeSetter getInstance() {
		if (sInstance == null) {
			sInstance = new CleanUpAudioFilesModeSetter();
		}
		return sInstance;
	}

	public void onSwitchToMode(@NotNull final Activity activity) {
		commonSetup(activity, R.layout.clean_up_audio_files);
		setupBrowsing(activity);
	}

	public void setup(Activity memoryDroid, ModeHandler modeHand) {
		parentSetup(memoryDroid, modeHand);
	}

	private void setupBrowsing(final Activity activity) {

		mStatusMessage = (TextView) activity.findViewById(R.id.current_status);
		mMissingFilesMessage = (TextView) activity.findViewById(R.id.missing_files);

		mStatusMessage.setText("Clean up audio files");
		final CleanUpAudioFilesModeSetter debugModeSetter = this;

		mRunItButton = (Button) activity.findViewById(R.id.runIt);

		mRunItButton.setVisibility(View.VISIBLE);
		mRunItButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				mRunItButton.setVisibility(View.INVISIBLE);
				new FindGoodFiles().execute();
			}
		});
	}

	public void setState(String string) {
		mStatusMessage.setText(string);
		mMissingFilesMessage.setText("Missing files before: " + mMissingFileCountBefore
				+ " missing after: " + mMissingFileCountAfter);
	}

	public class FindGoodFiles extends
			AsyncTask<Void, String, ArrayList<String>> {

		protected ArrayList<String> doInBackground(Void... params) {
			publishProgress("Looking for good files...");

			final DbNoteEditor noteEditor = DbNoteEditor.getInstance();

			String queryString = "SELECT " + AbstractNote.QUESTION
					+ ", " + AbstractNote.ANSWER + " FROM "
					+ NotesProvider.NOTES_TABLE_NAME;
			final ArrayList<String> audioFiles = new ArrayList<>();
			DbNoteEditor.DatabaseResult query = noteEditor.rawQuery(queryString);
			if (query == null) {
				return audioFiles;
			}

			while (query.getCursor().moveToNext()) {
				audioFiles.add(query.getCursor().getString(0));
				audioFiles.add(query.getCursor().getString(1));
			}

			query.getCursor().close();
			query.getDatabase().close();

			return audioFiles;
		}

		protected void onProgressUpdate(String... state) {
			setState(state[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<String> goodFiles) {
			super.onPostExecute(goodFiles);
			mGoodFiles = goodFiles;
			((TextView) mActivity.findViewById(R.id.good_files)).setText(
					"Good files: " + mGoodFiles.size());

			mRunItButton.setVisibility(View.VISIBLE);
			mRunItButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					mRunItButton.setVisibility(View.INVISIBLE);
					new FindBadFiles().execute();
				}
			});
		}
	}

	private int countValidGoodFiles() {
		// Call on background thread only!!!
		int missingFileCount = 0;
		for (String goodFile : mGoodFiles) {
			if (goodFile == null) {
				continue;
			}
			final String filePathString = AudioPlayer.sanitizePath(goodFile);
			File f = new File(filePathString);
			if(!f.exists()) {
				System.out.println("Missing file " + filePathString);
				missingFileCount++;
			}
		}
		return missingFileCount;
	}


	public class FindBadFiles extends AsyncTask<Void, String, ArrayList<File>> {

		private int mDirsSearched = 0;

		private int mFilesSearched = 0;

		protected ArrayList<File> doInBackground(Void... params) {
			publishProgress("Counting missing files...");
			mMissingFileCountBefore = countValidGoodFiles();
			publishProgress("Looking for all files...");

			final File audioMemoDir = new File(DbContants.getAudioLocation());
			final ArrayList<File> allFiles = getListFiles(audioMemoDir);


			// TODO(jrochest) Make a hashset of the all files. Sanitize the goods name and remove the
			// matches from the bad all files. We might have to convert uses of files to Strings in
			// the all files list.

			final ArrayList<File> badFiles = new ArrayList<>();
			final int allFilesNumber = allFiles.size();
			for (File file : allFiles) {
				mFilesSearched++;
				maybeAddToBadFiles(file, badFiles);
				if (mFilesSearched % 100 == 0) {
					publishProgress("Checked " + mFilesSearched + " of " + allFilesNumber);
				}
			}
			return badFiles;
		}

		private void maybeAddToBadFiles(File file, ArrayList<File> badFiles) {

			final String fileName = file.getName();
			for (String goodFile : mGoodFiles) {
				if (fileName.contains(goodFile)) {
					return;
				}

				String basename = goodFile;
				if (fileName.contains(basename)) {
					System.out.println("Basename that matches. basename " + basename);
					if (!fileName.contains("(")) {
						System.out.println("Error basename missing (" + basename);
						return;
					}
				}
			}

			System.out.println("Found bad file: " + fileName);
			badFiles.add(file);
		}

		private ArrayList<File> getListFiles(File parentDir) {

			ArrayList<File> inFiles = new ArrayList<>();

			File[] files = parentDir.listFiles();
			for (File file : files) {
				if (file.isDirectory()) {
					inFiles.addAll(getListFiles(file));
				} else {
					if (file.getName().endsWith("mp4")) {
						inFiles.add(file);
					} else if (file.getName().endsWith(".bad")) {
						System.out.println("deleting .bad file " + file.getName());
					    file.delete();
					} else {
						// TODO ignore nomedia files.
						System.out.println("Found weird file " + file.getName());
					}
				}
			}
			publishProgress("Looking for all files... Searched dirs count: " + mDirsSearched++);
			return inFiles;
		}

		protected void onProgressUpdate(String... state) {
			setState(state[0]);
		}

		@Override
		protected void onPostExecute(ArrayList<File> allFiles) {
			super.onPostExecute(allFiles);
			mBadFiles = allFiles;
			((TextView) mActivity.findViewById(R.id.all_files)).setText(
					"Bad files: " + mBadFiles.size());

			mRunItButton.setVisibility(View.VISIBLE);
			mRunItButton.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					mRunItButton.setVisibility(View.INVISIBLE);
					new DeleteBadFiles().execute();
				}
			});
		}
	}

	public class DeleteBadFiles extends AsyncTask<Void, String, Void> {

		protected Void doInBackground(Void... params) {
			publishProgress("Deleting for bad files...");

			for (File file : mBadFiles) {
				// STOPSHIP just make this delete like we do below.
				System.out.println("TODOJ deleting bad file: " + file);
				if (!file.getName().contains(".bad")) {
					file.renameTo(new File(file.getPath() + ".bad"));
					System.out.println("TODOJ renaming for now " + file.getName());
				} else {
					System.out.println("TODOJ not renaming " + file.getName());
				}
				//TODO(jrochest) allow deletes file.delete();
			}

			mMissingFileCountAfter = countValidGoodFiles();

			publishProgress("Deleted bad files!!");

			return null;
		}


		protected void onProgressUpdate(String... state) {
			setState(state[0]);
		}
	}

}