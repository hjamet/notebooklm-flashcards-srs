import { ItemView, WorkspaceLeaf, MarkdownRenderer } from "obsidian";
import { CardSRSData, Rating, PluginSettings } from "../types";
import { FSRSEngine } from "../srs/fsrs";
import { FlashcardDataStore } from "../storage/flashcardDataStore";

export const VIEW_TYPE_FLASHCARD_REVIEW = "notebooklm-flashcard-review-view";

export class FlashcardReviewView extends ItemView {
  private store: FlashcardDataStore;
  private settings: PluginSettings;
  private fsrs: FSRSEngine;

  private dueCards: CardSRSData[] = [];
  private currentIndex: number = 0;
  private showAnswer: boolean = false;

  constructor(leaf: WorkspaceLeaf, store: FlashcardDataStore, settings: PluginSettings) {
    super(leaf);
    this.store = store;
    this.settings = settings;
    this.fsrs = new FSRSEngine(
      undefined,
      settings.requestRetention,
      settings.maximumInterval
    );
  }

  public getViewType(): string {
    return VIEW_TYPE_FLASHCARD_REVIEW;
  }

  public getDisplayText(): string {
    return "Révision Flashcards NotebookLM";
  }

  public getIcon(): string {
    return "brain";
  }

  public async onOpen(): Promise<void> {
    await this.loadAndPrepareQueue();
    this.render();
  }

  public async onClose(): Promise<void> {
    // Cleanup event handlers if needed
  }

  public async loadAndPrepareQueue(): Promise<void> {
    const data = await this.store.load();
    const now = new Date();

    // Filter cards due for review or new cards
    const allCards = Object.values(data.cards);
    this.dueCards = allCards.filter((card) => {
      const dueDate = new Date(card.due);
      return dueDate <= now;
    });

    // Shuffle queue for better retention learning
    this.dueCards.sort(() => Math.random() - 0.5);
    this.currentIndex = 0;
    this.showAnswer = false;
  }

  private render(): void {
    const container = this.containerEl.children[1];
    container.empty();
    container.addClass("flashcard-review-container");

    if (this.dueCards.length === 0 || this.currentIndex >= this.dueCards.length) {
      this.renderEmptyState(container as HTMLElement);
      return;
    }

    const currentCard = this.dueCards[this.currentIndex];

    // Header
    const headerEl = container.createDiv({ cls: "flashcard-header" });
    headerEl.createDiv({
      cls: "flashcard-deck-title",
      text: `Deck : ${currentCard.deck}`,
    });
    headerEl.createDiv({
      cls: "flashcard-progress-badge",
      text: `Carte ${this.currentIndex + 1} / ${this.dueCards.length}`,
    });

    // Main Card Body
    const cardEl = container.createDiv({ cls: "flashcard-card" });

    // Question Section
    cardEl.createDiv({ cls: "flashcard-label", text: "Question" });
    const questionContainer = cardEl.createDiv({ cls: "flashcard-question" });
    MarkdownRenderer.renderMarkdown(
      currentCard.question,
      questionContainer,
      this.app.workspace.getActiveFile()?.path || "",
      this
    );

    // Answer Section
    if (this.showAnswer) {
      cardEl.createDiv({ cls: "flashcard-answer-divider" });
      cardEl.createDiv({ cls: "flashcard-label", text: "Réponse" });
      const answerContainer = cardEl.createDiv({ cls: "flashcard-answer" });
      MarkdownRenderer.renderMarkdown(
        currentCard.answer,
        answerContainer,
        this.app.workspace.getActiveFile()?.path || "",
        this
      );
    }

    // Controls Section
    const controlsEl = container.createDiv({ cls: "flashcard-controls" });

    if (!this.showAnswer) {
      const showBtn = controlsEl.createEl("button", {
        cls: "flashcard-show-btn",
        text: "Montrer la réponse (Espace)",
      });
      showBtn.addEventListener("click", () => {
        this.showAnswer = true;
        this.render();
      });
    } else {
      const now = new Date();

      const rateAgainBtn = controlsEl.createEl("button", {
        cls: "flashcard-rate-btn flashcard-rate-again",
      });
      rateAgainBtn.createSpan({ text: "🔴 À revoir" });
      rateAgainBtn.createSpan({
        cls: "interval",
        text: this.fsrs.getNextIntervalText(currentCard, Rating.Again, now),
      });
      rateAgainBtn.addEventListener("click", () => this.handleRate(Rating.Again));

      const rateHardBtn = controlsEl.createEl("button", {
        cls: "flashcard-rate-btn flashcard-rate-hard",
      });
      rateHardBtn.createSpan({ text: "🟠 Difficile" });
      rateHardBtn.createSpan({
        cls: "interval",
        text: this.fsrs.getNextIntervalText(currentCard, Rating.Hard, now),
      });
      rateHardBtn.addEventListener("click", () => this.handleRate(Rating.Hard));

      const rateGoodBtn = controlsEl.createEl("button", {
        cls: "flashcard-rate-btn flashcard-rate-good",
      });
      rateGoodBtn.createSpan({ text: "🟢 Bon" });
      rateGoodBtn.createSpan({
        cls: "interval",
        text: this.fsrs.getNextIntervalText(currentCard, Rating.Good, now),
      });
      rateGoodBtn.addEventListener("click", () => this.handleRate(Rating.Good));

      const rateEasyBtn = controlsEl.createEl("button", {
        cls: "flashcard-rate-btn flashcard-rate-easy",
      });
      rateEasyBtn.createSpan({ text: "🔵 Facile" });
      rateEasyBtn.createSpan({
        cls: "interval",
        text: this.fsrs.getNextIntervalText(currentCard, Rating.Easy, now),
      });
      rateEasyBtn.addEventListener("click", () => this.handleRate(Rating.Easy));
    }
  }

  private async handleRate(rating: Rating): Promise<void> {
    if (this.currentIndex >= this.dueCards.length) return;

    const card = this.dueCards[this.currentIndex];
    const updatedCard = this.fsrs.schedule(card, rating);

    // Save state update to persistent JSON store
    const data = await this.store.load();
    data.cards[updatedCard.id] = updatedCard;
    await this.store.save(data);

    // Advance queue
    this.currentIndex++;
    this.showAnswer = false;
    this.render();
  }

  private renderEmptyState(container: HTMLElement): void {
    const emptyState = container.createDiv({ cls: "flashcard-empty-state" });
    emptyState.createEl("h3", { text: "Session terminée ! 🎉" });
    emptyState.createEl("p", {
      text: "Vous avez révisé toutes les cartes dues pour le moment.",
    });

    const reloadBtn = emptyState.createEl("button", {
      cls: "mod-cta",
      text: "Recharger la file de révision",
    });
    reloadBtn.style.marginTop = "1rem";
    reloadBtn.addEventListener("click", async () => {
      await this.loadAndPrepareQueue();
      this.render();
    });
  }
}
