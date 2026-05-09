#!/usr/bin/env bash
set -euo pipefail

PORT="5004"
HOST=""

usage() {
  cat <<'EOF'
Usage:
  ./scripts/install-device-debug.sh [--host <ip>] [--port <port>] [--] [extra gradle args]

Examples:
  ./scripts/install-device-debug.sh
  ./scripts/install-device-debug.sh --host 192.168.233.222
  ./scripts/install-device-debug.sh --port 5004 -- --info

Notes:
  - If --host is omitted, the script auto-detects the active host IP from: ip route get 1.1.1.1
  - Runs: ./gradlew :app:installDeviceDebug -PapiHost=<host> -PapiPort=<port>
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --host)
      if [ $# -lt 2 ]; then
        echo "Missing value for --host" >&2
        usage
        exit 1
      fi
      HOST="$2"
      shift 2
      ;;
    --port)
      if [ $# -lt 2 ]; then
        echo "Missing value for --port" >&2
        usage
        exit 1
      fi
      PORT="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    --)
      shift
      break
      ;;
    *)
      break
      ;;
  esac
done

EXTRA_ARGS=("$@")

if [ -z "$HOST" ]; then
  if ! command -v ip >/dev/null 2>&1; then
    echo "Could not auto-detect host IP: 'ip' command not found. Use --host <ip>." >&2
    exit 1
  fi

  HOST="$(ip route get 1.1.1.1 2>/dev/null | awk '{for (i=1; i<=NF; i++) if ($i=="src") {print $(i+1); exit}}')"

  if [ -z "$HOST" ]; then
    echo "Could not auto-detect host IP. Use --host <ip>." >&2
    exit 1
  fi
fi

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd -- "$SCRIPT_DIR/.." && pwd)"

echo "Installing debug APK using host $HOST:$PORT"
cd "$PROJECT_DIR"
./gradlew :app:installDeviceDebug -PapiHost="$HOST" -PapiPort="$PORT" "${EXTRA_ARGS[@]}"
