package com.tds.binarystars.api

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.DELETE
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming
import okhttp3.RequestBody
import okhttp3.ResponseBody

interface ApiService {
    /** Login with email/username + password. */
    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): Response<AuthResponse>

    /** Register a new account and return a JWT. */
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): Response<AuthResponse>

    /** Login with an external provider token. */
    @POST("auth/login/external")
    suspend fun externalLogin(@Body request: ExternalAuthRequest): Response<AuthResponse>

    /** Fetch the current user profile. */
    @GET("accounts/me")
    suspend fun getProfile(): Response<AccountProfileDto>

    /** List devices linked to the user. */
    @GET("devices")
    suspend fun getDevices(): Response<List<DeviceDto>>

    /** Register or update the current device. */
    @POST("devices/register")
    suspend fun registerDevice(@Body request: RegisterDeviceRequest): Response<DeviceDto>

    /** Unlink a device by ID. */
    @DELETE("devices/{id}")
    suspend fun unlinkDevice(@Path("id") deviceId: String): Response<Void>

    /** Update telemetry for a linked device. */
    @PUT("devices/{id}/telemetry")
    suspend fun updateDeviceTelemetry(
        @Path("id") deviceId: String,
        @Body request: UpdateDeviceTelemetryRequest
    ): Response<DeviceDto>

    /** Send a liveness heartbeat for a linked device. */
    @POST("devices/{id}/heartbeat")
    suspend fun sendDeviceHeartbeat(@Path("id") deviceId: String): Response<DeviceDto>

    /** Pull pending notifications and schedules. */
    @GET("notifications/pull")
    suspend fun pullNotifications(@Query("deviceId") deviceId: String): Response<NotificationsPullResponse>

    /** Acknowledge notification sync. */
    @POST("notifications/ack")
    suspend fun ackNotificationSync(@Body request: NotificationSyncAckRequestDto): Response<Unit>

    /** Send a direct notification. */
    @POST("notifications/send")
    suspend fun sendNotification(@Body request: SendNotificationRequestDto): Response<DeviceNotificationMessage>

    /** List notification schedules for a target device. */
    @GET("notifications/schedules")
    suspend fun getNotificationSchedules(@Query("deviceId") deviceId: String): Response<List<NotificationScheduleDto>>

    /** Create a notification schedule. */
    @POST("notifications/schedules")
    suspend fun createNotificationSchedule(@Body request: CreateNotificationScheduleRequestDto): Response<NotificationScheduleDto>

    /** Update a notification schedule. */
    @PUT("notifications/schedules/{scheduleId}")
    suspend fun updateNotificationSchedule(
        @Path("scheduleId") scheduleId: String,
        @Body request: UpdateNotificationScheduleRequestDto
    ): Response<NotificationScheduleDto>

    /** Delete a notification schedule. */
    @DELETE("notifications/schedules/{scheduleId}")
    suspend fun deleteNotificationSchedule(@Path("scheduleId") scheduleId: String): Response<Void>

    /** Send a remote action command to a target device. */
    @POST("actions/send")
    suspend fun sendAction(@Body request: SendActionRequestDto): Response<DeviceActionCommandDto>

    // Notes endpoints
    /** List notes for the authenticated user. */
    @GET("notes")
    suspend fun getNotes(): Response<List<NoteResponse>>

    /** List notes for a specific device. */
    @GET("notes/device/{deviceId}")
    suspend fun getNotesByDevice(@Path("deviceId") deviceId: String): Response<List<NoteResponse>>

    /** Fetch a single note by ID. */
    @GET("notes/{noteId}")
    suspend fun getNoteById(@Path("noteId") noteId: String): Response<NoteResponse>

    /** Create a new note. */
    @POST("notes")
    suspend fun createNote(@Body request: CreateNoteRequest): Response<NoteResponse>

    /** Update an existing note. */
    @PUT("notes/{noteId}")
    suspend fun updateNote(@Path("noteId") noteId: String, @Body request: UpdateNoteRequestDto): Response<NoteResponse>

    /** Delete a note by ID. */
    @DELETE("notes/{noteId}")
    suspend fun deleteNote(@Path("noteId") noteId: String): Response<Void>

    // File transfers
    /** List file transfers for the authenticated user. */
    @GET("files/transfers")
    suspend fun getFileTransfers(@Query("deviceId") deviceId: String): Response<List<FileTransferSummaryDto>>

    /** Clear transfers by scope for a device. */
    @POST("files/transfers/clear")
    suspend fun clearFileTransfers(@Body request: ClearFileTransfersRequestDto): Response<Void>

    /** List pending transfers for a device. */
    @GET("files/transfers/pending")
    suspend fun getPendingTransfers(@Query("deviceId") deviceId: String): Response<List<FileTransferSummaryDto>>

    /** Create a new transfer. */
    @POST("files/transfers")
    suspend fun createFileTransfer(@Body request: CreateFileTransferRequestDto): Response<FileTransferDetailDto>

    /** Upload transfer bytes. */
    @PUT("files/transfers/{transferId}/upload")
    suspend fun uploadFileTransfer(@Path("transferId") transferId: String, @Body body: RequestBody): Response<Void>

    /** Stream transfer bytes to a file. */
    @Streaming
    @GET("files/transfers/{transferId}/download")
    suspend fun downloadFileTransfer(@Path("transferId") transferId: String, @Query("deviceId") deviceId: String): Response<ResponseBody>

    /** Reject a transfer for a device. */
    @POST("files/transfers/{transferId}/reject")
    suspend fun rejectFileTransfer(@Path("transferId") transferId: String, @Query("deviceId") deviceId: String): Response<Void>

    // Messaging
    /** Send a device-to-device message. */
    @POST("messaging/send")
    suspend fun sendMessage(@Body request: SendMessageRequestDto): Response<MessagingMessageDto>

    /** List chat summaries for a device. */
    @GET("messaging/chats")
    suspend fun getMessagingChats(@Query("deviceId") deviceId: String): Response<List<ChatSummaryDto>>

    /** List message history between two devices. */
    @GET("messaging/history")
    suspend fun getMessageHistory(
        @Query("deviceId") deviceId: String,
        @Query("targetDeviceId") targetDeviceId: String,
        @Query("limit") limit: Int = 200
    ): Response<List<MessagingMessageDto>>

    /** Clear one conversation between two devices. */
    @POST("messaging/clear")
    suspend fun clearConversation(@Body request: ClearConversationRequestDto): Response<Void>

    // Location
    /** Send a location update. */
    @POST("locations")
    suspend fun sendLocation(@Body request: LocationUpdateRequestDto): Response<Void>

    /** Send a live location update without persisting to history. */
    @POST("locations/live")
    suspend fun sendLiveLocation(@Body request: LocationUpdateRequestDto): Response<Void>

    /** Fetch location history for a device. */
    @GET("locations/history")
    suspend fun getLocationHistory(@Query("deviceId") deviceId: String): Response<List<LocationHistoryPointDto>>
}
