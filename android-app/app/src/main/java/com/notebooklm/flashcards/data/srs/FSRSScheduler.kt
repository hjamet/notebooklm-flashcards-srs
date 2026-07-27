package com.notebooklm.flashcards.data.srs

import com.notebooklm.flashcards.data.model.CardState
import com.notebooklm.flashcards.data.model.Rating
import com.notebooklm.flashcards.data.model.SrsCardRecord
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * FSRS v4 algorithm scheduler for Kotlin Android.
 * Compatible with ts-fsrs specs.
 */
object FSRSScheduler {

    // Default FSRS v4 parameters
    private val w = doubleArrayOf(
        0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49,
        0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61
    )

    private const val REQUEST_RETENTION = 0.9

    data class SchedulingResult(
        val card: SrsCardRecord,
        val intervalText: String
    )

    data class IntervalPreview(
        val again: String,
        val hard: String,
        val good: String,
        val easy: String
    )

    /**
     * Preview upcoming review intervals for all 4 ratings.
     */
    fun previewIntervals(card: SrsCardRecord, now: Instant = Instant.now()): IntervalPreview {
        val againRes = schedule(card, Rating.AGAIN, now)
        val hardRes = schedule(card, Rating.HARD, now)
        val goodRes = schedule(card, Rating.GOOD, now)
        val easyRes = schedule(card, Rating.EASY, now)

        return IntervalPreview(
            again = againRes.intervalText,
            hard = hardRes.intervalText,
            good = goodRes.intervalText,
            easy = easyRes.intervalText
        )
    }

    /**
     * Process review for card with given rating.
     */
    fun schedule(
        card: SrsCardRecord,
        rating: Rating,
        now: Instant = Instant.now()
    ): SchedulingResult {
        val lastReviewInstant = parseIsoDate(card.lastReview) ?: now
        val elapsedDays = max(0L, ChronoUnit.DAYS.between(lastReviewInstant, now))
        
        var newStability = card.stability
        var newDifficulty = card.difficulty
        var newState = card.state
        var newReps = card.reps + 1
        var newLapses = card.lapses
        var scheduledDays = 0L
        var nextDueInstant: Instant

        if (card.state == CardState.NEW) {
            // First time reviewing this card
            newStability = initStability(rating.value)
            newDifficulty = initDifficulty(rating.value)

            when (rating) {
                Rating.AGAIN -> {
                    newState = CardState.LEARNING
                    nextDueInstant = now.plus(1, ChronoUnit.MINUTES)
                    scheduledDays = 0
                }
                Rating.HARD -> {
                    newState = CardState.LEARNING
                    nextDueInstant = now.plus(5, ChronoUnit.MINUTES)
                    scheduledDays = 0
                }
                Rating.GOOD -> {
                    newState = CardState.REVIEW
                    scheduledDays = calculateInterval(newStability)
                    nextDueInstant = now.plus(scheduledDays, ChronoUnit.DAYS)
                }
                Rating.EASY -> {
                    newState = CardState.REVIEW
                    scheduledDays = max(4L, calculateInterval(newStability) * 2)
                    nextDueInstant = now.plus(scheduledDays, ChronoUnit.DAYS)
                }
            }
        } else {
            // Card was previously in Learning, Review, or Relearning
            val retrievability = calculateRetrievability(elapsedDays, card.stability)
            newDifficulty = nextDifficulty(card.difficulty, rating.value)

            if (rating == Rating.AGAIN) {
                newLapses += 1
                newStability = nextForgetStability(card.difficulty, card.stability, retrievability)
                newState = CardState.RELEARNING
                nextDueInstant = now.plus(10, ChronoUnit.MINUTES)
                scheduledDays = 0
            } else {
                newStability = nextRecallStability(card.difficulty, card.stability, retrievability, rating.value)
                newState = CardState.REVIEW
                val days = calculateInterval(newStability)
                val hardFactor = if (rating == Rating.HARD) 0.8 else 1.0
                scheduledDays = max(1L, (days * hardFactor).roundToLong())
                nextDueInstant = now.plus(scheduledDays, ChronoUnit.DAYS)
            }
        }

        val formattedDue = formatIsoDate(nextDueInstant)
        val formattedLastReview = formatIsoDate(now)
        val intervalText = formatIntervalText(now, nextDueInstant)

        val updatedCard = card.copy(
            state = newState,
            due = formattedDue,
            stability = (newStability * 100.0).roundToLong() / 100.0,
            difficulty = (newDifficulty * 100.0).roundToLong() / 100.0,
            elapsedDays = elapsedDays,
            scheduledDays = scheduledDays,
            reps = newReps,
            lapses = newLapses,
            lastReview = formattedLastReview
        )

        return SchedulingResult(updatedCard, intervalText)
    }

    private fun initStability(rating: Int): Double {
        return max(0.1, w[min(rating - 1, 3)])
    }

    private fun initDifficulty(rating: Int): Double {
        val d0 = w[4] - (rating - 3) * w[5]
        return d0.coerceIn(1.0, 10.0)
    }

    private fun nextDifficulty(d: Double, rating: Int): Double {
        val d0G = initDifficulty(Rating.GOOD.value)
        val nextD = w[7] * d0G + (1.0 - w[7]) * (d - w[6] * (rating - 3))
        return nextD.coerceIn(1.0, 10.0)
    }

    private fun calculateRetrievability(elapsedDays: Long, stability: Double): Double {
        if (stability <= 0.0) return 0.0
        val factor = 0.9.pow(-1.0 / w[15]) - 1.0
        return (1.0 + factor * (elapsedDays / stability)).pow(-1.0)
    }

    private fun nextRecallStability(d: Double, s: Double, r: Double, rating: Int): Double {
        val hardPenalty = if (rating == Rating.HARD.value) w[15] else 1.0
        val easyBonus = if (rating == Rating.EASY.value) w[16] else 1.0
        val sRecall = s * (1.0 + exp(w[8]) * (11.0 - d) * s.pow(-w[9]) * (exp(w[10] * (1.0 - r)) - 1.0) * hardPenalty * easyBonus)
        return max(0.1, sRecall)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val sForget = w[11] * d.pow(-w[12]) * ((s + 1.0).pow(w[13]) - 1.0) * exp(w[14] * (1.0 - r))
        return min(sForget, s).coerceAtLeast(0.1)
    }

    private fun calculateInterval(stability: Double): Long {
        val factor = (0.9.pow(-1.0 / w[15]) - 1.0)
        val rawDays = (stability / factor) * (1.0 / REQUEST_RETENTION - 1.0)
        return max(1L, rawDays.roundToLong())
    }

    private fun formatIntervalText(now: Instant, target: Instant): String {
        val totalMinutes = ChronoUnit.MINUTES.between(now, target)
        if (totalMinutes < 60) {
            return "${max(1L, totalMinutes)}m"
        }
        val totalHours = ChronoUnit.HOURS.between(now, target)
        if (totalHours < 24) {
            return "${totalHours}h"
        }
        val days = ChronoUnit.DAYS.between(now, target)
        return "${max(1L, days)}d"
    }

    fun parseIsoDate(isoStr: String?): Instant? {
        if (isoStr.isNull_or_blank()) return null
        return try {
            Instant.parse(isoStr)
        } catch (e: Exception) {
            null
        }
    }

    fun formatIsoDate(instant: Instant): String {
        return DateTimeFormatter.ISO_INSTANT.format(instant.atZone(ZoneOffset.UTC))
    }

    private fun String?.isNull_or_blank(): Boolean {
        return this == null || this.trim().isEmpty()
    }
}
