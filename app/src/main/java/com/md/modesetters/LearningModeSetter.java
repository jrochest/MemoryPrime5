package com.md.modesetters;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.md.AudioPlayer;
import com.md.DbNoteEditor;
import com.md.DbRepEditor;
import com.md.ModeHandler;
import com.md.R;
import com.md.RevisionQueue;
import com.md.SpacedRepeaterActivity;
import com.md.provider.AbstractRep;
import com.md.provider.Note;
import com.md.utils.ScreenDimmer;
import com.md.utils.ToastSingleton;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.md.SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_INSTANT;

public class LearningModeSetter extends ModeSetter implements
        ItemDeletedHandler {

    private static LearningModeSetter instance = null;

    protected LearningModeSetter() {
    }

    public static LearningModeSetter getInstance() {
        if (instance == null) {
            instance = new LearningModeSetter();
        }
        return instance;
    }

    /**
     * @param memoryDroid
     * @param modeHand
     */
    public void setUp(SpacedRepeaterActivity memoryDroid, ModeHandler modeHand) {
        parentSetup(memoryDroid, modeHand);
        this.memoryDroid = memoryDroid;
    }

    private final Deque<Note> lastNoteList = new ArrayDeque<>();

    private AbstractRep lastNoteRep;
    private SpacedRepeaterActivity memoryDroid;
    private Note currentNote;
    private int originalSize;
    private int repCounter;
    private int missCounter;
    private boolean questionMode = true;

    public void setupModeImpl(final Activity context) {
        originalSize = RevisionQueue.getCurrentDeckReviewQueue().getSize();

        lastNoteList.clear();

        commonSetup(context, R.layout.learnquestion);

        // Let's just load up the learn question also to get it ready.
        setupQuestionMode(context);
    }

    protected void updateShouldRepeat() {
        AudioPlayer.getInstance().setShouldRepeat(true);
    }

    private void commonLayoutSetup() {
        ViewGroup gestures = this.memoryDroid
                .findViewById(R.id.gestures);
        gestures.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mActivity.handleRhythmUiTaps(LearningModeSetter.this, SystemClock.uptimeMillis(), SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_SCREEN);
            }
        });

        final TextView reviewNumber = (TextView) this.memoryDroid
                .findViewById(R.id.reviewNumber);

        String firstLine = "Scheduled: " + originalSize;
        firstLine += "\nPerformed: " + repCounter;
        String secondLine = "\nItems Missed: " + missCounter;
        secondLine += "\nRemaining: " + RevisionQueue.getCurrentDeckReviewQueue().getSize();
        if (currentNote != null) {
            secondLine += "\n\nEasiness: " + currentNote.getEasiness();
            secondLine += "\nInterval: " + currentNote.interval();
        }

        reviewNumber.setText("::::" + ((questionMode) ? "Question" : "Answer") + "::::\n"
                + firstLine + "\n" + secondLine);

        final DbNoteEditor noteEditor = DbNoteEditor.getInstance();

        Button deleteButton = memoryDroid
                .findViewById(R.id.deleteButton);

        deleteButton.setOnClickListener(new DeleterOnClickListener(noteEditor, mActivity, this));

        memoryDroid.findViewById(R.id.new_note_button).setOnClickListener(new MultiClickListener() {
            @Override
            public void onMultiClick(View v) {
                CreateModeSetter.getInstance().setupMode(memoryDroid);
            }
        });

        memoryDroid.findViewById(R.id.dim_screen_button).setOnClickListener(new MultiClickListener() {
            @Override
            public void onMultiClick(View v) {
                toggleDim();
            }
        });

        memoryDroid.findViewById(R.id.back_button).setOnClickListener(new MultiClickListener() {
            @Override
            public void onMultiClick(View v) {
                mActivity.onBackPressed();
            }
        });

        View audioFocusToggle = memoryDroid.findViewById(R.id.audio_focus_toggle);
        audioFocusToggle.setOnKeyListener(
                new View.OnKeyListener() {
                    @Override
                    public boolean onKey(View view, int keyCode, KeyEvent keyEvent) {
                        // BR301 sends an enter command, which we want to ignore.
                        if (keyCode == KeyEvent.KEYCODE_ENTER) {
                            return true;
                        }
                        return false;
                    }
                }
        );

        audioFocusToggle.setOnClickListener(new MultiClickListener() {
            @Override
            public void onMultiClick(View v) {
                // Release audio focus since the dialog prevents keyboards from controlling memprime.
                mActivity.maybeChangeAudioFocus(!mActivity.hasAudioFocus());
            }
        });
    }

    public void handleTapCount(int count) {
        memoryDroid.handleRhythmUiTaps(this, SystemClock.uptimeMillis(), PRESS_GROUP_MAX_GAP_MS_INSTANT, count);
    }

    /**
     * Note: currently this is hardcoded to 3 clicks.
     */
    public static abstract class MultiClickListener implements View.OnClickListener {

        private long mClickWindowStartMillis = 0L;
        private int mClickCount = 0;

        public abstract void onMultiClick(View v);

        @Override
        public void onClick(View v) {
            final long currentTimeMillis = SystemClock.uptimeMillis();
            if (currentTimeMillis - mClickWindowStartMillis < TimeUnit.SECONDS.toMillis(1)) {
                mClickCount++;
                if (mClickCount == 3) {
                    onMultiClick(v);
                }
            } else {
                // Start new window.
                mClickWindowStartMillis = currentTimeMillis;
                mClickCount = 1;
            }
        }
    }

    public void undo() {
        if (questionMode) {
            undoLastQuestion(mActivity);
        } else {
            undoThisQuestion(mActivity);
        }
    }

    public void handleReplay() {
        mActivity.keepHeadphoneAlive();
        if (questionMode) {
            replay();
        } else {
            replayA();
        }
        hideSystemUi();
    }

    public void proceedFailure() {
        if (questionMode) {
            proceed(mActivity);
        } else {
            updateScoreAndMoveToNext(mActivity, 1);
        }
        mActivity.keepHeadphoneAlive();
    }

    public String secondaryAction() {
        if (questionMode) {
            handleReplay();
            return "bad go";
        } else {
            proceedFailure();
            return "bad bad";
        }
    }

    public void proceed() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity);
        if (questionMode) {
            proceed(mActivity);
        } else {
            updateScoreAndMoveToNext(mActivity, 4);
        }
        hideSystemUi();
        mActivity.keepHeadphoneAlive();
    }

    private void setupQuestionMode(final Activity context,
                                   boolean shouldUpdateQuestion) {
        if (shouldUpdateQuestion) {
            updateVal();
        }
        memoryDroid.setContentView(R.layout.learnquestion);
        applyDim(mIsDimmed);
        questionMode = true;

        if (currentNote != null) {
            AudioPlayer.getInstance().playFile(currentNote.getQuestion(), null, true);
        } else {
            if (mActivity instanceof SpacedRepeaterActivity) {
                // Release audio focus since the dialog prevents keyboards from controlling memprime.
                mActivity.maybeChangeAudioFocus(false);
            }
            AlertDialog.Builder builder = new AlertDialog.Builder(context);

            TtsSpeaker.speak("Great job! Deck done.");
            builder.setMessage("Great job! Deck done.")
                    .setCancelable(false)
                    .setNeutralButton("Ok",
                            new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog,
                                                    int id) {

                                }
                            });
            AlertDialog alert = builder.create();
            alert.show();

        }

        commonLayoutSetup();

        memoryDroid.findViewById(R.id.rerecord)
                .setOnTouchListener(
                        new RecordOnClickListener(currentNote, context, false,
                                getLastOrNull()));

    }

    @Nullable
    private Note getLastOrNull() {
        return !lastNoteList.isEmpty() ? lastNoteList.getLast() : null;
    }

    private void setupQuestionMode(final Activity context) {
        setupQuestionMode(context, true);
    }

    private void setupAnswerMode(final Activity context) {

        questionMode = false;
        memoryDroid.setContentView(R.layout.learnquestion);
        applyDim(mIsDimmed);

        if (currentNote != null) {
            AudioPlayer.getInstance().playFile(currentNote.getAnswer(), null, true);
        }

        commonLayoutSetup();

        memoryDroid.findViewById(R.id.rerecord)
                .setOnTouchListener(
                        new RecordOnClickListener(currentNote, context, true,
                                getLastOrNull()));
    }

    private void updateVal() {
        currentNote = RevisionQueue.getCurrentDeckReviewQueue().peekQueue();
        if (currentNote != null) {
            repCounter++;
        }
    }

    private void updateScoreAndMoveToNext(Activity context, int newGrade) {
        if (currentNote != null) {
            applyGradeStatic(context, newGrade, currentNote);
            ToastSingleton.getInstance().msg("Easiness: " + currentNote.getEasiness() + " Interval " + currentNote.getInterval());
            setupQuestionMode(context);
        }
    }

    public void applyGradeStatic(Activity context, int newGrade,
                                 Note currentNote) {
        DbNoteEditor noteEditor = DbNoteEditor.getInstance();

        if (lastNoteRep != null && getLastOrNull() != null) {
            DbRepEditor repEditor = DbRepEditor.getInstance();
            repEditor.insert(lastNoteRep);
        }

        lastNoteList.addLast(currentNote.clone());
        // Create the rep info before updating the note with the new internal.
        lastNoteRep = new AbstractRep(currentNote.getId(),
                currentNote.getInterval(), newGrade, System.currentTimeMillis());

        currentNote.process_answer(newGrade);

        noteEditor.update(context, currentNote);

        // If you scored too low review it again, at the end.
        if (currentNote.is_due_for_acquisition_rep()) {
            RevisionQueue.getCurrentDeckReviewQueue().updateNote(currentNote, false);
            missCounter++;
        } else {
            RevisionQueue.getCurrentDeckReviewQueue().removeNote(currentNote.getId());
        }
    }

    @Override
    public void deleteNote() {
        final DbNoteEditor noteEditor = DbNoteEditor.getInstance();
        Note note = currentNote;
        if (note != null) {
            noteEditor.deleteCurrent(mActivity, note);
            RevisionQueue.getCurrentDeckReviewQueue().removeNote(note.getId());
        }

        setupQuestionMode(mActivity);
    }

    private void replayA() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity);
        if (currentNote != null) {
            AudioPlayer.getInstance().playFile(currentNote.getAnswer(), null, true);
        }
    }

    private void replay() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity);
        if (currentNote != null) {
            AudioPlayer.getInstance().playFile(currentNote.getQuestion(), null, true);
        }
    }

    private void proceed(final Activity context) {
        if (currentNote != null) {
            setupAnswerMode(context);
        }
    }

    private void undoLastQuestion(final Activity context) {
        if (!lastNoteList.isEmpty()) {
            currentNote = lastNoteList.removeLast();
            repCounter--;
            DbNoteEditor noteEditor = DbNoteEditor.getInstance();
            noteEditor.update(context, currentNote);
            // In case the grade was bad take it out of revision queue.
            RevisionQueue.getCurrentDeckReviewQueue().removeNote(currentNote.getId());
            RevisionQueue.getCurrentDeckReviewQueue().addToFront(currentNote);
            setupAnswerMode(context);
        } else {
            TtsSpeaker.speak("Nothing to undo");
        }
    }

    public void resetActivity() {
        setupMode(mActivity);
    }

    private void undoThisQuestion(final Activity context) {
        setupQuestionMode(context, false);
    }

    protected void adjustScreenLock() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity);
        hideSystemUi();
        mIsDimmed = false;
    }

    private boolean mIsDimmed = false;

    public void toggleDim() {
        mIsDimmed = !mIsDimmed;
        applyDim(mIsDimmed);
        showSystemUi();
    }

    public void mark() {
        if (currentNote != null) {
            currentNote.setMarked(true);
            final DbNoteEditor noteEditor = DbNoteEditor.getInstance();
            noteEditor.update(mActivity, currentNote);
            ToastSingleton.getInstance().msg("Marked note");
        }
    }

    private void applyDim(boolean isDimmed) {
        // Note making the root invisible makes the background grey.
        ViewGroup layout = this.memoryDroid.findViewById(R.id.learn_layout_root);
        for (int i = 0; i < layout.getChildCount(); i++) {
            applyDimToView(isDimmed, layout.getChildAt(i));
        }

        applyDimToView(isDimmed, this.memoryDroid.findViewById(R.id.reviewNumber));

        // Gestures listeners don't work if dimmed.
        applyDimToView(false, this.memoryDroid.findViewById(R.id.gestures));
    }

    private void applyDimToView(boolean isDimmed, View view) {
        if (!isDimmed) {
            view.setVisibility(View.VISIBLE);
        } else {
            view.setVisibility(View.INVISIBLE);
        }
    }
}