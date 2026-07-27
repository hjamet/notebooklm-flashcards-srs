import { Vault, normalizePath } from "obsidian";
import { SRSDataStoreSchema } from "../types";

/**
 * Manages loading and atomic saving of flashcards-srs-data.json inside Obsidian Vault
 */
export class FlashcardDataStore {
  private vault: Vault;
  private filePath: string;

  constructor(vault: Vault, filePath: string) {
    this.vault = vault;
    this.filePath = normalizePath(filePath);
  }

  public setFilePath(newPath: string): void {
    this.filePath = normalizePath(newPath);
  }

  /**
   * Loads the data store. Returns initialized default store if file does not exist or fails to parse.
   */
  public async load(): Promise<SRSDataStoreSchema> {
    try {
      const exists = await this.vault.adapter.exists(this.filePath);
      if (!exists) {
        return this.createEmptyStore();
      }

      const content = await this.vault.adapter.read(this.filePath);
      const parsed = JSON.parse(content) as SRSDataStoreSchema;

      if (!parsed.version || !parsed.cards || !parsed.decks) {
        console.warn("[NotebookLM Flashcards] Formatted data store appears invalid. Reinitializing.");
        return this.createEmptyStore();
      }

      return parsed;
    } catch (err) {
      console.error("[NotebookLM Flashcards] Failed to load data store:", err);
      return this.createEmptyStore();
    }
  }

  /**
   * Saves the data store atomically using a temporary .tmp file
   */
  public async save(data: SRSDataStoreSchema): Promise<void> {
    data.last_updated = new Date().toISOString();
    const jsonString = JSON.stringify(data, null, 2);
    const tmpPath = `${this.filePath}.tmp`;

    // Ensure parent directory exists
    const lastSlash = this.filePath.lastIndexOf("/");
    if (lastSlash !== -1) {
      const parentDir = this.filePath.substring(0, lastSlash);
      const dirExists = await this.vault.adapter.exists(parentDir);
      if (!dirExists) {
        await this.vault.createFolder(parentDir);
      }
    }

    try {
      // 1. Write to temporary file
      await this.vault.adapter.write(tmpPath, jsonString);

      // 2. Atomic rename / replace
      const targetExists = await this.vault.adapter.exists(this.filePath);
      if (targetExists) {
        await this.vault.adapter.remove(this.filePath);
      }
      await this.vault.adapter.rename(tmpPath, this.filePath);
    } catch (err) {
      console.error("[NotebookLM Flashcards] Error during atomic save:", err);
      // Clean up tmp file if leftover
      if (await this.vault.adapter.exists(tmpPath)) {
        try {
          await this.vault.adapter.remove(tmpPath);
        } catch (_) {
          // ignore
        }
      }
      throw err;
    }
  }

  private createEmptyStore(): SRSDataStoreSchema {
    return {
      version: 1,
      last_updated: new Date().toISOString(),
      decks: {},
      cards: {},
    };
  }
}
