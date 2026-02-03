package com.tds.binarystars.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path

interface ApiService {
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    @POST("auth/login/external")
    suspend fun externalLogin(@Body request: ExternalAuthRequest): Response<AuthResponse>

    @GET("devices")
    suspend fun getDevices(): Response<List<DeviceDto>>

    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceDto>

    @DELETE("devices/{id}")
    suspend fun unlinkDevice(@Path("id") deviceId: String): Response<Void>

    // Notes endpoints
    @GET("notes")
    suspend fun getNotes(): Response<List<NoteResponse>>

    @GET("notes/device/{deviceId}")
    suspend fun getNotesByDevice(@Path("deviceId") deviceId: String): Response<List<NoteResponse>>

    @GET("notes/{noteId}")
    suspend fun getNoteById(@Path("noteId") noteId: String): Response<NoteResponse>

    @POST("notes")
    suspend fun createNote(@Body request: CreateNoteRequest): Response<NoteResponse>

    @PUT("notes/{noteId}")
    suspend fun updateNote(@Path("noteId") noteId: String, @Body request: UpdateNoteRequestDto): Response<NoteResponse>

    @DELETE("notes/{noteId}")
    suspend fun deleteNote(@Path("noteId") noteId: String): Response<Void>
}
