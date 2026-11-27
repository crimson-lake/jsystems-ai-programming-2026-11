import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { CaseSubmitter } from '../../features/case-form/case-submitter';
import {
  RecordedBlock,
  httpResponse,
  ofType,
  recordedBlocks,
  recordedEvents,
  splice,
  sseResponse,
} from '../../../testing/sse-test-utils';
import { ApiError, CaseFormValue, DecisionView, ReducedPhoto, UiMessage } from '../model';
import { t } from '../../shared/texts.pl';
import {
  CASE_STORE_PROVIDERS,
  CaseStore,
  WATCHDOG_MS,
  chatValidationKey,
} from './case.store';

@Component({ template: '' })
class DummyPage {}

// Real example photo bytes (assets/example-images/README.md).
const PHONE = readFileSync(join(process.cwd(), '..', '..', 'assets', 'example-images', 'phone-1.jpg'));

const SUCCESS = recordedEvents('case-success.sse');
const ERROR = recordedEvents('case-error.sse');
const CHAT_TEXT = recordedEvents('chat-text.sse');
const CHAT_PHOTO = recordedEvents('chat-photo.sse');
const CHAT_REPLAY = recordedEvents('chat-replay.sse');
const CHAT_TIMEOUT = recordedEvents('chat-error-timeout.sse');
const CASE = ofType(SUCCESS, 'case');
const PROGRESS = ofType(SUCCESS, 'progress'); // UPLOAD_RECEIVED, ANALYZING_PHOTO, PREPARING_DECISION, WRITING_REPLY
const DECISION = ofType(SUCCESS, 'decision');
const DELTAS = ofType(SUCCESS, 'delta');
const DONE = ofType(SUCCESS, 'done');
const ERROR_EVENT = ofType(ERROR, 'error');
const RECORDED_TEXT = DELTAS.map((d) => (d.data as { text: string }).text).join('');
const RECORDED_DECISION = DECISION[0].data as DecisionView;

/** A chat turn as the backend streams it (ADR-002 section 5), built only from recorded blocks. */
const CHAT_TURN: RecordedBlock[] = [PROGRESS[2], ...DECISION, PROGRESS[3], ...DELTAS, ...DONE];
/** First-submit prefix: case, progress, decision, writing, first deltas. */
const FIRST_UNTIL_DELTAS = [...CASE, ...PROGRESS, ...DECISION, ...DELTAS.slice(0, 5)];

function photo(): ReducedPhoto {
  return {
    blob: new Blob([PHONE as BlobPart], { type: 'image/jpeg' }),
    objectUrl: 'blob:photo-' + Math.random(),
    originalName: 'phone-1.jpg',
    width: 10,
    height: 10,
  };
}

function formValue(): CaseFormValue {
  return {
    caseType: 'RETURN',
    category: 'SMARTPHONE',
    model: 'Galaxy S23',
    purchaseDate: new Date(2026, 10, 16),
    reason: 'Zmiana zdania.',
    photo: new File([PHONE as BlobPart], 'phone-1.jpg', { type: 'image/jpeg' }),
  };
}

interface Reply {
  blocks: RecordedBlock[];
  hang?: boolean;
  status?: number;
  network?: boolean;
  /** Recorded pre-stream HTTP error (`http-*.json`) answered as the real response. */
  http?: string;
}

describe('CaseStore', () => {
  let store: CaseStore;
  let router: Router;
  let replies: Reply[];
  let signals: AbortSignal[];
  let bodies: FormData[];
  let urls: string[];

  beforeEach(async () => {
    replies = [];
    signals = [];
    bodies = [];
    urls = [];
    vi.stubGlobal(
      'fetch',
      vi.fn((url: string, init: RequestInit) => {
        urls.push(url);
        bodies.push(init.body as FormData);
        signals.push(init.signal as AbortSignal);
        const reply = replies.shift();
        if (!reply) return Promise.reject(new Error('unexpected fetch'));
        if (reply.network) return Promise.reject(new TypeError('Failed to fetch'));
        if (reply.http) return Promise.resolve(httpResponse(reply.http));
        if (reply.status) return Promise.resolve(new Response(null, { status: reply.status }));
        return Promise.resolve(
          sseResponse([splice(reply.blocks)], { hang: reply.hang, signal: init.signal }),
        );
      }),
    );
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: '', component: DummyPage },
          { path: 'sprawa', component: DummyPage },
        ]),
        ...CASE_STORE_PROVIDERS,
      ],
    });
    store = TestBed.inject(CaseStore);
    router = TestBed.inject(Router);
    await router.navigateByUrl('/');
  });

  afterEach(() => {
    store.reset();
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  const idle = () => vi.waitFor(() => expect(store.streaming()).toBe(false));

  async function submitOk(): Promise<void> {
    replies.push({ blocks: [...CASE, ...PROGRESS, ...DECISION, ...DELTAS, ...DONE] });
    store.submitCase(formValue(), photo());
    await idle();
  }

  describe('first submit', () => {
    it('is provided as the CaseSubmitter implementation', () => {
      expect(TestBed.inject(CaseSubmitter)).toBe(store);
    });

    it('sets submitting synchronously and clears a previous submit error', async () => {
      replies.push({ blocks: ERROR_EVENT });
      store.submitCase(formValue(), photo());
      await idle();
      expect(store.submitError()).toBe(true);

      replies.push({ blocks: FIRST_UNTIL_DELTAS, hang: true });
      store.submit(formValue(), photo());
      expect(store.submitting()).toBe(true);
      expect(store.submitError()).toBe(false);
      expect(store.streaming()).toBe(true);
      expect(store.canSend()).toBe(false);
    });

    it('applies the recorded success transcript: case, decision, text, navigation, done', async () => {
      const value = formValue();
      const reduced = photo();
      replies.push({ blocks: [...CASE, ...PROGRESS, ...DECISION, ...DELTAS, ...DONE] });
      store.submitCase(value, reduced);
      await idle();

      expect(urls).toEqual(['/api/cases']);
      expect(store.caseId()).toBe((CASE[0].data as { caseId: string }).caseId);
      expect(store.decision()).toEqual(RECORDED_DECISION);
      expect(store.messages()).toHaveLength(1);
      const [message] = store.messages();
      expect(message.role).toBe('assistant');
      expect(message.isFirst).toBe(true);
      expect(message.status).toBe('complete');
      expect(message.text).toBe(RECORDED_TEXT);
      expect(message.decision).toEqual(RECORDED_DECISION);
      expect(router.url).toBe('/sprawa');
      expect(store.submitting()).toBe(false);
      expect(store.submitError()).toBe(false);
      expect(store.progressStep()).toBeNull();
      expect(store.canSend()).toBe(true);
      expect(store.additionalPhotoCount()).toBe(0);
      expect(store.canAttachPhoto()).toBe(true);
      expect(store.form()).toBe(value);
      expect(store.formPhoto()).toBe(reduced);
    });

    it('follows `progress` events while staying on the form', async () => {
      replies.push({ blocks: [...CASE, PROGRESS[0], PROGRESS[1]], hang: true });
      store.submitCase(formValue(), photo());
      await vi.waitFor(() => expect(store.progressStep()).toBe('ANALYZING_PHOTO'));
      expect(router.url).toBe('/');
      expect(store.caseId()).toBe((CASE[0].data as { caseId: string }).caseId);
    });

    it('navigates to /sprawa on `decision`, before `done`, and appends deltas', async () => {
      replies.push({ blocks: FIRST_UNTIL_DELTAS, hang: true });
      store.submitCase(formValue(), photo());
      await vi.waitFor(() => expect(router.url).toBe('/sprawa'));
      const partial = DELTAS.slice(0, 5)
        .map((d) => (d.data as { text: string }).text)
        .join('');
      await vi.waitFor(() => expect(store.messages()[0]?.text).toBe(partial));
      expect(store.messages()[0].status).toBe('streaming');
      expect(store.streaming()).toBe(true);
      expect(store.decision()).toEqual(RECORDED_DECISION);
    });

    it('stays on the form with the banner when `error` arrives before `decision`', async () => {
      const value = formValue();
      const reduced = photo();
      replies.push({ blocks: ERROR });
      store.submitCase(value, reduced);
      await idle();

      expect(store.submitError()).toBe(true);
      expect(store.submitting()).toBe(false);
      expect(store.caseId()).toBeNull();
      expect(store.messages()).toEqual([]);
      expect(store.progressStep()).toBeNull();
      expect(router.url).toBe('/');
      expect(store.form()).toBe(value);
      expect(store.formPhoto()).toBe(reduced);
    });

    it('goes back to the form with values and photo restored on `error` after `decision`', async () => {
      const value = formValue();
      const reduced = photo();
      // Real decision + deltas from the success recording, then the real error event (ADR-002 3.5).
      replies.push({ blocks: [...FIRST_UNTIL_DELTAS, ...ERROR_EVENT] });
      store.submitCase(value, reduced);
      await idle();

      expect(router.url).toBe('/');
      expect(store.submitError()).toBe(true);
      expect(store.submitting()).toBe(false);
      expect(store.caseId()).toBeNull();
      expect(store.messages()).toEqual([]);
      expect(store.decision()).toBeNull();
      expect(store.form()).toBe(value);
      expect(store.formPhoto()).toBe(reduced);
      expect(store.canSend()).toBe(false);
    });

    it.each([
      ['before decision', [...CASE, PROGRESS[0]], '/'],
      ['after decision', FIRST_UNTIL_DELTAS, '/'],
    ])('treats a recording truncated before `done` (%s) as an error', async (_name, blocks, url) => {
      replies.push({ blocks });
      store.submitCase(formValue(), photo());
      await idle();
      expect(store.submitError()).toBe(true);
      expect(router.url).toBe(url);
      expect(store.caseId()).toBeNull();
      expect(store.messages()).toEqual([]);
    });

    it('treats a network failure like an error', async () => {
      replies.push({ blocks: [], network: true });
      store.submitCase(formValue(), photo());
      await idle();
      expect(store.submitError()).toBe(true);
      expect(store.submitting()).toBe(false);
    });

    it('treats a non-2xx answer with an unparsable body as an error (HTTP error table: T34)', async () => {
      replies.push({ blocks: [], status: 502 });
      store.submitCase(formValue(), photo());
      await idle();
      expect(store.submitError()).toBe(true);
      expect(store.submitting()).toBe(false);
    });

    it('ignores a second submit while one is running', () => {
      replies.push({ blocks: CASE, hang: true });
      store.submitCase(formValue(), photo());
      store.submitCase(formValue(), photo());
      expect(urls).toHaveLength(1);
    });

    it('allows the same data to be resubmitted after an error', async () => {
      replies.push({ blocks: ERROR_EVENT });
      store.submitCase(formValue(), photo());
      await idle();
      await submitOk();
      expect(store.submitError()).toBe(false);
      expect(router.url).toBe('/sprawa');
      expect(store.messages()).toHaveLength(1);
    });
  });

  describe('watchdog (60 s without `decision`)', () => {
    beforeEach(() => vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout'] }));

    it('has a 60 s budget', () => expect(WATCHDOG_MS).toBe(60_000));

    it('aborts the stream and shows the submit error after 60 s without a decision', async () => {
      replies.push({ blocks: [...CASE, PROGRESS[0], PROGRESS[1]], hang: true });
      store.submitCase(formValue(), photo());
      await vi.advanceTimersByTimeAsync(WATCHDOG_MS - 1);
      expect(store.submitting()).toBe(true);
      expect(signals[0].aborted).toBe(false);

      await vi.advanceTimersByTimeAsync(1);
      expect(signals[0].aborted).toBe(true);
      expect(store.submitting()).toBe(false);
      expect(store.submitError()).toBe(true);
      expect(store.caseId()).toBeNull();
      expect(router.url).toBe('/');
    });

    it('is cleared by the `decision` event', async () => {
      replies.push({ blocks: FIRST_UNTIL_DELTAS, hang: true });
      store.submitCase(formValue(), photo());
      await vi.waitFor(() => expect(store.decision()).not.toBeNull());
      await vi.advanceTimersByTimeAsync(WATCHDOG_MS * 2);
      expect(signals[0].aborted).toBe(false);
      expect(store.submitError()).toBe(false);
      expect(store.messages()[0].status).toBe('streaming');
    });

    it('marks a chat message failed when no decision arrives in 60 s', async () => {
      replies.push({ blocks: [...CASE, ...PROGRESS, ...DECISION, ...DELTAS, ...DONE] });
      store.submitCase(formValue(), photo());
      await vi.waitFor(() => expect(store.streaming()).toBe(false));
      replies.push({ blocks: [PROGRESS[2]], hang: true });
      store.sendMessage('Pytanie');
      await vi.advanceTimersByTimeAsync(WATCHDOG_MS);
      expect(signals[1].aborted).toBe(true);
      const user = store.messages().find((m: UiMessage) => m.role === 'user')!;
      expect(user.status).toBe('failed');
      expect(store.streaming()).toBe(false);
      expect(store.messageErrors()[user.id]).toBe('chat.error.generic');
    });
  });

  describe('chat turns', () => {
    beforeEach(async () => submitOk());

    it('appends the user message, locks sending and posts clientMessageId and text', () => {
      replies.push({ blocks: CHAT_TURN, hang: true });
      store.sendMessage('Jaką datę zakupu podałem?');
      expect(store.streaming()).toBe(true);
      expect(store.canSend()).toBe(false);
      const user = store.messages().at(-1)!;
      expect(user).toMatchObject({
        role: 'user',
        text: 'Jaką datę zakupu podałem?',
        status: 'sending',
        isFirst: false,
      });
      expect(user.clientMessageId).toMatch(/^[0-9a-f-]{36}$/);
      expect(urls.at(-1)).toBe(`/api/cases/${store.caseId()}/messages`);
      expect(bodies.at(-1)!.get('clientMessageId')).toBe(user.clientMessageId);
      expect(bodies.at(-1)!.get('text')).toBe('Jaką datę zakupu podałem?');
    });

    it('ignores sendMessage while a turn is running', () => {
      replies.push({ blocks: CHAT_TURN, hang: true });
      store.sendMessage('Pierwsze');
      store.sendMessage('Drugie');
      expect(urls).toHaveLength(2);
    });

    it('completes a turn from recorded events: reply, decision, complete statuses', async () => {
      replies.push({ blocks: CHAT_TURN });
      store.sendMessage('Pytanie');
      await idle();
      const [, user, reply] = store.messages();
      expect(user.status).toBe('complete');
      expect(reply).toMatchObject({ role: 'assistant', isFirst: false, status: 'complete' });
      expect(reply.text).toBe(RECORDED_TEXT);
      expect(reply.decision).toEqual(RECORDED_DECISION);
      expect(store.canSend()).toBe(true);
      expect(store.progressStep()).toBeNull();
      expect(router.url).toBe('/sprawa');
    });

    it('counts a photo only on `done`, never on send, and stops attaching at 3', async () => {
      for (let i = 0; i < 3; i++) {
        expect(store.canAttachPhoto()).toBe(true);
        replies.push({ blocks: CHAT_TURN });
        store.sendMessage('Zdjęcie ' + i, photo());
        expect(store.additionalPhotoCount()).toBe(i);
        expect(bodies.at(-1)!.has('photo')).toBe(true);
        await idle();
        expect(store.additionalPhotoCount()).toBe(i + 1);
      }
      expect(store.canAttachPhoto()).toBe(false);
    });

    it('does not count a text-only turn', async () => {
      replies.push({ blocks: CHAT_TURN });
      store.sendMessage('Sam tekst');
      await idle();
      expect(store.additionalPhotoCount()).toBe(0);
    });

    it('on `error` after `decision`: user message failed, partial reply discarded, decision restored', async () => {
      const before = store.decision();
      replies.push({ blocks: [PROGRESS[2], ...DECISION, PROGRESS[3], ...DELTAS.slice(0, 5), ...ERROR_EVENT] });
      store.sendMessage('Pytanie');
      await idle();
      const messages = store.messages();
      expect(messages).toHaveLength(2);
      expect(messages[1]).toMatchObject({ role: 'user', status: 'failed' });
      expect(store.decision()).toBe(before);
      expect(store.messageErrors()[messages[1].id]).toBe('chat.error.generic');
      expect(store.canSend()).toBe(true);
      expect(store.additionalPhotoCount()).toBe(0);
    });

    it('marks the message failed when the recording is truncated before `done`', async () => {
      replies.push({ blocks: [PROGRESS[2], ...DECISION, PROGRESS[3], ...DELTAS.slice(0, 5)] });
      store.sendMessage('Pytanie');
      await idle();
      expect(store.messages().at(-1)).toMatchObject({ role: 'user', status: 'failed' });
      expect(store.messages()).toHaveLength(2);
    });

    it('retry re-POSTs the same clientMessageId, text and photo; a replay is a normal reply', async () => {
      const attached = photo();
      replies.push({ blocks: [PROGRESS[2], ...ERROR_EVENT] });
      store.sendMessage('Pytanie', attached);
      await idle();
      const failed = store.messages().at(-1)!;
      expect(failed.status).toBe('failed');
      const firstPost = bodies.at(-1)!;

      // Replay of a committed turn: decision, one delta with the full text, done (ADR-002 3.4).
      replies.push({ blocks: [...DECISION, DELTAS[0], ...DONE] });
      store.retry(failed.id);
      expect(store.messages().at(-1)).toMatchObject({ id: failed.id, status: 'sending' });
      expect(store.messageErrors()[failed.id]).toBeUndefined();
      await idle();

      const secondPost = bodies.at(-1)!;
      expect(secondPost.get('clientMessageId')).toBe(failed.clientMessageId);
      expect(secondPost.get('clientMessageId')).toBe(firstPost.get('clientMessageId'));
      expect(secondPost.get('text')).toBe('Pytanie');
      expect((secondPost.get('photo') as Blob).size).toBe(attached.blob.size);
      const messages = store.messages();
      expect(messages.filter((m: UiMessage) => m.role === 'user')).toHaveLength(1);
      expect(messages.at(-1)).toMatchObject({ role: 'assistant', status: 'complete' });
      expect(messages.at(-1)!.text).toBe((DELTAS[0].data as { text: string }).text);
      expect(messages.find((m: UiMessage) => m.id === failed.id)!.status).toBe('complete');
      expect(store.additionalPhotoCount()).toBe(1);
    });

    it('retry is ignored for a message that has not failed', async () => {
      replies.push({ blocks: CHAT_TURN });
      store.sendMessage('Pytanie');
      await idle();
      const count = urls.length;
      store.retry(store.messages()[1].id);
      expect(urls).toHaveLength(count);
    });

    it('marks a chat message failed on a network failure', async () => {
      replies.push({ blocks: [], network: true });
      store.sendMessage('Pytanie');
      await idle();
      expect(store.messages().at(-1)).toMatchObject({ role: 'user', status: 'failed' });
    });
  });

  describe('chat turns from the T27 recordings', () => {
    beforeEach(async () => submitOk());

    const textOf = (events: RecordedBlock[]) =>
      ofType(events, 'delta')
        .map((d) => (d.data as { text: string }).text)
        .join('');
    const decisionOf = (events: RecordedBlock[]) => ofType(events, 'decision')[0].data as DecisionView;

    it('chat-text.sse (with its : ping comment): reply text, decision and completion', async () => {
      replies.push({ blocks: recordedBlocks('chat-text.sse') });
      store.sendMessage('Jaką datę zakupu podałem?');
      await idle();
      const [, user, reply] = store.messages();
      expect(user.status).toBe('complete');
      expect(reply.text).toBe(textOf(CHAT_TEXT));
      expect(reply.decision).toEqual(decisionOf(CHAT_TEXT));
      expect(reply.status).toBe('complete');
      expect(store.decision()).toEqual(decisionOf(CHAT_TEXT));
      expect(store.additionalPhotoCount()).toBe(0);
    });

    it('chat-photo.sse: follows ANALYZING_PHOTO and counts the photo only on `done`', async () => {
      replies.push({ blocks: recordedBlocks('chat-photo.sse') });
      store.sendMessage('Przesyłam dodatkowe zdjęcie.', photo());
      await vi.waitFor(() => expect(store.messages()).toHaveLength(3));
      await idle();
      expect(store.messages().at(-1)!.text).toBe(textOf(CHAT_PHOTO));
      expect(store.progressStep()).toBeNull();
      expect(store.additionalPhotoCount()).toBe(1);
    });

    it('chat-error-timeout.sse: a real TIMEOUT error marks the message failed with the generic error', async () => {
      replies.push({ blocks: recordedBlocks('chat-error-timeout.sse') });
      store.sendMessage('Przesyłam dodatkowe zdjęcie.', photo());
      await idle();
      const user = store.messages().at(-1)!;
      expect(store.messages()).toHaveLength(2);
      expect(user.status).toBe('failed');
      expect(store.messageErrors()[user.id]).toBe('chat.error.generic');
      expect(store.additionalPhotoCount()).toBe(0);
      expect(store.composerRejection()).toBeNull();
    });

    it('error after decision (spliced: real decision + deltas of chat-text, real error of chat-error-timeout)', async () => {
      const before = store.decision();
      replies.push({
        blocks: [...ofType(CHAT_TEXT, 'decision'), ...ofType(CHAT_TEXT, 'delta').slice(0, 4), ...ofType(CHAT_TIMEOUT, 'error')],
      });
      store.sendMessage('Pytanie');
      await idle();
      expect(store.messages()).toHaveLength(2);
      expect(store.messages()[1]).toMatchObject({ role: 'user', status: 'failed' });
      expect(store.decision()).toBe(before);
      expect(store.messageErrors()[store.messages()[1].id]).toBe('chat.error.generic');
    });

    it('replay: a retry after TIMEOUT is answered with chat-replay.sse and completes like a normal reply', async () => {
      replies.push({ blocks: recordedBlocks('chat-error-timeout.sse') });
      store.sendMessage('Jaką datę zakupu podałem?');
      await idle();
      const failed = store.messages().at(-1)!;
      expect(failed.status).toBe('failed');

      replies.push({ blocks: recordedBlocks('chat-replay.sse') });
      store.retry(failed.id);
      await idle();
      expect(bodies.at(-1)!.get('clientMessageId')).toBe(failed.clientMessageId);
      const messages = store.messages();
      expect(messages).toHaveLength(3);
      expect(messages[1]).toMatchObject({ id: failed.id, status: 'complete' });
      expect(messages[2]).toMatchObject({ role: 'assistant', status: 'complete' });
      expect(messages[2].text).toBe(textOf(CHAT_REPLAY));
      expect(messages[2].decision).toEqual(decisionOf(CHAT_REPLAY));
      expect(store.messageErrors()[failed.id]).toBeUndefined();
    });
  });

  describe('HTTP error table for chat (ADR-003 3.7), from the recorded responses', () => {
    beforeEach(async () => submitOk());

    const sendWith = async (http: string, text = 'Pytanie', withPhoto = false) => {
      replies.push({ blocks: [], http });
      const reduced = withPhoto ? photo() : undefined;
      store.sendMessage(text, reduced);
      await idle();
      return reduced;
    };

    it.each([
      ['http-409.json', 'chat.error.busy'],
      ['http-404.json', 'chat.error.expired'],
    ] as const)('%s keeps the failed message with %s', async (file, key) => {
      await sendWith(file);
      const user = store.messages().at(-1)!;
      expect(store.messages()).toHaveLength(2);
      expect(user).toMatchObject({ role: 'user', text: 'Pytanie', status: 'failed' });
      expect(store.messageErrors()[user.id]).toBe(key);
      expect(store.composerRejection()).toBeNull();
      expect(store.canSend()).toBe(true);
    });

    it('409 CASE_BUSY can be retried with the same clientMessageId', async () => {
      await sendWith('http-409.json');
      const user = store.messages().at(-1)!;
      replies.push({ blocks: recordedBlocks('chat-text.sse') });
      store.retry(user.id);
      await idle();
      expect(bodies.at(-1)!.get('clientMessageId')).toBe(user.clientMessageId);
      expect(store.messages().find((m: UiMessage) => m.id === user.id)!.status).toBe('complete');
      expect(store.messageErrors()[user.id]).toBeUndefined();
    });

    it('an unparsable error body (no recording: a gateway page) is the generic error with retry', async () => {
      replies.push({ blocks: [], status: 502 });
      store.sendMessage('Pytanie');
      await idle();
      const user = store.messages().at(-1)!;
      expect(user.status).toBe('failed');
      expect(store.messageErrors()[user.id]).toBe('chat.error.generic');
    });

    it('422 PHOTO_LIMIT: removes the message, hands the text back, drops the photo and blocks attaching', async () => {
      const reduced = await sendWith('http-422.json', 'Opis zdjęcia', true);
      expect(store.messages()).toHaveLength(1);
      expect(store.composerRejection()).toEqual({ text: 'Opis zdjęcia', errorKey: 'chat.photoLimit' });
      expect(store.canAttachPhoto()).toBe(false);
      expect(store.additionalPhotoCount()).toBe(3);
      expect(store.messageErrors()).toEqual({});
      expect(store.canSend()).toBe(true);
      expect(reduced!.objectUrl).toBeTruthy();
    });

    it('413 PAYLOAD_TOO_LARGE: removes the message, keeps the text, error form.photo.size', async () => {
      await sendWith('http-413.json', 'Duże zdjęcie', true);
      expect(store.messages()).toHaveLength(1);
      expect(store.composerRejection()).toEqual({ text: 'Duże zdjęcie', errorKey: 'form.photo.size' });
      expect(store.canAttachPhoto()).toBe(true);
    });

    it('400 VALIDATION: removes the message and reports chat.message.empty for the recorded text error', async () => {
      await sendWith('http-400.json', '');
      expect(store.messages()).toHaveLength(1);
      expect(store.composerRejection()).toEqual({ text: '', errorKey: 'chat.message.empty' });
    });

    it('400 VALIDATION for a text over 2000 characters reports chat.message.tooLong', async () => {
      const long = 'a'.repeat(2001);
      await sendWith('http-400.json', long);
      expect(store.composerRejection()).toEqual({ text: long, errorKey: 'chat.message.tooLong' });
    });

    it('clearComposerRejection and the next send clear the rejection', async () => {
      await sendWith('http-413.json', 'x', true);
      store.clearComposerRejection();
      expect(store.composerRejection()).toBeNull();
      await sendWith('http-413.json', 'y', true);
      expect(store.composerRejection()).not.toBeNull();
      replies.push({ blocks: recordedBlocks('chat-text.sse'), hang: true });
      store.sendMessage('z');
      expect(store.composerRejection()).toBeNull();
    });

    it('reset clears a pending rejection', async () => {
      await sendWith('http-422.json', 'x', true);
      store.reset();
      expect(store.composerRejection()).toBeNull();
    });

    it('revokes the photo object URL of a rejected message (nothing shows it any more)', async () => {
      const revoke = vi.fn();
      vi.stubGlobal('URL', Object.assign(URL, { revokeObjectURL: revoke }));
      replies.push({ blocks: [], http: 'http-413.json' });
      store.sendMessage('x', { ...photo(), objectUrl: 'blob:rejected' });
      await idle();
      expect(revoke).toHaveBeenCalledWith('blob:rejected');
    });
  });

  describe('leaving the page and reset', () => {
    const leave = () => {
      const event = new Event('beforeunload', { cancelable: true });
      window.dispatchEvent(event);
      return event.defaultPrevented;
    };

    it('asks for confirmation only while a case exists', async () => {
      expect(leave()).toBe(false);
      await submitOk();
      expect(leave()).toBe(true);
      store.reset();
      expect(leave()).toBe(false);
    });

    it('does not keep the prompt after a failed first submit', async () => {
      replies.push({ blocks: [...FIRST_UNTIL_DELTAS, ...ERROR_EVENT] });
      store.submitCase(formValue(), photo());
      await idle();
      expect(leave()).toBe(false);
    });

    it('reset aborts the running stream and clears everything', async () => {
      replies.push({ blocks: FIRST_UNTIL_DELTAS, hang: true });
      store.submitCase(formValue(), photo());
      await vi.waitFor(() => expect(store.messages()).toHaveLength(1));
      store.reset();
      expect(signals[0].aborted).toBe(true);
      expect(store.caseId()).toBeNull();
      expect(store.form()).toBeNull();
      expect(store.formPhoto()).toBeNull();
      expect(store.decision()).toBeNull();
      expect(store.messages()).toEqual([]);
      expect(store.streaming()).toBe(false);
      expect(store.submitting()).toBe(false);
      expect(store.submitError()).toBe(false);
      expect(store.progressStep()).toBeNull();
      expect(store.additionalPhotoCount()).toBe(0);
      expect(store.messageErrors()).toEqual({});
    });
  });
});

/**
 * T50 finding 5: the chat 400 VALIDATION mapping. No recorded chat 400 with a photo field error exists
 * (only `http-400.json`, a text error), so the photo cases are tested on the ApiError value object.
 */
describe('chatValidationKey', () => {
  const validation = (field: string, message: string): ApiError => ({
    code: 'VALIDATION',
    fieldErrors: [{ field, message }],
  });

  it('maps a photo type error to form.photo.type', () => {
    expect(chatValidationKey(validation('photo', t('form.photo.type')), 'Tekst')).toBe('form.photo.type');
  });

  it('maps a photo size error to form.photo.size', () => {
    expect(chatValidationKey(validation('photo', t('form.photo.size')), '')).toBe('form.photo.size');
  });

  it('maps an unknown photo message to form.photo.type, never to chat.message.empty', () => {
    expect(chatValidationKey(validation('photo', 'Inny błąd zdjęcia'), '')).toBe('form.photo.type');
  });

  it('prefers the photo error when the text is also too long', () => {
    expect(chatValidationKey(validation('photo', t('form.photo.size')), 'a'.repeat(2001))).toBe(
      'form.photo.size',
    );
  });

  it('keeps the text mappings: too long by length or by message, otherwise empty', () => {
    expect(chatValidationKey(validation('text', t('chat.message.empty')), 'a'.repeat(2001))).toBe(
      'chat.message.tooLong',
    );
    expect(chatValidationKey(validation('text', t('chat.message.tooLong')), 'krótki')).toBe(
      'chat.message.tooLong',
    );
    expect(chatValidationKey(validation('text', t('chat.message.empty')), '')).toBe('chat.message.empty');
    expect(chatValidationKey({ code: 'VALIDATION' }, '')).toBe('chat.message.empty');
  });
});
