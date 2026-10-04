#!/usr/bin/env bash
# Builds the signed release APK and installs it on a watch together with its baseline
# profile, so the watch compiles it the way a store install would — never a slow debug build.
#
# Usage: scripts/install-release.sh [adb-serial]
# Without a serial, the first connected Wear OS device is used (emulator included).
set -euo pipefail
cd "$(dirname "$0")/.."

: "${JAVA_HOME:=/Applications/Android Studio.app/Contents/jbr/Contents/Home}"
export JAVA_HOME

# Use the SDK's adb (the one Android Studio runs), not whatever is first on PATH: a server
# started from another adb can lack macOS Local Network permission and lose the watch.
sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_dir/platform-tools/adb"

if [ ! -f keystore.properties ]; then
    echo "keystore.properties is missing, so the release build can't be signed." >&2
    exit 1
fi

is_watch() {
    "$adb" -s "$1" shell getprop ro.build.characteristics </dev/null 2>/dev/null | grep -q watch
}

serial="${1:-}"
if [ -z "$serial" ]; then
    for candidate in $("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
        if is_watch "$candidate"; then serial="$candidate"; break; fi
    done
fi
if [ -z "$serial" ]; then
    echo "No Wear OS device connected. Connect the watch (adb connect <ip>:<port>) or start the emulator." >&2
    exit 1
fi
if ! is_watch "$serial"; then
    echo "$serial is not a Wear OS device; not installing." >&2
    exit 1
fi

./gradlew :app:assembleRelease

out=app/build/outputs/apk/release
api="$("$adb" -s "$serial" shell getprop ro.build.version.sdk </dev/null | tr -d '\r')"
# AGP writes one profile for API 31+ (0/) and one for API 28–30 (1/).
if [ "$api" -ge 31 ]; then profile_dir=0; else profile_dir=1; fi

"$adb" -s "$serial" install-multiple -r "$out/app-release.apk" "$out/baselineProfiles/$profile_dir/app-release.dm"
echo "Installed on $serial:"
"$adb" -s "$serial" shell dumpsys package dexopt </dev/null | grep -A2 '\[com.sharkula.gusle\]' | grep -m1 status || true
