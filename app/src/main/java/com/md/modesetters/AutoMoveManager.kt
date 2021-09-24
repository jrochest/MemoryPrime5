package com.md.modesetters

import android.os.SystemClock
import kotlinx.coroutines.Job

object AutoMoveManager {

    private var lastProceedTime = 0L

    fun recordQuestionProceed() {
        lastProceedTime = SystemClock.uptimeMillis()
    }

    // Wait enough time between the proceeds to avoid overlapping proceeds.
    fun safeToProceedToNewQuestion() = lastProceedTime + 1500 < SystemClock.uptimeMillis()

    private val currentJobs = mutableListOf<Job>()

    fun addJob(job: Job) {
        currentJobs.add(job)
    }

    fun cancelJobs() {
        currentJobs.forEach { it.cancel() }
        currentJobs.clear()
    }
}