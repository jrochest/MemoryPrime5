package com.md.modesetters

import android.app.Activity
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueue
import com.md.modesetters.TtsSpeaker.speak
import com.md.provider.AbstractRep
import com.md.provider.Note
import com.md.utils.ScreenDimmer
import com.md.utils.ToastSingleton
import com.md.workers.BackupPreferences.markAllStale
import kotlinx.coroutines.*
import java.util.*
import java.util.concurrent.TimeUnit

class LearningModeSetter protected constructor() : ModeSetter(), ItemDeletedHandler {
    /**
     * @param memoryDroid
     * @param modeHand
     */
    fun setUp(memoryDroid: SpacedRepeaterActivity?, modeHand: ModeHandler?) {
        parentSetup(memoryDroid, modeHand)
        this.memoryDroid = memoryDroid
    }

    private val lastNoteList: Deque<Note> = ArrayDeque()
    private var lastNoteRep: AbstractRep? = null
    private var memoryDroid: SpacedRepeaterActivity? = null
    private var currentNote: Note? = null
    private var originalSize = 0
    var repCounter = 0
    private var missCounter = 0
    private var questionMode = true
    override fun switchModeImpl(context: Activity) {
        originalSize = currentDeckReviewQueue!!.getSize()
        lastNoteList.clear()
        commonSetup(context, R.layout.learnquestion)

        // Let's just load up the learn question also to get it ready.
        setupQuestionMode(context)
    }

    var middleTappableButton: View? = null

    private fun commonLayoutSetup() {
        val memoryDroid = memoryDroid!!
        middleTappableButton = memoryDroid
            .findViewById<ViewGroup>(R.id.gestures)
        middleTappableButton?.setOnClickListener { _ ->
            mActivity!!.handleRhythmUiTaps(
                this@LearningModeSetter,
                SystemClock.uptimeMillis(),
                SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_SCREEN
            )
        }
        middleTappableButton?.setOnLongClickListener { _ ->
            if (AudioPlayer.instance.wantsToPlay) {
                AudioPlayer.instance.pause()
            } else {
                AudioPlayer.instance.playWhenReady()
            }

            true
        }
        val reviewNumber = memoryDroid
            .findViewById<View>(R.id.reviewNumber) as TextView
        var firstLine = "Scheduled: $originalSize"
        firstLine += "\nPerformed: $repCounter"
        var secondLine = "\nItems Missed: $missCounter"
        secondLine += """
            
            Remaining: ${currentDeckReviewQueue!!.getSize()}
            """.trimIndent()
        if (currentNote != null) {
            secondLine += """
                
                
                Easiness: ${currentNote!!.easiness}
                """.trimIndent()
            secondLine += """
                
                Interval: ${currentNote!!.interval()}
                """.trimIndent()
        }
        reviewNumber.text = """
            ::::${if (questionMode) "Question" else "Answer"}::::
            $firstLine
            $secondLine
            """.trimIndent()
        val noteEditor = DbNoteEditor.instance
        val deleteButton = memoryDroid
            .findViewById<Button>(R.id.deleteButton)
        deleteButton.setOnClickListener(DeleterOnClickListener(noteEditor, mActivity, this))
        memoryDroid!!.findViewById<View>(R.id.new_note_button)
            .setOnClickListener(object : MultiClickListener() {
                override fun onMultiClick(v: View?) {
                    CreateModeSetter.getInstance().switchMode(memoryDroid!!)
                }
            })
        memoryDroid!!.findViewById<View>(R.id.dim_screen_button)
            .setOnClickListener(object : MultiClickListener() {
                override fun onMultiClick(v: View?) {
                    toggleDim()
                }
            })
        memoryDroid!!.findViewById<View>(R.id.back_button)
            .setOnClickListener(object : MultiClickListener() {
                override fun onMultiClick(v: View?) {
                    mActivity!!.onBackPressed()
                }
            })
        val audioFocusToggle = memoryDroid!!.findViewById<View>(R.id.audio_focus_toggle)
        audioFocusToggle.setOnClickListener { v: View? -> AudioPlayer.instance.playWhenReady() }
    }

    fun handleTapCount(count: Int) {
        memoryDroid!!.handleRhythmUiTaps(
            this,
            SystemClock.uptimeMillis(),
            SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_INSTANT,
            count
        )
    }

    /**
     * Note: currently this is hardcoded to 3 clicks.
     */
    abstract class MultiClickListener : View.OnClickListener {
        private var mClickWindowStartMillis = 0L
        private var mClickCount = 0
        abstract fun onMultiClick(v: View?)
        override fun onClick(v: View) {
            val currentTimeMillis = SystemClock.uptimeMillis()
            if (currentTimeMillis - mClickWindowStartMillis < TimeUnit.SECONDS.toMillis(1)) {
                mClickCount++
                if (mClickCount == 3) {
                    onMultiClick(v)
                }
            } else {
                // Start new window.
                mClickWindowStartMillis = currentTimeMillis
                mClickCount = 1
            }
        }
    }

    override fun undo() {
        if (questionMode) {
            if (!AudioPlayer.instance.wantsToPlay) {

                AudioPlayer.instance.playWhenReady()
            } else {
                undoLastQuestion(mActivity!!)
            }
        } else {
            undoThisQuestion(mActivity!!)
        }
    }

    override fun handleReplay() {
        mActivity!!.keepHeadphoneAlive()
        if (questionMode) {
            replay()
        } else {
            replayA()
        }
        hideSystemUi()
    }

    override fun proceedFailure() {
        if (questionMode) {
            proceed(mActivity!!)
        } else {
            updateScoreAndMoveToNext(mActivity!!, 1)
        }
        mActivity!!.keepHeadphoneAlive()
    }

    override fun secondaryAction(): String {
        return if (questionMode) {
            "question mode"
        } else {
            proceedFailure()
            "bad bad"
        }
    }

    /** Moves this note to the end of the queue.  */
    override fun postponeNote(shouldQueue: Boolean) {
        val currentNote = currentNote ?: return

        if (shouldQueue) {
            // Place at end of queue.
            currentDeckReviewQueue!!.updateNote(currentNote, false)
        } else {
            val editor = DbNoteEditor.instance
            val context = mActivity
            if (context != null && editor != null) {
                currentNote.decreasePriority()
                editor.update(context, currentNote)
            } else {
                speak("Error decreasing priority")
            }

            currentDeckReviewQueue!!.hardPostpone(currentNote)
        }
        // Prepare the next note in the queue.
        setupQuestionMode(mActivity!!)
    }

    override fun proceed() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity)
        if (questionMode) {
            if (AudioPlayer.instance.wantsToPlay) {
                // Avoid a double move.
                if (!MoveManager.safeToProceedToNewQuestion()) {
                    return
                }
                AudioPlayer.instance.pause()
            } else {
                proceed(mActivity!!)
            }
        } else {
            updateScoreAndMoveToNext(mActivity!!, 4)
        }
        hideSystemUi()
        mActivity!!.keepHeadphoneAlive()
    }

    private fun setupQuestionMode(
        context: Activity,
        shouldUpdateQuestion: Boolean = true
    ) {
        MoveManager.recordQuestionProceed()
        if (shouldUpdateQuestion) {
            updateVal()
        }
        memoryDroid!!.setContentView(R.layout.learnquestion)
        applyDim(mIsDimmed)
        questionMode = true

        val lifeCycleOwner = mActivity
        val currentNote = currentNote
        if (currentNote != null && lifeCycleOwner != null) {
            val question = currentNote.question
            var shouldPlayTwiceInARow = true
            MoveManager.addJob(lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                while (isActive) {
                    if (question != currentNote.question) {
                        // TODOJ maybe turn into precondition.
                        return@launch
                    }
                    while (middleTappableButton?.isPressed == true) {
                        delay(500)
                        continue
                    }
                    if (!mActivity.isAtLeastResumed()) {
                        return@launch;
                    }

                    AudioPlayer.instance.playFile(
                        question,
                        firedOnceCompletionListener = {},
                        shouldRepeat = shouldPlayTwiceInARow,
                        autoPlay = true)
                    // TODO(jrochest) Start the delay below after done playing. Utilize the
                    // firedOnceCompletionListener
                    shouldPlayTwiceInARow = false
                    delay(30_000)
                    // This TTS is mostly helpful to avoid the bluetooth speakers being off during
                    // replay.
                    speak("replay", lowVolume = true)
                    delay(500)
                }
            })
        } else {
            if (mActivity != null) {
                // Release audio focus since the dialog prevents keyboards from controlling memprime.
                mActivity!!.maybeChangeAudioFocus(false)
            }
            speak("Great job! Deck done.")
            val deckChooser = DeckChooseModeSetter.getInstance()
            val nextDeckWithItems = deckChooser.nextDeckWithItems
            if (nextDeckWithItems != null) {
                deckChooser.loadDeck(nextDeckWithItems)
                instance.switchMode(mActivity!!)
                speak("Loading " + nextDeckWithItems.name)
            } else {
                speak("All decks done..")
            }
        }
        commonLayoutSetup()
        memoryDroid!!.findViewById<View>(R.id.rerecord)
            .setOnTouchListener(
                RecordOnClickListener(
                    currentNote, context, false,
                    lastOrNull
                )
            )
    }

    private val lastOrNull: Note?
        private get() = if (!lastNoteList.isEmpty()) lastNoteList.last else null

    private fun setupAnswerMode(context: Activity) {
        questionMode = false
        memoryDroid!!.setContentView(R.layout.learnquestion)
        applyDim(mIsDimmed)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.answer,
                    {
                        MoveManager.addJob(GlobalScope.launch(Dispatchers.Main) {
                            delay(10_000)
                            if (isActive) {
                                proceed()
                            }
                        })
                    }
                    , true)
        }
        commonLayoutSetup()
        memoryDroid!!.findViewById<View>(R.id.rerecord)
            .setOnTouchListener(
                RecordOnClickListener(
                    currentNote, context, true,
                    lastOrNull
                )
            )

    }

    private fun updateVal() {
        currentNote = currentDeckReviewQueue!!.peekQueue()
        if (currentNote != null) {
            repCounter++
            if (repCounter % 10 == 9) {
                markAllStale(mActivity!!)
            }
        }
    }

    private fun updateScoreAndMoveToNext(context: Activity, newGrade: Int) {
        if (currentNote != null) {
            applyGradeStatic(context, newGrade, currentNote!!)
            ToastSingleton.getInstance()
                .msg("Easiness: " + currentNote!!.easiness + " Interval " + currentNote!!.interval)
            setupQuestionMode(context)
        }
    }

    fun applyGradeStatic(
        context: Activity?, newGrade: Int,
        currentNote: Note
    ) {
        val noteEditor = DbNoteEditor.instance
        if (lastNoteRep != null && lastOrNull != null) {
            val repEditor = DbRepEditor.getInstance()
            repEditor.insert(lastNoteRep)
        }
        lastNoteList.addLast(currentNote.clone())
        // Create the rep info before updating the note with the new internal.
        lastNoteRep = AbstractRep(
            currentNote.id,
            currentNote.interval, newGrade, System.currentTimeMillis()
        )
        currentNote.process_answer(newGrade)
        noteEditor!!.update(context!!, currentNote)

        // If you scored too low review it again, at the end.
        if (currentNote.is_due_for_acquisition_rep) {
            currentDeckReviewQueue!!.updateNote(currentNote, false)
            missCounter++
        } else {
            currentDeckReviewQueue!!.removeNote(currentNote.id)
        }
    }

    override fun deleteNote() {
        val noteEditor = DbNoteEditor.instance
        val note = currentNote
        if (note != null) {
            noteEditor!!.deleteCurrent(mActivity!!, note)
            currentDeckReviewQueue!!.removeNote(note.id)
        }
        setupQuestionMode(mActivity!!)
    }

    private fun replayA() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.answer, null, true)
        }
    }

    private fun replay() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.question, null, true)
        }
    }

    private fun proceed(context: Activity) {
        if (currentNote != null) {
            setupAnswerMode(context)
        }
    }

    private fun undoLastQuestion(context: Activity) {
        if (!lastNoteList.isEmpty()) {
            val currentNote = lastNoteList.removeLast()
            this.currentNote = currentNote
            repCounter--
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(context, currentNote)
            // In case the grade was bad take it out of revision queue.
            currentDeckReviewQueue!!.removeNote(currentNote.id)
            currentDeckReviewQueue!!.addToFront(currentNote)
            setupAnswerMode(context)
        } else {
            speak("Nothing to undo")
        }
    }

    override fun resetActivity() {
        switchMode(mActivity!!)
    }

    private fun undoThisQuestion(context: Activity) {
        setupQuestionMode(context, false)
    }

    override fun adjustScreenLock() {
        ScreenDimmer.getInstance().keepScreenOn(mActivity)
        hideSystemUi()
        mIsDimmed = false
    }

    private var mIsDimmed = false
    override fun toggleDim() {
        mIsDimmed = !mIsDimmed
        applyDim(mIsDimmed)
        showSystemUi()
    }

    override fun mark() {
        if (currentNote != null) {
            currentNote!!.isMarked = true
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(mActivity!!, currentNote!!)
            ToastSingleton.getInstance().msg("Marked note")
        }
    }

    private fun applyDim(isDimmed: Boolean) {
        // Note making the root invisible makes the background grey.
        val layout = memoryDroid!!.findViewById<ViewGroup>(R.id.learn_layout_root)
        for (i in 0 until layout.childCount) {
            applyDimToView(isDimmed, layout.getChildAt(i))
        }
        applyDimToView(isDimmed, memoryDroid!!.findViewById(R.id.reviewNumber))

        // Gestures listeners don't work if dimmed.
        applyDimToView(false, memoryDroid!!.findViewById(R.id.gestures))
    }

    private fun applyDimToView(isDimmed: Boolean, view: View) {
        if (!isDimmed) {
            view.visibility = View.VISIBLE
        } else {
            view.visibility = View.INVISIBLE
        }
    }

    companion object {
        @JvmStatic
        val instance: LearningModeSetter by lazy { LearningModeSetter() }
    }
}