#!/usr/bin/env bash
# Runs the connected instrumented tests and dumps logcat for diagnosis.
# Invoked from android-emulator-runner's `script:` input, which runs each
# line as a separate `sh -c`, so all logic lives in this file.
set +e
flavor="$2"
./gradlew "connected${flavor^}DebugAndroidTest" \
  -Pandroid.testInstrumentationRunnerArguments.serverPassword="$1"
status=$?
adb -s emulator-5554 logcat -d > logcat.txt 2>/dev/null
exit $status
