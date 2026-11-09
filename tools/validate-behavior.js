#!/usr/bin/env node
// Static behavior-suite validation. No app build, no simulator, no install.
// Parses YAML with js-yaml if present, else falls back to python3 + PyYAML.
//
//   node reflex/tools/validate-behavior.js reflex/specs
//
// Checks (per behavior/SCHEMA.md and contracts/runner.yaml):
//   - every journey parses and declares schema reflex/behavior/1, id == filename stem
//   - every node in `nodes:` exists in contracts/nodes.yaml
//   - every send target is a declared action; every deliver target a declared message
//   - every state.<node>.<field> path resolves to a declared state/derived field
//   - every data./store.saved. path starts at a real MedicalData top-level key
//   - every fixture reference (given.fixture, $fixture:<name>) exists in fixtures/index.yaml
//   - every $seed_* alias is a reserved id in fixtures/determinism.yaml
//   - every divergence id is in the file's decisions list AND in decisions.yaml
//   - case ids unique (per file and across the suite, so runner --case is unambiguous)
//   - each assertion has exactly one known operator; each step exactly one verb (or bare expect)
const fs = require('fs');
const path = require('path');
const { spawnSync } = require('child_process');

const base = process.argv[2];
if (!base) {
  console.error('usage: validate-behavior.js <specs-dir>');
  process.exit(2);
}

let jsyaml = null;
try { jsyaml = require('js-yaml'); } catch (e) { /* fall back to python3 */ }

function parseYaml(text, label) {
  if (jsyaml) return jsyaml.load(text);
  const r = spawnSync('python3',
    ['-c', 'import yaml,json,sys; print(json.dumps(yaml.safe_load(sys.stdin)))'],
    { input: text, encoding: 'utf8' });
  if (r.status !== 0) throw new Error(`python3 could not parse ${label}: ${r.stderr.trim()}`);
  return JSON.parse(r.stdout);
}
function readYaml(rel, label) {
  return parseYaml(fs.readFileSync(path.join(base, rel), 'utf8'), label || rel);
}

const nodesDoc = readYaml('contracts/nodes.yaml');
const decisionsDoc = readYaml('behavior/decisions.yaml');
const indexDoc = readYaml('fixtures/index.yaml');
const determinismDoc = readYaml('fixtures/determinism.yaml');

const machines = nodesDoc.nodes || {};
const machineInfo = {};
for (const [name, m] of Object.entries(machines)) {
  machineInfo[name] = {
    fields: new Set([...Object.keys(m.state || {}), ...Object.keys(m.derived || {})]),
    actions: new Set(Object.keys(m.actions || {})),
    messages: new Set(Object.keys(m.messages || {})),
  };
}
const decisionIds = new Set((decisionsDoc.decisions || []).map((d) => d.id));
const fixtureNames = new Set(Object.keys(indexDoc.fixtures || {}));
const reservedIds = new Set(Object.keys((determinismDoc.ids || {}).reserved || {}));
const dataKeys = new Set(['appointments', 'medications', 'consultations', 'vitals', 'logs', 'notificationSettings']);
const settingsKeys = new Set(['allNotificationsEnabled', 'medicationsEnabled', 'appointmentsEnabled',
  'consultationsEnabled', 'lowStockEnabled', 'lowStockThreshold', 'medicationReminderTime', 'appointmentReminderTime']);

const VERBS = new Set(['send', 'deliver', 'flush', 'advance', 'inject', 'note']);
const OPERATORS = new Set(['equals', 'not_equals', 'count', 'contains', 'not_contains',
  'unordered_equals', 'is_null', 'not_null', 'one_of', 'matches', 'gt', 'gte', 'lt', 'lte', 'close_to']);
const SCALAR_PATHS = new Set(['store.writeCount', 'store.filePresent', 'notifications',
  'notifications.replaceCount', 'clock.now', 'ids.consumed']);

let errors = [];
function err(where, msg) { errors.push(`${where}: ${msg}`); }

// Recursively collect $fixture:<name> and $seed_* alias references from any value.
function scanAliases(value, where, seen) {
  if (typeof value === 'string') {
    let m;
    const fx = /\$fixture:([A-Za-z0-9_]+)/g;
    while ((m = fx.exec(value))) {
      if (!fixtureNames.has(m[1])) err(where, `unknown fixture alias $fixture:${m[1]}`);
    }
    const sd = /\$(seed_[A-Za-z0-9_]+)/g;
    while ((m = sd.exec(value))) {
      if (!reservedIds.has(m[1])) err(where, `unknown reserved alias $${m[1]}`);
    }
  } else if (Array.isArray(value)) {
    value.forEach((v) => scanAliases(v, where, seen));
  } else if (value && typeof value === 'object') {
    for (const v of Object.values(value)) scanAliases(v, where, seen);
  }
}

function firstSegment(s) { return s.split(/[.[]/)[0]; }

function checkPath(p, where) {
  if (typeof p !== 'string') { err(where, `path is not a string: ${JSON.stringify(p)}`); return; }
  scanAliases(p, where);
  if (p.startsWith('state.')) {
    const rest = p.slice('state.'.length);
    const node = firstSegment(rest);
    if (!machineInfo[node]) { err(where, `state path references unknown node "${node}"`); return; }
    const after = rest.slice(node.length + 1);
    if (after) {
      const field = firstSegment(after);
      if (!machineInfo[node].fields.has(field)) {
        err(where, `state.${node}.${field} is not a declared state/derived field`);
      }
    }
  } else if (p.startsWith('data.')) {
    const key = firstSegment(p.slice('data.'.length));
    if (!dataKeys.has(key)) err(where, `data.${key} is not a MedicalData top-level key`);
  } else if (p === 'store.saved' || p.startsWith('store.saved.')) {
    if (p.startsWith('store.saved.')) {
      const key = firstSegment(p.slice('store.saved.'.length));
      if (!dataKeys.has(key)) err(where, `store.saved.${key} is not a MedicalData top-level key`);
    }
  } else if (p.startsWith('kv.')) {
    // any key allowed
  } else if (SCALAR_PATHS.has(p)) {
    // ok
  } else {
    err(where, `unknown path namespace "${p}"`);
  }
}

function checkGiven(given, where) {
  if (!given || typeof given !== 'object') return;
  if (given.fixture !== undefined) {
    if (!fixtureNames.has(given.fixture)) err(where, `given.fixture "${given.fixture}" not in index.yaml`);
    else {
      const file = (indexDoc.fixtures[given.fixture] || {}).file;
      if (file && !fs.existsSync(path.join(base, 'fixtures', file))) {
        err(where, `fixture file missing: fixtures/${file}`);
      }
    }
    if (given.store !== undefined) err(where, 'given.fixture and given.store are mutually exclusive');
  }
  if (given.store !== undefined && !['absent', 'unreadable'].includes(given.store)) {
    err(where, `given.store must be absent|unreadable, got "${given.store}"`);
  }
  if (given.load !== undefined && typeof given.load !== 'boolean') {
    err(where, 'given.load must be a boolean');
  }
  if (given.settings && typeof given.settings === 'object') {
    for (const k of Object.keys(given.settings)) {
      if (!settingsKeys.has(k)) err(where, `given.settings.${k} is not a NotificationSettings field`);
    }
  }
  if (given.data && typeof given.data === 'object') {
    for (const k of Object.keys(given.data)) {
      if (!dataKeys.has(k)) err(where, `given.data.${k} is not a MedicalData top-level key`);
    }
  }
  scanAliases(given, where);
}

function checkExpect(list, where) {
  if (!Array.isArray(list)) { err(where, 'expect must be a list'); return; }
  list.forEach((a, i) => {
    const w = `${where} expect[${i}]`;
    if (!a || typeof a !== 'object') { err(w, 'assertion must be a map'); return; }
    if (typeof a.path !== 'string') { err(w, 'assertion missing string path'); return; }
    checkPath(a.path, w);
    const ops = Object.keys(a).filter((k) => k !== 'path');
    if (ops.length !== 1) { err(w, `expected exactly one operator, got [${ops.join(', ')}]`); return; }
    if (!OPERATORS.has(ops[0])) err(w, `unknown operator "${ops[0]}"`);
    scanAliases(a[ops[0]], w);
  });
}

function checkStep(step, where) {
  if (!step || typeof step !== 'object') { err(where, 'step must be a map'); return; }
  const verbs = Object.keys(step).filter((k) => VERBS.has(k));
  const unknown = Object.keys(step).filter((k) => !VERBS.has(k) && k !== 'expect' && k !== 'args');
  if (unknown.length) err(where, `unexpected step key(s): ${unknown.join(', ')}`);
  if (verbs.length > 1) err(where, `step has more than one verb: ${verbs.join(', ')}`);
  if (verbs.length === 0 && step.expect === undefined) err(where, 'step has neither a verb nor an expect');
  const verb = verbs[0];
  if (verb === 'send' || verb === 'deliver') {
    const target = step[verb];
    const dot = typeof target === 'string' ? target.indexOf('.') : -1;
    if (dot < 0) { err(where, `${verb} target "${target}" must be <node>.<name>`); }
    else {
      const node = target.slice(0, dot), name = target.slice(dot + 1);
      const info = machineInfo[node];
      if (!info) err(where, `${verb} references unknown node "${node}"`);
      else if (verb === 'send' && !info.actions.has(name)) err(where, `send target "${target}" is not a declared action`);
      else if (verb === 'deliver' && !info.messages.has(name)) err(where, `deliver target "${target}" is not a declared message`);
    }
    scanAliases(step.args, where);
  } else if (verb === 'flush') {
    if (!['scheduler', 'store', 'all'].includes(step.flush)) err(where, `flush must be scheduler|store|all, got "${step.flush}"`);
  } else if (verb === 'advance') {
    const s = step.advance && step.advance.seconds;
    if (typeof s !== 'number') err(where, 'advance requires { seconds: <number> }');
  } else if (verb === 'inject') {
    if (!step.inject || step.inject.service !== 'store') err(where, 'inject only supports { service: store, ... }');
  } else if (verb === 'note') {
    if (typeof step.note !== 'string') err(where, 'note must be a string');
  }
  if (step.expect !== undefined) checkExpect(step.expect, where);
}

const behaviorDir = path.join(base, 'behavior');
const files = fs.readdirSync(behaviorDir).filter((f) => f.endsWith('.yaml')).sort();
const journeyIds = new Set();
const globalCaseIds = new Map();
let journeyCount = 0, caseCount = 0;

for (const f of files) {
  const stem = f.replace(/\.yaml$/, '');
  let doc;
  try {
    doc = parseYaml(fs.readFileSync(path.join(behaviorDir, f), 'utf8'), f);
  } catch (e) {
    err(stem, `YAML parse failed: ${e.message}`);
    continue;
  }
  if (!doc || doc.schema !== 'reflex/behavior/1') {
    // decisions.yaml lives here but is a registry, not a journey.
    if (doc && doc.schema === 'reflex/decisions/1') continue;
    err(stem, `missing or wrong schema (expected reflex/behavior/1, got ${doc && doc.schema})`);
    continue;
  }
  journeyCount++;
  const where = stem;
  if (doc.id !== stem) err(where, `journey id "${doc.id}" must equal filename stem "${stem}"`);
  if (journeyIds.has(doc.id)) err(where, `duplicate journey id "${doc.id}"`);
  journeyIds.add(doc.id);

  const fileDecisions = new Set(doc.decisions || []);
  for (const d of fileDecisions) {
    if (!decisionIds.has(d)) err(where, `decisions list cites unknown decision "${d}"`);
  }
  if (!Array.isArray(doc.nodes) || doc.nodes.length === 0) err(where, 'nodes must be a non-empty list');
  else for (const n of doc.nodes) {
    if (!machineInfo[n]) err(where, `nodes lists unknown machine "${n}"`);
  }
  if (doc.defaults !== undefined) checkGiven(doc.defaults, `${where} defaults`);

  if (!Array.isArray(doc.cases)) { err(where, 'cases must be a list'); continue; }
  const localCaseIds = new Set();
  doc.cases.forEach((c, ci) => {
    const cw = `${where} case[${ci}]${c && c.id ? ` (${c.id})` : ''}`;
    if (!c || typeof c !== 'object') { err(cw, 'case must be a map'); return; }
    if (typeof c.id !== 'string') { err(cw, 'case missing string id'); return; }
    caseCount++;
    if (localCaseIds.has(c.id)) err(cw, `duplicate case id within journey: "${c.id}"`);
    localCaseIds.add(c.id);
    if (globalCaseIds.has(c.id)) err(cw, `case id "${c.id}" also used in ${globalCaseIds.get(c.id)} — runner --case would be ambiguous`);
    else globalCaseIds.set(c.id, stem);

    if (c.divergence !== undefined) {
      if (!fileDecisions.has(c.divergence)) err(cw, `divergence ${c.divergence} not in file decisions list [${[...fileDecisions].join(', ')}]`);
      if (!decisionIds.has(c.divergence)) err(cw, `divergence cites unknown decision "${c.divergence}"`);
    }
    checkGiven(c.given, cw);
    if (!Array.isArray(c.steps)) { err(cw, 'steps must be a list'); return; }
    c.steps.forEach((s, si) => checkStep(s, `${cw} step[${si}]`));
  });
}

// Report grouped by journey for readability.
if (errors.length === 0) {
  console.log(`OK  ${journeyCount} journeys, ${caseCount} cases — all targets, paths, fixtures, aliases and divergences resolve.`);
  process.exit(0);
}
const byJourney = {};
for (const e of errors) {
  const j = e.split(':')[0];
  (byJourney[j] = byJourney[j] || []).push(e);
}
for (const [j, list] of Object.entries(byJourney)) {
  console.log(`FAIL ${j}`);
  list.forEach((e) => console.log(`     ${e}`));
}
console.log(`\n${errors.length} problem(s) across ${journeyCount} journeys / ${caseCount} cases.`);
process.exit(1);