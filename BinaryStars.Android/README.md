# BinaryStars.Android

## OAuth Setup Instructions

### Step-by-Step

**1. Create the Client IDs in Google Cloud Console**

You need **two** Client IDs in the same Google Cloud Project for the integration to work on Android (even though we only use one in the code).

**A. Create the Android Client ID (Required for Authorization):**

1. Go to Google Cloud Console.
2. Click **Create Credentials** -> **OAuth client ID**.
3. Select **Android**.
4. Enter the Package name: `com.tds.binarystars`.
5. Enter the **SHA-1 certificate fingerprint** of your debug keystore.
   - Run `./gradlew signingReport` in the `BinaryStars.Android` directory to find this.
6. Click **Create**.
   - _Note: You do not use this Client ID string in your code, but it MUST exist to authorize your app._

**B. Create the Web Client ID (Required for Token Exchange):**

1. Click **Create Credentials** -> **OAuth client ID**.
2. Select **Web application**.
3. Name it "Web Client for Backend".
4. Click **Create**.
5. Copy the **Client ID** (ends in `.apps.googleusercontent.com`).
   - _This IS the Client ID you will use in your `build.gradle`._

**2. Update your `build.gradle`**
Replace the value in your `defaultConfig` with the **Web** Client ID you just created.

```kotlin
defaultConfig {
    // ...
    // REPLACE this with the NEW Web Client ID you just created
    buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"YOUR_NEW_WEB_CLIENT_ID.apps.googleusercontent.com\"")
    // ...
}
```

## Managing Signing Keys

### View SHA-1 Fingerprint (for existing keystore)

To find the SHA-1 fingerprint required for the Google Cloud Console "Android Client ID":

This command will print the signing details for all variants. Look for the `debug` variant SHA-1.

```bash
./gradlew signingReport
```

### Generate a New Debug Keystore

If you do not have a debug keystore or need to generate a specific one:

```bash
keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000
```

- **Password**: `android`
- **Alias**: `androiddebugkey`
- **Location**: Typically placed in `~/.android/debug.keystore` or inside your project `app/` folder depending on your configuration.
