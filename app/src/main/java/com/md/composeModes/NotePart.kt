package com.md.composeModes

import com.md.AudioRecorder

data class NotePart(
    var partIsAnswer: Boolean = true,
    val updateHasPart: (Boolean) -> Unit,
) {
    var pendingRecorder: AudioRecorder? = null
    var savableRecorder: AudioRecorder? = null
        set(value) {
            updateHasPart(value != null && value.isRecorded)
            field = value
        }

    fun consumeSavableRecorder(): AudioRecorder? {
        val savableRecorder = checkNotNull(savableRecorder)
        this.savableRecorder = null
        return savableRecorder
    }

    fun clearRecordings() {
        pendingRecorder = null
        savableRecorder = null
    }

    val name: String = if (partIsAnswer) "answer" else "question"
}