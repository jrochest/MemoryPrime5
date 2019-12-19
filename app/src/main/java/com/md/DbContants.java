package com.md;

import android.content.Context;
import android.os.Environment;

import androidx.documentfile.provider.DocumentFile;

public abstract class DbContants {

	public static final String DATABASE_NAME = "memory_droid.db";

	static DocumentFile storagePath = null;
	static String databasePath = null;

	public static String getDatabasePath(Context context) {
		if (databasePath == null) {
			databasePath = context.getFilesDir() + "/" + DATABASE_NAME;
		}
		return databasePath;
	}

	public static DocumentFile getAudioLocation() {
		if (AUDIO_LOCATION == null) {
			AUDIO_LOCATION = DbContants.storagePath.findFile("AudioMemo");
		}
		return AUDIO_LOCATION;
	}
	
	public static String getDataLocation() {
		return Environment
		.getExternalStorageDirectory().getAbsolutePath()
		+ "/com.md.MemoryPrime/";
	}

	private static DocumentFile AUDIO_LOCATION;
}
