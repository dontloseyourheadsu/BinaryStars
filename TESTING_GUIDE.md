# Bluetooth Chat — Testing Guide

Bluetooth Classic RFCOMM requires both devices to be paired at the OS level before they can connect.

## Step 1: Pair Linux and Android in System Settings

### On Android:
1. Open **Settings** -> **Connected Devices**.
2. Tap **Pair New Device** so that the phone is actively discoverable.

### On Linux:
Run `bluetoothctl` in your terminal:
1. Type `power on`, then `agent on`, then `default-agent`.
2. Start scanning: `scan on`.
3. Locate your phone's MAC address (`C4:EF:3D:E4:8B:8E`).
4. Pair with it: `pair C4:EF:3D:E4:8B:8E`. Accept the pairing PIN on both screens.
5. Trust the phone: `trust C4:EF:3D:E4:8B:8E`.
6. Type `exit`.

---

## Step 2: Auto-Chat Mode
We updated the applications to automatically launch straight into a chat room with each other:
- **Android** will launch and immediately listen as a server.
- **Linux** will launch and immediately scan/connect to your Android phone (`C4:EF:3D:E4:8B:8E`).

### Build & Run Android:
```bash
dotnet build BinaryStars.Android/BinaryStars.Android.csproj
adb -s adb-R5CY608ELFM-1tKEOj._adb-tls-connect._tcp install -r BinaryStars.Android/bin/Debug/net10.0-android/com.CompanyName.BinaryStars-Signed.apk
adb -s adb-R5CY608ELFM-1tKEOj._adb-tls-connect._tcp shell monkey -p com.CompanyName.BinaryStars -c android.intent.category.LAUNCHER 1
```

### Run Linux Desktop:
```bash
DISPLAY=:0 dotnet run --project BinaryStars.Desktop/BinaryStars.Desktop.csproj
```

Both apps will bypass the main screen and attempt to connect directly to each other!
