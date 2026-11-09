#!/usr/bin/env bash
# Cross-platform parity gate — contracts/runner.yaml#comparison.
#
# Builds both runners, runs the SAME journey glob through each, and diffs the two canonical
# NDJSON streams. Any divergence fails the gate, including a difference in record count.
#
#   reflex/tools/parity-gate.sh [--spec <glob>] [--keep]
#
# This script compiles and runs code. It is what CI executes and what Amit runs by hand —
# the agent never runs it.
set -euo pipefail

SPEC="*.yaml"
KEEP=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --spec) SPEC="$2"; shift 2 ;;
    --keep) KEEP=1; shift ;;
    -h|--help) sed -n '2,12p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# The tree lives under reflex/ until the cutover promotes it to the repo root. Find whichever
# layout is present so this script keeps working after the promotion.
ROOT=""
probe="$(dirname "$SCRIPT_DIR")"
while [[ "$probe" != "/" && -n "$probe" ]]; do
  if [[ -d "$probe/reflex/specs/behavior" ]]; then ROOT="$probe/reflex"; break; fi
  if [[ -d "$probe/specs/behavior" ]]; then ROOT="$probe"; break; fi
  probe="$(dirname "$probe")"
done
if [[ -z "$ROOT" ]]; then
  echo "parity-gate: cannot find the reflex tree (expected specs/behavior under reflex/ or at the root)" >&2
  exit 2
fi

SPECS="$ROOT/specs"
IOS="$ROOT/ios"
ANDROID="$ROOT/android"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/reflex-parity.XXXXXX")"
cleanup() { [[ "$KEEP" -eq 1 ]] || rm -rf "$WORK"; }
trap cleanup EXIT

echo "parity-gate: specs=$SPECS"
echo "parity-gate: spec glob='$SPEC'"

echo "parity-gate: building the iOS runner"
(cd "$IOS" && swift build -c release)

echo "parity-gate: building the Android runner"
(cd "$ANDROID" && ./gradlew --no-daemon :tools:reflex-runner:installDist)

IOS_RUNNER="$IOS/.build/release/reflex-runner"
ANDROID_RUNNER="$ANDROID/tools/reflex-runner/build/install/reflex-runner/bin/reflex-runner"
[[ -x "$IOS_RUNNER" ]] || { echo "parity-gate: missing $IOS_RUNNER" >&2; exit 2; }
[[ -x "$ANDROID_RUNNER" ]] || { echo "parity-gate: missing $ANDROID_RUNNER" >&2; exit 2; }

echo "parity-gate: running the iOS runner"
ios_status=0
REFLEX_SPECS_DIR="$SPECS" "$IOS_RUNNER" --platform ios --spec "$SPEC" --out "$WORK/ios.ndjson" || ios_status=$?
if [[ "$ios_status" -ne 0 ]]; then
  echo "parity-gate: the iOS runner exited $ios_status (1 = an assertion failed, 2/3 = usage or fixture exhaustion)" >&2
  exit "$ios_status"
fi

echo "parity-gate: running the Android runner"
android_status=0
REFLEX_SPECS_DIR="$SPECS" "$ANDROID_RUNNER" --platform android --spec "$SPEC" --out "$WORK/android.ndjson" || android_status=$?
if [[ "$android_status" -ne 0 ]]; then
  echo "parity-gate: the Android runner exited $android_status (1 = an assertion failed, 2/3 = usage or fixture exhaustion)" >&2
  exit "$android_status"
fi

echo "parity-gate: diffing the streams"
status=0
node "$SCRIPT_DIR/diff-parity.js" "$WORK/ios.ndjson" "$WORK/android.ndjson" || status=$?

if [[ "$KEEP" -eq 1 ]]; then
  echo "parity-gate: streams kept in $WORK"
fi
exit "$status"
