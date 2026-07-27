import { Plugin, PluginSettingTab, Setting, Notice, WorkspaceLeaf, Modal } from "obsidian";
import { PluginSettings, DEFAULT_SETTINGS } from "./types";
import { parseNotebookLMCSV } from "./parser/csvParser";
import { FSRSEngine } from "./srs/fsrs";
import { FlashcardDataStore } from "./storage/flashcardDataStore";
import { MarkdownGenerator } from "./markdown/markdownGenerator";
import { FlashcardReviewView, VIEW_TYPE_FLASHCARD_REVIEW } from "./views/FlashcardReviewView";

export default class NotebookLMFlashcardsPlugin extends Plugin {
  public settings: PluginSettings = DEFAULT_SETTINGS;
  public store!: FlashcardDataStore;
  public markdownGenerator!: MarkdownGenerator;

  async onload(): Promise<void> {
    await this.loadSettings();

    this.store = new FlashcardDataStore(this.app.vault, this.settings.dataFilePath);
    this.markdownGenerator = new MarkdownGenerator(this.app.vault);

    // Register review view
    this.registerView(
      VIEW_TYPE_FLASHCARD_REVIEW,
      (leaf: WorkspaceLeaf) => new FlashcardReviewView(leaf, this.store, this.settings)
    );

    // Add ribbon icon
    this.addRibbonIcon("brain", "Flashcards NotebookLM SRS", () => {
      this.activateReviewView();
    });

    // Register Commands
    this.addCommand({
      id: "import-notebooklm-csv",
      name: "Importer un fichier CSV NotebookLM",
      callback: () => {
        new CSVImportModal(this).open();
      },
    });

    this.addCommand({
      id: "start-flashcard-review",
      name: "Démarrer la session de révision",
      callback: () => {
        this.activateReviewView();
      },
    });

    this.addCommand({
      id: "regenerate-consultation-notes",
      name: "Régénérer les notes Markdown de consultation",
      callback: async () => {
        await this.regenerateAllConsultationNotes();
      },
    });

    // Add Settings Tab
    this.addSettingTab(new NotebookLMSRSSettingTab(this.app, this));
  }

  async onunload(): Promise<void> {
    this.app.workspace.detachLeavesOfType(VIEW_TYPE_FLASHCARD_REVIEW);
  }

  async loadSettings(): Promise<void> {
    this.settings = Object.assign({}, DEFAULT_SETTINGS, await this.loadData());
  }

  async saveSettings(): Promise<void> {
    await this.saveData(this.settings);
    if (this.store) {
      this.store.setFilePath(this.settings.dataFilePath);
    }
  }

  public async activateReviewView(): Promise<void> {
    const { workspace } = this.app;

    let leaf: WorkspaceLeaf | null = null;
    const leaves = workspace.getLeavesOfType(VIEW_TYPE_FLASHCARD_REVIEW);

    if (leaves.length > 0) {
      leaf = leaves[0];
    } else {
      leaf = workspace.getRightLeaf(false);
      if (leaf) {
        await leaf.setViewState({
          type: VIEW_TYPE_FLASHCARD_REVIEW,
          active: true,
        });
      }
    }

    if (leaf) {
      workspace.revealLeaf(leaf);
      const view = leaf.view;
      if (view instanceof FlashcardReviewView) {
        await view.loadAndPrepareQueue();
      }
    }
  }

  public async processCSVImport(fileName: string, csvContent: string): Promise<void> {
    const parseResult = parseNotebookLMCSV(csvContent);

    if (parseResult.importedCount === 0) {
      new Notice(`⚠️ Échec de l'import CSV. ${parseResult.errors.join(" ")}`);
      return;
    }

    // Display Notice Report as required by Point of Vigilance 1
    const reportMsg =
      `✅ Import CSV réussi : ${parseResult.importedCount} carte(s) importée(s).\n` +
      (parseResult.skippedCount > 0 ? `⚠️ ${parseResult.skippedCount} ligne(s) ignorée(s).` : "");
    new Notice(reportMsg, 8000);

    // Load store & merge cards
    const data = await this.store.load();
    const fsrsEngine = new FSRSEngine(undefined, this.settings.requestRetention, this.settings.maximumInterval);

    const importedCards = parseResult.cards.map((parsedCard) => {
      // Preserve existing card SRS state if already present
      if (data.cards[parsedCard.id]) {
        return {
          ...data.cards[parsedCard.id],
          question: parsedCard.question,
          answer: parsedCard.answer,
        };
      } else {
        return fsrsEngine.createNewCard(
          parsedCard.id,
          fileName,
          parsedCard.question,
          parsedCard.answer
        );
      }
    });

    // Update store cards
    importedCards.forEach((c) => {
      data.cards[c.id] = c;
    });

    // Generate Markdown consultation note
    let markdownPath = "";
    if (this.settings.autoGenerateMarkdown) {
      markdownPath = await this.markdownGenerator.generateOrUpdateDeckNote(
        this.settings.targetMarkdownFolder,
        fileName,
        importedCards
      );
    }

    // Update deck metadata
    data.decks[fileName] = {
      source_path: `${this.settings.targetMarkdownFolder}/${fileName}`,
      markdown_path: markdownPath,
      card_count: importedCards.length,
      last_imported: new Date().toISOString(),
    };

    // Save store atomically
    await this.store.save(data);
  }

  public async regenerateAllConsultationNotes(): Promise<void> {
    const data = await this.store.load();
    const decks = Object.keys(data.decks);

    if (decks.length === 0) {
      new Notice("Aucun deck enregistré dans les données SRS.");
      return;
    }

    let count = 0;
    for (const deckName of decks) {
      const cardsInDeck = Object.values(data.cards).filter((c) => c.deck === deckName);
      if (cardsInDeck.length > 0) {
        await this.markdownGenerator.generateOrUpdateDeckNote(
          this.settings.targetMarkdownFolder,
          deckName,
          cardsInDeck
        );
        count++;
      }
    }

    new Notice(`✅ ${count} note(s) de consultation régénérée(s).`);
  }
}

/**
 * Modal to pick a CSV file from local disk for import
 */
class CSVImportModal extends Modal {
  private plugin: NotebookLMFlashcardsPlugin;

  constructor(plugin: NotebookLMFlashcardsPlugin) {
    super(plugin.app);
    this.plugin = plugin;
  }

  onOpen(): void {
    const { contentEl } = this;
    contentEl.empty();

    contentEl.createEl("h2", { text: "Importer des Flashcards NotebookLM (.csv)" });
    contentEl.createEl("p", {
      text: "Sélectionnez le fichier CSV exporté depuis NotebookLM pour l'intégrer au système SRS.",
    });

    const fileInput = contentEl.createEl("input", {
      type: "file",
    });
    fileInput.accept = ".csv";
    fileInput.style.marginTop = "1rem";

    const submitBtn = contentEl.createEl("button", {
      cls: "mod-cta",
      text: "Importer",
    });
    submitBtn.style.marginTop = "1rem";
    submitBtn.style.marginLeft = "1rem";

    submitBtn.addEventListener("click", async () => {
      const files = fileInput.files;
      if (!files || files.length === 0) {
        new Notice("Veuillez sélectionner un fichier CSV.");
        return;
      }

      const file = files[0];
      const reader = new FileReader();

      reader.onload = async (e) => {
        const text = e.target?.result as string;
        if (text) {
          await this.plugin.processCSVImport(file.name, text);
          this.close();
        }
      };

      reader.readAsText(file, "UTF-8");
    });
  }

  onClose(): void {
    const { contentEl } = this;
    contentEl.empty();
  }
}

/**
 * Plugin Settings Tab
 */
class NotebookLMSRSSettingTab extends PluginSettingTab {
  plugin: NotebookLMFlashcardsPlugin;

  constructor(app: any, plugin: NotebookLMFlashcardsPlugin) {
    super(app, plugin);
    this.plugin = plugin;
  }

  display(): void {
    const { containerEl } = this;
    containerEl.empty();

    containerEl.createEl("h2", { text: "Réglages NotebookLM Flashcards SRS" });

    new Setting(containerEl)
      .setName("Chemin du fichier de données SRS")
      .setDesc("Chemin relatif dans le coffre Obsidian pour stocker flashcards-srs-data.json")
      .addText((text) =>
        text
          .setPlaceholder("Flashcards/flashcards-srs-data.json")
          .setValue(this.plugin.settings.dataFilePath)
          .onChange(async (value) => {
            this.plugin.settings.dataFilePath = value.trim() || DEFAULT_SETTINGS.dataFilePath;
            await this.plugin.saveSettings();
          })
      );

    new Setting(containerEl)
      .setName("Dossier des notes de consultation Markdown")
      .setDesc("Dossier du coffre où les fichiers Flashcards - [Nom].md seront générés")
      .addText((text) =>
        text
          .setPlaceholder("Flashcards")
          .setValue(this.plugin.settings.targetMarkdownFolder)
          .onChange(async (value) => {
            this.plugin.settings.targetMarkdownFolder = value.trim() || DEFAULT_SETTINGS.targetMarkdownFolder;
            await this.plugin.saveSettings();
          })
      );

    new Setting(containerEl)
      .setName("Génération automatique des notes Markdown")
      .setDesc("Génère automatiquement une note de consultation Markdown à chaque import CSV")
      .addToggle((toggle) =>
        toggle
          .setValue(this.plugin.settings.autoGenerateMarkdown)
          .onChange(async (value) => {
            this.plugin.settings.autoGenerateMarkdown = value;
            await this.plugin.saveSettings();
          })
      );

    new Setting(containerEl)
      .setName("Taux de rétention souhaité (FSRS)")
      .setDesc("Valeur entre 0.7 et 0.95 (défaut : 0.9 = 90%)")
      .addText((text) =>
        text
          .setPlaceholder("0.9")
          .setValue(String(this.plugin.settings.requestRetention))
          .onChange(async (value) => {
            const num = parseFloat(value);
            if (!isNaN(num) && num >= 0.7 && num <= 0.95) {
              this.plugin.settings.requestRetention = num;
              await this.plugin.saveSettings();
            }
          })
      );
  }
}
