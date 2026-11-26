# Backend SSE recordings (T26)

Raw response bodies of `POST /api/cases`, saved byte-for-byte (no header lines, so parsers can read them directly).
They are the protocol inputs for the frontend SSE client and store tests (plan T32). Do not edit them by hand.

| File | Bytes | Backend | Result |
|---|---|---|---|
| `case-success.sse` | 2572 | real OpenRouter (`openai/gpt-6-luna`), port 8080 | `case progress×3 : ping decision progress delta×49 done` (two `: ping` heartbeat comments included) |
| `case-error.sse` | 287 | second instance with `OPENROUTER_API_KEY=invalid`, port 8081 | `case progress×2 error{AI_UNAVAILABLE}` (real HTTP 401 from OpenRouter on the vision call) |

## Recording details

- **Date:** 2026-11-26 (11:34 UTC success, 11:35 UTC error).
- **Backend commit:** `1c93af6` plus the uncommitted T26 working tree (CaseController, CaseService, DecisionService, ImageAnalysisService, SseEventWriter).
- **Run:** `./mvnw -q -Pno-frontend spring-boot:run` from `app/backend`; `TRIAGE_MODE` not implemented yet, so no triage call.
- **Request (both files):** `POST /api/cases`, `Accept: text/event-stream`, `multipart/form-data` with
  - part `form` (`application/json`): `{"caseType":"RETURN","category":"SMARTPHONE","model":"Galaxy S23","purchaseDate":"2026-11-16","reason":"Zmiana zdania."}` (purchase date = today − 10 days);
  - part `photo` (`image/jpeg`, `phone-1.jpg`): the unmodified bytes of `assets/example-images/phone-1.jpg` (106 565 B).
- **Client:** `playwright-cli` `run-code` with `page.request.post` (Playwright APIRequestContext); the body was read as bytes and written unchanged.
- **Response headers (both):** `200`, `content-type: text/event-stream`, `cache-control: no-cache`, `x-accel-buffering: no`, `transfer-encoding: chunked`.
- **Success decision:** `REJECTED`, ruleIds `R1.1`, `R3.4`, `suggestOtherCaseType=COMPLAINT` (the model saw a cracked screen). This is the recorded model output, not an expected result.

# Backend chat-turn and HTTP-error recordings (T27)

Raw response bodies of `POST /api/cases/{caseId}/messages`, saved byte-for-byte like the T26 files, plus the real
pre-stream HTTP error responses. They are the protocol inputs for the chat-turn and HTTP-error store specs (plan T34).
Do not edit them by hand.

| File | Bytes | Request | Result |
|---|---|---|---|
| `chat-text.sse` | 1004 | text turn `"Jaką datę zakupu podałem?"`, new `clientMessageId` X | `progress(PREPARING_DECISION) : ping decision progress(WRITING_REPLY) delta×19 done`; `changed=false`, `previousLabel=null` |
| `chat-photo.sse` | 1721 | text `"Przesyłam dodatkowe zdjęcie."` + photo `phone-3.jpeg` (JPEG, unmodified bytes) | `progress(ANALYZING_PHOTO) progress(PREPARING_DECISION) : ping decision progress(WRITING_REPLY) : ping delta×35 done` (`VISION` in the log) |
| `chat-replay.sse` | 339 | the text turn again with the same `clientMessageId` X | `decision delta done`: the decision snapshot, one delta with the full stored text, the original `messageId`; no `ai.call` line |
| `chat-error-timeout.sse` | 231 | first attempt of the photo turn above (extra recording, not requested) | `progress(ANALYZING_PHOTO) progress(PREPARING_DECISION) : ping : ping error{TIMEOUT}`: the real DECISION call hit its 20 s per-call timeout; nothing was committed |
| `http-400.json` | 319 | `text=""`, no photo | 400 `{"code":"VALIDATION","fieldErrors":[{"field":"text","message":"Wpisz wiadomość lub dodaj zdjęcie."}]}` |
| `http-404.json` | 267 | text turn to a random case UUID | 404 `{"code":"CASE_NOT_FOUND"}` |
| `http-409.json` | 262 | a second text turn sent 0.5 s after the first, without awaiting it | 409 `{"code":"CASE_BUSY"}` (the first turn finished with `done`) |
| `http-413.json` | 281 | photo part of 11 615 585 B (`phone-1.jpg` bytes concatenated; only the transport size matters) | 413 `{"code":"PAYLOAD_TOO_LARGE","message":"Zdjęcie przekracza limit 10 MB"}` |
| `http-422.json` | 264 | a 4th photo turn (`phone-3.jpeg`) after 3 committed photo turns (`phone-3.jpeg`, `phone-1.jpg`, `phone-2.jpeg`) | 422 `{"code":"PHOTO_LIMIT"}` |

The `http-*.json` files hold `{status, headers, body}`: `headers` as returned by Playwright (lower-case names),
`body` as the verbatim response text (a JSON string, not a parsed object).

## Recording details (T27)

- **Date:** 2026-11-26, 11:53–11:57 UTC.
- **Backend commit:** `8f2c693` plus the uncommitted T27 working tree (`MessageController`, `CaseService` chat turn,
  `CaseSession` thread-safe replay reads).
- **Run:** `LOGGING_FILE_NAME=../../test-results/app-t27.log ./mvnw -q -Pno-frontend spring-boot:run` from
  `app/backend`, real OpenRouter (`openai/gpt-6-luna`), port 8080; triage not wired yet (T29), so no `TRIAGE` call.
- **Case:** created first with `POST /api/cases`: `{"caseType":"RETURN","category":"SMARTPHONE","model":"Galaxy S23",
  "purchaseDate":"2026-11-16","reason":"Zmiana zdania."}` + `phone-1.jpg`; first decision `REJECTED`
  (`R1.1`, `R3.4`, `suggestOtherCaseType=COMPLAINT`). This is the recorded model output, not an expected result.
- **Requests:** `Accept: text/event-stream, application/json`, `multipart/form-data` with the parts `clientMessageId`
  (random UUID), `text` and optionally `photo` (`image/jpeg`).
- **Client:** `playwright-cli` `run-code` with `page.request.post`; photos read with `page.goto('file://…')` +
  `response.body()`; bodies returned as base64 and decoded unchanged.
- **Response headers (streams):** `200`, `content-type: text/event-stream`, `cache-control: no-cache`,
  `x-accel-buffering: no`, `transfer-encoding: chunked`.
- **After the recordings:** `DELETE` of the case → 204, then a text turn → 404 `CASE_NOT_FOUND` (not saved).
