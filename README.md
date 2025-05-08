# SysColab

**SysColab** is a cross-platform, collaborative system that allows real-time interaction between devices on the same network. It includes device pairing, file transfers, remote control, and system monitoring — all powered by WebSockets and built using .NET MAUI Blazor.

This is the **root-level README** for the full SysColab solution. Each major part of the solution also includes its own dedicated README file with more detailed documentation:

* 📦 Shared Logic & Models
* 🌐 WebSocket Relay Server
* 📱 MAUI Client App

Refer to those project-specific README files for implementation details. Below is a high-level overview of what each part contributes to the system.

---

## 📚 Project Summaries

### 1. Shared Library

This library contains **shared types, models, and utilities** used across the server and client projects. It ensures consistent data structures and logic, such as:

* 📄 `DeviceInfo`: Describes a device's identity and pairing status.
* 📁 `FileOffer`: Metadata for file transfer offers.
* 📩 `RelayMessage`: Generic message container for WebSocket communication.

These models enable structured communication and serialization between components.

---

### 2. WebSocket Relay Server

A lightweight **.NET Web API server** that handles:

* ✅ Device registration via `POST /api/register`
* 🔄 Persistent WebSocket communication using UUIDs
* 🔀 Message relaying between devices with message types like:

  * `connect`, `file_offer`, `device_metrics_request`, etc.
* 📂 File uploads (up to 5 MB) with one-time download capability
* 👥 Real-time connected device listing via `GET /api/connected-devices`

It acts as the central hub for coordinating interactions between devices on the network.

---

### 3. MAUI Blazor Client

A cross-platform **client app** (Windows & Android) built with **.NET MAUI Blazor**, featuring:

* 📺 Real-time dashboard of online devices
* 🔎 Network-based device discovery and pairing
* 🔁 File transfer with drag-and-drop support
* 🧠 System metric reporting (CPU, RAM, storage, network)
* 🖥️ Remote control capabilities (on supported platforms)
* 🔄 `SharedComponent.cs` to manage WebSocket sessions and UI updates

This client consumes the shared library and server APIs to deliver a rich, synchronized collaboration experience.

---

## 🛠 Platform-Aware Design

The client uses a **platform-aware dependency injection model** for device metrics:

```csharp
#if ANDROID
    builder.Services.AddSingleton<IDeviceMetricService, SysColab.Platforms.Android.Services.DeviceMetricsService>();
#elif WINDOWS
    builder.Services.AddSingleton<IDeviceMetricService, SysColab.Platforms.Windows.Services.DeviceMetricsService>();
#endif
```

This lets each platform report hardware stats using native APIs — all behind a shared interface (`IDeviceMetricService`).

---

## 🚀 Getting Started

Each project contains its own README with setup steps and usage instructions. Start by reviewing the server setup, then launch the client for real-time device interaction on your local network.

---

## 🛠 Installation & Execution

To run the full **SysColab** system (server + MAUI client), follow these steps carefully. This solution requires cross-platform coordination and local network access.

---

### ✅ Prerequisites

* **.NET 9 SDK**
  Ensure you have the [.NET 9 SDK](https://dotnet.microsoft.com/en-us/download) installed — it is required by all three projects.

* **.NET MAUI Workload**
  Install the MAUI workload:

  ```bash
  dotnet workload install maui
  ```

* **Android Debugging (if applicable):**
  Enable developer mode and USB or Wi-Fi debugging on your device.

---

### 🌐 Server Setup

1. Open the server project.
2. Modify `launchSettings.json` under `Properties/` to reflect a **LAN-accessible IP** (e.g., your local IPv4 address):

```json
{
  "$schema": "https://json.schemastore.org/launchsettings.json",
  "profiles": {
    "https": {
      "commandName": "Project",
      "dotnetRunMessages": true,
      "applicationUrl": "https://192.168.110.97:7268;http://192.168.110.97:5268",
      "environmentVariables": {
        "ASPNETCORE_ENVIRONMENT": "Development"
      }
    }
  }
}
```

Ensure that **all devices (clients)** on your network can access the IP and ports defined here.

---

### 📱 MAUI Client Setup

1. Update the constants file under the MAUI project:

**`Constants/ServerConstants.cs`**

```csharp
namespace SysColab.Constants
{
    internal class ServerConstants
    {
        public const string ServerBaseUrl = "http://192.168.110.97:5268";
        public const string ServerDomain = "192.168.110.97:5268";
    }
}
```

> Make sure this IP matches the one configured in the server's launch profile.

2. For **Android builds**, also update the cleartext permission in:

**`Platforms/Android/Resources/xml/network_security_config.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">192.168.110.97</domain>
  </domain-config>
</network-security-config>
```

---

### 🚀 Running the Solution

1. **Start the Server Project**
   Run it in your IDE or with `dotnet run` and confirm the WebSocket/HTTP server is live at the configured IP.

2. **Launch a Client App**

   * On **Windows**, simply run the MAUI app using the Windows target.
   * On **Android**, deploy to a debug-enabled device with either:

     * USB debugging
     * Wi-Fi debugging (with correct IP access to the server)

3. Use two or more devices to **discover**, **pair**, **transfer files**, or **control remotely**.
