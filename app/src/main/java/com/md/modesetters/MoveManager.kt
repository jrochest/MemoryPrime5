package com.md.modesetters

import android.os.SystemClock
import kotlinx.coroutines.Job

object MoveManager {

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