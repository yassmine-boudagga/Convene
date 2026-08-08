package com.yassmine.projetpfe.data.api
import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: T,
    @SerializedName("message") val message: String? = null,
    @SerializedName("total") val total: Int? = null,
    @SerializedName("page") val page: Int? = null,
    @SerializedName("pages") val pages: Int? = null,
)

data class AuthData(
    @SerializedName("user") val user: UserDto,
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String
)

data class RefreshTokenRequest(
    @SerializedName("refreshToken") val refreshToken: String
)

data class LogoutRequest(
    @SerializedName("refreshToken") val refreshToken: String? = null
)

data class RefreshTokenData(
    @SerializedName("token") val token: String,
    @SerializedName("refreshToken") val refreshToken: String? = null,
)

data class UserDto(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class UserResponse(
    @SerializedName("user") val user: UserDto
)

data class RegisterRequest(
    @SerializedName("name") val name: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class LoginRequest(
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)

data class ForgotPasswordRequest(
    @SerializedName("email") val email: String
)

data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("code") val code: String,
    @SerializedName("newPassword") val newPassword: String
)

data class SimpleMessageResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("message") val message: String
)

data class ErrorResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("message") val message: String,
    @SerializedName("errors") val errors: Map<String, String>? = null
)

//  Notification models(backend + websocket)
data class NotificationDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("type") val type: String = "info",
    @SerializedName("title") val title: String = "",
    @SerializedName("message") val message: String = "",
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("isDelivered") val isDelivered: Boolean = false,
    @SerializedName("createdAt") val createdAt: String = "",
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("actionTaken") val actionTaken: String? = null,
    @SerializedName("data") val data: NotificationPayloadDto? = null,
)

data class NotificationPayloadDto(
    @SerializedName("taskId") val taskId: String? = null,
    @SerializedName("meetingId") val meetingId: String? = null,
    @SerializedName("meetingTitle") val meetingTitle: String? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("actionUrl") val actionUrl: String? = null,
    @SerializedName("fromUserId") val fromUserId: String? = null,
    @SerializedName("fromUserName") val fromUserName: String? = null,
    @SerializedName("organizerName") val organizerName: String? = null,
)

// Réponses API
data class NotificationsListResponse(
    @SerializedName("notifications") val notifications: List<NotificationDto> = emptyList(),
    @SerializedName("unreadCount") val unreadCount: Int = 0,
    @SerializedName("hasMore") val hasMore: Boolean = false,
    @SerializedName("page") val page: Int = 1,
    @SerializedName("total") val total: Int = 0,
)

data class UnreadCountResponse(
    @SerializedName("count") val count: Int = 0,
)

data class AIResultResponse(
    @SerializedName("meetingId") val meetingId: String = "",
    @SerializedName("summary") val summary: SummaryData? = null,
    @SerializedName("transcript") val transcript: TranscriptData? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
)

data class SummaryData(
    @SerializedName("keyPoints") val keyPoints: List<String> = emptyList(),
    @SerializedName("decisions") val decisions: List<String> = emptyList(),
    @SerializedName("actionItems") val actionItems: List<ActionItem> = emptyList(),
)

data class ActionItem(
    @SerializedName("text") val text: String = "",
    @SerializedName("ownerHint") val ownerHint: String? = null,
    @SerializedName("dueDateHint") val dueDateHint: String? = null,
)

data class TranscriptData(
    @SerializedName("rawText") val rawText: String? = null,
    @SerializedName("language") val language: String? = null,
    @SerializedName("durationSeconds") val durationSeconds: Int? = null,
)

data class RecordingInfoResponse(
    @SerializedName("success") val success: Boolean,
    @SerializedName("data") val data: RecordingData?
)

data class RecordingData(
    @SerializedName("recordingUrl") val recordingUrl: String?,
    @SerializedName("duration") val duration: Long? = null,
    @SerializedName("status") val status: String? = null
)

data class TaskResponse(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "",
    @SerializedName("assigneeId") val assigneeId: String? = null,
    @SerializedName("assigneeName") val assigneeName: String? = null,
    @SerializedName("assigneeEmail") val assigneeEmail: String? = null,
    @SerializedName("meetingId") val meetingId: String? = null,
    @SerializedName("meetingTitle") val meetingTitle: String? = null,
    @SerializedName("status") val status: String = "todo",
    @SerializedName("priority") val priority: String = "medium",
    @SerializedName("dueDate") val dueDate: String? = null,
    @SerializedName("completedAt") val completedAt: String? = null,
    @SerializedName("archivedAt") val archivedAt: String? = null,
    @SerializedName("source") val source: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
) {
    val taskId: String get() = id
}

data class CreateTaskRequest(
    @SerializedName("title") val title: String,
    @SerializedName("meetingId") val meetingId: String,
    @SerializedName("assigneeId") val assigneeId: String? = null,
    @SerializedName("priority") val priority: String = "medium",
    @SerializedName("dueDate") val dueDate: String? = null,
)