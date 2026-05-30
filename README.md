# BinaryStars Bluetooth Chat & File Sharing

A modern, cross-platform Bluetooth chat and file-sharing application built with **Avalonia UI** and **RFCOMM/SPP**. 

Supports **Android and Linux (Desktop)**.

## Features
- **Real-time Chat**: Messaging over Bluetooth Serial Port Profile (SPP).
- **Persistent History**: Local chat logs and settings saved securely using SQLite.
- **Cross-Platform**: Unified codebase for Desktop and Mobile.
- **Dual Role Support**: Every device can act as both a **Server** and a **Client**.

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
- Bluetooth must be active.

#### Android
- **Crucial**: Bluetooth and Location permissions are required.

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

## Architecture Note
The system uses **RFCOMM (Serial Port Profile)**:
- **Service UUID**: `00001101-0000-1000-8000-00805F9B34FB` (Standard SPP).
- **SQLite Storage**: Uses `DatabaseService` for local persistence of chats and messages.
- **Avalonia UI**: Handles rendering natively across platforms.
