package com.yassmine.projetpfe.data.model

enum class NotificationType {
    MEETING, TASK, INVITATION
}

data class Notification(
    val id: Int,
    val type: NotificationType,
    val title: String,
    val message: String,
    val timeAgo: String,
    val isRead: Boolean
)