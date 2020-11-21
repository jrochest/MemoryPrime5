package com.md.modesetters;

import java.io.IOException;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;

import com.md.ActivityHelper;
import com.md.AudioPlayer;
import com.md.AudioRecorder;
import com.md.DbNoteEditor;
import com.md.SpacedRepeaterActivity;
import com.md.provider.Note;

public class RecordOnClickListener implements OnTouchListener {

	private AudioRecorder audioRecorder = null;
	private final Activity context;
	private final Note mNote;
	private final boolean isAnswer;
	private final Note lastNote;

	public RecordOnClickListener(Note note, Activity context, boolean isAnswer,
			Note lastNote) {
		mNote = note;
		this.context = context;
		this.isAnswer = isAnswer;
		if (mNote != null && lastNote != null && lastNote.getId() == mNote.getId()) {
            this.lastNote = lastNote;
		} else {
			this.lastNote = null;
		}
	}

	public boolean onTouch(View v, MotionEvent event) {
		if (mNote != null) {
			try {
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN: {
					audioRecorder = new AudioRecorder(System.currentTimeMillis()+".m4a");
					audioRecorder.start();
					return true;
				}
				case MotionEvent.ACTION_UP: {
					if (audioRecorder != null) {
						audioRecorder.stop();
						if (!audioRecorder.isRecorded()) {
							return true;
						}
						AlertDialog.Builder builder = new AlertDialog.Builder(
								context);
						builder.setMessage(
								"Do you want to save this recording, and overwrite the old one?")
								.setCancelable(false)
								.setPositiveButton("Yes",
										new DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int id) {
												AudioPlayer
														.getInstance()
														.playFile(
																audioRecorder
																		.getOriginalFile(), null);

												System.err.println("TEMP: "
														+ mNote);
												// If the revision queue has one in it's history
												// replace that too.
												// We only care if it is a match.

												if (isAnswer) {
													mNote.setAnswer(audioRecorder
															.getOriginalFile());
													if (lastNote != null) {
														lastNote.setAnswer(audioRecorder
																.getOriginalFile());
													}
												} else {
													mNote.setQuestion(audioRecorder
															.getOriginalFile());
													if (lastNote != null) {
														lastNote.setQuestion(audioRecorder
																.getOriginalFile());
													}
												}

												DbNoteEditor.getInstance().update(context, mNote);
											}
										})
								.setNegativeButton("No",
										new DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int id) {

											}
										});
						AlertDialog alert = builder.create();
						alert.show();
					}
					return true;
				}
				default:
					return false;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return false;
	}
}
