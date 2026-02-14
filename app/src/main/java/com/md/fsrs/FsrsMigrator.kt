package com.md.fsrs

import com.md.provider.AbstractRep
import com.md.provider.Note

/**
 * Handles one-time migration of existing SM-2 notes to FSRS state.
 */
object FsrsMigrator {

    /**
     * Migrate a single note to FSRS state.
     * Determines the scenario (A=full history, B=partial, C=no history)
     * and applies the appropriate conversion logic.
     *
     * @param note The note to migrate.
     * @param reps Review history records for this note, sorted by timestamp ascending.
     * @return The computed FSRS state.
     */
    fun migrateNote(note: Note, reps: List<AbstractRep>): FsrsScheduler.FsrsState {
        if (reps.isEmpty()) {
            // Scenario C: No history — heuristic fallback
            return mapFromSm2(note)
        }

        val totalRepsInNote = note.acq_reps + note.ret_reps
        val sortedReps = reps.sortedBy { it.timeStampMs }

        if (sortedReps.size >= totalRepsInNote && totalRepsInNote > 0) {
            // Scenario A: Full history — replay through FSRS
            return replayHistory(sortedReps)
        }

        // Scenario B: Partial history — synthetic start then replay
        return partialReplay(note, sortedReps)
    }

    /**
     * Scenario C: Map SM-2 values directly to FSRS state.
     *
     * Stability = current interval (days).
     * Difficulty = max(1, min(10, 11 - 2 × easiness)).
     */
    fun mapFromSm2(note: Note): FsrsScheduler.FsrsState {
        val interval = note.interval
        val stability = if (interval > 0) interval.toDouble() else 1.0
        val difficulty = mapEasinessToDifficulty(note.easiness)

        val state = when {
            note.is_new -> FsrsScheduler.CardState.New
            note.grade < 2 -> FsrsScheduler.CardState.Relearning
            else -> FsrsScheduler.CardState.Review
        }

        return FsrsScheduler.FsrsState(
            stability = stability,
            difficulty = difficulty,
            state = state
        )
    }

    /**
     * Scenario A: Replay full history through the FSRS scheduler.
     */
    private fun replayHistory(reps: List<AbstractRep>): FsrsScheduler.FsrsState {
        var current: FsrsScheduler.FsrsState? = null
        var lastTimestampMs: Long = 0

        for (rep in reps) {
            val elapsedDays = if (lastTimestampMs == 0L) {
                0.0
            } else {
                (rep.timeStampMs - lastTimestampMs).toDouble() / (1000.0 * 3600.0 * 24.0)
            }
            val rating = FsrsScheduler.Rating.fromSm2Grade(rep.score)
            current = FsrsScheduler.nextState(current, rating, elapsedDays)
            lastTimestampMs = rep.timeStampMs
        }

        return current ?: FsrsScheduler.FsrsState(
            stability = 1.0,
            difficulty = 5.0,
            state = FsrsScheduler.CardState.New
        )
    }

    /**
     * Scenario B: Partial history.
     * Create a synthetic initial state from the note's SM-2 values,
     * then replay the available history from that starting point.
     */
    private fun partialReplay(note: Note, reps: List<AbstractRep>): FsrsScheduler.FsrsState {
        // Start with heuristic state as baseline
        var current: FsrsScheduler.FsrsState = mapFromSm2(note)

        // Replay available reps from the synthetic starting point
        var lastTimestampMs: Long = 0
        for (rep in reps) {
            val elapsedDays = if (lastTimestampMs == 0L) {
                // For the first available rep, estimate elapsed from the note's interval
                rep.interval.toDouble().coerceAtLeast(1.0)
            } else {
                (rep.timeStampMs - lastTimestampMs).toDouble() / (1000.0 * 3600.0 * 24.0)
            }
            val rating = FsrsScheduler.Rating.fromSm2Grade(rep.score)
            current = FsrsScheduler.nextState(current, rating, elapsedDays)
            lastTimestampMs = rep.timeStampMs
        }

        return current
    }

    /**
     * Map SM-2 easiness (1.3–3.0+) to FSRS difficulty (1–10).
     * Higher easiness = lower difficulty.
     * D = max(1, min(10, 11 - 2 × easiness))
     */
    private fun mapEasinessToDifficulty(easiness: Float): Double {
        return (11.0 - 2.0 * easiness).coerceIn(1.0, 10.0)
    }
}
