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

## Setting up OAuth Providers

### Google OAuth Setup

1.  Go to the [Google Cloud Console](https://console.cloud.google.com/).
2.  Create a new project or select an existing one.
3.  Navigate to **APIs & Services** > **OAuth consent screen**.
    - Choose **External** (unless you are in a Google Workspace organization).
    - Fill in the required app information.
    - Add your developer contact information.
4.  Navigate to **Credentials**.
5.  Click **Create Credentials** > **OAuth client ID**.
6.  For the **Application type**, select **Web application** (for the API backing web/mobile clients).
    - **Name**: `BinaryStars API`
    - **Authorized redirect URIs**:
      - For local development: `https://localhost:5001/signin-google` (or whatever port your API uses).
      - For Postman/Swagger: You might need specific callback URLs depending on how you test.
7.  Click **Create**.
8.  Copy the **Client ID** and **Client Secret**.
9.  Update `BinaryStars.Api/appsettings.json`:
    ```json
    "Authentication": {
      "Google": {
        "ClientId": "YOUR_OBTAINED_CLIENT_ID",
        "ClientSecret": "YOUR_OBTAINED_CLIENT_SECRET"
      }
    }
    ```

### Microsoft OAuth (Azure AD) Setup

1.  Go to the [Azure Portal](https://portal.azure.com/).
2.  Search for and select **Microsoft Entra ID** (formerly Azure Active Directory).
3.  Navigate to **App registrations** > **New registration**.
    - **Name**: `BinaryStars API`
    - **Supported account types**: Choose who can use this application (e.g., "Accounts in any organizational directory (Any Microsoft Entra ID tenant - Multitenant) and personal Microsoft accounts").
    - **Redirect URI**: Select **Web** and enter `https://localhost:5001/signin-oidc`.
4.  Click **Register**.
5.  On the **Overview** page, copy the:
    - **Application (client) ID** -> This is your `ClientId`.
    - **Directory (tenant) ID** -> This is your `TenantId`.
6.  (Optional for simple auth, but likely needed) Navigate to **Certificates & secrets** > **New client secret**. Copy the **Value** (not the Secret ID).
7.  Update `BinaryStars.Api/appsettings.json`:
    ```json
    "AzureAd": {
      "Instance": "https://login.microsoftonline.com/",
      "Domain": "your-domain.onmicrosoft.com", // Optional for multi-tenant
      "TenantId": "YOUR_TENANT_ID", // or "common" for multi-tenant
      "ClientId": "YOUR_CLIENT_ID",
      "Scopes": "access_as_user",
      "CallbackPath": "/signin-oidc"
    }
    ```

## Running the API

1.  Navigate to the directory: `cd BinaryStars.Api`
2.  Run the application: `dotnet run`

### API Reference (Scalar UI)

A visual API reference is available at:

- **URL**: `http://localhost:5000/scalar/v1`

### OpenAPI Specification

The raw OpenAPI specification is at:

- **URL**: `http://localhost:5000/openapi/v1.json`

You can import this URL into tools like **Postman** or **Insomnia** to automatically generate client requests.

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
