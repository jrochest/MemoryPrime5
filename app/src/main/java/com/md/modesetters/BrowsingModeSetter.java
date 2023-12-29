package com.md.modesetters;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.md.AudioPlayer;
import com.md.CreateModeData;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.NoteEditorListener;
import com.md.R;
import com.md.provider.Note;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class BrowsingModeSetter extends ModeSetter implements
		ItemDeletedHandler {

	private static BrowsingModeSetter instance = null;

	protected BrowsingModeSetter() {

	}

	public static BrowsingModeSetter getInstance() {
		if (instance == null) {
			instance = new BrowsingModeSetter();
		}
		return instance;
	}

	/**
	 * @param memoryDroid
	 * @param modeHand
	 */
	public void setup(Activity memoryDroid,
			ModeHandler modeHand) {
		parentSetup(memoryDroid, modeHand);

	}

	public void onSwitchToMode(@NotNull final Activity context) {

		commonSetup(context, R.layout.browsing);
		setupBrowsing(context);
	}

	private void setupBrowsing(final Activity memoryDroid) {

		final Activity context = memoryDroid;
		final DbNoteEditor noteEditor = DbNoteEditor.getInstance();

		noteEditor.setMarkedMode(false);

		noteEditor.addListener(new NoteEditorListener() {
			@Override
			public void onNoteUpdate(Note note) {
				final ToggleButton markButton = (ToggleButton) memoryDroid
						.findViewById(R.id.markButton);
				final TextView findItem = (TextView) memoryDroid
						.findViewById(R.id.qAStats);

				if (note != null) {
					markButton.setChecked(note.isMarked());
					findItem.setText(note.toString());
				} else {
					markButton.setChecked(false);

					if (noteEditor.isMarkedMode()) {
						findItem.setText("No marked notes!");
					} else {
						findItem.setText("No notes!");
					}
				}
			}
		});

		noteEditor.getFirst();

		Button nextButton = (Button) memoryDroid.findViewById(R.id.nextButton);

		nextButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getNext();
			}
		});

		Button nextButton10 = (Button) memoryDroid
				.findViewById(R.id.nextButton10);

		nextButton10.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getNext(9);
			}
		});

		Button nextButton100 = (Button) memoryDroid
				.findViewById(R.id.nextButton100);

		nextButton100.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getNext(99);
			}
		});

		Button lastButton = (Button) memoryDroid.findViewById(R.id.lastButton);

		lastButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getLast();
			}
		});

		Button lastButton10 = (Button) memoryDroid
				.findViewById(R.id.lastButton10);

		lastButton10.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getLast(9);
			}
		});

		Button lastButton100 = (Button) memoryDroid
				.findViewById(R.id.lastButton100);

		lastButton100.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.getLast(99);
			}
		});

		Button updateAudio = (Button) memoryDroid
				.findViewById(R.id.updateAudio);

		updateAudio.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Note note = noteEditor.getNote();

				if (note != null) {
					CreateModeData.getInstance().setAudioFile(note.getAnswer(),
							CreateModeSetter.ANSWER_INDEX);

					CreateModeData.getInstance()
							.setAudioFile(note.getQuestion(),
									CreateModeSetter.QUESTION_INDEX);

					CreateModeSetter.INSTANCE.setNote(note);
					CreateModeSetter.INSTANCE.switchMode(memoryDroid);

				}
			}
		});

		Button deleteButton = (Button) memoryDroid
				.findViewById(R.id.deleteButton);

		deleteButton.setOnClickListener(new DeleterOnClickListener(noteEditor,
				context, this));

		Button playAnswerButton = (Button) memoryDroid
				.findViewById(R.id.playAnswerButton);
		playAnswerButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Note note = noteEditor.getNote();
				if (note != null) {
					AudioPlayer.getInstance().playFile(note.getAnswer(), null, true);
				}
			}
		});

		Button playQuestionButton = memoryDroid
				.findViewById(R.id.playQuestionButton);
		playQuestionButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Note note = noteEditor.getNote();

				if (note != null) {
					AudioPlayer.getInstance().playFile(
							note.getQuestion(), null, true);
				}
			}
		});

		final ToggleButton markButton = (ToggleButton) memoryDroid
				.findViewById(R.id.markButton);

		markButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				Note note = noteEditor.getNote();
				if (note != null) {
					// swap the value.
					note.setMarked(!note.isMarked());
					noteEditor.update(note);

					dealWithMarkedModeChange(DbNoteEditor.getInstance());
				} else {
					noteEditor.setNullNote();
				}
			}
		});
		Button toggleMarked = (Button) memoryDroid
				.findViewById(R.id.toggleMarked);

		toggleMarked.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				noteEditor.setMarkedMode(!noteEditor.isMarkedMode());
				dealWithMarkedModeChange(noteEditor);
			}

		});
	}

	private void dealWithMarkedModeChange(final DbNoteEditor noteEditor) {
		Note note = noteEditor.getNote();
		if (noteEditor.isMarkedMode() && note != null) {
			// Can note show that one in this mode.
			if (!note.isMarked()) {
				note = noteEditor.getNext();
				if (note == null) {
					note = noteEditor.getLast();

					if (note == null) {
						noteEditor.setNullNote();
					}
				}
			}
		} else if (note == null) {
			noteEditor.getFirst();
			// TODO make this a callback.
			note = noteEditor.getNote();
		}
	}

	@Override
	public void deleteNote() {
		final DbNoteEditor noteEditor = DbNoteEditor.getInstance();
		
		Note note = noteEditor.getNote();
		if (note != null) {
			noteEditor.deleteCurrent(mActivity, note);
		}

	}

}