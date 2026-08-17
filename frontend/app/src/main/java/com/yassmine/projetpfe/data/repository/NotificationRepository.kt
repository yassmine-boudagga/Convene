package com.yassmine.projetpfe.data.repository

import com.yassmine.projetpfe.data.api.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationRepository @Inject constructor(
    private val apiService: ApiService
) {

    suspend fun getNotifications(unreadOnly: Boolean = false, page: Int = 1): Result<NotificationsListResponse> = try {
        Result.success(apiService.getNotifications(unreadOnly, page = page).data)
    } catch (e: Exception) {
        Result.failure(Exception("Erreur chargement notifications"))
    }

    suspend fun getUnreadCount(): Result<Int> = try {
        Result.success(apiService.getUnreadCount().data.count)
    } catch (e: Exception) {
        Result.failure(Exception("Erreur comptage notifications"))
    }

    suspend fun markAsRead(id: String): Result<Unit> = try {
        apiService.markNotificationRead(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception("Erreur marquage notification"))
    }

    suspend fun markAllAsRead(): Result<Unit> = try {
        apiService.markAllNotificationsRead()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception("Erreur marquage toutes notifications"))
    }

    suspend fun deleteNotification(id: String): Result<Unit> = try {
        apiService.deleteNotification(id)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(Exception("Erreur suppression notification"))
    }
}