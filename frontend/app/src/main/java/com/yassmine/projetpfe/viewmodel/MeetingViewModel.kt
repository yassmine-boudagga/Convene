package com.yassmine.projetpfe.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.CreateMeetingRequest
import com.yassmine.projetpfe.data.api.JoinedParticipantDto
import com.yassmine.projetpfe.data.api.MeetingDto
import com.yassmine.projetpfe.data.api.MeetingNoteDto
import com.yassmine.projetpfe.data.api.RecordingStatusResponse
import com.yassmine.projetpfe.data.api.UpdateMeetingRequest
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.repository.MeetingRepository
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class MeetingViewModel @Inject constructor(
    private val apiService: ApiService,
    private val meetingRepository: MeetingRepository,
    private val preferencesManager: PreferencesManager,
    private val wsClient: NotificationWebSocketClient,
) : ViewModel() {

    private fun isSummaryReadyStatus(aiStatus: String?): Boolean {
        return aiStatus == "completed"
    }

    private val _meetings = MutableStateFlow<List<MeetingDto>>(emptyList())
    val meetings: StateFlow<List<MeetingDto>> = _meetings.asStateFlow()

    private val _hasMorePast = MutableStateFlow(false)
    val hasMorePast: StateFlow<Boolean> = _hasMorePast.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private var currentPastPage = 1

    private val _archivedMeetings = MutableStateFlow<List<MeetingDto>>(emptyList())
    val archivedMeetings: StateFlow<List<MeetingDto>> = _archivedMeetings.asStateFlow()

    private val _isArchivedLoading = MutableStateFlow(false)
    val isArchivedLoading: StateFlow<Boolean> = _isArchivedLoading.asStateFlow()

    private val _selectedMeeting = MutableStateFlow<MeetingDto?>(null)
    val selectedMeeting: StateFlow<MeetingDto?> = _selectedMeeting.asStateFlow()

    private val _notes = MutableStateFlow<List<MeetingNoteDto>>(emptyList())
    val notes: StateFlow<List<MeetingNoteDto>> = _notes.asStateFlow()

    private val _recordingStatus = MutableStateFlow<RecordingStatusResponse?>(null)
    @Suppress("unused")
    val recordingStatus: StateFlow<RecordingStatusResponse?> = _recordingStatus.asStateFlow()

    private val _recordingUrl = MutableStateFlow<String?>(null)
    val recordingUrl: StateFlow<String?> = _recordingUrl.asStateFlow()

    private val _recordingLoading = MutableStateFlow(false)
    val recordingLoading: StateFlow<Boolean> = _recordingLoading.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _operationSuccess = MutableStateFlow(false)
    val operationSuccess: StateFlow<Boolean> = _operationSuccess.asStateFlow()

    private val _isSummaryAvailable = MutableStateFlow(false)
    val isSummaryAvailable: StateFlow<Boolean> = _isSummaryAvailable.asStateFlow()

    private val _snackbarMessage = MutableStateFlow<String?>(null)
    val snackbarMessage: StateFlow<String?> = _snackbarMessage.asStateFlow()

    // ID de l'utilisateur courant (pour vérifier les permissions)
    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _authToken = MutableStateFlow<String?>(null)
    val authToken: StateFlow<String?> = _authToken.asStateFlow()

    private var meetingDetailRealtimeJob: Job? = null
    private var lastMeetingsStatusFilter: String? = null
    private val getMeetingRequestGeneration = AtomicInteger(0)

    init {
        viewModelScope.launch {
            _currentUserId.value = preferencesManager.getUserId().first()
        }

        viewModelScope.launch {
            _authToken.value = preferencesManager.getToken().first()
        }

        observeSummaryReadyEvents()
        observeMeetingStatusChangedEvents()
        observeNoteAddedEvents()
        observeMeetingNotificationEvents()
    }

    private fun observeNoteAddedEvents() {
        viewModelScope.launch {
            wsClient.noteAddedEvents.collect { event ->
                val selectedId = _selectedMeeting.value?.realId ?: return@collect
                if (selectedId == event.meetingId) {
                    loadNotes(selectedId)
                    getMeetingById(selectedId)
                }
            }
        }
    }

    private fun observeMeetingStatusChangedEvents() {
        viewModelScope.launch {
            wsClient.meetingStatusChangedEvents.collect {
                loadMeetings(lastMeetingsStatusFilter)
                if (_archivedMeetings.value.isNotEmpty()) {
                    loadArchivedMeetings(force = true)
                }
            }
        }
    }

    private fun observeSummaryReadyEvents() {
        viewModelScope.launch {
            wsClient.aiSummaryReadyEvents.collect { event ->
                val selectedId = _selectedMeeting.value?.realId
                if (selectedId == null || selectedId != event.meetingId) return@collect

                val currentAiStatus = _selectedMeeting.value?.aiStatus
                if (currentAiStatus == "completed_empty") return@collect

                _isSummaryAvailable.value = true
                _selectedMeeting.update { meeting ->
                    meeting?.copy(aiStatus = "completed")
                }
                _snackbarMessage.value = "Le résumé IA est prêt !"
            }
        }
    }

    private fun observeMeetingNotificationEvents() {
        viewModelScope.launch {
            wsClient.notifications.collect { notif ->
                when (notif.type) {
                    "meeting_created",
                    "meeting_updated",
                    "meeting_cancelled" -> {
                        loadMeetings(lastMeetingsStatusFilter)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun loadMeetings(status: String? = null) {
        lastMeetingsStatusFilter = status
        // Réinitialiser la pagination Past à chaque chargement initial
        if (status == "finished") {
            currentPastPage = 1
        }
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val result = meetingRepository.getMeetings(status, page = 1, limit = 20)
            if (result.isSuccess) {
                val response = result.getOrThrow()
                _meetings.value = response.meetings
                if (status == "finished") {
                    val totalPages = response.pagination?.totalPages ?: 1
                    _hasMorePast.value = totalPages > 1
                    currentPastPage = 1
                } else {
                    _hasMorePast.value = false
                }
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Erreur chargement"
            }
            _isLoading.value = false
        }
    }

    fun loadMorePastMeetings() {
        if (_isLoadingMore.value || !_hasMorePast.value) return
        viewModelScope.launch {
            _isLoadingMore.value = true
            val nextPage = currentPastPage + 1
            val result = meetingRepository.getMeetings(
                status = "finished",
                page = nextPage,
                limit = 20
            )
            if (result.isSuccess) {
                val response = result.getOrThrow()
                // Accumuler les nouvelles réunions à la liste existante
                _meetings.value = _meetings.value + response.meetings
                currentPastPage = nextPage
                val totalPages = response.pagination?.totalPages ?: 1
                _hasMorePast.value = nextPage < totalPages
            }
            _isLoadingMore.value = false
        }
    }

    fun loadArchivedMeetings(force: Boolean = false) {
        if (!force && _archivedMeetings.value.isNotEmpty()) return

        viewModelScope.launch {
            _isArchivedLoading.value = true
            val result = meetingRepository.getMeetings(status = "archived")
            if (result.isSuccess) {
                _archivedMeetings.value = result.getOrThrow().meetings
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Erreur chargement archives"
            }
            _isArchivedLoading.value = false
        }
    }

    fun getMeetingById(id: String) {
        val generation = getMeetingRequestGeneration.incrementAndGet()
        viewModelScope.launch {
            if (generation == getMeetingRequestGeneration.get()) {
                _isLoading.value = true
                _error.value = null
            }
            try {
                val result = meetingRepository.getMeeting(id)
                if (generation != getMeetingRequestGeneration.get()) return@launch

                if (result.isSuccess) {
                    val incoming = result.getOrThrow()
                    val currentAiStatus = _selectedMeeting.value?.aiStatus

                    // Priorité des statuts (du plus avancé au moins avancé)
                    val aiStatusPriority = listOf("completed", "completed_empty", "failed", "processing", "not_started", null)
                    val incomingPriority = aiStatusPriority.indexOf(incoming.aiStatus)
                    val currentPriority = aiStatusPriority.indexOf(currentAiStatus)

                    val finalAiStatus = if (currentPriority != -1 && incomingPriority != -1 && currentPriority < incomingPriority) {
                        // L'état local est plus avancé, on le garde
                        currentAiStatus
                    } else {
                        incoming.aiStatus
                    }

                    val finalMeeting = if (finalAiStatus != incoming.aiStatus) {
                        incoming.copy(aiStatus = finalAiStatus)
                    } else {
                        incoming
                    }

                    _selectedMeeting.value = finalMeeting
                    _isSummaryAvailable.value = isSummaryReadyStatus(finalMeeting.aiStatus)
                } else {
                    _error.value = result.exceptionOrNull()?.message ?: "Réunion introuvable"
                    _isSummaryAvailable.value = false
                }
            } finally {
                if (generation == getMeetingRequestGeneration.get()) {
                    _isLoading.value = false
                }
            }
        }
    }

    fun loadMeeting(meetingId: String) {
        getMeetingById(meetingId)
    }

    fun startMeetingDetailRealtime(meetingId: String) {
        meetingDetailRealtimeJob?.cancel()
        meetingDetailRealtimeJob = viewModelScope.launch {
            launch {
                wsClient.participantPresenceEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        val userId = event.userId
                        if (userId != null) {
                            _selectedMeeting.update { meeting ->
                                if (meeting == null) return@update meeting
                                val currentJoined = meeting.joinedParticipants.toMutableList()
                                if (event.joined) {
                                    val alreadyPresent = currentJoined.any {
                                        it.id?.trim()?.lowercase() == userId.trim().lowercase() ||
                                        it.email?.trim()?.lowercase() == userId.trim().lowercase()
                                    }
                                    if (!alreadyPresent) {
                                        currentJoined.add(JoinedParticipantDto(id = userId))
                                    }
                                } else {
                                    currentJoined.removeAll {
                                        it.id?.trim()?.lowercase() == userId.trim().lowercase() ||
                                        it.email?.trim()?.lowercase() == userId.trim().lowercase()
                                    }
                                }
                                meeting.copy(joinedParticipants = currentJoined)
                            }
                        }
                        loadMeeting(meetingId)
                    }
                }
            }

            launch {
                wsClient.meetingForceEndEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        // Mise à jour optimiste : si aiStatus est not_started ou null,
                        // le pipeline va démarrer — afficher "processing" immédiatement
                        _selectedMeeting.update { meeting ->
                            if (meeting != null &&
                                (meeting.aiStatus.isNullOrBlank() || meeting.aiStatus == "not_started")
                            ) {
                                meeting.copy(aiStatus = "processing")
                            } else {
                                meeting
                            }
                        }
                        getMeetingById(meetingId)
                        retryLoadRecordingInfoIfNeeded(meetingId, maxAttempts = 3)
                    }
                }
            }

            launch {
                wsClient.aiSummaryReadyEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        _isSummaryAvailable.value = true
                        _selectedMeeting.update { meeting ->
                            meeting?.copy(aiStatus = "completed")
                        }
                        getMeetingById(meetingId)
                        loadRecordingInfo(meetingId)
                    }
                }
            }
            launch {
                wsClient.aiSummaryEmptyEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        _selectedMeeting.update { meeting ->
                            meeting?.copy(aiStatus = "completed_empty")
                        }
                        _isSummaryAvailable.value = false
                        getMeetingById(meetingId)
                    }
                }
            }
            launch {
                wsClient.aiSummaryFailedEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        _selectedMeeting.update { meeting ->
                            meeting?.copy(aiStatus = "failed")
                        }
                        _isSummaryAvailable.value = false
                        getMeetingById(meetingId)
                    }
                }
            }
            launch {
                wsClient.recordingStateEvents.collect { event ->
                    if (event.meetingId == meetingId && !event.isRecording) {
                        retryLoadRecordingInfoIfNeeded(meetingId)
                    }
                }
            }
            launch {
                wsClient.reconnectedEvent.collect {
                    // WS vient de se reconnecter : rafraîchir les données de la réunion
                    getMeetingById(meetingId)
                }
            }

            // Écoute recording_available WS
            launch {
                wsClient.recordingAvailableEvents.collect { eventMeetingId ->
                    if (eventMeetingId == meetingId) {
                        loadRecordingInfo(meetingId)
                        getMeetingById(meetingId)
                    }
                }
            }

            // Écoute les notifications recording_ready via le flow
            launch {
                wsClient.notifications.collect { notif ->
                    if (
                        notif.data?.meetingId == meetingId &&
                        notif.type == "recording_ready"
                    ) {
                        loadRecordingInfo(meetingId)
                        getMeetingById(meetingId)
                    }
                }
            }
        }
    }
    fun retryLoadRecordingInfoIfNeeded(meetingId: String, maxAttempts: Int = 3) {
        viewModelScope.launch {
            val delaysMs = longArrayOf(3_000, 8_000, 20_000)
            for (attempt in 0 until minOf(maxAttempts, 3)) {
                delay(delaysMs[attempt.coerceAtMost(delaysMs.size - 1)])
                loadRecordingInfo(meetingId)
                if (!_recordingUrl.value.isNullOrBlank()) break
            }
        }
    }

    fun createMeeting(request: CreateMeetingRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _operationSuccess.value = false
            val result = meetingRepository.createMeeting(request)
            if (result.isSuccess) {
                _operationSuccess.value = true
                loadMeetings()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Erreur création"
            }
            _isLoading.value = false
        }
    }

    fun updateMeeting(id: String, request: UpdateMeetingRequest) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = meetingRepository.updateMeeting(id, request)
            if (result.isSuccess) {
                _selectedMeeting.value = result.getOrThrow()
                _operationSuccess.value = true
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Erreur modification"
            }
            _isLoading.value = false
        }
    }

    fun cancelMeeting(meetingId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val result = meetingRepository.cancelMeeting(meetingId)
            if (result.isSuccess) {
                _operationSuccess.value = true
                loadMeetings()
            } else {
                _error.value = result.exceptionOrNull()?.message ?: "Erreur annulation"
            }
            _isLoading.value = false
        }
    }

    //  Notes 
    fun loadNotes(meetingId: String) {
        viewModelScope.launch {
            val result = meetingRepository.getNotes(meetingId)
            if (result.isSuccess) {
                _notes.value = result.getOrThrow()
            }
        }
    }

    fun addNote(meetingId: String, content: String) {
        viewModelScope.launch {
            val result = meetingRepository.addNote(meetingId, content)
            if (result.isSuccess) {
                // Recharger les notes
                loadNotes(meetingId)
            } else {
                _error.value = result.exceptionOrNull()?.message
            }
        }
    }

    fun loadRecordingInfo(meetingId: String) {
        viewModelScope.launch {
            _recordingLoading.value = true
            try {
                val response = apiService.getRecordingInfo(meetingId)
                if (response.isSuccessful) {
                    val body = response.body()
                    val data = body?.data
                    _recordingStatus.value = RecordingStatusResponse(
                        status = data?.status ?: "none",
                        recordingUrl = data?.recordingUrl
                    )
                    val rawUrl = data?.recordingUrl
                    if (!rawUrl.isNullOrBlank()) {
                        _recordingUrl.value = if (rawUrl.startsWith("http", ignoreCase = true)) {
                            rawUrl
                        } else {
                            val base = BuildConfig.WS_BASE_URL.trimEnd('/')
                            if (rawUrl.startsWith('/')) "$base$rawUrl" else "$base/$rawUrl"
                        }
                    } else {
                        _recordingUrl.value = null
                    }
                }
            } catch (e: Exception) {
                Log.e("MeetingDetailVM", "Erreur chargement recording", e)
            } finally {
                _recordingLoading.value = false
            }
        }
    }

    fun resetOperationSuccess() {
        _operationSuccess.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSnackbarMessage() {
        _snackbarMessage.value = null
    }
}