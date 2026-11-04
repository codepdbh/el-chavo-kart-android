#!/usr/bin/env bash
# Renderer A/B test on a connected phone: writes extra_args.txt (developer overrides read by the launcher),
# starts the game through the launcher, waits and captures the screen.
#
#   scripts/android-render-test.sh <serial> <name> <seconds> [--cvar=value ...]
#
# Screenshot: out/android-tests/<name>.png. Uses only the app's own files; touches nothing else on the phone.
set -euo pipefail
export MSYS_NO_PATHCONV=1
serial=$1; name=$2; seconds=$3; shift 3
root=$(cd "$(dirname "$0")/.." && pwd)
out="$root/out/android-tests"; mkdir -p "$out"
pkg=com.codepdbh.chavokart
dir=/sdcard/Android/data/$pkg/files
adb -s "$serial" shell mkdir -p "$dir"
printf '%s\n' "$@" > "$out/$name.args"
adb -s "$serial" push "$(cygpath -w "$out/$name.args")" "$dir/extra_args.txt" >/dev/null
adb -s "$serial" shell am force-stop $pkg
adb -s "$serial" shell am start -n $pkg/.LauncherActivity >/dev/null
sleep 3
adb -s "$serial" shell uiautomator dump /sdcard/ck_ui.xml >/dev/null 2>&1 || true
bounds=$(adb -s "$serial" shell cat /sdcard/ck_ui.xml | tr '>' '
' | grep 'package="com.codepdbh.chavokart"' |
  grep 'text="JUGAR"\|text="PLAY"\|text="JOGAR"' | grep -o 'bounds="[^"]*"' | head -1)
xy=$(echo "$bounds" | sed -E 's/.*\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\].*/\1 \2 \3 \4/' | awk '{print int(($1+$3)/2), int(($2+$4)/2)}')
[ -n "$xy" ] || { echo "PLAY button not found"; exit 1; }
adb -s "$serial" logcat -c
adb -s "$serial" shell input tap $xy
sleep "$seconds"
adb -s "$serial" exec-out screencap -p > "$out/$name.png"
pid=$(adb -s "$serial" shell pidof $pkg:game | tr -d '\r' || true)
echo "[$name] game pid=${pid:-none} args: $*"
adb -s "$serial" logcat -d -b crash | grep -E "Abort message|signal [0-9]" | head -3 || true
