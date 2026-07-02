#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MOBILE_DIR="$SCRIPT_DIR/mobile"
PROFILE="${1:-preview}"

echo "========================================="
echo "  Attendance Mobile — Build Script"
echo "  Profile: $PROFILE"
echo "========================================="

cd "$MOBILE_DIR"

# Load .env if present
if [ -f .env ]; then
  echo "Loading environment from mobile/.env"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

# Check Node version (Expo 51 works best with Node 20)
if command -v node &>/dev/null; then
  NODE_MAJOR=$(node -v | cut -d. -f1 | tr -d v)
  if [ "$NODE_MAJOR" -gt 20 ]; then
    echo "WARNING: Node $(node -v) detected. Expo SDK 51 recommends Node 20 LTS."
    echo "  nvm install 20 && nvm use 20"
  fi
fi

echo ""
echo "Installing dependencies..."
npm install

echo ""
echo "Running expo-doctor..."
npx expo-doctor || true

if [ "$PROFILE" = "local" ]; then
  echo ""
  echo "Building local Android debug APK..."
  export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
  npx expo run:android --variant debug
elif command -v eas &>/dev/null; then
  echo ""
  echo "Starting EAS build (profile: $PROFILE)..."
  eas build --platform android --profile "$PROFILE"
else
  echo ""
  echo "EAS CLI not found. Building local Android release..."
  echo "Install EAS for cloud builds: npm install -g eas-cli"
  export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-17-openjdk-amd64}"
  cd android
  ./gradlew assembleRelease
  echo ""
  echo "APK: android/app/build/outputs/apk/release/app-release.apk"
fi

echo ""
echo "Build script finished."
