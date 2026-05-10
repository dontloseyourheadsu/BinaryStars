# BinaryStars Bluetooth Chat & File Sharing

A modern, cross-platform Bluetooth chat and file-sharing application built with **Avalonia UI** and **Shiny BLE**. 

Supports **Android, Linux (BlueZ), macOS, and Windows**.

## Features
- **Real-time Chat**: High-performance instant messaging over Bluetooth Low Energy (BLE).
- **File Sharing**: Chunked streaming protocol for reliable transfer of any file type.
- **Image Previews**: Auto-generated previews for common image formats (JPG, PNG, WebP) directly in bubbles.
- **Persistent History**: Local chat logs saved securely using SQLite.
- **Stable UX**: Custom-built minimalist UI designed for high stability on diverse Android hardware.
- **Dual Role Support**: Every device can act as both a **Host** (Peripheral/Server) and a **Scanner** (Central/Client).

---

## Development Setup

### 1. Prerequisites
- **.NET 10 SDK**
- **Android Workload**:
  ```bash
  dotnet workload install android
  ```

### 2. Platform-Specific Setup
#### Linux (Desktop)
- Ensure the `bluez` package is installed.
- Your user must be in the `bluetooth` group to access the D-Bus interface.
- Bluetooth must be active.

#### Android
- Enable **Developer Options** and **Wireless Debugging**.
- **Crucial**: Turn on **Location/GPS** (Required for BLE scanning on Android).

---

## Running the Application

### Running on Linux Desktop
Execute the following command from the root directory:
```bash
dotnet run --project BinaryStars.Desktop/BinaryStars.Desktop.csproj -f net10.0
```

### Running on Android
Ensure your device is connected (via USB or Wireless Debugging) and detected by `adb devices`, then run:
```bash
dotnet build BinaryStars.Android/BinaryStars.Android.csproj -f net10.0-android -t:Run
```

---

## How to Test the Chat
1.  Launch the app on **Device A** and click **"Start Host"**.
2.  Launch the app on **Device B** and click **"Scan"** (top right).
3.  On **Device B**, find **Device A** in the "Nearby Devices" list and click it to connect.
4.  Once connected, start chatting or sharing files using the **"+"** icon.

---

## Architecture Note
The system uses a custom **Chunked GATT Protocol**:
- **MTU Safety**: Data is split into 180-byte chunks to fit within standard Bluetooth packets.
- **Packet Headers**: Uses header bytes (`0x01` to `0x04`) to distinguish between text, metadata, and binary streams.
- **Shiny v4**: Leverages the latest Reactive Bluetooth abstraction for unified cross-platform communication.
- **Avalonia UI**: Handles rendering natively, bypassing the complex accessibility layers that cause crashes on some Android devices.
