import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { format } from 'date-fns';
import { Observable } from 'rxjs';
import { CaseFormValue, ReducedPhoto } from '../model';
import { StreamHandlers, openStream } from '../sse/sse-stream';

/** A running streaming request: `promise` settles like `openStream`, `abort()` cancels it. */
export interface StreamHandle {
  promise: Promise<void>;
  abort(): void;
}

const CASES_URL = '/api/cases';
const PHOTO_FILE_NAME = 'photo.jpg';

/** Builds the multipart bodies of ADR-002 section 5 and starts the SSE requests (ADR-003 section 3.3). */
@Injectable({ providedIn: 'root' })
export class CaseApiService {
  private readonly http = inject(HttpClient);

  createCase(form: CaseFormValue, photo: ReducedPhoto, handlers: StreamHandlers): StreamHandle {
    const body = new FormData();
    // `purchaseDate` is the local calendar date; JSON.stringify(Date) would shift it through UTC.
    const json = {
      caseType: form.caseType,
      category: form.category,
      model: form.model,
      purchaseDate: format(form.purchaseDate, 'yyyy-MM-dd'),
      reason: form.reason,
    };
    body.append('form', new Blob([JSON.stringify(json)], { type: 'application/json' }));
    body.append('photo', photo.blob, PHOTO_FILE_NAME);
    return this.start(CASES_URL, body, handlers);
  }

  sendMessage(
    caseId: string,
    clientMessageId: string,
    text: string,
    photo: ReducedPhoto | undefined,
    handlers: StreamHandlers,
  ): StreamHandle {
    const body = new FormData();
    body.append('clientMessageId', clientMessageId);
    if (text) body.append('text', text);
    if (photo) body.append('photo', photo.blob, PHOTO_FILE_NAME);
    return this.start(`${CASES_URL}/${encodeURIComponent(caseId)}/messages`, body, handlers);
  }

  deleteCase(caseId: string): Observable<void> {
    return this.http.delete<void>(`${CASES_URL}/${encodeURIComponent(caseId)}`);
  }

  private start(url: string, body: FormData, handlers: StreamHandlers): StreamHandle {
    const controller = new AbortController();
    return {
      promise: openStream(url, body, handlers, controller.signal),
      abort: () => controller.abort(),
    };
  }
}
