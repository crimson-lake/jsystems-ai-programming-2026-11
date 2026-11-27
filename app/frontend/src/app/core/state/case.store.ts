import { DOCUMENT } from '@angular/common';
import { Injectable, Provider, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { CaseSubmitter } from '../../features/case-form/case-submitter';
import { TextKey, t } from '../../shared/texts.pl';
import { CaseApiService, StreamHandle } from '../api/case-api.service';
import {
  ApiError,
  CaseFormValue,
  CopilotEvent,
  DecisionView,
  ProgressStep,
  ReducedPhoto,
  UiMessage,
} from '../model';
import { StreamHandlers } from '../sse/sse-stream';

/** No `decision` within this time after the request started is a TIMEOUT (AC-40, ADR-003 3.5). */
export const WATCHDOG_MS = 60_000;
/** Photos a customer may add in chat after the form (AC-33). */
export const MAX_ADDITIONAL_PHOTOS = 3;

/** Longest chat message the backend accepts (AC-31). */
export const MAX_MESSAGE_LENGTH = 2000;

/**
 * The composer text key of a chat 400 VALIDATION (ADR-003 3.7). A `photo` field error maps to its
 * form text (`form.photo.size` by message, otherwise `form.photo.type`); then a too-long text; else
 * `chat.message.empty`. The refusal path drops the photo preview in every case.
 */
export function chatValidationKey(error: ApiError, text: string): TextKey {
  const photo = error.fieldErrors?.find((f) => f.field === 'photo');
  if (photo) return photo.message === t('form.photo.size') ? 'form.photo.size' : 'form.photo.type';
  const tooLong =
    text.length > MAX_MESSAGE_LENGTH ||
    error.fieldErrors?.some((f) => f.message === t('chat.message.tooLong'));
  return tooLong ? 'chat.message.tooLong' : 'chat.message.empty';
}

const CHAT_URL = '/sprawa';
const FORM_URL = '';

/** One running request and what is needed to undo it. */
interface Turn {
  readonly kind: 'first' | 'chat';
  readonly userMessageId?: string;
  readonly carriesPhoto: boolean;
  handle?: StreamHandle;
  watchdog?: ReturnType<typeof setTimeout>;
  assistantId?: string;
  /** Decision shown before this turn's `decision` event; restored if the turn fails. */
  restoreDecision?: DecisionView | null;
  finished: boolean;
}

/**
 * A chat send the server refused before streaming (400, 413, 422): the message is removed from the
 * list and the composer takes `text` back, drops its photo preview and shows `errorKey` (ADR-003 3.7).
 */
export interface ComposerRejection {
  readonly text: string;
  readonly errorKey: TextKey;
}

interface PendingChat {
  text: string;
  photo?: ReducedPhoto;
}

/**
 * Signal store of the open case (ADR-003 sections 3.3 and 3.5). Maps SSE events to state; the
 * components only read signals. Also the real implementation of the form's `CaseSubmitter` seam.
 */
@Injectable({ providedIn: 'root' })
export class CaseStore implements CaseSubmitter {
  private readonly api = inject(CaseApiService);
  private readonly router = inject(Router);
  private readonly window = inject(DOCUMENT).defaultView;

  private readonly caseId_ = signal<string | null>(null);
  private readonly form_ = signal<CaseFormValue | null>(null);
  private readonly formPhoto_ = signal<ReducedPhoto | null>(null);
  private readonly decision_ = signal<DecisionView | null>(null);
  private readonly messages_ = signal<UiMessage[]>([]);
  private readonly streaming_ = signal(false);
  private readonly submitting_ = signal(false);
  private readonly submitError_ = signal(false);
  private readonly progressStep_ = signal<ProgressStep | null>(null);
  private readonly additionalPhotoCount_ = signal(0);
  private readonly messageErrors_ = signal<Readonly<Record<string, TextKey>>>({});
  private readonly composerRejection_ = signal<ComposerRejection | null>(null);

  readonly caseId = this.caseId_.asReadonly();
  /** Values and reduced photo of the first submit; the form restores them after a failure. */
  readonly form = this.form_.asReadonly();
  readonly formPhoto = this.formPhoto_.asReadonly();
  /** Current decision shown in the summary badge. */
  readonly decision = this.decision_.asReadonly();
  readonly messages = this.messages_.asReadonly();
  readonly streaming = this.streaming_.asReadonly();
  readonly progressStep = this.progressStep_.asReadonly();
  readonly additionalPhotoCount = this.additionalPhotoCount_.asReadonly();
  readonly submitting = this.submitting_.asReadonly();
  readonly submitError = this.submitError_.asReadonly();
  /** Text key of the inline error of each failed user message, by message id. */
  readonly messageErrors = this.messageErrors_.asReadonly();
  /** Set when the server refused a send before streaming; the composer reads it and acknowledges. */
  readonly composerRejection = this.composerRejection_.asReadonly();

  readonly canAttachPhoto = computed(() => this.additionalPhotoCount_() < MAX_ADDITIONAL_PHOTOS);
  readonly canSend = computed(() => this.caseId_() !== null && !this.streaming_());

  private turn: Turn | null = null;
  private sequence = 0;
  private readonly pending = new Map<string, PendingChat>();

  private readonly beforeUnload = (event: Event): void => {
    event.preventDefault();
    (event as BeforeUnloadEvent).returnValue = '';
  };
  private unloadGuardOn = false;

  /** `CaseSubmitter` seam used by the case form. */
  submit(form: CaseFormValue, photo: ReducedPhoto): void {
    this.submitCase(form, photo);
  }

  submitCase(form: CaseFormValue, photo: ReducedPhoto): void {
    if (this.submitting_() || this.streaming_()) return;
    this.discardTurn();
    this.setCaseId(null);
    this.messages_.set([]);
    this.messageErrors_.set({});
    this.decision_.set(null);
    this.additionalPhotoCount_.set(0);
    this.pending.clear();
    this.form_.set(form);
    this.formPhoto_.set(photo);
    this.submitError_.set(false);
    this.submitting_.set(true);
    this.streaming_.set(true);
    this.progressStep_.set(null);

    const turn = this.newTurn({ kind: 'first', carriesPhoto: false });
    turn.handle = this.api.createCase(form, photo, this.handlersFor(turn));
  }

  sendMessage(text: string, photo?: ReducedPhoto): void {
    const caseId = this.caseId_();
    if (!caseId || this.streaming_()) return;
    const user: UiMessage = {
      id: this.nextId('u'),
      role: 'user',
      text,
      photoUrl: photo?.objectUrl,
      createdAt: new Date(),
      status: 'sending',
      clientMessageId: crypto.randomUUID(),
      isFirst: false,
    };
    this.composerRejection_.set(null);
    this.pending.set(user.id, { text, photo });
    this.messages_.update((list) => [...list, user]);
    this.startChatTurn(caseId, user);
  }

  /** The composer applied the rejection (text restored, preview dropped, error shown). */
  clearComposerRejection(): void {
    this.composerRejection_.set(null);
  }

  /** Re-sends a failed message with its original `clientMessageId`, text and photo (AC-41). */
  retry(messageId: string): void {
    const caseId = this.caseId_();
    const message = this.messages_().find((m) => m.id === messageId);
    if (!caseId || this.streaming_() || message?.status !== 'failed') return;
    this.clearMessageError(messageId);
    const retried = { ...message, status: 'sending' as const };
    this.patchMessage(messageId, () => retried);
    this.startChatTurn(caseId, retried);
  }

  /** Aborts any stream and returns to the empty state (new case). Does not navigate or call DELETE. */
  reset(): void {
    this.discardTurn();
    for (const message of this.messages_()) this.revoke(message.photoUrl);
    this.revoke(this.formPhoto_()?.objectUrl);
    this.setCaseId(null);
    this.form_.set(null);
    this.formPhoto_.set(null);
    this.decision_.set(null);
    this.messages_.set([]);
    this.messageErrors_.set({});
    this.composerRejection_.set(null);
    this.pending.clear();
    this.streaming_.set(false);
    this.submitting_.set(false);
    this.submitError_.set(false);
    this.progressStep_.set(null);
    this.additionalPhotoCount_.set(0);
  }

  private startChatTurn(caseId: string, user: UiMessage): void {
    const pending = this.pending.get(user.id);
    this.streaming_.set(true);
    this.progressStep_.set(null);
    const turn = this.newTurn({
      kind: 'chat',
      userMessageId: user.id,
      carriesPhoto: !!pending?.photo,
    });
    turn.handle = this.api.sendMessage(
      caseId,
      user.clientMessageId!,
      pending?.text ?? user.text,
      pending?.photo,
      this.handlersFor(turn),
    );
  }

  private newTurn(init: Pick<Turn, 'kind' | 'carriesPhoto' | 'userMessageId'>): Turn {
    const turn: Turn = { ...init, finished: false };
    this.turn = turn;
    turn.watchdog = setTimeout(() => this.onWatchdog(turn), WATCHDOG_MS);
    return turn;
  }

  private handlersFor(turn: Turn): StreamHandlers {
    return {
      onEvent: (event) => this.onEvent(turn, event),
      onHttpError: (status, error) => this.onHttpError(turn, status, error),
      onFailure: () => this.fail(turn),
    };
  }

  private onWatchdog(turn: Turn): void {
    if (turn !== this.turn || turn.finished) return;
    turn.watchdog = undefined;
    turn.handle?.abort();
    this.fail(turn);
  }

  private onEvent(turn: Turn, event: CopilotEvent): void {
    if (turn !== this.turn || turn.finished) return;
    switch (event.type) {
      case 'case':
        this.setCaseId(event.data.caseId);
        break;
      case 'progress':
        this.progressStep_.set(event.data.step);
        break;
      case 'decision':
        this.onDecision(turn, event.data);
        break;
      case 'delta':
        if (turn.assistantId) {
          this.patchMessage(turn.assistantId, (m) => ({ ...m, text: m.text + event.data.text }));
        }
        break;
      case 'done':
        this.onDone(turn);
        break;
      case 'error':
        this.fail(turn);
        break;
    }
  }

  private onDecision(turn: Turn, decision: DecisionView): void {
    this.clearWatchdog(turn);
    turn.restoreDecision = this.decision_();
    turn.assistantId = this.nextId('a');
    const shell: UiMessage = {
      id: turn.assistantId,
      role: 'assistant',
      text: '',
      createdAt: new Date(),
      status: 'streaming',
      decision,
      isFirst: turn.kind === 'first',
    };
    this.messages_.update((list) => [...list, shell]);
    this.decision_.set(decision);
    if (turn.kind === 'first') void this.router.navigateByUrl(CHAT_URL);
  }

  private onDone(turn: Turn): void {
    turn.finished = true;
    this.clearWatchdog(turn);
    if (turn.assistantId) {
      this.patchMessage(turn.assistantId, (m) => ({ ...m, status: 'complete' }));
    }
    if (turn.userMessageId) {
      this.patchMessage(turn.userMessageId, (m) => ({ ...m, status: 'complete' }));
      this.pending.delete(turn.userMessageId);
    }
    if (turn.carriesPhoto) this.additionalPhotoCount_.update((n) => n + 1);
    this.streaming_.set(false);
    this.submitting_.set(false);
    this.progressStep_.set(null);
  }

  /**
   * Pre-stream HTTP error (ADR-003 3.7). Chat: 404 and 409 fail the message with their own text;
   * 400, 413 and 422 refuse the send (the composer takes the text back); anything else is generic.
   */
  private onHttpError(turn: Turn, status: number, error: ApiError | null): void {
    if (turn.kind === 'first') return this.fail(turn);
    switch (error?.code) {
      case 'CASE_NOT_FOUND':
        return this.fail(turn, 'chat.error.expired');
      case 'CASE_BUSY':
        return this.fail(turn, 'chat.error.busy');
      case 'PHOTO_LIMIT':
        return this.refuse(turn, 'chat.photoLimit', () =>
          this.additionalPhotoCount_.set(MAX_ADDITIONAL_PHOTOS),
        );
      case 'PAYLOAD_TOO_LARGE':
        return this.refuse(turn, 'form.photo.size');
      case 'VALIDATION': {
        const text = this.pending.get(turn.userMessageId!)?.text ?? '';
        return this.refuse(turn, chatValidationKey(error, text));
      }
      default:
        return this.fail(turn);
    }
  }

  /** The server refused the send before streaming: undo the optimistic message, give the text back. */
  private refuse(turn: Turn, errorKey: TextKey, apply?: () => void): void {
    if (!this.endTurn(turn)) return;
    const userId = turn.userMessageId!;
    const pending = this.pending.get(userId);
    const message = this.messages_().find((m) => m.id === userId);
    this.messages_.update((list) => list.filter((m) => m.id !== userId));
    this.pending.delete(userId);
    this.revoke(message?.photoUrl);
    apply?.();
    this.composerRejection_.set({ text: pending?.text ?? message?.text ?? '', errorKey });
  }

  /** Common end of a failed turn; false when the turn is no longer the live one. */
  private endTurn(turn: Turn): boolean {
    if (turn !== this.turn || turn.finished) return false;
    turn.finished = true;
    this.clearWatchdog(turn);
    turn.handle?.abort();
    this.streaming_.set(false);
    this.submitting_.set(false);
    this.progressStep_.set(null);
    return true;
  }

  /** Stream `error`, truncation, network failure, HTTP error or watchdog: the server committed nothing. */
  private fail(turn: Turn, errorKey: TextKey = 'chat.error.generic'): void {
    if (!this.endTurn(turn)) return;

    if (turn.kind === 'first') {
      const wasOnChat = turn.assistantId !== undefined;
      this.setCaseId(null);
      this.messages_.set([]);
      this.decision_.set(null);
      this.submitError_.set(true);
      if (wasOnChat) void this.router.navigateByUrl(FORM_URL);
      return;
    }

    if (turn.assistantId) {
      const assistantId = turn.assistantId;
      this.messages_.update((list) => list.filter((m) => m.id !== assistantId));
      this.decision_.set(turn.restoreDecision ?? null);
    }
    const userId = turn.userMessageId!;
    this.patchMessage(userId, (m) => ({ ...m, status: 'failed' }));
    this.messageErrors_.update((errors) => ({ ...errors, [userId]: errorKey }));
  }

  private discardTurn(): void {
    const turn = this.turn;
    this.turn = null;
    if (!turn) return;
    turn.finished = true;
    this.clearWatchdog(turn);
    turn.handle?.abort();
  }

  private clearWatchdog(turn: Turn): void {
    if (turn.watchdog !== undefined) clearTimeout(turn.watchdog);
    turn.watchdog = undefined;
  }

  private patchMessage(id: string, change: (message: UiMessage) => UiMessage): void {
    this.messages_.update((list) => list.map((m) => (m.id === id ? change(m) : m)));
  }

  private clearMessageError(id: string): void {
    this.messageErrors_.update((errors) => {
      const rest = { ...errors };
      delete rest[id];
      return rest;
    });
  }

  private nextId(prefix: string): string {
    return `${prefix}${++this.sequence}`;
  }

  private revoke(url: string | undefined): void {
    if (url?.startsWith('blob:') && typeof URL.revokeObjectURL === 'function') {
      URL.revokeObjectURL(url);
    }
  }

  /** `beforeunload` prompt while a case exists (AC-43). */
  private setCaseId(id: string | null): void {
    this.caseId_.set(id);
    if (id && !this.unloadGuardOn) {
      this.window?.addEventListener('beforeunload', this.beforeUnload);
      this.unloadGuardOn = true;
    } else if (!id && this.unloadGuardOn) {
      this.window?.removeEventListener('beforeunload', this.beforeUnload);
      this.unloadGuardOn = false;
    }
  }
}

/** Provider array for app.config.ts: the store is also the form's `CaseSubmitter`. */
export const CASE_STORE_PROVIDERS: Provider[] = [
  CaseStore,
  { provide: CaseSubmitter, useExisting: CaseStore },
];
