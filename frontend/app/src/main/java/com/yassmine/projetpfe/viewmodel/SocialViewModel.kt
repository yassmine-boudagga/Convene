package com.yassmine.projetpfe.viewmodel

import androidx.compose.runtime.mutableStateMapOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.FriendRequest
import com.yassmine.projetpfe.data.api.PublicProfile
import com.yassmine.projetpfe.data.api.UserSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SocialViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    //  States 
    private val _searchResults = MutableStateFlow<List<UserSearchResult>>(emptyList())
    val searchResults: StateFlow<List<UserSearchResult>> = _searchResults.asStateFlow()

    private val _friends = MutableStateFlow<List<UserSearchResult>>(emptyList())
    val friends: StateFlow<List<UserSearchResult>> = _friends.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<FriendRequest>>(emptyList())
    val pendingRequests: StateFlow<List<FriendRequest>> = _pendingRequests.asStateFlow()

    private val _myProfile = MutableStateFlow<PublicProfile?>(null)
    val myProfile: StateFlow<PublicProfile?> = _myProfile.asStateFlow()

    private val _viewedProfile = MutableStateFlow<PublicProfile?>(null)
    val viewedProfile: StateFlow<PublicProfile?> = _viewedProfile.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow<String?>(null)
    val operationSuccess: StateFlow<String?> = _operationSuccess.asStateFlow()

    // Cache session local: évite l'écrasement visuel des statuts sociaux
    // lors des rechargements réseau après navigation.
    private val _friendStatusCache = mutableStateMapOf<String, String>()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        searchUsers(query)
    }

    private var searchJob: Job? = null

    //  Search with debounce 

    fun searchUsers(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _isLoading.value = true
            _error.value = null
            try {
                val resp = api.searchUsers(query)
                val networkResults = resp.data?.users ?: emptyList()
                _searchResults.value = networkResults.map { user ->
                    val cachedStatus = _friendStatusCache[user.id]
                    if (cachedStatus != null) user.copy(friendStatus = cachedStatus) else user
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur recherche"
            }
            _isLoading.value = false
        }
    }

    //  Friends 

    fun loadFriends() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val resp = api.getMyFriends()
                _friends.value = resp.data?.friends ?: emptyList()
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur chargement amis"
            }
            _isLoading.value = false
        }
    }

    //  Pending requests 

    //  Profiles 

    fun loadMyProfile() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = api.getMyProfile()
                _myProfile.value = resp.data?.profile
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur profil"
            }
            _isLoading.value = false
        }
    }

    fun loadProfile(userId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _viewedProfile.value = null
            try {
                val resp = api.getUserProfile(userId)
                val profile = resp.data?.profile
                val cachedStatus = _friendStatusCache[userId]
                _viewedProfile.value = if (profile != null && cachedStatus != null) {
                    profile.copy(friendStatus = cachedStatus)
                } else {
                    profile
                }
            } catch (e: Exception) {
                _error.value = e.message ?: "Profil introuvable"
            }
            _isLoading.value = false
        }
    }

    fun updateMyProfile(fields: Map<String, String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val resp = api.updateMyProfile(fields)
                _myProfile.value = resp.data?.profile
                _operationSuccess.value = "Profil mis à jour"
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur mise à jour"
            }
            _isLoading.value = false
        }
    }

    //  Friend request actions 

    fun sendFriendRequest(userId: String) {
        viewModelScope.launch {
            try {
                api.sendFriendRequest(userId)
                _operationSuccess.value = "Demande envoyée"
                updateFriendStatusLocally(userId, "pending_sent")
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur envoi demande"
            }
        }
    }

    fun acceptRequest(fromUserId: String) {
        viewModelScope.launch {
            try {
                api.acceptFriendRequest(fromUserId)
                _operationSuccess.value = "Demande acceptée"
                _pendingRequests.value = _pendingRequests.value.filter { it.from.id != fromUserId }
                updateFriendStatusLocally(fromUserId, "friends")
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur acceptation"
            }
        }
    }

    fun cancelFriendRequest(userId: String) {
        viewModelScope.launch {
            try {
                api.rejectFriendRequest(userId)
                _operationSuccess.value = "Demande annulée"
                _pendingRequests.value = _pendingRequests.value.filter { it.from.id != userId }
                updateFriendStatusLocally(userId, "none")
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur suppression demande"
            }
        }
    }

    fun rejectRequest(userId: String) {
        viewModelScope.launch {
            try {
                api.rejectFriendRequest(userId)
                _operationSuccess.value = "Demande refusée"
                _pendingRequests.value = _pendingRequests.value.filter { it.from.id != userId }
                updateFriendStatusLocally(userId, "none")
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur refus demande"
            }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            try {
                val resp = api.removeFriend(userId)
                if (!resp.isSuccessful) {
                    throw IllegalStateException("Erreur suppression amitie")
                }
                _operationSuccess.value = "Amitié supprimée"
                updateFriendStatusLocally(userId, "none")
            } catch (e: Exception) {
                _error.value = e.message ?: "Erreur suppression amitié"
            }
        }
    }

    private fun updateFriendStatusLocally(userId: String, newStatus: String) {
        _friendStatusCache[userId] = newStatus

        _searchResults.value = _searchResults.value.map { user ->
            if (user.id == userId) user.copy(friendStatus = newStatus) else user
        }

        if (_viewedProfile.value?.id == userId) {
            _viewedProfile.value = _viewedProfile.value?.copy(friendStatus = newStatus)
        }

        if (newStatus == "friends") {
            viewModelScope.launch { loadFriends() }
        } else if (newStatus == "none") {
            _friends.value = _friends.value.filter { it.id != userId }
        }
    }

    //  Refresh invitation statuses (au retour de PublicProfileScreen) 

    fun refreshInvitationStatuses() {
        // Met à jour les statuts des résultats courants sans refaire la recherche API
        val currentResults = _searchResults.value
        if (currentResults.isEmpty()) return

        val userIds = currentResults.map { it.id }
        viewModelScope.launch {
            try {
                // Refetch les profils publics pour mettre à jour les statuts
                val updated = mutableListOf<UserSearchResult>()
                for (userId in userIds) {
                    try {
                        val resp = api.getUserProfile(userId)
                        val profile = resp.data?.profile
                        if (profile != null) {
                            val cachedStatus = _friendStatusCache[userId]
                            val newStatus = cachedStatus ?: profile.friendStatus
                            _friendStatusCache[userId] = newStatus
                            updated.add(
                                UserSearchResult(
                                    id = profile.id,
                                    name = profile.name,
                                    email = profile.email,
                                    profilePicture = profile.profilePicture,
                                    bio = profile.bio,
                                    jobTitle = profile.jobTitle,
                                    company = profile.company,
                                    friendStatus = newStatus
                                )
                            )
                        }
                    } catch (e: Exception) {
                        // Ignorer les erreurs individuelles, conserver le statut en cache
                    }
                }
                // Appliquer les statuts mis à jour uniquement si on a des résultats
                if (updated.isNotEmpty()) {
                    _searchResults.value = _searchResults.value.map { user ->
                        updated.firstOrNull { it.id == user.id }?.copy(friendStatus = _friendStatusCache[user.id] ?: user.friendStatus) ?: user
                    }
                }
            } catch (e: Exception) {
                // Erreur silencieuse — ne pas déranger l'utilisateur
            }
        }
    }

    //  Utils 

    fun clearError() { _error.value = null }
    fun clearSuccess() { _operationSuccess.value = null }
}
