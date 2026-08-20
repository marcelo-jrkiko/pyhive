#!/usr/bin/env bash
# NOTE: When this script is launched from VS Code's task runner, it runs in a
# non-interactive shell. ~/.bashrc bails out early for non-interactive shells
# (case "$-" in *i) ... *) return;; esac), so the ADB PATH exports near the
# bottom are never applied. Source it best-effort, then fall back to adding the
# Android SDK binary dirs directly.
source "$HOME/.bashrc" 2>/dev/null || true
if ! command -v adb >/dev/null 2>&1; then
  ANDROID_HOME="${ANDROID_HOME:-/opt/extended/Apps/Android/SDK}"
  ANDROID_TOOLS_VERSION="${ANDROID_TOOLS_VERSION:-36.0.0}"
  export ANDROID_HOME ANDROID_TOOLS_VERSION
  export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/build-tools/$ANDROID_TOOLS_VERSION/"
fi
APP_ID="${APP_ID:-krs.pyhive}"
MAIN_ACTIVITY="${MAIN_ACTIVITY:-krs.pyhive/.MainActivity}"
LOCAL_DEBUG_PORT="${LOCAL_DEBUG_PORT:-8700}"
WORKDIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

pick_device() {
  if [[ -n "${ADB_SERIAL:-}" ]]; then
    echo "${ADB_SERIAL}"
    return 0
  fi

  local serial
  serial="$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')"
  if [[ -z "${serial}" ]]; then
    echo "No authorized Android device found. Connect device and run: adb devices" >&2
    exit 1
  fi

  echo "${serial}"
}

DEVICE_SERIAL="$(pick_device)"

echo "Using device: ${DEVICE_SERIAL}"
echo "Building + installing debug APK..."
cd "${WORKDIR}"
./gradlew :app:installDebug

echo "Starting app activity: ${MAIN_ACTIVITY}"
adb -s "${DEVICE_SERIAL}" shell am start -W -n "${MAIN_ACTIVITY}" >/dev/null

PID=""
for _ in 1 2 3 4 5; do
  PID="$(adb -s "${DEVICE_SERIAL}" shell pidof -s "${APP_ID}" 2>/dev/null | tr -d '\r' || true)"
  if [[ -n "${PID}" ]]; then
    break
  fi
done
if [[ -z "${PID}" ]]; then
  echo "Could not find process PID for ${APP_ID}. Is the app running?" >&2
  exit 1
fi

echo "Preparing JDWP forward localhost:${LOCAL_DEBUG_PORT} -> pid:${PID}"
if adb -s "${DEVICE_SERIAL}" forward --list | grep -q "tcp:${LOCAL_DEBUG_PORT}"; then
  adb -s "${DEVICE_SERIAL}" forward --remove "tcp:${LOCAL_DEBUG_PORT}" || true
fi
adb -s "${DEVICE_SERIAL}" forward "tcp:${LOCAL_DEBUG_PORT}" "jdwp:${PID}"

echo "Ready to debug"
echo "Device serial : ${DEVICE_SERIAL}"
echo "Package       : ${APP_ID}"
echo "PID           : ${PID}"
echo "Local port    : ${LOCAL_DEBUG_PORT}"
