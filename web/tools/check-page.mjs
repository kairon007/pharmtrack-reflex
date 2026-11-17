#!/usr/bin/env node
/**
 * check-page.js — deterministic pre-ship check for a scaffolded landing page.
 *
 * Usage:
 *   node check-page.js <project-dir> [--skip <dir> ...]
 *
 * Exits non-zero when any of these fail, because each one ships a visible
 * defect:
 *   - a local src/href that does not resolve        -> broken image or link
 *   - a css url() that does not resolve             -> missing font or asset
 *   - an unreplaced {{PLACEHOLDER}} token           -> placeholder text in public copy
 *   - a leftover reference to another brand         -> wrong-brand copy shipped
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, relative, extname, dirname } from "node:path";

const argv = process.argv.slice(2);
const SKIP_SET = new Set();
for (let i = 0; i < argv.length; i++) {
  if (argv[i] === "--skip" && argv[i + 1]) SKIP_SET.add(argv[++i]);
}
const dir = argv.find((a) => !a.startsWith("--") && !SKIP_SET.has(a));
if (!dir) {
  console.error("usage: node check-page.js <project-dir> [--skip <dir> ...]");
  process.exit(2);
}
for (const d of SKIP_SET) {
  if (!existsSync(join(dir, d))) {
    console.error(`  WARN  --skip "${d}" does not exist under ${dir}`);
  }
}

let errors = 0;
let warnings = 0;
const fail = (m) => {
  console.error("  FAIL  " + m);
  errors++;
};
const warn = (m) => {
  console.error("  WARN  " + m);
  warnings++;
};

/* ------------------------------------------------------- collect files -- */
function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    if (entry === "node_modules" || entry.startsWith(".")) continue;
    if (SKIP_SET.has(entry)) continue;
    const p = join(dir, entry);
    if (statSync(p).isDirectory()) walk(p, out);
    else out.push(p);
  }
  return out;
}
const files = walk(dir);
const htmlFiles = files.filter((f) => extname(f) === ".html");
const cssFiles = files.filter((f) => extname(f) === ".css");

console.log(`checking ${dir}  (${htmlFiles.length} html, ${cssFiles.length} css)`);

/* ----------------------------------------------- html local references -- */
const isRemote = (u) => /^(https?:|mailto:|tel:|data:|#|\/\/)/i.test(u);
let refCount = 0;
for (const file of htmlFiles) {
  const html = readFileSync(file, "utf8");
  for (const m of html.matchAll(/(?:src|href)\s*=\s*"([^"]*)"/g)) {
    const url = m[1].trim();
    if (!url || isRemote(url)) continue;
    if (url.includes("{{")) continue; // still an unfilled placeholder, reported below
    refCount++;
    // Resolve against the file's own directory, the way a browser does —
    // a page in a subfolder (terms/index.html) uses ../assets/... paths.
    const target = join(dirname(file), url.split("#")[0].split("?")[0]);
    if (!existsSync(target)) {
      fail(`${relative(dir, file)}: missing "${url}"`);
    }
  }
}
console.log(`  html: ${refCount} local references checked`);

/* ------------------------------------------------------- srcset candidates -- */
// A srcset overrides src, so a srcset naming files that do not exist shows a
// broken image even though every src resolves. This is the failure mode when
// a real screenshot replaces src but the responsive candidates are left behind.
let srcsetCount = 0;
for (const file of htmlFiles) {
  const html = readFileSync(file, "utf8");
  for (const m of html.matchAll(/srcset\s*=\s*"([^"]*)"/g)) {
    for (const candidate of m[1].split(",")) {
      const url = candidate.trim().split(/\s+/)[0];
      if (!url || isRemote(url) || url.includes("{{")) continue;
      srcsetCount++;
      const target = join(dirname(file), url.split("#")[0].split("?")[0]);
      if (!existsSync(target)) {
        fail(`${relative(dir, file)}: srcset candidate "${url}" does not exist`);
      }
    }
  }
}
console.log(`  html: ${srcsetCount} srcset candidates checked`);

/* --------------------------------------------------- css url() refs ----- */
let urlCount = 0;
for (const file of cssFiles) {
  const css = readFileSync(file, "utf8");
  for (const m of css.matchAll(/url\(\s*["']?([^"')]+)["']?\s*\)/g)) {
    const url = m[1].trim();
    if (!url || isRemote(url)) continue;
    urlCount++;
    // A built stylesheet references its assets from the site root ("/assets/…");
    // a source stylesheet references them relative to itself ("../fonts/…").
    const target = url.startsWith("/")
      ? join(dir, url.slice(1))
      : join(dirname(file), url.split("#")[0].split("?")[0]);
    if (!existsSync(target)) {
      fail(`${relative(dir, file)}: missing url(${url})`);
    }
  }
}
console.log(`  css:  ${urlCount} url() references checked`);

/* -------------------------------------------------- unfilled tokens ----- */
const TOKENS = /\{\{[A-Z0-9_]+\}\}/g;
let tokenHits = 0;
for (const file of files) {
  if (![".html", ".css", ".js", ".json", ".md", ".svg"].includes(extname(file))) continue;
  const text = readFileSync(file, "utf8");
  const found = text.match(TOKENS);
  if (found) {
    tokenHits += found.length;
    const uniq = [...new Set(found)];
    fail(`${relative(dir, file)}: ${uniq.length} unfilled placeholder(s): ${uniq.slice(0, 6).join(" ")}`);
  }
}
if (tokenHits === 0) console.log("  copy: no unfilled {{PLACEHOLDER}} tokens");

/* ------------------------------------------------------ brand residue --- */
// Only user-visible copy matters here. The stylesheet and script headers are
// generic and may legitimately mention the design system's provenance.
const BRAND = /\b(timmly)\b/i;
let brandHits = 0;
for (const file of files) {
  if (extname(file) !== ".html") continue;
  const text = readFileSync(file, "utf8");
  if (BRAND.test(text)) {
    brandHits++;
    fail(`${relative(dir, file)}: still references the reference brand "Timmly"`);
  }
}
if (brandHits === 0) console.log("  copy: no leftover reference-brand mentions");

/* ------------------------------------------------- reference brand mark -- */
// The reference mark is a path drawing: it carries no brand name, so the
// text check above cannot see it. Match its unique geometry rather than a
// colour -- the accent hex is shared by every tile in the design system, so
// testing for it would flag any innocent inline SVG.
const MARK_SIGNATURE = /M101\.61 372A160 160 0 1 1 410\.39 372Z/;
let markHits = 0;
for (const file of files) {
  if (![".html", ".svg"].includes(extname(file))) continue;
  const text = readFileSync(file, "utf8");
  if (MARK_SIGNATURE.test(text)) {
    markHits++;
    fail(`${relative(dir, file)}: still uses the reference brand's logo`);
  }
}
if (markHits === 0) console.log("  brand: reference logo not present");

/* --------------------------------------------------- critical css rules -- */
// A missing rule is invisible to a link check but silently breaks the page:
// without .s-shot the phone mockups crop the screenshot to a corner.
const rules = new Map();
for (const file of cssFiles) rules.set(file, readFileSync(file, "utf8"));
const allCss = [...rules.values()].join("\n");
for (const rule of [".s-shot", ".s-device", ".s-bezel", ".s-screen"]) {
  // The built stylesheet is minified, so the brace may sit flush against the
  // selector: ".s-shot{…}" rather than ".s-shot {…}".
  const pattern = new RegExp(rule.replace(".", "\\.") + "\\s*\\{");
  if (!pattern.test(allCss)) {
    fail(`css: the "${rule}" rule is missing — the device mockups will not render`);
  }
}
for (const file of htmlFiles) {
  const html = readFileSync(file, "utf8");
  if (/<img[^>]*class="[^"]*s-shot/.test(html) && !/\.s-shot\s*\{/.test(allCss)) {
    fail(`${relative(dir, file)}: uses .s-shot but the rule is not defined`);
  }
}
console.log("  css:  critical device-mockup rules present");

/* -------------------------------------------------------------- done ---- */
console.log("");
if (errors === 0) {
  console.log(`PASS  ${warnings ? `(${warnings} warning(s))` : "no warnings"}`);
  process.exit(0);
}
console.error(`FAIL  ${errors} problem(s), ${warnings} warning(s)`);
process.exit(1);
