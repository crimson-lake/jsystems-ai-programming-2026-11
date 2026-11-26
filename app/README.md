# App

This folder will contain the application built during the course.

> **This project's stack (decided in `../docs/ADR/`):**
> - Spring Boot 4.2 + Maven + Java 25 in `backend/`, using the OpenAI Java SDK against OpenRouter.
> - Angular 22 + Angular Material in `frontend/`.
> - Playwright in `e2e/`.
>
> The generic checklist below names other stacks (Next.js, Vercel AI SDK, Tailwind) only as course examples. For this project, follow the ADRs and `../docs/plans/poc-implementation-plan.md`. Stack-specific commands live in `backend/AGENTS.md` and `frontend/AGENTS.md`, which are created during scaffolding.

## How to start

The app is scaffolded live during the course through a structured process:

1. **Research** — use agents to research and validate the project idea
2. **PRD** — generate a Product Requirements Document (`../docs/PRD.md`)
3. **ADR** — generate Architecture Decision Records (`../docs/ADR/`) to choose the tech stack
4. **Scaffold** — use the chosen boilerplate (`create-next-app`, AI SDK starter, Mastra, etc.)
5. **Implement** — build features with agents using TDD

## Checklist

Use this checklist during scaffolding. Some items are provided by the boilerplate (e.g. `create-next-app` ships `tsconfig.json`, ESLint config). Others you add explicitly.

### Project setup
- [ ] Choose framework (Next.js, Express+Vite, Mastra, other) — record in ADR
- [ ] Initialize project (e.g. `npx create-next-app@latest` or equivalent)
- [ ] TypeScript config (`tsconfig.json`)
- [ ] Package manager chosen (npm / pnpm / bun)

### Code quality
- [ ] ESLint config (`eslint.config.js`)
- [ ] Prettier config (`.prettierrc`, `.prettierignore`)
- [ ] `.editorconfig` (optional but recommended)

### Testing
- [ ] Unit/integration test runner (Vitest / Jest)
- [ ] E2E test runner (Playwright)
- [ ] Test setup file (e.g. `test-setup.ts` with Testing Library matchers)

### Environment
- [ ] `.env.example` with required env vars (API keys, ports)
- [ ] `.env` created locally (gitignored)
- [ ] `.gitignore` (node_modules, .env, build output, etc.)

### AI integration
- [ ] Vercel AI SDK (`ai` package) or equivalent
- [ ] API route / endpoint for chat
- [ ] Model configuration (provider, model name, API key from env)

### Design
- [ ] Design tokens (`../assets/design-tokens.json`)
- [ ] Tailwind CSS or equivalent
- [ ] Logo and favicon (`../assets/`)
- [ ] Design system doc (`../docs/design-guidelines.md`)

### Documentation
- [ ] PRD (`../docs/PRD.md`)
- [ ] ADRs (`../docs/ADR/`)
- [ ] AGENTS.md in `app/` with stack-specific rules

## Notes

- Don't create config files manually if the boilerplate provides them — it leads to conflicts.
- Let the agent research and recommend the right boilerplate based on the ADR decisions.
- Keep this folder organized: separate routes, components, domain logic, and tests.
