package com.yassmine.projetpfe.viewmodel

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.api.MeetingDto
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import javax.inject.Inject

@HiltViewModel
class InPersonMeetingViewModel @Inject constructor(
    private val apiService: ApiService,
    private val webSocketClient: NotificationWebSocketClient,
    private val preferencesManager: PreferencesManager,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val meeting = MutableStateFlow<MeetingDto?>(null)
    val uiState = MutableStateFlow<InPersonUiState>(InPersonUiState.Loading)
    val isRecording = MutableStateFlow(false)
    val recordingDuration = MutableStateFlow(0L)
    val uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val currentUserId = MutableStateFlow<String?>(null)
    val forceEndCountdown = MutableStateFlow<Int?>(null)
    val forceEndReason = MutableStateFlow<String?>(null)
    val localNoteTimestamps = MutableStateFlow<Map<String, Long>>(emptyMap())
    val manualStopUsed = MutableStateFlow(false)
    val stopSuccessMessage = MutableStateFlow<String?>(null)
    val recordingStoppedByHost = MutableStateFlow(false)

    private var mediaRecorder: MediaRecorder? = null
    private var recordingFile: File? = null
    private var recordingTimer: Job? = null
    private var webSocketJob: Job? = null
    private var heartbeatJob: Job? = null
    private var finishAfterUploadPending: Boolean = false
    private var activeMeetingId: String? = null
    private val loadMeetingGeneration = AtomicInteger(0)

    sealed class InPersonUiState {
        data object Loading : InPersonUiState()
        data class Active(val meeting: MeetingDto) : InPersonUiState()
        data object Finished : InPersonUiState()
        data class Error(val message: String) : InPersonUiState()
    }

    sealed class UploadState {
        data object Idle : UploadState()
        data object Uploading : UploadState()
        data object Success : UploadState()
        data class Error(val message: String) : UploadState()
    }

    init {
        viewModelScope.launch {
            currentUserId.value = preferencesManager.getUserId().first()
        }
    }

    fun loadMeeting(meetingId: String) {
        val generation = loadMeetingGeneration.incrementAndGet()
        viewModelScope.launch {
            try {
                val response = apiService.getMeeting(meetingId)
                if (generation != loadMeetingGeneration.get()) return@launch
                val m = response.data.meeting
                if (m != null) {
                    meeting.value = m
                    val suppressAutoLeaveNavigation = !forceEndReason.value.isNullOrBlank()
                    uiState.value = if (m.status == "finished" && !suppressAutoLeaveNavigation) {
                        InPersonUiState.Finished
                    } else {
                        InPersonUiState.Active(m)
                    }
                } else {
                    uiState.value = InPersonUiState.Error("Réunion introuvable")
                }
            } catch (e: Exception) {
                if (generation != loadMeetingGeneration.get()) return@launch
                uiState.value = InPersonUiState.Error(e.message ?: "Erreur chargement")
            }
        }
    }

    fun joinMeeting(meetingId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.joinPhysicalMeeting(meetingId)
                if (!response.isSuccessful) {
                    uiState.value = InPersonUiState.Error("Erreur join: ${response.code()}")
                    return@launch
                }
                loadMeeting(meetingId)
                startHeartbeat(meetingId)
            } catch (e: Exception) {
                Log.e("InPersonVM", "Erreur join", e)
                uiState.value = InPersonUiState.Error(e.message ?: "Erreur join")
            }
        }
    }

    fun leaveMeeting(meetingId: String) {
        stopHeartbeat()
        recordingStoppedByHost.value = false
        viewModelScope.launch {
            try {
                val isHost = meeting.value?.createdBy?.realId == currentUserId.value
                if (isHost && isRecording.value) {
                    finishAfterUploadPending = true
                    stopRecording()
                    uploadRecording(meetingId)
                    return@launch
                }
                apiService.leavePhysicalMeeting(meetingId)
                uiState.value = InPersonUiState.Finished
            } catch (e: Exception) {
                Log.e("InPersonVM", "Erreur leave", e)
            }
        }
    }

    fun leaveAndExit(onDone: () -> Unit) {
        val meetingId = activeMeetingId ?: run {
            onDone()
            return
        }
        viewModelScope.launch {
            try {
                apiService.leavePhysicalMeeting(meetingId)
            } catch (_: Exception) {
                // Navigation même en cas d'erreur réseau
            } finally {
                onDone()
            }
        }
    }

    fun stopAndUploadAndFinish(meetingId: String) {
        val isHost = meeting.value?.createdBy?.realId == currentUserId.value
        if (!isHost) return
        if (manualStopUsed.value || uploadState.value is UploadState.Uploading) return

        viewModelScope.launch {
            val stopOk = stopRecordingOnServer(meetingId)
            if (!stopOk) return@launch

            manualStopUsed.value = true
            stopSuccessMessage.value = "Enregistrement arrêté."

            finishAfterUploadPending = false
            if (isRecording.value) {
                stopRecording()
            }
            uploadRecording(meetingId)
        }
    }

    fun consumeStopSuccessMessage() {
        stopSuccessMessage.value = null
    }

    private suspend fun stopRecordingOnServer(meetingId: String): Boolean {
        return try {
            val response = apiService.stopRecording(meetingId)
            if (response.success) {
                true
            } else {
                val message = response.message?.takeIf { it.isNotBlank() }
                    ?: "Impossible d'arrêter l'enregistrement"
                uiState.value = InPersonUiState.Error(message)
                false
            }
        } catch (e: Exception) {
            uiState.value = InPersonUiState.Error(e.message ?: "Erreur arrêt enregistrement")
            false
        }
    }

    fun addNote(meetingId: String, content: String) {
        viewModelScope.launch {
            try {
                val response = apiService.addNote(
                    meetingId,
                    com.yassmine.projetpfe.data.api.AddNoteRequest(content)
                )
                val createdNote = response.data.note
                if (createdNote.timestamp.isBlank()) {
                    val noteId = createdNote.realId.ifBlank { "local-${System.currentTimeMillis()}" }
                    localNoteTimestamps.value = localNoteTimestamps.value + (noteId to System.currentTimeMillis())
                }
                loadMeeting(meetingId)
            } catch (e: Exception) {
                Log.e("InPersonVM", "Erreur addNote", e)
            }
        }
    }

    fun startRecording() {
        if (isRecording.value) {
            Log.w("InPersonVM", "startRecording() ignoré : recording déjà actif")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            uiState.value = InPersonUiState.Error("Enregistrement impossible. API 29 minimum requise pour OGG.")
            return
        }

        val outputDir = File(appContext.getExternalFilesDir(null), "recordings")
        outputDir.mkdirs()
        val file = File(outputDir, "physical-${System.currentTimeMillis()}.ogg")
        recordingFile = file

        try {
            recordingDuration.value = 0L
            uploadState.value = UploadState.Idle

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.OGG)
                setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                setAudioSamplingRate(16000)
                setAudioChannels(1)
                setAudioEncodingBitRate(64000)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }

            isRecording.value = true
            startRecordingTimer()

        } catch (e: Exception) {
            Log.e("InPersonVM", "Impossible de démarrer MediaRecorder OGG", e)
            releaseRecorder()
            try {
                if (recordingFile?.exists() == true && recordingFile?.length() == 0L) {
                    recordingFile?.delete()
                }
            } catch (_: Exception) {
            }
            recordingFile = null
            uiState.value = InPersonUiState.Error(
                "Enregistrement impossible. Appareil incompatible OGG(API 29 requis)."
            )
        }
    }

    fun stopRecording() {
        if (!isRecording.value) return
        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.e("InPersonVM", "Erreur arrêt MediaRecorder", e)
        } finally {
            releaseRecorder()
        }
        isRecording.value = false
        stopRecordingTimer()
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.e("InPersonVM", "Erreur release recorder", e)
        } finally {
            mediaRecorder = null
        }
    }

    private fun startRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = viewModelScope.launch {
            while (isRecording.value) {
                delay(1000L)
                recordingDuration.value += 1
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimer?.cancel()
        recordingTimer = null
    }

    fun uploadRecording(meetingId: String) {
        val file = recordingFile ?: return
        if (!file.exists()) return

        val isHost = meeting.value?.createdBy?.realId == currentUserId.value
        if (!isHost) return

        viewModelScope.launch {
            uploadState.value = UploadState.Uploading
            try {
                val requestFile = file.asRequestBody("audio/ogg".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val durationBody = recordingDuration.value
                    .coerceAtLeast(0L)
                    .toString()
                    .toRequestBody("text/plain".toMediaTypeOrNull())

                val response = apiService.uploadPhysicalRecording(meetingId, body, durationBody)
                if (response.isSuccessful && response.body()?.data?.success == true) {
                    uploadState.value = UploadState.Success
                    file.delete()
                    recordingFile = null

                    if (finishAfterUploadPending) {
                        finishAfterUploadPending = false
                        delay(500L)
                        leaveAndFinishMeeting(meetingId)
                    }
                } else {
                    finishAfterUploadPending = false
                    uploadState.value = UploadState.Error("Erreur upload : ${response.code()}")
                }
            } catch (e: Exception) {
                finishAfterUploadPending = false
                uploadState.value = UploadState.Error(e.message ?: "Erreur inconnue")
            }
        }
    }

    private fun leaveAndFinishMeeting(meetingId: String) {
        viewModelScope.launch {
            try {
                val response = apiService.leavePhysicalMeeting(meetingId)
                if (!response.isSuccessful) {
                    uploadState.value = UploadState.Error("Finalisation impossible (${response.code()})")
                }
            } catch (e: Exception) {
                Log.e("InPersonVM", "Erreur leave après upload", e)
                uploadState.value = UploadState.Error("Échec finalisation réunion: ${e.message ?: "erreur inconnue"}")
            }
        }
    }

    fun finishFromForceEnd() {
        recordingStoppedByHost.value = false
        forceEndCountdown.value = null
        forceEndReason.value = null
        uiState.value = InPersonUiState.Finished
    }

    private fun startHeartbeat(meetingId: String) {
        heartbeatJob?.cancel()
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(45_000L)
                try {
                    apiService.sendHeartbeat(meetingId)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private suspend fun waitForUploadToSettle(maxWaitMs: Long = 45_000L) {
        val start = System.currentTimeMillis()
        while (uploadState.value is UploadState.Uploading && (System.currentTimeMillis() - start) < maxWaitMs) {
            delay(200L)
        }
    }

    fun listenToWebSocket(meetingId: String) {
        if (activeMeetingId != null && activeMeetingId != meetingId) {
            webSocketClient.leaveMeetingRoom(activeMeetingId!!)
        }
        activeMeetingId = meetingId
        webSocketClient.joinMeetingRoom(meetingId)

        webSocketJob?.cancel()
        webSocketJob = viewModelScope.launch {
            // Écouter les événements recording_started/recording_stopped
            launch {
                webSocketClient.recordingStateEvents.collect { event ->
                    if (event.meetingId != meetingId) return@collect

                    loadMeeting(meetingId)
                    delay(300L)

                    val currentMeeting = meeting.value ?: return@collect
                    val uid = currentUserId.value ?: return@collect

                    if (event.isRecording) {
                        if (manualStopUsed.value) {
                            return@collect
                        }

                        val isHost = currentMeeting.createdBy?.realId == uid
                        if (isHost) {
                            startRecording()
                        } else {
                            // Ne redémarrer le timer que si le recording n'était pas déjà actif
                            if (!isRecording.value) {
                                isRecording.value = true
                                recordingDuration.value = 0L
                                startRecordingTimer()
                            }
                        // Si isRecording.value == true déjà, le Cron B re-broadcaste
                        // recording_started mais on ignore pour ne pas remettre à zéro
                     }
                    } else {
                        isRecording.value = false
                        stopRecordingTimer()

                        val isHost = meeting.value?.createdBy?.realId == uid
                        if (isHost) {
                            manualStopUsed.value = true
                        } else {
                            recordingStoppedByHost.value = true
                        }
                    }
                }
            }

            // Écouter participant presence changes
            launch {
                webSocketClient.participantPresenceEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        loadMeeting(meetingId)
                    }
                }
            }

            // Rafraîchir l'état après une reconnexion WS
            launch {
                webSocketClient.reconnectedEvent.collect {
                    // WS vient de se reconnecter : rafraîchir les données de la réunion
                    // pour récupérer les participants qui se sont joints pendant la coupure
                    loadMeeting(meetingId)
                }
            }

            // Recharger les notes en temps réel après ajout
            launch {
                webSocketClient.noteAddedEvents.collect { event ->
                    if (event.meetingId == meetingId) {
                        loadMeeting(meetingId)
                    }
                }
            }

            // Écouter meeting_force_end
            launch {
                webSocketClient.meetingForceEndEvents.collect { event ->
                    if (event.meetingId != meetingId) return@collect

                    val normalizedReason = event.reason.trim().lowercase()
                    val shouldShowForceEndDialog = normalizedReason in setOf(
                        "host_left",
                        "all_guests_left",
                        "auto_finish"
                    )

                    if (!shouldShowForceEndDialog) {
                        forceEndCountdown.value = null
                        forceEndReason.value = null
                        uiState.value = InPersonUiState.Finished
                        return@collect
                    }

                    val uid = currentUserId.value ?: return@collect
                    val isHost = meeting.value?.createdBy?.realId == uid
                    val triggeredByCurrentUser =
                        event.triggeredBy?.trim()?.equals(uid, ignoreCase = true) == true
                    val shouldSkipDialogByHeuristic = event.triggeredBy.isNullOrBlank() && (
                        (normalizedReason == "host_left" && isHost) ||
                            (normalizedReason == "all_guests_left" && !isHost)
                    )

                    if (triggeredByCurrentUser || shouldSkipDialogByHeuristic) {
                        forceEndReason.value = null
                        forceEndCountdown.value = null
                        uiState.value = InPersonUiState.Finished
                        return@collect
                    }

                    forceEndReason.value = normalizedReason
                    forceEndCountdown.value = null

                    if (isHost && isRecording.value) {
                        stopRecording()
                        uploadRecording(meetingId)
                        waitForUploadToSettle()
                    } else {
                        isRecording.value = false
                        stopRecordingTimer()
                    }

                    forceEndCountdown.value = event.countdown.coerceAtLeast(1)
                    var remaining = forceEndCountdown.value ?: 1
                    while (remaining > 0) {
                        forceEndCountdown.value = remaining
                        delay(1000L)
                        remaining -= 1
                    }
                    forceEndCountdown.value = 0
                }
            }
        }
    }

    override fun onCleared() {
        stopHeartbeat()
        val meetingIdSnapshot = activeMeetingId
        val currentUserIdSnapshot = currentUserId.value
        val isStillJoined = if (!meetingIdSnapshot.isNullOrBlank()) {
            val joined = meeting.value?.joinedParticipants.orEmpty()
            joined.any { participant ->
                val pId = participant.id?.trim()
                pId != null && pId == currentUserIdSnapshot
            }
        } else {
            false
        }

        if (!meetingIdSnapshot.isNullOrBlank() && isStillJoined) {
            viewModelScope.launch {
                try {
                    apiService.leavePhysicalMeeting(meetingIdSnapshot)
                } catch (_: Exception) {
                    // Log silencieux — cleanup géré aussi côté heartbeat
                }
            }
        }

        recordingTimer?.cancel()
        webSocketJob?.cancel()
        activeMeetingId?.let { webSocketClient.leaveMeetingRoom(it) }
        if (isRecording.value) {
            try {
                mediaRecorder?.stop()
            } catch (_: Exception) {
            }
        }
        releaseRecorder()
        super.onCleared()
    }
}
