package com.yassmine.projetpfe.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "meetflow_prefs")

@Singleton
class PreferencesManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_TOKEN = stringPreferencesKey("jwt_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_REMEMBER_ME = booleanPreferencesKey("remember_me")
        private val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        private val KEY_ONBOARDING_SEEN = booleanPreferencesKey("onboarding_seen")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_LOGIN_TIMESTAMP = longPreferencesKey("login_timestamp")
        private val KEY_PREJOIN_CAMERA_ENABLED = booleanPreferencesKey("prejoin_camera_enabled")
        private val KEY_PREJOIN_MIC_ENABLED = booleanPreferencesKey("prejoin_mic_enabled")
    }

    val jwtTokenFlow: Flow<String?> = context.dataStore.data.map { it[KEY_TOKEN] }
    fun getToken(): Flow<String?> = jwtTokenFlow
    suspend fun saveToken(token: String) {
        context.dataStore.edit { it[KEY_TOKEN] = token }
    }

    suspend fun clearToken() {
        context.dataStore.edit { it.remove(KEY_TOKEN) }
    }

    fun getRefreshToken(): Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    suspend fun saveRefreshToken(token: String) {
        context.dataStore.edit { it[KEY_REFRESH_TOKEN] = token }
    }

    suspend fun clearRefreshToken() {
        context.dataStore.edit { it.remove(KEY_REFRESH_TOKEN) }
    }

    fun getRememberMe(): Flow<Boolean> = context.dataStore.data.map { it[KEY_REMEMBER_ME] ?: false }
    suspend fun saveRememberMe(remember: Boolean) {
        context.dataStore.edit { it[KEY_REMEMBER_ME] = remember }
    }

    fun getAppLanguage(): Flow<String> = context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: "fr" }
    suspend fun saveAppLanguage(languageCode: String) {
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = languageCode }
    }

    fun getOnboardingSeen(): Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_SEEN] ?: false }
    suspend fun saveOnboardingSeen(seen: Boolean) {
        context.dataStore.edit { it[KEY_ONBOARDING_SEEN] = seen }
    }

    fun getLoginTimestamp(): Flow<Long> = context.dataStore.data.map { it[KEY_LOGIN_TIMESTAMP] ?: 0L }
    suspend fun saveLoginTimestamp(timestamp: Long) {
        context.dataStore.edit { it[KEY_LOGIN_TIMESTAMP] = timestamp }
    }


    val userIdFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    fun getUserId(): Flow<String?> = userIdFlow
    suspend fun saveUserId(id: String) {
        context.dataStore.edit { it[KEY_USER_ID] = id }
    }

    val userNameFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    fun getUserName(): Flow<String?> = userNameFlow
    suspend fun saveUserName(name: String) {
        context.dataStore.edit { it[KEY_USER_NAME] = name }
    }

    val userEmailFlow: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    fun getUserEmail(): Flow<String?> = userEmailFlow
    suspend fun saveUserEmail(email: String) {
        context.dataStore.edit { it[KEY_USER_EMAIL] = email }
    }

    suspend fun saveUser(id: String, name: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = id
            prefs[KEY_USER_NAME] = name
            prefs[KEY_USER_EMAIL] = email
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_REMEMBER_ME)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_USER_EMAIL)
        }
    }
    //pre-join preferences
    fun getPreJoinCameraEnabled(): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PREJOIN_CAMERA_ENABLED] ?: true }

    fun getPreJoinMicEnabled(): Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PREJOIN_MIC_ENABLED] ?: true }

    suspend fun savePreJoinPreferences(cameraEnabled: Boolean, micEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PREJOIN_CAMERA_ENABLED] = cameraEnabled
            prefs[KEY_PREJOIN_MIC_ENABLED] = micEnabled
        }
    }
}