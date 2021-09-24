package com.md.modesetters

import kotlinx.coroutines.Job

object AutoMoveManager {

    private val currentJobs = mutableListOf<Job>()

    fun addJob(job: Job) {
        currentJobs.add(job)
    }

    fun cancelJobs() {
        currentJobs.forEach { it.cancel() }
        currentJobs.clear()
    }
}