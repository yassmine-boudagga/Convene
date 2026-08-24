package com.yassmine.projetpfe.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.UserSearchResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class UserSummary(
    val id: String,
    val name: String,
    val email: String,
    val profilePicture: String? = null,
    val jobTitle: String? = null,
    val company: String? = null,
    val isOnline: Boolean = false
)

data class FriendInvitation(
    val invitationId: String,
    val user: UserSummary,
    val createdAt: String? = null
)

@HiltViewModel
class FriendsViewModel @Inject constructor(
    private val api: ApiService
) : ViewModel() {

    private val _friends = MutableStateFlow<List<UserSummary>>(emptyList())
    val friends: StateFlow<List<UserSummary>> = _friends.asStateFlow()

    private val _receivedInvitations = MutableStateFlow<List<FriendInvitation>>(emptyList())
    val receivedInvitations: StateFlow<List<FriendInvitation>> = _receivedInvitations.asStateFlow()

    private val _sentInvitations = MutableStateFlow<List<FriendInvitation>>(emptyList())
    val sentInvitations: StateFlow<List<FriendInvitation>> = _sentInvitations.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _success = MutableStateFlow<String?>(null)
    val success: StateFlow<String?> = _success.asStateFlow()

    init {
        refreshAll()
    }

    fun onSearchQueryChange(value: String) {
        _searchQuery.value = value
    }

    fun refreshAll() {
        viewModelScope.launch {
            _isLoading.value = true
            fetchFriends()
            fetchReceivedInvitations()
            fetchSentInvitations()
            _isLoading.value = false
        }
    }

    fun loadFriends() {
        viewModelScope.launch {
            fetchFriends()
        }
    }

    fun acceptInvitation(invitationId: String) {
        viewModelScope.launch {
            runCatching {
                api.acceptFriendRequest(invitationId)
            }.onSuccess {
                _success.value = "Invitation acceptée"
                refreshAll()
            }.onFailure {
                _error.value = it.message ?: "Erreur acceptation invitation"
            }
        }
    }

    fun declineInvitation(invitationId: String) {
        viewModelScope.launch {
            runCatching {
                api.rejectFriendRequest(invitationId)
            }.onSuccess {
                _success.value = "Invitation refusée"
                refreshAll()
            }.onFailure {
                _error.value = it.message ?: "Erreur refus invitation"
            }
        }
    }

    fun cancelInvitation(invitationId: String) {
        viewModelScope.launch {
            runCatching {
                api.rejectFriendRequest(invitationId)
            }.onSuccess {
                _success.value = "Invitation annulée"
                refreshAll()
            }.onFailure {
                _error.value = it.message ?: "Erreur annulation invitation"
            }
        }
    }

    fun removeFriend(userId: String) {
        viewModelScope.launch {
            runCatching {
                api.removeFriend(userId)
            }.onSuccess {
                _success.value = "Ami retiré"
                refreshAll()
            }.onFailure {
                _error.value = it.message ?: "Erreur suppression ami"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSuccess() {
        _success.value = null
    }

    private fun UserSearchResult.toUserSummary(): UserSummary {
        return UserSummary(
            id = id,
            name = name,
            email = email,
            profilePicture = profilePicture,
            jobTitle = jobTitle,
            company = company,
            isOnline = false
        )
    }

    private suspend fun fetchFriends() {
        runCatching {
            api.getMyFriends()
        }.onSuccess { response ->
            _friends.value = response.data?.friends.orEmpty().map { it.toUserSummary() }
        }.onFailure {
            _error.value = it.message ?: "Erreur chargement amis"
        }
    }

    private suspend fun fetchReceivedInvitations() {
        runCatching {
            api.getPendingFriendRequests()
        }.onSuccess { response ->
            _receivedInvitations.value = response.data?.requests.orEmpty().map { request ->
                FriendInvitation(
                    invitationId = request.from.id,
                    user = request.from.toUserSummary(),
                    createdAt = request.createdAt
                )
            }
        }.onFailure {
            _error.value = it.message ?: "Erreur chargement invitations reçues"
        }
    }

    private suspend fun fetchSentInvitations() {
        runCatching {
            api.getSentFriendRequests()
        }.onSuccess { response ->
            _sentInvitations.value = response.data?.requests.orEmpty().map { request ->
                FriendInvitation(
                    invitationId = request.to.id,
                    user = request.to.toUserSummary(),
                    createdAt = request.createdAt
                )
            }
        }.onFailure {
            _sentInvitations.value = emptyList()
            _error.value = it.message ?: "Erreur chargement invitations envoyées"
        }
    }
}
