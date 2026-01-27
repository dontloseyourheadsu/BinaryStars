package com.tds.binarystars

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.textfield.TextInputEditText
import com.microsoft.identity.client.AuthenticationCallback
import com.microsoft.identity.client.IAuthenticationResult
import com.microsoft.identity.client.IPublicClientApplication
import com.microsoft.identity.client.ISingleAccountPublicClientApplication
import com.microsoft.identity.client.PublicClientApplication
import com.microsoft.identity.client.exception.MsalException
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.ExternalAuthRequest
import com.tds.binarystars.api.LoginRequest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private val logTag = "BinaryStarsLogin"

    private lateinit var googleSignInClient: GoogleSignInClient
    private var msalApp: ISingleAccountPublicClientApplication? = null

    private val googleSignInLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val task = GoogleSignIn.getSignedInAccountFromIntent(data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            val usernameInput = findViewById<TextInputEditText>(R.id.etUsername).text?.toString().orEmpty()
            val username = if (usernameInput.isNotBlank()) usernameInput else account?.email.orEmpty()
            if (idToken.isNullOrEmpty()) {
                toast("Google sign-in failed: missing token")
                return@registerForActivityResult
            }
            if (username.isBlank()) {
                toast("Username is required for Google sign-in")
                return@registerForActivityResult
            }
            submitExternalLogin("google", idToken, username)
        } catch (e: Exception) {
            toast("Google sign-in failed: ${e.message}")
        }
    }

    // Configuration values are available via BuildConfig
    // BuildConfig.GOOGLE_WEB_CLIENT_ID
    // BuildConfig.MICROSOFT_CLIENT_ID
    // BuildConfig.MICROSOFT_TENANT_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        googleSignInClient = GoogleSignIn.getClient(this, GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .build())

        PublicClientApplication.createSingleAccountPublicClientApplication(
            this,
            R.raw.msal_config,
            object : IPublicClientApplication.ISingleAccountApplicationCreatedListener {
                override fun onCreated(application: ISingleAccountPublicClientApplication) {
                    msalApp = application
                }

                override fun onError(exception: MsalException) {
                    toast("Microsoft auth init failed: ${exception.errorCode}")
                }
            }
        )

        val etUsername = findViewById<TextInputEditText>(R.id.etUsername)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnLoginGoogle = findViewById<Button>(R.id.btnLoginGoogle)
        val btnLoginMicrosoft = findViewById<Button>(R.id.btnLoginMicrosoft)
        val tvRegister = findViewById<TextView>(R.id.tvRegister)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.login(LoginRequest(email, password))
                    if (response.isSuccessful) {
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
            val intent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(intent)
        }

        btnLoginMicrosoft.setOnClickListener {
            val usernameInput = etUsername.text?.toString().orEmpty()

            val scopes = arrayOf("openid", "profile", "email")
            Log.d(logTag, "Starting Microsoft sign-in (usernameInput=${usernameInput.isNotBlank()}, scopes=${scopes.joinToString()})")

            val app = msalApp
            if (app == null) {
                toast("Microsoft auth not initialized")
                return@setOnClickListener
            }

            app.signIn(this, null, scopes, object : AuthenticationCallback {
                override fun onSuccess(authenticationResult: IAuthenticationResult?) {
                    // MSAL Android exposes the ID token via account claims; extract it directly
                    val idToken = authenticationResult?.account?.claims?.get("id_token") as? String
                    val claims = authenticationResult?.account?.claims
                    val derivedUsername = when {
                        !usernameInput.isBlank() -> usernameInput
                        claims?.get("preferred_username") is String -> claims["preferred_username"] as String
                        claims?.get("email") is String -> claims["email"] as String
                        claims?.get("upn") is String -> claims["upn"] as String
                        else -> ""
                    }
                    Log.d(logTag, "MSAL success: claims keys=${claims?.keys?.joinToString()} idTokenPresent=${!idToken.isNullOrEmpty()}")

                    if (idToken.isNullOrEmpty()) {
                        toast("Microsoft sign-in failed: missing token")
                        return
                    }
                    if (derivedUsername.isBlank()) {
                        toast("Username is required for Microsoft sign-in")
                        return
                    }
                    submitExternalLogin("microsoft", idToken, derivedUsername)
                }

                override fun onError(exception: MsalException) {
                    Log.e(logTag, "MSAL error code=${exception.errorCode} message=${exception.message}", exception)
                    toast("Microsoft sign-in failed: ${exception.errorCode}")
                }

                override fun onCancel() {
                    Log.d(logTag, "MSAL sign-in canceled by user")
                    toast("Microsoft sign-in canceled")
                }
            })
        }
    }

    private fun submitExternalLogin(provider: String, idToken: String, username: String) {
        lifecycleScope.launch {
            try {
                val response = ApiClient.apiService.externalLogin(ExternalAuthRequest(provider, idToken, username))
                if (response.isSuccessful) {
                    Log.d(logTag, "External login succeeded for provider=$provider")
                    toast("Login Successful")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } else {
                    Log.w(logTag, "External login failed for provider=$provider status=${response.code()} body=${response.errorBody()?.string()}")
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
