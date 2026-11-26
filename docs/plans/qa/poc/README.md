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
