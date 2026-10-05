#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
if [ -z "${JAVA_HOME:-}" ] && [ -d /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home ]; then
    export JAVA_HOME=/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home
fi
if [ -n "${JAVA_HOME:-}" ]; then export PATH="$JAVA_HOME/bin:$PATH"; fi
mkdir -p app/build/check-classes
javac --release 17 -d app/build/check-classes \
    app/src/nl/retroid/touchguard/ProcessIdentity.java \
    app/src/nl/retroid/touchguard/UsbIdentity.java \
    app/src/nl/retroid/touchguard/GuardLogState.java \
    app/src/nl/retroid/touchguard/GuardHealth.java \
    app/tests/ProcessIdentityTest.java app/tests/UsbIdentityTest.java app/tests/GuardLogStateTest.java app/tests/GuardHealthTest.java
java -cp app/build/check-classes nl.retroid.touchguard.ProcessIdentityTest
java -cp app/build/check-classes nl.retroid.touchguard.UsbIdentityTest
java -cp app/build/check-classes nl.retroid.touchguard.GuardLogStateTest
java -cp app/build/check-classes nl.retroid.touchguard.GuardHealthTest
for script in app/build.sh capture.sh tools/*.sh; do bash -n "$script"; done
python3 -m py_compile tools/collect-usb-open-test.py
python3 app/tests/guard_lifecycle_test.py
