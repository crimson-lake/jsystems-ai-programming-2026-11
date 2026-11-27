#!/usr/bin/env node
// Validates relative Markdown links and #anchors (GitHub slug rules) in all .md files under the given folders.
// Usage: node check-links.cjs docs [more folders or files...]
// Exit code 1 when a link target or anchor is missing. External (http/https/mailto/javascript) links are skipped.
const fs = require("fs");
const path = require("path");

const slug = (h) => h.trim().toLowerCase().replace(/`/g, "").replace(/[^\p{L}\p{N} _-]/gu, "").replace(/ /g, "-");
const anchorCache = new Map();
function anchors(file) {
  if (!anchorCache.has(file)) {
    const set = new Set();
    for (const line of fs.readFileSync(file, "utf8").split("\n")) {
      const m = line.match(/^#{1,6} (.*)$/);
      if (m) set.add(slug(m[1]));
    }
    anchorCache.set(file, set);
  }
  return anchorCache.get(file);
}

function collect(target, out) {
  const st = fs.statSync(target);
  if (st.isDirectory()) {
    for (const e of fs.readdirSync(target)) collect(path.join(target, e), out);
  } else if (target.endsWith(".md")) {
    out.push(target);
  }
  return out;
}

const targets = process.argv.slice(2);
if (targets.length === 0) {
  console.error("usage: node check-links.cjs <folder-or-file> [...]");
  process.exit(2);
}
const files = targets.flatMap((t) => collect(t, []));
let total = 0;
let bad = 0;
for (const file of files) {
  const text = fs.readFileSync(file, "utf8");
  for (const m of text.matchAll(/\]\(([^)\s]+)\)/g)) {
    const link = m[1];
    if (/^(https?:|mailto:|javascript:)/.test(link)) continue;
    total++;
    const [p, a] = link.split("#");
    const dest = p ? path.normalize(path.join(path.dirname(file), p)) : file;
    if (!fs.existsSync(dest)) {
      console.log(`${file}: MISSING ${link}`);
      bad++;
      continue;
    }
    if (a && dest.endsWith(".md") && !anchors(dest).has(a)) {
      console.log(`${file}: BAD ANCHOR ${link}`);
      bad++;
    }
  }
}
console.log(`${files.length} files, ${total} links, ${bad} broken`);
process.exit(bad ? 1 : 0);
