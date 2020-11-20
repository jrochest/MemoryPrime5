package com.md.modesetters;

import java.io.IOException;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;

import com.md.AudioPlayer;
import com.md.AudioRecorder;
import com.md.CreateModeData;
import com.md.DbNoteEditor;
import com.md.ModeHandler;
import com.md.R;
import com.md.RevisionQueue;
import com.md.CreateModeData.State;
import com.md.provider.Note;

public class CreateModeSetter extends ModeSetter {

	private static CreateModeSetter instance = null;

	protected CreateModeSetter() {

	}

	public static CreateModeSetter getInstance() {
		if (instance == null) {
			instance = new CreateModeSetter();
		}
		return instance;
	}

	private AudioRecorder question = null;

	private AudioRecorder answer = null;

	/**
	 * @param memoryDroid
	 * @param modeHand
	 */
	public void setUp(Activity memoryDroid,
			ModeHandler modeHand) {
		parentSetup(memoryDroid, modeHand);
	}

	public void setupModeImpl(final Activity context) {
		commonSetup(context, R.layout.creation);
		setupCreateMode(context);

	}

	public final static int QUESTION_INDEX = 0;
	public final static int ANSWER_INDEX = 1;

	private static Button recordQAButton[] = new Button[ANSWER_INDEX + 1];
	private static Button playQAButton[] = new Button[ANSWER_INDEX + 1];
	private static Button saveButton;
	private static Button resetButton;
	private static Drawable stopImage;
	private static Drawable recordImage;
	private static Drawable recordImageGrey;
	private static Drawable playImage;
	private static Drawable playImageGrey;
	private static Drawable saveImage;
	private static Drawable saveImageGrey;
	private static Drawable refreshImage;
	private static Drawable refreshImageGrey;

	void setupCreateMode(final Activity memoryDroid) {

		Resources res = (memoryDroid).getResources();
		stopImage = res.getDrawable(R.drawable.stop);
		recordImage = res.getDrawable(R.drawable.record);

		recordImageGrey = res.getDrawable(R.drawable.greyrecord);

		playImage = res.getDrawable(R.drawable.play);
		playImageGrey = res.getDrawable(R.drawable.greyplay);
		saveImage = res.getDrawable(R.drawable.save);
		saveImageGrey = res.getDrawable(R.drawable.greysave);
		refreshImage = res.getDrawable(R.drawable.refresh);
		refreshImageGrey = res.getDrawable(R.drawable.greyrefresh);

		recordQAButton[QUESTION_INDEX] = (Button) memoryDroid
				.findViewById(R.id.recordQuestionButton);
		recordQAButton[ANSWER_INDEX] = (Button) memoryDroid
				.findViewById(R.id.recordAnswerButton);
		playQAButton[ANSWER_INDEX] = (Button) memoryDroid
				.findViewById(R.id.playAnswerButton);
		playQAButton[QUESTION_INDEX] = (Button) memoryDroid
				.findViewById(R.id.playQuestionButton);
		saveButton = (Button) memoryDroid.findViewById(R.id.saveQAButton);
		resetButton = (Button) memoryDroid.findViewById(R.id.restartQaButton);

		setupRecordButton(memoryDroid, QUESTION_INDEX);
		setupRecordButton(memoryDroid, ANSWER_INDEX);
		setupPlayButton(memoryDroid, ANSWER_INDEX);
		setupPlayButton(memoryDroid, QUESTION_INDEX);
		setupSaveButton(memoryDroid, saveButton);
		setupResetButton(memoryDroid, resetButton);

		updateState();

	}

	public void restartCreateMode(boolean deleteLastMp3s) {

		// We're updating an existing note, no restarts allowed.
		if (note == null) {
			CreateModeData createMode = CreateModeData.getInstance();
			createMode.clearState();

			if (answer != null) {
				if (deleteLastMp3s) {
					answer.deleteFile();
				}
				answer = null;
			}
			if (question != null) {
				if (deleteLastMp3s) {
					question.deleteFile();
				}
				question = null;
			}
		}
	}

	public void updateState() {
		CreateModeData createMode = CreateModeData.getInstance();

		if (qaUpdateState(createMode, QUESTION_INDEX)) {
			return;
		}
		if (qaUpdateState(createMode, ANSWER_INDEX)) {
			return;
		}

		// Update mode
		if (note != null) {
			saveButton.setText("Go Back");
		} else {
			saveButton.setText("Save Note");
		}

		// Save button
		if (createMode.getQuestionState(ANSWER_INDEX) == CreateModeData.State.RECORDED
				&& createMode.getQuestionState(QUESTION_INDEX) == CreateModeData.State.RECORDED) {
			enableSave();
		} else {
			disableSave();
		}

		// If one of them things has been recorded and we are not in
		// note update mode.
		if ((createMode.getQuestionState(ANSWER_INDEX) != CreateModeData.State.BLANK || createMode
				.getQuestionState(QUESTION_INDEX) != CreateModeData.State.BLANK)
				&& note == null) {
			resetEnable();
		} else {
			resetDisable();
		}

	}

	private boolean qaUpdateState(CreateModeData createMode, int currentIndex) {
		if (createMode.getQuestionState(currentIndex) == CreateModeData.State.PLAYING) {
			disableAllOthers();
			qPlayEnableReadyToStop(currentIndex);
			return true;
		} else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.BLANK) {
			qaPlayDisable(currentIndex);
			qRecorderEnableRecord(currentIndex);

		} else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.RECORDED) {
			qaPlayEnableReadyToPlay(currentIndex);
			qRecorderEnableRecordAgain(currentIndex);
		} else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.RECORDING) {
			disableAllOthers();
			qRecorderEnableRecording(currentIndex);
			return true;
		}
		return false;
	}

	private void resetEnable() {
		resetButton.setEnabled(true);
		resetButton.setCompoundDrawablesWithIntrinsicBounds(refreshImage, null,
				null, null);
	}

	private void resetDisable() {
		resetButton.setEnabled(false);
		resetButton.setCompoundDrawablesWithIntrinsicBounds(refreshImageGrey,
				null, null, null);
	}

	private void disableAllOthers() {
		qRecorderDisable(QUESTION_INDEX);
		qaPlayDisable(QUESTION_INDEX);
		qRecorderDisable(ANSWER_INDEX);
		qaPlayDisable(ANSWER_INDEX);
		disableSave();
		resetDisable();
	}

	private void disableSave() {
		saveButton.setEnabled(false);
		saveButton.setCompoundDrawablesWithIntrinsicBounds(saveImageGrey, null,
				null, null);
	}

	private void enableSave() {
		saveButton.setEnabled(true);
		saveButton.setCompoundDrawablesWithIntrinsicBounds(saveImage, null,
				null, null);
	}

	private static final String TAG = "CreateModeSetter";

	private void setupSaveButton(final Activity memoryDroid,
			final Button saveButton) {

		saveButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Create mode
				if (note == null) {
					DbNoteEditor noteEditor = DbNoteEditor.getInstance();

					Note note = new Note(CreateModeData.getInstance()
							.getQuestion(CreateModeSetter.QUESTION_INDEX),
							CreateModeData.getInstance().getQuestion(
									CreateModeSetter.ANSWER_INDEX));

					Log.v(TAG, "Writing node with ID  " + note.isUnseen());

					note = (Note) noteEditor.insert(memoryDroid, note);

					// Add new note.
					RevisionQueue.getCurrentDeckReviewQueue().add(note);

					Log.v(TAG, "Wrote node with ID  " + note.isUnseen());

					restartCreateMode(false);
					updateState();
				} else // Update mode.
				{
					// So what we restart back to the initial state.
					note = null;
					BrowsingModeSetter.getInstance().setupMode(memoryDroid);

				}
			}
		});
	}

	private void setupResetButton(final Activity memoryDroid,
			final Button resetButton) {

		resetButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				restartCreateMode(true);
				updateState();
			}
		});
	}

	private void setupPlayButton(final Activity memoryDroid, final int qaIndex) {

		playQAButton[qaIndex].setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {

				CreateModeData createMode = CreateModeData.getInstance();

				moveToDonePlayingOrPlaying(qaIndex, createMode, false);
			}

		});
	}

	public void moveToDonePlayingOrPlaying(final int qaIndex,
			CreateModeData createMode, boolean callbackMakeStateRecording) {

		State questionState = createMode.getQuestionState(qaIndex);

		// We don't want to change back to playing if the media player and
		// the user both stop the playing (because it's over and a button press)
		if (questionState == CreateModeData.State.PLAYING
				|| callbackMakeStateRecording) {

			// If this was not because of a callback then we need to clean up
			// the media player.
			if (!callbackMakeStateRecording) {
				AudioPlayer.getInstance().cleanUp();
			}

			createMode.setQuestionState(CreateModeData.State.RECORDED, qaIndex);
		} else if (questionState == CreateModeData.State.RECORDED) {

			createMode.setQuestionState(CreateModeData.State.PLAYING, qaIndex);
		}
		updateState();
	}

	private String PLAY = "Play ";
	private String STOP = "Stop\nPlaying ";
	private String FIELD_NAMES[] = { "\nQuestion", "\nAnswer" };

	void qaPlayDisable(int currentIndex) {
		playQAButton[currentIndex].setEnabled(false);
		playQAButton[currentIndex].setText(PLAY + FIELD_NAMES[currentIndex]);
		playQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				playImageGrey, null, null, null);
	}

	void qaPlayEnableReadyToPlay(int currentIndex) {
		playQAButton[currentIndex].setEnabled(true);
		playQAButton[currentIndex].setText(PLAY + FIELD_NAMES[currentIndex]);
		playQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				playImage, null, null, null);
	}

	void qPlayEnableReadyToStop(int currentIndex) {
		playQAButton[currentIndex].setEnabled(true);
		playQAButton[currentIndex].setText(STOP);
		playQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				stopImage, null, null, null);

		if (currentIndex == ANSWER_INDEX) {
			answer.playFile(this, currentIndex);
		} else {
			question.playFile(this, currentIndex);
		}
	}

	final String norecording = "Record ";
	final String recording = "Stop Recording";
	final String rerecording = "Rerecord ";

	private Note note;

	public void qRecorderEnableRecord(int currentIndex) {
		recordQAButton[currentIndex].setEnabled(true);
		recordQAButton[currentIndex].setText(norecording
				+ FIELD_NAMES[currentIndex]);
		recordQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				recordImage, null, null, null);
	}

	public void qRecorderEnableRecording(int currentIndex) {
		recordQAButton[currentIndex].setEnabled(true);
		recordQAButton[currentIndex].setText(recording);
		recordQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				stopImage, null, null, null);
	}

	public void qRecorderEnableRecordAgain(int currentIndex) {
		recordQAButton[currentIndex].setEnabled(true);
		recordQAButton[currentIndex].setText(rerecording
				+ FIELD_NAMES[currentIndex]);
		recordQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				recordImage, null, null, null);
	}

	public void qRecorderDisable(int currentIndex) {
		recordQAButton[currentIndex].setEnabled(false);
		recordQAButton[currentIndex].setCompoundDrawablesWithIntrinsicBounds(
				recordImageGrey, null, null, null);
	}

	private void setupRecordButton(final Activity memoryDroid,
			final int questionIndex) {

		recordQAButton[questionIndex]
				.setOnTouchListener(new View.OnTouchListener() {
					public boolean onTouch(View v, MotionEvent event) {
						CreateModeData createData = CreateModeData
								.getInstance();

						switch (event.getAction()) {
						case MotionEvent.ACTION_DOWN: {

							if (createData.getQuestionState(questionIndex) == CreateModeData.State.BLANK) {

								startRecordingWhole(questionIndex, createData);

							} else if (createData
									.getQuestionState(questionIndex) == CreateModeData.State.RECORDED) {

								startNewRecordingAndEraseOld(questionIndex,
										createData);

							}
							updateState();

							return true;
						}

						case MotionEvent.ACTION_UP: {
							if (createData.getQuestionState(questionIndex) == CreateModeData.State.RECORDING) {
								stopRecording(questionIndex, createData);
							}
							updateState();

							return true;
						}
						default:
							return false;
						}
					}

					private void startRecordingWhole(final int questionIndex,
							CreateModeData createData) {
						startRecording(questionIndex);

						createData.setQuestionState(
								CreateModeData.State.RECORDING, questionIndex);
					}

					private void startNewRecordingAndEraseOld(
							final int questionIndex, CreateModeData createData) {

						if (questionIndex == ANSWER_INDEX) {
							answer.deleteFile();
							answer = null;
						} else {
							question.deleteFile();
							question = null;
						}

						startRecordingWhole(questionIndex, createData);
					}

					private void stopRecording(final int questionIndex,
							CreateModeData createData) {
						createData.setQuestionState(
								CreateModeData.State.RECORDED, questionIndex);

						if (questionIndex == ANSWER_INDEX) {
							handleStopRecording(note, answer, questionIndex, memoryDroid);
						} else {
							handleStopRecording(note, question, questionIndex, memoryDroid);
						}
					}

					private void startRecording(final int questionIndex) {
						long currentTimeMillis = System.currentTimeMillis();

						if (questionIndex == ANSWER_INDEX) {
							String answerName = currentTimeMillis + ".m4a";
							answer = new AudioRecorder(answerName);
							try {
								answer.start();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}

						} else {
							String questionName = currentTimeMillis + ".m4a";
							question = new AudioRecorder(questionName);
							try {
								question.start();
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
						}
					}

				});
	}

	private void handleStopRecording(Note note, AudioRecorder recorder, int questionIndex, Activity memoryDroid) {
		try {
			recorder.stop();
			if (!recorder.isRecorded()) {
				return;
			}

			CreateModeData.getInstance().setAudioFile(
					recorder.getOriginalFile(), questionIndex);

			if (note != null) {
                if (questionIndex == ANSWER_INDEX) {
                	note.setAnswer(recorder.getOriginalFile());
				} else {
					note.setQuestion(recorder.getOriginalFile());
				}
                DbNoteEditor.getInstance().update(
                        memoryDroid, note);
            }

        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
	}

	public void setNote(Note note) {
		this.note = note;

		if (note != null) {
			answer = new AudioRecorder(note.getAnswer());
			question = new AudioRecorder(note.getQuestion());
			CreateModeData.getInstance().setQuestionState(
					CreateModeData.State.RECORDED, QUESTION_INDEX);
			CreateModeData.getInstance().setQuestionState(
					CreateModeData.State.RECORDED, ANSWER_INDEX);
		} else {
			CreateModeData.getInstance().clearState();
			answer = null;
			question = null;
		}
	}
}