#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
  JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
  export JAVA_HOME
fi
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell ime enable com.kamaal.padkeys/.ime.PadKeysImeService
# Android may reset the default IME shortly after an IME package is updated; set it until it sticks.
IME=com.kamaal.padkeys/.ime.PadKeysImeService
for _ in 1 2 3 4 5; do
  adb shell ime set "$IME" >/dev/null
  sleep 1
  current=$(adb shell settings get secure default_input_method | tr -d '\r')
  [ "$current" = "$IME" ] && break
done
echo "$current"
[ "$current" = "$IME" ]
