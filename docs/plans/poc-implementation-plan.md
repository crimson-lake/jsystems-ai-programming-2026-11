# Implementation Plan — Hardware Service Decision Copilot PoC (whole application)

> **Status: DRAFT, awaiting review by the product owner/human lead.**
> **Date:** 2026-11-26 (revised after the independent document review of the same day) · **Author:** orchestrator (Claude Code, Opus 5.5)
> **Supersedes:** `docs/plans/return-first-decision-implementation-plan.md` (single-slice draft of 2026-11-25). The whole PoC is implemented in one pass.
>
> This plan does not authorize execution, new agents, publication or merge. Each phase starts only after an explicit "go".

---

## 1. Scope

**Deliverable:** the complete PoC described in [`docs/PRD.md`](../PRD.md), delivered as **one feature branch and one pull request**, with one commit per task:
- all flows [4.1–4.9](../PRD.md#4-main-flows);
- all acceptance criteria [AC-01 … AC-48](../PRD.md#6-acceptance-criteria);
- the agent behavior in [PRD §11](../PRD.md#11-agent--system-behavior-specification), including the [precedence rules](../PRD.md#precedence-when-criteria-conflict).

**Architecture:** the ADRs are the specification. They are linked here, not copied.

| ADR | Covers |
|---|---|
| [000](../ADR/000-main-architecture.md) | System and contracts |
| [001](../ADR/001-llm-integration.md) | LLM, prompts, guardrails and time budget |
| [002](../ADR/002-backend.md) | Backend and test setup |
| [003](../ADR/003-frontend.md) | Frontend, UI texts and error mapping |
| [004](../ADR/004-triage-decisions-model.md) | Triage, `assist` mode only |

**Design:** `docs/design-guidelines.md`, `assets/design-tokens.json`, `assets/logo.webp`, `assets/favicon.ico` and `assets/homepage*.png`. These arrive with PR #2 (design system) and must be on the base branch before T00 (§10.1). The brand exception is recorded in [PRD §8](../PRD.md#business).

**Product decisions applied** ([PRD §12, 2026-11-26](../PRD.md#decisions-made-with-the-product-owner-2026-11-26)):
- **D1:** evals assert only form- and policy-driven outcomes; photo assessments are recorded without pass/fail; the triage fast path stays disabled.
- **D2:** the NOVOMATIC brand is kept, as a course exception.
- **D4:** a short manual QA follows every runtime-affecting task.
- **D5:** OpenRouter spend is capped at $2, with cut lines.
- **R7.1:** the return policy gains a safety rule.

### Traceability: PRD criteria → tasks

| PRD criteria | Implemented in | Proven by |
|---|---|---|
| AC-01…AC-15 (form) | T20, T31 | T20/T31 unit specs, T17 E2E, per-task QA, T42 |
| AC-16…AC-19 (image analysis) | T23, T25, T26 | T15 IT, T43 eval report (recorded, not pre-assumed) |
| AC-20…AC-29 (first decision) | T22, T25, T26 | T22 unit (guardrails, precedence), T15 IT, T43 evals, T17 E2E |
| AC-30…AC-39 (chat) | T27, T33, T34 | T16 IT, T33/T34 specs, T17 E2E |
| AC-40…AC-44 (errors, session) | T24, T26, T27, T32–T34 | T15/T16 IT, T32 store specs, T17 E2E, T42 |
| AC-45…AC-48 (general) | T25, T30, all FE tasks | T43 (Polish reply), T17 viewports and keyboard, T42 brand comparison |
| ADR-004 triage (`assist`) | T28, T29 | T28 unit, T29 IT, T43 triage record |

### Out of scope

- Everything in [PRD §7](../PRD.md#7-out-of-scope).
- Docker ([ADR-000 D-08](../ADR/000-main-architecture.md#d-08-local-only-deployment)).
- **Triage `fast-path` mode,** including its templates, `FastPathMessageRenderer` and the derived `ImageAnalysis`. It is disabled in the PoC by the [ADR-004 D-T2 amendment](../ADR/004-triage-decisions-model.md#d-t2-thresholds-080-low-095-high-asymmetric-safety-calibrate-with-evals-before-enabling-the-fast-path). T28 implements the router and rule table only to **compute and log** the would-be fast-path label.

---

## 2. Current state (inspected 2026-11-26)

| Item | Observed |
|---|---|
| Checkout | Worktree `.claude/worktrees/adr-architecture`. The main checkout is used by another session and stays untouched |
| Branches and PRs | `docs/prd-adr-plan` holds policies, PRD, ADRs and this plan. It is pushed and open as **PR #3** (base `main`). PR #2 (design system) is open (base `main`). `adr-architecture` holds `Chore: ignore Claude Code worktrees`. The target branch for the app (README: `app-implementation`, not `main`) is **not created yet**, by your decision (§10.2) |
| Design assets on `docs/prd-adr-plan` | **Missing:** `docs/design-guidelines.md`, `assets/design-tokens.json`, `logo.webp`, `favicon.ico`, `homepage*.png` (they are in PR #2) |
| Application code | **None.** `app/` contains only `README.md`. There are no build descriptors, entry points or tests |
| Toolchain | **Present:**<br>• `node` v24.22.3, `npm` 11.19.1, `gh` 2.105.0<br>• `playwright-cli` 0.1.25 (`~/.local/bin`)<br>• `java` openjdk 25.0.5.1<br>• Spring Boot CLI v4.1.0 (`~/.sdkman/candidates/springboot/current/bin/spring`, not on `PATH`; `spring init --list` reaches start.spring.io)<br>**Missing:** `opencode`, `docker` (not needed), a system `mvn` (not needed: Maven Wrapper) |
| Claude permissions (`.claude/settings.json`) | **Denied:** `sudo`, `curl`, `wget`, `ssh`, `scp`, `rm`, `rmdir`, `chmod`, `chown`, `env`, `printenv`, `git reset --hard`, force push, push to `main`/`master`/`prod`, reading `.env*`. **Not allow-listed, so each use prompts:** `./mvnw`, `java`, the Spring CLI, `opencode`, `playwright-cli`, `gh`, `kill`, `cp`, `mkdir`, `git rm`, `git push`, `git worktree`. A security-review **agent hook fires on Claude `Edit`/`Write` of `pom.xml`** |
| `.gitignore` (relevant) | Ignores `.agent-briefs/` ("Private agent briefs"), `**/target/`, `.env*` (except `.env.example`), `node_modules/`, `dist/`, `test-results/`, `playwright-report/`, `.playwright-cli/`. `.agent-runs/` is **not yet** ignored (T01 adds it) |
| Repo automation | `.github/workflows/pr-agent-azure-review.yml` runs on every PR and needs Azure secrets, so expect a failing or costly check on PRs |
| Tracker | GitHub Issues are disabled. **The Claude Code task list is the execution tracker**: one task per matrix row, created only after authorization |
| Baseline | Nothing to build or test yet, so there are no baseline failures. The first green baseline comes from T10/T11 |

Only the version checks above have been verified so far. Every project command is **verified for the first time** in T10/T11, and its real output is recorded there.

---

## 3. Agents, models and budget

| Role | Agent / model | Provider | Used for | Budget |
|---|---|---|---|---|
| Orchestrator (ORCH) | Claude Code, **Opus 5.5** (this session) | Anthropic | Architecture decisions, contracts, `pom.xml`, lockfiles, configs, prompts, route wiring, integration tasks, diff review, re-running checks, per-task QA, commits | Claude subscription (outside the OpenRouter cap) |
| Worker (LUNA) | **OpenCode CLI headless**, `openrouter/openai/gpt-6-luna` (Luna 6) | OpenRouter | Bounded implementation and test-writing tasks | OpenRouter cap below |
| Escalation worker | Claude Code `Agent` subagent, `model: sonnet` | Anthropic | Tasks Luna failed twice, or SDK-heavy work; also fallback F2 | On escalation only |
| Independent reviewer | Fresh Claude Code subagent (Opus), given the diff, PRD and ADRs only, + `/code-review` | Anthropic | T50 | — |
| Final acceptance | Human lead | — | Plan approval, phase gates, PR merge | — |

**Pricing facts (2026-11-25):**
- **Luna 6 on OpenRouter:** per million tokens, input costs $0.05–$0.20 and output $0.25–$1.00, depending on the provider.
- **`gpt-6-luna-decisions`:** unpriced; measured in S-2.
- **Not chosen:**
  - Claude Haiku: only after checking `/model` and pricing.
  - Codex Luna: OpenCode was requested instead.
  - GitHub Copilot: check its model picker and current pricing before any use.

**Budget: $2.00 OpenRouter total** (D5), covering workers, spikes, triage calls, E2E and evals.

| Item | Estimate (worst-case provider prices; to be re-measured) |
|---|---|
| About 20 Luna worker runs | $0.30–$1.20 (headless runs resend context, so the real figure may be higher) |
| Spikes S-1/S-2 (≤ 10 calls), E2E (≤ 8 runs × 4–5 calls incl. triage), evals (about 15 cases × 3 repeats × 4–5 calls) | $0.15–$0.50 + the unknown triage price |

**Cut lines:**
- **After T01 and after the first real worker run,** ORCH recomputes the forecast from recorded usage. If it exceeds $2, ORCH reports it before continuing.
- **At $1.50 (75%):** stop dispatching Luna and use fallback F2 (Sonnet subagents).
- **At $1.90 (90%):** one eval repeat and one final E2E run only (F5).

**Harness check:**
- The Claude Code `Agent` tool with a `model` override is available.
- OpenCode is not installed and is verified in T01. If it cannot run, the same matrix runs sequentially with Claude subagents (F2).
- **Role definitions:** none. All workers are ad hoc, driven by exact briefs.

**Commit tooling:** do **not** use `/git:commit` for task commits. It runs on Haiku and allows `git add:*`. ORCH stages named paths and writes `Area: summary` messages itself.

---

## 4. OpenCode headless — how we use it

Sources: Context7 `/websites/opencode_ai` (docs `cli/commands`, `models`, `permissions`, `policies`, `migrate-v1`). The items marked **verify** are checked in T01, and the real syntax is recorded in this section.

### 4.1 Install and check (T01)

1. Install into the user prefix (`/usr` is not writable). The docs name `@opencode/cli`, while an older package is `opencode-ai`:
   - try `npm install -g --prefix ~/.local @opencode/cli` first;
   - **verify** the binary with `opencode --version`;
   - fall back to `opencode-ai` only if the first package does not exist.
2. Run `opencode run --help` and **verify** the flags `--model`, `--file`, `--format json` and `--auto`, and whether a working-directory flag exists.
3. **Credentials:**
   - the human provides the OpenRouter key through OpenCode's credential store, or by exporting it in the shell that ORCH uses;
   - **verify** the mechanism with `opencode auth --help`. An interactive `! opencode auth login` may not work through `!`, in which case the human runs it in their own terminal;
   - the key never goes into `opencode.jsonc` or a brief.
4. Run `opencode models openrouter` and confirm the `gpt-6-luna` slug.

### 4.2 Project configuration (`opencode.jsonc` at the repo root, written by ORCH in T01)

- **Default model:** `openrouter/openai/gpt-6-luna`.
- **Ordered `permissions`** (V2 format, **last match wins**; verify the format against `opencode` docs for the installed version):
  1. **First, catch-all deny:** `edit *` → deny, `shell *` → deny, `webfetch *` → deny.
  2. **Then narrow allows:**
     - `edit` on `app/backend/src/**`, `app/frontend/src/**` and `app/e2e/tests/**`;
     - `shell` for exactly: `./mvnw -Pno-frontend test*`, `./mvnw -Pno-frontend verify*`, `sh mvnw -Pno-frontend *`, `npm test*`, `npm run build*`, `npm run lint*`, `npx ng test*`, `npx playwright test*`, `git status*`, `git diff*`.
  3. **Then explicit denies** (kept even though the catch-all already denies, as documentation): `npm install*`, `npm i *`, `npx ng add*`, `npx ng generate*`, `git commit*`, `git push*`, `git reset*`, `git checkout*`, `git restore*`, `git stash*`, `git clean*`, `rm *`, `mv *`, `find * -delete*`, `curl *`, `wget *`.
- **`policies` (hard deny, applies even with `--auto`):**
  - `edit` on `**/.env*`, `**/pom.xml`, `**/package.json`, `**/package-lock.json`, `**/angular.json`, `opencode.jsonc`, `AGENTS.md`, `CLAUDE.md`, `.claude/**`, `docs/**`;
  - `webfetch:*`.
- **Working directory:**
  - backend workers run with **cwd `app/backend`**, frontend workers with **cwd `app/frontend`** and E2E workers with **cwd `app/e2e`**, so the allow patterns match the commands they actually type;
  - T01 **verifies** that a root `opencode.jsonc` is picked up from a subdirectory. If not, ORCH places a copy in each lane folder.
- **Proof in T01:** a smoke run asks the worker to:
  - (a) edit `docs/README.md`;
  - (b) run `npm install left-pad`;
  - (c) run `git stash`;
  - (d) create `app/backend/src/tmp-smoke.txt`.

  The first three must be **blocked**; (d) must succeed. ORCH then reverts (d) by asking the human to delete the file.

### 4.3 Running one task (ORCH, Bash, in the background)

From `app/backend`:
`opencode run --model openrouter/openai/gpt-6-luna --auto --format json --file ../../.agent-briefs/T20.md "Execute the attached brief exactly. Edit only the files it lists. Finish with the Report section." > ../../.agent-runs/T20.jsonl`

| Flag | Effect |
|---|---|
| `run` | Non-interactive mode |
| `--file` | Attaches the brief. **Verify** in T01 that a gitignored file can be attached |
| `--format json` | Newline-delimited event log |
| `--auto` | Auto-approves only what is not denied |

- The Claude permission prompt appears **once**, when ORCH starts the `opencode` command. After that the run continues in the background.
- `.agent-runs/` is added to `.gitignore` in T01.
- ORCH never trusts a worker's report. It reads `git diff`, re-runs the checks and only then accepts.

### 4.4 Backend generation with Spring Boot CLI + Maven (T10, ORCH)

**Generator:** Maven, generated with the Spring CLI (product owner, 2026-11-26; [ADR-002 §3.2](../ADR/002-backend.md#32-project-initialization-spring-initializr-via-spring-boot-cli-maven), [D-B1](../ADR/002-backend.md#d-b1-initialize-with-spring-initializr-via-spring-boot-cli-as-a-maven-project-with-the-maven-wrapper-no-system-maven)).

**Command,** run from the repository root:
`~/.sdkman/candidates/springboot/current/bin/spring init --build=maven --type=maven-project -l java -b 4.2.0 -j 25 -g com.hsdc -a copilot -n copilot --package-name com.hsdc.copilot --packaging jar -d web,validation,actuator,configuration-processor,devtools,cache app/backend`

**Required flags:**
- `--build=maven` is mandatory, because the CLI defaults to Gradle.
- `-b`/`-j` must be explicit, because the Initializr defaults to Java 17.

**After generation:**
- Check that `pom.xml`, `mvnw` and `.mvn/wrapper/` exist and that there is no `build.gradle`.
- If `mvnw` is not executable, use `sh mvnw` (`chmod` is denied).
- Move `spring.application.name` into `application.yml`. Remove `application.properties` and `HELP.md` with `git rm` after the first commit (this prompts), or ask the human.

### 4.5 HTTP probes and app lifecycle (ORCH)

- **Start:** `curl` is denied, so the backend is started in the background with `./mvnw -Pno-frontend spring-boot:run` (dev), or with `java -jar app/backend/target/*.jar` **from the repo root** (packaged, so the root `.env` is found; [ADR-000 §7](../ADR/000-main-architecture.md#7-environment-variables)).
- **Probe:** `node -e "fetch('http://localhost:8080/actuator/health').then(async r=>console.log(r.status, await r.text()))"`, or `playwright-cli`.
- **Stop:** `kill <pid>`, which prompts.
- **Key:** the real OpenRouter key reaches the app only through the root `.env`, which the human maintains with `OPENROUTER_API_KEY` and `LLM_MODEL`. Tests use `application-test.yml` and never the key.

---

## 5. Dependency matrix (authoritative)

**Owner key:** ORCH = orchestrator (Opus), LUNA = OpenCode + Luna 6 worker, HUMAN = human lead.

**Commit rule** (AGENTS.md "Commit only after verification passes"):
- Each task ends with ORCH reproducing its verification and making one commit (`Area: summary`, named paths only).
- Workers never commit.
- **Failing behavioral tests** (T15, T16, T17) are written and their red run is recorded, but they stay **uncommitted** in the working tree. Each is committed together with the task that turns it green: T15 with T26, T16 with T27, T17 with T41. No commit ever contains a failing test.
- Lane workers run **unit** checks only (`./mvnw -Pno-frontend test`, Vitest), so the pending red `*IT` and E2E files do not block them.

**Acceptance QA rule (D4):** from T26 (backend) and T31 (frontend) on, every runtime-affecting task's acceptance includes a **short manual QA**:
1. Start the affected app part (§4.5).
2. Exercise the changed behavior with `playwright-cli` (screenshot) or an HTTP probe.
3. Record the evidence in the task's tracker entry.

T42 remains the full QA with brand comparison.

**Paths:**
- **BE** = `app/backend/src/main/java/com/hsdc/copilot/`
- **BT** = `app/backend/src/test/java/com/hsdc/copilot/`
- **FE** = `app/frontend/src/app/`

### Phase 0 — Preconditions

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T00** Base branch ready + `feat/poc` | The base branch contains PR #2 (design system) and PR #3 (docs); `feat/poc` is created from it | §10.1–10.2 | Plan approved; base branch decided and both PRs merged (HUMAN) | HUMAN (merge) + ORCH | — | `test -f` for `docs/PRD.md`, `docs/ADR/000-main-architecture.md`, `docs/design-guidelines.md`, `assets/design-tokens.json`, `assets/logo.webp`, `assets/favicon.ico`, `assets/homepage.png`; `git status` clean |
| **T01** Toolchain + OpenCode | JDK 25 and Spring CLI confirmed; OpenCode runs headless with Luna inside the permission box | [ADR-002 §3.1](../ADR/002-backend.md#31-toolchain-installation-course-vm-ubuntu), §4 | T00 | HUMAN (OpenCode credential; root `.env` with `OPENROUTER_API_KEY` + `LLM_MODEL`) + ORCH | `opencode.jsonc`, `.gitignore` (`.agent-runs/`) | `java -version` → 25; `spring --version` (full path) → 4.1.0; `opencode --version`; the §4.2 smoke run blocks (a)–(c) and allows (d); cost recorded; forecast recomputed (§3). Commit `Tooling: configure OpenCode headless workers` |

### Phase 1 — Scaffolds, spikes and contracts (T10 ∥ T11, then serialized)

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T10** Backend scaffold (Maven) | Spring Boot 4.2 Maven project with the wrapper. Surefire, Failsafe, tag exclusion and JaCoCo configured; test discovery proven; health UP | [ADR-002 §3.2](../ADR/002-backend.md#32-project-initialization-spring-initializr-via-spring-boot-cli-maven), [§3.9](../ADR/002-backend.md#39-configuration-applicationyml), [D-B1/D-B2](../ADR/002-backend.md#6-technical-decisions) | T01 | ORCH (§4.4) | `app/backend/**` (generated), `pom.xml` (openai-java 4.113.0, caffeine, wiremock-spring-boot 4.4.3, **failsafe declared**, surefire/failsafe `excludedGroups=${test.excludedGroups}` defaulting to `capture,eval`, JaCoCo, `no-frontend` profile, policies copy-resources), `application.yml`, `app/backend/AGENTS.md` | Separate records:<br>• no `build.gradle`; `./mvnw -v` (Maven 3.x on Java 25);<br>• a throw-away `SmokeTest` + `SmokeIT` + a `@Tag("eval")` test prove that `test` runs 1, `verify` runs 2, and the tagged test runs only with `-Dtest.excludedGroups= -Dgroups=eval` (remove the throw-aways after the proof);<br>• startup: `spring-boot:run` → health UP via the node probe;<br>• the POM has `spring-boot-starter-webmvc` and no Jackson 2.<br>Commit `Backend: scaffold Spring Boot 4.2 Maven application` |
| **T11** Frontend + E2E scaffold | Angular 22 + Material builds, tests and serves; Playwright project with Chromium | [ADR-003 §3.1](../ADR/003-frontend.md#31-initialization), [§3.3](../ADR/003-frontend.md#33-application-structure-appfrontendsrcapp) | T00 (parallel with T10; different folders) | ORCH | `app/frontend/**` (generated, `app.config.ts`, `app.routes.ts` with empty placeholder routes, `src/proxy.conf.json`, `angular.json`, `package*.json` incl. marked, dompurify, eventsource-parser, date-fns, `@angular/material-date-fns-adapter`, `@fontsource/montserrat`), `app/frontend/AGENTS.md`, `app/e2e/{package.json,package-lock.json,playwright.config.ts}` | `npm test -- --no-watch` (count), `npm run build`, `npm start` + `playwright-cli` screenshot, `cd app/e2e && npx playwright install chromium && npx playwright --version`. Commit `Frontend: scaffold Angular 22 with Material` |
| **T12** Spike S-1 (Responses API) | Verbatim captures (a)–(g) in [ADR-001 S-1](../ADR/001-llm-integration.md#d-l1-responses-api-not-chat-completions-for-all-calls) | ADR-001 D-L1 | T10 | ORCH (architecture gate) | `BT/capture/OpenRouterCaptureIT.java` (tag `capture`), **provisional schema records** in `BT/capture/schema/*` (test scope), `app/backend/src/test/resources/openrouter-captures/*` | `./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=capture` (≤ 8 real calls, cost recorded). **Gate:** if (a), (b) or (d) fails, amend ADR-001 to the Chat Completions fallback before T23. Commit `Backend: capture OpenRouter Responses API samples` |
| **T13** Spike S-2 (Decisions API) | Captures of `/api/alpha/decisions` with `gpt-6-luna-decisions` | [ADR-004 D-T1](../ADR/004-triage-decisions-model.md#d-t1-add-a-triage-stage-with-the-openrouter-decisions-api-behind-a-spike-gate) | T12 | ORCH | `BT/capture/DecisionsCaptureIT.java`, `openrouter-captures/decisions-*.json` | ≤ 3 real calls. **Gate (non-blocking):** on failure, drop T28/T29, default `TRIAGE_MODE=off`, record it in ADR-004 Q-T1. Commit `Backend: capture OpenRouter Decisions API samples` |
| **T14** Shared contracts | Compiling records, interfaces and TS types that freeze every wire and module contract; test profile | [ADR-000 §5–6](../ADR/000-main-architecture.md#6-api--interface-contracts), [ADR-001 §4](../ADR/001-llm-integration.md#4-data-structures), [ADR-002 §4](../ADR/002-backend.md#4-data-structures), [ADR-003 §4](../ADR/003-frontend.md#4-data-structures), [ADR-003 §3.7](../ADR/003-frontend.md#37-polish-ui-texts-and-error-handling-single-source), [ADR-004 §4](../ADR/004-triage-decisions-model.md#4-data-structures) | T12, T13 | ORCH | **BE:**<br>• `cases/{CaseForm,CaseType,Category,CaseSession,ChatMessage,TurnInput}.java`;<br>• `llm/{LlmGateway,LlmRequest,StreamResult}.java`;<br>• `analysis/ImageAnalysis.java` and `decision/{DecisionResult,Decision}.java`, promoted from the T12 provisional records;<br>• `triage/{TriageAnswer,TriageResult,RouteDecision}.java`;<br>• `web/sse/*Event.java`, `web/ApiError.java`, `config/*Properties.java`.<br>**BT:** `src/test/resources/application-test.yml` (dummy key and models, both base URLs → WireMock).<br>**FE:** `core/model/*.ts`, `shared/texts.pl.ts` (all of [ADR-003 §3.7](../ADR/003-frontend.md#37-polish-ui-texts-and-error-handling-single-source)).<br>**Root:** `.env.example` ([ADR-000 §7](../ADR/000-main-architecture.md#7-environment-variables)) | `./mvnw -Pno-frontend test` and `npm run build` green. Unit tests: disclaimer = PRD §11; backend and frontend texts are equal. Capture test switched to the main classes. **Frozen after commit** (exception: T20 adds validation annotations to `CaseForm.java`). Commit `Backend+Frontend: add shared contracts` |

### Phase 2 — Failing behavioral tests first (kept uncommitted until green)

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T15** Failing IT: case submission | `POST /api/cases` SSE contract IT, red for the expected reason | [ADR-002 §5](../ADR/002-backend.md#5-interface-contracts), [TAC-002-04/05](../ADR/002-backend.md#technical-acceptance-criteria), [TAC-03/04](../ADR/000-main-architecture.md#technical-acceptance-criteria), AC-22, AC-24, AC-26, AC-40, AC-42 | T14 | LUNA (backend lane) | `BT/web/CaseSubmissionIT.java`, `app/backend/src/test/resources/wiremock/case-submission/**` (from captures or documented synthetic exceptions) | ORCH runs `./mvnw -Pno-frontend verify -Dit.test=CaseSubmissionIT` and confirms it **fails with no handler/404** (not a compile or context error). Cases: event order incl. `error` before `decision`; `response.failed` → `error` last and no case stored; 400 field errors; no internals in bodies; safety + 45 days → ESCALATED. **Not committed** (committed in T26) |
| **T16** Failing IT: chat turn | `POST /api/cases/{id}/messages` IT, red | [ADR-002 §3.4, §5](../ADR/002-backend.md#34-turn-orchestration-and-state), AC-31…AC-36, AC-41 | T15 | LUNA | `BT/web/ChatTurnIT.java`, `…/wiremock/chat-turn/**` | Red for the expected reason. Cases: revision (`changed`, `previousLabel`, `changeReasonPl`); no revision; 4th photo → 422; concurrent → 409 `CASE_BUSY`; a committed `clientMessageId` → replay; failure → nothing committed; follow-up "Jaką datę zakupu podałem?" sends form facts (request journal, AC-34). **Not committed** (committed in T27) |
| **T17** Failing E2E suite | Playwright specs for the main flows, red against the placeholder app | [ADR-003 §8](../ADR/003-frontend.md#8-testing-strategy), [TAC-003-05](../ADR/003-frontend.md#technical-acceptance-criteria), [TAC-08](../ADR/000-main-architecture.md#technical-acceptance-criteria) | T11, T14 | LUNA (frontend lane) | `app/e2e/tests/{return-happy,outside-window,complaint-followup,error-banner,new-case,keyboard,no-horizontal-scroll}.spec.ts` | Run against `npm start` (dev server, T11 placeholder): fails on missing elements. Flows at 360 and 1280 px; scroll check at 360/390/768/1280/1920 px; console-error assertion. Specs assert **structure and form-driven outcomes only**, never what the photo shows (D1). **Not committed** (committed in T41) |

### Phase 3a — Backend lane (strictly one Maven user at a time, ORCH included)

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T20** Validation + photo check | Bean Validation with exact Polish texts; magic-byte `PhotoValidator`; chat text rules | [ADR-002 §3.6](../ADR/002-backend.md#36-request-validation), [ADR-003 §3.7](../ADR/003-frontend.md#37-polish-ui-texts-and-error-handling-single-source), AC-04/05/07/09/10/31/32 | T16 | LUNA | `BE/cases/CaseForm.java` (annotations only), `BE/cases/validation/**`, `BE/cases/PhotoValidator.java`, `app/backend/src/main/resources/ValidationMessages_pl.properties` + `BT/cases/**` unit tests | Tests first, red→green reproduced. Boundaries: 1/2/100/101 chars; 19/20; today/tomorrow/−10y/−10y−1d; 0 B / 10 MB / +1 B. Photo fixtures: the real example photos plus byte-level fixtures (truncated headers, renamed text file) **for validator unit tests only**; no generated images (D1). Commit `Backend: validate case form, chat text and photos` |
| **T21** Policy + prompt repositories | Classpath loading; rule-ID sets extracted; placeholder prompts so the context starts | [ADR-001 §3.1](../ADR/001-llm-integration.md#31-classes-and-responsibilities-package-copilotllm-analysis-decision-prompts), [D-07](../ADR/000-main-architecture.md#d-07-policies-and-prompts-as-versioned-markdown-files) | T20 | LUNA | `BE/policy/**`, `BE/prompts/**`, **placeholder** `app/backend/src/main/resources/prompts/*.md` (one line each, marked `PLACEHOLDER`), `app/backend/src/main/resources/messages/guardrail-reasons.pl.properties` (empty keys) + tests | ID sets equal [ADR-001 §3.1](../ADR/001-llm-integration.md#31-classes-and-responsibilities-package-copilotllm-analysis-decision-prompts) (incl. `R7`, `R7.1`); a missing file fails startup (unit test with a temp classpath). Commit `Backend: load policies and prompts` |
| **T22** Facts + guardrails + rule-ID tools | G1–G6, precedence, ID replacement, `RuleIdStreamFilter`, day counting | [ADR-001 §3.4](../ADR/001-llm-integration.md#34-decision-pipeline-and-guardrails), [PRD §11 precedence](../PRD.md#precedence-when-criteria-conflict), AC-23…AC-27 | T21 | LUNA | `BE/decision/{CaseFacts,Guardrails,RuleIdValidator,RuleIdStreamFilter}.java` + tests | 100% branch coverage via the JaCoCo report (TAC-001-04). Day 30/31; 2 years ± 1 day incl. 29 February; lexicon with and without diacritics; 45 days + safety → ESCALATED `R7.1`; G6 with < 15 s left; ID split across deltas. Commit `Backend: add decision guardrails` |
| **T23** Responses gateway | `ResponsesApiGateway` + `OpenAiClientConfig` (`maxRetries(0)`, per-call timeouts) | [ADR-001 §3.3, §3.6](../ADR/001-llm-integration.md#33-context-assembly-stateless-replay), [TAC-001-01/02/05](../ADR/001-llm-integration.md#technical-acceptance-criteria) | T12, T22 | LUNA; escalate to Sonnet after 2 failures | `BE/llm/**`, `BE/config/OpenAiClientConfig.java` + `BT/llm/*IT.java` | WireMock IT from captures: structured parse; ordered deltas; reasoning not forwarded; no `store`/`previous_response_id`; 401/502/timeout mapping; history replay order. ORCH runs `verify -Dit.test='*Gateway*IT'`. Commit `Backend: add OpenRouter Responses gateway` |
| **T24** Store, locking, errors, config beans | Caffeine TTL store; per-case lock; replay index; `ApiExceptionHandler`; clock, executor and store beans | [ADR-002 §3.4, §3.7](../ADR/002-backend.md#37-error-handling), [D-B3](../ADR/002-backend.md#d-b3-server-authoritative-turn-with-atomic-commit-and-per-case-lock) | T23 | LUNA | `BE/cases/{CaseStore,CaseLocks}.java`, `BE/web/ApiExceptionHandler.java`, `BE/config/{ClockConfig,ExecutorConfig,CaseStoreConfig}.java` + tests | TTL via a fake ticker; LRU at 501; `tryLock` 409; bodies for 400/404/409/413/422/500 have no internals; codes as in [ADR-002 §4](../ADR/002-backend.md#4-data-structures). Commit `Backend: add case store, locking and error mapping` |
| **T25** Prompt texts | All six real prompt files + the guardrail reason texts | [ADR-001 §3.2, §3.5](../ADR/001-llm-integration.md#32-prompt-files-appbackendsrcmainresourcesprompts), [PRD §11](../PRD.md#11-agent--system-behavior-specification) | T12, T21 | ORCH (quality-critical; runs while the lane works, edits text files only, no Maven run) | `app/backend/src/main/resources/prompts/*.md`, `…/messages/guardrail-reasons.pl.properties` | Reviewed against PRD §11 allowed/not-allowed, tone, length, cite-only-`decision.ruleIds`, triage-signal block; placeholders match `PromptRepository`. Verified in the next Maven slot. Commit `Backend: add AI instruction prompts` |
| **T26** First-decision pipeline (integration) | `ImageAnalysisService`, `DecisionService`, `CaseService.create`, `SseEventWriter` (heartbeat, cancel), `CaseController`, `DELETE /api/cases/{id}` | [ADR-000 §9.3](../ADR/000-main-architecture.md#93-sequence-diagrams), [ADR-002 §3.5](../ADR/002-backend.md#35-sse-emission-sseeventwriter), [ADR-001 §3.6](../ADR/001-llm-integration.md#36-streaming-cancellation-and-errors) | T15, T20–T25 | ORCH | `BE/analysis/**`, `BE/decision/DecisionService.java`, `BE/cases/CaseService.java`, `BE/web/{CaseController,SseEventWriter}.java` | **T15 green**; `./mvnw -Pno-frontend verify -Dit.test='!ChatTurnIT'` (counts and skips recorded). **QA:** backend + `npm start`, POST via the browser placeholder or an HTTP probe, events observed. Commit (incl. T15's files) `Backend: stream first decision for new cases` |
| **T27** Chat turn (integration) | `MessageController`, chat photo analysis, revision, atomic commit/rollback, replay, photo counter, chat deadline | [ADR-000 §9.3 chat](../ADR/000-main-architecture.md#chat-message-with-an-optional-photo-and-a-revised-decision-flow-46), [ADR-002 §3.4](../ADR/002-backend.md#34-turn-orchestration-and-state) | T16, T26 | ORCH | `BE/web/MessageController.java`, `BE/cases/CaseService.java` (turn method) | **T16 green** + full `./mvnw -Pno-frontend verify`. QA: probe one chat turn. Commit (incl. T16's files) `Backend: add chat turns with decision revision` |
| **T28** Triage components (assist) | `DecisionsApiClient`, question sets (incl. `reason_reports_defect`), `TriageService`, `TriageRouter` + `FastPathRuleTable` (label only, for the agreement log), `TriageAgreementLogger` | [ADR-004 §3](../ADR/004-triage-decisions-model.md#3-component-design) | T13 passed, **T27 accepted** (shared Maven lane) | LUNA | `BE/triage/**` (no templates, no renderer), `app/backend/src/main/resources/triage/**` + tests | Router table incl. bounds 0.80/0.95; T1 safety before T2 window; AUDIO/OTHER and a defect-reporting reason never T5; mapping from captured responses. Commit `Backend: add decisions-model triage` |
| **T29** Triage wiring (integration) | Triage runs in `assist` mode before the LLM, first submission only; signals in CASE FACTS; triage safety feeds G1; fallback on failure | [ADR-004 §3.1, §3.4](../ADR/004-triage-decisions-model.md#31-position-in-the-pipeline), [TAC-004-01/02](../ADR/004-triage-decisions-model.md#technical-acceptance-criteria), [ADR-001 G1](../ADR/001-llm-integration.md#34-decision-pipeline-and-guardrails) | T28 | ORCH | `BE/cases/CaseService.java`, `BE/decision/DecisionService.java` (context assembly), `application.yml` | IT: `off` → zero Decisions calls; `assist` → one call + agreement log line; Decisions 502/timeout → normal decision with no error event; triage P(safety)=0.6 → ESCALATED. **Re-run all earlier ITs** (they now hit the WireMock Decisions stub). QA probe. Commit `Backend: run triage in assist mode` |

### Phase 3b — Frontend lane (one Luna worker at a time; parallel to Phase 3a)

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T30** Theme + app shell | M3 theme with exact tokens; Montserrat; navy header with logo, product name and subtitle; favicon | [ADR-003 §3.2](../ADR/003-frontend.md#32-theming-material-3--brand-tokens), design guidelines | T17 written | LUNA | `app/frontend/src/styles.scss`, `app/frontend/src/styles/**`, `FE/app.{ts,html,scss}`, `app/frontend/public/{favicon.ico,logo.webp}` (copied from `assets/`) | `npm run build`; no hex outside `_tokens.scss` (TAC-003-02, grep); ORCH screenshot vs `assets/homepage.png` header. Commit `Frontend: apply brand theme and app shell` |
| **T31** Case form | Form page, type cards, photo picker (no `capture`), reducer, validators, progress steps, submit banner | [ADR-003 §3.4](../ADR/003-frontend.md#34-case-form-behavior), [§3.7](../ADR/003-frontend.md#37-polish-ui-texts-and-error-handling-single-source), [PRD §9.1](../PRD.md#91-screen-1--case-form), AC-01…AC-15, AC-40 | T30 | LUNA | `FE/features/case-form/*` (files of [ADR-003 §3.3](../ADR/003-frontend.md#33-application-structure-appfrontendsrcapp)), `FE/core/image/*` + specs | Vitest + harness specs first: order, options, texts, focus on the first invalid field, type-dependent hint and marker, live counter (`updateOn: 'change'`), reducer ≤1600 px JPEG with the real example photos. **ORCH then wires `''` → `CaseFormPage`** in `app.routes.ts`. QA: form screenshots at 390 and 1280 px. Commit `Frontend: add case form` |
| **T32** SSE client, API, store | `sse-stream`, `CaseApiService` (local `yyyy-MM-dd` date), `CaseStore` incl. watchdog, error table, first-submit error after `decision`, replay, retry, photo counter on `done` | [ADR-003 §3.5, §3.7](../ADR/003-frontend.md#35-sse-client-and-chat-flow), [ADR-002 §5](../ADR/002-backend.md#5-interface-contracts) | T31 | LUNA | `FE/core/{sse,api,state}/*` + specs | Mid-line chunk splits, `: ping`, a stream without `done`, the 60 s watchdog (fake timers), every §3.7 error row, date at 00:30 CET, `decision`→`delta`→`error` on the first submit → back to the form. Commit `Frontend: add SSE client and case store` |
| **T33** Chat screen | Summary panel (sidenav / expansion), message list, assistant segments, markdown pipe, badge, typing indicator, new-case dialog, guard, `beforeunload`, thumbnail dialog | [ADR-003 §3.3, §3.6](../ADR/003-frontend.md#36-markdown-rendering-during-streaming), [ADR-001 §3.5](../ADR/001-llm-integration.md#35-message-composition-what-is-model-generated-and-what-is-fixed), [PRD §9.2–9.3](../PRD.md#92-screen-2--decision-chat), AC-22, AC-26, AC-30, AC-35–AC-37, AC-43, AC-44 | T32 | LUNA | `FE/features/chat/{chat.page,case-summary,message-list,assistant-message,user-message,typing-indicator,new-case-dialog}.*`, `FE/shared/decision-badge.*`, `FE/core/{markdown,guards}/*` + specs | Sanitization specs; segment order (safety before change block; safety block only with a badge); dialog focus on "Anuluj"; 404 reset without the dialog. **ORCH then wires `'sprawa'` → `ChatPage` with the guard.** QA: chat screenshots against the running backend. Commit `Frontend: add chat screen` |
| **T34** Composer + chat behavior | Composer (Enter / Shift+Enter, counter, photo preview, limit hint), failed-message retry, auto-scroll with "Nowe wiadomości", `LiveAnnouncer` | [ADR-003 §3.5](../ADR/003-frontend.md#35-sse-client-and-chat-flow), AC-31…AC-33, AC-38, AC-39, AC-41, AC-47 | T33 | LUNA | `FE/features/chat/composer.*`, `FE/features/chat/message-list.*` (scroll logic only) + specs | Key handling, empty and 2001-char blocking, photo-only send, attach disabled at 3, scroll pinned vs not. QA: a chat turn by keyboard only. Commit `Frontend: add chat composer` |

### Phase 4 — Integration and verification

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T40** Packaging into the JAR | `./mvnw package` builds Angular, copies dist + policies; SPA fallback | [ADR-002 §3.8](../ADR/002-backend.md#38-packaging-angular-into-the-jar-and-spa-routing), [D-B5](../ADR/002-backend.md#d-b5-angular-built-by-maven-with-frontend-maven-plugin-policies-copied-from-docs) | T29 (or T27 if triage was dropped), T34 | ORCH | `pom.xml` (frontend-maven-plugin 2.0.2, copy-resources), `BE/web/SpaForwardingConfig.java` + IT | Separate records: build (`./mvnw package`); tests (Surefire, Failsafe and Vitest counts, all non-zero, TAC-002-07); startup from the repo root (`java -jar` → health UP < 15 s; `/` and `/sprawa` → index.html; `/main-*.js` → JS; `/api/x` → JSON 404). Commit `Build: package Angular into Spring Boot JAR` |
| **T41** E2E on the real stack | T17 green against the JAR with real OpenRouter | [ADR-000 §10](../ADR/000-main-architecture.md#10-testing-strategy) | T40 | ORCH runs; LUNA may fix selectors and waits only | `app/e2e/**` (selectors and waits only) | `npx playwright test` (≤ 8 real runs, cost recorded); no console errors; the error-banner spec uses the app started with an invalid key. Commit (incl. T17's files) `E2E: pass PoC flows on real stack` |
| **T43** AI evals + triage record | Form- and policy-driven eval suite; per-photo record without pass/fail | [ADR-001 §8](../ADR/001-llm-integration.md#8-testing-strategy), [ADR-004 §8](../ADR/004-triage-decisions-model.md#8-testing-strategy), [TAC-07](../ADR/000-main-architecture.md#technical-acceptance-criteria) | T29, T41 (runs after it; shared API rate limit) | LUNA writes `app/evals/cases/*.json` and `BT/eval/EvalIT.java`; ORCH runs and judges | `app/evals/**`, `BT/eval/**`, `docs/plans/qa/poc/eval-report.md` | `./mvnw -Pno-frontend verify -Dtest.excludedGroups= -Dgroups=eval` (3 repeats unless F5): 100% on the windows, safety wording, injection, cited IDs ⊆ policy, Polish reply; the photo record lists the analyses and labels. A failed safety or window case blocks the PR. Commit `Backend: add AI decision evals` |
| **T42** Full manual QA | Every screen exercised by hand; brand comparison | [AGENTS.md Manual QA](../../AGENTS.md), design guidelines | T41, T43 | ORCH via `playwright-cli` + HUMAN look | `docs/plans/qa/poc/*.png` | Screenshots at 390 and 1280 px: form (empty, errors, photo), progress, chat (accepted, rejected, needs-info with a photo re-send, safety case, revision), dialog, error banner, chat inline errors. Checklist: navigation, data, Polish text, clean console, colors, typography, radius, logo on navy vs `assets/homepage*.png`. Defects go back to the owning task. Commit `Docs: add PoC QA evidence` |

### Phase 5 — Review and publication

| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff |
|---|---|---|---|---|---|---|
| **T50** Independent review | Findings triaged and fixed | AGENTS.md "Code Review Rules" | T42 | Fresh Opus subagent (diff + PRD + ADRs only) + `/code-review` + HUMAN | Fixes in the owning task's files | Each finding is marked fixed / not-a-bug with evidence; full verification re-run (build, all test counts, startup, E2E, QA spot-check) |
| **T51** Pull request | PR `feat/poc` → the agreed base branch | §10.2 | T50 + HUMAN "publish" | ORCH | — | PR body: scope, AC traceability (§1), commands with counts, QA screenshots, eval report, OpenRouter spend, the note about the PR-agent workflow check. **No merge by agents** |

---

## 6. Critical path, parallelism and hotspots

**Critical path:** T00 → T01 → T10 → T12 → T13 → T14 → T15 → T16 → T20 → T21 → T22 → T23 → T24 → T26 → T27 → T28 → T29 → T40 → T41 → T43 → T42 → T50 → T51.

The backend lane, with ORCH's own Maven runs included, is the long pole.

**Parallel lanes:**

| Window | Concurrent work | Notes passed to every worker |
|---|---|---|
| After T00 | T10 ∥ T11 | Both ORCH, different folders |
| After T14 | **Backend Maven lane:** T15 → T16 → T20 → T21 → T22 → T23 → T24 → T26 (ORCH) → T27 (ORCH) → T28 → T29 (ORCH) ∥ **Frontend lane:** T17 → T30 → T31 → T32 → T33 → T34 | At most **2 Luna workers at once**, one per lane. Each brief names the other lane's running task and files as off-limits, and names the pending uncommitted test files as do-not-touch |
| ORCH during the lanes | T25 (text files only, no Maven); route wiring after T31/T33; per-task QA | ORCH does not run Maven while a backend worker runs |
| After both lanes | T40 → T41 → T43 → T42 | Sequential: shared API rate limit and budget |

**Shared-file hotspots** (ORCH-only, serialized):
- **Build descriptors and lockfiles:**
  - `app/backend/pom.xml`: security hook; OpenCode is denied by policy;
  - `app/frontend/package*.json`, `app/e2e/package*.json`, `angular.json`: workers report missing dependencies;
  - `application.yml`, `application-test.yml`.
- **App wiring and config:** `app.config.ts`, `app.routes.ts`, `src/proxy.conf.json`, `.env.example`, `.gitignore`, `opencode.jsonc`.
- **T14 contract files:** frozen; the only exception is T20's `CaseForm` annotations.
- **Integration services:** `CaseService.java` and `DecisionService.java` (T26 → T27 → T29, all ORCH).
- **Build directories:**
  - `app/backend/target/`: exactly one Maven process at a time, workers and ORCH alike;
  - `app/frontend/.angular/` and `node_modules/`: one frontend process at a time.

**Checkout:** one checkout (the existing worktree), branch `feat/poc` created in T00. No further worktrees. After the PR is merged or abandoned, ORCH proposes `git worktree remove` + `git worktree prune`, with approval.

---

## 7. Briefs and acceptance protocol (after authorization)

**Brief:**
- Written by ORCH immediately before dispatch as `.agent-briefs/<task>.md`.
- `.agent-briefs/` is gitignored by the repository ("Private agent briefs"), so briefs stay durable on disk but are not committed. Each task's commit message names its task ID for traceability.
- **Sections:**
  1. **Goal and observable outcome.**
  2. **Concurrency:** the running task in the other lane; off-limits files; pending uncommitted tests.
  3. **Exact editable files.** Anything else is forbidden and is also blocked by `opencode.jsonc`.
  4. **Inlined inputs:**
     - T14 contract excerpts;
     - predecessor outputs;
     - the exact PRD AC texts, ADR rules and §3.7 strings for this task;
     - relevant Context7 excerpts.
  5. **Ordered TDD steps:** write tests → run → confirm the expected failure → implement → run.
  6. **Test inputs and expected results.**
  7. **Commands**, copied from the verified T10/T11 output, as typed from the lane's cwd.
  8. **Report:** files changed, command tails, red→green counts, contradictions, anything not done.
- **Rules repeated in every brief:**
  - no scope, contract or dependency changes;
  - no commits;
  - no `.env` access;
  - never pre-assume what a photo shows (D1);
  - report contradictions instead of resolving them.

**Acceptance by ORCH:**
1. Read the full `git diff`, checked against the editable-file list.
2. Re-run the task's checks and the lane's unit suite.
3. Short QA where the task affects the running app (D4).
4. Check the AC/ADR traceability.
5. Commit with named paths.
6. Update the Claude Code task list.

---

## 8. Failure handling and rollback

| Situation | Handling |
|---|---|
| Worker output fails verification | Reject it. Revert that task's tracked files with `git restore <paths>`; list new untracked files and ask the human to delete them (`rm` is denied). Re-dispatch once with the failure output. Second failure → Sonnet. Third → ORCH implements |
| Worker edits a forbidden file | It should be blocked by `opencode.jsonc`. If not, reject, revert, tighten the config and re-dispatch |
| Contradiction between PRD/ADR and reality | The worker stops and reports. ORCH decides and records architectural changes as an ADR amendment before continuing |
| S-1 fails | Pause T23/T26. Amend ADR-001 to Chat Completions (D-L1). Re-brief T23 |
| S-2 fails | Drop T28/T29, set `TRIAGE_MODE=off`, record it in ADR-004 Q-T1 |
| A committed task is later found wrong | `git revert <sha>` of that task's commit |

---

## 9. Fallbacks (cut lines, applied in this order)

| ID | Trigger | Cut |
|---|---|---|
| F1 | Time short, or S-2 fails | Drop triage: T13 result recorded; T28/T29 skipped; `TRIAGE_MODE=off` |
| F2 | OpenCode unusable, or spend ≥ $1.50 | The same matrix runs **sequentially** with Claude Code subagents (`model: sonnet`), using the same briefs |
| F3 | Time still short | Drop chat photos (AC-32/AC-33). This is a **scope change** that must first be recorded in the PRD with the product owner (AGENTS.md); the composer becomes text-only |
| F4 | Time critically short | Deliver **slice 1 only** (return: form → first streamed decision): T10–T15, T17 (return specs only), T20–T26 (T20's prerequisite becomes T15), T30–T33 (chat without composer), T40–T42. Record the cut in the PRD |
| F5 | Spend ≥ $1.90 | One eval repeat and one final E2E run only |
| — | Initializr unreachable (`spring init` fails) | The human installs Maven once (`! sudo apt install maven`) and runs `mvn -N wrapper:wrapper` in a hand-written Boot 4.2 Maven project. The POM goes through ORCH and the `pom.xml` hook |
| — | JDK unusable | No meaningful cut. Finish the frontend lane with unit tests only, report the blocker and wait |

---

## 10. Decisions

**Resolved 2026-11-26** (recorded in [PRD §12](../PRD.md#decisions-made-with-the-product-owner-2026-11-26)):
- D1 evals;
- D2 brand;
- D4 per-task QA;
- D5 the $2 budget with cuts;
- R7.1;
- Maven generated by Spring CLI;
- no privileged commands needed.

**Open:**
1. **Base branch contents:** the base must contain PR #2 (design system) and PR #3 (docs) before T00.
2. **Base branch name:** the course README says to develop in `app-implementation`, not `main`. You postponed creating it (2026-11-26). Until you decide, PR #2 and PR #3 keep base `main`, and T00 is blocked.
3. **OpenCode credential method:** confirmed in T01; you enter the key.
4. **Approval prompts:** `./mvnw`, `java`, `opencode`, `playwright-cli`, `gh`, `kill`, `cp`, `mkdir` and `git rm` each prompt. You can add narrow allow rules in `/permissions` to speed execution. I do not edit permission settings.
5. **Carried-over product questions** (non-blocking):
   - ADR-003 Q-F1: CTA contrast.
   - PRD §12 assumption: the photo limit while the case still needs more information.
   - ADR-004 Q-T2/Q-T3: moot while the fast path is disabled.

No new ADR is needed. Any contradiction found by S-1, S-2 or T10 becomes an ADR amendment proposed by ORCH before work continues.

---

## 11. First bounded task and review gate

**Review gate (now):** you review this plan and the revised PRD/ADRs (PR #3). Planning does not authorize execution.

**After your "go"** and the §10.1–10.2 decisions, the first bounded task is **T00**:
1. The base branch contains PR #2 and PR #3.
2. Create `feat/poc`.
3. Run the `test -f` checks.

**T01** then needs from you only the OpenCode credential and a root `.env` with `OPENROUTER_API_KEY` and `LLM_MODEL`.
