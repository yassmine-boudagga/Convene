package com.yassmine.projetpfe.data.model

enum class TaskStatus { TODO, COMPLETED }
enum class TaskPriority { HIGH, MEDIUM, LOW }

data class Task(
    val id: Int,
    val title: String,
    val meetingTitle: String,
    val assignedTo: String,
    val assignedToMe: Boolean,
    val dueDate: String,
    val priority: TaskPriority,
    val status: TaskStatus,
    val isOverdue: Boolean = false
)