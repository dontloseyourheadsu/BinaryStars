package com.tds.binarystars

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.textfield.TextInputEditText
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.ExternalAuthRequest
import com.tds.binarystars.api.LoginRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private val logTag = "BinaryStarsLogin"
    private val credentialManagerTimeoutMs = 10_000L
    private var googleSignInInProgress = false
    private var googleFallbackCountdownJob: Job? = null
    private var defaultGoogleButtonText: CharSequence = ""

    private lateinit var credentialManager: CredentialManager
    private var msalApp: ISingleAccountPublicClientApplication? = null
    private val legacyGoogleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        try {
            if (result.resultCode != RESULT_OK) {
                Log.d(logTag, "Legacy Google sign-in canceled, resultCode=${result.resultCode}")
                return@registerForActivityResult
            }

            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken

            if (idToken.isNullOrBlank()) {
                Log.e(logTag, "Legacy Google sign-in returned empty idToken")
                toast("Google sign-in failed: missing ID token")
                return@registerForActivityResult
            }

            Log.d(logTag, "Legacy Google sign-in succeeded; submitting token")
            submitExternalLogin("google", idToken)
        } catch (e: ApiException) {
            Log.e(logTag, "Legacy Google sign-in API error code=${e.statusCode}", e)
            toast("Google sign-in failed: API ${e.statusCode}")
        } catch (e: Exception) {
            Log.e(logTag, "Legacy Google sign-in error", e)
            toast("Google sign-in failed: ${e.message}")
        } finally {
            googleSignInInProgress = false
            Log.d(logTag, "Legacy Google sign-in flow finished")
        }
    }

    // Configuration values are available via BuildConfig
    // BuildConfig.GOOGLE_WEB_CLIENT_ID
    // BuildConfig.MICROSOFT_CLIENT_ID
    // BuildConfig.MICROSOFT_TENANT_ID

    /**
     * Initializes login flows for password, Google, and Microsoft providers.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login whenever a persisted session exists.
        // API calls will refresh/replace the token as responses arrive.
        if (AuthTokenStore.hasStoredSession()) {
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        // Agrega esto en onCreate para ver el hash real
        try {
            val packageName = packageName
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
            val info = packageManager.getPackageInfo(packageName, flags)
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = info.signingInfo
                if (signingInfo == null) {
                    emptyArray()
                } else if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners ?: emptyArray()
                } else {
                    signingInfo.signingCertificateHistory ?: emptyArray()
                }
            } else {
                @Suppress("DEPRECATION")
                info.signatures ?: emptyArray()
            }
            for (signature in signatures) {
                val md = java.security.MessageDigest.getInstance("SHA")
                md.update(signature.toByteArray())
                val hash = android.util.Base64.encodeToString(md.digest(), android.util.Base64.NO_WRAP)
                Log.e("MY_HASH", "Hash real de la app: $hash")
                // ¡Compara este valor con el que tienes en el JSON!
            }
        } catch (e: Exception) {
            Log.e("MY_HASH", "Error obteniendo hash", e)
        }

        credentialManager = CredentialManager.create(this)

        PublicClientApplication.createSingleAccountPublicClientApplication(
            this,
            R.raw.auth_config_single_account,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                }

                override fun onError(exception: MsalException) {
                    toast("Microsoft auth init failed: ${exception.errorCode}")
                }
            }
        )

        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnLoginGoogle = findViewById<Button>(R.id.btnLoginGoogle)
        val btnLoginMicrosoft = findViewById<Button>(R.id.btnLoginMicrosoft)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        defaultGoogleButtonText = btnLoginGoogle.text

        btnLogin.setOnClickListener {
            val emailOrUsername = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (emailOrUsername.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.login(LoginRequest(emailOrUsername, password))
                    if (response.isSuccessful) {
                        response.body()?.let { AuthTokenStore.setToken(it.accessToken, it.expiresIn) }
                        Toast.makeText(this@LoginActivity, "Login Successful", Toast.LENGTH_SHORT).show()
                        startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                        finish()
                    } else {
                        Toast.makeText(this@LoginActivity, "Login Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    val message = if (!com.tds.binarystars.util.NetworkUtils.isOnline(this@LoginActivity)) {
                        "No connection available"
                    } else {
                        "Error: ${e.message}"
                    }
                    Toast.makeText(this@LoginActivity, message, Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLoginGoogle.setOnClickListener {
            Log.d(logTag, "Google button tapped")
            if (googleSignInInProgress) {
                Log.d(logTag, "Google sign-in ignored because another request is in progress")
                return@setOnClickListener
            }

            googleSignInInProgress = true
            startGoogleFallbackCountdown(btnLoginGoogle)
            lifecycleScope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .setAutoSelectEnabled(false)
                        .setNonce(null) // Optional: provide a nonce if your server requires it
                        .build()

                    Log.d(logTag, "Starting Google credential request with configured web client id")

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = withTimeout(credentialManagerTimeoutMs) {
                        credentialManager.getCredential(
                            request = request,
                            context = this@LoginActivity
                        )
                    }

                    Log.d(logTag, "Google credential returned type=${result.credential.type}")

                    val credential = result.credential
                    if (credential is CustomCredential &&
                        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                        
                        val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                        val idToken = googleIdTokenCredential.idToken
                        val email = googleIdTokenCredential.id // Email is ID in google token credential
                        
                        Log.d(logTag, "Google login success: email=$email")
                        submitExternalLogin("google", idToken)
                    } else {
                        Log.e(logTag, "Unexpected credential type: ${credential.type}")
                        toast("Google sign-in failed: Unexpected credential type")
                    }
                } catch (e: GetCredentialException) {
                    Log.e(logTag, "Google sign-in failed", e)
                    toast("Google sign-in issue. Opening fallback...")
                    startLegacyGoogleSignInFallback("credentials_exception")
                } catch (e: TimeoutCancellationException) {
                    Log.e(logTag, "Google sign-in timed out waiting for credential UI/result", e)
                    toast("Google sign-in is taking too long. Opening fallback...")
                    startLegacyGoogleSignInFallback("timeout")
                } catch (e: CancellationException) {
                    Log.d(logTag, "Google sign-in canceled/interrupted", e)
                    googleSignInInProgress = false
                    stopGoogleFallbackCountdown(btnLoginGoogle)
                } catch (e: Exception) {
                    Log.e(logTag, "Google sign-in error", e)
                    toast("Google sign-in issue. Opening fallback...")
                    startLegacyGoogleSignInFallback("unexpected_exception")
                } finally {
                    Log.d(logTag, "Google credential-manager flow finished")
                }
            }
        }

        btnLoginMicrosoft.setOnClickListener {
            // Request access token for the BinaryStars API scope
            val apiScope = "api://${BuildConfig.MICROSOFT_CLIENT_ID}/access_as_user"
            val scopes = arrayOf(apiScope, "openid", "profile")
            Log.d(logTag, "Starting Microsoft sign-in")

            val app = msalApp
            if (app == null) {
                toast("Microsoft auth not initialized")
                return@setOnClickListener
            }

            val signInParameters = com.microsoft.identity.client.SignInParameters
                .builder()
                .withActivity(this)
                .withScopes(scopes.toList())
                .withCallback(object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                    // 2. FIX: Use accessToken. The 'claims' map does NOT contain the raw ID token.
                    // If your backend specifically needs the ID token string, note that MSAL Android
                    // abstracts it away. Usually, the accessToken is sufficient for backend auth.
                    val tokenToSend = authenticationResult?.accessToken

                    if (tokenToSend.isNullOrEmpty()) {
                        toast("Microsoft sign-in failed: missing token")
                        return
                    }
                    // Send the access token to your backend
                    submitExternalLogin("microsoft", tokenToSend)
                }

                override fun onError(exception: MsalException) {
                    // If this prints "invalid_parameter", check Azure Portal Hash vs Logcat Hash
                    Log.e(logTag, "MSAL error: ${exception.errorCode} : ${exception.message}", exception)
                    toast("Microsoft sign-in failed: ${exception.errorCode}")
                }

                override fun onCancel() {
                    Log.d(logTag, "MSAL sign-in canceled")
                }
            })
                .build()

            app.signIn(signInParameters)
        }
    }

    /**
     * Sends an external provider token to the API and handles registration flow.
     */
    private fun submitExternalLogin(provider: String, token: String) {
        lifecycleScope.launch {
            try {
                Log.d(logTag, "Submitting external login to API for provider=$provider tokenLength=${token.length}")
                // Initial login with empty username
                val response = ApiClient.apiService.externalLogin(ExternalAuthRequest(provider, token, ""))
                if (response.isSuccessful) {
                    response.body()?.let { AuthTokenStore.setToken(it.accessToken, it.expiresIn) }
                    Log.d(logTag, "External login succeeded for provider=$provider")
                    toast("Login Successful")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else if (response.code() == 401) {
                    // Possible registration required
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = parseApiErrorMessage(errorBody)
                    Log.w(logTag, "External login returned 401: $errorBody")
                    
                    if ((errorMessage ?: "").contains("Registration required", ignoreCase = true)) {
                         val intent = Intent(this@LoginActivity, UsernameInputActivity::class.java)
                         intent.putExtra("EXTRA_PROVIDER", provider)
                        intent.putExtra("EXTRA_TOKEN", token)
                         startActivity(intent)
                    } else {
                        toast("Login failed: ${errorMessage ?: "Authentication rejected"}")
                    }
                } else {
                    val errorBody = response.errorBody()?.string()
                    val errorMessage = parseApiErrorMessage(errorBody)
                    Log.w(logTag, "External login failed for provider=$provider status=${response.code()}")
                    toast("Login failed (${response.code()}): ${errorMessage ?: "Unknown server error"}")
                }
            } catch (e: Exception) {
                Log.e(logTag, "External login error for provider=$provider", e)
                if (!com.tds.binarystars.util.NetworkUtils.isOnline(this@LoginActivity)) {
                    toast("No connection available")
                } else {
                    toast("Error: ${e.message}")
                }
            } finally {
                if (provider.equals("google", ignoreCase = true)) {
                    googleSignInInProgress = false
                    val button = findViewById<Button>(R.id.btnLoginGoogle)
                    stopGoogleFallbackCountdown(button)
                }
            }
        }
    }

    private fun startLegacyGoogleSignInFallback(reason: String) {
        Log.w(logTag, "Falling back to legacy Google Sign-In; reason=$reason")
        try {
            val button = findViewById<Button>(R.id.btnLoginGoogle)
            stopGoogleFallbackCountdown(button)

            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                .build()

            val client = GoogleSignIn.getClient(this, options)
            legacyGoogleSignInLauncher.launch(client.signInIntent)
        } catch (e: Exception) {
            Log.e(logTag, "Failed to start legacy Google sign-in fallback", e)
            toast("Google sign-in failed: ${e.message}")
            googleSignInInProgress = false
            val button = findViewById<Button>(R.id.btnLoginGoogle)
            stopGoogleFallbackCountdown(button)
        }
    }

    private fun startGoogleFallbackCountdown(button: Button) {
        stopGoogleFallbackCountdown(button)

        googleFallbackCountdownJob = lifecycleScope.launch {
            var secondsLeft = (credentialManagerTimeoutMs / 1000).toInt()
            while (secondsLeft > 0 && googleSignInInProgress) {
                button.text = "$defaultGoogleButtonText (fallback in ${secondsLeft}s)"
                delay(1000)
                secondsLeft -= 1
            }
            if (googleSignInInProgress) {
                button.text = "$defaultGoogleButtonText (opening fallback...)"
            }
        }
    }

    private fun stopGoogleFallbackCountdown(button: Button) {
        googleFallbackCountdownJob?.cancel()
        googleFallbackCountdownJob = null
        button.text = defaultGoogleButtonText
    }

    private fun parseApiErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) {
            return null
        }

        return try {
            val trimmed = raw.trim()
            when {
                trimmed.startsWith("[") -> {
                    val array = JSONArray(trimmed)
                    (0 until array.length())
                        .mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
                        .joinToString("; ")
                        .ifBlank { null }
                }
                trimmed.startsWith("{") -> {
                    val obj = JSONObject(trimmed)
                    when {
                        obj.has("errors") -> {
                            val errors = obj.optJSONArray("errors")
                            if (errors != null) {
                                (0 until errors.length())
                                    .mapNotNull { index -> errors.optString(index).takeIf { it.isNotBlank() } }
                                    .joinToString("; ")
                                    .ifBlank { trimmed }
                            } else {
                                obj.optString("errors", trimmed)
                            }
                        }
                        obj.has("message") -> obj.optString("message", trimmed)
                        else -> trimmed
                    }
                }
                else -> trimmed
            }
        } catch (_: Exception) {
            raw
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
