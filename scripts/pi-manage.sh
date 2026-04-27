#!/usr/bin/env bash
set -euo pipefail

# Load config
if [ -f .env.pi ]; then
    source .env.pi
else
    echo "Error: .env.pi not found. Copy .env.pi.example and fill it out."
    exit 1
fi

PI_ADDR="${PI_USER}@${PI_IP}"

usage() {
    cat <<EOF
BinaryStars Pi Manager
Usage: $0 [command]

Commands:
  discover    Network Scan: Searches your local subnet for the Raspberry Pi by looking for the open API port ($API_PORT).
  sync        File Transfer: Uses rsync to upload the project source code to the Pi, excluding build artifacts and node_modules.
  up          Start Services: Connects via SSH to start the Docker containers (API, Kafka, DB) in detached mode.
  down        Stop Services: Connects via SSH to stop and remove all BinaryStars Docker containers.
  logs        Live Logs: Streams the real-time output from the API container on the Pi for debugging.
  android     Mobile Deploy: Builds the Android APK and installs it on your connected device, pre-configured to hit the Pi's IP.
  linux       Desktop Client: Launches the Linux (Tauri) app on your local machine, configured to connect to the remote Pi API.
  all         Full Lifecycle: Sequential execution of 'sync', 'up', and 'android' to get everything running in one step.
EOF
}

check_ssh() {
    ssh -o BatchMode=yes -o ConnectTimeout=5 "$PI_ADDR" "echo 1" >/dev/null 2>&1 || {
        echo "Error: Cannot SSH into Pi. Check PI_IP and ensure your SSH key is in ~/.ssh/authorized_keys on the Pi."
        exit 1
    }
}

case "${1:-}" in
    discover)
        echo "Scanning subnet for BinaryStars API..."
        SUBNET=$(ip route get 1.1.1.1 | awk '{print $7}' | cut -d. -f1-3)
        nmap -p $API_PORT $SUBNET.0/24 --open | grep "Nmap scan report"
        ;;

    sync)
        echo "Syncing files to $PI_ADDR..."
        rsync -avz --delete \
            --exclude '.git' \
            --exclude 'node_modules' \
            --exclude 'bin' \
            --exclude 'obj' \
            --exclude '.gradle' \
            --exclude 'target' \
            ./ "$PI_ADDR:~/BinaryStars/"
        ;;

    up)
        check_ssh
        echo "Starting services on Pi..."
        ssh "$PI_ADDR" "cd ~/BinaryStars && docker compose up -d"
        ;;

    down)
        check_ssh
        echo "Stopping services on Pi..."
        ssh "$PI_ADDR" "cd ~/BinaryStars && docker compose down"
        ;;

    logs)
        check_ssh
        ssh "$PI_ADDR" "docker logs -f binarystars-binarystars-api-1"
        ;;

    android)
        echo "Installing Android app pointing to $PI_IP..."
        cd BinaryStars.Android
        ./gradlew :app:installDeviceDebug -PapiHost="$PI_IP" -PapiPort="$API_PORT"
        ;;

    linux)
        echo "Starting Linux app pointing to $PI_IP..."
        cd BinaryStars.Linux
        VITE_API_BASE_URL="http://$PI_IP:$API_PORT/api" npm run tauri dev
        ;;

    all)
        $0 sync
        $0 up
        $0 android
        ;;

    *)
        usage
        ;;
esac
