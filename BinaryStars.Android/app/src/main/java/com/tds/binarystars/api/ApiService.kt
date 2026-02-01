package com.tds.binarystars.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<Void>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<Void>

    @POST("auth/login/external")
    suspend fun externalLogin(@Body request: ExternalAuthRequest): Response<Void>

    @GET("devices")
    suspend fun getDevices(): Response<List<DeviceDto>>

    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceDto>

    @DELETE("devices/{id}")
    suspend fun unlinkDevice(@Path("id") deviceId: String): Response<Void>
}
