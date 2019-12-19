package com.md;

import java.io.File;
import java.io.IOException;

import android.app.Activity;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.md.modesetters.CreateModeSetter;
import com.md.utils.ToastSingleton;

public class AudioRecorder {

	private MediaRecorder recorder = null;
	private DocumentFile path;
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

	public void deleteFile() {
		if (recorded) {
			recorded = false;
			this.path.delete();
		}
	}

	public static boolean deleteFile(String fileName) {
		DocumentFile path = AudioPlayer.sanitizePath(fileName);
		return path.delete();

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

		if (!this.path.exists()) {
			ToastSingleton.getInstance().error(this.path + " does not exist!");
		} else if (this.path.length() < 4_000) {
			System.out.println("Length is: " + this.path.length());
			this.path.delete();
		} else {
			recorded = true;
		}
	}

	/**
	 * Starts a new recording.
	 */
	public void start() throws IOException {
		String state = android.os.Environment.getExternalStorageState();
		if (!state.equals(android.os.Environment.MEDIA_MOUNTED)) {
			throw new IOException("SD Card is not mounted.  It is " + state
					+ ".");
		}

		// make sure the directory we plan to store the recording in exists
		// TODOJ need to create parent.
		if (this.path.exists()) {
			// TODO create the directory!
			throw new IOException("Path to file could not be created.");
		}

		try {
			recorder = new MediaRecorder();
			recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
			recorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
			recorder.setAudioChannels(1);
			recorder.setAudioEncodingBitRate(128000);
			recorder.setAudioSamplingRate(44100);

			//STOPSHIPrecorder.setOutputFile(this.path.getUri());
			recorder.prepare();
			recorder.start();

		} catch (Exception e) {
			Log.d("AudioRecorder", e.getMessage());
			// TODO: handle exception
		}

	}

	public void playFile(CreateModeSetter createModeSetter, int currentIndex, Activity context) {
		final AudioPlayer audioPlayer = AudioPlayer.getInstance();

		OnCompletionListener listener = new MyOnCompletionListener(
				createModeSetter, currentIndex);

        audioPlayer.playFile(originalFile, listener, context);
	}

	public boolean isRecorded() {
		return recorded;
	}
}
