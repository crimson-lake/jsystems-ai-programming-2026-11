---
name: research-report
description: Use when the user asks to research a topic, fact-check or debunk a claim, or find newer evidence (studies, benchmarks, statistics) and wants the result as a report or artifact.
---

# Research report workflow

The deliverables are evidence-first: each number links to its source and has a confidence label. The lead model coordinates the work, checks it and writes the report. The subagents only gather evidence.

## 0. Clarify before starting (one AskUserQuestion call)

Ask only for details the request leaves open:
- **Destination folder** for the report files (always ask; suggest `course-materials/Research/` when it exists).
- **Second report language** (default: Polish; English is always included).
- **Search languages**: the languages the sources may be in (for example "Slavic languages" or "German + French").
- **Branch name**: suggest `docs/<topic-slug>-research`.

File names: `<topic-slug>-<YYYY-MM-DD>-en.md`, `<topic-slug>-<YYYY-MM-DD>-<lang2>.md` and `<topic-slug>-<YYYY-MM-DD>.html`.

If those files already exist, copy them to the scratchpad before overwriting. Then treat the old report as a list of claims to re-verify.

## 1. Branch

```bash
git fetch origin main
git switch -c <branch> origin/main   # uncommitted files travel with you
git branch --unset-upstream          # so a plain `git push` can never land on main
```

If `git status` shows unrelated modified files, ask before switching.

## 2. Delegate research: 5 parallel Haiku subagents

Split the question into **5 non-overlapping scopes**. For a claim of the form "language/tool X is better", typical scopes are:

1. The original claim and its primary source, plus replications and media fact-checks
2. Domain A (for example search, retrieval and RAG)
3. Domain B (for example reasoning)
4. Native-language sources (searched in each requested language)
5. Cost and efficiency, plus local or national benchmarks

Launch all 5 in **one message**, using `subagent_type: general-purpose`, `model: haiku` and `run_in_background: true`. Each brief must contain:

- Today's date and the overall question, in 2–3 sentences.
- **Known sources to verify:** list what you already know, with arXiv IDs and the exact numbers. Ask the agent to check 2–3 of them and then look for newer (last 12–18 months) or additional ones.
- **Numbered tasks** with example queries, in the source languages when relevant.
- **Multiple attempts:** "If a language or angle gives nothing, try at least 5 distinct queries (native wording, synonyms, local venues and institutions) before moving on, and record the queries you tried."
- **Tool budget:** about 25–45 calls. Tell the agent to load tools with `ToolSearch "select:WebSearch,WebFetch"` if they are deferred.
- **Rules:**
  - Prefer peer-reviewed papers, arXiv and official vendor pages.
  - Record each exact number together with the table or section it comes from.
  - Prefer `arxiv.org/html/<id>` over the PDF; WebFetch fails on PDFs over 10 MB.
  - Mark anything not opened as UNVERIFIED.
  - **Never invent a URL or a number.**
- **Output file:** `<scratchpad>/agentN-<scope>.md`. For each source: title, authors, venue + date, URL, key numbers, relevance, confidence (`VERIFIED-primary` / `secondary` / `UNVERIFIED`).
- **Final reply:** a 10-line summary plus the file path.

While the agents run, load the `artifact-design` skill. Do not repeat the agents' searches yourself.

## 3. Quality check and iterate (at least one verification round)

For each agent report:

1. **Grade it.** Did it reach primary sources, or did it rely on search snippets? Did it skip languages or tasks?
2. **List the weak claims.** These are the numbers the conclusion depends on that are marked secondary or UNVERIFIED, conflicting figures, and "new" sources that look important.
3. **Verify the weak claims.** Launch `web-fetch` agents (background, one per 2–3 papers) with **precise questions**: "quote the sentence giving X", "give Table N rows for A and B", "is Y in the paper? quote it or say not found". Ask for verbatim quotes. Tool-made page summaries are known to be wrong, so trust only verbatim text.
4. **Repeat for gaps.** Use a new round for any gap that would change the verdict: `SendMessage` to the same agent to continue it, or start a new one. Stop when the key claims are verified, or are clearly labelled as unverified.

Pull URLs from the notes with `grep -hoE 'https?://[^ )>\]]+' <scratchpad>/agent*.md | sort -u`, instead of reading whole files.

## 4. Write the report (lead model only)

English Markdown structure:

1. **H1 title**, then **Verdict** in bold, then 3–5 sentences of summary.
2. **Scope table:** scope | what the newest evidence says | strength.
3. **One section per scope.** Use tables of exact numbers with venue and year, end each section with a "Bottom line", and mark NEW sources.
4. **Practical recommendations**, numbered.
5. **Method and quality control:** the agents and tool calls, what the lead re-verified, corrections to earlier versions, quality issues found in the agents' work, and the main limitations.
6. **Sources:** a flat list of URLs.

Then write the second-language file as a faithful translation. Keep the same numbers and use the local number format (for example `0,87` in Polish). Avoid the `</script` sequence in the Markdown.

## 5. Build the HTML and publish the Artifact

```bash
python3 -I .agents/skills/research-report/scripts/build_html.py \
  --en <dir>/<slug>-<date>-en.md --l2 <dir>/<slug>-<date>-<lang2>.md --l2-code <lang2> \
  --title "<2–4 word name>" --eyebrow-en "Evidence review · <date>" --eyebrow-l2 "<translated eyebrow>" \
  --out <dir>/<slug>-<date>.html
```

The template (`assets/template.html`) renders both Markdown files with marked.js from cdnjs, wraps tables so they scroll, has a toggle that is remembered across visits and supports `#en` / `#<lang2>` deep links, and works in light and dark themes. Without the CDN it falls back to plain text.

- Take **one** look at the page in the browser pane (`navigate` to the `file://` path, then take a screenshot).
- Publish with `Artifact` (`file_path` = the HTML, `icon: "chart"`, and a one-sentence `description`).
- Tell the user that the Artifact is private until they share it.

## 6. Commit and push

- Stage named paths only. Never use `git add -A` or `git add .`.
- Commit message: `Docs: add <topic> evidence review`, ending with the attribution line from the system reminder.
- Push with `git push -u origin <branch>`.
- Offer to open a PR with `gh pr create --base main`. Don't open it unasked.

## Final message to the user

Include:
- the verdict and 4–6 key numbers
- the Artifact link and the file links
- what was verified and what remains unverified, with the gaps
- the branch and the commit

Keep it short. The report holds the detail.
