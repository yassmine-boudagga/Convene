package com.yassmine.projetpfe.viewmodel

import com.yassmine.projetpfe.data.api.TaskResponse

internal fun normalizeTaskStatus(status: String): String {
    return when (status.lowercase()) {
        "done" -> "completed"
        else -> status.lowercase()
    }
}

internal fun normalizeTaskResponse(task: TaskResponse): TaskResponse {
    return task.copy(status = normalizeTaskStatus(task.status))
}

internal fun mergeResolvedTask(local: TaskResponse, server: TaskResponse): TaskResponse {
    return local.copy(
        id = server.id.takeIf { it.isNotBlank() } ?: local.id,
        title = server.title.ifBlank { local.title },
        assigneeId = local.assigneeId?.takeIf { it.isNotBlank() } ?: server.assigneeId,
        assigneeName = local.assigneeName?.takeIf { it.isNotBlank() } ?: server.assigneeName,
        assigneeEmail = local.assigneeEmail?.takeIf { it.isNotBlank() } ?: server.assigneeEmail,
        meetingId = local.meetingId?.takeIf { it.isNotBlank() } ?: server.meetingId,
        meetingTitle = local.meetingTitle?.takeIf { it.isNotBlank() } ?: server.meetingTitle,
        status = normalizeTaskStatus(server.status).ifBlank { local.status },
        priority = server.priority.ifBlank { local.priority },
        dueDate = server.dueDate ?: local.dueDate,
        completedAt = server.completedAt ?: local.completedAt,
        archivedAt = server.archivedAt ?: local.archivedAt,
        source = server.source ?: local.source,
        updatedAt = server.updatedAt ?: local.updatedAt,
    )
}
