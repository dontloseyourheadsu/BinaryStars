# BinaryStars.Linux (Tauri)

BinaryStars Linux reproduces the Android app feature set with a responsive desktop/tablet UI:

- Devices (link/unlink, online/offline status)
- File transfers (send/list/download/reject)
- Notes (create/edit/delete, markdown preview)
- Messaging (chat list + device chat, websocket + REST fallback)
- Map (device location history + background location posting)
- Settings (profile, connected devices, dark mode, sign out)

## Toolchain (Latest Verified)

These versions were verified from npm/crates registries:

- `@tauri-apps/cli` `2.10.0`
- `@tauri-apps/api` `2.10.1`
- `tauri` crate `2.10.2`
- `@tauri-apps/plugin-opener` `2.5.3`

## Prerequisites (Linux)

Install Tauri Linux prerequisites first:

- `webkit2gtk`
- Rust toolchain
- Node.js 20+

Official prerequisites guide:

- https://tauri.app/start/prerequisites/

## Local Development

From `BinaryStars.Linux`:

```bash
npm install
npm run tauri dev
```

For frontend-only development:

```bash
npm run dev
```

## API Configuration

By default, the Linux app calls:

- `http://localhost:5004/api`

To change base URL, set:

- `VITE_API_BASE_URL`

For OAuth desktop setup (used by PKCE/browser flow), set:

- `VITE_GOOGLE_CLIENT_ID`
- `VITE_GOOGLE_REDIRECT_URI` (recommended loopback callback)
- `VITE_GOOGLE_CLIENT_SECRET` (optional; only needed if your Google OAuth client requires it during token exchange)
- `VITE_MICROSOFT_CLIENT_ID`
- `VITE_MICROSOFT_TENANT_ID` (use `common` or a specific tenant id)
- `VITE_MICROSOFT_REDIRECT_URI` (recommended loopback callback)
- `VITE_MICROSOFT_SCOPE` (optional; defaults to `api://<MICROSOFT_CLIENT_ID>/access_as_user openid profile email offline_access`)

Example:

```bash
VITE_API_BASE_URL=https://your-api-host/api npm run tauri dev
```

Example `.env.local`:

```bash
VITE_API_BASE_URL=http://localhost:5004/api
VITE_GOOGLE_CLIENT_ID=YOUR_GOOGLE_CLIENT_ID
VITE_GOOGLE_REDIRECT_URI=http://127.0.0.1:53123/callback
VITE_GOOGLE_CLIENT_SECRET=YOUR_GOOGLE_CLIENT_SECRET
VITE_MICROSOFT_CLIENT_ID=YOUR_MICROSOFT_CLIENT_ID
VITE_MICROSOFT_TENANT_ID=common
VITE_MICROSOFT_REDIRECT_URI=http://127.0.0.1:53124/callback
VITE_MICROSOFT_SCOPE=api://YOUR_MICROSOFT_CLIENT_ID/access_as_user openid profile email offline_access
```

Important:

- Prefer OAuth public clients + PKCE (no client secret) for desktop apps.
- If your Google credential type still requires `client_secret`, set `VITE_GOOGLE_CLIENT_SECRET` for local/dev use and keep it out of git.

## OAuth Setup (Google + Microsoft for Linux/Tauri)

BinaryStars Linux supports the same providers as Android. The current Linux client uses the API `/auth/login/external` exchange and expects you to provide a provider token.
BinaryStars Linux now supports desktop OAuth PKCE directly from the app for Google and Microsoft (open browser → complete provider sign-in → callback captured locally → token sent to API).

### 1) Google OAuth Setup

1. Open Google Cloud Console → **APIs & Services** → **Credentials**.
2. Create OAuth client credentials for your Linux desktop flow.
3. Configure redirect URI(s) for desktop browser sign-in (loopback is recommended for desktop apps, for example `http://127.0.0.1:53123/callback`).
4. Copy your Google client ID.

### 2) Microsoft OAuth Setup (Azure / Entra ID)

1. Open Azure Portal → **Microsoft Entra ID** → **App registrations**.
2. Select/create your app registration.
3. In **Authentication**, add a desktop/public client redirect URI.
4. In **Expose an API**, ensure your backend scope exists:
   - `api://c727b034-bd56-4e8a-a749-5ea51a9a1c73/access_as_user`
5. Grant required API permissions/consent for your test tenant.

### 3) Using OAuth in the Linux App

The login page includes 3 sign-in methods:

- Email/Password (`/api/auth/login`)
- Continue with Google (`/api/auth/login/external`)
- Continue with Microsoft (`/api/auth/login/external`)

For external providers, the app starts PKCE, opens the system browser, receives the loopback callback, exchanges the code for token, and then authenticates against your API.

If the external account does not exist yet, the app prompts for username and completes first-time registration through `/api/auth/login/external`.

The login page also includes:

- `Continue with Google`
- `Continue with Microsoft`

## Detailed OAuth (PKCE) desktop flow

The short prompt-based flow works for quick testing, but for a polished desktop experience you should implement the PKCE (Proof Key for Code Exchange) desktop flow and capture the provider authorization code automatically. High-level steps:

1. Build the authorization URL (example for Google):
   - `https://accounts.google.com/o/oauth2/v2/auth?client_id=YOUR_CLIENT_ID&response_type=code&scope=openid%20email%20profile&redirect_uri=http://127.0.0.1:PORT/callback&code_challenge=CODE_CHALLENGE&code_challenge_method=S256&access_type=offline`

2. Generate PKCE values in the app:
   - Create a random `code_verifier` (43-128 characters), then compute `code_challenge = base64url(SHA256(code_verifier))`.

3. Open the system browser to the authorization URL.
   - In Tauri you can use the opener plugin or `@tauri-apps/api/shell` to open the URL in the user's default browser.

4. Capture the redirect with a loopback HTTP listener or a custom URI scheme.
   - Loopback (recommended): start a tiny local HTTP server (listening on `127.0.0.1:PORT`) and register the redirect URI with the provider.
   - Custom URI scheme: register `myapp://auth/callback` as a redirect for packaged apps; this requires platform-specific registration during packaging.

5. Exchange the authorization `code` for a provider access token using the PKCE `code_verifier`:
   - POST to the provider token endpoint with `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id`, and `code_verifier`.

6. Send the provider token to your backend's exchange endpoint:
   - `POST /api/auth/login/external` with JSON body `{ provider: "google"|"microsoft", token: "<provider_access_token_or_id_token>" }`.

7. Backend returns the BinaryStars JWT; store it in the app (the existing client-side `tokenStore` handles this).

Notes & Tauri specifics:

- For development you can use an ephemeral loopback port (e.g., 53123). The provider must include that loopback URI in the OAuth client configuration.
- When packaging the app, update the redirect URI(s) accordingly (loopback URIs still work for desktop apps; custom URI schemes require OS registration).
- If you want a fully native UX, open the auth URL using the system browser and capture the provider redirect in the background server; do not embed the provider login in a webview unless explicitly allowed by the provider.
- You can automate the flow in the frontend by spawning a tiny HTTP listener (e.g., using `node:http` during development or a small Rust sidecar in Tauri) and then exchanging the code server-side.

Example resources and libraries:

- PKCE helper: `@openid/appauth` or implement PKCE with `crypto.subtle` (Web) / Rust `ring` (native).
- Tauri opener: `@tauri-apps/plugin-opener` or `@tauri-apps/api/shell` to open URLs in default browser.

Security reminders:

- Never embed client secrets in the desktop app. Use public/OAuth client flows (PKCE) without confidential secrets.
- Validate tokens and use refresh / revocation flows as appropriate on the backend.

## Notes

- Do not commit real OAuth secrets.
- Use secure environment variables for production builds.
- If packaging desktop binaries, verify OAuth redirect URIs for packaged app behavior.
