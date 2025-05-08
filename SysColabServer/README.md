# WebSocket Relay Server

This server allows registered devices to communicate in real time using WebSockets. Devices register via HTTP, then establish a WebSocket connection using a UUID. Messages can be relayed between devices, and file transfers of up to 5 MB are supported.

## Table of Contents

* [Getting Started](#getting-started)
* [Register a Device](#register-a-device)
* [Connect via WebSocket](#connect-via-websocket)
* [Message Format](#message-format)
* [File Upload and Transfer](#file-upload-and-transfer)
* [Get Connected Devices](#get-connected-devices)
* [Disconnect Handling](#disconnect-handling)

---

## Getting Started

### Prerequisites

* .NET 8+
* A WebSocket-capable client (e.g., browser, Postman, `wscat`, or custom app)

Server runs on:

* HTTP: `http://localhost:5268`
* HTTPS: `https://localhost:7268`

---

## Register a Device

Before establishing a WebSocket connection, you must register your device via HTTP.

**Endpoint:** `POST /api/register`
**Body:** JSON with device information.

```json
{
  "id": "4fd18eaa-8cfb-4b60-84a4-9e57ae444d7e",
  "name": "Device Alpha",
  "type": "sensor"
}
```

**Response:**

* `200 OK`: Ready to connect WebSocket.
* `400 Bad Request`: UUID already registered.

---

## Connect via WebSocket

**URL format:**

```
ws://localhost:5268/ws?uuid={deviceUUID}
wss://localhost:7268/ws?uuid={deviceUUID}
```

Replace `{deviceUUID}` with the `id` you used in the registration step.

---

## Message Format

All messages sent via WebSocket must be in this format:

```json
{
  "targetId": "another-device-uuid",
  "messageType": "custom_event",
  "serializedJson": "{\"data\":\"example payload\"}"
}
```

**Fields:**

* `targetId`: UUID of the device you're sending to (`"all"` for broadcast)
* `messageType`: A string representing the event type (e.g., `"command"`, `"status"`)
* `serializedJson`: Any data (as JSON string)

**Responses:**

* On success: Message is forwarded to the target device.
* On error (e.g., target offline), an error message is returned to sender.

Example error:

```json
{
  "targetId": "sender-uuid",
  "messageType": "error",
  "serializedJson": "{\"errorCode\":\"DEVICE_OFFLINE\",\"message\":\"Target '...' not found or offline.\"}"
}
```

---

## File Upload and Transfer

### Step 1: Upload a File

**Endpoint:** `POST /api/file`
**Form Data:**

* `senderId`: UUID of the sending device
* `targetId`: UUID of the receiving device
* `file`: File to upload (`≤ 5 MB`)

**Response:**

```json
{
  "fileId": "generated-file-id"
}
```

If the target is connected, it receives a `file_offer` message:

```json
{
  "targetId": "target-device-uuid",
  "messageType": "file_offer",
  "serializedJson": "{\"fileId\":\"...\",\"name\":\"report.pdf\",\"size\":12345,\"senderId\":\"...\"}"
}
```

### Step 2: Download a File

**Endpoint:** `GET /api/file/{fileId}`
Returns the file contents and removes it from memory.

---

## Get Connected Devices

**Endpoint:** `GET /api/connected-devices`
**Response:**

```json
[
  {
    "id": "uuid",
    "name": "Device Alpha",
    "type": "sensor"
  },
  ...
]
```

Returns `404 Not Found` if no devices are connected.

---

## Disconnect Handling

When a device disconnects, all other connected devices receive a:

```json
{
  "targetId": "all",
  "messageType": "device_disconnected",
  "serializedJson": "{\"id\":\"...\",\"name\":\"...\",\"type\":\"...\"}"
}
```

Likewise, on connect, the same format is sent with `messageType: "device_connected"`.
