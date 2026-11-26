import { computed, signal } from '@angular/core';
import { TextKey } from '../app/shared/texts.pl';
import { CaseFormValue, DecisionView, ReducedPhoto, UiMessage } from '../app/core/model';

/**
 * Test double with the read/write surface of `CaseStore` that the chat components use.
 * Plain signals and call recorders only (no vitest import: this file is part of the app tsconfig).
 */
export class FakeCaseStore {
  readonly caseId = signal<string | null>('case-1');
  readonly form = signal<CaseFormValue | null>(null);
  readonly formPhoto = signal<ReducedPhoto | null>(null);
  readonly decision = signal<DecisionView | null>(null);
  readonly messages = signal<UiMessage[]>([]);
  readonly streaming = signal(false);
  readonly additionalPhotoCount = signal(0);
  readonly messageErrors = signal<Readonly<Record<string, TextKey>>>({});
  readonly canSend = computed(() => this.caseId() !== null && !this.streaming());
  readonly canAttachPhoto = computed(() => this.additionalPhotoCount() < 3);

  readonly retried: string[] = [];
  readonly sent: { text: string; photo?: ReducedPhoto }[] = [];
  resetCount = 0;

  sendMessage(text: string, photo?: ReducedPhoto): void {
    this.sent.push({ text, photo });
  }

  retry(messageId: string): void {
    this.retried.push(messageId);
  }

  reset(): void {
    this.resetCount++;
    this.caseId.set(null);
  }
}

export function decisionView(overrides: Partial<DecisionView> = {}): DecisionView {
  return {
    label: 'ACCEPTED_PRELIMINARY',
    previousLabel: null,
    changed: false,
    changeReasonPl: null,
    safety: false,
    suggestOtherCaseType: 'NONE',
    ruleIds: [],
    ...overrides,
  };
}

export function uiMessage(overrides: Partial<UiMessage> = {}): UiMessage {
  return {
    id: 'a1',
    role: 'assistant',
    text: '',
    createdAt: new Date(2026, 10, 26, 14, 5),
    status: 'complete',
    isFirst: true,
    ...overrides,
  };
}
