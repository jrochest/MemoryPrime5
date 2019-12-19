package com.md;

import android.os.Environment;

public abstract class DbContants {

	public static final String DATABASE_NAME = "memory_droid.db";

	public static String getFullPath() {
		if (FULL_PATH == null) {
		   FULL_PATH = getDataLocation() + DATABASE_NAME;
		}
		return FULL_PATH;	
	}

	public static String getAudioLocation() {
		if (AUDIO_LOCATION == null) {
			AUDIO_LOCATION = getDataLocation() + "AudioMemo/";
		}
		return AUDIO_LOCATION;	
	}
	
	public static String getDataLocation() {
		
		return Environment
		.getExternalStorageDirectory().getAbsolutePath()
		+ "/com.md.MemoryPrime/";
	}
	
	private static String FULL_PATH;
	private static String AUDIO_LOCATION;

}
