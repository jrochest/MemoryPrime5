package com.md;

import android.media.MediaRecorder;

import com.md.modesetters.TtsSpeaker;
import com.md.utils.ToastSingleton;

import java.io.File;
import java.io.IOException;
import java.util.Random;


public class AudioRecorder {
	public static final String sessionSuffixTwoDigitNumber = createSuffix();
	private static final String sessionSuffixTwoDigitNumberWithExtension = sessionSuffixTwoDigitNumber + ".m4a";

	private static String createSuffix() {
		Random random = new Random();

		// String with two digit ints between 00 and 99.
		return random.nextInt(10) + "" + random.nextInt(10);
	}

	private MediaRecorder recorder = null;
	private final String path;
	private final String originalFile;
	boolean recorded = false;

	public String getOriginalFile() {
		return originalFile;
	}

	/**
	 * Creates a new audio recording at the given path (relative to root of SD card).
	 */
	public AudioRecorder(String path) {
		originalFile = path;
		this.path = AudioPlayer.sanitizePath(path);
	}

	/** AudioRecorder that generates its own new from current time and suffix */
	public AudioRecorder() {
		// Force the last two digits of the time to be the same to always write to the same dir.
		// To allow backups to exhibit some temporal locality and decrease the number of directory
		// specific zips that need to be updated.
		this((System.currentTimeMillis() / 100) + sessionSuffixTwoDigitNumberWithExtension);
	}

	public void deleteFile() {
		if (recorded) {
			recorded = false;
			File file = new File(this.path);
			file.delete();
		}
	}

	public static boolean deleteFile(String fileName) {
		String path = AudioPlayer.sanitizePath(fileName);
		File file = new File(path);
		return file.delete();
	}

	/**
	 * Stops a recording that has been previously started.
	 */
	public void stop() throws IOException {
		try {
			recorder.stop();
		} catch (Exception e) {
			System.out.print("Error during stop and release" + e);
		} finally {
			try {
				recorder.release();
			} catch (Exception e) {
				System.out.print("Removed in finally" + e);
			}
		}

		File audioFileExists = new File(this.path);
		if (!audioFileExists.exists()) {
			ToastSingleton.getInstance().error(this.path + " does not exist!");
		} else if (audioFileExists.length() < 4_000) {
			audioFileExists.delete();
			throw new RecordingTooSmallException();
		} else {
			recorded = true;
		}
	}

	/**
	 * Starts a new recording.
	 */
	public void start() throws IOException {
		start(false);
	}

	/**
	 * Starts a new recording.
	 */
	public void start(boolean isRetry) throws IOException {
		// Try write without checking if dir exists. Correct thing upon error.
		try {
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
			recorder.setAudioChannels(1);
			recorder.setAudioEncodingBitRate(128000);
			recorder.setAudioSamplingRate(44100);

			recorder.setOutputFile(AudioPlayer.transformToM4a(this.path));
			recorder.prepare();
			recorder.start();

		} catch (Exception e) {
			String state = android.os.Environment.getExternalStorageState();
			if (!state.equals(android.os.Environment.MEDIA_MOUNTED)) {
				TtsSpeaker.error("SD Card is not mounted.  It is " + state);
			}

			// make sure the directory we plan to store the recording in exists
			File directory = new File(AudioPlayer.transformToM4a(this.path)).getParentFile();
			if (!directory.exists() && !directory.mkdirs()) {
				TtsSpeaker.error("Path to file could not be created.");
			}

			if (!isRetry) {
				// Retry once after directory creation.
				start(true);
			}
		}

	}

	public void playFile() {
		final AudioPlayer audioPlayer = AudioPlayer.getInstance();
		audioPlayer.playFile(originalFile);
	}

	public boolean isRecorded() {
		return recorded;
	}
}
