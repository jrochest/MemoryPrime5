package com.md.modesetters

import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueue
import com.md.provider.AbstractRep
import com.md.provider.Note
import com.md.utils.KeepScreenOn
import com.md.utils.ToastSingleton
import com.md.workers.BackupPreferences.markAllStale
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.Mode
import com.md.composeModes.ModeViewModel
import com.md.composeModes.PracticeMode
import com.md.composeModes.PracticeModeViewModel
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.*
import javax.inject.Inject

@ActivityScoped
class PracticeModeStateHandler @Inject constructor(
    val currentNotePartManager: CurrentNotePartManager,
    @ActivityContext val context: Context,
    private val focusedQueueStateModel: RevisionQueueStateModel,
    // TODOJNOW use theses.
    private val model: ModeViewModel,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val deckLoadManager: DeckLoadManager,
) {
    val activity: SpacedRepeaterActivity = context as SpacedRepeaterActivity

    private val lastNoteList: Deque<Note> = ArrayDeque()
    private var lastNoteRep: AbstractRep? = null
    // TODOJSOONNOW delete this
    private var currentNote: Note? = null
    private var originalSize = 0
    var repCounter = 0
    private var missCounter = 0
    // TODOJSOONNOW delete this
    private var questionMode = true
    fun onSwitchToMode() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    flow = model.modeModel,
                    flow2 = practiceModeViewModel.practiceStateFlow,
                    flow3 = focusedQueueStateModel.queue,
                    flow4 = currentNotePartManager.noteStateFlow
                ) { mode: Mode, practiceMode: PracticeMode, revisionQueue: RevisionQueue?,
                    noteState: CurrentNotePartManager.NoteState? ->
                    if (mode != Mode.Practice || practiceMode != PracticeMode.Practicing) {
                        MoveManager.cancelJobs()
                        return@combine
                    }

                    if (revisionQueue == null || revisionQueue.getSize() == 0) {
                        MoveManager.cancelJobs()
                        deckLoadManager.refreshDeckListAndFocusFirstActiveNonemptyQueue()
                        val focusedQueue = focusedQueueStateModel.queue.value
                        if (focusedQueue != null) {
                            TtsSpeaker.speak("Deck done. Loading next")
                        } else {
                            TtsSpeaker.speak("Great job! Deck done. All decks done..")
                        }
                        return@combine
                    }

                    if (noteState == null) {
                        return@combine
                    }

                    // Ensure that we don't allow the user to go forward without playing through the
                    // audio file once.
                    practiceModeViewModel.hasPlayedCurrentNotePart.value = false
                    MoveManager.replaceMoveJobWith(activity.lifecycleScope.launch(Dispatchers.Main) {
                        while (isActive) {
                            while (!activity.isAtLeastResumed()) {
                                delay(1000)
                            }
                            val part = noteState.notePart
                            val note = checkNotNull(noteState.currentNote)
                            if (part.partIsAnswer) {
                                AudioPlayer.instance.suspendPlay(note.answer)
                                practiceModeViewModel.hasPlayedCurrentNotePart.value = true
                                AudioPlayer.instance.suspendPlay(note.answer)
                                delay(8_000)
                                while (!activity.isAtLeastResumed()) {
                                    delay(1000)
                                }
                                TtsSpeaker.speak("Auto-proceed soon.", lowVolume = true)
                                delay(2_000)
                                while (!activity.isAtLeastResumed()) {
                                    delay(1000)
                                }
                                if (isActive) {
                                    TtsSpeaker.speak("Auto-proceed.", lowVolume = true)
                                    proceed()
                                    return@launch
                                }
                            } else {
                                // TODOJNOW added next task. Make Play file suspending so that we can
                                // play multiple times and stop playback on cancelation.
                                AudioPlayer.instance.suspendPlay(note.question)
                                practiceModeViewModel.hasPlayedCurrentNotePart.value = true
                                delay(2_000)
                                while (!activity.isAtLeastResumed()) {
                                    delay(1000)
                                }
                                // This tone is mostly helpful to avoid the bluetooth speakers being off during
                                // replay.
                                val unused = async { activity.lowVolumePrimeSpeakerTone() }
                                delay(500)
                            }
                        }
                    })
                }.collect {}
            }
        }


        activity.lifecycleScope.launch {
            focusedQueueStateModel.queue.collect {
                val revisionQueue = it ?: return@collect
                originalSize = revisionQueue.getSize()
                lastNoteList.clear()
                // Let's just load up the learn question also to get it ready.
                setupQuestionMode()
            }
        }
    }

    fun handleTapCount(count: Int) {
        activity.handleRhythmUiTaps(
            SystemClock.uptimeMillis(),
            SpacedRepeaterActivity.PRESS_GROUP_MAX_GAP_MS_INSTANT,
            count
        )
    }

     fun undo() {
        if (questionMode) {
            undoFromQuestionToAnswerMode()
        } else {
            undoFromAnswerToQuestion()
        }
    }

    // Used to indicate a answer was not remembered
    fun secondaryAction(): String {
        if (!practiceModeViewModel.hasPlayedCurrentNotePart.value) {
            return ""
        }

        KeepScreenOn.getInstance().keepScreenOn(activity)

        val messageToSpeak = if (questionMode) {
            "secondary action. proceed"
        } else {
            "bad bad"
        }
        if (questionMode) {
            proceedCommon()
        } else {
            updateScoreAndMoveToNext(1)
        }
        return messageToSpeak
    }

    /** Moves this note to the end of the queue.  */
     fun postponeNote(shouldQueue: Boolean) {
        val currentNote = currentNote ?: return

        if (shouldQueue) {
            // Place at end of queue.
            currentDeckReviewQueue!!.updateNote(currentNote, false)
        } else {
            val editor = DbNoteEditor.instance
            val context = activity
            if (editor != null) {
                currentNote.decreasePriority()
                editor.update(currentNote)
            } else {
                TtsSpeaker.speak("Error decreasing priority")
            }

            currentDeckReviewQueue!!.hardPostpone(currentNote)
        }
        // Prepare the next note in the queue.
        setupQuestionMode()
    }

     fun proceed() {
         KeepScreenOn.getInstance().keepScreenOn(activity)
        if (!practiceModeViewModel.hasPlayedCurrentNotePart.value) {
          return
        }
        if (questionMode) {
            proceedCommon()
        } else {
            updateScoreAndMoveToNext(4)
        }
    }

    private fun setupQuestionMode(
        shouldUpdateQuestion: Boolean = true
    ) {
        if (shouldUpdateQuestion) {
            updateStartingInQuestionMode()
        }
        questionMode = true
        val currentNote = currentNote
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
    }

    private val lastOrNull: Note?
        get() = if (!lastNoteList.isEmpty()) lastNoteList.last else null

    private fun setupAnswerMode() {
        questionMode = false
        val currentNote = currentNote
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = true)
    }

    private fun updateStartingInQuestionMode() {
        currentNote = currentDeckReviewQueue!!.peekQueue()
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
        if (currentNote != null) {
            repCounter++
            val metrics = practiceModeViewModel.metricsFlow.value
            practiceModeViewModel.metricsFlow.value = metrics.copy(notesPracticed = metrics.notesPracticed + 1, remainingInQueue = currentDeckReviewQueue?.getSize() ?: 0)
            if (repCounter % 10 == 9) {
                markAllStale(activity!!)
            }
        }
    }

    private fun updateScoreAndMoveToNext(newGrade: Int) {
        if (currentNote != null) {
            applyGradeStatic(newGrade, currentNote!!)
            ToastSingleton.getInstance()
                .msg("Easiness: " + currentNote!!.easiness + " Interval " + currentNote!!.interval)
            setupQuestionMode()
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

    fun deleteNote() {
        val noteEditor = DbNoteEditor.instance
        val note = currentNote
        if (note != null) {
            noteEditor!!.deleteCurrent(activity!!, note)
            currentDeckReviewQueue!!.removeNote(note.id)
            currentNotePartManager.onDelete()
        }
        setupQuestionMode()
    }

    private fun replayA() {
        KeepScreenOn.getInstance().keepScreenOn(activity)
        if (currentNote != null) {
            AudioPlayer.instance.playFile(currentNote!!.answer, null, true)
        }
    }

    private fun replay() {
        KeepScreenOn.getInstance().keepScreenOn(activity)
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

            val metrics = practiceModeViewModel.metricsFlow.value
            practiceModeViewModel.metricsFlow.value = metrics.copy(notesPracticed = metrics.notesPracticed - 1, remainingInQueue = currentDeckReviewQueue?.getSize() ?: 0)

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

    private fun undoFromAnswerToQuestion() {
        setupQuestionMode(false)
    }

     fun adjustScreenLock() {
        KeepScreenOn.getInstance().keepScreenOn(activity)
       // hideSystemUi()
    }

     fun mark() {
        if (currentNote != null) {
            currentNote!!.isMarked = true
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote!!)
            ToastSingleton.getInstance().msg("Marked note")
        }
    }

}