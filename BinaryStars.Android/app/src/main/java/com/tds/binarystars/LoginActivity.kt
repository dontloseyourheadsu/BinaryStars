package com.tds.binarystars

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
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
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val logTag = "BinaryStarsLogin"

    private lateinit var credentialManager: CredentialManager
    private var msalApp: ISingleAccountPublicClientApplication? = null

    // Configuration values are available via BuildConfig
    // BuildConfig.GOOGLE_WEB_CLIENT_ID
    // BuildConfig.MICROSOFT_CLIENT_ID
    // BuildConfig.MICROSOFT_TENANT_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Auto-login if a valid token is stored
        if (!AuthTokenStore.getToken().isNullOrBlank()) {
            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_login)

        // Agrega esto en onCreate para ver el hash real
        try {
            val info = packageManager.getPackageInfo(
                "com.tds.binarystars", // Tu package name
                android.content.pm.PackageManager.GET_SIGNATURES
            )
            for (signature in info.signatures) {
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
                    Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace()
                }
            }
        }

        tvRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnLoginGoogle.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val googleIdOption = GetGoogleIdOption.Builder()
                        .setFilterByAuthorizedAccounts(false)
                        .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
                        .setAutoSelectEnabled(false)
                        .setNonce(null) // Optional: provide a nonce if your server requires it
                        .build()

                    val request = GetCredentialRequest.Builder()
                        .addCredentialOption(googleIdOption)
                        .build()

                    val result = credentialManager.getCredential(
                        request = request,
                        context = this@LoginActivity
                    )

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
                    toast("Google sign-in failed: ${e.message}")
                } catch (e: Exception) {
                    Log.e(logTag, "Google sign-in error", e)
                    toast("Google sign-in error: ${e.message}")
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

            app.signIn(this, null, scopes, object : AuthenticationCallback {
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
        }
    }

    private fun submitExternalLogin(provider: String, token: String) {
        lifecycleScope.launch {
            try {
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
                    Log.w(logTag, "External login returned 401: $errorBody")
                    
                    if (errorBody != null && errorBody.contains("Registration required")) {
                         val intent = Intent(this@LoginActivity, UsernameInputActivity::class.java)
                         intent.putExtra("EXTRA_PROVIDER", provider)
                        intent.putExtra("EXTRA_TOKEN", token)
                         startActivity(intent)
                    } else {
                        toast("Login Failed: Authentication rejected")
                    }
                } else {
                    Log.w(logTag, "External login failed for provider=$provider status=${response.code()}")
                    toast("Login Failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e(logTag, "External login error for provider=$provider", e)
                toast("Error: ${e.message}")
            }
        }
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
