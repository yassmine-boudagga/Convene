package com.yassmine.projetpfe.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.repository.AuthSessionEvent
import com.yassmine.projetpfe.data.repository.AuthRepository
import com.yassmine.projetpfe.notifications.NotificationBackgroundScheduler
import com.yassmine.projetpfe.notifications.NotificationDisplayStore
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import com.yassmine.projetpfe.notifications.RealtimeNotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class AuthUiState {
    object Idle    : AuthUiState()
    object Loading : AuthUiState()
    object Success : AuthUiState()
    data class Error(val message: String) : AuthUiState()
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val appContext: Context,
    private val wsClient: NotificationWebSocketClient
) : ViewModel() {

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Idle)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _isLoggedIn = MutableStateFlow<Boolean?>(null)
    val isLoggedIn: StateFlow<Boolean?> = _isLoggedIn.asStateFlow()

    private val _isLoggingOut = MutableStateFlow(false)
    val isLoggingOut: StateFlow<Boolean> = _isLoggingOut.asStateFlow()

    val sessionEvents: SharedFlow<AuthSessionEvent> = authRepository.sessionEvents

    init {
        bootstrapSession()
    }

    private fun bootstrapSession() {
        viewModelScope.launch {
            val restored = authRepository.bootstrapSession()
            _isLoggedIn.value = restored

            if (restored) {
                startRealtimeServices()
            }
        }

        viewModelScope.launch {
            sessionEvents.collect { event ->
                if (event is AuthSessionEvent.SessionExpired) {
                    stopRealtimeServices()
                    _isLoggedIn.value = false
                }
            }
        }
    }

    fun login(email: String, password: String, rememberMe: Boolean) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.login(email, password, rememberMe)

            if (result.isSuccess) {
                _isLoggedIn.value = true
                startRealtimeServices()
                _uiState.value = AuthUiState.Success
            } else {
                _uiState.value = AuthUiState.Error(
                    result.exceptionOrNull()?.message ?: "Erreur inconnue"
                )
            }
        }
    }

    fun register(name: String, email: String, password: String) {
        viewModelScope.launch {
            _uiState.value = AuthUiState.Loading
            val result = authRepository.register(name, email, password)
            _uiState.value = if (result.isSuccess) {
                _isLoggedIn.value = true
                startRealtimeServices()
                AuthUiState.Success
            } else {
                AuthUiState.Error(result.exceptionOrNull()?.message ?: "Erreur inconnue")
            }
        }
    }

    fun logout() {
        _isLoggingOut.value = true
        viewModelScope.launch {
            try {
                stopRealtimeServices()
                authRepository.logout(appContext)
                _isLoggedIn.value = false
                _uiState.value = AuthUiState.Idle
            } finally {
                _isLoggingOut.value = false
            }
        }
    }

    fun forgotPassword(email: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch {
            onResult(authRepository.forgotPassword(email))
        }
    }

    fun resetPassword(
        email: String,
        code: String,
        newPassword: String,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            onResult(authRepository.resetPassword(email, code, newPassword))
        }
    }

    fun resetState() {
        _uiState.value = AuthUiState.Idle
    }

    private fun startRealtimeServices() {
        wsClient.prepareForConnect()
        RealtimeNotificationService.start(appContext)
        NotificationBackgroundScheduler.start(appContext)
    }

    private fun stopRealtimeServices() {
        wsClient.disconnect()
        RealtimeNotificationService.stop(appContext)
        NotificationBackgroundScheduler.stop(appContext)
        NotificationDisplayStore.clear(appContext)
    }
}