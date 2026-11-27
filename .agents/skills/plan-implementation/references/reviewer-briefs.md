# Independent reviewer briefs

Spawn both reviewers **in parallel** as fresh general-purpose subagents. Give them paths, not your conclusions. Both are read-only. After they report, verify the top findings yourself before telling the user; discard false ones with a reason.

Replace `<…>`.

## Reviewer A — documents consistency (PRD, policies, ADRs)

```
You are an independent reviewer. READ-ONLY: do not edit/create/delete files, no state-changing git, no network/AI calls, do not read .env files<, or course-materials/>.

Checkout: <absolute path> (branch <branch>). Files: <PRD path>, <policy/rule files>, <ADR folder>. Design guidance: <path or "not on this branch, read from <path>">.

Goal: find defects that would make an implementing agent build the wrong thing or be unsure what to do. Check:
1. Every PRD acceptance criterion and flow: covered by the ADRs? any ADR contradicting it (texts, limits, order of sections, labels, timeouts, error texts, field order, enum lists, date rules, limits, language)?
2. Cross-ADR consistency: API paths, event names/payload fields/order, enum names and values, env var names/defaults, timeouts, size limits, class/package names, file paths, library versions, test tools, ownership.
3. Rule IDs cited exist in the rule files and are used with the right meaning; counting rules (days, periods) match formulas.
4. Internal logic: precedence between rules, ordering of guardrails/routers, message composition vs criteria, revision/retry semantics, idempotency.
5. Ambiguities an agent would have to guess (undefined fields/behavior, naming drift between languages, unclear ownership).
6. Technically implausible claims → "needs verification", not asserted.

Output: numbered findings, most severe first. Each: severity (blocker/major/minor), file + section/line, exact quotes from both sides, why it matters, concrete fix. Then a short list of what you checked and found consistent. Only real findings.
```

## Reviewer B — plan executability vs agent configuration

```
You are an independent reviewer. READ-ONLY: no edits, no state-changing git, no installs, no network/AI calls, no .env reads.

Checkout: <absolute path> (branch <branch>). Primary: <plan path>. Must be executable against: <PRD>, <ADRs>, <policies>, and repo/agent config: AGENTS.md, CLAUDE.md, .github/**/instructions, .codex/**, .claude/settings.json (allow/ask/deny + hooks), .claude/commands/**, .mcp.json, .gitignore, .env.example, app/README.md, README.md, docs/README.md, assets/**/README.md.

Find anything that prevents execution as written or leaves an agent unsure:
1. Contradictions with AGENTS.md/CLAUDE.md (TDD order, test strategy, manual QA, verification/commit rules, worktree and sub-agent rules, mock-from-capture rule, language).
2. Commands the plan expects that are denied/not allowed; hooks that fire; files planned for commit that .gitignore ignores, or vice versa.
3. Internal plan consistency: undefined task IDs, cycles, prerequisites vs critical path, overlapping editable files in parallel tasks, owner contradictions, links that don't say what the plan claims, stale statements, budget math, fallback order.
4. Plan vs PRD/ADR mismatches: scope, versions, commands/profiles/tags (are they defined?), paths, defaults, contracts, test tags.
5. Instructions written for another stack that would mislead an agent here.
6. Feasibility of external worker setup (permissions format, auto-approve, credentials) — "needs verification" when unproven.
7. Mock policy: any stub not derived from a recorded real call; any E2E/manual test that is not against the real app; any test that pre-assumes what a model will see.

Output: numbered findings, most severe first, each with severity, file + section/line, quotes from both sides, why it matters, concrete fix. Then what you checked and found consistent.
```

## Re-review brief (after fixes)

```
Same checkout, HEAD <sha>. The last <n> commits (`git show <shas>`) revised <files> after a review. Read the CURRENT versions in full and find remaining or newly introduced problems: contradictions, executability (cycles, ownerless files, overlapping edits, denied commands, red-test rule vs lane checks, build-dir serialization), ambiguities, conflicts with asset README rules and AGENTS.md. Do not re-report clearly fixed items; if a fix is wrong, say why. Same output format.
```
