package com.notebooklm.flashcards.data.parser

import com.notebooklm.flashcards.data.model.CsvCard
import java.security.MessageDigest

object CsvParser {

    data class ParseResult(
        val cards: List<CsvCard>,
        val ignoredLinesCount: Int,
        val errors: List<String>
    )

    /**
     * Parses a CSV text strictly following RFC 4180 rules.
     * Supports columns: Question, Answer, Context, Source/Deck.
     */
    fun parseCsv(csvText: String, defaultDeckName: String): ParseResult {
        val records = parseRawCsvRecords(csvText)
        if (records.isEmpty()) {
            return ParseResult(emptyList(), 0, listOf("Le fichier CSV est vide"))
        }

        val cards = mutableListOf<CsvCard>()
        val errors = mutableListOf<String>()
        var ignoredCount = 0

        // Check if first line is header
        val firstRow = records.first()
        val hasHeader = isHeaderRow(firstRow)
        
        var questionCol = 0
        var answerCol = 1
        var contextCol = if (firstRow.size > 2) 2 else -1
        var deckCol = if (firstRow.size > 3) 3 else -1

        if (hasHeader) {
            firstRow.forEachIndexed { colIdx, header ->
                val h = header.trim().lowercase()
                when {
                    h == "question" || h == "front" || h == "recto" || h == "q" -> questionCol = colIdx
                    h == "answer" || h == "back" || h == "verso" || h == "a" || h == "reponse" || h == "réponse" -> answerCol = colIdx
                    h == "context" || h == "contexte" || h == "notes" || h == "note" || h == "extra" -> contextCol = colIdx
                    h == "deck" || h == "source" || h == "paquet" || h == "category" -> deckCol = colIdx
                }
            }
        }

        val dataRows = if (hasHeader) records.drop(1) else records

        dataRows.forEachIndexed { index, row ->
            val lineNumber = if (hasHeader) index + 2 else index + 1
            if (row.size < 2) {
                ignoredCount++
                errors.add("Ligne $lineNumber ignorée: moins de 2 colonnes (${row.size})")
                return@forEachIndexed
            }

            val question = row.getOrNull(questionCol)?.trim() ?: ""
            val answer = row.getOrNull(answerCol)?.trim() ?: ""
            val context = if (contextCol >= 0) row.getOrNull(contextCol)?.trim()?.ifBlank { null } else null
            val rowDeck = if (deckCol >= 0) row.getOrNull(deckCol)?.trim()?.ifBlank { null } else null
            val finalDeckName = rowDeck ?: defaultDeckName

            if (question.isEmpty() && answer.isEmpty()) {
                ignoredCount++
                return@forEachIndexed
            }

            val cardId = generateDeterministicId(question, answer)
            cards.add(
                CsvCard(
                    id = cardId,
                    question = question,
                    answer = answer,
                    deckName = finalDeckName,
                    context = context
                )
            )
        }

        return ParseResult(cards, ignoredCount, errors)
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        if (row.size < 2) return false
        val col0 = row[0].trim().lowercase()
        val col1 = row[1].trim().lowercase()
        return (col0 == "question" || col0 == "front" || col0 == "recto" || col0 == "q") &&
               (col1 == "answer" || col1 == "back" || col1 == "verso" || col1 == "a" || col1 == "reponse" || col1 == "réponse") ||
               row.any { cell ->
                   val c = cell.trim().lowercase()
                   c == "question" || c == "answer" || c == "context" || c == "contexte" || c == "deck" || c == "source"
               }
    }

    /**
     * State-machine RFC 4180 CSV parser handling multiline quoted cells.
     */
    private fun parseRawCsvRecords(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val currentRow = mutableListOf<String>()
        val currentCell = StringBuilder()
        var inQuotes = false
        var i = 0

        val normalizedText = text.replace("\r\n", "\n").replace("\r", "\n")

        while (i < normalizedText.length) {
            val c = normalizedText[i]

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < normalizedText.length && normalizedText[i + 1] == '"') {
                        // Escaped quote ""
                        currentCell.append('"')
                        i++
                    } else {
                        // Closing quote
                        inQuotes = false
                    }
                } else {
                    currentCell.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    ',' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.clear()
                    }
                    '\n' -> {
                        currentRow.add(currentCell.toString())
                        currentCell.clear()
                        rows.add(ArrayList(currentRow))
                        currentRow.clear()
                    }
                    else -> currentCell.append(c)
                }
            }
            i++
        }

        if (currentCell.isNotEmpty() || currentRow.isNotEmpty()) {
            currentRow.add(currentCell.toString())
            rows.add(currentRow)
        }

        return rows
    }

    fun generateDeterministicId(question: String, answer: String): String {
        val input = "${question.trim()}|${answer.trim()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(16)
    }
}
