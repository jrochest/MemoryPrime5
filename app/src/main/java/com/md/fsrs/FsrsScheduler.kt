package com.md.fsrs

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Pure-Kotlin implementation of FSRS-5 (Free Spaced Repetition Scheduler).
 *
 * Reference: https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 */
object FsrsScheduler {

    /** FSRS-5 default parameters (w0..w18). */
    private val W = doubleArrayOf(
        0.4072,   // w0:  initial stability for Again
        1.1829,   // w1:  initial stability for Hard
        3.1262,   // w2:  initial stability for Good
        15.4722,  // w3:  initial stability for Easy
        7.2102,   // w4:  difficulty baseline
        0.5316,   // w5:  difficulty revision factor
        1.0651,   // w6:  difficulty mean reversion strength
        0.0046,   // w7:  stability increase factor
        1.5418,   // w8:  stability increase exponent (S)
        0.1618,   // w9:  stability increase exponent (D)
        1.0190,   // w10: retrievability decay
        1.9395,   // w11: stability increase exponent (R)
        0.1100,   // w12: forget stability factor
        0.2900,   // w13: forget difficulty factor
        2.2700,   // w14: forget retrievability factor
        0.2315,   // w15: hard penalty
        2.9898,   // w16: easy bonus
        0.5100,   // w17: short-term stability factor
        0.6000,   // w18: short-term stability exponent
    )

    /** Default desired retention probability. */
    const val DEFAULT_RETENTION: Double = 0.9

    /** FSRS card states. */
    enum class CardState(val value: Int) {
        New(0),
        Learning(1),
        Review(2),
        Relearning(3);

        companion object {
            fun fromInt(v: Int): CardState = entries.first { it.value == v }
        }
    }

    /** FSRS rating (maps to user actions). */
    enum class Rating(val value: Int) {
        Again(1),
        Hard(2),
        Good(3),
        Easy(4);

        companion object {
            /**
             * Convert SM-2 grade (1,2,4,5) to FSRS Rating.
             * Grade 0,3 are not used in the current click scheme.
             */
            fun fromSm2Grade(grade: Int): Rating = when (grade) {
                0, 1 -> Again
                2 -> Hard
                3, 4 -> Good
                5 -> Easy
                else -> Good
            }
        }
    }

    /** Immutable FSRS card state. */
    data class FsrsState(
        val stability: Double,
        val difficulty: Double,
        val state: CardState,
    )

    // ─── Core Algorithm ────────────────────────────────────────────────

    /**
     * Compute the next FSRS state after a review.
     *
     * @param current Current card state (or null for a new card).
     * @param rating  The user rating.
     * @param elapsedDays Days since last review (0 for first review).
     */
    fun nextState(current: FsrsState?, rating: Rating, elapsedDays: Double): FsrsState {
        if (current == null || current.state == CardState.New) {
            return firstReview(rating)
        }

        return when (current.state) {
            CardState.Learning, CardState.Relearning ->
                shortTermReview(current, rating)
            CardState.Review ->
                longTermReview(current, rating, elapsedDays)
            else -> firstReview(rating)
        }
    }

    /**
     * Compute retrievability (probability of recall) given stability and elapsed days.
     *
     * R(t, S) = (1 + FACTOR * t / S) ^ DECAY
     * where FACTOR = 19/81 and DECAY = -0.5 for FSRS-5.
     */
    fun retrievability(stability: Double, elapsedDays: Double): Double {
        if (stability <= 0.0 || elapsedDays <= 0.0) return 1.0
        val factor = 19.0 / 81.0
        val decay = -0.5
        return (1.0 + factor * elapsedDays / stability).pow(decay)
    }

    /**
     * Compute the next interval from stability and desired retention.
     *
     * interval = S / FACTOR * (R^(1/DECAY) - 1)
     */
    fun nextInterval(stability: Double, desiredRetention: Double = DEFAULT_RETENTION): Int {
        val factor = 19.0 / 81.0
        val decay = -0.5
        val interval = stability / factor * (desiredRetention.pow(1.0 / decay) - 1.0)
        return max(1, interval.roundToInt())
    }

    // ─── Internal ──────────────────────────────────────────────────────

    /** First review: initialize stability and difficulty from the rating. */
    private fun firstReview(rating: Rating): FsrsState {
        val s = initialStability(rating)
        val d = initialDifficulty(rating)
        val state = if (rating == Rating.Again) CardState.Learning else CardState.Review
        return FsrsState(stability = s, difficulty = d, state = state)
    }

    /** Short-term review (Learning/Relearning states). */
    private fun shortTermReview(current: FsrsState, rating: Rating): FsrsState {
        val newD = nextDifficulty(current.difficulty, rating)
        val newS = if (rating == Rating.Again) {
            // Reset stability on fail
            current.stability * W[17]
        } else {
            // Short-term stability update
            current.stability * exp(W[17] * (rating.value - 3 + W[18]))
        }
        val newState = when {
            rating == Rating.Again -> CardState.Relearning
            current.state == CardState.Learning && rating.value >= Rating.Good.value -> CardState.Review
            current.state == CardState.Relearning && rating.value >= Rating.Good.value -> CardState.Review
            else -> current.state
        }
        return FsrsState(stability = max(0.01, newS), difficulty = newD, state = newState)
    }

    /** Long-term review (Review state). */
    private fun longTermReview(current: FsrsState, rating: Rating, elapsedDays: Double): FsrsState {
        val r = retrievability(current.stability, elapsedDays)
        val newD = nextDifficulty(current.difficulty, rating)

        val newS: Double
        val newState: CardState

        if (rating == Rating.Again) {
            // Lapse: compute forget stability
            newS = forgetStability(current.difficulty, current.stability, r)
            newState = CardState.Relearning
        } else {
            // Recall: compute recall stability
            newS = recallStability(current.difficulty, current.stability, r, rating)
            newState = CardState.Review
        }

        return FsrsState(stability = max(0.01, newS), difficulty = newD, state = newState)
    }

    /** S_0(G): Initial stability based on first rating. */
    private fun initialStability(rating: Rating): Double {
        return W[rating.value - 1]
    }

    /** D_0(G): Initial difficulty based on first rating. */
    private fun initialDifficulty(rating: Rating): Double {
        return clampDifficulty(W[4] - exp(W[5] * (rating.value - 1)) + 1)
    }

    /**
     * D'(D, G): Next difficulty after a review.
     * Applies mean reversion toward D_0(G).
     */
    private fun nextDifficulty(d: Double, rating: Rating): Double {
        val deltaD = -W[6] * (rating.value - 3)
        val newD = d + deltaD
        // Mean reversion
        val d0 = initialDifficulty(Rating.Good)
        val reverted = W[7] * d0 + (1 - W[7]) * newD
        return clampDifficulty(reverted)
    }

    /**
     * S'_r(D, S, R, G): Stability after successful recall.
     */
    private fun recallStability(d: Double, s: Double, r: Double, rating: Rating): Double {
        val hardPenalty = if (rating == Rating.Hard) W[15] else 1.0
        val easyBonus = if (rating == Rating.Easy) W[16] else 1.0
        return s * (1 + exp(W[8]) *
                (11 - d) *
                s.pow(-W[9]) *
                (exp((1 - r) * W[10]) - 1) *
                hardPenalty *
                easyBonus)
    }

    /**
     * S'_f(D, S, R): Stability after a lapse (forget).
     */
    private fun forgetStability(d: Double, s: Double, r: Double): Double {
        return W[11] * d.pow(-W[12]) * ((s + 1).pow(W[13]) - 1) * exp((1 - r) * W[14])
    }

    private fun clampDifficulty(d: Double): Double = max(1.0, min(10.0, d))
}
