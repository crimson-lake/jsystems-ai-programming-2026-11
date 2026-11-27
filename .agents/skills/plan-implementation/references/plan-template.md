# Plan template

Copy this skeleton to `docs/plans/<feature>-implementation-plan.md`. Replace `<…>`. Keep links to PRD/ADR anchors; do not copy their text.

```markdown
# Implementation Plan — <product> <scope>

> **Status: DRAFT, awaiting review by the product owner/human lead.**
> **Date:** <YYYY-MM-DD> · **Author:** orchestrator (<agent, model>)
> This plan does not authorize execution, new agents, publication or merge. Each phase starts only after an explicit "go".

## 1. Scope
- Deliverable (one branch + one PR, one commit per task), flows and AC range with links.
- ADR table (number → area). Design references (and which PR brings them).
- Product decisions applied (dated, also recorded in PRD §Decisions).
- Traceability: PRD criteria → implementing tasks → proving tests/gates.
- Out of scope (PRD out-of-scope, disabled modes, deferred items).

## 2. Current state (inspected <date>)
| Item | Observed |  ← checkout/branches/PRs, design assets present?, app code, toolchain (present/missing, versions), agent permissions (deny/ask/hooks), .gitignore rules, repo automation, tracker, baseline.
Say which commands were verified and that project commands are first verified in the scaffold tasks.

## 3. Agents, models and budget
Roles table (orchestrator, worker, escalation, reviewer, human) with model, provider, use, budget.
Pricing facts with date and source; models not chosen and why. Budget table + cut lines (75% → fallback, 90% → minimal evals/E2E). Harness check. "No custom role files; ad hoc briefs."

## 4. Tooling
4.1 Worker install/check (record real flags). 4.2 Worker permission box: deny-by-default, exact allow-list per cwd, hard policies (no edits to build descriptors, lockfiles, .env, docs, agent config), smoke test. 4.3 Running one task (command, log path). 4.4 Scaffold commands. 4.5 Manual testing with playwright-cli and app lifecycle (start, UI checks, API checks via `page.request`, evidence in repo, stop), key handling, mock policy, E2E instances (base URL env, bad-key instance). 4.6 Demo video (playwright-cli video + ffmpeg).

## 5. Dependency matrix (authoritative)
Owner key. 5.1 Status tracking (values, who updates when, commit prefix `<ID> <Area>: <summary>`, mirrors: agent task list, GitHub issues).
Commit rule (red tests stay uncommitted until green). Acceptance QA rule. Phase gates rule.

### Phase 0 — Preconditions
| Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff | Status | Issue |
|---|---|---|---|---|---|---|---|---|
| **T00** … | … | … | … | … | … | … Commit `T00 Area: …` | TODO | — |
| **Q0** Phase 0 gate | … | … | … | ORCH | `docs/plans/qa/<feature>/Q0/…` | playwright-cli checks … | TODO | — |

### Phase 1 — Scaffolds, spikes (real captures incl. errors), shared contracts → gate Q1
### Phase 2 — Failing behavioral tests (uncommitted until green) → gate Q2
### Phase 3a/3b — Lanes (one worker per lane, one process per build dir) → gate Q3
### Phase 4 — Packaging, E2E on real stack with coverage proof, evals, full manual QA (Q4)
### Phase 5 — Independent review, gate Q5, demo video, PR (no agent merge)

## 6. Critical path, parallelism and hotspots
Critical path incl. gates. Parallel windows table (what runs concurrently; what each brief must say). Hotspots (orchestrator-only files, frozen contracts, build dirs). Checkout/worktree policy and cleanup.

## 7. Briefs and acceptance protocol
Brief location (often gitignored → not committed), sections (goal, concurrency, editable files, inlined inputs, TDD steps, test data, commands, report), rules repeated in every brief. Acceptance steps (diff vs file list, re-run checks, QA, traceability, commit with Status=DONE, mirror).

## 8. Failure handling and rollback
## 9. Fallbacks (ordered cut lines; scope cuts recorded in the PRD)
## 10. Decisions (resolved with dates / open, each with the task it blocks)
## 11. First bounded task and review gate
## 12. Definition of Done (numbered, evidence-based) + "stop and ask" conditions
```

Matrix cell rules:
- Observable outcome: something a person can see or a command can prove.
- Prerequisites: task IDs and explicit artifacts (e.g. "T26 recordings"), never vague phases.
- Editable files: exact paths/globs; exclude frozen files; one owner per file.
- Verification: commands as run from a stated cwd, expected counts, QA evidence path, commit message with the task ID.
