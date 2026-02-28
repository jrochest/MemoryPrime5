package com.md.provider;

import static com.md.modesetters.TtsSpeaker.speak;

import android.provider.BaseColumns;
import android.util.Log;

import androidx.annotation.Nullable;

import com.md.CategorySingleton;
import com.md.fsrs.FsrsScheduler;
import com.md.modesetters.DeckInfo;

/**
 * Notes table
 */
public final class Note extends AbstractNote implements BaseColumns, Cloneable {

    private static final String TAG = "NOTE";

    public Note(int grade, int id, String question, String answer,
            String questionTranscript, String answerTranscript,
            float questionTranscriptConfidence, float answerTranscriptConfidence,
            int category, boolean unseen, boolean marked, float easiness,
            int acq_reps, int ret_reps, int lapses, int acq_reps_since_lapse,
            int ret_reps_since_lapse, int last_rep, int next_rep,
            float fsrsStability, float fsrsDifficulty, int fsrsState) {
        super();
        this.grade = grade;
        this.id = id;
        this.question = question;
        this.answer = answer;
        this.questionTranscript = questionTranscript;
        this.answerTranscript = answerTranscript;
        this.questionTranscriptConfidence = questionTranscriptConfidence;
        this.answerTranscriptConfidence = answerTranscriptConfidence;
        this.categoryAkaDeckId = category;
        this.unseen = unseen;
        this.marked = marked;
        this.easiness = easiness;
        this.acq_reps = acq_reps;
        this.ret_reps = ret_reps;
        this.lapses = lapses;
        this.acq_reps_since_lapse = acq_reps_since_lapse;
        this.ret_reps_since_lapse = ret_reps_since_lapse;
        this.last_rep = last_rep;
        this.next_rep = next_rep;
        this.fsrsStability = fsrsStability;
        this.fsrsDifficulty = fsrsDifficulty;
        this.fsrsState = fsrsState;
    }

    @Override
    public boolean equals(@Nullable Object obj) {
        if (obj != null) {
            return id == (((Note) obj).id);
        }
        return false;
    }

    public Note clone() {
        return new Note(grade, id, question, answer, questionTranscript, answerTranscript,
                questionTranscriptConfidence, answerTranscriptConfidence,
                categoryAkaDeckId, unseen,
                marked,
                easiness, acq_reps, ret_reps, lapses, acq_reps_since_lapse,
                ret_reps_since_lapse, last_rep, next_rep,
                fsrsStability, fsrsDifficulty, fsrsState);

    }

    @Override
    public String toString() {

        String lastRep = prettyPrint(last_rep);

        String nextRep = prettyPrint(next_rep);

        return "Note [marked=" + marked + ", grade=" + grade + ", id=" + id
                + ", question=" + question + ", answer=" + answer
                + ", category=" + categoryAkaDeckId + ", unseen=" + unseen
                + ", easiness=" + easiness + ", acq_reps=" + acq_reps
                + ", ret_reps=" + ret_reps + ", lapses=" + lapses
                + ", acq_reps_since_lapse=" + acq_reps_since_lapse
                + ", ret_reps_since_lapse=" + ret_reps_since_lapse
                + ", last_rep=" + lastRep + ", next_rep=" + nextRep + "]";
    }

    private String prettyPrint(int repOrig) {

        if (repOrig == 0) {
            return "new";
        }

        int rep = (repOrig - CategorySingleton.getInstance()
                .getDaysSinceStart());

        if (rep == -1) {
            return "today";
        }
        if (rep < -1) {
            return (Math.abs(rep) - 1) + " days ago";
        }
        if (rep > 0) {
            return (Math.abs(rep) - 1) + " from now";
        }

        return "" + rep;
    }

    private float calculate_interval_noise(int interval) {

        float noise = 0.0f;
        if (interval == 0) {
            noise = 0f;
        } else if (interval == 1) {
            noise = (int) (Math.random());
        } else if (interval <= 10) {
            // random.randint(-1,1)
            noise = (int) (2 * Math.random()) - 1;
        } else if (interval <= 60) {
            // random.randint(-3,3);

            noise = (int) (6 * Math.random()) - 3;
        } else {
            int a = (int) (.05 * interval);
            // noise = round(random.uniform(-a,a))
            noise = (int) (2 * a * Math.random()) - a;
        }

        return noise;
    }

    public boolean is_new() {
        return (acq_reps == 0) && (ret_reps == 0);
    }

    public boolean is_due_for_acquisition_rep() {
        return (grade < 2);
    }

    // #########################################################################
    //
    // is_due_for_retention_rep
    //
    // Due for a retention repetion within 'days' days?
    //
    // ########################################################################

    public boolean is_due_for_retention_rep(int days) {
        // TODO what is this days_since_start
        return (grade >= 2)
                && (CategorySingleton.getInstance().getDaysSinceStart() >= next_rep
                        - days);
    }

    public boolean is_overdue() {
        final CategorySingleton instance = CategorySingleton.getInstance();
        int overDueDate = instance.getDaysSinceStart() + instance.getLookAheadDays();

        return grade >= 2 && overDueDate > next_rep;
    }

    int calculate_initial_interval(int grade) {
        // If this is the first time we grade this item, allow for slightly
        // longer scheduled intervals, as we might know this item from before.
        int[] initialGrade = { 0, 0, 1, 3, 4, 5 };

        int interval = initialGrade[grade];
        return interval;
    }

    public int process_answer(int new_grade) {

        // Calculate scheduled and actual interval, taking care of corner
        // case when learning ahead on the same day.

        int scheduled_interval = next_rep - last_rep;
        int actual_interval = CategorySingleton.getInstance()
                .getDaysSinceStart() - last_rep;

        if (actual_interval == 0) {
            actual_interval = 1; // Otherwise new interval can become zero.
        }

        // If the interval
        int new_interval = 100000;

        if (is_new()) {
            // The item is not graded yet, e.g. because it is imported.
            acq_reps = 1;
            acq_reps_since_lapse = 1;

            new_interval = calculate_initial_interval(new_grade);

            // Make sure the second copy of a grade 0 item doesn't show up
            // again.

        } else if (grade <= 1 && new_grade <= 1) {

            // In the acquisition phase and staying there.
            acq_reps += 1;
            acq_reps_since_lapse += 1;
            new_interval = 0;
        } else if (grade <= 1 && new_grade >= 2) {
            // In the acquisition phase and moving to the retention phase.
            acq_reps += 1;
            acq_reps_since_lapse += 1;
            new_interval = 1;

        } else if (grade >= 2 && new_grade <= 1) {

            // In the retention phase and dropping back to the acquisition
            // phase.
            ret_reps += 1;
            lapses += 1;
            acq_reps_since_lapse = 0;
            ret_reps_since_lapse = 0;

            new_interval = 0;

            // Move this item to the front of the list, to have precedence over
            // items which are still being learned for the first time.
            // TODO sort by ID lowest to highest to accomplish

            // JTODO I added this
            if (new_grade == 0) {
                easiness -= 0.2;
            }
            // JTODO I added this
            if (new_grade == 1) {
                easiness -= 0.18;
            }

        } else if (grade >= 2 && new_grade >= 2) {
            // In the retention phase and staying there.

            ret_reps += 1;
            ret_reps_since_lapse += 1;

            // Jacob's Note I'm making the easiness vary
            if (actual_interval >= scheduled_interval) {

                if (new_grade == 2) {
                    easiness -= 0.16;
                }
                if (new_grade == 3) {
                    easiness -= 0.14;
                }
                if (new_grade == 5) {
                    easiness += 0.10;
                }
                if (easiness < 1.3) {
                    easiness = 1.3f;
                }
            }

            new_interval = 0;
            // This inside the if
            if (ret_reps_since_lapse == 1) {
                new_interval = 6;
            } else {
                if (new_grade == 2 || new_grade == 3) {
                    if (actual_interval <= scheduled_interval) {
                        new_interval = (int) (actual_interval * easiness);
                    } else {
                        new_interval = scheduled_interval;
                    }
                } else if (new_grade == 4) {
                    new_interval = (int) (actual_interval * easiness);
                }
                if (new_grade == 5) {
                    if (actual_interval < scheduled_interval) {
                        new_interval = scheduled_interval; // Avoid spacing.
                    } else {
                        new_interval = (int) (actual_interval * easiness);
                    }

                }
            }

            // Shouldn't happen, but build in a safeguard.

            if (new_interval == 0) {
                if (Log.isLoggable(TAG, Log.ERROR)) {
                    Log.e(TAG, "Internal error: new interval was zero.");
                }
                System.out.println();
                new_interval = scheduled_interval;
            }

        }

        // When doing a dry run, stop here and return the scheduled interval.

        // Add some randomness to interval.
        float noise = calculate_interval_noise(new_interval);

        // Update grade and interval.

        grade = new_grade;
        last_rep = CategorySingleton.getInstance().getDaysSinceStart();
        next_rep = (int) (CategorySingleton.getInstance().getDaysSinceStart()
                + new_interval + noise);
        unseen = false;

        // Don't schedule inverse or identical questions on the same day.
        return (int) (new_interval + noise);
    }

    /**
     * Process a review using the FSRS algorithm.
     * This replaces process_answer() for cards that have been migrated to FSRS.
     *
     * @param newGrade SM-2 style grade (0-5), will be converted to FSRS Rating.
     * @return The new interval in days.
     */
    public int processFsrsAnswer(int newGrade) {
        FsrsScheduler.Rating rating = FsrsScheduler.Rating.Companion.fromSm2Grade(newGrade);

        // Compute elapsed days since last review
        int daysSinceStart = CategorySingleton.getInstance().getDaysSinceStart();
        double elapsedDays;
        if (last_rep == 0) {
            elapsedDays = 0;
        } else {
            elapsedDays = Math.max(0, daysSinceStart - last_rep);
        }

        // Build current FSRS state (or null for new/unmigrated cards)
        FsrsScheduler.FsrsState currentState;
        if (isFsrsMigrated()) {
            currentState = new FsrsScheduler.FsrsState(
                    fsrsStability, fsrsDifficulty,
                    FsrsScheduler.CardState.Companion.fromInt(fsrsState));
        } else {
            // First FSRS review — treat as new
            currentState = null;
        }

        // Compute next FSRS state
        FsrsScheduler.FsrsState nextState = FsrsScheduler.INSTANCE.nextState(
                currentState, rating, elapsedDays);

        // Update FSRS fields
        fsrsStability = (float) nextState.getStability();
        fsrsDifficulty = (float) nextState.getDifficulty();
        fsrsState = nextState.getState().getValue();

        // Compute interval from stability
        int newInterval = FsrsScheduler.INSTANCE.nextInterval(
                nextState.getStability(), FsrsScheduler.DEFAULT_RETENTION);

        // If in learning/relearning, use short intervals
        if (nextState.getState() == FsrsScheduler.CardState.Learning
                || nextState.getState() == FsrsScheduler.CardState.Relearning) {
            newInterval = 0; // Review again today
        }

        // Map FSRS state back to SM-2 grade for backward compat
        if (rating == FsrsScheduler.Rating.Again) {
            grade = 1;
            lapses++;
            acq_reps_since_lapse = 0;
            ret_reps_since_lapse = 0;
        } else {
            grade = 4; // Passed
            ret_reps++;
            ret_reps_since_lapse++;
        }

        // Update scheduling fields
        last_rep = daysSinceStart;
        next_rep = daysSinceStart + newInterval;
        unseen = false;

        return newInterval;
    }

    public Note(String question, String answer, DeckInfo deck) {
        this(question, answer, deck.getId());
    }

    public Note(String question, String answer, int deckId) {
        this.question = question;
        this.answer = answer;
        this.categoryAkaDeckId = deckId;

        resetLearningData();
    }

    private void resetLearningData() {

        grade = 0;
        easiness = 2.5f;

        acq_reps = 0;
        ret_reps = 0;
        lapses = 0;
        acq_reps_since_lapse = 0;
        ret_reps_since_lapse = 0;

        last_rep = 0;
        next_rep = 0;

        unseen = true;
        marked = false;
        priority = DEFAULT_PRIORITY;
    }

    public static final int DEFAULT_PRIORITY = 100;

    public int getInterval() {
        return getNext_rep() - getLast_rep();
    }

    public void decreasePriority() {
        priority--;
        speak("decreasing priority to " + priority);
    }
}