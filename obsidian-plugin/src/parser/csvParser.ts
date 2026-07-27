import { ParsedCard, ParseResult } from "../types";
import { sha256 } from "../utils/sha256";

/**
 * Robust RFC 4180 CSV parser for NotebookLM flashcards.
 * Handles UTF-8 BOM, multiline fields, quote escaping, header detection,
 * and deterministic SHA-256 hash generation for every card.
 */
export function parseNotebookLMCSV(csvContent: string): ParseResult {
  const errors: string[] = [];

  // Remove UTF-8 BOM if present
  let cleanContent = csvContent;
  if (cleanContent.charCodeAt(0) === 0xfeff) {
    cleanContent = cleanContent.slice(1);
  }

  // Parse CSV into rows of string arrays according to RFC 4180
  const rows: string[][] = [];
  let currentRow: string[] = [];
  let currentField = "";
  let inQuotes = false;
  let i = 0;

  while (i < cleanContent.length) {
    const char = cleanContent[i];

    if (inQuotes) {
      if (char === '"') {
        if (i + 1 < cleanContent.length && cleanContent[i + 1] === '"') {
          // Escaped quote inside quoted string
          currentField += '"';
          i += 2;
          continue;
        } else {
          // Closing quote
          inQuotes = false;
          i++;
          continue;
        }
      } else {
        currentField += char;
        i++;
        continue;
      }
    } else {
      if (char === '"') {
        inQuotes = true;
        i++;
        continue;
      } else if (char === ",") {
        currentRow.push(currentField);
        currentField = "";
        i++;
        continue;
      } else if (char === "\r") {
        if (i + 1 < cleanContent.length && cleanContent[i + 1] === "\n") {
          i++;
        }
        currentRow.push(currentField);
        rows.push(currentRow);
        currentRow = [];
        currentField = "";
        i++;
        continue;
      } else if (char === "\n") {
        currentRow.push(currentField);
        rows.push(currentRow);
        currentRow = [];
        currentField = "";
        i++;
        continue;
      } else {
        currentField += char;
        i++;
        continue;
      }
    }
  }

  // Push final field/row if lingering
  if (currentField.length > 0 || currentRow.length > 0) {
    currentRow.push(currentField);
    rows.push(currentRow);
  }

  // Filter out completely empty rows
  const validRows = rows.filter((r) => r.some((field) => field.trim().length > 0));

  if (validRows.length === 0) {
    return {
      cards: [],
      totalLines: 0,
      importedCount: 0,
      skippedCount: 0,
      errors: ["Le fichier CSV est vide."],
    };
  }

  let questionIdx = 0;
  let answerIdx = 1;
  let startRowIdx = 0;

  // Check if first row is a header
  const firstRow = validRows[0].map((f) => f.trim().toLowerCase());
  const headerQuestionKeywords = ["question", "front", "recto", "prompt", "carte"];
  const headerAnswerKeywords = ["answer", "back", "verso", "response", "réponse", "reponse"];

  const foundQIdx = firstRow.findIndex((f) => headerQuestionKeywords.some((k) => f.includes(k)));
  const foundAIdx = firstRow.findIndex((f) => headerAnswerKeywords.some((k) => f.includes(k)));

  if (foundQIdx !== -1 && foundAIdx !== -1) {
    questionIdx = foundQIdx;
    answerIdx = foundAIdx;
    startRowIdx = 1;
  }

  const cards: ParsedCard[] = [];
  let skippedCount = 0;

  for (let r = startRowIdx; r < validRows.length; r++) {
    const row = validRows[r];

    if (row.length <= Math.max(questionIdx, answerIdx)) {
      skippedCount++;
      errors.push(`Ligne ${r + 1} ignorée : nombre insuffisant de colonnes (${row.length}).`);
      continue;
    }

    const question = row[questionIdx]?.trim() || "";
    const answer = row[answerIdx]?.trim() || "";

    if (!question || !answer) {
      skippedCount++;
      errors.push(`Ligne ${r + 1} ignorée : question ou réponse vide.`);
      continue;
    }

    // Deterministic SHA-256 hash of question + answer
    const id = sha256(question + "\n" + answer);

    cards.push({
      question,
      answer,
      id,
    });
  }

  return {
    cards,
    totalLines: validRows.length,
    importedCount: cards.length,
    skippedCount,
    errors,
  };
}
