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
