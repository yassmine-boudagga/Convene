package com.yassmine.projetpfe.data.repository

import android.util.Base64
import com.yassmine.projetpfe.data.local.PreferencesManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

sealed class AuthSessionEvent {
    data object SessionExpired : AuthSessionEvent()
}

data class TokenPair(val accessToken: String, val refreshToken: String?)

@Singleton
class AuthSessionManager @Inject constructor(
    private val preferencesManager: PreferencesManager,
) {

    @Volatile
    private var inMemoryAccessToken: String? = null

    @Volatile
    private var inMemoryRefreshToken: String? = null

    @Volatile
    private var rememberMe: Boolean = false

    private val _sessionEvents = MutableSharedFlow<AuthSessionEvent>(
        replay = 0,
        extraBufferCapacity = 1,
    )
    val sessionEvents: SharedFlow<AuthSessionEvent> = _sessionEvents.asSharedFlow()

    private val refreshMutex = Mutex()

    suspend fun initializeFromStorage() {
        rememberMe = preferencesManager.getRememberMe().first()
        if (!rememberMe) {
            preferencesManager.clearToken()
            preferencesManager.clearRefreshToken()
            inMemoryAccessToken = null
            inMemoryRefreshToken = null
            return
        }

        inMemoryAccessToken = preferencesManager.getToken().first()
        inMemoryRefreshToken = preferencesManager.getRefreshToken().first()
    }

    fun getAccessToken(): String? = inMemoryAccessToken

    fun currentAccessToken(): String? = inMemoryAccessToken

    fun getRefreshToken(): String? = inMemoryRefreshToken

    fun currentRefreshToken(): String? = inMemoryRefreshToken

    fun isRememberMeEnabled(): Boolean = rememberMe

    suspend fun saveSession(accessToken: String, refreshToken: String, rememberMeEnabled: Boolean) {
        rememberMe = rememberMeEnabled
        inMemoryAccessToken = accessToken
        inMemoryRefreshToken = refreshToken

        if (rememberMeEnabled) {
            preferencesManager.saveRememberMe(true)
            preferencesManager.saveToken(accessToken)
            preferencesManager.saveRefreshToken(refreshToken)
        } else {
            preferencesManager.saveRememberMe(false)
            preferencesManager.clearToken()
            preferencesManager.clearRefreshToken()
        }
    }

    suspend fun updateTokens(accessToken: String, refreshToken: String?) {
        inMemoryAccessToken = accessToken
        if (!refreshToken.isNullOrBlank()) {
            inMemoryRefreshToken = refreshToken
        }

        if (rememberMe) {
            preferencesManager.saveToken(accessToken)
            if (!refreshToken.isNullOrBlank()) {
                preferencesManager.saveRefreshToken(refreshToken)
            }
        }
    }

    suspend fun clearSession() {
        inMemoryAccessToken = null
        inMemoryRefreshToken = null
        rememberMe = false
        preferencesManager.clearAuthData()
    }

    suspend fun emitSessionExpired() {
        clearSession()
        _sessionEvents.emit(AuthSessionEvent.SessionExpired)
    }

    suspend fun refreshIfNeeded(
        doRefresh: suspend (refreshToken: String) -> TokenPair?
    ): Boolean {
        val currentToken = currentAccessToken()
        if (!currentToken.isNullOrBlank() && !isJwtExpired(currentToken, skewSeconds = 60)) {
            return true
        }

        return refreshMutex.withLock {
            val recheckedToken = currentAccessToken()
            if (!recheckedToken.isNullOrBlank() && !isJwtExpired(recheckedToken, skewSeconds = 60)) {
                return@withLock true
            }

            val refreshToken = currentRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                return@withLock false
            }

            if (isJwtExpired(refreshToken, skewSeconds = 60)) {
                return@withLock false
            }

            val refreshed = doRefresh(refreshToken) ?: return@withLock false
            updateTokens(refreshed.accessToken, refreshed.refreshToken)
            true
        }
    }

    fun isJwtExpired(token: String, skewSeconds: Long = 30): Boolean {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return true

            val payload = decodeBase64Url(parts[1])
            val exp = JSONObject(payload).optLong("exp", 0L)
            if (exp <= 0L) return true

            val nowSeconds = System.currentTimeMillis() / 1000L
            nowSeconds >= (exp - skewSeconds)
        } catch (_: Exception) {
            true
        }
    }

    private fun decodeBase64Url(value: String): String {
        val normalized = value
            .replace('-', '+')
            .replace('_', '/')
            .let { raw ->
                val padding = (4 - raw.length % 4) % 4
                raw + "=".repeat(padding)
            }
        val decoded = Base64.decode(normalized, Base64.DEFAULT)
        return String(decoded, Charsets.UTF_8)
    }
}
