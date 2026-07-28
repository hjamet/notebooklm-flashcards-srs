package com.notebooklm.flashcards.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Direct parsed card from CSV export.
 */
data class CsvCard(
    val id: String,
    val question: String,
    val answer: String,
    val deckName: String,
    val context: String? = null
)

/**
 * Represents card state in FSRS algorithm.
 * 0: New, 1: Learning, 2: Review, 3: Relearning
 */
object CardState {
    const val NEW = 0
    const val LEARNING = 1
    const val REVIEW = 2
    const val RELEARNING = 3
}

/**
 * Rating selection for SRS review.
 */
enum class Rating(val value: Int) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4)
}

/**
 * Represents card SRS record saved in flashcards-srs-data.json
 */
@Serializable
data class SrsCardRecord(
    val id: String,
    val deck: String,
    val question: String,
    val answer: String,
    val context: String? = null,
    val state: Int = CardState.NEW,
    val due: String, // ISO-8601 string
    val stability: Double = 0.4,
    val difficulty: Double = 5.0,
    @SerialName("elapsed_days") val elapsedDays: Long = 0,
    @SerialName("scheduled_days") val scheduledDays: Long = 0,
    val reps: Int = 0,
    val lapses: Int = 0,
    @SerialName("last_review") val lastReview: String? = null
)

@Serializable
data class DeckMetaData(
    @SerialName("source_path") val sourcePath: String,
    @SerialName("markdown_path") val markdownPath: String? = null,
    @SerialName("card_count") val cardCount: Int = 0
)

@Serializable
data class SrsDatabase(
    val version: Int = 1,
    @SerialName("last_updated") val lastUpdated: String = "",
    val decks: Map<String, DeckMetaData> = emptyMap(),
    val cards: Map<String, SrsCardRecord> = emptyMap()
)

data class DeckSummary(
    val deckName: String,
    val totalCards: Int,
    val newCards: Int,
    val dueCards: Int,
    val sourcePath: String
)
