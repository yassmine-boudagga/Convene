package com.yassmine.projetpfe.data.repository

import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.TaskResponse
import javax.inject.Inject
import javax.inject.Singleton

data class TaskFetchResult(
    val tasks: List<TaskResponse>,
    val hasMore: Boolean,
)

@Singleton
class TaskFilterRepository @Inject constructor(
    private val apiService: ApiService,
) {
    suspend fun fetchTasks(
        endpoint: String,
        meetingId: String?,
        status: String?,
        toDate: String?,
        archived: Boolean,
        page: Int = 1,
    ): Result<TaskFetchResult> {
        return try {
            val effectiveStatus = if (archived) "archived" else status

            when (endpoint) {
                ENDPOINT_MY -> {
                    val response = apiService.getMyTasks(
                        meetingId = meetingId,
                        status = effectiveStatus,
                        toDate = toDate,
                        archived = archived,
                        page = page,
                        limit = 20,
                    )

                    if (response.isSuccessful) {
                        val body = response.body()
                        val tasks = body?.data.orEmpty()
                        val totalPages = body?.pages?.coerceAtLeast(1) ?: 1
                        val hasMore = page < totalPages
                        Result.success(TaskFetchResult(tasks = tasks, hasMore = hasMore))
                    } else {
                        Result.failure(IllegalStateException("Erreur ${response.code()}"))
                    }
                }

                ENDPOINT_ALL -> {
                    val response = apiService.getRelatedTasks(
                        meetingId = meetingId,
                        status = effectiveStatus,
                        toDate = toDate,
                        archived = archived,
                        page = 1,
                        limit = 200,
                    )

                    if (response.isSuccessful) {
                        val tasks = response.body()?.data.orEmpty()
                        Result.success(TaskFetchResult(tasks = tasks, hasMore = false))
                    } else {
                        Result.failure(IllegalStateException("Erreur ${response.code()}"))
                    }
                }

                else -> Result.failure(IllegalArgumentException("Unknown endpoint: $endpoint"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    companion object {
        const val ENDPOINT_MY = "my"
        const val ENDPOINT_ALL = "all"
    }
}
