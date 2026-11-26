# PoC QA evidence index

Screenshots from per-task QA and phase gates (plan §4.5). Each entry gives the task or gate, date, app commit, the step each file shows and the observed result. No API keys or personal data appear in any file; only the example photos and synthetic form data are used.

## Q0 — Phase 0 gate (2026-11-26)

- **App commit:** none (no application exists yet).
- **Tool:** `playwright-cli` 0.1.25.

| File | Step | Result |
|---|---|---|
| `Q0/01-example-com-1280.png` | `playwright-cli -s=q0 open https://example.com`, `screenshot`, `close` | Page rendered and captured; the browser driver works |

## T11 — Frontend scaffold (2026-11-26)

- **App commit:** T11 commit (placeholder app, no backend involved).

| File | Step | Result |
|---|---|---|
| `T11/01-placeholder-1280.png` | `npm start`, open `:4200` at 1280 px | Placeholder heading renders in Montserrat; console has 0 errors |
