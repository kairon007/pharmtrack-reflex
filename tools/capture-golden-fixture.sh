#!/usr/bin/env bash
# Capture the real medical_data.json from a running PharmTrack v0.0.19 install.
#
# READ-ONLY. Pulls a file off the device; never installs, launches, writes or clears.
# VIRTUAL DEVICES ONLY. Refuses to address a physical iPhone or Pixel even if one is
# plugged in — the Android path accepts emulator-* serials exclusively, and the iOS
# path uses simctl, which cannot see physical hardware.
#
# Usage:
#   ./reflex/tools/capture-golden-fixture.sh ios
#   ./reflex/tools/capture-golden-fixture.sh android [emulator-5554]
#   ./reflex/tools/capture-golden-fixture.sh both
#
# Output: specs/fixtures/golden_ios.json / golden_android.json
# (under reflex/ until the cutover promotes the tree to the repo root)
# Existing captures are never overwritten without --force.

set -euo pipefail

BUNDLE_ID="com.tunnxo.medtrack"
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
  echo "cannot find the reflex tree (expected specs/behavior under reflex/ or at the root)" >&2
  exit 2
fi

OUT_DIR="$ROOT/specs/fixtures"
FORCE=0

args=()
for a in "$@"; do
  if [[ "$a" == "--force" ]]; then FORCE=1; else args+=("$a"); fi
done
set -- "${args[@]+"${args[@]}"}"

TARGET="${1:-}"
if [[ -z "$TARGET" ]]; then
  echo "usage: $0 {ios|android|both} [emulator-serial] [--force]" >&2
  exit 2
fi

write_out() {
  local dest="$1" content="$2"
  if [[ -e "$dest" && $FORCE -eq 0 ]]; then
    echo "refusing to overwrite $dest (pass --force)" >&2
    return 1
  fi
  if ! printf '%s' "$content" | python3 -c 'import json,sys; json.load(sys.stdin)' 2>/dev/null; then
    echo "captured content is not valid JSON — not writing $dest" >&2
    echo "first 200 bytes:" >&2
    printf '%s' "${content:0:200}" >&2
    return 1
  fi
  printf '%s' "$content" > "$dest"
  echo "wrote $dest ($(wc -c < "$dest" | tr -d ' ') bytes)"
}

capture_ios() {
  command -v xcrun >/dev/null || { echo "xcrun not found" >&2; return 1; }

  local udid
  udid="$(xcrun simctl list devices booted -j \
    | python3 -c 'import json,sys
d=json.load(sys.stdin)["devices"]
 sims=[x for runtimelist in d.values() for x in runtimelist if x.get("state")=="Booted"]
if len(sims)!=1:
    sys.stderr.write("expected exactly one booted simulator, found %d\n"%len(sims)); sys.exit(1)
print(sims[0]["udid"])')" || return 1

  echo "iOS simulator: $udid"
  local container
  container="$(xcrun simctl get_app_container "$udid" "$BUNDLE_ID" data 2>/dev/null)" || {
    echo "$BUNDLE_ID is not installed on that simulator. Install the v0.0.19 build first." >&2
    return 1
  }

  local src="$container/Documents/medical_data.json"
  [[ -f "$src" ]] || { echo "no medical_data.json at $src — launch the app once to create it" >&2; return 1; }
  write_out "$OUT_DIR/golden_ios.json" "$(cat "$src")"
}

capture_android() {
  command -v adb >/dev/null || { echo "adb not found" >&2; return 1; }

  local serial="${1:-}"
  if [[ -z "$serial" ]]; then
    local emulators
    emulators="$(adb devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}')"
    local count
    count="$(printf '%s' "$emulators" | grep -c . || true)"
    if [[ "$count" -ne 1 ]]; then
      echo "expected exactly one running emulator, found $count." >&2
      echo "pass the serial explicitly: $0 android emulator-5554" >&2
      echo "physical devices are never used by this script." >&2
      return 1
    fi
    serial="$emulators"
  fi

  # Hard guard: no physical device serial can reach adb below.
  if [[ ! "$serial" =~ ^emulator-[0-9]+$ ]]; then
    echo "refusing '$serial': only emulator-* serials are permitted." >&2
    return 1
  fi
  if ! adb devices | awk '$2=="device"{print $1}' | grep -qx "$serial"; then
    echo "$serial is not a running emulator" >&2
    return 1
  fi

  echo "Android emulator: $serial"
  local content
  content="$(adb -s "$serial" shell run-as "$BUNDLE_ID" cat files/medical_data.json 2>/dev/null | tr -d '\r')" || {
    echo "could not read files/medical_data.json via run-as." >&2
    echo "run-as needs a debuggable build; a Play/release build will refuse." >&2
    return 1
  }
  [[ -n "$content" ]] || { echo "file is empty or absent — launch the app once to create it" >&2; return 1; }
  write_out "$OUT_DIR/golden_android.json" "$content"
}

case "$TARGET" in
  ios)     capture_ios ;;
  android) capture_android "${2:-}" ;;
  both)    capture_ios; capture_android "${2:-}" ;;
  *)       echo "unknown target '$TARGET' (expected ios|android|both)" >&2; exit 2 ;;
esac

cat <<'EOF'

Next: reconcile the capture against the statically-derived schema.
  node reflex/tools/validate-fixtures.js reflex/specs

Check specifically, since these are the things static derivation can get wrong:
  - are nil optionals ABSENT, or present as null?
  - do dates carry fractional seconds or a +00:00 offset?
  - is the UUID casing upper or lower?
  - are all six top-level keys always written?
EOF