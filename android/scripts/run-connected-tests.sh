#!/usr/bin/env bash
# Helper script to run Android instrumented integration tests in headless mode

set -e

AVD_NAME="Medium_Phone_API_35"

# Find emulator binary
EMULATOR_BIN=""
if [ -n "$ANDROID_HOME" ]; then
    EMULATOR_BIN="$ANDROID_HOME/emulator/emulator"
elif [ -d "$HOME/Android/Sdk" ]; then
    EMULATOR_BIN="$HOME/Android/Sdk/emulator/emulator"
else
    echo "Error: ANDROID_HOME is not set and Sdk not found in default location."
    exit 1
fi

if [ ! -f "$EMULATOR_BIN" ]; then
    echo "Error: emulator binary not found at $EMULATOR_BIN"
    exit 1
fi

echo "Starting emulator AVD '$AVD_NAME' in headless mode..."
"$EMULATOR_BIN" -avd "$AVD_NAME" -no-audio -no-window -no-snapshot-load -gpu swiftshader &
EMU_PID=$!

function cleanup {
    echo "Shutting down emulator (PID: $EMU_PID)..."
    kill $EMU_PID 2>/dev/null || true
    wait $EMU_PID 2>/dev/null || true
    echo "Cleanup complete."
}
trap cleanup EXIT

echo "Waiting for emulator to boot completed..."
adb wait-for-device
while [ "$(adb shell getprop sys.boot_completed | tr -d '\r')" != "1" ]; do
    sleep 3
done
echo "Emulator booted successfully!"

echo "Running instrumented integration tests..."
./gradlew connectedDebugAndroidTest
