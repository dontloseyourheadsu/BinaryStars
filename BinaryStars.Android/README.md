# BinaryStars Android App

This is the Android client for the BinaryStars application.

## Development Setup

### Keystore Configuration

To successfully authenticate with Google Services (Maps, Sign-In, etc.) during development, the app needs to be signed with a specific debug key, and that key's fingerprint must be registered in the Google Cloud Console.

A local debug keystore has been generated in `BinaryStars.Android/debug.keystore`.

#### 1. Keystore Details

- **File Path**: `BinaryStars.Android/debug.keystore`
- **Alias**: `androiddebugkey`
- **Store Password**: `android`
- **Key Password**: `android`

#### 2. SHA-1 Fingerprint

Use the fingerprint from your debug keystore when configuring your **Android** OAuth Client ID in the Google Cloud Console.

To retrieve your SHA-1 fingerprint, run the following command:

```bash
keytool -list -v -keystore BinaryStars.Android/debug.keystore -alias androiddebugkey -storepass android
```

Look for the `SHA1` under `Certificate fingerprints`.

### Google OAuth Configuration (Android Client)

1.  Go to [Google Cloud Console](https://console.cloud.google.com/).
2.  Navigate to **APIs & Services > Credentials**.
3.  Create an **OAuth client ID**.
4.  Select **Android** as the Application type.
    - **Package name**: `com.binarystars.android` (Check `AndroidManifest.xml` or `build.gradle` to confirm package name).
    - **SHA-1 certificate fingerprint**: Paste the SHA-1 fingerprint from above.
5.  Click **Create**.
