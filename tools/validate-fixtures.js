#!/usr/bin/env node
// Static fixture validation. No app build, no simulator, no dependencies.
// Implements only the JSON Schema subset used by medical_data.schema.json:
// type, required, properties, items, enum, pattern, $ref. Not a general validator.
//
//   node reflex/tools/validate-fixtures.js reflex/specs
//
// Golden captures (golden_ios.json / golden_android.json) are picked up automatically
// once present and are held to the same schema.
const fs = require('fs');
const path = require('path');

const base = process.argv[2];
if (!base) {
  console.error('usage: validate-fixtures.js <specs-dir>');
  process.exit(2);
}
const root = path.join(base, 'contracts/medical_data.schema.json');
const schema = JSON.parse(fs.readFileSync(root, 'utf8'));

function deref(s) {
  if (!s.$ref) return s;
  const parts = s.$ref.replace(/^#\//, '').split('/');
  let t = schema;
  for (const p of parts) t = t[p];
  return { ...t, ...Object.fromEntries(Object.entries(s).filter(([k]) => k !== '$ref')) };
}

function typeOk(v, t) {
  switch (t) {
    case 'object': return v !== null && typeof v === 'object' && !Array.isArray(v);
    case 'array': return Array.isArray(v);
    case 'string': return typeof v === 'string';
    case 'boolean': return typeof v === 'boolean';
    case 'number': return typeof v === 'number' && Number.isFinite(v);
    case 'integer': return typeof v === 'number' && Number.isInteger(v);
    case 'null': return v === null;
    default: throw new Error('unknown type ' + t);
  }
}

function validate(v, s, p, errs) {
  s = deref(s);
  if (s.type) {
    const types = Array.isArray(s.type) ? s.type : [s.type];
    if (!types.some((t) => typeOk(v, t))) {
      errs.push(`${p}: expected ${types.join('|')}, got ${v === null ? 'null' : typeof v}`);
      return;
    }
  }
  if (typeof v === 'string') {
    if (s.pattern && !new RegExp(s.pattern).test(v)) errs.push(`${p}: /${s.pattern}/ failed on "${v}"`);
    if (s.enum && !s.enum.includes(v)) errs.push(`${p}: "${v}" not in enum`);
  }
  if (Array.isArray(v) && s.items) v.forEach((x, i) => validate(x, s.items, `${p}[${i}]`, errs));
  if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
    for (const r of s.required || []) {
      if (!(r in v)) errs.push(`${p}: missing required "${r}"`);
    }
    for (const [k, sub] of Object.entries(s.properties || {})) {
      if (k in v) validate(v[k], sub, `${p}.${k}`, errs);
    }
  }
}

const dir = path.join(base, 'fixtures');
// Fixtures that are intentionally invalid, per fixtures/index.yaml.
const expectFail = {
  'malformed.json': 'truncated JSON',
  'null_on_required.json': 'null on a required String',
  'partial_settings.json': 'notificationSettings present with only 2 of 8 required fields',
};

let bad = 0;
for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
  const raw = fs.readFileSync(path.join(dir, f), 'utf8');
  let parsed;
  try {
    parsed = JSON.parse(raw);
  } catch (e) {
    if (expectFail[f]) console.log(`EXPECTED FAIL  ${f}  (${expectFail[f]}: parse error)`);
    else { console.log(`UNEXPECTED FAIL ${f}: ${e.message}`); bad++; }
    continue;
  }
  const errs = [];
  validate(parsed, schema, f, errs);
  if (errs.length === 0) {
    if (expectFail[f]) { console.log(`SHOULD HAVE FAILED ${f}`); bad++; }
    else console.log(`VALID          ${f}`);
  } else if (expectFail[f]) {
    console.log(`EXPECTED FAIL  ${f}  (${expectFail[f]})`);
    errs.slice(0, 2).forEach((e) => console.log(`                 ${e}`));
  } else {
    console.log(`INVALID        ${f}`);
    errs.forEach((e) => console.log(`                 ${e}`));
    bad++;
  }
}
console.log(bad === 0 ? '\nAll fixtures behaved as specified.' : `\n${bad} fixture(s) deviated.`);
process.exit(bad === 0 ? 0 : 1);