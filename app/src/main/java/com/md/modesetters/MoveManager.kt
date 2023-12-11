package com.md.modesetters

import android.os.SystemClock
import kotlinx.coroutines.Job

object MoveManager {

    private var lastQuestionProceedTime = 0L

    fun recordQuestionProceed() {
        lastQuestionProceedTime = SystemClock.uptimeMillis()
    }

    private const val MIN_TIME_ON_QUESTION = 5000

    // Only proceed if MIN_TIME_ON_QUESTION has elapsed.
    fun safeToProceedToNewQuestion() = SystemClock.uptimeMillis() > lastQuestionProceedTime + MIN_TIME_ON_QUESTION

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