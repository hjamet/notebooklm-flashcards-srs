import { CardSRSData, Rating, State } from "../types";

/**
 * FSRS v4.5 Algorithm Implementation
 */
export const DEFAULT_FSRS_WEIGHTS = [
  0.4, 0.6, 2.4, 5.8, 4.93, 0.94, 0.86, 0.01, 1.49, 0.14, 0.94, 2.18, 0.05, 0.34, 1.26, 0.29, 2.61,
];

export interface FSRSSchedulingResult {
  card: CardSRSData;
  intervalDays: number;
}

export class FSRSEngine {
  private w: number[];
  private requestRetention: number;
  private maximumInterval: number;

  constructor(
    weights: number[] = DEFAULT_FSRS_WEIGHTS,
    requestRetention: number = 0.9,
    maximumInterval: number = 36500
  ) {
    this.w = weights;
    this.requestRetention = requestRetention;
    this.maximumInterval = maximumInterval;
  }

  /**
   * Initializes a brand new card SRS record
   */
  public createNewCard(id: string, deck: string, question: string, answer: string): CardSRSData {
    return {
      id,
      deck,
      question,
      answer,
      state: State.New,
      due: new Date().toISOString(),
      stability: 0,
      difficulty: 0,
      elapsed_days: 0,
      scheduled_days: 0,
      reps: 0,
      lapses: 0,
    };
  }

  /**
   * Evaluates card review and returns updated card state
   */
  public schedule(card: CardSRSData, rating: Rating, now: Date = new Date()): CardSRSData {
    const lastReviewDate = card.last_review ? new Date(card.last_review) : now;
    const elapsedDays = Math.max(
      0,
      Math.floor((now.getTime() - lastReviewDate.getTime()) / (1000 * 60 * 60 * 24))
    );

    let newStability = card.stability;
    let newDifficulty = card.difficulty;
    let newState = card.state;
    let newReps = card.reps + 1;
    let newLapses = card.lapses;
    let scheduledDays = 0;

    if (card.state === State.New) {
      // First review
      newDifficulty = this.initDifficulty(rating);
      newStability = this.initStability(rating);

      if (rating === Rating.Again) {
        newState = State.Learning;
        newLapses += 1;
        scheduledDays = 0; // Review within 10 minutes
      } else {
        newState = State.Review;
        scheduledDays = this.nextInterval(newStability);
      }
    } else if (card.state === State.Learning || card.state === State.Relearning) {
      // Learning / Relearning state
      if (rating === Rating.Again) {
        newState = card.state;
        scheduledDays = 0;
      } else if (rating === Rating.Hard) {
        newState = card.state;
        scheduledDays = 0;
      } else {
        newState = State.Review;
        newDifficulty = this.nextDifficulty(card.difficulty, rating);
        newStability = this.initStability(rating);
        scheduledDays = this.nextInterval(newStability);
      }
    } else {
      // Review state
      const retrievability = this.retrievability(elapsedDays, card.stability);
      newDifficulty = this.nextDifficulty(card.difficulty, rating);

      if (rating === Rating.Again) {
        newState = State.Relearning;
        newLapses += 1;
        newStability = this.nextStabilityLapse(card.difficulty, card.stability, retrievability);
        scheduledDays = 0;
      } else {
        newState = State.Review;
        newStability = this.nextStabilitySuccess(
          card.difficulty,
          card.stability,
          retrievability,
          rating
        );
        scheduledDays = this.nextInterval(newStability);
      }
    }

    // Calculate due date
    const dueDate = new Date(now.getTime());
    if (scheduledDays === 0) {
      // 10 minutes in the future for short term learning
      dueDate.setMinutes(dueDate.getMinutes() + 10);
    } else {
      dueDate.setDate(dueDate.getDate() + scheduledDays);
    }

    return {
      ...card,
      state: newState,
      due: dueDate.toISOString(),
      stability: Number(newStability.toFixed(4)),
      difficulty: Number(newDifficulty.toFixed(4)),
      elapsed_days: elapsedDays,
      scheduled_days: scheduledDays,
      reps: newReps,
      lapses: newLapses,
      last_review: now.toISOString(),
    };
  }

  /**
   * Helper to format human readable interval for rating buttons (e.g. "<10m", "1d", "4d", "12d")
   */
  public getNextIntervalText(card: CardSRSData, rating: Rating, now: Date = new Date()): string {
    const scheduledCard = this.schedule(card, rating, now);
    if (scheduledCard.scheduled_days === 0) {
      return "<10m";
    }
    if (scheduledCard.scheduled_days === 1) {
      return "1d";
    }
    return `${scheduledCard.scheduled_days}d`;
  }

  private initStability(r: Rating): number {
    return Math.max(0.1, this.w[r - 1]);
  }

  private initDifficulty(r: Rating): number {
    const d = this.w[4] - (r - 3) * this.w[5];
    return this.clamp(d, 1, 10);
  }

  private nextDifficulty(d: number, r: Rating): number {
    const nextD = d - this.w[6] * (r - 3);
    const meanReversion = this.w[7] * this.initDifficulty(Rating.Good) + (1 - this.w[7]) * nextD;
    return this.clamp(meanReversion, 1, 10);
  }

  private retrievability(t: number, s: number): number {
    if (s <= 0) return 0;
    return Math.pow(1 + (19 / 81) * (t / s), -0.5);
  }

  private nextStabilitySuccess(d: number, s: number, r: number, rating: Rating): number {
    let hardPenalty = 1;
    if (rating === Rating.Hard) {
      hardPenalty = this.w[15];
    } else if (rating === Rating.Easy) {
      hardPenalty = this.w[16];
    }

    const inc =
      Math.exp(this.w[8]) *
      (11 - d) *
      Math.pow(s, -this.w[9]) *
      (Math.exp((1 - r) * this.w[10]) - 1) *
      hardPenalty;

    return Math.max(0.1, s * (1 + inc));
  }

  private nextStabilityLapse(d: number, s: number, r: number): number {
    const newS =
      this.w[11] *
      Math.pow(d, -this.w[12]) *
      (Math.pow(s + 1, this.w[13]) - 1) *
      Math.exp((1 - r) * this.w[14]);

    return this.clamp(newS, 0.1, s);
  }

  private nextInterval(s: number): number {
    const newInterval = (s / (19 / 81)) * (Math.pow(this.requestRetention, 1 / -0.5) - 1);
    const rounded = Math.round(newInterval);
    return this.clamp(rounded, 1, this.maximumInterval);
  }

  private clamp(val: number, min: number, max: number): number {
    return Math.min(Math.max(val, min), max);
  }
}
