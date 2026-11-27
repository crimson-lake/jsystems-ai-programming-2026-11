---
name: plan-implementation
description: Turn an approved PRD and ADRs into a reviewable, executable implementation plan (dependency matrix with Status/Issue columns, phase gates with playwright-cli manual tests, recorded-only mock policy, Definition of Done) and get it through independent review, decisions, publication as a PR and optional GitHub Issues. Use this whenever the user asks to plan an implementation, write an implementation plan or task breakdown, split work for agents/subagents/OpenCode/Codex workers, prepare a /goal run, or "check the plan/PRD/ADRs for contradictions" — even if they only say "make a plan from the PRD" or "przygotuj plan implementacji".
---

# Plan an implementation from PRD + ADRs

This skill distils a full planning session (PRD → ADRs → plan → three review rounds → issues → /goal handoff). Almost every rule below exists because skipping it produced a real defect last time. Follow the phases in order; each ends with something the human can check.

**Outputs:** `docs/plans/<feature>-implementation-plan.md` (status: DRAFT until the human accepts), optional GitHub issues, a docs PR. Planning never authorizes execution, new agents, publication or merge — ask for each.

Bundled resources (read when the phase says so):
- `references/plan-template.md` — section skeleton and matrix format.
- `references/review-checklist.md` — every defect class found in past reviews; run it before any reviewer sees the plan.
- `references/reviewer-briefs.md` — copy-paste briefs for the two independent reviewers.
- `scripts/check-links.cjs` — validates relative links and heading anchors across docs.
- `scripts/plan-to-issues.cjs` — builds/creates one GitHub issue per matrix row and writes `#n` back into the plan.

---

## Phase 0 — Isolate and orient (git)

- If another Claude session works in the same checkout (fork notice, or the user says so), **work in your own git worktree** before editing: `git worktree add -b <branch> .claude/worktrees/<name> <base>` then enter it. Create it from the commit that already contains the PRD/ADRs (often not `origin/main`), and make sure `.claude/worktrees/` is in `.gitignore`.
- Read `README.md` for the repo's branch policy (e.g. "develop in `app-implementation`, not `main`") **before** recommending a PR base. Last time `main` was recommended against the README.
- Never `git add .`/`-A`. Stage named paths. Never stage untracked files you did not create (e.g. a user's `.env` rename, other sessions' folders).
- If `.env.example` (tracked) shows as deleted, the user probably renamed it; ask, restore with `git restore`, never read `.env`.
- Long one-liners that mix `cd`, `git` and other commands may be refused in a worktree session; split them into separate plain commands.

## Phase 1 — Inspect reality (report it in plan §2)

Collect and write down, with the commands you ran:
- App code, build descriptors, entry points, tests — or "none" (no baseline).
- Toolchain versions actually installed (`--version`), and what is missing. Check user-local installs (`~/.local/bin`, `~/.sdkman/candidates/*/current/bin`) before declaring a tool missing — a Spring CLI was hiding in SDKMAN.
- **Agent permissions**: read `.claude/settings.json` allow/ask/deny and hooks. Plan around denies (`curl`, `rm`, `sudo`, `chmod`, `env` are common) instead of discovering them mid-execution. Note hooks (e.g. a security hook on `pom.xml` edits that external workers would bypass).
- `.gitignore` rules that affect the plan (`.agent-briefs/`, `test-results/`, `target/`) — do not plan to commit ignored paths.
- Tracker availability: `gh repo view --json hasIssuesEnabled`; enabling issues needs admin rights the agent token may lack (HTTP 403) — ask the human to enable them in Settings.
- Repo rules hidden in asset READMEs (e.g. example photos: "real photos only, never pre-assume what the model sees") — they constrain tests and evals.
- Open PRs and where the design/doc assets actually live (a plan that needs `design-guidelines.md` must make "that PR is merged into the base" a prerequisite).
- CI workflows that fire on every PR (secrets, cost).

## Phase 2 — Ask the consequential questions (form, not prose)

Use the question tool (2–4 questions per call, recommended option first). Typical decisions that changed the plan last time:
- Scope: one vertical slice vs the whole PoC (the user switched mid-way — design the plan so a slice is a fallback cut line).
- Base branch name and PR order.
- Budget cap for paid model calls, and cut lines.
- Worker model/provider (e.g. OpenCode + a cheap model) vs the orchestrator.
- Mock policy (recorded-only?), QA cadence (after every task? every phase?), evidence location, demo video.
- Product questions the ADRs left open (e.g. what happens at a limit).
Record every answer with its date in the PRD §"Decisions" and in plan §Decisions, so a new session does not ask again.

## Phase 3 — Verify facts before planning on them

- Library/CLI facts: Context7 first, then `<tool> --help`, then vendor docs. Record versions and IDs.
- **Never invent capabilities.** If a model/API cannot be confirmed (a model page returned 404), plan a **spike task** that captures real responses and gates the dependent tasks, with a fallback.
- Capture-first: any mock must come from a recorded real call. Plan the capture tasks (spikes) before the tasks that need stubs, including **real error responses** (invalid key, invalid model).

## Phase 4 — Draft the plan

Read `references/plan-template.md` and fill it. Non-negotiables (each was a past defect):
- **One authoritative dependency matrix** per phase: `Task | Observable outcome | PRD/ADR links | Prerequisites | Owner/model | Editable files | Verification and handoff | Status | Issue`. `Status` is the tracker of record.
- Link exact PRD criteria and ADR sections (`file.md#anchor`), never copy them. Missing architecture → propose an ADR amendment, not a plan assumption.
- **Failing behavioral test before implementation**, but **never commit a red test**: keep it uncommitted until the task that turns it green, and make lane workers run only unit checks so the pending red files don't block them. Red tests may reference only already-frozen contract types (so they compile).
- **Test discovery**: if the stack splits unit/integration (Maven Surefire `*Test` / Failsafe `*IT`), declare the IT runner explicitly and prove non-zero counts at scaffold time — otherwise ITs silently never run and look green.
- **Profiles/secrets**: tests must never load the real key; only deliberate real-API tests (capture/eval) do, under their own profile.
- **Mock policy**: E2E and manual tests use the real app and real external APIs, no mocks. Unit/integration stubs only from recordings; transport faults (delay, connection reset) are the only allowed additions; uncapturable shapes are reported, not stubbed. Frontend protocol tests use recordings of the **real backend**; impossible sequences are spliced from recorded events.
- **Coverage proof**: E2E must prove every endpoint and every AI/external call step ran (network log + one structured log line per external call).
- **Phase gates**: after every phase a manual test gate with `playwright-cli` against the real running app; screenshots saved in the repo (`docs/plans/qa/<feature>/<ID>/…`) with an index README.
- **Shared-file hotspots and serialization**: lockfiles, build descriptors, contracts, and build output dirs (`target/`, `.angular/`) — one process per build dir, orchestrator included.
- Every file a task needs has exactly one owner; frozen contract files are excluded from worker globs.
- External workers (OpenCode/Codex) get a **deny-by-default** permission box, explicit command allow-list matching their cwd, and a T01 smoke test proving forbidden edits/commands are blocked. They never commit, never edit build descriptors.
- Budget table with cut lines; recompute after the first real worker run.
- Fallbacks in order, each a smaller but demonstrable deliverable; scope cuts recorded in the PRD.
- **Definition of Done** section (stop condition for `/goal`) plus explicit "stop and ask" conditions.
- Final documentation task: demo video recorded with `playwright-cli video-start/chapter/stop`, encoded with ffmpeg (no sudo: `npm i -g --prefix ~/.local ffmpeg-static`) to H.264 ≤ 25 MB, committed and linked from the PR.

## Phase 5 — Self-check, then independent review

1. Run `node <skill>/scripts/check-links.cjs docs` — fix every broken link/anchor (links to files arriving with another PR are expected; say so).
2. Walk `references/review-checklist.md` yourself.
3. Spawn **two fresh reviewers in parallel** with the briefs in `references/reviewer-briefs.md` (documents consistency; plan executability vs agent config). Give them files, not your conclusions — you wrote the docs and will not see your own errors (last time: 50 findings, then 22 after fixes).
4. **Verify the top findings yourself** in the files before reporting; reject false ones (a reviewer claimed a photo was missing — it was not).
5. Report grouped: blockers / contradictions / decisions for the human (as a form) / feasibility / minor. Do not fix silently what needs a product decision.
6. Fix in one pass, commit per logical change (PRD+policy, ADRs, plan, agent instructions), re-run the link check, and re-review until no blocker/major remains. Minor items may be deferred into the plan.

## Phase 6 — Publish and track

- Push the docs branch and open the PR as **separate** commands (a combined push+PR command was refused once). PR body: scope, decisions, verified vs unverified facts, open questions, dependency on other PRs.
- Optional issues: after the human enables Issues, run `node <skill>/scripts/plan-to-issues.cjs --plan <plan> --repo <owner/repo> --ref <branch>` (dry run), then `--create`. It creates labels, one issue per row, writes `#n` into the `Issue` column; commit the plan. Plan `Status` stays authoritative; issues mirror it (`Refs #n` in commits, `Closes #…` in the final PR).
- Mark the plan `ACCEPTED` only when the human says so.

## Phase 7 — Hand off for execution

- Base branch: create it (e.g. from `origin/main`, merging the design and docs PRs) and verify the T00 prerequisites (`test -f` of required assets, link check).
- Give the human the exact `/goal` prompt (pointing at the plan and its Definition of Done), and recommend narrow `/permissions` allow rules for the commands the plan needs (`./mvnw`, `java -jar`, `playwright-cli`, `opencode`, `gh issue`), otherwise autonomous runs stall on prompts.
- Resolve every open decision that would stop the run (check the DoD stop list) before handing off.
- Suggest a skill from repeated work (e.g. plan → issues) and nested `AGENTS.md` per app folder.

## Communication

Chat in the user's language; documents in English unless agreed. Lead with what changed and what needs the human; keep raw evidence in files, not in chat.
