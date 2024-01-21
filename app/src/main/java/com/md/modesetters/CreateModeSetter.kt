package com.md.modesetters

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.View.OnTouchListener
import android.widget.Button
import com.md.*
import com.md.RevisionQueue.Companion.currentDeckReviewQueueDeleteThisTODO
import com.md.provider.Note
import java.io.IOException

@SuppressLint("StaticFieldLeak")
object CreateModeSetter : ModeSetter() {
    private var question: AudioRecorder? = null
    private var answer: AudioRecorder? = null

    /**
     * @param memoryDroid
     * @param modeHand
     */
    fun setUp(
        memoryDroid: Activity?,
        modeHand: ModeHandler?
    ) {
        parentSetup(memoryDroid, modeHand)
    }

    override fun onSwitchToMode(context: Activity) {
        commonSetup(context, R.layout.creation)
        setupCreateMode(context)
    }

    fun setupCreateMode(memoryDroid: Activity) {
        val res = memoryDroid.resources
        stopImage = res.getDrawable(R.drawable.stop)
        recordImage = res.getDrawable(R.drawable.record)
        recordImageGrey = res.getDrawable(R.drawable.greyrecord)
        playImage = res.getDrawable(R.drawable.play)
        playImageGrey = res.getDrawable(R.drawable.greyplay)
        saveImage = res.getDrawable(R.drawable.save)
        saveImageGrey = res.getDrawable(R.drawable.greysave)
        refreshImage = res.getDrawable(R.drawable.refresh)
        refreshImageGrey = res.getDrawable(R.drawable.greyrefresh)
        recordQAButton[QUESTION_INDEX] = memoryDroid
            .findViewById<View>(R.id.recordQuestionButton) as Button
        recordQAButton[ANSWER_INDEX] = memoryDroid
            .findViewById<View>(R.id.recordAnswerButton) as Button
        playQAButton[ANSWER_INDEX] = memoryDroid
            .findViewById<View>(R.id.playAnswerButton) as Button
        playQAButton[QUESTION_INDEX] = memoryDroid
            .findViewById<View>(R.id.playQuestionButton) as Button
        saveButton = memoryDroid.findViewById<View>(R.id.saveQAButton) as Button
        resetButton = memoryDroid.findViewById<View>(R.id.restartQaButton) as Button
        setupRecordButton(memoryDroid, QUESTION_INDEX)
        setupRecordButton(memoryDroid, ANSWER_INDEX)
        setupPlayButton(memoryDroid, ANSWER_INDEX)
        setupPlayButton(memoryDroid, QUESTION_INDEX)
        setupSaveButton(memoryDroid, saveButton)
        setupResetButton(memoryDroid, resetButton)
        updateState()
    }

    fun restartCreateMode(deleteLastMp3s: Boolean) {

        // We're updating an existing note, no restarts allowed.
        if (note == null) {
            val createMode = CreateModeData.getInstance()
            createMode.clearState()
            if (answer != null) {
                if (deleteLastMp3s) {
                    answer!!.deleteFile()
                }
                answer = null
            }
            if (question != null) {
                if (deleteLastMp3s) {
                    question!!.deleteFile()
                }
                question = null
            }
        }
    }

    fun updateState() {
        val createMode = CreateModeData.getInstance()
        if (qaUpdateState(createMode, QUESTION_INDEX)) {
            return
        }
        if (qaUpdateState(createMode, ANSWER_INDEX)) {
            return
        }

        // Update mode
        if (note != null) {
            saveButton!!.text = "Go Back"
        } else {
            saveButton!!.text = "Save Note"
        }

        // Save button
        if (createMode.getQuestionState(ANSWER_INDEX) == CreateModeData.State.RECORDED
            && createMode.getQuestionState(QUESTION_INDEX) == CreateModeData.State.RECORDED
        ) {
            enableSave()
        } else {
            disableSave()
        }

        // If one of them things has been recorded and we are not in
        // note update mode.
        if ((createMode.getQuestionState(ANSWER_INDEX) != CreateModeData.State.BLANK || createMode
                .getQuestionState(QUESTION_INDEX) != CreateModeData.State.BLANK)
            && note == null
        ) {
            resetEnable()
        } else {
            resetDisable()
        }
    }

    private fun qaUpdateState(createMode: CreateModeData, currentIndex: Int): Boolean {
        if (createMode.getQuestionState(currentIndex) == CreateModeData.State.PLAYING) {
            disableAllOthers()
            qPlayEnableReadyToStop(currentIndex)
            return true
        } else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.BLANK) {
            qaPlayDisable(currentIndex)
            qRecorderEnableRecord(currentIndex)
        } else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.RECORDED) {
            qaPlayEnableReadyToPlay(currentIndex)
            qRecorderEnableRecordAgain(currentIndex)
        } else if (createMode.getQuestionState(currentIndex) == CreateModeData.State.RECORDING) {
            disableAllOthers()
            qRecorderEnableRecording(currentIndex)
            return true
        }
        return false
    }

    private fun resetEnable() {
        resetButton!!.isEnabled = true
        resetButton!!.setCompoundDrawablesWithIntrinsicBounds(
            refreshImage, null,
            null, null
        )
    }

    private fun resetDisable() {
        resetButton!!.isEnabled = false
        resetButton!!.setCompoundDrawablesWithIntrinsicBounds(
            refreshImageGrey,
            null, null, null
        )
    }

    private fun disableAllOthers() {
        qRecorderDisable(QUESTION_INDEX)
        qaPlayDisable(QUESTION_INDEX)
        qRecorderDisable(ANSWER_INDEX)
        qaPlayDisable(ANSWER_INDEX)
        disableSave()
        resetDisable()
    }

    private fun disableSave() {
        saveButton!!.isEnabled = false
        saveButton!!.setCompoundDrawablesWithIntrinsicBounds(
            saveImageGrey, null,
            null, null
        )
    }

    private fun enableSave() {
        saveButton!!.isEnabled = true
        saveButton!!.setCompoundDrawablesWithIntrinsicBounds(
            saveImage, null,
            null, null
        )
    }

    private fun setupSaveButton(
        memoryDroid: Activity,
        saveButton: Button?
    ) {
        saveButton!!.setOnClickListener {
            val currentDeckReviewQueue = currentDeckReviewQueueDeleteThisTODO
            if (currentDeckReviewQueue == null) {
                TtsSpeaker.error("No revision queue. Make and or select a deck.")
                return@setOnClickListener
            }

            // Create note
            if (note == null) {
                val noteEditor = DbNoteEditor.instance
                var note = Note(
                    CreateModeData.getInstance()
                        .getQuestion(QUESTION_INDEX),
                    CreateModeData.getInstance().getQuestion(
                        ANSWER_INDEX
                    )
                )
                Log.v(TAG, "Writing node with ID  " + note.isUnseen)
                note = noteEditor!!.insert(note) as Note

                // Add new note.
                currentDeckReviewQueue.add(note)
                Log.v(TAG, "Wrote node with ID  " + note.isUnseen)
                restartCreateMode(false)
                updateState()
            } else  // Update mode.
            {
                // So what we restart back to the initial state.
                note = null
                BrowsingModeSetter.getInstance().switchMode(memoryDroid)
            }
        }
    }

    private fun setupResetButton(
        memoryDroid: Activity,
        resetButton: Button?
    ) {
        resetButton!!.setOnClickListener {
            restartCreateMode(true)
            updateState()
        }
    }

    private fun setupPlayButton(memoryDroid: Activity, qaIndex: Int) {
        playQAButton[qaIndex]!!.setOnClickListener {
            val createMode = CreateModeData.getInstance()
            moveToDonePlayingOrPlaying(qaIndex, createMode, false)
        }
    }

    fun moveToDonePlayingOrPlaying(
        qaIndex: Int,
        createMode: CreateModeData, callbackMakeStateRecording: Boolean
    ) {
        val questionState = createMode.getQuestionState(qaIndex)

        // We don't want to change back to playing if the media player and
        // the user both stop the playing (because it's over and a button press)
        if (questionState == CreateModeData.State.PLAYING
            || callbackMakeStateRecording
        ) {
            createMode.setQuestionState(CreateModeData.State.RECORDED, qaIndex)
        } else if (questionState == CreateModeData.State.RECORDED) {
            createMode.setQuestionState(CreateModeData.State.PLAYING, qaIndex)
        }
        updateState()
    }

    private val PLAY = "Play "
    private val STOP = "Stop\nPlaying "
    private val FIELD_NAMES = arrayOf("\nQuestion", "\nAnswer")
    fun qaPlayDisable(currentIndex: Int) {
        playQAButton[currentIndex]!!.isEnabled = false
        playQAButton[currentIndex]!!.text =
            PLAY + FIELD_NAMES[currentIndex]
        playQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            playImageGrey, null, null, null
        )
    }

    fun qaPlayEnableReadyToPlay(currentIndex: Int) {
        playQAButton[currentIndex]!!.isEnabled = true
        playQAButton[currentIndex]!!.text =
            PLAY + FIELD_NAMES[currentIndex]
        playQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            playImage, null, null, null
        )
    }

    fun qPlayEnableReadyToStop(currentIndex: Int) {
        playQAButton[currentIndex]!!.isEnabled = true
        playQAButton[currentIndex]!!.text = STOP
        playQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            stopImage, null, null, null
        )
        if (currentIndex == ANSWER_INDEX) {
            //answer!!.playFile(this, currentIndex)
        } else {
           // question!!.playFile(this, currentIndex)
        }
    }

    val norecording = "Record "
    val recording = "Stop Recording"
    val rerecording = "Rerecord "
    private var note: Note? = null
    fun qRecorderEnableRecord(currentIndex: Int) {
        recordQAButton[currentIndex]!!.isEnabled = true
        recordQAButton[currentIndex]!!.text = (norecording
                + FIELD_NAMES[currentIndex])
        recordQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            recordImage, null, null, null
        )
    }

    fun qRecorderEnableRecording(currentIndex: Int) {
        recordQAButton[currentIndex]!!.isEnabled = true
        recordQAButton[currentIndex]!!.text = recording
        recordQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            stopImage, null, null, null
        )
    }

    fun qRecorderEnableRecordAgain(currentIndex: Int) {
        recordQAButton[currentIndex]!!.isEnabled = true
        recordQAButton[currentIndex]!!.text = (rerecording
                + FIELD_NAMES[currentIndex])
        recordQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            recordImage, null, null, null
        )
    }

    fun qRecorderDisable(currentIndex: Int) {
        recordQAButton[currentIndex]!!.isEnabled = false
        recordQAButton[currentIndex]!!.setCompoundDrawablesWithIntrinsicBounds(
            recordImageGrey, null, null, null
        )
    }

    private fun setupRecordButton(
        memoryDroid: Activity,
        questionIndex: Int
    ) {
        checkNotNull(recordQAButton[questionIndex])
        recordQAButton[questionIndex]
            ?.setOnTouchListener(object : OnTouchListener {
                override fun onTouch(v: View, event: MotionEvent): Boolean {
                    val createData = CreateModeData
                        .getInstance()
                    return when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            if (createData.getQuestionState(questionIndex) == CreateModeData.State.BLANK) {
                                startRecordingWhole(questionIndex, createData)
                            } else if (createData
                                    .getQuestionState(questionIndex) == CreateModeData.State.RECORDED
                            ) {
                                startNewRecordingAndEraseOld(
                                    questionIndex,
                                    createData
                                )
                            }
                            updateState()
                            true
                        }
                        MotionEvent.ACTION_UP -> {
                            if (createData.getQuestionState(questionIndex) == CreateModeData.State.RECORDING) {
                                stopRecording(questionIndex, createData)
                            }
                            updateState()
                            true
                        }
                        else -> false
                    }
                }

                private fun startRecordingWhole(
                    questionIndex: Int,
                    createData: CreateModeData
                ) {
                    startRecording(questionIndex)
                    createData.setQuestionState(
                        CreateModeData.State.RECORDING, questionIndex
                    )
                }

                private fun startNewRecordingAndEraseOld(
                    questionIndex: Int, createData: CreateModeData
                ) {
                    if (questionIndex == ANSWER_INDEX) {
                        answer!!.deleteFile()
                        answer = null
                    } else {
                        question!!.deleteFile()
                        question = null
                    }
                    startRecordingWhole(questionIndex, createData)
                }

                private fun stopRecording(
                    questionIndex: Int,
                    createData: CreateModeData
                ) {
                    createData.setQuestionState(
                        CreateModeData.State.RECORDED, questionIndex
                    )
                    if (questionIndex == ANSWER_INDEX) {
                        handleStopRecording(note, answer, questionIndex, memoryDroid)
                    } else {
                        handleStopRecording(note, question, questionIndex, memoryDroid)
                    }
                }

                private fun startRecording(questionIndex: Int) {
                    if (questionIndex == ANSWER_INDEX) {
                        answer = AudioRecorder()
                        try {
                            answer!!.start()
                        } catch (e: IOException) {
                            // TODO Auto-generated catch block
                            e.printStackTrace()
                        }
                    } else {
                        question = AudioRecorder()
                        try {
                            question!!.start()
                        } catch (e: IOException) {
                            // TODO Auto-generated catch block
                            e.printStackTrace()
                        }
                    }
                }
            })
    }

    private fun handleStopRecording(
        note: Note?,
        recorder: AudioRecorder?,
        questionIndex: Int,
        memoryDroid: Activity
    ) {
        try {
            recorder!!.stop()
            if (!recorder.isRecorded) {
                return
            }
            CreateModeData.getInstance().setAudioFile(
                recorder.originalFile, questionIndex
            )
            if (note != null) {
                if (questionIndex == ANSWER_INDEX) {
                    note.answer = recorder.originalFile
                } else {
                    note.question = recorder.originalFile
                }
                DbNoteEditor.instance!!.update(
                    note
                )
            }
        } catch (e: IOException) {
            // TODO Auto-generated catch block
            e.printStackTrace()
        }
    }

    fun setNote(note: Note?) {
        this.note = note
        if (note != null) {
            answer = AudioRecorder(note.answer)
            question = AudioRecorder(note.question)
            CreateModeData.getInstance().setQuestionState(
                CreateModeData.State.RECORDED, QUESTION_INDEX
            )
            CreateModeData.getInstance().setQuestionState(
                CreateModeData.State.RECORDED, ANSWER_INDEX
            )
        } else {
            CreateModeData.getInstance().clearState()
            answer = null
            question = null
        }
    }

        const val QUESTION_INDEX = 0
        const val ANSWER_INDEX = 1
        private val recordQAButton = arrayOfNulls<Button>(ANSWER_INDEX + 1)
        private val playQAButton = arrayOfNulls<Button>(ANSWER_INDEX + 1)
        private var saveButton: Button? = null
        private var resetButton: Button? = null
        private var stopImage: Drawable? = null
        private var recordImage: Drawable? = null
        private var recordImageGrey: Drawable? = null
        private var playImage: Drawable? = null
        private var playImageGrey: Drawable? = null
        private var saveImage: Drawable? = null
        private var saveImageGrey: Drawable? = null
        private var refreshImage: Drawable? = null
        private var refreshImageGrey: Drawable? = null
        private const val TAG = "CreateModeSetter"

}