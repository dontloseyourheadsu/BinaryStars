# BinaryStars API Endpoints

Base URL (local): http://localhost:5004

All routes are prefixed with /api unless otherwise noted.

## Authentication

Protected endpoints require an Authorization header:

```
Authorization: Bearer <access-token>
```

The API also refreshes JWTs on authenticated responses:

- X-Access-Token
- X-Access-Token-ExpiresIn

## Auth

### Register

POST /api/auth/register

```json
{
  "username": "jupiter",
  "email": "jupiter@example.com",
  "password": "StrongPassword123!"
}
```

Response

```json
{
  "tokenType": "Bearer",
  "accessToken": "<jwt>",
  "expiresIn": 7200
}
```

### Login

POST /api/auth/login

```json
{
  "email": "jupiter@example.com",
  "password": "StrongPassword123!"
}
```

### External Login

POST /api/auth/login/external

```json
{
  "provider": "Google",
  "token": "<provider-id-token>",
  "username": "optional_username"
}
```

## Accounts

### Get Current Profile

GET /api/accounts/me

Response

```json
{
  "id": "7c6cf0b1-6f93-4c6b-a8cb-7f5e4c1c3d5a",
  "username": "jupiter",
  "email": "jupiter@example.com",
  "role": "Free"
}
```

## Devices

### List Devices

GET /api/devices

### Register Device

POST /api/devices/register

```json
{
  "id": "android-ssa-id-123",
  "name": "Pixel 8",
  "ipAddress": "192.168.1.10",
  "ipv6Address": "fe80::1",
  "publicKey": "<base64-public-key>",
  "publicKeyAlgorithm": "RSA"
}
```

### Update Device Telemetry

PUT /api/devices/{deviceId}/telemetry

```json
{
  "batteryLevel": 82,
  "cpuLoadPercent": 14,
  "isOnline": true,
  "isAvailable": true,
  "isSynced": true,
  "wifiUploadSpeed": "1200 kbps",
  "wifiDownloadSpeed": "5400 kbps"
}
```

### Unlink Device

DELETE /api/devices/{deviceId}

### Device Heartbeat

POST /api/devices/{deviceId}/heartbeat

Records device liveness and marks the device online.

## Notes

### List Notes

GET /api/notes

### List Notes For Device

GET /api/notes/device/{deviceId}

### Get Note By Id

GET /api/notes/{noteId}

### Create Note

POST /api/notes

```json
{
  "name": "My first note",
  "deviceId": "android-ssa-id-123",
  "contentType": "Text",
  "content": "Hello from BinaryStars"
}
```

### Update Note

PUT /api/notes/{noteId}

```json
{
  "name": "Updated title",
  "content": "Updated content"
}
```

### Delete Note

DELETE /api/notes/{noteId}

## File Transfers

### List Transfers

GET /api/files/transfers

### List Pending Transfers For Device

GET /api/files/transfers/pending?deviceId={deviceId}

### Get Transfer By Id

GET /api/files/transfers/{transferId}

### Create Transfer

POST /api/files/transfers

```json
{
  "fileName": "example.bin",
  "contentType": "application/octet-stream",
  "sizeBytes": 123456,
  "senderDeviceId": "android-ssa-id-123",
  "targetDeviceId": "android-ssa-id-456",
  "encryptionEnvelope": "{\"alg\":\"RSA\",\"keyId\":\"key-1\"}"
}
```

### Upload Transfer Bytes

PUT /api/files/transfers/{transferId}/upload

- Body: raw binary content
- Returns 202 Accepted when queued for Kafka publishing.

### Download Transfer Bytes

GET /api/files/transfers/{transferId}/download?deviceId={deviceId}

Response headers:

- Content-Disposition (original filename)
- X-Transfer-Envelope (base64-encoded JSON)
- X-Transfer-ChunkSize

### Reject Transfer

POST /api/files/transfers/{transferId}/reject?deviceId={deviceId}

## Locations

### Create Location

POST /api/locations

```json
{
  "deviceId": "android-ssa-id-123",
  "latitude": 40.7128,
  "longitude": -74.006,
  "accuracyMeters": 6.5,
  "recordedAt": "2026-02-08T16:00:00Z"
}
```

### Create Live Location (No History Persistence)

POST /api/locations/live

```json
{
  "deviceId": "android-ssa-id-123",
  "latitude": 40.7128,
  "longitude": -74.006,
  "accuracyMeters": 6.5,
  "recordedAt": "2026-02-08T16:00:00Z"
}
```

### Get Location History

GET /api/locations/history?deviceId={deviceId}&limit=50

If a fresh live update exists and is not yet persisted, the first item may be titled `Live`.

## Messaging

### Send Message

POST /api/messaging/send

```json
{
  "senderDeviceId": "android-ssa-id-123",
  "targetDeviceId": "android-ssa-id-456",
  "body": "Hello from BinaryStars",
  "sentAt": "2026-02-08T16:00:00Z"
}
```

### Websocket Messaging

GET /ws/messaging?deviceId={deviceId}

- Requires Authorization: Bearer <access-token> header.
- Sends pending Kafka messages before realtime messages.

WebSocket envelope types currently used by the API include:

- `message`
- `device_removed`
- `device_presence`
- `location_update`
- `action_command`
- `action_result`

## Actions (Realtime)

Action delivery is realtime over `/ws/messaging` when both devices are online and connected.

### Send Action Command (HTTP bridge)

POST /api/actions/send

```json
{
  "senderDeviceId": "android-ssa-id-123",
  "targetDeviceId": "linux-desktop-001",
  "actionType": "list_installed_apps",
  "payloadJson": null,
  "correlationId": "req-123"
}
```

Behavior:

- Validates device ownership and Linux target constraints.
- Dispatches `action_command` to the target device websocket.
- Returns 400 when the target websocket is not connected.

### Publish Action Result (HTTP bridge)

POST /api/actions/results

```json
{
  "senderDeviceId": "linux-desktop-001",
  "targetDeviceId": "android-ssa-id-123",
  "actionType": "list_installed_apps",
  "status": "success",
  "payloadJson": "[{\"name\":\"Firefox\",\"exec\":\"firefox\"}]",
  "error": null,
  "correlationId": "req-123"
}
```

Behavior:

- Dispatches `action_result` to the requester websocket.
- Returns 400 when the requester websocket is not connected.

### Pull Endpoints (Deprecated Compatibility)

The following routes are retained for compatibility but no longer drive action delivery:

- GET /api/actions/pull?deviceId={deviceId}
- GET /api/actions/results/pull?deviceId={deviceId}

Both return empty arrays in the realtime model.

## Notifications

### Send Notification Now

POST /api/notifications/send

```json
{
  "senderDeviceId": "android-ssa-id-123",
  "targetDeviceId": "android-ssa-id-456",
  "title": "Heads up",
  "body": "This is an immediate notification"
}
```

### Get Notification Schedules For Target Device

GET /api/notifications/schedules?deviceId={deviceId}

### Create Notification Schedule

POST /api/notifications/schedules

```json
{
  "sourceDeviceId": "android-ssa-id-123",
  "targetDeviceId": "android-ssa-id-456",
  "title": "Reminder",
  "body": "Stretch and hydrate",
  "isEnabled": true,
  "scheduledForUtc": "2026-03-15T09:00:00Z",
  "repeatMinutes": 60
}
```

### Update Notification Schedule

PUT /api/notifications/schedules/{scheduleId}

Body is the same shape as schedule creation.

### Delete Notification Schedule

DELETE /api/notifications/schedules/{scheduleId}

### Pull Pending Notifications + Schedules

GET /api/notifications/pull?deviceId={deviceId}

Response:

```json
{
  "hasPendingNotificationSync": true,
  "notifications": [
    {
      "id": "notification-id",
      "userId": "user-id",
      "senderDeviceId": "android-ssa-id-123",
      "targetDeviceId": "android-ssa-id-456",
      "title": "Heads up",
      "body": "This is an immediate notification",
      "createdAt": "2026-03-14T12:00:00Z"
    }
  ],
  "schedules": []
}
```

### Acknowledge Notification Sync Applied

POST /api/notifications/ack

```json
{
  "deviceId": "android-ssa-id-456"
}
```

## Actions

Actions are currently supported only for Linux target devices.

### Send Action Command

POST /api/actions/send

```json
{
  "senderDeviceId": "android-ssa-id-123",
  "targetDeviceId": "linux-desktop-001",
  "actionType": "get_clipboard_history",
  "payloadJson": null,
  "correlationId": "req-123"
}
```

### Pull Pending Actions For Device

GET /api/actions/pull?deviceId={deviceId}

### Publish Action Result

POST /api/actions/results

```json
{
  "senderDeviceId": "linux-desktop-001",
  "targetDeviceId": "android-ssa-id-123",
  "actionType": "get_clipboard_history",
  "status": "success",
  "payloadJson": "[\"first entry\",\"second entry\"]",
  "error": null,
  "correlationId": "req-123"
}
```

### Pull Pending Action Results For Device

GET /api/actions/results/pull?deviceId={deviceId}

For `get_clipboard_history`, payload currently returns up to 20 text entries when history providers are available on the Linux target, and otherwise falls back to a single current clipboard entry.

Common action types:

- `block_screen`
- `shutdown`
- `reboot`
- `list_installed_apps`
- `list_running_apps`
- `launch_app`
- `close_app`
- `get_clipboard_history`

## Debug (Development Only)

### Decode JWT (No Validation)

POST /api/debug/token

```json
{
  "token": "<jwt>"
}
```
