package com.md.modesetters

import android.app.Activity
import android.os.SystemClock
import androidx.lifecycle.lifecycleScope
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueue
import com.md.provider.AbstractRep
import com.md.provider.Note
import com.md.utils.ScreenDimmer
import com.md.utils.ToastSingleton
import com.md.workers.BackupPreferences.markAllStale
import com.md.workingMemory.CurrentNotePartManager
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PracticeModeStateModel @Inject constructor(
    val currentNotePartManager: CurrentNotePartManager,
    val memoryDroid: SpacedRepeaterActivity
    // TODOJNOW remove the mode setter
) : ModeSetter(), ItemDeletedHandler {
    /**
     * @param activity
     * @param modeHand
     */
    fun setUp(activity: SpacedRepeaterActivity?, modeHand: ModeHandler?) {
        parentSetup(activity, modeHand)
    }

    private val lastNoteList: Deque<Note> = ArrayDeque()
    private var lastNoteRep: AbstractRep? = null
    // TODOJNOW delete this
    private var currentNote: Note? = null
    private var originalSize = 0
    var repCounter = 0
    private var missCounter = 0
    // TODOJNOW delete this
    private var questionMode = true
    override fun switchModeImpl(context: Activity) {
        originalSize = currentDeckReviewQueue!!.getSize()
        lastNoteList.clear()
        commonSetup(context, R.layout.learnquestion)

        // Let's just load up the learn question also to get it ready.
        setupQuestionMode(context)
    }

    /**
     * TODOJNOW use this
     *  memoryDroid!!.handleRhythmUiTaps(
     *                 this@PracticeModeStateModel,
     *                 SystemClock.uptimeMillis(),
     *                 SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_SCREEN
     *             )
     */

    fun handleTapCount(count: Int) {
        memoryDroid!!.handleRhythmUiTaps(
            this,
            SystemClock.uptimeMillis(),
            SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_INSTANT,
            count
        )
    }

    override fun undo() {
        if (questionMode) {
            if (!AudioPlayer.instance.wantsToPlay) {
                AudioPlayer.instance.playWhenReady()
            } else {
                undoFromQuestionToAnswerMode()
            }
        } else {
            undoThisQuestion(memoryDroid!!)
        }
    }

    override fun handleReplay() {
        if (questionMode) {
            replay()
        } else {
            replayA()
        }
        hideSystemUi()
    }

    override fun proceedFailure() {
        if (questionMode) {
            proceedCommon()
        } else {
            updateScoreAndMoveToNext(1)
        }
    }

    override fun secondaryAction(): String {
        val messageToSpeak = if (questionMode) {
            if (AudioPlayer.instance.hasPlayedCurrentFile()) {
                return ""
            }

            "secondary action. proceed"
        } else {
            "bad bad"
        }
        proceedFailure()
        return messageToSpeak
    }

    /** Moves this note to the end of the queue.  */
    override fun postponeNote(shouldQueue: Boolean) {
        val currentNote = currentNote ?: return

        if (shouldQueue) {
            // Place at end of queue.
            currentDeckReviewQueue!!.updateNote(currentNote, false)
        } else {
            val editor = DbNoteEditor.instance
            val context = memoryDroid
            if (context != null && editor != null) {
                currentNote.decreasePriority()
                editor.update(currentNote)
            } else {
                TtsSpeaker.speak("Error decreasing priority")
            }

            currentDeckReviewQueue!!.hardPostpone(currentNote)
        }
        // Prepare the next note in the queue.
        setupQuestionMode(memoryDroid!!)
    }

    override fun proceed() {
        ScreenDimmer.getInstance().keepScreenOn(memoryDroid)
        if (!AudioPlayer.instance.hasPlayedCurrentFile()) {
          return
        }
        if (questionMode) {
            proceedCommon()
        } else {
            updateScoreAndMoveToNext(4)
        }
        hideSystemUi()
    }

    private fun setupQuestionMode(
        context: Activity,
        shouldUpdateQuestion: Boolean = true
    ) {
        MoveManager.recordQuestionProceed()
        if (shouldUpdateQuestion) {
            updateStartingInQuestionMode()
        }
        questionMode = true
        val lifeCycleOwner = memoryDroid
        val currentNote = currentNote
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = true)
        if (currentNote != null) {
            val questionAudioFilePath = currentNote.question
            var shouldPlayTwiceInARow = true
            MoveManager.replaceMoveJobWith(lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                while (isActive) {
                    if (questionAudioFilePath != currentNote.question) {
                        // TODOJ maybe turn into precondition.
                        return@launch
                    }
                    if (!memoryDroid.isAtLeastResumed()) {
                        return@launch
                    }

                    AudioPlayer.instance.playFile(
                        questionAudioFilePath,
                        firedOnceCompletionListener = {},
                        shouldRepeat = shouldPlayTwiceInARow,
                        autoPlay = true)

                    // Delay for 1 second to avoid interfering with the main file that is playing...
                    delay(1_000)
                    // ... then preload a file that is likely to be used soon.
                    try {
                        currentDeckReviewQueue!!.preload()
                    } catch (e: IllegalStateException) {
                        TtsSpeaker.speak("preload failed", lowVolume = true)
                        e.printStackTrace()
                    }

                    // TODO(jrochest) Start the delay below after done playing. Utilize the
                    // firedOnceCompletionListener
                    // Note this works fine if it replays before the note is done.
                    shouldPlayTwiceInARow = false
                    delay(30_000)
                    // This TTS is mostly helpful to avoid the bluetooth speakers being off during
                    // replay.
                    TtsSpeaker.speak("replay", lowVolume = true)
                    delay(500)
                }
            })
        } else {
            // Release audio focus since the dialog prevents keyboards from controlling memprime.
            memoryDroid.maybeChangeAudioFocus(false)
            TtsSpeaker.speak("Great job! Deck done.")
            val deckChooser = DeckChooseModeSetter.getInstance()
            val nextDeckWithItems = deckChooser.nextDeckWithItems
            if (nextDeckWithItems != null) {
                deckChooser.loadDeck(nextDeckWithItems)
                this.switchMode(memoryDroid)
                TtsSpeaker.speak("Loading " + nextDeckWithItems.name)
            } else {
                TtsSpeaker.speak("All decks done..")
            }
        }
    }

    private val lastOrNull: Note?
        get() = if (!lastNoteList.isEmpty()) lastNoteList.last else null

    private fun setupAnswerMode() {
        questionMode = false
        val currentNote = currentNote
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
        val lifeCycleOwner = memoryDroid
        if (currentNote != null && lifeCycleOwner != null) {
            MoveManager.replaceMoveJobWith(lifeCycleOwner.lifecycleScope.launch(Dispatchers.Main) {
                AudioPlayer.instance.playFile(currentNote.answer, shouldRepeat = true)
                delay(30_000)
                if (isActive) {
                    TtsSpeaker.speak("Auto-proceed from answer.")
                    proceed()
                }
            })
        }
    }


    private fun updateStartingInQuestionMode() {
        currentNote = currentDeckReviewQueue!!.peekQueue()
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
        if (currentNote != null) {
            repCounter++
            if (repCounter % 10 == 9) {
                markAllStale(memoryDroid!!)
            }
        }
    }

    private fun updateScoreAndMoveToNext(newGrade: Int) {
        if (currentNote != null) {
            applyGradeStatic(newGrade, currentNote!!)
            ToastSingleton.getInstance()
                .msg("Easiness: " + currentNote!!.easiness + " Interval " + currentNote!!.interval)
            setupQuestionMode(memoryDroid)
        }
    }

    private fun applyGradeStatic(
        newGrade: Int, currentNote: Note
    ) {
        val noteEditor = DbNoteEditor.instance
        if (lastNoteRep != null && lastOrNull != null) {
            val repEditor = DbRepEditor.getInstance()
            repEditor.insert(lastNoteRep)
        }
        // It seems unwise to create a clone, which not just update the current one.
        lastNoteList.addLast(currentNote.clone())
        // Create the rep info before updating the note with the new internal.
        lastNoteRep = AbstractRep(
            currentNote.id,
            currentNote.interval, newGrade, System.currentTimeMillis()
        )
        currentNote.process_answer(newGrade)
        noteEditor!!.update(currentNote)

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
            noteEditor!!.deleteCurrent(memoryDroid!!, note)
            currentDeckReviewQueue!!.removeNote(note.id)
        }
        setupQuestionMode(memoryDroid!!)
    }

    private fun replayA() {
        ScreenDimmer.getInstance().keepScreenOn(memoryDroid)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.answer, null, true)
        }
    }

    private fun replay() {
        ScreenDimmer.getInstance().keepScreenOn(memoryDroid)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.question, null, true)
        }
    }

    private fun proceedCommon() {
        if (currentNote != null) {
            setupAnswerMode()
        }
    }

    private fun undoFromQuestionToAnswerMode() {
        if (!lastNoteList.isEmpty()) {
            val currentNote = lastNoteList.removeLast()
            this.currentNote = currentNote
            currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = true)

            repCounter--
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote)
            // In case the grade was bad take it out of revision queue.
            currentDeckReviewQueue!!.removeNote(currentNote.id)
            currentDeckReviewQueue!!.addToFront(currentNote)
            setupAnswerMode()
        } else {
            TtsSpeaker.speak("Nothing to undo")
        }
    }

    override fun resetActivity() {
        switchMode(memoryDroid!!)
    }

    private fun undoThisQuestion(context: Activity) {
        setupQuestionMode(context, false)
    }

    override fun adjustScreenLock() {
        ScreenDimmer.getInstance().keepScreenOn(memoryDroid)
        hideSystemUi()
    }

    override fun mark() {
        if (currentNote != null) {
            currentNote!!.isMarked = true
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote!!)
            ToastSingleton.getInstance().msg("Marked note")
        }
    }

}