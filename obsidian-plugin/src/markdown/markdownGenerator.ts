import { Vault, normalizePath } from "obsidian";
import { CardSRSData } from "../types";

/**
 * Generates a clean consultation Markdown note for imported deck flashcards.
 * Preserves inline ($...$) and display ($$...$$) LaTeX expressions intact.
 */
export class MarkdownGenerator {
  private vault: Vault;

  constructor(vault: Vault) {
    this.vault = vault;
  }

  public async generateOrUpdateDeckNote(
    folderPath: string,
    deckName: string,
    cards: CardSRSData[]
  ): Promise<string> {
    const cleanFolder = normalizePath(folderPath);
    const sanitizedDeckName = deckName.replace(/\.csv$/i, "");
    const fileName = `Flashcards - ${sanitizedDeckName}.md`;
    const fullPath = normalizePath(`${cleanFolder}/${fileName}`);

    // Ensure folder exists
    const dirExists = await this.vault.adapter.exists(cleanFolder);
    if (!dirExists && cleanFolder !== "") {
      await this.vault.createFolder(cleanFolder);
    }

    const lines: string[] = [];

    // Note Header
    lines.push(`---`);
    lines.push(`tags: [flashcards, notebooklm, fsrs]`);
    lines.push(`deck: "${sanitizedDeckName}"`);
    lines.push(`total_cards: ${cards.length}`);
    lines.push(`updated_at: "${new Date().toISOString()}"`);
    lines.push(`---`);
    lines.push(``);
    lines.push(`# 🎴 Flashcards — ${sanitizedDeckName}`);
    lines.push(``);
    lines.push(`> [!NOTE] Note de consultation non-éditable`);
    lines.push(`> Cette note est générée automatiquement à partir de l'export NotebookLM \`${deckName}\`.`);
    lines.push(`> Pour réviser ces cartes avec l'algorithme FSRS, lancez la commande **NotebookLM SRS: Démarrer la session de révision**.`);
    lines.push(``);
    lines.push(`---`);
    lines.push(``);

    cards.forEach((card, index) => {
      lines.push(`### Carte ${index + 1}`);
      lines.push(``);
      lines.push(`**Question :**`);
      lines.push(card.question);
      lines.push(``);
      lines.push(`**Réponse :**`);
      lines.push(card.answer);
      lines.push(``);
      lines.push(`---`);
      lines.push(``);
    });

    const noteContent = lines.join("\n");

    const fileExists = await this.vault.adapter.exists(fullPath);
    if (fileExists) {
      await this.vault.adapter.write(fullPath, noteContent);
    } else {
      await this.vault.create(fullPath, noteContent);
    }

    return fullPath;
  }
}
