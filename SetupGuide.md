# Solution Setup & Running Guide

This guide describes how to run the backend and connect to it from the Android emulator or device.

## Prerequisites

- .NET 10 SDK
- Docker & Docker Compose
- Android Studio & Emulator (or physical device)

## 1. Running the Backend

The backend consists of the API and a PostgreSQL database.

### Using Docker Compose (Recommended)

To run the entire backend stack (API + DB):

```bash
docker-compose up --build
```

The API will be available at: `http://localhost:5004` (mapped to port 8080 inside container).
The Database will be available at: `localhost:5432`.

### Running Locally (Development)

If you prefer running the API outside of Docker (but DB in Docker):

1. Start only the database:
   ```bash
   docker-compose up binarystars.db -d
   ```
2. Run the API:
   ```bash
   dotnet run --project BinaryStars.Api/BinaryStars.Api.csproj --launch-profile https
   ```

## 2. Connecting from Android Emulator

The Android emulator routes `10.0.2.2` to the host machine's `localhost`.

- **API URL**: `http://10.0.2.2:5004/api/`
- **Configuration**: The `ApiClient.kt` file in the Android project is already configured with this URL:
  ```kotlin
  private const val BASE_URL = "http://10.0.2.2:5004/api/"
  ```

### Authentication Flow (Simplified)

1. **Register**: `/api/auth/register` (POST)
2. **Login**: `/api/auth/login` (POST) - Returns a cookie (Identity)
3. **Devices**: `/api/devices` (GET) - Requires Authentication

## 3. Connecting from Physical Android Device

To connect a physical device, both your computer and phone must be on the same Wi-Fi network.

1. Find your computer's local IP address (e.g., `192.168.1.100`).
2. Update `ApiClient.kt` in the Android project:
   ```kotlin
   private const val BASE_URL = "http://192.168.1.100:5004/api/"
   ```
3. Update `BinaryStars.Api/launchSettings.json` or Docker configuration to bind to `0.0.0.0` or allow external connections.
   - For Docker, it's already binding to `0.0.0.0:5004` by default with port mapping.
   - For local `dotnet run`, use `dotnet run --urls "http://0.0.0.0:5004"`.
4. Allow port 5004 through your computer's firewall.

## 4. Warnings treated as Errors

The solution is configured to treat all warnings as errors (`Directory.Build.props`). Ensure code is clean before building.

```bash
dotnet build
```
