package com.tds.binarystars.api

import com.tds.binarystars.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object ApiClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL
    private const val HEADER_ACCESS_TOKEN = "X-Access-Token"
    private const val HEADER_ACCESS_TOKEN_EXPIRES = "X-Access-Token-ExpiresIn"

    private fun isAuthExchangePath(path: String): Boolean {
        return path.contains("/auth/login") || path.contains("/auth/register")
    }
    
    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .addInterceptor { chain ->
            val token = AuthTokenStore.getStoredToken()
            val request = if (!token.isNullOrBlank()) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            val response = chain.proceed(request)
            if (response.code == 401 && !isAuthExchangePath(request.url.encodedPath)) {
                AuthTokenStore.clear()
            }
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

    /**
     * Lazily constructed Retrofit API service.
     */
    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
