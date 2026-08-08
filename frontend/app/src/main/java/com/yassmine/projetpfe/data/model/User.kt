package com.yassmine.projetpfe.data.model

data class User(
    val id: Int,
    val fullName: String,
    val email: String,
    val initials: String,
    val totalMeetings: Int,
    val completedTasks: Int,
    val teamMembers: Int,
    val avgMeetingTime: Int,
    val achievements: List<Achievement>
)

data class Achievement(
    val id: Int,
    val title: String,
    val description: String,
    val emoji: String
)