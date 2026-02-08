# BinaryStars API

The BinaryStars API is the .NET 10 backend that powers authentication, device management, notes, file transfers, messaging, and location history. It exposes REST endpoints and a websocket endpoint for realtime messaging, while streaming large file transfers through Kafka.

## What This Service Does

- Authenticates users (password + external providers) and issues JWTs.
- Manages devices, notes, and location history stored in PostgreSQL.
- Streams file transfers to/from Kafka using packetized uploads.
- Delivers device-to-device messages via websocket or Kafka fallback.
- Runs background cleanup jobs with Hangfire.

## Related Docs

- Endpoints and request/response details: Endpoints.md
- REST client examples: Endpoints.http
- Kafka TLS/SASL setup and local certificates: ../kafka/README.md
- Overall system architecture: ../README.md

## Run Locally (Dotnet)

1. Start infrastructure (PostgreSQL, Kafka, Hangfire DB, Grafana/Loki):
   ```bash
   docker compose up -d
   ```
2. Configure the API:
   - Copy BinaryStars.Api/appsettings.json or create BinaryStars.Api/appsettings.Development.json.
   - Update connection strings and auth settings as needed (see Configuration below).
3. Run the API:
   ```bash
   cd BinaryStars.Api
   dotnet run
   ```

The API listens on http://localhost:5004 by default (see docker-compose.yaml).

## Run Locally (Docker)

To run the API in Docker with its dependencies:

```bash
docker compose up -d binarystars.api
```

The API container mounts kafka/secrets for TLS/SASL settings. Make sure you generated local certificates in kafka/secrets first (see ../kafka/README.md).

If you are using docker compose, the default values in docker-compose.yaml are
for local development only.

## Configuration

The API uses appsettings.json and environment variables. Environment variables use double-underscore keys (for example, ConnectionStrings\_\_DefaultConnection).

Minimal configuration:

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=binarystars;Username=postgres;Password=yourPassword"
  },
  "Jwt": {
    "Issuer": "BinaryStars.Api",
    "Audience": "BinaryStars.Api",
    "SigningKey": "YOUR_LONG_RANDOM_SECRET",
    "ExpiresInMinutes": 120
  },
  "Kafka": {
    "BootstrapServers": "binarystars.kafka:9093",
    "Topic": "binarystars.transfers",
    "MessagingTopic": "binarystars.messages",
    "DeviceRemovedTopic": "binarystars.device-removed",
    "Security": {
      "UseTls": true,
      "UseSasl": true,
      "CaPath": "kafka/secrets/ca.pem",
      "ClientCertPath": "kafka/secrets/client.pem",
      "ClientKeyPath": "kafka/secrets/client.key"
    },
    "Scram": {
      "Username": "binarystars",
      "Password": "binarystars"
    }
  },
  "FileTransfers": {
    "ChunkSizeBytes": 524288,
    "TempPath": "/tmp/binarystars-transfers",
    "ExpiresInMinutes": 60
  },
  "Hangfire": {
    "ConnectionString": "Host=localhost;Database=binarystars_hangfire;Username=postgres;Password=yourPassword",
    "Schema": "hangfire"
  },
  "Serilog": {
    "LokiUrl": "http://localhost:3100"
  }
}
```

## OAuth Provider Setup

### Google OAuth

- Use the same Web Client ID in the Android app and API.
- The API validates Google ID tokens using Authentication:Google:ClientId.
- If the client ID does not match, tokens are rejected.

### Microsoft OAuth (Azure Entra ID)

- Register the backend app in Microsoft Entra ID.
- Configure Authentication:Microsoft:ClientId, TenantId, ClientSecret, and ApiAudience.
- Expose the API scope access_as_user and request it from clients.

See the Android README for signature hash and redirect URI details.

## Websocket Messaging

- Endpoint: /ws/messaging
- Requires a valid Authorization Bearer token.
- Clients must include deviceId as a query string (deviceId=...).

The websocket handler delivers pending Kafka messages first, then forwards realtime messages. If a device is removed, only device removal events are delivered before the socket closes.

## OpenAPI + Scalar

- OpenAPI JSON: http://localhost:5004/openapi/v1.json
- Scalar UI: http://localhost:5004/scalar/v1

## Secrets and Safety

- Do not commit real secrets or private keys.
- Prefer environment variables or a secrets store in production.
- Keep appsettings.Development.json out of source control.
- The docker-compose.yaml values are for local development only.

## Cloud Deployment Notes

- Use managed PostgreSQL and configure ConnectionStrings\_\_DefaultConnection in your deployment environment.
- Store JWT signing keys and OAuth secrets in a secure secret manager.
- Provide Kafka certificates and SASL credentials via mounted secrets or a secure configuration service.
- Replace local Grafana/Loki with your preferred hosted observability stack.
