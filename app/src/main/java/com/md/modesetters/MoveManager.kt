package com.md.modesetters

import android.os.SystemClock
import kotlinx.coroutines.Job

object MoveManager {

    private var lastProceedTime = 0L

    fun recordQuestionProceed() {
        lastProceedTime = SystemClock.uptimeMillis()
    }

    // Wait enough time between the proceeds to avoid overlapping proceeds to new question.
    fun safeToProceedToNewQuestion() = lastProceedTime + 1500 < SystemClock.uptimeMillis()

    private val currentJobs = mutableListOf<Job>()

    fun replaceMoveJobWith(job: Job) {
        cancelJobs()
        currentJobs.add(job)
    }

    fun cancelJobs() {
        currentJobs.forEach { it.cancel() }
        currentJobs.clear()
    }
}