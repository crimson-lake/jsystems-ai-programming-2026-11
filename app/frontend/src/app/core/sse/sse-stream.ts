import { EventSourceParserStream } from 'eventsource-parser/stream';
import { ApiError, CopilotEvent, CopilotEventType } from '../model';

/** Why a stream ended without a terminal `done`/`error` event or an HTTP error answer. */
export type StreamFailure = 'NETWORK' | 'TRUNCATED' | 'MALFORMED';

export interface StreamHandlers {
  /** Every typed event, including the terminal `done` or `error`. */
  onEvent(event: CopilotEvent): void;
  /** Non-2xx answer before the stream started; `error` is null when the body is not a JSON ApiError. */
  onHttpError(status: number, error: ApiError | null): void;
  /** Network failure, a stream that ended without `done`/`error`, or an unreadable event. */
  onFailure(reason: StreamFailure): void;
}

const EVENT_TYPES: ReadonlySet<string> = new Set<CopilotEventType>([
  'case',
  'progress',
  'decision',
  'delta',
  'done',
  'error',
]);

async function readApiError(response: Response): Promise<ApiError | null> {
  try {
    const body: unknown = await response.json();
    if (body && typeof body === 'object' && typeof (body as ApiError).code === 'string') {
      return body as ApiError;
    }
  } catch {
    // not JSON
  }
  return null;
}

/**
 * POSTs `body` and parses the answer as server-sent events (ADR-003 section 3.5).
 * Resolves when `done` or `error` arrived, after an HTTP error or failure was reported, or when the
 * caller aborts (then nothing more is reported). It never rejects for transport problems.
 */
export async function openStream(
  url: string,
  body: FormData,
  handlers: StreamHandlers,
  signal: AbortSignal,
): Promise<void> {
  let response: Response;
  try {
    response = await fetch(url, {
      method: 'POST',
      headers: { Accept: 'text/event-stream' },
      body,
      signal,
    });
  } catch {
    if (!signal.aborted) handlers.onFailure('NETWORK');
    return;
  }
  if (signal.aborted) return;

  if (!response.ok) {
    const error = await readApiError(response);
    if (!signal.aborted) handlers.onHttpError(response.status, error);
    return;
  }
  if (!response.body) {
    handlers.onFailure('NETWORK');
    return;
  }

  const reader = response.body
    .pipeThrough(new TextDecoderStream())
    .pipeThrough(new EventSourceParserStream())
    .getReader();
  const onAbort = () => void reader.cancel().catch(() => undefined);
  signal.addEventListener('abort', onAbort, { once: true });

  try {
    for (;;) {
      let result: ReadableStreamReadResult<{ event?: string; data: string }>;
      try {
        result = await reader.read();
      } catch {
        if (!signal.aborted) handlers.onFailure('NETWORK');
        return;
      }
      if (signal.aborted) return;
      if (result.done) {
        handlers.onFailure('TRUNCATED');
        return;
      }

      const { event: type, data } = result.value;
      let parsed: unknown;
      try {
        parsed = JSON.parse(data);
      } catch {
        parsed = undefined;
      }
      if (!type || !EVENT_TYPES.has(type) || parsed === undefined) {
        void reader.cancel().catch(() => undefined);
        handlers.onFailure('MALFORMED');
        return;
      }
      handlers.onEvent({ type, data: parsed } as CopilotEvent);
      if (type === 'done' || type === 'error') {
        void reader.cancel().catch(() => undefined);
        return;
      }
    }
  } finally {
    signal.removeEventListener('abort', onAbort);
  }
}
