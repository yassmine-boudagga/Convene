package com.yassmine.projetpfe.data.api

import retrofit2.http.*
import retrofit2.Response
import okhttp3.MultipartBody
import okhttp3.RequestBody

interface ApiService {
    @POST("auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<AuthData>

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthData>

    @POST("auth/logout")
    suspend fun logout(@Body request: LogoutRequest): ApiResponse<Unit>

    @POST("auth/refresh")
    suspend fun refreshAccessToken(@Body request: RefreshTokenRequest): ApiResponse<RefreshTokenData>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(@Body request: ForgotPasswordRequest): Response<SimpleMessageResponse>

    @POST("auth/reset-password")
    suspend fun resetPassword(@Body request: ResetPasswordRequest): Response<SimpleMessageResponse>


    @GET("meetings")
    suspend fun getMeetings(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("all") all: Boolean = false
    ): ApiResponse<MeetingListResponse>

    @POST("meetings")
    suspend fun createMeeting(@Body request: CreateMeetingRequest): ApiResponse<MeetingResponse>

    @GET("meetings/{id}")
    suspend fun getMeeting(@Path("id") id: String): ApiResponse<MeetingResponse>

    @PUT("meetings/{id}")
    suspend fun updateMeeting(@Path("id") id: String,@Body request: UpdateMeetingRequest): ApiResponse<MeetingResponse>

    @POST("meetings/{id}/join")
    suspend fun joinMeeting(@Path("id") id: String): ApiResponse<JoinMeetingResponse>

    @POST("meetings/{id}/leave")
    suspend fun leaveMeeting(@Path("id") id: String): ApiResponse<Unit>

    @POST("meetings/{id}/join/physical")
    suspend fun joinPhysicalMeeting(@Path("id") meetingId: String): Response<ApiResponse<JoinPhysicalResponse>>

    @POST("meetings/{id}/leave/physical")
    suspend fun leavePhysicalMeeting(@Path("id") meetingId: String): Response<ApiResponse<GenericApiResponse>>

    @POST("meetings/{id}/heartbeat")
    suspend fun sendHeartbeat(@Path("id") id: String): ApiResponse<Unit>

    @POST("meetings/{id}/cancel")
    suspend fun cancelMeeting(@Path("id") id: String): ApiResponse<Unit>

 
    @POST("meetings/{id}/add-note")
    suspend fun addNote(@Path("id") id: String, @Body request: AddNoteRequest): ApiResponse<NoteResponse>

    @GET("meetings/{id}/notes")
    suspend fun getNotes(@Path("id") id: String): ApiResponse<NotesListResponse>


    @POST("meetings/{id}/stop-recording")
    suspend fun stopRecording(@Path("id") id: String): ApiResponse<RecordingResponse>

    @GET("meetings/{meetingId}/recording")
    suspend fun getRecordingInfo(@Path("meetingId") meetingId: String): Response<RecordingInfoResponse>

    @Multipart
    @POST("meetings/{id}/recording/upload")
    suspend fun uploadPhysicalRecording(
        @Path("id") meetingId: String,
        @Part file: MultipartBody.Part,
        @Part("durationSeconds") durationSeconds: RequestBody
    ): Response<ApiResponse<UploadRecordingResponse>>

    @GET("meetings/{meetingId}/ai-result")
    suspend fun getMeetingAIResult(@Path("meetingId") meetingId: String): Response<ApiResponse<AIResultResponse>>

    @GET("meetings/{meetingId}/tasks")
    suspend fun getMeetingTasks(@Path("meetingId") meetingId: String): Response<ApiResponse<List<TaskResponse>>>

    @GET("tasks/me")
    suspend fun getMyTasks(
        @Query("meetingId") meetingId: String? = null,
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("archived") archived: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50
    ): Response<ApiResponse<List<TaskResponse>>>

    @PATCH("tasks/{taskId}/complete")
    suspend fun completeTask(@Path("taskId") taskId: String): Response<ApiResponse<TaskResponse>>

    @POST("tasks")
    suspend fun createTask(@Body request: CreateTaskRequest): Response<ApiResponse<TaskResponse>>

    @GET("tasks/related")
    suspend fun getRelatedTasks(
        @Query("meetingId") meetingId: String? = null,
        @Query("status") status: String? = null,
        @Query("priority") priority: String? = null,
        @Query("fromDate") fromDate: String? = null,
        @Query("toDate") toDate: String? = null,
        @Query("archived") archived: Boolean = false,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 50,
    ): Response<ApiResponse<List<TaskResponse>>>


    @GET("notifications")
    suspend fun getNotifications(
        @Query("unread_only") unreadOnly: Boolean = false,
        @Query("limit") limit: Int = 30,
        @Query("page") page: Int = 1
    ): ApiResponse<NotificationsListResponse>

    @GET("notifications/unread-count")
    suspend fun getUnreadCount(): ApiResponse<UnreadCountResponse>

    @PUT("notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): ApiResponse<Unit>

    @PUT("notifications/read-all")
    suspend fun markAllNotificationsRead(): ApiResponse<Unit>

    @DELETE("notifications/{id}")
    suspend fun deleteNotification(@Path("id") id: String): ApiResponse<Unit>


    @GET("users/search")
    suspend fun searchUsers(@Query("q") query: String): ApiResponse<UserSearchListResponse>

    @GET("users/me/friends")
    suspend fun getMyFriends(): ApiResponse<FriendsListResponse>

    @GET("users/me/profile")
    suspend fun getMyProfile(): ApiResponse<PublicProfileResponse>

    @GET("users/me/stats")
    suspend fun getMyStats(): ApiResponse<UserStatsResponse>

    @GET("users/me/friend-requests")
    suspend fun getPendingFriendRequests(): ApiResponse<FriendRequestsResponse>

    @GET("users/friend-requests/sent")
    suspend fun getSentFriendRequests(): ApiResponse<SentFriendRequestsResponse>

    @GET("users/{id}/profile")
    suspend fun getUserProfile(@Path("id") userId: String): ApiResponse<PublicProfileResponse>

    @PUT("users/me/profile")
    suspend fun updateMyProfile(@Body body: Map<String, String>): ApiResponse<PublicProfileResponse>

    @Multipart
    @POST("users/me/avatar")
    suspend fun uploadAvatar(
        @Part avatar: MultipartBody.Part
    ): Response<ApiResponse<AvatarResponse>>

    @DELETE("users/me/avatar")
    suspend fun deleteAvatar(): Response<ApiResponse<Unit>>

    @POST("users/{id}/friend-request")
    suspend fun sendFriendRequest(@Path("id") userId: String): ApiResponse<Unit>

    @POST("users/{id}/friend-request/accept")
    suspend fun acceptFriendRequest(@Path("id") userId: String): ApiResponse<Unit>

    @DELETE("users/{id}/friend-request")
    suspend fun rejectFriendRequest(@Path("id") userId: String): ApiResponse<Unit>

    @DELETE("users/{id}/friend")
    suspend fun removeFriend(@Path("id") userId: String): Response<Any>
}