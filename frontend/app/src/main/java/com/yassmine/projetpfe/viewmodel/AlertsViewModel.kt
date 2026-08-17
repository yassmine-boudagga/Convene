package com.yassmine.projetpfe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.NotificationDto
import com.yassmine.projetpfe.data.repository.NotificationRepository
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AlertsUiState(
    val notifications: List<NotificationDto> = emptyList(),
    val unreadCount: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val currentPage: Int = 1,
    val errorMessage: String? = null,
    val isWsConnected: Boolean = false,
)

@HiltViewModel
class AlertsViewModel @Inject constructor(
    private val notificationRepository: NotificationRepository,
    private val wsClient: NotificationWebSocketClient,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        loadNotifications()
        observeWebSocket()
    }

    //  Chargement initial
    fun loadNotifications() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null, currentPage = 1) }
            val result = notificationRepository.getNotifications(page = 1)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                _uiState.update {
                    it.copy(
                        notifications = data.notifications,
                        unreadCount   = data.unreadCount,
                        hasMore       = data.hasMore,
                        currentPage   = 1,
                        isLoading     = false
                    )
                }
            } else {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = result.exceptionOrNull()?.message)
                }
            }
        }
    }

    //  Chargement page suivante (append) 
    fun loadMoreNotifications() {
        val state = _uiState.value
        if (!state.hasMore || state.isLoadingMore) return

        viewModelScope.launch {
            val nextPage = state.currentPage + 1
            _uiState.update { it.copy(isLoadingMore = true) }
            val result = notificationRepository.getNotifications(page = nextPage)
            if (result.isSuccess) {
                val data = result.getOrThrow()
                _uiState.update {
                    val merged = it.notifications + data.notifications
                    val deduplicated = merged.distinctBy { n ->
                        n.id.ifBlank { n.createdAt + n.type }
                    }
                    it.copy(
                        notifications = deduplicated,
                        unreadCount   = data.unreadCount,
                        hasMore       = data.hasMore,
                        currentPage   = nextPage,
                        isLoadingMore = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoadingMore = false) }
            }
        }
    }

    //  Écoute temps réel WebSocket 
    private fun observeWebSocket() {
        viewModelScope.launch {
            wsClient.notifications.collect { newNotif ->
                _uiState.update { state ->
                    if (newNotif.id.isBlank()) return@update state

                    val exists  = state.notifications.any { it.id == newNotif.id }
                    val updated = if (exists) state.notifications
                    else listOf(newNotif) + state.notifications
                    state.copy(
                        notifications = updated,
                        unreadCount   = updated.count { !it.isRead }
                    )
                }
            }
        }

        viewModelScope.launch {
            wsClient.isConnected.collect { connected ->
                _uiState.update { it.copy(isWsConnected = connected) }
            }
        }
    }

    //  markAsRead 
    fun markAsRead(notificationId: String) {
        if (notificationId.isBlank()) return

        viewModelScope.launch {
            notificationRepository.markAsRead(notificationId)
            _uiState.update { state ->
                val updated = state.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                }
                state.copy(
                    notifications = updated,
                    unreadCount   = updated.count { !it.isRead }
                )
            }
            wsClient.decrementUnread()
        }
    }

    //  markAllAsRead 
    fun markAllAsRead() {
        viewModelScope.launch {
            notificationRepository.markAllAsRead()
            _uiState.update { state ->
                state.copy(
                    notifications = state.notifications.map { it.copy(isRead = true) },
                    unreadCount   = 0
                )
            }
            wsClient.resetUnread()
        }
    }

    //  deleteNotification 
    fun deleteNotification(notificationId: String) {
        if (notificationId.isBlank()) return

        viewModelScope.launch {
            notificationRepository.deleteNotification(notificationId)
            _uiState.update { state ->
                val updated = state.notifications.filter { it.id != notificationId }
                state.copy(
                    notifications = updated,
                    unreadCount   = updated.count { !it.isRead }
                )
            }
        }
    }

    fun markNotifAction(notificationId: String, action: String) {
        if (notificationId.isBlank()) return
        _uiState.update { state ->
            state.copy(
                notifications = state.notifications.map {
                    if (it.id == notificationId) it.copy(actionTaken = action) else it
                }
            )
        }
        markAsRead(notificationId)
    }
}