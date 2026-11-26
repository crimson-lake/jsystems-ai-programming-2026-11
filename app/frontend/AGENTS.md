# Frontend Guidelines (app/frontend)

Angular 22.3 + Angular Material 22.3 (M3), standalone, zoneless, Vitest. Spec: `docs/ADR/003-frontend.md`.
Run commands from `app/frontend`. Node 24, npm 11.

## Verified commands (T11, 2026-11-26)
| Command | Result |
|---|---|
| `npm test -- --no-watch` | exit 0, 1 file, 2 tests passed (Vitest 5, jsdom) |
| `npm run build` | exit 0, no warnings; output `dist/frontend/browser` |
| `npm start` | `ng serve` on http://localhost:4200, proxies `/api` to `http://localhost:8080` (`src/proxy.conf.json`); one instance at a time |
| `npm run lint` | exit 0 (angular-eslint) |

## Rules
- Zoneless: no `zone.js`, no `NgZone` reliance. State with signals (`signal`, `computed`, `input()`, `output()`); no NgRx. RxJS only where `HttpClient` returns observables.
- Standalone components only, `inject()` over constructor injection, new control flow (`@if`, `@for`).
- Colors: no hex literals in component SCSS. Use only `var(--mat-sys-*)` and `--app-*` variables. Hex values live only in generated `src/_theme-colors.scss` and `src/styles/_tokens.scss`.
- Fixed Polish UI text only from `shared/texts.pl.json` / `texts.pl.ts` (added in T14). No inline string literals in templates.
- Library APIs: check Context7 and the installed version before use.
- `src/app/app.routes.ts` holds placeholder routes only until the feature tasks add them.
