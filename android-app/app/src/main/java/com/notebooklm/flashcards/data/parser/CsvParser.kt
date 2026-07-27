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
     */
    fun parseCsv(csvText: String, deckName: String): ParseResult {
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
        val dataRows = if (hasHeader) records.drop(1) else records

        dataRows.forEachIndexed { index, row ->
            val lineNumber = if (hasHeader) index + 2 else index + 1
            if (row.size < 2) {
                ignoredCount++
                errors.add("Ligne $lineNumber ignorée: moins de 2 colonnes (${row.size})")
                return@forEachIndexed
            }

            val question = row[0].trim()
            val answer = row[1].trim()

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
                    deckName = deckName
                )
            )
        }

        return ParseResult(cards, ignoredCount, errors)
    }

    private fun isHeaderRow(row: List<String>): Boolean {
        if (row.size < 2) return false
        val col0 = row[0].trim().lowercase()
        val col1 = row[1].trim().lowercase()
        return (col0 == "question" || col0 == "front" || col0 == "recto") &&
               (col1 == "answer" || col1 == "back" || col1 == "verso")
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
