package com.yassmine.projetpfe.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.PublicProfile
import com.yassmine.projetpfe.data.api.UserStats
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.repository.AuthRepository
import com.yassmine.projetpfe.notifications.NotificationBackgroundScheduler
import com.yassmine.projetpfe.notifications.NotificationDisplayStore
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import com.yassmine.projetpfe.notifications.RealtimeNotificationService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authRepository: AuthRepository,
    private val wsClient: NotificationWebSocketClient,
    private val api: ApiService,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    //  Basic identity (from DataStore) 
    private val _userName = MutableStateFlow<String?>(null)
    val userName: StateFlow<String?> = _userName.asStateFlow()

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail.asStateFlow()

    //  Profile (from API) 
    private val _myProfile = MutableStateFlow<PublicProfile?>(null)
    val myProfile: StateFlow<PublicProfile?> = _myProfile.asStateFlow()

    //  Stats (from API) 
    private val _myStats = MutableStateFlow<UserStats?>(null)
    val myStats: StateFlow<UserStats?> = _myStats.asStateFlow()

    //  UI state 
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _updateSuccess = MutableStateFlow<String?>(null)
    val updateSuccess: StateFlow<String?> = _updateSuccess.asStateFlow()

    init {
        loadIdentityFromPrefs()
        loadMyProfile()
    }

    //  Load identity from local prefs 

    private fun loadIdentityFromPrefs() {
        viewModelScope.launch {
            preferencesManager.userNameFlow.collect { name ->
                _userName.value = name
            }
        }
        viewModelScope.launch {
            preferencesManager.userEmailFlow.collect { email ->
                _userEmail.value = email
            }
        }
    }

    //  Load profile + stats in parallel 
    fun loadMyProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val profileDeferred = async {
                    try { api.getMyProfile().data?.profile } catch (e: Exception) { null }
                }
                val statsDeferred = async {
                    try { api.getMyStats().data?.stats } catch (e: Exception) { null }
                }
                _myProfile.value = profileDeferred.await()
                _myStats.value = statsDeferred.await()
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur chargement profil"
            } finally {
                _isLoading.value = false
            }
        }
    }

    //  Update profile 

    fun updateProfile(
        name: String? = null,
        bio: String? = null,
        jobTitle: String? = null,
        company: String? = null,
        profilePicture: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val fields = mutableMapOf<String, String>()
                name?.let { fields["name"] = it }
                bio?.let { fields["bio"] = it }
                jobTitle?.let { fields["jobTitle"] = it }
                company?.let { fields["company"] = it }
                profilePicture?.let { fields["profilePicture"] = it }

                if (fields.isNotEmpty()) {
                    val resp = api.updateMyProfile(fields)
                    _myProfile.value = resp.data?.profile
                    _updateSuccess.value = "Profil mis à jour"
                    // Update local prefs if name changed
                    name?.let { preferencesManager.saveUserName(it) }
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur mise à jour"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun uploadAvatar(context: Context, uri: Uri) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val stream = context.contentResolver.openInputStream(uri)
                    ?: run {
                        _error.value = "Impossible d'ouvrir l'image"
                        return@launch
                    }
                val bytes = stream.readBytes()
                stream.close()

                val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
                val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
                val fileName = "avatar_${System.currentTimeMillis()}.jpg"
                val part = MultipartBody.Part.createFormData(
                    "avatar",
                    fileName,
                    requestBody
                )

                val response = api.uploadAvatar(part)
                if (response.isSuccessful) {
                    loadMyProfile()
                } else {
                    _error.value = "Échec upload photo"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur upload photo"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteAvatar() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val response = api.deleteAvatar()
                if (response.isSuccessful) {
                    loadMyProfile()
                } else {
                    _error.value = "Échec suppression photo"
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur suppression photo"
            } finally {
                _isLoading.value = false
            }
        }
    }

    //  Logout 

    fun logout() {
        viewModelScope.launch {
            RealtimeNotificationService.stop(appContext)
            wsClient.disconnect()
            NotificationBackgroundScheduler.stop(appContext)
            NotificationDisplayStore.clear(appContext)
            authRepository.logout(appContext)
        }
    }

    //  Utils 

    fun clearError() { _error.value = null }
    fun clearUpdateSuccess() { _updateSuccess.value = null }
}