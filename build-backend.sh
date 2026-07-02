#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "========================================="
echo "  Attendance Backend — Build Script"
echo "========================================="

# Load .env if present
if [ -f .env ]; then
  echo "Loading environment from .env"
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

# Require Java 21
if command -v java &>/dev/null; then
  JAVA_VER=$(java -version 2>&1 | head -1 | awk -F '"' '{print $2}' | cut -d. -f1)
  if [ "$JAVA_VER" != "21" ]; then
    echo "WARNING: Java $JAVA_VER detected. Backend requires Java 21."
    echo "  export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64"
    if [ -d "/usr/lib/jvm/java-21-openjdk-amd64" ]; then
      export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
      export PATH="$JAVA_HOME/bin:$PATH"
      echo "  Auto-switched to Java 21."
    else
      echo "ERROR: Java 21 not found. Install with: sudo apt install openjdk-21-jdk"
      exit 1
    fi
  fi
else
  echo "ERROR: java not found."
  exit 1
fi

# Require DB_PASSWORD for non-prod local runs
if [ -z "${DB_PASSWORD:-}" ]; then
  echo "WARNING: DB_PASSWORD is not set. Copy .env.example to .env and set DB_PASSWORD."
fi

echo ""
echo "Building backend (skip tests)..."
./mvnw clean package -DskipTests

echo ""
echo "Build complete: target/attendance-0.0.1-SNAPSHOT.jar"
echo ""
echo "Run locally:"
echo "  export \$(grep -v '^#' .env | xargs) && java -jar target/attendance-0.0.1-SNAPSHOT.jar"
echo ""
echo "Run in production mode:"
echo "  SPRING_PROFILES_ACTIVE=prod JWT_SECRET=your-secret DB_URL=... DB_USERNAME=... DB_PASSWORD=... java -jar target/attendance-0.0.1-SNAPSHOT.jar"
