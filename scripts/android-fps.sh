#!/usr/bin/env bash
# Presented FPS of the game surface (SurfaceFlinger timestats), measured without touching the game.
#   scripts/android-fps.sh <serial> [seconds]
export MSYS_NO_PATHCONV=1
s=$1; secs=${2:-6}
adb -s "$s" shell dumpsys SurfaceFlinger --timestats -enable -clear >/dev/null
sleep "$secs"
adb -s "$s" shell dumpsys SurfaceFlinger --timestats -dump -maxlayers 8 | tr -d '\r' |
  awk '/layerName = .*SurfaceView\[com.codepdbh.chavokart/{f=1} f&&/totalFrames|averageFPS/{print} f&&/averageFPS/{exit}'
adb -s "$s" shell dumpsys SurfaceFlinger --timestats -disable >/dev/null
