package com.tds.binarystars.api

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    // Emulator creates a loopback at 10.0.2.2 pointing to host's localhost
    private const val BASE_URL = "http://10.0.2.2:5004/api/"
    private const val HEADER_ACCESS_TOKEN = "X-Access-Token"
    private const val HEADER_ACCESS_TOKEN_EXPIRES = "X-Access-Token-ExpiresIn"
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .addInterceptor { chain ->
            val token = AuthTokenStore.getToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            val refreshedToken = response.header(HEADER_ACCESS_TOKEN)
            val refreshedExpires = response.header(HEADER_ACCESS_TOKEN_EXPIRES)?.toIntOrNull()
            if (!refreshedToken.isNullOrBlank() && refreshedExpires != null && refreshedExpires > 0) {
                AuthTokenStore.setToken(refreshedToken, refreshedExpires)
            }
            response
        }
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(5, TimeUnit.MINUTES)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
