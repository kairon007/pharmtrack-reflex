#!/usr/bin/env bash
# Real-install migration verification for PharmTrack 1.0.
#
# Takes a medical_data.json captured from a LIVE v0.0.19 install and proves, with the real
# codecs on both platforms, that:
#   1. the file matches specs/contracts/medical_data.schema.json
#   2. each platform loads it and writes it back with NO field loss (semantic equality)
#   3. the two platforms agree with each other, byte for byte, on the canonical stream
#
#   reflex/tools/verify-migration.sh [--ios <file>] [--android <file>]
#
# Defaults to specs/fixtures/golden_ios.json and specs/fixtures/golden_android.json.
#
# CAPTURING THE FILES IS A HAND TASK on a virtual device:
#   reflex/tools/capture-golden-fixture.sh both
#
# This script compiles and runs code. It is what Amit runs by hand — the agent never runs it.
set -euo pipefail

GOLDEN_IOS=""
GOLDEN_ANDROID=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --ios) GOLDEN_IOS="$2"; shift 2 ;;
    --android) GOLDEN_ANDROID="$2"; shift 2 ;;
    -h|--help) sed -n '2,16p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "unknown argument: $1" >&2; exit 2 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

ROOT=""
probe="$(dirname "$SCRIPT_DIR")"
while [[ "$probe" != "/" && -n "$probe" ]]; do
  if [[ -d "$probe/reflex/specs/behavior" ]]; then ROOT="$probe/reflex"; break; fi
  if [[ -d "$probe/specs/behavior" ]]; then ROOT="$probe"; break; fi
  probe="$(dirname "$probe")"
done
if [[ -z "$ROOT" ]]; then
  echo "verify-migration: cannot find the reflex tree (expected specs/behavior under reflex/ or at the root)" >&2
  exit 2
fi

SPECS="$ROOT/specs"
IOS="$ROOT/ios"
ANDROID="$ROOT/android"
WORK="$(mktemp -d "${TMPDIR:-/tmp}/reflex-migration.XXXXXX")"
trap 'rm -rf "$WORK"' EXIT

# The journey runs against a throwaway copy of the specs tree, so the frozen suite is never
# mutated by a migration check.
cp -R "$SPECS" "$WORK/specs"
cp "$SCRIPT_DIR/templates/migration_install.yaml" "$WORK/specs/behavior/migration_install.yaml"

copy_capture() {
  local source="$1" name="$2" flag="$3" destination="$WORK/specs/fixtures/$name"
  if [[ -n "$source" ]]; then
    [[ -f "$source" ]] || { echo "verify-migration: no such capture: $source" >&2; exit 2; }
    cp "$source" "$destination"
  else
    [[ -f "$destination" ]] || {
      echo "verify-migration: $name is missing." >&2
      echo "  capture it on a virtual device: $SCRIPT_DIR/capture-golden-fixture.sh" >&2
      echo "  or pass a file: $flag <path>" >&2
      exit 2
    }
  fi
  echo "verify-migration: $name <- $destination ($(wc -c < "$destination" | tr -d ' ') bytes)"
}

copy_capture "$GOLDEN_IOS" golden_ios.json --ios
copy_capture "$GOLDEN_ANDROID" golden_android.json --android

echo "verify-migration: inventory of the captures"
node -e '
const fs = require("fs");
for (const name of ["golden_ios.json", "golden_android.json"]) {
  const data = JSON.parse(fs.readFileSync(process.argv[1] + "/fixtures/" + name, "utf8"));
  const counts = ["medications", "appointments", "consultations", "vitals", "logs"]
    .map((key) => `${key}=${Array.isArray(data[key]) ? data[key].length : "absent"}`)
    .join(" ");
  console.log(`  ${name}: ${counts} settings=${data.notificationSettings ? "present" : "absent"}`);
}
' "$WORK/specs"

echo "verify-migration: validating the captures against the schema"
node "$SCRIPT_DIR/validate-fixtures.js" "$WORK/specs"

echo "verify-migration: validating the migration journey"
node "$SCRIPT_DIR/validate-behavior.js" "$WORK/specs"

echo "verify-migration: building the iOS runner"
(cd "$IOS" && swift build -c release)
echo "verify-migration: building the Android runner"
(cd "$ANDROID" && ./gradlew --no-daemon :tools:reflex-runner:installDist)

IOS_RUNNER="$IOS/.build/release/reflex-runner"
ANDROID_RUNNER="$ANDROID/tools/reflex-runner/build/install/reflex-runner/bin/reflex-runner"

echo "verify-migration: running the iOS runner over the captured files"
ios_status=0
REFLEX_SPECS_DIR="$WORK/specs" "$IOS_RUNNER" --platform ios --spec "migration_install.yaml" --out "$WORK/ios.ndjson" || ios_status=$?
if [[ "$ios_status" -ne 0 ]]; then
  echo "verify-migration: the iOS runner exited $ios_status — the capture did not survive the iOS codec" >&2
  exit "$ios_status"
fi

echo "verify-migration: running the Android runner over the captured files"
android_status=0
REFLEX_SPECS_DIR="$WORK/specs" "$ANDROID_RUNNER" --platform android --spec "migration_install.yaml" --out "$WORK/android.ndjson" || android_status=$?
if [[ "$android_status" -ne 0 ]]; then
  echo "verify-migration: the Android runner exited $android_status — the capture did not survive the Android codec" >&2
  exit "$android_status"
fi

echo "verify-migration: diffing the two streams"
status=0
node "$SCRIPT_DIR/diff-parity.js" "$WORK/ios.ndjson" "$WORK/android.ndjson" || status=$?

if [[ "$status" -eq 0 ]]; then
  echo "verify-migration: PASS — both real captures round-trip losslessly and both platforms agree"
fi
exit "$status"
