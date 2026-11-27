#!/usr/bin/env node
// Mirrors an implementation plan's dependency matrix into GitHub issues.
// Rows are matrix lines starting with "| **T<nn>" or "| **Q<n>" under "### Phase <n>" headings.
// The plan's Status column stays authoritative; issues are a mirror.
//
// Usage (run from the repository root):
//   node plan-to-issues.cjs --plan docs/plans/x-implementation-plan.md --repo owner/name --ref <branch-for-links>        # dry run
//   node plan-to-issues.cjs --plan ... --repo ... --ref ... --create   # create labels + issues, write "#n" into the Issue column
//
// Idempotent: an existing issue whose title starts with "<ID> " is reused. Requires `gh` with issues enabled
// (enabling needs repo admin rights; ask the human). Lane labels follow the T1x/T2x = backend, T3x = frontend convention;
// adjust LANES below for other plans.
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const args = process.argv.slice(2);
const opt = (name) => { const i = args.indexOf(name); return i >= 0 ? args[i + 1] : undefined; };
const planPath = opt("--plan");
const repo = opt("--repo");
const ref = opt("--ref") || "main";
const create = args.includes("--create");
if (!planPath || !repo) {
  console.error("usage: --plan <plan.md> --repo <owner/name> [--ref <branch>] [--create]");
  process.exit(2);
}

const LANES = [
  [/^T1[5-6]$|^T2\d$/, "lane:backend"],
  [/^T17$|^T3\d$/, "lane:frontend"],
];
const LABEL_COLORS = { "poc-plan": "5319E7", gate: "E99695", "lane:backend": "0E8A16", "lane:frontend": "1D76DB" };

const blobBase = `https://github.com/${repo}/blob/${ref}/`;
const planDir = path.dirname(planPath);

function splitRow(row) {
  const cells = [];
  let cur = "";
  for (let i = 1; i < row.length - 1; i++) {
    if (row[i] === "|" && row[i - 1] !== "\\") { cells.push(cur.trim()); cur = ""; } else cur += row[i];
  }
  cells.push(cur.trim());
  return cells;
}

// Rewrite relative links to absolute GitHub blob links and <br> to newlines.
function toMarkdown(text) {
  return text.replace(/<br>/g, "\n").replace(/\]\((?!https?:|#)([^)\s]+)\)/g, (_, link) => {
    const [p, a] = link.split("#");
    const resolved = path.normalize(path.join(planDir, p)).split(path.sep).join("/");
    return `](${blobBase}${resolved}${a ? "#" + a : ""})`;
  });
}

const lines = fs.readFileSync(planPath, "utf8").split("\n");
let phase = null;
const issues = [];
for (const line of lines) {
  const ph = line.match(/^### Phase (\d)/);
  if (ph) phase = ph[1];
  const m = line.match(/^\| \*\*((?:T\d+|Q\d)[^*]*)\*\*/);
  if (!m || phase === null) continue;
  const cells = splitRow(line);
  const id = m[1].split(" ")[0];
  const alias = (m[1].match(/=\s*(Q\d)/) || [])[1];
  const name = (alias ? `(${alias}) ` : "") + cells[0].replace(/\*\*[^*]+\*\*\s*/, "");
  const labels = ["poc-plan", `phase-${phase}`];
  if (id.startsWith("Q") || alias) labels.push("gate");
  for (const [re, label] of LANES) if (re.test(id)) labels.push(label);
  const body = [
    `**Plan row ${id}** in [the implementation plan](${blobBase}${planPath}). The plan's \`Status\` column is authoritative; this issue mirrors it.`,
    "", "### Observable outcome", toMarkdown(cells[1]),
    "", "### PRD/ADR links", toMarkdown(cells[2]),
    "", "### Prerequisites", toMarkdown(cells[3]),
    "", "### Owner / model", toMarkdown(cells[4]),
    "", "### Editable files", toMarkdown(cells[5]),
    "", "### Verification and handoff", toMarkdown(cells[6]),
  ].join("\n");
  issues.push({ id, title: `${id} ${name}`.trim(), labels, body });
}

console.log(`${issues.length} rows`);
for (const i of issues) console.log(`  ${i.title}  [${i.labels.join(", ")}]`);
if (!create) { console.log("dry run; add --create to create issues"); process.exit(0); }

const gh = (a, input) => execFileSync("gh", a, { encoding: "utf8", input }).trim();
for (const label of new Set(issues.flatMap((i) => i.labels))) {
  const color = LABEL_COLORS[label] || "C5DEF5";
  gh(["label", "create", label, "-R", repo, "--color", color, "--force"]);
}
const existing = JSON.parse(gh(["issue", "list", "-R", repo, "--state", "all", "--limit", "500", "--json", "number,title"]));
const numbers = {};
for (const e of existing) { const id = e.title.split(" ")[0]; if (issues.some((i) => i.id === id)) numbers[id] = e.number; }
for (const i of issues) {
  if (numbers[i.id]) { console.log(`${i.id}: exists #${numbers[i.id]}`); continue; }
  const a = ["issue", "create", "-R", repo, "--title", i.title, "--body-file", "-"];
  for (const l of i.labels) a.push("--label", l);
  numbers[i.id] = Number(gh(a, i.body).split("/").pop());
  console.log(`${i.id}: created #${numbers[i.id]}`);
}
const updated = lines.map((line) => {
  const m = line.match(/^\| \*\*(T\d+|Q\d)/);
  return m && numbers[m[1]] ? line.replace(/\| [^|]* \|$/, `| #${numbers[m[1]]} |`) : line;
});
fs.writeFileSync(planPath, updated.join("\n"));
console.log(`Issue column updated for ${Object.keys(numbers).length} rows; commit the plan.`);
