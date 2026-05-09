#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
BINARY="$PROJECT_DIR/src-tauri/target/release/binarystarslinux"

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  cat <<'EOF'
Usage:
  ./scripts/run-release.sh

Builds are not performed automatically.
If the release binary is missing, build first:
  npm run tauri:build:local
EOF
  exit 0
fi

if [ ! -x "$BINARY" ]; then
  echo "Release binary not found at: $BINARY"
  echo "Build it first:"
  echo "  cd $PROJECT_DIR"
  echo "  npm run tauri:build:local"
  exit 1
fi

exec "$BINARY"
