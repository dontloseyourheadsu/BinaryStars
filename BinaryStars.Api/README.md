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
      "ClientId": "YOUR_WEB_CLIENT_ID",
      "ClientSecret": "YOUR_GOOGLE_CLIENT_SECRET"
    },
    "Microsoft": {
      "ClientId": "YOUR_CLIENT_ID",
      "TenantId": "YOUR_TENANT_ID",
      "ClientSecret": "YOUR_CLIENT_SECRET",
      "ApiAudience": "api://YOUR_CLIENT_ID"
    }
  },
  "Jwt": {
    "Issuer": "BinaryStars.Api",
    "Audience": "BinaryStars.Api",
    "SigningKey": "YOUR_LONG_RANDOM_SECRET",
    "ExpiresInMinutes": 120
  },
  "Serilog": {
    "LokiUrl": "http://localhost:3100"
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
- **Authentication:Google**:
  - **Important**: The `ClientId` here MUST MATCH the **Web Client ID** you use in your Android/Web clients.
  - When the Android app sends an ID Token, it was issued for that specific Web Client ID. The API validates it by checking if the token's audience (`aud`) matches this `ClientId`.
- **Authentication:Microsoft**: Settings for Microsoft Entra ID (Azure AD).
- **Jwt**: Settings for the BinaryStars API access token that the server issues after login.

## Setting up OAuth Providers

### Google OAuth Setup (Crucial for Client Validation)

For Google Sign-In to work across Android and API:

1.  **Do not create separate Client IDs for the API and the Client App for validation purposes.**
2.  Use the **Web Client ID** that you created in the Google Cloud Console.
    - This is the **same ID** you put in the Android `build.gradle` (`GOOGLE_WEB_CLIENT_ID`).
3.  Update `BinaryStars.Api/appsettings.json`:
    ```json
    "Authentication": {
      "Google": {
        "ClientId": "YOUR_SHARED_WEB_CLIENT_ID.apps.googleusercontent.com",
        "ClientSecret": "YOUR_CLIENT_SECRET"
      }
    }
    ```
    _If these IDs do not match, the API will reject the token with `Invalid audience`._

**Token flow:** Android obtains a **Google ID token** using the Web Client ID. The API validates that ID token and then issues a **BinaryStars API JWT** for app access.

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
7.  **Expose an API** for your backend:
    - Go to **Expose an API**.
    - Set the **Application ID URI** to `api://<YOUR_CLIENT_ID>`.
    - Add a scope named `access_as_user`.
8.  Update `BinaryStars.Api/appsettings.json`:
    ```json
    "Authentication": {
      "Microsoft": {
        "ClientId": "YOUR_CLIENT_ID",
        "TenantId": "YOUR_TENANT_ID",
        "ClientSecret": "YOUR_CLIENT_SECRET",
        "ApiAudience": "api://YOUR_CLIENT_ID"
      }
    }
    ```

**Token flow:** Android requests an **access token** for the API scope `api://<YOUR_CLIENT_ID>/access_as_user`. The API validates that access token and then issues a **BinaryStars API JWT** for app access.

## Running the API

1.  Navigate to the directory: `cd BinaryStars.Api`
2.  Run the application: `dotnet run`

### API Reference (Scalar UI)

A visual API reference is available at:

- **URL**: `http://localhost:5004/scalar/v1`

### OpenAPI Specification

The raw OpenAPI specification is at:

- **URL**: `http://localhost:5004/openapi/v1.json`

You can import this URL into tools like **Postman** or **Insomnia** to automatically generate client requests.

## API Usage

All routes are prefixed with `/api`. Authenticated routes require a Bearer token.

### Authentication Endpoints

These endpoints return a **BinaryStars API JWT** after a successful login or registration.

#### 1. Register a new user

- **URL**: `/api/auth/register`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "username": "your_username",
    "email": "user@example.com",
    "password": "StrongPassword123!"
  }
  ```

#### 2. Login

Obtain a Bearer token to access protected resources.

- **URL**: `/api/auth/login`
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
    "expiresIn": 7200
  }
  ```

#### 3. External Login

- **URL**: `/api/auth/login/external`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "provider": "Google",
    "token": "<provider-id-token>",
    "username": "optional_username"
  }
  ```

### Accounts Endpoints (Authenticated)

#### 1. Get current user profile

- **URL**: `/api/accounts/me`
- **Method**: `GET`

### Devices Endpoints (Authenticated)

#### 1. List devices

- **URL**: `/api/devices`
- **Method**: `GET`

#### 2. Register device

- **URL**: `/api/devices/register`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "id": "<device-id>",
    "name": "<device-name>",
    "ipAddress": "192.168.1.10",
    "ipv6Address": "fe80::1",
    "publicKey": "<base64-public-key>",
    "publicKeyAlgorithm": "RSA"
  }
  ```

#### 3. Unlink device

- **URL**: `/api/devices/{deviceId}`
- **Method**: `DELETE`

### Notes Endpoints (Authenticated)

#### 1. List notes

- **URL**: `/api/notes`
- **Method**: `GET`

#### 2. List notes for a device

- **URL**: `/api/notes/device/{deviceId}`
- **Method**: `GET`

#### 3. Get note by id

- **URL**: `/api/notes/{noteId}`
- **Method**: `GET`

#### 4. Create note

- **URL**: `/api/notes`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "name": "<note-title>",
    "deviceId": "<device-id>",
    "contentType": "Text",
    "content": "<note-body>"
  }
  ```

#### 5. Update note

- **URL**: `/api/notes/{noteId}`
- **Method**: `PUT`
- **Body**:
  ```json
  {
    "name": "<note-title>",
    "content": "<note-body>"
  }
  ```

#### 6. Delete note

- **URL**: `/api/notes/{noteId}`
- **Method**: `DELETE`

### File Transfer Endpoints (Authenticated)

#### 1. List transfers

- **URL**: `/api/files/transfers`
- **Method**: `GET`

#### 2. List pending transfers for a device

- **URL**: `/api/files/transfers/pending?deviceId={deviceId}`
- **Method**: `GET`

#### 3. Get transfer by id

- **URL**: `/api/files/transfers/{transferId}`
- **Method**: `GET`

#### 4. Create transfer

- **URL**: `/api/files/transfers`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "fileName": "example.bin",
    "contentType": "application/octet-stream",
    "sizeBytes": 123456,
    "senderDeviceId": "<device-id>",
    "targetDeviceId": "<device-id>",
    "encryptionEnvelope": "<optional-envelope>"
  }
  ```

#### 5. Upload file bytes

- **URL**: `/api/files/transfers/{transferId}/upload`
- **Method**: `PUT`
- **Body**: Raw file bytes in the request body.

#### 6. Download file bytes

- **URL**: `/api/files/transfers/{transferId}/download?deviceId={deviceId}`
- **Method**: `GET`
- **Response headers**:
  - `Content-Disposition`: includes original filename.
  - `X-Transfer-Envelope`: base64-encoded envelope when present.
  - `X-Transfer-ChunkSize`: chunk size used during streaming.

#### 7. Reject transfer

- **URL**: `/api/files/transfers/{transferId}/reject?deviceId={deviceId}`
- **Method**: `POST`

### Debug Endpoints (Development Only)

#### 1. Decode a JWT (no validation)

- **URL**: `/api/debug/token`
- **Method**: `POST`
- **Body**:
  ```json
  {
    "token": "<jwt>"
  }
  ```

### Using the Token

To access protected endpoints, include the `accessToken` in the `Authorization` header:

````
Authorization: Bearer <your-api-access-token>

**Azure: Expose an API (Add a scope)**

When you open the **Expose an API** blade for your backend app in the Azure Portal, click **Add a scope** and fill the form as follows:

- **Scope name**: `access_as_user`
- **Who can consent?**: `Admins and users`
- **Admin consent display name**: `Access BinaryStars API`
- **Admin consent description**: `Allows the app to access the BinaryStars API on behalf of the signed-in user.`
- **User consent display name**: `Access BinaryStars API`
- **User consent description**: `Allow this app to access the BinaryStars API on your behalf.`
- **State**: `Enabled`

After creating the scope:

- Note the full scope string you will request from clients: `api://c727b034-bd56-4e8a-a749-5ea51a9a1c73/access_as_user` (replace the GUID with your Application (client) ID if different).
- Optionally click **Add a client application** to authorize your Android/Web client applications directly.
- If you want tenant-wide consent, use **Grant admin consent** in **API permissions** or the Enterprise Applications blade to approve the permission for all users.

Update `BinaryStars.Api/appsettings.json`:

```json
  "Authentication": {
    "Microsoft": {
      "ClientId": "<YOUR_CLIENT_ID>",
      "TenantId": "<YOUR_TENANT_ID>",
      "ApiAudience": "api://<YOUR_CLIENT_ID>"
    }
  },
  "Jwt": {
    "Issuer": "BinaryStars.Api",
    "Audience": "BinaryStars.Api",
    "SigningKey": "<ADD_LONG_RANDOM_SIGNING_KEY>",
    "ExpiresInMinutes": 120
  }
````

Notes:

- Android should request the scope `api://<YOUR_CLIENT_ID>/access_as_user` when signing in (see the Android README and `LoginActivity.kt`).
- Make sure your Android app's MSAL config includes the correct redirect URI (raw, unencoded signature hash) and the app registration contains the Android platform entry with that signature hash.
- The API will validate incoming Microsoft tokens against the `ApiAudience` and issuer configured in the `ExternalIdentityValidator` logic.

```

```
