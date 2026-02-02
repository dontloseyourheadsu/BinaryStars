package com.tds.binarystars

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.tds.binarystars.api.ApiClient
import com.tds.binarystars.api.AuthTokenStore
import com.tds.binarystars.api.ExternalAuthRequest
import kotlinx.coroutines.launch

class UsernameInputActivity : AppCompatActivity() {

    private val logTag = "BinaryStarsReg"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_username_input)

        val provider = intent.getStringExtra("EXTRA_PROVIDER")
        val token = intent.getStringExtra("EXTRA_TOKEN")

        if (provider == null || token == null) {
            Toast.makeText(this, "Error: Missing auth data", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val etUsername = findViewById<TextInputEditText>(R.id.etUsernameRegistration)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitUsername)

        btnSubmit.setOnClickListener {
            val username = etUsername.text.toString()
            if (username.isBlank()) {
                Toast.makeText(this, "Please enter a username", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                try {
                    val response = ApiClient.apiService.externalLogin(ExternalAuthRequest(provider, token, username))
                    if (response.isSuccessful) {
                        response.body()?.accessToken?.let { AuthTokenStore.setToken(it) }
                        Toast.makeText(this@UsernameInputActivity, "Registration Successful", Toast.LENGTH_SHORT).show()
                        val intent = Intent(this@UsernameInputActivity, MainActivity::class.java)
                        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(intent)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.w(logTag, "Registration failed: code=${response.code()} body=$errorBody")
                        // If username taken, etc.
                        Toast.makeText(this@UsernameInputActivity, "Registration Failed: ${response.code()}", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Log.e(logTag, "Registration error", e)
                    Toast.makeText(this@UsernameInputActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
