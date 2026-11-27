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

## Q3 — Phase 3 gate, dev stack with real OpenRouter (2026-11-26)

- **App commit:** `ae4e781` (dev stack: `spring-boot:run` with the real `.env`, `TRIAGE_MODE=assist` with `typesafe/jev-1.14`, plus the Angular dev server). Logs: `test-results/app-q3.log`, `test-results/app-q3-badkey.log`.
- **Result:** all 11 checklist items PASS; no functional defect. One UX finding was fixed afterwards in T34 (commit "keep the start of long replies in view"): following a long reply scrolled past its safety block and badge.
- **Model outputs are recorded, never pre-assumed.**

| Files | Step | Result |
|---|---|---|
| `Q3/01-return-*` (1280, 390) | RETURN, `phone-1.jpg`, 10 days | Odrzucone (`R1.1`, `R3.4`, suggests a complaint); summary panel at 390 behind "Szczegóły sprawy" |
| `Q3/02-complaint-*`, `Q3/03-complaint-ask-date-1280.png` | COMPLAINT, `laptop-1.png`, 200 days; "Jaką datę zakupu podałem?" | Przekazane do specjalisty (`C8`, photo contradicts the reason); reply gives the form date 10.05.2026 (AC-34) |
| `Q3/04-chat-photo-*` | Chat photo `laptop-2.webp` with text | Decision changed to Potrzebne dodatkowe informacje: "Zmiana decyzji" block with both badges, summary badge updated (AC-35) |
| `Q3/05-outside-window-*` | RETURN, `phone-2.jpeg`, 45 days | Odrzucone with "45 dni" and "30 dni" and `[R2.3]` (AC-24); triage T2 agreed |
| `Q3/06-safety-*` (1280, 390) | RETURN, `phone-3.jpeg`, reason "Bateria spuchła i czuć swąd." | Przekazane do specjalisty, safety block first (AC-26), `[R7.1]`; triage T1 agreed |
| `Q3/07-new-case-*` | "Nowa sprawa" → confirm | Dialog text per PRD §9.3; `DELETE /api/cases/{id}` → 204; empty form |
| `Q3/08-reload-empty-form-390.png` | Reload with a chat open | `beforeunload` prompt shown; after reload the guard shows the empty form (AC-43) |
| — | API checks via `page.request` | Replay = `decision, delta, done` with no new `ai.call`; concurrent turns → 409 `CASE_BUSY`; empty text → 400; 4th photo → 422 `PHOTO_LIMIT`; `DELETE` 204 then 404 `CASE_NOT_FOUND` |
| `Q3/09-error-banner-*` (1280, 390) | Backend with `OPENROUTER_API_KEY=invalid` | Banner "Nie udało się przeanalizować zgłoszenia. Spróbuj ponownie." with "Spróbuj ponownie"; fields and photo kept (AC-40); log: TRIAGE and VISION `status=ERROR` (401), nothing stored |
| — | Console | No app errors on any page |
| — | `ai.call` in `app-q3.log` | 50 lines, all `status=OK`: TRIAGE 8 (`typesafe/jev-1.14`), VISION 12 (8 form + 4 chat), DECISION 15, STREAM 15; `DECISION_REASK` not observed |
| — | Brand | Header `#0D244C`, white logo on navy only, orange CTA `#FF9700` with 8 px radius, navy secondary buttons, Montserrat; no horizontal scroll at 390 and 1280 |

## T40 — Packaging into the JAR (2026-11-26)

- **App commit:** `0ad9543`. Separate records:
  - **Build:** `./mvnw package` (from `app/backend`) and `./mvnw verify`: BUILD SUCCESS, 0 `[WARNING]` lines. Node v24.22.3 is downloaded into `target/` by frontend-maven-plugin 2.0.2.
  - **Tests in the same `verify` run:** Surefire 266, Failsafe 64 (incl. `SpaForwardingIT` 4, not skipped), Vitest 24 files / 268 tests. All passed, 0 skipped.
  - **Artifact:** `copilot-0.0.1-SNAPSHOT.jar` contains `static/index.html`, `static/main-*.js` and `policies/*.md` (113 MB, of which 77 MB is `openai-java-core`).
  - **Startup from the repo root:** `java -jar app/backend/target/copilot-0.0.1-SNAPSHOT.jar` → health UP after 11.0 s ("Started CopilotApplication in 8.8 seconds"; TAC-02 < 15 s). `/` and `/sprawa` → `index.html`; `/main-*.js` → `text/javascript`; `/api/x` → 404 JSON without internals.
- **QA on the JAR (also confirms the Q3 scroll fix):**

| File | Step | Result |
|---|---|---|
| `T40/01-form-jar-390.png` | Form served by the JAR at 390 px | Same UI as the dev server |
| `T40/02-chat-start-visible-jar-390.png` | RETURN, `phone-3.jpeg`, reason "Bateria spuchła i czuć swąd." | After the reply completes the list stays at the start of the reply: safety block, greeting and the "Przekazane do specjalisty" badge are visible; console clean |

## T41 — E2E on the real stack (2026-11-26)

- **App commit:** `b13bd45` (JAR copied to a private path so a later Maven build could not replace it mid-run). Real OpenRouter on `:8080`; a second JAR with `OPENROUTER_API_KEY=invalid` on `:8081` for the error banner. No mocks, no `page.route`.
- **Command (from `app/e2e`):** `E2E_BASE_URL=http://localhost:8080 E2E_BAD_KEY_URL=http://localhost:8081 E2E_APP_LOG=<fresh log per run> npx playwright test`.
- **Full runs: 3 (the budget limit).** Run 1 aborted (360 px category select click intercepted by the floating label; fixed in the helper with a forced click after checking that a real mouse click opens the select). Run 2: 15/16 (keyboard at 360 px could not reach "Nowa sprawa" inside the collapsed summary; the spec now opens "Szczegóły sprawy" with Enter first, still keyboard-only). **Run 3: 16/16 passed, 6.1 min.**
- **Specs (each at 360 and 1280 px unless noted):** return-happy, outside-window, safety-wording, complaint-followup, chat-photo-new-case, error-banner, keyboard, no-horizontal-scroll (360/390/768/1280/1920 in one test), actuator health.
- **Coverage teardown (runs 2 and 3), "proof OK":** `POST /api/cases` 15, chat text-only 2, chat with photo 2, `DELETE` → 204 2, `GET /actuator/health` 1; `ai.call` with `status=OK`: TRIAGE 13, VISION 15 (form and chat on the same case), DECISION 17, STREAM 17; `DECISION_REASK` not observed.
- **No app defects.** Changes were limited to `app/e2e` selectors, waits and the network logger (Chromium hides multipart bodies that contain a File, so a null body is logged as a photo part).

## T42 = Q4 — Full manual QA on the packaged JAR (2026-11-26)

- **App commit:** `a2c3abe` (final JAR after the T50 review fixes; copied to a private path; real OpenRouter on `:8080`, invalid key on `:8081`). Startup 8.5–12.2 s.
- **E2E re-run (T50 verification):** 16/16 passed in 5.3 min; coverage teardown "proof OK" (TRIAGE 13, VISION 15 incl. form + chat, DECISION 17, STREAM 17; every endpoint; `DECISION_REASK` not observed).
- **Manual checklist (35 screenshots in `Q4/`, 390 and 1280 px): all PASS, no functional defect.**

| Files | Step | Result |
|---|---|---|
| `Q4/01-form-empty-*`, `02-form-validation-*`, `03-form-filled-*`, `04-form-progress-*` | Form states | Polish errors, photo thumbnail, progress steps |
| `Q4/05-chat-phone-1-1280`, `05-chat-phone-2-390`, `06-chat-laptop-1-1280`, `07-chat-laptop-2-390`, `08-chat-phone-3-1280` | Chat for each of the 5 example photos | Labels recorded (not pre-assumed): Odrzucone, Potrzebne dodatkowe informacje, Potrzebne dodatkowe informacje (another run: Przekazane do specjalisty), Wstępnie przyjęte, Przekazane do specjalisty |
| `Q4/09-outside-window-*` | RETURN 45 days ago | Odrzucone, "45 dni … 30 dni [R2.3]" |
| `Q4/10-safety-wording-*` | Safety reason | Przekazane do specjalisty; safety block first and visible at the top after the reply completes |
| `Q4/11`–`14-followup-*` | Chat follow-up with `laptop-2.webp` | Decision unchanged, no "Zmiana decyzji" block (the changed case was shown in Q3) |
| `Q4/15-new-case-dialog-*`, `16-after-new-case-empty-form-1280` | "Nowa sprawa" | Focus on "Anuluj"; confirm → empty form; `DELETE` → 204 |
| `Q4/17-error-banner-*` | Invalid-key instance | Banner + "Spróbuj ponownie", data and photo kept. **Finding:** at 390 px the retry button was below the fold → fixed (banner scrolled into view and focused) |
| `Q4/19-chat-409-*`, `Q4/20-chat-404-expired-*` | 409 / 404 via API while the chat is open | 409 `CASE_BUSY` for the concurrent request; after `DELETE`, a UI message shows "Ta sprawa wygasła. Rozpocznij nową sprawę." with "Nowa sprawa" |
| `Q4/21-console-pass-*` | Console + scroll | 0 errors/warnings; no horizontal scroll at 390 and 1280 |
| — | Brand | Header `#0D244C`, CTA `#FF9700` with white text, navy outline secondary, Montserrat, 8 px radius, logo on navy only; matches `assets/homepage*.png` |
| — | `ai.call` (`q4.log`) | 64 lines, all OK: TRIAGE 15 (`typesafe/jev-1.14`), VISION 16 (form + chat), DECISION 17, STREAM 16 |

- **Second finding:** a client disconnect on an SSE stream was logged as an unhandled ERROR with a stack trace → fixed (logged at DEBUG).

## Q5 — Phase 5 gate, final JAR regression (2026-11-26)

- **App commit:** `0fab921` (final JAR; `./mvnw clean verify`: Surefire 283, Failsafe 64, Vitest 274, JaCoCo met, 0 warnings). Real OpenRouter on `:8080`, invalid key on `:8081`.
- **Result: all checks PASS, no defect.** Labels recorded, not pre-assumed.

| Files | Step | Result |
|---|---|---|
| `Q5/01`–`03-return-*` (1280, 390) | RETURN with `phone-1.jpg` / `phone-2.jpeg` | Odrzucone (cracked screen, suggests a complaint) / Wstępnie przyjęte |
| `Q5/04`–`05-complaint-*` (1280, 390) | COMPLAINT with `laptop-1.png` | Przekazane do specjalisty (`[C8]`, description vs photo) |
| `Q5/06`–`08-chat-photo-*` | Chat photo turn with `laptop-2.webp` | Reply streamed; decision unchanged |
| `Q5/09-new-case-dialog-*`, `Q5/10-after-new-case-empty-form-1280` | "Nowa sprawa" | Focus on "Anuluj"; confirm → empty form; `DELETE` → 204 |
| `Q5/11-error-banner-390`, `Q5/11-error-banner-1280` | Invalid-key instance | Banner focused and in view; "Spróbuj ponownie" fully visible at 390 (Q4 fix confirmed); data and photo kept |
| — | Console | 0 errors, 0 warnings |
| — | `ai.call` (`q5.log`) | Every case: TRIAGE (`typesafe/jev-1.14`), VISION, DECISION, STREAM all OK; chat photo turn: VISION, DECISION, STREAM OK; 0 ERROR/WARN lines. Invalid-key log: TRIAGE and VISION `status=ERROR` |
