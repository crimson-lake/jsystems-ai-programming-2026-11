# PoC QA evidence index

Screenshots from per-task QA and phase gates (plan §4.5). Each entry gives the task or gate, date, app commit, the step each file shows and the observed result. No API keys or personal data appear in any file; only the example photos and synthetic form data are used.

## Q0 — Phase 0 gate (2026-11-26)

- **App commit:** none (no application exists yet).
- **Tool:** `playwright-cli` 0.1.25.

| File | Step | Result |
|---|---|---|
| `Q0/01-example-com-1280.png` | `playwright-cli -s=q0 open https://example.com`, `screenshot`, `close` | Page rendered and captured; the browser driver works |

## T11 — Frontend scaffold (2026-11-26)

- **App commit:** `46fce74` (placeholder app, no backend involved).

| File | Step | Result |
|---|---|---|
| `T11/01-placeholder-1280.png` | `npm start`, open `:4200` at 1280 px | Placeholder heading renders in Montserrat; console has 0 errors |

## Q1 — Phase 1 gate (2026-11-26)

- **App commit:** `3937b25` + the DevTools fix committed with this gate.
- **Stack:** `./mvnw -Pno-frontend spring-boot:run` with the real root `.env`, and `npm start` (proxy `/api` → `:8080`).
- **Captures reviewed:** S-1 7/7 recorded; S-2 failed (upstream 401), triage off (F1). See `app/backend/src/test/resources/openrouter-captures/README.md`.

| File | Step | Result |
|---|---|---|
| `Q1/01-health-1280.png` | Open `:8080/actuator/health` | `{"status":"UP"}` |
| `Q1/02-placeholder-1280.png` | Open `:4200` at 1280 px | Placeholder renders; `h1` font-family is Montserrat |
| `Q1/03-placeholder-390.png` | Same at 390 px | No horizontal scroll, heading wraps |
| — | `run-code`: `GET :4200/api/does-not-exist` | **Defect found and fixed:** the first run returned a JSON 404 **with a stack trace**, because DevTools forces `include-stacktrace=always`. With `spring.devtools.add-properties=false` the body is `{"timestamp","status":404,"error":"Not Found","path"}`, which proves the proxy reaches the backend without leaking internals (AC-42) |
| — | Console | 0 errors, 0 warnings |

## T30 — Theme and app shell (2026-11-26)

- **App commit:** the T30 commit (frontend only, dev server).

| File | Step | Result |
|---|---|---|
| `T30/01-shell-1280.png` | `npm start`, 1280 px | Navy header (`--app-header-bg`), white logo 178×45 at the left, title and subtitle in white Montserrat; matches the header of `assets/homepage.png` |
| `T30/02-shell-390.png` | Same at 390 px | Title and subtitle stack under the logo with no overlap (the reference site's overlap bug is not copied) |

## Q2 — Phase 2 gate (2026-11-26)

- **App commit:** `03d64cf` (dev stack: `spring-boot:run` + `npm start`).
- **Red runs (uncommitted tests, failing for the expected reason):**
  - T15 `CaseSubmissionIT`: 14 run, 12 failed with `expected 200/400 but was 404` (no `/api/cases` handler); the 2 sanity tests (WireMock serves the verbatim captures; every stub is a capture) pass.
  - T16 `ChatTurnIT`: 12 run, 9 failed with 404; 3 sanity tests pass.
  - T17 Playwright suite against the placeholder: 16 failed on missing elements (`Wyślij zgłoszenie` not found), none on compile errors.

| File | Step | Result |
|---|---|---|
| `Q2/01-health-1280.png` | `:8080/actuator/health` | UP |
| `Q2/02-shell-1280.png` | `:4200` at 1280 px | App shell with the navy header |
| `Q2/03-shell-390.png` | Same at 390 px | Header stacks, no overlap |
| — | `GET :4200/api/does-not-exist` | JSON 404 without internals (proxy works) |
| — | Console | 0 errors, 0 warnings |

## T31 — Case form (2026-11-26)

- **App commit:** the T31 commit (frontend dev server; submission is wired in T32).

| File | Step | Result |
|---|---|---|
| `T31/01-empty-1280.png` | Open `/` | Fields in AC-01 order, no preselection, info line, orange CTA "Wyślij zgłoszenie" |
| `T31/02-validation-errors-1280.png` | Click "Wyślij zgłoszenie" on the empty form | Polish inline errors on every field; focus moved to the first invalid control (case-type radio, AC-13) |
| `T31/03-filled-complaint-1280.png` | Reklamacja, Laptop, model, typed date `10.10.2026`, reason, `laptop-1.png` | Reason required marker shown for Reklamacja, complaint photo hint, thumbnail + file name + "Usuń", counter 41/2000 |
| `T31/04-filled-complaint-390.png` | Same at 390 px | Single column, `scrollWidth` 390 = `clientWidth` (no horizontal scroll) |
| — | Console | 0 errors, 0 warnings |

## T26 — First-decision pipeline, real-API check (2026-11-26)

- **App commit:** the T26 commit (backend `spring-boot:run` with the real `.env`; triage not built yet).
- **Method:** `playwright-cli` `run-code` with `page.request.post` multipart: RETURN, SMARTPHONE, purchase 10 days ago, `phone-1.jpg` (unedited bytes). No screenshots (API-only task).
- **Port 8080, real key:** 200 `text/event-stream`, 22.6 s. Events `case, progress×3, : ping, decision, progress WRITING_REPLY, delta×49, done`. The model returned `REJECTED` with `[R1.1, R3.4]` and `suggestOtherCaseType=COMPLAINT` (recorded, not pre-assumed).
- **`ai.call` lines** (`test-results/app-t26.log`, model `openai/gpt-6-luna`): `VISION OK 12242 ms 2653/357`, `DECISION OK 2817 ms 2257/120`, `STREAM OK 5222 ms 1846/366`. No customer text in the log.
- **Port 8081, `OPENROUTER_API_KEY=invalid`:** `case, progress×2, error{AI_UNAVAILABLE}`; log has `AI_CONFIG … 401` at ERROR and `ai.call step=VISION status=ERROR`; nothing stored.
- **Recordings for frontend tests:** `app/frontend/src/testing/captures/case-success.sse` and `case-error.sse` (byte-exact), described in that folder's README.

## T27 — Chat turn, real-API check (2026-11-26)

- **App commit:** the T27 commit (backend `spring-boot:run`, real `.env`). API-only task, no screenshots.
- **Checks via `playwright-cli` `run-code`:** a text turn ("Jaką datę zakupu podałem?"); a photo turn (`VISION` in the log); the same `clientMessageId` again → replay (`decision`, one `delta`, `done`, no `ai.call`); two concurrent turns → one 409 `CASE_BUSY`; `DELETE` → 204, then a message → 404 `CASE_NOT_FOUND`; 3 committed photo turns then a 4th → 422 `PHOTO_LIMIT`; empty text without photo → 400; an 11.6 MB photo part → 413.
- **`ai.call` lines** (`test-results/app-t27.log`, model `openai/gpt-6-luna`): all steps OK except one chat-turn `DECISION status=TIMEOUT latencyMs=20033` (nothing committed; the retry succeeded). Chat-turn decisions took 12–17 s, so the per-call decision timeout was raised to 30 s (still capped by the 55 s deadline; ADR-001 §3.6 amended).
- **Recordings:** `chat-text.sse`, `chat-photo.sse`, `chat-replay.sse`, `chat-error-timeout.sse`, `http-400/404/409/413/422.json` in `app/frontend/src/testing/captures/`.

## T29 — Triage wiring, real-API probe (2026-11-26)

- **App commit:** the T29 commit (`spring-boot:run`, default `TRIAGE_MODE=assist`, model `typesafe/jev-1.14`). API-only task.
- **RETURN, `phone-1.jpg`, 10 days:** 200, 23.4 s; `ai.call` TRIAGE OK 928 ms (3916/233), VISION OK 10943 ms, DECISION OK 4235 ms, STREAM OK 6637 ms; `triage.agreement ruleId=T6 … llmLabel=REJECTED status=OK`.
- **COMPLAINT, `phone-3.jpeg`, 200 days:** 200, 36.7 s; TRIAGE OK 272 ms, VISION OK 7051 ms, DECISION OK 18310 ms, STREAM OK 11007 ms; `triage.agreement ruleId=T6 … llmLabel=NEEDS_MORE_INFO status=OK`.
- The model's labels are recorded, not pre-assumed. Chat turns make no Decisions call (IT-proven).

## T33 + T34 — Chat screen and composer, browser QA (2026-11-26)

- **App commit:** the T34 commit (dev stack: `spring-boot:run` with the real `.env`, `TRIAGE_MODE=assist`, plus the Angular dev server).
- **Note:** the first attempt redirected from `/sprawa` back to `/` because a stale dev server from T31 (without the `sprawa` route) still held port 4200. After stopping it the flow worked; no app defect.

| File | Step | Result |
|---|---|---|
| `T34/01-progress-1280.png` | Submit a RETURN (Smartfon, 10 days ago, `phone-1.jpg`) | Form disabled, progress steps shown (AC-14) |
| `T34/02-first-decision-1280.png` | First decision | Navigated to `/sprawa`; summary panel; greeting, badge, three sections, disclaimer. The model returned **Odrzucone** with `[R1.1]`, `[R3.4]` and a suggestion to start a complaint (recorded, not pre-assumed) |
| `T34/03-keyboard-question-1280.png` | Keyboard only: focus the textarea, type "Jaką datę zakupu podałem?", Enter | Send disabled while streaming (AC-38); the reply gives the form date "16 listopada 2026" (AC-34) |
| `T34/04-photo-preview-1280.png` | Attach `phone-3.jpeg` in the composer | Removable preview above the input |
| `T34/05-photo-turn-1280.png` | Send text + photo | Reply streamed; `ai.call step=VISION` for the chat photo in the log; no "Zmiana decyzji" block because the label stayed the same (AC-36) |
| `T34/06-chat-390.png` | 390 px | Collapsed "Szczegóły sprawy" panel above the chat; `scrollWidth` 390 = `clientWidth` |
| `T34/07-summary-open-390.png` | Open "Szczegóły sprawy" | Summary with thumbnail and badge |
| `T34/08-new-case-dialog-390.png` | "Nowa sprawa" | Dialog text from PRD §9.3, focus on "Anuluj"; confirming returns an empty form |
| — | Console | 0 errors, 0 warnings |
