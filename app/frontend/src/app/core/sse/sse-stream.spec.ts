import {
  chunkEvery,
  httpResponse,
  recordedHttp,
  ofType,
  readRecording,
  recordedBlocks,
  recordedEvents,
  splice,
  splitAt,
  sseResponse,
} from '../../../testing/sse-test-utils';
import { CopilotEvent } from '../model';
import { StreamHandlers, openStream } from './sse-stream';

/** Protocol inputs are the byte-exact backend recordings (plan T26); see src/testing/captures/README.md. */
describe('openStream', () => {
  const body = new FormData();
  let events: CopilotEvent[];
  let failures: string[];
  let httpErrors: { status: number; error: unknown }[];
  let handlers: StreamHandlers;

  beforeEach(() => {
    events = [];
    failures = [];
    httpErrors = [];
    handlers = {
      onEvent: (event) => events.push(event),
      onHttpError: (status, error) => httpErrors.push({ status, error }),
      onFailure: (reason) => failures.push(reason),
    };
  });

  afterEach(() => vi.unstubAllGlobals());

  function stubFetch(
    chunks: Uint8Array[],
    options: { hang?: boolean; status?: number } = {},
  ): ReturnType<typeof vi.fn> {
    const fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(sseResponse(chunks, { ...options, signal: init?.signal })),
    );
    vi.stubGlobal('fetch', fetchMock);
    return fetchMock;
  }

  const expectedTypes = (name: string) => recordedEvents(name).map((b) => b.type);

  it('POSTs the FormData with the abort signal and asks for an event stream', async () => {
    const fetchMock = stubFetch([readRecording('case-success.sse')]);
    const controller = new AbortController();
    await openStream('/api/cases', body, handlers, controller.signal);
    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/cases');
    expect(init.method).toBe('POST');
    expect(init.body).toBe(body);
    expect(init.signal).toBe(controller.signal);
    expect(new Headers(init.headers).get('accept')).toBe('text/event-stream');
  });

  it('delivers the recorded success transcript as typed events in order, ignoring `: ping`', async () => {
    stubFetch([readRecording('case-success.sse')]);
    await openStream('/api/cases', body, handlers, new AbortController().signal);

    expect(recordedBlocks('case-success.sse').some((b) => b.type === null)).toBe(true);
    expect(events.map((e) => e.type)).toEqual(expectedTypes('case-success.sse'));
    expect(events.map((e) => e.data)).toEqual(recordedEvents('case-success.sse').map((b) => b.data));
    expect(events[0].type).toBe('case');
    expect(events.at(-1)?.type).toBe('done');
    expect(events.filter((e) => e.type === 'delta')).toHaveLength(49);
    expect(failures).toEqual([]);
  });

  it.each([1, 2, 3, 7, 64, 500])('yields identical events when re-chunked every %i bytes', async (size) => {
    stubFetch(chunkEvery(readRecording('case-success.sse'), size));
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.map((e) => e.data)).toEqual(recordedEvents('case-success.sse').map((b) => b.data));
    expect(failures).toEqual([]);
  });

  it('survives chunk boundaries inside a multi-byte Polish character and inside a line', async () => {
    const bytes = readRecording('case-success.sse');
    // First two-byte UTF-8 lead byte in the recording (e.g. the "ę" of "zdjęciu"); cut between its bytes.
    const lead = bytes.findIndex((b) => b >= 0xc2 && b <= 0xdf);
    expect(lead).toBeGreaterThan(0);
    const midLine = bytes.indexOf(0x64 /* d */, 20);
    stubFetch(splitAt(bytes, midLine + 3, lead + 1));
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.map((e) => e.data)).toEqual(recordedEvents('case-success.sse').map((b) => b.data));
    const text = events
      .filter((e) => e.type === 'delta')
      .map((e) => (e.data as { text: string }).text)
      .join('');
    expect(text).toMatch(/[ąćęłńóśźż]/);
  });

  it('resolves on the recorded error event and reports it as an event', async () => {
    stubFetch([readRecording('case-error.sse')]);
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.map((e) => e.type)).toEqual(expectedTypes('case-error.sse'));
    expect(events.at(-1)).toEqual({
      type: 'error',
      data: recordedEvents('case-error.sse').at(-1)?.data,
    });
    expect(failures).toEqual([]);
  });

  it('resolves right after `done` even when the server keeps the connection open', async () => {
    stubFetch([readRecording('case-success.sse')], { hang: true });
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.at(-1)?.type).toBe('done');
    expect(failures).toEqual([]);
  });

  it('reports a stream truncated before `done` as a failure (no done event)', async () => {
    const all = recordedEvents('case-success.sse');
    stubFetch([splice(all.slice(0, -1))]);
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.map((e) => e.type)).toEqual(expectedTypes('case-success.sse').slice(0, -1));
    expect(failures).toEqual(['TRUNCATED']);
  });

  it('reports a recording cut in the middle of the `done` line as a failure', async () => {
    const bytes = readRecording('case-success.sse');
    const doneAt = new TextDecoder().decode(bytes).indexOf('event:done');
    stubFetch(chunkEvery(bytes.slice(0, doneAt + 20), 13));
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(events.some((e) => e.type === 'done')).toBe(false);
    expect(failures).toEqual(['TRUNCATED']);
  });

  it('reports an event whose data line is cut off as malformed', async () => {
    const delta = ofType(recordedEvents('case-success.sse'), 'delta')[3];
    const cut = delta.raw.slice(0, delta.raw.indexOf('"text"') + 4) + '\n\n';
    stubFetch([splice(ofType(recordedEvents('case-success.sse'), 'case'), [{ ...delta, raw: cut }])]);
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(failures).toEqual(['MALFORMED']);
  });

  it('reports a network failure before any response', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(failures).toEqual(['NETWORK']);
    expect(events).toEqual([]);
  });

  it('reports a non-2xx answer before the stream as an HTTP error (unparsable body: null)', async () => {
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(new Response(null, { status: 502 }))));
    await openStream('/api/cases', body, handlers, new AbortController().signal);
    expect(httpErrors).toEqual([{ status: 502, error: null }]);
    expect(events).toEqual([]);
    expect(failures).toEqual([]);
  });

  it('stays silent and resolves when the caller aborts mid-stream', async () => {
    const early = splice(recordedEvents('case-success.sse').slice(0, 3));
    stubFetch([early], { hang: true });
    const controller = new AbortController();
    const done = openStream('/api/cases', body, handlers, controller.signal);
    await vi.waitFor(() => expect(events).toHaveLength(3));
    controller.abort();
    await done;
    expect(events).toHaveLength(3);
    expect(failures).toEqual([]);
  });
  describe('recorded HTTP error responses (T27 http-*.json)', () => {
    it.each(['http-400.json', 'http-404.json', 'http-409.json', 'http-413.json', 'http-422.json'])(
      '%s is reported with its status and parsed JSON ApiError',
      async (file) => {
        vi.stubGlobal('fetch', vi.fn().mockResolvedValue(httpResponse(file)));
        await openStream('/api/cases/x/messages', body, handlers, new AbortController().signal);

        const recorded = recordedHttp(file);
        expect(httpErrors).toEqual([{ status: recorded.status, error: JSON.parse(recorded.body) }]);
        expect(events).toEqual([]);
        expect(failures).toEqual([]);
      },
    );

    it('the 400 recording keeps its fieldErrors (field and Polish message)', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue(httpResponse('http-400.json')));
      await openStream('/u', body, handlers, new AbortController().signal);
      expect(httpErrors[0].error).toMatchObject({
        code: 'VALIDATION',
        fieldErrors: [{ field: 'text', message: 'Wpisz wiadomość lub dodaj zdjęcie.' }],
      });
    });
  });
});
