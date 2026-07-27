package com.notebooklm.flashcards.data.storage

import com.notebooklm.flashcards.data.model.CardState
import com.notebooklm.flashcards.data.model.DeckMetaData
import com.notebooklm.flashcards.data.model.DeckSummary
import com.notebooklm.flashcards.data.model.Rating
import com.notebooklm.flashcards.data.model.SrsDatabase
import com.notebooklm.flashcards.data.model.SrsCardRecord
import com.notebooklm.flashcards.data.parser.CsvParser
import com.notebooklm.flashcards.data.srs.FSRSScheduler
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

class SrsDataStore(private val storageManager: SafStorageManager) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private var currentDatabase: SrsDatabase = SrsDatabase()

    /**
     * Loads JSON database and scans/merges CSV files in the vault.
     */
    fun syncVault(): SrsDatabase {
        val mode = storageManager.getAccessMode()
        if (mode is StorageAccessMode.None) {
            return currentDatabase
        }

        // 1. Load existing json database if present
        val rawJson = storageManager.readSrsJson(mode)
        val loadedDb = if (!rawJson.isNullOrBlank()) {
            try {
                json.decodeFromString<SrsDatabase>(rawJson)
            } catch (e: Exception) {
                e.printStackTrace()
                SrsDatabase()
            }
        } else {
            SrsDatabase()
        }

        // 2. Discover CSV files
        val csvFiles = storageManager.findCsvFiles(mode)
        val updatedDecks = loadedDb.decks.toMutableMap()
        val updatedCards = loadedDb.cards.toMutableMap()
        val nowIso = FSRSScheduler.formatIsoDate(Instant.now())

        for (csvFile in csvFiles) {
            val parseResult = CsvParser.parseCsv(csvFile.readText(), csvFile.fileName)
            val deckName = csvFile.fileName
            val markdownPath = "Flashcards/Flashcards - ${deckName.removeSuffix(".csv")}.md"

            updatedDecks[deckName] = DeckMetaData(
                sourcePath = csvFile.relativePath,
                markdownPath = markdownPath,
                cardCount = parseResult.cards.size
            )

            for (csvCard in parseResult.cards) {
                val existing = updatedCards[csvCard.id]
                if (existing != null) {
                    // Update question & answer in case CSV content changed, keeping FSRS stats
                    updatedCards[csvCard.id] = existing.copy(
                        question = csvCard.question,
                        answer = csvCard.answer,
                        deck = deckName
                    )
                } else {
                    // New card entry
                    updatedCards[csvCard.id] = SrsCardRecord(
                        id = csvCard.id,
                        deck = deckName,
                        question = csvCard.question,
                        answer = csvCard.answer,
                        state = CardState.NEW,
                        due = nowIso,
                        stability = 0.4,
                        difficulty = 5.0,
                        elapsedDays = 0,
                        scheduledDays = 0,
                        reps = 0,
                        lapses = 0,
                        lastReview = null
                    )
                }
            }
        }

        currentDatabase = SrsDatabase(
            version = 1,
            lastUpdated = nowIso,
            decks = updatedDecks,
            cards = updatedCards
        )

        // 3. Persist merged state atomically
        saveDatabase(mode)
        return currentDatabase
    }

    /**
     * Get summary statistics for all decks.
     */
    fun getDecksSummary(): List<DeckSummary> {
        val now = Instant.now()
        val summaries = mutableListOf<DeckSummary>()

        for ((deckName, meta) in currentDatabase.decks) {
            val deckCards = currentDatabase.cards.values.filter { it.deck == deckName }
            var newCount = 0
            var dueCount = 0

            for (card in deckCards) {
                if (card.state == CardState.NEW) {
                    newCount++
                    dueCount++
                } else {
                    val dueInstant = FSRSScheduler.parseIsoDate(card.due)
                    if (dueInstant == null || !dueInstant.isAfter(now)) {
                        dueCount++
                    }
                }
            }

            summaries.add(
                DeckSummary(
                    deckName = deckName,
                    totalCards = meta.cardCount,
                    newCards = newCount,
                    dueCards = dueCount,
                    sourcePath = meta.sourcePath
                )
            )
        }

        return summaries
    }

    /**
     * Retrieves cards due for review for a specific deck or all decks if deckName is null.
     */
    fun getDueCards(deckName: String? = null): List<SrsCardRecord> {
        val now = Instant.now()
        val cardList = if (deckName == null) {
            currentDatabase.cards.values
        } else {
            currentDatabase.cards.values.filter { it.deck == deckName }
        }

        return cardList.filter { card ->
            if (card.state == CardState.NEW || card.state == CardState.LEARNING || card.state == CardState.RELEARNING) {
                true
            } else {
                val dueInstant = FSRSScheduler.parseIsoDate(card.due)
                dueInstant == null || !dueInstant.isAfter(now)
            }
        }.sortedWith(compareBy({ it.state }, { it.due }))
    }

    /**
     * Records a user's SRS review rating for a card and saves changes atomically.
     */
    fun recordReview(cardId: String, rating: Rating): SrsCardRecord? {
        val currentCard = currentDatabase.cards[cardId] ?: return null
        val result = FSRSScheduler.schedule(currentCard, rating)
        val updatedCard = result.card

        val updatedCards = currentDatabase.cards.toMutableMap()
        updatedCards[cardId] = updatedCard

        val nowIso = FSRSScheduler.formatIsoDate(Instant.now())
        currentDatabase = currentDatabase.copy(
            lastUpdated = nowIso,
            cards = updatedCards
        )

        val mode = storageManager.getAccessMode()
        saveDatabase(mode)

        return updatedCard
    }

    private fun saveDatabase(mode: StorageAccessMode) {
        if (mode is StorageAccessMode.None) return
        val rawJson = json.encodeToString(currentDatabase)
        storageManager.writeSrsJsonAtomically(mode, rawJson)
    }

    fun getDatabase(): SrsDatabase = currentDatabase
}
