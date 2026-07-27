export enum State {
  New = 0,
  Learning = 1,
  Review = 2,
  Relearning = 3,
}

export enum Rating {
  Again = 1,
  Hard = 2,
  Good = 3,
  Easy = 4,
}

export interface CardSRSData {
  id: string; // SHA-256 hash of question + answer
  deck: string; // e.g. "flashcards.csv"
  question: string;
  answer: string;
  state: State;
  due: string; // ISO string
  stability: number;
  difficulty: number;
  elapsed_days: number;
  scheduled_days: number;
  reps: number;
  lapses: number;
  last_review?: string; // ISO string
}

export interface DeckMetadata {
  source_path: string;
  markdown_path: string;
  card_count: number;
  last_imported?: string;
}

export interface SRSDataStoreSchema {
  version: number;
  last_updated: string;
  decks: Record<string, DeckMetadata>;
  cards: Record<string, CardSRSData>;
}

export interface PluginSettings {
  dataFilePath: string;
  autoGenerateMarkdown: boolean;
  targetMarkdownFolder: string;
  requestRetention: number;
  maximumInterval: number;
}

export const DEFAULT_SETTINGS: PluginSettings = {
  dataFilePath: "Flashcards/flashcards-srs-data.json",
  autoGenerateMarkdown: true,
  targetMarkdownFolder: "Flashcards",
  requestRetention: 0.9,
  maximumInterval: 36500,
};

export interface ParsedCard {
  question: string;
  answer: string;
  id: string;
}

export interface ParseResult {
  cards: ParsedCard[];
  totalLines: number;
  importedCount: number;
  skippedCount: number;
  errors: string[];
}
