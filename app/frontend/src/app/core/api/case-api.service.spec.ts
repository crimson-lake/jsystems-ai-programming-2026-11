import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { readRecording, sseResponse } from '../../../testing/sse-test-utils';
import { CaseFormValue, CopilotEvent, ReducedPhoto } from '../model';
import { StreamHandlers } from '../sse/sse-stream';
import { CaseApiService } from './case-api.service';

// Real example photo (assets/example-images/README.md), already JPEG.
const PHONE = readFileSync(join(process.cwd(), '..', '..', 'assets', 'example-images', 'phone-1.jpg'));

function reducedPhoto(): ReducedPhoto {
  return {
    blob: new Blob([PHONE as BlobPart], { type: 'image/jpeg' }),
    objectUrl: 'blob:test',
    originalName: 'phone-1.jpg',
    width: 100,
    height: 100,
  };
}

function formValue(purchaseDate: Date): CaseFormValue {
  return {
    caseType: 'RETURN',
    category: 'SMARTPHONE',
    model: 'Galaxy S23',
    purchaseDate,
    reason: 'Zmiana zdania.',
    photo: new File([PHONE as BlobPart], 'phone-1.jpg', { type: 'image/jpeg' }),
  };
}

function readText(blob: Blob): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(reader.error);
    reader.readAsText(blob);
  });
}

describe('CaseApiService', () => {
  let service: CaseApiService;
  let events: CopilotEvent[];
  let handlers: StreamHandlers;
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(CaseApiService);
    events = [];
    handlers = { onEvent: (e) => events.push(e), onHttpError: vi.fn(), onFailure: vi.fn() };
    fetchMock = vi.fn((_url: string, init?: RequestInit) =>
      Promise.resolve(sseResponse([readRecording('case-success.sse')], { signal: init?.signal })),
    );
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  async function createdForm(date: Date): Promise<{ form: FormData; json: Record<string, unknown> }> {
    await service.createCase(formValue(date), reducedPhoto(), handlers).promise;
    const sent = (fetchMock.mock.calls[0] as [string, RequestInit])[1].body as FormData;
    const part = sent.get('form') as Blob;
    return { form: sent, json: JSON.parse(await readText(part)) as Record<string, unknown> };
  }

  it('POSTs /api/cases with a JSON `form` part and the reduced JPEG as `photo`', async () => {
    const { form, json } = await createdForm(new Date(2026, 10, 24));
    expect((fetchMock.mock.calls[0] as [string])[0]).toBe('/api/cases');
    expect((form.get('form') as Blob).type).toBe('application/json');
    expect(json).toEqual({
      caseType: 'RETURN',
      category: 'SMARTPHONE',
      model: 'Galaxy S23',
      purchaseDate: '2026-11-24',
      reason: 'Zmiana zdania.',
    });
    const photo = form.get('photo') as File;
    expect(photo.name).toBe('photo.jpg');
    expect(photo.type).toBe('image/jpeg');
    expect(photo.size).toBe(PHONE.length);
  });

  it('streams the recorded events to the handlers and exposes a settled promise', async () => {
    await service.createCase(formValue(new Date(2026, 10, 24)), reducedPhoto(), handlers).promise;
    expect(events[0].type).toBe('case');
    expect(events.at(-1)?.type).toBe('done');
  });

  it('keeps the local calendar date at 00:30 CET (no UTC shift to the previous day)', async () => {
    vi.stubEnv('TZ', 'Europe/Warsaw');
    const date = new Date(2026, 10, 24, 0, 30);
    // Guard: in this zone the UTC date really is the day before, which is the bug being prevented.
    expect(date.toISOString().slice(0, 10)).toBe('2026-11-23');
    const { json } = await createdForm(date);
    expect(json['purchaseDate']).toBe('2026-11-24');
  });

  it('keeps 23:30 local on the same calendar day in a zone ahead of and behind UTC', async () => {
    vi.stubEnv('TZ', 'America/Los_Angeles');
    const { json } = await createdForm(new Date(2026, 10, 24, 23, 30));
    expect(json['purchaseDate']).toBe('2026-11-24');
  });

  it('serializes a leap day', async () => {
    const { json } = await createdForm(new Date(2028, 1, 29));
    expect(json['purchaseDate']).toBe('2028-02-29');
  });

  it('POSTs a chat message with clientMessageId, text and the optional photo', async () => {
    const id = '0b9f6d8e-4a53-4c1e-a43c-0cf1f0a3a111';
    await service.sendMessage('case-1', id, 'Jaką datę zakupu podałem?', reducedPhoto(), handlers).promise;
    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe('/api/cases/case-1/messages');
    expect(init.method).toBe('POST');
    const sent = init.body as FormData;
    expect(sent.get('clientMessageId')).toBe(id);
    expect(sent.get('text')).toBe('Jaką datę zakupu podałem?');
    expect((sent.get('photo') as File).name).toBe('photo.jpg');
  });

  it('omits `text` and `photo` parts that are not given', async () => {
    const id = '0b9f6d8e-4a53-4c1e-a43c-0cf1f0a3a111';
    await service.sendMessage('case-1', id, 'Tylko tekst', undefined, handlers).promise;
    expect((fetchMock.mock.calls[0] as [string, RequestInit])[1].body instanceof FormData).toBe(true);
    const sent = (fetchMock.mock.calls[0] as [string, RequestInit])[1].body as FormData;
    expect(sent.has('photo')).toBe(false);

    await service.sendMessage('case-1', id, '', reducedPhoto(), handlers).promise;
    const photoOnly = (fetchMock.mock.calls[1] as [string, RequestInit])[1].body as FormData;
    expect(photoOnly.has('text')).toBe(false);
    expect(photoOnly.has('photo')).toBe(true);
  });

  it('abort() aborts the request signal', () => {
    const handle = service.createCase(formValue(new Date(2026, 10, 24)), reducedPhoto(), handlers);
    const signal = (fetchMock.mock.calls[0] as [string, RequestInit])[1].signal as AbortSignal;
    expect(signal.aborted).toBe(false);
    handle.abort();
    expect(signal.aborted).toBe(true);
    return handle.promise;
  });

  it('deleteCase sends DELETE /api/cases/{id} through HttpClient', () => {
    const http = TestBed.inject(HttpTestingController);
    service.deleteCase('case-1').subscribe();
    const req = http.expectOne('/api/cases/case-1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    http.verify();
  });
});
