#!/usr/bin/env node
// Diff two canonical NDJSON state streams — one from the iOS runner, one from the Android
// runner. contracts/runner.yaml#comparison: ANY difference fails the gate, including a
// difference in the NUMBER of records.
//
//   node reflex/tools/diff-parity.js <ios.ndjson> <android.ndjson>
//
// runner.yaml#failure_report asks for the journey id, case id, step index and the FIRST
// differing path — never a whole state object, which is far too large to read.
const fs = require('fs');

const [iosPath, androidPath] = process.argv.slice(2);
if (!iosPath || !androidPath) {
  console.error('usage: diff-parity.js <ios.ndjson> <android.ndjson>');
  process.exit(2);
}

function readStream(path, label) {
  let text;
  try {
    text = fs.readFileSync(path, 'utf8');
  } catch (error) {
    console.error(`diff-parity: cannot read ${label} stream ${path}: ${error.message}`);
    process.exit(2);
  }
  const lines = text.split('\n');
  if (lines.length > 0 && lines[lines.length - 1] === '') lines.pop();
  return lines;
}

function kind(value) {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  return typeof value;
}

// The first difference in sorted-key order, so the report is deterministic.
function firstDifference(left, right, path) {
  if (left === right) return null;
  const leftKind = kind(left);
  const rightKind = kind(right);
  if (leftKind !== rightKind) {
    return { path, left, right, reason: `type ${leftKind} vs ${rightKind}` };
  }
  if (leftKind === 'array') {
    if (left.length !== right.length) {
      return { path: `${path}.length`, left: left.length, right: right.length, reason: 'length' };
    }
    for (let i = 0; i < left.length; i += 1) {
      const difference = firstDifference(left[i], right[i], `${path}[${i}]`);
      if (difference) return difference;
    }
    return null;
  }
  if (leftKind === 'object') {
    const keys = [...new Set([...Object.keys(left), ...Object.keys(right)])].sort();
    for (const key of keys) {
      const inLeft = Object.prototype.hasOwnProperty.call(left, key);
      const inRight = Object.prototype.hasOwnProperty.call(right, key);
      if (!inLeft) return { path: `${path}.${key}`, left: undefined, right: right[key], reason: 'missing on iOS' };
      if (!inRight) return { path: `${path}.${key}`, left: left[key], right: undefined, reason: 'missing on Android' };
      const difference = firstDifference(left[key], right[key], `${path}.${key}`);
      if (difference) return difference;
    }
    return null;
  }
  return { path, left, right, reason: 'value' };
}

function preview(value) {
  if (value === undefined) return '(absent)';
  const text = typeof value === 'string' ? value : JSON.stringify(value);
  if (text === undefined) return '(absent)';
  return text.length > 160 ? `${text.slice(0, 157)}...` : text;
}

const iosLines = readStream(iosPath, 'iOS');
const androidLines = readStream(androidPath, 'Android');

let journey = '(none)';
let caseId = '(none)';
let stepIndex = null;
let divergentRecords = 0;
let firstFailure = null;

const count = Math.min(iosLines.length, androidLines.length);
for (let i = 0; i < count; i += 1) {
  let left;
  let right;
  try {
    left = JSON.parse(iosLines[i]);
  } catch (error) {
    console.error(`diff-parity: iOS record ${i + 1} is not valid JSON: ${error.message}`);
    process.exit(2);
  }
  try {
    right = JSON.parse(androidLines[i]);
  } catch (error) {
    console.error(`diff-parity: Android record ${i + 1} is not valid JSON: ${error.message}`);
    process.exit(2);
  }

  if (left.journey) journey = left.journey;
  if (left.case) caseId = left.case;
  if (left.type === 'step' && typeof left.index === 'number') stepIndex = left.index;

  const difference = firstDifference(left, right, '');
  if (!difference) continue;

  divergentRecords += 1;
  if (!firstFailure) {
    firstFailure = {
      record: i + 1,
      type: left.type || right.type || '(unknown)',
      journey,
      case: caseId,
      step: stepIndex,
      path: difference.path.replace(/^\./, ''),
      left: difference.left,
      right: difference.right,
      reason: difference.reason,
    };
  }
}

if (iosLines.length !== androidLines.length) {
  const extra = iosLines.length > androidLines.length ? 'iOS' : 'Android';
  const missingAt = count + 1;
  if (!firstFailure) {
    firstFailure = {
      record: missingAt,
      type: '(record count)',
      journey,
      case: caseId,
      step: stepIndex,
      path: '(records)',
      left: `${iosLines.length} records`,
      right: `${androidLines.length} records`,
      reason: `${extra} emitted an extra record`,
    };
  }
}

if (!firstFailure) {
  console.log(`parity OK — ${iosLines.length} records identical on both platforms`);
  process.exit(0);
}

console.log('PARITY DIVERGENCE');
console.log(`  record:  #${firstFailure.record} (type=${firstFailure.type})`);
console.log(`  journey: ${firstFailure.journey}`);
console.log(`  case:    ${firstFailure.case}`);
console.log(`  step:    ${firstFailure.step === null ? '(n/a)' : firstFailure.step}`);
console.log(`  path:    ${firstFailure.path}`);
console.log(`  iOS:     ${preview(firstFailure.left)}`);
console.log(`  Android: ${preview(firstFailure.right)}`);
console.log(`  reason:  ${firstFailure.reason}`);
console.log(`  (${divergentRecords} record(s) differ in total; the first is reported above)`);
process.exit(1);
