package com.md;

import android.content.Context;
import android.os.Environment;

public abstract class DbContants {

	public static final String DATABASE_NAME = "memory_droid.db";

	public static String getDatabasePath(Context context) {
		setup(context);
		return FULL_PATH;
	}

	public static String getDatabasePath() {
		return FULL_PATH;	
	}

	public static String getAudioLocation() {
		return AUDIO_LOCATION;	
	}
	
	public static String getDataLocation() {
		return DATA_LOCATION;
	}
	
	private static String FULL_PATH;
	private static String AUDIO_LOCATION;
	private static String DATA_LOCATION;

	public static void setup(Context context) {
		if (DATA_LOCATION == null) {
			DATA_LOCATION = context.getFilesDir() + "/com.md.MemoryPrime/";
		}
		if (FULL_PATH == null) {
			FULL_PATH = DATA_LOCATION + DATABASE_NAME;
		}
		if (AUDIO_LOCATION == null) {
			AUDIO_LOCATION = DATA_LOCATION + "AudioMemo/";
		}
	}
}
