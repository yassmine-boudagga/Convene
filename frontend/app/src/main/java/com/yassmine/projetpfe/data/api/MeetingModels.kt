package com.yassmine.projetpfe.data.api

import com.google.gson.annotations.SerializedName

data class MeetingListResponse(
    @SerializedName("meetings") val meetings: List<MeetingDto> = emptyList(),
    @SerializedName("pagination") val pagination: PaginationDto? = null
)

data class MeetingResponse(
    @SerializedName("meeting") val meeting: MeetingDto? = null,
    @SerializedName("userRole") val userRole: String? = null,
    @SerializedName("permissions") val permissions: MeetingPermissionsDto? = null
)

data class MeetingDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("title") val title: String = "Sans titre",
    @SerializedName("description") val description: String? = null,
    @SerializedName("startTime") val startTime: String = "",
    @SerializedName("duration") val duration: Int = 60,
    @SerializedName("meetingType") val meetingType: String = "online",
    @SerializedName("location") val location: String? = null,
    @SerializedName("createdBy") val createdBy: MeetingCreatorDto? = null,
    @SerializedName("participants") val participants: List<String> = emptyList(),
    @SerializedName("participantUsers") val participantUsers: List<MeetingParticipantDto> = emptyList(),
    @SerializedName("roomId") val roomId: String? = null,
    @SerializedName("isRecording") val isRecording: Boolean = false,
    @SerializedName("activeEgressId") val activeEgressId: String? = null,
    @SerializedName("recordingId") val recordingId: String? = null,
    @SerializedName("recordingStatus") val recordingStatus: String? = null,
    @SerializedName("status") val status: String = "scheduled",
    @SerializedName("realMeetingStarted") val realMeetingStarted: Boolean = false,
    @SerializedName("joinedParticipants") val joinedParticipants: List<JoinedParticipantDto> = emptyList(),
    @SerializedName("notes") val notes: List<MeetingNoteDto> = emptyList(),
    @SerializedName("recordingStartedAt") val recordingStartedAt: String? = null,
    @SerializedName("recordingStoppedAt") val recordingStoppedAt: String? = null,
    @SerializedName("recordingUrl") val recordingUrl: String? = null,
    @SerializedName("recordingDuration") val recordingDuration: Int? = null,
    @SerializedName("recordingLocalPath") val recordingLocalPath: String? = null,
    @SerializedName("aiStatus") val aiStatus: String? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    @SerializedName("permissions") val permissions: MeetingPermissionsDto? = null,
    @SerializedName("userRole") val userRole: String? = null
) {
    val realId: String get() = id
}

data class MeetingPermissionsDto(
    @SerializedName("canJoin") val canJoin: Boolean = false,
    @SerializedName("canEdit") val canEdit: Boolean = false,
    @SerializedName("canCancel") val canCancel: Boolean = false
)

data class MeetingCreatorDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "Inconnu",
    @SerializedName("email") val email: String = "",
    @SerializedName("profilePicture") val profilePicture: String? = null
) {
    val realId: String get() = id
}

data class MeetingParticipantDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("email") val email: String = "",
    @SerializedName("name") val name: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null
)

data class JoinedParticipantDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("email") val email: String? = null,
    @SerializedName("profilePicture") val profilePicture: String? = null,
)

data class MeetingNoteDto(
    @SerializedName("id") val id: String = "",
    @SerializedName("userId") val userId: MeetingCreatorDto? = null,
    @SerializedName("content") val content: String = "",
    @SerializedName("timestamp") val timestamp: String = ""
) {
    val realId: String get() = id
}

data class PaginationDto(
    @SerializedName("page") val page: Int = 1,
    @SerializedName("pages") val totalPages: Int = 1,
    @SerializedName("totalItems") val totalItems: Int = 0,
    @SerializedName("total") val total: Int = 0
)


data class CreateMeetingRequest(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String? = null,
    @SerializedName("startTime") val startTime: String,
    @SerializedName("duration") val duration: Int,
    @SerializedName("meetingType") val meetingType: String,
    @SerializedName("location") val location: String? = null,
    @SerializedName("participants") val participants: List<String>
)

data class UpdateMeetingRequest(
    @SerializedName("title") val title: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("startTime") val startTime: String? = null,
    @SerializedName("duration") val duration: Int? = null,
    @SerializedName("meetingType") val meetingType: String? = null,
    @SerializedName("location") val location: String? = null,
    @SerializedName("participants") val participants: List<String>? = null
)

data class JoinMeetingResponse(
    @SerializedName("meeting") val meeting: MeetingDto? = null,
    @SerializedName("token") val token: String = "",
    @SerializedName("livekitUrl") val livekitUrl: String = "",
    @SerializedName("roomName") val roomName: String = "",
    @SerializedName("role") val role: String = "guest",
    @SerializedName("role_display") val roleDisplay: String = "",
    @SerializedName("isRejoin") val isRejoin: Boolean = false
)

data class AddNoteRequest(
    @SerializedName("content") val content: String
)

data class NoteResponse(
    @SerializedName("note") val note: MeetingNoteDto
)

data class NotesListResponse(
    @SerializedName("notes") val notes: List<MeetingNoteDto> = emptyList()
)

data class RecordingResponse(
    @SerializedName("recordingId") val recordingId: String = "",
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("stoppedAt") val stoppedAt: String? = null
)

data class RecordingStatusResponse(
    @SerializedName("recordingId") val recordingId: String? = null,
    @SerializedName("isRecording") val isRecording: Boolean = false,
    @SerializedName("startedAt") val startedAt: String? = null,
    @SerializedName("stoppedAt") val stoppedAt: String? = null,
    @SerializedName("recordingUrl") val recordingUrl: String? = null,
    @SerializedName("recordingDuration") val recordingDuration: Int? = null,
    @SerializedName("status") val status: String = "none"
)

data class JoinPhysicalResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("joinedParticipants") val joinedParticipants: List<JoinedParticipantDto> = emptyList(),
    @SerializedName("status") val status: String = "scheduled"
)

data class GenericApiResponse(
    @SerializedName("success") val success: Boolean = false
)

data class UploadRecordingResponse(
    @SerializedName("success") val success: Boolean = false,
    @SerializedName("recordingPath") val recordingPath: String? = null
)

data class AvatarResponse(
    @SerializedName("avatarUrl") val avatarUrl: String = ""
)

data class MessageResponse(
    @SerializedName("message") val message: String = ""
)


data class UserSearchResult(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("jobTitle") val jobTitle: String? = null,
    @SerializedName("company") val company: String? = null,
    @SerializedName("friendStatus") val friendStatus: String = "none"
)

data class FriendRequest(
    @SerializedName("from") val from: UserSearchResult,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class PublicProfile(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("profilePicture") val profilePicture: String? = null,
    @SerializedName("bio") val bio: String? = null,
    @SerializedName("jobTitle") val jobTitle: String? = null,
    @SerializedName("company") val company: String? = null,
    @SerializedName("friendStatus") val friendStatus: String = "none",
    @SerializedName("meetingsOrganized") val meetingsOrganized: Int = 0,
    @SerializedName("meetingsAttended") val meetingsAttended: Int = 0,
    @SerializedName("notesAdded") val notesAdded: Int = 0,
    @SerializedName("tasksCompleted") val tasksCompleted: Int = 0,
    @SerializedName("friendsCount") val friendsCount: Int? = null,
    @SerializedName("pendingRequestsCount") val pendingRequestsCount: Int? = null,
    @SerializedName("achievements") val achievements: List<Achievement> = emptyList()
)

data class UserSearchListResponse(
    @SerializedName("users") val users: List<UserSearchResult> = emptyList()
)

data class FriendsListResponse(
    @SerializedName("friends") val friends: List<UserSearchResult> = emptyList()
)

data class PublicProfileResponse(
    @SerializedName("profile") val profile: PublicProfile? = null
)

data class FriendRequestsResponse(
    @SerializedName("requests") val requests: List<FriendRequest> = emptyList()
)

data class SentFriendRequest(
    @SerializedName("to") val to: UserSearchResult,
    @SerializedName("createdAt") val createdAt: String? = null
)

data class SentFriendRequestsResponse(
    @SerializedName("requests") val requests: List<SentFriendRequest> = emptyList()
)


data class Achievement(
    @SerializedName("id") val id: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("unlocked") val unlocked: Boolean = false,
    @SerializedName("unlockedAt") val unlockedAt: String? = null,
    @SerializedName("current") val current: Int = 0,
    @SerializedName("target") val target: Int = 0
)

data class UserStats(
    @SerializedName("meetingsOrganized") val meetingsOrganized: Int = 0,
    @SerializedName("meetingsAttended") val meetingsAttended: Int = 0,
    @SerializedName("notesAdded") val notesAdded: Int = 0,
    @SerializedName("tasksCompleted") val tasksCompleted: Int = 0,
    @SerializedName("achievements") val achievements: List<Achievement> = emptyList()
)

data class UserStatsResponse(
    @SerializedName("stats") val stats: UserStats? = null
)

object AchievementMeta {
    data class Meta(val emoji: String, val description: String)
    val map: Map<String, Meta> = mapOf(
        "organizer"    to Meta("📅", "10 réunions organisées"),
        "punctual"     to Meta("🕒", "10 réunions rejointes à l'heure"),
        "collaborator" to Meta("👥", "20 notes ajoutées"),
        "bilingual"    to Meta("🌐", "Réunions en 2 langues"),
        "marathon"     to Meta("⏳", "Réunion > 1h"),
        "efficient"    to Meta("🎯", "10 tâches complétées avant deadline")
    )
}