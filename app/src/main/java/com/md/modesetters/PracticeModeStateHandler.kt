package com.md.modesetters

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.md.AudioPlayer
import com.md.CategorySingleton
import com.md.DbNoteEditor
import com.md.DbRepEditor
import com.md.FocusedQueueStateModel
import com.md.RevisionQueue.Companion.currentDeckReviewQueueDeleteThisTODOJSOON
import com.md.SpacedRepeaterActivity
import com.md.application.MainDispatcher
import com.md.composeModes.CurrentNotePartManager
import com.md.composeModes.Mode
import com.md.composeModes.PracticeMode
import com.md.composeModes.PracticeModeViewModel
import com.md.isAtLeastResumed
import com.md.provider.AbstractRep
import com.md.provider.Note
import com.md.utils.ToastSingleton
import com.md.viewmodel.TopModeFlowProvider
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.Deque
import javax.inject.Inject


@ActivityScoped
class PracticeModeStateHandler @Inject constructor(
    val currentNotePartManager: CurrentNotePartManager,
    @ActivityContext val context: Context,
    private val focusedQueueStateModel: FocusedQueueStateModel,
    private val modeFlowProvider: TopModeFlowProvider,
    private val practiceModeViewModel: PracticeModeViewModel,
    private val deckLoadManager: DeckLoadManager,
    private val audioPlayer: AudioPlayer,
    @MainDispatcher private val mainDispatcher: CoroutineDispatcher,
) {
    val activity: SpacedRepeaterActivity = context as SpacedRepeaterActivity

    private val lastNoteList: Deque<Note> = ArrayDeque()
    private var lastNoteRep: AbstractRep? = null

    private var originalSize = 0
    var repCounter = 0
    private var missCounter = 0

    fun onSwitchToMode() {
        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                combine(
                    flow = modeFlowProvider.modeModel,
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
                    MoveManager.replaceMoveJobWith(activity.lifecycleScope.launch(mainDispatcher) {
                        while (isActive) {
                            while (!activity.isAtLeastResumed()) {
                                delay(1000)
                            }
                            val noteState = currentNotePartManager.noteStateFlow.value ?: return@launch

                            val part = noteState.notePart
                            val note = checkNotNull(noteState.currentNote)
                            if (part.partIsAnswer) {
                                audioPlayer.suspendPlayReturningTrueIfLoadedFileChanged(
                                    note.answer
                                )
                            } else {
                                val firstPlay =
                                    audioPlayer.suspendPlayReturningTrueIfLoadedFileChanged(
                                        note.question
                                    )
                                if (firstPlay) {
                                    // Play twice without a break the first time.
                                    audioPlayer.suspendPlayReturningTrueIfLoadedFileChanged(
                                        note.question
                                    )
                                }
                            }
                            practiceModeViewModel.hasPlayedCurrentNotePartOrIgnoredAProceed.value =
                                true

                            // Preload in parallel while the delay occurs.
                            activity.lifecycleScope.launch(mainDispatcher) {
                                deckInfo.revisionQueue.preload()
                            }
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
        val noteState = currentNotePartManager.noteStateFlow.value ?: return
        if (noteState.notePart.partIsAnswer) {
            undoFromAnswerToQuestion()
        } else {
            undoFromQuestionToAnswerMode()
        }
    }

    // Used to indicate an answer was not remembered or proceed for a question.
    fun secondaryAction() {
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
        handlePracticeNoteState(noFocusedNoteHandler = {
            // Do nothing.
        }, focusedNoteExistsHandler = { noteState ->
            val currentNote = noteState.currentNote
            if (shouldQueue) {
                // Place at end of queue.
                currentDeckReviewQueueDeleteThisTODOJSOON!!.updateNote(currentNote, false)
            } else {
                val editor = DbNoteEditor.instance
                if (editor != null) {
                    currentNote.decreasePriority()
                    editor.update(currentNote)
                } else {
                    TtsSpeaker.speak("Error decreasing priority")
                }
                currentDeckReviewQueueDeleteThisTODOJSOON!!.hardPostpone(currentNote)
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
            currentNotePartManager.changeToQuestionForCurrent()
        }
    }

    private val lastOrNull: Note?
        get() = if (!lastNoteList.isEmpty()) lastNoteList.last else null

    private fun setupAnswerMode() {
        currentNotePartManager.changeToAnswerForCurrent()
    }

    private fun updateStartingInQuestionMode() {
        val currentNote = currentDeckReviewQueueDeleteThisTODOJSOON!!.peekQueue()
        currentNotePartManager.changeCurrentNotePart(currentNote, partIsAnswer = false)
        if (currentNote != null) {
            val metrics = practiceModeViewModel.metricsFlow.value
            practiceModeViewModel.metricsFlow.value = metrics.copy(
                notesPracticed = metrics.notesPracticed + 1,
                remainingInQueue = currentDeckReviewQueueDeleteThisTODOJSOON?.getSize() ?: 0
            )
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
            currentDeckReviewQueueDeleteThisTODOJSOON!!.updateNote(currentNote, false)
            missCounter++
        } else {
            currentDeckReviewQueueDeleteThisTODOJSOON!!.removeNote(currentNote.id)
        }
    }

    fun deleteNote() {
        handlePracticeNoteState {
            noteState ->
            val noteEditor = DbNoteEditor.instance
            val note = noteState.currentNote
            noteEditor!!.deleteCurrent(activity, note)
            currentDeckReviewQueueDeleteThisTODOJSOON!!.removeNote(note.id)
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
                remainingInQueue = currentDeckReviewQueueDeleteThisTODOJSOON?.getSize() ?: 0
            )

            val noteEditor = DbNoteEditor.instance
            noteEditor!!.update(currentNote)
            // In case the grade was bad take it out of revision queue.
            currentDeckReviewQueueDeleteThisTODOJSOON!!.removeNote(currentNote.id)
            currentDeckReviewQueueDeleteThisTODOJSOON!!.addToFront(currentNote)
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