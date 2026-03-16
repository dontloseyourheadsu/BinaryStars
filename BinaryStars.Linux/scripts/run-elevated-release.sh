#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage:
  ./scripts/run-elevated-release.sh

Deprecated: full-app elevated mode is no longer recommended.
Run the normal release app instead:
  ./scripts/run-release.sh

Privileged actions (shutdown/reboot) now request authorization on demand
through PolicyKit from normal mode.
EOF
}

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"
BINARY="$PROJECT_DIR/src-tauri/target/release/binarystarslinux"

if [ ! -x "$BINARY" ]; then
  echo "Release binary not found at: $BINARY"
  echo "Build it first:"
  echo "  cd $PROJECT_DIR"
  echo "  npm run tauri:build:local"
  exit 1
fi

echo "Full-app elevated launch is deprecated. Starting normal release app..."
exec "$BINARY"
