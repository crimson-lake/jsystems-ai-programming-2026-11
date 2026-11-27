# Review checklist (defects found in past plans)

Walk this before any reviewer sees the documents. Each line is a defect class that actually occurred.

## Cross-document contracts
- [ ] Every wire payload (e.g. SSE event fields) is defined in **one** authoritative place; others link to it. (Three different definitions existed.)
- [ ] Enum types are identical everywhere (a field was `bool` in one ADR and an enum in another).
- [ ] Field names identical across backend/frontend/ADRs (`caseId` vs `id`, `additionalPhotoCount` vs `extraPhotoCount`).
- [ ] Error codes: the wire set is closed and every code has an emitter and a UI behavior; internal codes map to wire codes.
- [ ] Every typed model has one typed table (field → type → note), including list/enum element types.
- [ ] Event-order specs and regex oracles allow the error paths the tests assert (error before/after the main event).
- [ ] Endpoint check order is defined when several pre-checks can fire (e.g. 404 → replay → 409 → 400 → 422 limit).

## Business-rule logic
- [ ] Precedence between conflicting criteria is written in the PRD (safety > time window > input quality > low confidence) and implemented in that order everywhere (guardrails, fast paths).
- [ ] Every cited policy/rule ID exists and means what it is used for (a "return safety" rule did not exist).
- [ ] "Changed" / "previous" semantics defined for the first event (null previous).
- [ ] Fallback text when a model returns an empty required field.
- [ ] ID filters/validators don't hit innocent text (bracketed citation format avoids "LG C3").
- [ ] Deletion vs in-flight commit races (cancelled flag before re-put).
- [ ] Idempotent retry: a committed request ID replays instead of erroring; client state is restored on failure after partial updates (badge reverted).

## Time, money, limits
- [ ] One time-budget table; sums fit the deadline including retries; SDK retries disabled if the app controls budget; per-call timeouts; client-side watchdog.
- [ ] Same timeout value in every document (8 s vs 10 s drift).
- [ ] Budget covers workers, spikes, E2E per suite run, evals × repeats, manual QA, demo; recompute after first real run.
- [ ] Limits checked against what the product does when exhausted (photo limit while info still missing).

## Tests and evidence
- [ ] Unit vs integration discovery configured explicitly; non-zero counts asserted.
- [ ] Real-API tests tagged and excluded by default; their own profile; normal tests never load real secrets.
- [ ] No mock derived from documentation; stubs reference capture files; transport faults only; missing captures listed.
- [ ] Frontend/client protocol tests use recordings of the real server.
- [ ] Tests never pre-assume what a model will see/decide on real inputs (assert form/policy-driven outcomes only; allow precedence outcomes, e.g. "rejected or safety-escalated").
- [ ] Fixture rules from asset READMEs respected; any exception recorded as a dated PRD decision.
- [ ] Coverage gates (e.g. JaCoCo) scoped to the classes the criterion names, not whole packages.
- [ ] E2E covers every endpoint and every external call step, with proof (network + call log).
- [ ] Manual QA uses the real app via playwright-cli after every runtime task and every phase; screenshots committed with an index.

## Plan executability
- [ ] No dependency cycles (spike needing classes created later → provisional test-scope records).
- [ ] Red tests never committed; red reason stated precisely; red tests compile against frozen types only.
- [ ] Every needed file has an owner; frozen contracts excluded from worker globs; generated files handled (e.g. `application.properties` left by an initializer).
- [ ] Build output directories serialized (orchestrator's own builds count).
- [ ] Worker sandbox: deny-by-default, cwd-relative allow-list, hard policies, smoke test; workers can write every path they're assigned (assets, eval folders).
- [ ] Every command the orchestrator needs is allowed (no `curl`/`rm`/`chmod`/`sudo` if denied; use playwright-cli/`git rm`/`sh mvnw`/human).
- [ ] Probe/QA processes don't violate "one dev server at a time" while a lane worker runs.
- [ ] Second instances (bad key, other port) and base URLs are specified, with env vars overriding config files.
- [ ] Text-only tasks still verified before commit (prompts checked by tests in the next build slot).
- [ ] Gates, critical path, prerequisites and fallbacks agree with each other.

## Repo and agent instructions
- [ ] Base branch follows README policy; PR order and dependencies stated.
- [ ] Stack-specific instructions don't mislead (generic app READMEs, framework-specific instruction files with broad `applyTo`).
- [ ] `AGENTS.md` lists all required env vars and points to test assets and their rules.
- [ ] Ignored paths aren't planned as committed artifacts (and vice versa).
- [ ] Real brand/trademark use vs "fictional store" requirement decided explicitly.
- [ ] Accessibility facts measured (contrast ratios computed, not guessed).
