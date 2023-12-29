package com.md.workingMemory

import com.md.AudioRecorder

class NotePart(
    var recorder: AudioRecorder? = null,
    var pendingRecorder: AudioRecorder? = null,
    val updateHasPart: (Boolean) -> Unit,
    val hasPart: () -> Boolean,
    val partIsAnswer: Boolean = true,
) {
    val name: String = if (partIsAnswer) "answer" else "question"
}