# BinaryStars API

This is the API backend for the BinaryStars application. It is built with .NET 10.

## Configuration

The application is configured using `appsettings.json`. You can also override these settings using environment variables (e.g., `ConnectionStrings__DefaultConnection`).

### appsettings.json Structure

Create a `appsettings.json` (or `appsettings.Development.json` for local dev) in the `BinaryStars.Api` directory with the following structure:

```json
{
  "ConnectionStrings": {
    "DefaultConnection": "Host=localhost;Database=binarystars;Username=postgres;Password=yourPassword"
  },
  "Authentication": {
    "Google": {
      "ClientId": "YOUR_GOOGLE_CLIENT_ID",
      "ClientSecret": "YOUR_GOOGLE_CLIENT_SECRET"
    }
  },
  "Serilog": {
    "LokiUrl": "http://localhost:3100"
  },
  "AzureAd": {
    "Instance": "https://login.microsoftonline.com/",
    "Domain": "your-domain.onmicrosoft.com",
    "TenantId": "YOUR_TENANT_ID",
    "ClientId": "YOUR_CLIENT_ID",
    "Scopes": "access_as_user",
    "CallbackPath": "/signin-oidc"
  },
  "Logging": {
    "LogLevel": {
      "Default": "Information",
      "Microsoft.AspNetCore": "Warning"
    }
  },
  "AllowedHosts": "*"
}
```

### Key Sections:

- **ConnectionStrings:DefaultConnection**: Your PostgreSQL connection string. Ensure you have a running PostgreSQL instance. The app will attempt to create the database schema on startup if it doesn't exist.
- **Authentication:Google**: Get these credentials from the Google Cloud Console (APIs & Services -> Credentials).
- **Serilog:LokiUrl**: The URL for your Grafana Loki instance for log aggregation.
- **AzureAd**: Settings for Microsoft Identity Web if you are using Azure AD auth.

## Running the API

1.  Navigate to the directory: `cd BinaryStars.Api`
2.  Run the application: `dotnet run`

## API Usage

The API currently exposes **Identity** endpoints for user management and authentication.

### Authentication Endpoints

These endpoints are provided by ASP.NET Core Identity.

#### 1. Register a new user

- **URL**: `/register`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPassword123!"
  }
  ```

#### 2. Login

Obtain a Bearer token to access protected resources.

- **URL**: `/login`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "email": "user@example.com",
    "password": "StrongPassword123!"
  }
  ```
- **Response**:
  ```json
  {
    "tokenType": "Bearer",
    "accessToken": "eyJhbGciOiJIUzI1NiIs...",
    "expiresIn": 3600,
    "refreshToken": "..."
  }
  ```

#### 3. Refresh Token

- **URL**: `/refresh`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "refreshToken": "..."
  }
  ```

### Using the Token

To access protected endpoints (once they are created), include the `accessToken` in the `Authorization` header:

```
Authorization: Bearer <your-access-token>
```
