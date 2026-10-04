#!/usr/bin/env bash
# Builds the signed release APK and installs it on a Wear OS device together with its
# baseline profile, so the watch compiles it like a store install — never a slow debug build.
#
# Usage: scripts/install-release.sh [adb-serial]
# Without a serial, the first connected Wear OS device is used (emulator included).
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "$*" >&2; exit 1; }

command -v python3 >/dev/null || fail "python3 is needed to read the build's output metadata."

# Android Studio's bundled JDK, unless JAVA_HOME is already set. If Studio lives elsewhere,
# leave it unset so gradlew falls back to java on PATH.
studio_jdk="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
if [ -z "${JAVA_HOME:-}" ] && [ -d "$studio_jdk" ]; then
    export JAVA_HOME="$studio_jdk"
fi

# The SDK's adb (the one Android Studio runs), not whatever is first on PATH.
sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_dir/platform-tools/adb"
[ -x "$adb" ] || fail "adb not found at $adb."

# Never start the adb server from here: macOS grants Local Network access per launching
# app, so a server started from a terminal can't reach the watch over Wi-Fi, and Android
# Studio would then reuse it and lose the watch too.
nc -z 127.0.0.1 5037 2>/dev/null ||
    fail "No adb server is running. Open Android Studio (it starts adb with network access), then rerun."

device_state() { "$adb" -s "$1" get-state 2>&1 | tail -n 1 || true; }

is_watch() {
    local characteristics
    characteristics="$("$adb" -s "$1" shell getprop ro.build.characteristics </dev/null 2>/dev/null)" || return 1
    [[ "$characteristics" == *watch* ]]
}

serial="${1:-}"
if [ -n "$serial" ]; then
    state="$(device_state "$serial")"
    [ "$state" = "device" ] ||
        fail "$serial isn't connected ($state). Reconnect it; the watch's port changes when its Wi-Fi sleeps."
    is_watch "$serial" || fail "$serial is not a Wear OS device; not installing."
else
    for candidate in $("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
        if is_watch "$candidate"; then serial="$candidate"; break; fi
    done
    [ -n "$serial" ] ||
        fail "No Wear OS device connected. Connect the watch (adb connect <ip>:<port>) or start the emulator."
fi

# Gradle refuses to build an unsigned release (see app/build.gradle.kts).
./gradlew :app:assembleRelease

# APK name, app ID and baseline profile come from the metadata AGP writes with this build.
# (Kept in a function: macOS's bash 3.2 mis-parses quotes in a heredoc inside "< <(...)".)
read_metadata() {
    python3 - "$1" <<'EOF'
import json, sys
meta = json.load(open(sys.argv[1]))
# With minSdk 33, every device that can install the app falls in the open-ended
# API 31+ profile that AGP writes: the entry with the highest maxApi.
profiles = sorted(meta.get("baselineProfiles", []), key=lambda p: p["maxApi"])
profile = profiles[-1]["baselineProfiles"][0] if profiles else ""
print(meta["applicationId"], meta["elements"][0]["outputFile"], profile)
EOF
}
out=app/build/outputs/apk/release
read -r app_id apk profile < <(read_metadata "$out/output-metadata.json")
[ -n "$profile" ] ||
    fail "The build produced no baseline profile; not installing a build that would start slower."

# The build takes a while; make sure the watch didn't drop off in the meantime.
[ "$(device_state "$serial")" = "device" ] ||
    fail "$serial disconnected during the build. Reconnect it and rerun."

"$adb" -s "$serial" install-multiple -r "$out/$apk" "$out/$profile"

echo "Installed $app_id on $serial:"
"$adb" -s "$serial" shell dumpsys package dexopt </dev/null | grep -A2 "\[$app_id\]" | grep -m1 status || true
