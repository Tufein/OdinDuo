#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# Honor configured Java/Android installations; use the usual macOS locations as a fallback.
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ] && [ -d "$HOME/Library/Android/sdk" ]; then
    export ANDROID_HOME="$HOME/Library/Android/sdk"
fi

variant=${1:-debug}
case "$variant" in
    debug) task=assembleDebug ;;
    release) task=assembleRelease ;;
    *) printf 'Usage: %s [debug|release]\n' "$0" >&2; exit 2 ;;
esac
./gradlew ":app:$task" --console=plain
cp "app/build/outputs/apk/$variant/app-$variant.apk" app/build/OdinDuo.apk
printf 'APK: %s/app/build/OdinDuo.apk\n' "$PWD"
