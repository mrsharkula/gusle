#!/usr/bin/env bash
# Makes a few test songs like the ones on the watch (Opus and M4A, tagged, some with an embedded
# cover) and copies them to the Wear OS emulator, into the folder Wear Files uses on the watch.
# Android indexes them as music on its own. Also adds one notification sound, which Gusle must
# not list.
#
# Usage: scripts/push-test-songs.sh [--remove] [emulator-serial]
# --remove deletes the test songs again. Without a serial, the first running Wear OS emulator
# is used. Never touches a real watch.
set -euo pipefail
cd "$(dirname "$0")/.."

fail() { echo "$*" >&2; exit 1; }

command -v ffmpeg >/dev/null || fail "ffmpeg is needed to make the songs (brew install ffmpeg)."
command -v python3 >/dev/null || fail "python3 is needed to embed covers in Opus files."

sdk_dir="$(sed -n 's/^sdk\.dir=//p' local.properties 2>/dev/null || true)"
sdk_dir="${sdk_dir:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
adb="$sdk_dir/platform-tools/adb"
[ -x "$adb" ] || fail "adb not found at $adb."

remove=false
if [ "${1:-}" = "--remove" ]; then remove=true; shift; fi

is_wear_emulator() {
    local characteristics
    characteristics="$("$adb" -s "$1" shell getprop ro.build.characteristics </dev/null 2>/dev/null)" || return 1
    [[ "$1" == emulator-* && "$characteristics" == *watch* ]]
}

serial="${1:-}"
if [ -n "$serial" ]; then
    is_wear_emulator "$serial" || fail "$serial is not a running Wear OS emulator; not touching it."
else
    for candidate in $("$adb" devices | awk 'NR > 1 && $2 == "device" { print $1 }'); do
        if is_wear_emulator "$candidate"; then serial="$candidate"; break; fi
    done
    [ -n "$serial" ] || fail "No Wear OS emulator is running. Start one first."
fi

songs_dir=/sdcard/Download/Received
sound="/sdcard/Notifications/Test ping.ogg"

# File, format, title, artist, length in seconds, cover (WIDTHxHEIGHT:color, or empty).
# Lengths only matter for the list; the audio is a quiet tone.
songs=(
    "River at Dawn.opus|River at Dawn|Test Ensemble|211|600x600:0x93291C"
    "Mountain Song.opus|Mountain Song|Test Ensemble|314|1280x720:0x1C4E93"
    "Long title.m4a|A Very Long Song Title That Runs Well Past the Edge of the Round Screen|Test Trio|249|600x600:0x2E7D32"
    "No Cover Here.opus|No Cover Here|Test Trio|125|"
    "Untagged song.m4a|||45|"
    "Zither Interlude.m4a|Zither Interlude|Test Ensemble|12|1280x720:0xC9A227"
    "Šumadija.opus|Šumadija|Test Ensemble|90|600x600:0x6A1B9A"
)

if $remove; then
    for song in "${songs[@]}"; do
        "$adb" -s "$serial" shell rm -f "\"$songs_dir/${song%%|*}\""
    done
    "$adb" -s "$serial" shell rm -f "\"$sound\""
    echo "Removed the test songs from $serial."
    exit 0
fi

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

# A cover as Opus files carry it: a FLAC picture block (front cover), base64-encoded in the
# METADATA_BLOCK_PICTURE tag. ffmpeg can't embed a picture in Ogg itself.
picture_tag() {
    python3 - "$@" <<'EOF'
import base64, struct, sys
path, width, height = sys.argv[1], int(sys.argv[2]), int(sys.argv[3])
data = open(path, "rb").read()
mime = b"image/jpeg"
block = struct.pack(">II", 3, len(mime)) + mime + struct.pack(">I", 0)
block += struct.pack(">IIIII", width, height, 24, 0, len(data)) + data
print(base64.b64encode(block).decode())
EOF
}

for song in "${songs[@]}"; do
    IFS='|' read -r file title artist seconds cover <<<"$song"
    out="$work/$file"
    tags=()
    [ -n "$title" ] && tags+=(-metadata "title=$title" -metadata "album=Gusle test songs")
    [ -n "$artist" ] && tags+=(-metadata "artist=$artist")
    tone=(-f lavfi -i "sine=frequency=$((220 + RANDOM % 440)):duration=$seconds,volume=0.1")

    jpeg=""
    if [ -n "$cover" ]; then
        size="${cover%%:*}" color="${cover#*:}"
        jpeg="$work/${file%.*}.jpg"
        ffmpeg -loglevel error -y -f lavfi \
            -i "gradients=s=$size:c0=$color:c1=0x101010:x0=0:y0=0:x1=${size%x*}:y1=${size#*x}" \
            -frames:v 1 -update 1 "$jpeg"
    fi

    case "$file" in
        *.opus)
            [ -n "$jpeg" ] && tags+=(-metadata "METADATA_BLOCK_PICTURE=$(picture_tag "$jpeg" "${size%x*}" "${size#*x}")")
            ffmpeg -loglevel error -y "${tone[@]}" -c:a libopus -b:a 24k ${tags[@]+"${tags[@]}"} "$out"
            ;;
        *.m4a)
            if [ -n "$jpeg" ]; then
                ffmpeg -loglevel error -y "${tone[@]}" -i "$jpeg" -map 0:a -map 1:v -c:a aac -b:a 48k \
                    -c:v copy -disposition:v attached_pic ${tags[@]+"${tags[@]}"} "$out"
            else
                ffmpeg -loglevel error -y "${tone[@]}" -c:a aac -b:a 48k ${tags[@]+"${tags[@]}"} "$out"
            fi
            ;;
    esac
done
ffmpeg -loglevel error -y -f lavfi -i "sine=frequency=880:duration=1" -c:a libvorbis "$work/ping.ogg"

# Android lists a pushed file right away, but only reads its tags (title, artist, length, whether
# it's music) when asked to scan it.
push() {
    "$adb" -s "$serial" push -q "$1" "$2"
    "$adb" -s "$serial" shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE \
        -d "\"file://$2\"" >/dev/null
}

"$adb" -s "$serial" shell mkdir -p "$songs_dir" "\"${sound%/*}\""
for song in "${songs[@]}"; do
    file="${song%%|*}"
    push "$work/$file" "$songs_dir/$file"
done
push "$work/ping.ogg" "$sound"

echo "Pushed ${#songs[@]} test songs to $serial:$songs_dir, and a notification sound."
