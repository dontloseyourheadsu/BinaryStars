package com.tds.binarystars.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Void>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Void>

    @POST("auth/login/external")
    suspend fun externalLogin(@Body request: ExternalAuthRequest): Response<Void>

    @GET("devices")
    suspend fun getDevices(): Response<List<DeviceDto>>
}
