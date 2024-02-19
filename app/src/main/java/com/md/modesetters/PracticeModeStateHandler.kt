package com.md.modesetters

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueueDeleteThisTODO
import com.md.provider.AbstractRep
import com.md.provider.Note
import com.md.utils.KeepScreenOn
import com.md.utils.ToastSingleton
import com.md.workers.BackupPreferences.markAllStale
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.Mode
import com.md.composeModes.TopModeViewModel
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
    private val focusedQueueStateModel: FocusedQueueStateModel,
    // TODOJNOW use theses.
    private val model: TopModeViewModel,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val deckLoadManager: DeckLoadManager,
    private val keepScreenOn: KeepScreenOn,
) {
    val activity: SpacedRepeaterActivity = context as SpacedRepeaterActivity

    private val lastNoteList: Deque<Note> = ArrayDeque()
    private var lastNoteRep: AbstractRep? = null

    private var originalSize = 0
    var repCounter = 0
    private var missCounter = 0

    // TODOJSOON delete this upon switching to currentNotePartManager
    private var questionMode = true

    fun onSwitchToMode() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    flow = model.modeModel,
                    flow2 = practiceModeViewModel.practiceStateFlow,
                    flow3 = focusedQueueStateModel.deck,
                    flow4 = currentNotePartManager.noteStateFlow
                ) { mode: Mode, practiceMode: PracticeMode, deckInfo: DeckInfo?,
                    noteState: CurrentNotePartManager.NoteState? ->
                    if (mode != Mode.Practice || practiceMode != PracticeMode.Practicing) {
                        MoveManager.cancelJobs()
                        return@combine
                    }


                    if (deckInfo == null) {
                        // Do nothing until a new deck is loaded, and only upon user action.
                        return@combine
                    }

                    if (deckInfo.revisionQueue.isEmpty()) {
                        TtsSpeaker.speak("Deck done")
                    }

                    if (noteState == null) {
                        // The user tapping proceed will load the next deck and note.
                        return@combine
                    }

                    // Ensure that we don't allow the user to go forward without playing through the
                    // audio file once.
                    practiceModeViewModel.hasPlayedCurrentNotePartOrIgnoredAProceed.value = false
                    MoveManager.replaceMoveJobWith(activity.lifecycleScope.launch(Dispatchers.Main) {
                        while (isActive) {
                            while (!activity.isAtLeastResumed()) {
                                delay(1000)
                            }
                            val part = noteState.notePart
                            val note = checkNotNull(noteState.currentNote)
                            if (part.partIsAnswer) {
                                AudioPlayer.instance.suspendPlayReturningTrueIfLoadedFileChanged(
                                    note.answer
                                )
                            } else {
                                val firstPlay =
                                    AudioPlayer.instance.suspendPlayReturningTrueIfLoadedFileChanged(
                                        note.question
                                    )
                                if (firstPlay) {
                                    // Play twice without a break the first time.
                                    AudioPlayer.instance.suspendPlayReturningTrueIfLoadedFileChanged(
                                        note.question
                                    )
                                }
                            }
                            practiceModeViewModel.hasPlayedCurrentNotePartOrIgnoredAProceed.value =
                                true
                            delay(2_000)
                            while (!activity.isAtLeastResumed()) {
                                delay(1000)
                            }
                            // This tone is mostly helpful to avoid the bluetooth speakers being off during
                            // replay.
                            val unused = async { activity.lowVolumePrimeSpeakerTone() }
                            delay(500)
                        }
                    })
                }.collect {}
            }
        }


        activity.lifecycleScope.launch {
            focusedQueueStateModel.deck.collect {
                val deck = it ?: return@collect
                originalSize = deck.revisionQueue.getSize()
                lastNoteList.clear()
                // Let's just load up the learn question also to get it ready.
                setupQuestionMode()
            }
        }
    }

    fun undo() {
        if (questionMode) {
            undoFromQuestionToAnswerMode()
        } else {
            undoFromAnswerToQuestion()
        }
    }

    // Used to indicate an answer was not remembered or proceed for a question.
    fun secondaryAction() {
        keepScreenOn.keepScreenOn()
        handlePracticeNoteState(noFocusedNoteHandler = {
            TtsSpeaker.speak("single tap to proceed")
        }, focusedNoteExistsHandler = { noteState ->
            if (shouldProceed()) return@handlePracticeNoteState
            if (noteState.notePart.partIsAnswer) {
                TtsSpeaker.speak("bad bad")
                updateScoreAndMoveToNext(1, noteState.currentNote)
            } else {
                setupAnswerMode()
            }
        })
    }

    /** Moves this note to the end of the queue.  */
    fun postponeNote(shouldQueue: Boolean) {
        keepScreenOn.keepScreenOn()
        handlePracticeNoteState(noFocusedNoteHandler = {
            // Do nothing.
        }, focusedNoteExistsHandler = { noteState ->
            val currentNote = noteState.currentNote
            if (shouldQueue) {
                // Place at end of queue.
                currentDeckReviewQueueDeleteThisTODO!!.updateNote(currentNote, false)
            } else {
                val editor = DbNoteEditor.instance
                if (editor != null) {
                    currentNote.decreasePriority()
                    editor.update(currentNote)
                } else {
                    TtsSpeaker.speak("Error decreasing priority")
                }
                currentDeckReviewQueueDeleteThisTODO!!.hardPostpone(currentNote)
            }
            // Prepare the next note in the queue.
            setupQuestionMode()
        })
    }

    private fun handlePracticeNoteState(
        noFocusedNoteHandler: () -> Unit = {},
        focusedNoteExistsHandler: (noteState: CurrentNotePartManager.NoteState) -> Unit) {
        val noteState = currentNotePartManager.noteStateFlow.value
        if (noteState == null) {
            noFocusedNoteHandler()
            return
        }

        focusedNoteExistsHandler(noteState)
    }

    fun proceed() {
        keepScreenOn.keepScreenOn()
        activity.lifecycleScope.launch {
            val focusedDeck = focusedQueueStateModel.deck.value
            val noteState = currentNotePartManager.noteStateFlow.value
            // Note state is null after a delete.
            if (focusedDeck != null && !focusedDeck.revisionQueue.isEmpty() && noteState != null) {
                handlePracticeNoteState(noFocusedNoteHandler = {
                    setupFirstReviewableDeckInQuestionMode()
                }, focusedNoteExistsHandler = { noteState ->
                    if (shouldProceed()) return@handlePracticeNoteState
                    if (noteState.notePart.partIsAnswer) {
                        updateScoreAndMoveToNext(4, noteState.currentNote)
                    } else {
                        setupAnswerMode()
                    }
                })
            } else {
                val nonEmptyDeck = if (focusedDeck == null || focusedDeck.revisionQueue.isEmpty()) {
                    val decks = deckLoadManager.decks.value ?: return@launch
                    val nonEmptyDeckLocal = decks.firstOrNull { deck ->
                        deck.isActive && !deck.revisionQueue.isEmpty()
                    }
                    if (nonEmptyDeckLocal == null) {
                        TtsSpeaker.speak("All decks done.")
                        return@launch
                    }
                    TtsSpeaker.speak("Loading deck " + nonEmptyDeckLocal.name + " items to study " + nonEmptyDeckLocal.revisionQueue.getSize())
                    nonEmptyDeckLocal
                } else {
                    focusedDeck
                }

                val metrics = practiceModeViewModel.metricsFlow.value
                // TODOJSOON Deprecated the size copy and instead use the focused queue
                // as the single source of truth.
                practiceModeViewModel.metricsFlow.value = metrics.copy(
                    remainingInQueue = nonEmptyDeck.revisionQueue.getSize()
                )
                val note = nonEmptyDeck.revisionQueue.peekQueue()
                focusedQueueStateModel.deck.value = nonEmptyDeck
                CategorySingleton.getInstance().setDeckInfo(nonEmptyDeck)
                currentNotePartManager.changeCurrentNotePart(note, partIsAnswer = false)
            }
        }
    }

    private fun shouldProceed(): Boolean {
        if (!practiceModeViewModel.hasPlayedCurrentNotePartOrIgnoredAProceed.value) {
            // Only ignore a single proceed request. To allow the user to override the proceed block
            // or a really long note.
            practiceModeViewModel.hasPlayedCurrentNotePartOrIgnoredAProceed.value = true
            return true
        }
        return false
    }

    private fun setupQuestionMode(
        shouldUpdateQuestion: Boolean = true
    ) {
        handlePracticeNoteState (noFocusedNoteHandler = {
            setupFirstReviewableDeckInQuestionMode()
        }) {
            if (shouldUpdateQuestion) {
                updateStartingInQuestionMode()
            }
            questionMode = true
            currentNotePartManager.changeToQuestionForCurrent()
        }
    }

    private val lastOrNull: Note?
        get() = if (!lastNoteList.isEmpty()) lastNoteList.last else null

    private fun setupAnswerMode() {
        questionMode = false
        currentNotePartManager.changeToAnswerForCurrent()
    }

    private fun updateStartingInQuestionMode() {
        val currentNote = currentDeckReviewQueueDeleteThisTODO!!.peekQueue()
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
        if (currentNote != null) {
            repCounter++
            val metrics = practiceModeViewModel.metricsFlow.value
            practiceModeViewModel.metricsFlow.value = metrics.copy(
                notesPracticed = metrics.notesPracticed + 1,
                remainingInQueue = currentDeckReviewQueueDeleteThisTODO?.getSize() ?: 0
            )
            if (repCounter % 10 == 9) {
                markAllStale(activity)
            }
        }
    }

    private fun updateScoreAndMoveToNext(newGrade: Int, currentNote: Note) {
        applyGradeStatic(newGrade, currentNote)
        ToastSingleton.getInstance()
            .msg("Easiness: " + currentNote.easiness + " Interval " + currentNote!!.interval)
        setupQuestionMode()
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
            currentDeckReviewQueueDeleteThisTODO!!.updateNote(currentNote, false)
            missCounter++
        } else {
            currentDeckReviewQueueDeleteThisTODO!!.removeNote(currentNote.id)
        }
    }

    fun deleteNote() {
        handlePracticeNoteState {
            noteState ->
            val noteEditor = DbNoteEditor.instance
            val note = noteState.currentNote
            noteEditor!!.deleteCurrent(activity, note)
            currentDeckReviewQueueDeleteThisTODO!!.removeNote(note.id)
            currentNotePartManager.onDelete()
            setupQuestionMode()
        }
    }

    private fun setupFirstReviewableDeckInQuestionMode() {
        // focused note being null typically means there's nothing left in the deck.
        // Proceeding should go to the next reviewable deck with note that are wanting ready
        // to review.
        activity.lifecycleScope.launch {
            deckLoadManager.refreshDeckListAndFocusFirstActiveNonemptyQueue()
        }
    }

    private fun proceedToAnswerIfNoteExistsElseNextDeck() {
        setupAnswerMode()
    }

    private fun undoFromQuestionToAnswerMode() {
        if (!lastNoteList.isEmpty()) {
            val currentNote = lastNoteList.removeLast()
            currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = true)

            val metrics = practiceModeViewModel.metricsFlow.value
            practiceModeViewModel.metricsFlow.value = metrics.copy(
                notesPracticed = metrics.notesPracticed - 1,
                remainingInQueue = currentDeckReviewQueueDeleteThisTODO?.getSize() ?: 0
            )

            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote)
            // In case the grade was bad take it out of revision queue.
            currentDeckReviewQueueDeleteThisTODO!!.removeNote(currentNote.id)
            currentDeckReviewQueueDeleteThisTODO!!.addToFront(currentNote)
            setupAnswerMode()
        } else {
            TtsSpeaker.speak("Nothing to undo")
        }
    }

    private fun undoFromAnswerToQuestion() {
        setupQuestionMode(false)
    }

    fun mark() {
        handlePracticeNoteState { noteState ->
            val currentNote = noteState.currentNote
            currentNote.isMarked = true
            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote)
            ToastSingleton.getInstance().msg("Marked note")
        }
    }
}