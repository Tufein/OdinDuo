#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
task_sdk="${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}"
task_jdk="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME="$task_jdk"
export PATH="$task_jdk/bin:$PATH"
mkdir -p app/build/console-classes app/build/console-dex
javac --release 8 -cp "$task_sdk/platforms/android-36/android.jar" -d app/build/console-classes tools/java/*.java
jar cf app/build/console.jar -C app/build/console-classes .
"$task_sdk/build-tools/36.0.0/d8" --min-api 33 --lib "$task_sdk/platforms/android-36/android.jar" \
    --output app/build/console-dex app/build/console.jar
