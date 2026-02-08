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

### Unlink Device

DELETE /api/devices/{deviceId}

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

### Get Location History

GET /api/locations/history?deviceId={deviceId}&limit=50

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

## Debug (Development Only)

### Decode JWT (No Validation)

POST /api/debug/token

```json
{
  "token": "<jwt>"
}
```
