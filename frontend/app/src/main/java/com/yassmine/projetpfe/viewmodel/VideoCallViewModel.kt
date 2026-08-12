package com.yassmine.projetpfe.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yassmine.projetpfe.data.api.MeetingDto
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.repository.MeetingRepository
import com.yassmine.projetpfe.notifications.MeetingForceEndEvent
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import dagger.hilt.android.lifecycle.HiltViewModel
import io.livekit.android.LiveKit
import io.livekit.android.RoomOptions
import io.livekit.android.events.DisconnectReason
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import io.livekit.android.room.track.LocalVideoTrack
import io.livekit.android.room.track.Track
import io.livekit.android.room.track.VideoTrack
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

//  ÉTATS DE LA CONNEXION 
sealed class VideoCallState {
    object Connecting   : VideoCallState()
    object Connected    : VideoCallState()
    object Disconnected : VideoCallState()
    data class Error(val message: String) : VideoCallState()
}

//  MODÈLE DE PARTICIPANT 
data class VideoParticipant(
    val id          : String,
    val name        : String,
    val isLocal     : Boolean,
    val isMuted     : Boolean,
    val isCameraOff : Boolean,
    val role        : String, 
    val profilePicture: String? = null,
    val videoTrack  : VideoTrack? = null,
)

@HiltViewModel
class VideoCallViewModel @Inject constructor(
    application: Application,
    private val meetingRepository: MeetingRepository,
    private val preferencesManager: PreferencesManager,
    private val wsClient: NotificationWebSocketClient,
) : AndroidViewModel(application) {

    private val tag = "VideoCall"

    private val _callState = MutableStateFlow<VideoCallState>(VideoCallState.Connecting)
    val callState: StateFlow<VideoCallState> = _callState.asStateFlow()

    private val _participants = MutableStateFlow<List<VideoParticipant>>(emptyList())
    val participants: StateFlow<List<VideoParticipant>> = _participants.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _isCameraOff = MutableStateFlow(false)
    val isCameraOff: StateFlow<Boolean> = _isCameraOff.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val _isStoppingRecording = MutableStateFlow(false)
    val isStoppingRecording: StateFlow<Boolean> = _isStoppingRecording.asStateFlow()

    private val _isHost = MutableStateFlow(false)
    val isHost: StateFlow<Boolean> = _isHost.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()

    private val _intentionalDisconnect = MutableStateFlow(false)
    val intentionalDisconnect: StateFlow<Boolean> = _intentionalDisconnect.asStateFlow()

    private val _connectionError = MutableStateFlow(false)
    val connectionError: StateFlow<Boolean> = _connectionError.asStateFlow()

    private val _forceEndEvent = MutableSharedFlow<MeetingForceEndEvent>(
        replay = 0,
        extraBufferCapacity = 4,
    )
    val forceEndEvent: SharedFlow<MeetingForceEndEvent> = _forceEndEvent.asSharedFlow()

    private var currentRoom: Room? = null
    private var eventListenerJob: Job? = null
    private var heartbeatJob: Job? = null
    private var currentMeetingId: String? = null
    private var localUserRole: String = "guest"
    private var isLeaving: Boolean = false
    private var participantPicturesByUserId: Map<String, String> = emptyMap()
    private var participantPicturesByName: Map<String, String> = emptyMap()
    private var desiredMicEnabled: Boolean = true
    private var desiredCameraEnabled: Boolean = true

    init {
        observeForceEndEvents()
        observeRecordingEvents()
        observeParticipantPresenceEvents()
        observeReconnectionEvents()
    }

    private fun observeReconnectionEvents() {
        viewModelScope.launch {
            wsClient.reconnectedEvent.collect {
                val activeMeetingId = currentMeetingId
                if (!activeMeetingId.isNullOrBlank()) {
                    // WS vient de se reconnecter : rafraîchir les données de la réunion
                    refreshParticipantProfilePictures(activeMeetingId)
                }
            }
        }
    }

    private fun observeParticipantPresenceEvents() {
        viewModelScope.launch {
            wsClient.participantPresenceEvents.collect { event ->
                val activeMeetingId = currentMeetingId
                if (!activeMeetingId.isNullOrBlank() && event.meetingId == activeMeetingId) {
                    refreshParticipantProfilePictures(activeMeetingId)
                }
            }
        }
    }

    private fun observeForceEndEvents() {
        viewModelScope.launch {
            wsClient.meetingForceEndEvents.collect { event ->
                val activeMeetingId = currentMeetingId
                if (!activeMeetingId.isNullOrBlank() && event.meetingId == activeMeetingId) {
                    onForceEndReceived(event)
                }
            }
        }
    }

    private fun onForceEndReceived(event: MeetingForceEndEvent) {
        if (BuildConfig.DEBUG) Log.d(tag, "=== FORCE END RECEIVED ===")
        if (BuildConfig.DEBUG) Log.d(tag, "Setting intentionalDisconnect=true BEFORE LiveKit disconnect")
        _intentionalDisconnect.value = true
        _connectionError.value = false
        _error.value = null
        _forceEndEvent.tryEmit(event)
        if (BuildConfig.DEBUG) Log.d(tag, "meeting_force_end reçu -> intentionalDisconnect=true meetingId=${event.meetingId}")
    }

    private fun observeRecordingEvents() {
        viewModelScope.launch {
            wsClient.recordingStateEvents.collect { event ->
                val activeMeetingId = currentMeetingId
                if (!activeMeetingId.isNullOrBlank() && event.meetingId == activeMeetingId) {
                    _isRecording.value = event.isRecording
                    _isRecordingActive.value = event.isRecording
                    if (!event.isRecording) {
                        _isStoppingRecording.value = false
                    }
                    if (BuildConfig.DEBUG) Log.d(tag, "recordingState=${event.isRecording} meetingId=${event.meetingId}")
                }
            }
        }
    }

    fun markIntentionalDisconnect() {
        _intentionalDisconnect.value = true
        if (BuildConfig.DEBUG) Log.d(tag, "intentionalDisconnect = true")
    }

    fun resetIntentionalDisconnect() {
        _intentionalDisconnect.value = false
    }

    //  JOIN MEETING 
    fun joinMeeting(meetingId: String) {
        if (_callState.value is VideoCallState.Connected) {
            Log.w(tag, "Déjà connecté — ignoré")
            return
        }
        isLeaving = false
        resetIntentionalDisconnect()
        _connectionError.value = false
        currentMeetingId = meetingId
        wsClient.joinMeetingRoom(meetingId)
        _callState.value = VideoCallState.Connecting
        _error.value = null

        viewModelScope.launch {
            try {
                if (BuildConfig.DEBUG) Log.d(tag, "POST /api/meetings/$meetingId/join")
                val result = meetingRepository.joinMeeting(meetingId)

                if (result.isFailure) {
                    val msg = result.exceptionOrNull()?.message ?: "Erreur de connexion"
                    Log.e(tag, "API /join failed: $msg")
                    _callState.value = VideoCallState.Error(msg)
                    _error.value = msg
                    return@launch
                }

                val joinData   = result.getOrThrow()
                val token      = joinData.token
                val livekitUrl = joinData.livekitUrl
                localUserRole  = joinData.role
                _isHost.value = localUserRole.trim().lowercase() in setOf("host", "hôte")
                _isRecording.value = joinData.meeting?.isRecording == true ||
                    !joinData.meeting?.activeEgressId.isNullOrBlank()

                if (token.isBlank()) {
                    _callState.value = VideoCallState.Error("Token LiveKit invalide")
                    return@launch
                }

                meetingRepository.getMeeting(meetingId)
                    .getOrNull()
                    ?.let { cacheParticipantProfilePictures(it) }

                if (BuildConfig.DEBUG) Log.d(tag, "Join OK — role=$localUserRole, url=$livekitUrl")
                connectToLiveKit(token, livekitUrl)

            } catch (e: Exception) {
                Log.e(tag, "joinMeeting: ${e.message}", e)
                _callState.value = VideoCallState.Error("Erreur: ${e.message}")
            }
        }
    }

    private fun cacheParticipantProfilePictures(meeting: MeetingDto) {
        val byId = mutableMapOf<String, String>()
        val byName = mutableMapOf<String, String>()

        val hostPicture = meeting.createdBy?.profilePicture?.trim().orEmpty()
        if (hostPicture.isNotBlank()) {
            val hostId = meeting.createdBy?.realId.orEmpty()
            if (hostId.isNotBlank()) {
                byId[hostId] = hostPicture
            }
            val hostName = meeting.createdBy?.name?.trim().orEmpty()
            if (hostName.isNotBlank()) {
                byName[hostName.lowercase()] = hostPicture
            }
        }

        meeting.participantUsers.forEach { participant ->
            val picture = participant.profilePicture?.trim().orEmpty()
            if (picture.isBlank()) return@forEach

            val participantId = participant.id?.trim().orEmpty()
            if (participantId.isNotBlank()) {
                byId[participantId] = picture
            }

            val participantName = (
                participant.name?.takeIf { it.isNotBlank() }
                    ?: participant.email.takeIf { it.isNotBlank() }
            )?.trim().orEmpty()

            if (participantName.isNotBlank()) {
                byName[participantName.lowercase()] = picture
            }
        }

        participantPicturesByUserId = byId
        participantPicturesByName = byName
    }

    private fun refreshParticipantProfilePictures(meetingId: String) {
        viewModelScope.launch {
            meetingRepository.getMeeting(meetingId)
                .getOrNull()
                ?.let { cacheParticipantProfilePictures(it) }
        }
    }

    private fun resolveParticipantProfilePicture(id: String, name: String): String? {
        val idKey = id.trim()
        if (idKey.isNotBlank()) {
            val idMatch = participantPicturesByUserId[idKey]
            if (!idMatch.isNullOrBlank()) return idMatch
        }

        val nameKey = name.trim().lowercase()
        if (nameKey.isNotBlank()) {
            val nameMatch = participantPicturesByName[nameKey]
            if (!nameMatch.isNullOrBlank()) return nameMatch
        }

        return null
    }

    //  CONNEXION LIVEKIT 
    private fun connectToLiveKit(token: String, url: String) {
        viewModelScope.launch {
            try {
                val room = LiveKit.create(
                    appContext = getApplication(),
                    options    = RoomOptions(adaptiveStream = true, dynacast = true)
                )
                currentRoom = room
                _room.value = room

                startEventListener(room)
                room.connect(url = url, token = token)

                val micEnabled = preferencesManager.getPreJoinMicEnabled().first()
                val cameraEnabled = preferencesManager.getPreJoinCameraEnabled().first()
                desiredMicEnabled = micEnabled
                desiredCameraEnabled = cameraEnabled
                _isMuted.value = !micEnabled
                _isCameraOff.value = !cameraEnabled
                room.localParticipant.setMicrophoneEnabled(micEnabled)
                room.localParticipant.setCameraEnabled(cameraEnabled)

                onRoomConnected()
                updateParticipants(room)
                startHeartbeat()

                //  FIX CAMÉRA NOIRE :
                // The local VideoTrack is not available immediately after
                // setCameraEnabled(true). TrackPublished event will trigger
                // updateParticipants() when the track is ready.
                // We add a single safety-net poll after 1.5s to cover edge cases
                // where TrackPublished might fire before event listener is fully set up.
                delay(1500)
                updateParticipants(room)

                if (BuildConfig.DEBUG) Log.d(tag, "Connecté à LiveKit — role=$localUserRole")

            } catch (e: Exception) {
                Log.e(tag, "connectToLiveKit: ${e.message}", e)
                currentRoom = null
                _room.value = null
                _connectionError.value = true
                _callState.value = VideoCallState.Error("Connexion échouée: ${e.message}")
            }
        }
    }

    private fun onRoomConnected() {
        _intentionalDisconnect.value = false
        _connectionError.value = false
        _callState.value = VideoCallState.Connected
    }

    //  EVENT LISTENER 
    private fun startEventListener(room: Room) {
        eventListenerJob?.cancel()
        eventListenerJob = viewModelScope.launch {
            room.events.collect { event ->
                when (event) {
                    is RoomEvent.TrackSubscribed         -> updateParticipants(room)
                    is RoomEvent.TrackUnsubscribed       -> updateParticipants(room)
                    is RoomEvent.TrackMuted              -> updateParticipants(room)
                    is RoomEvent.TrackUnmuted            -> updateParticipants(room)
                    is RoomEvent.TrackPublished -> {
                        //  FIX CAMÉRA NOIRE LOCALE :
                        // TrackPublished est émis aussi pour la track locale dans SDK 2.7.1.
                        // event.participant permet d'identifier si c'est local ou distant.
                        updateParticipants(room)
                    }
                    is RoomEvent.TrackUnpublished        -> updateParticipants(room)
                    is RoomEvent.ParticipantConnected    -> updateParticipants(room)
                    is RoomEvent.ParticipantDisconnected -> updateParticipants(room)
                    is RoomEvent.Disconnected -> {
                        val reason = event.reason
                        val reasonText = reason.name.lowercase()
                        val errorText = event.error?.message?.lowercase().orEmpty()

                        if (BuildConfig.DEBUG) Log.d(tag, "=== LIVEKIT DISCONNECT ===")
                        if (BuildConfig.DEBUG) Log.d(tag, "onDisconnect reason='$reasonText' intentional=${_intentionalDisconnect.value}")

                        stopHeartbeat()
                        currentRoom = null
                        _room.value = null

                        val serverClosedReasons = listOf(
                            "room closed",
                            "server shutdown",
                            "participant removed",
                            "room deleted"
                        )
                        val isServerClosure = serverClosedReasons.any {
                            reasonText.contains(it) || errorText.contains(it)
                        }

                        when {
                            isLeaving -> {
                                _connectionError.value = false
                                _callState.value = VideoCallState.Disconnected
                            }
                            _intentionalDisconnect.value -> {
                                if (BuildConfig.DEBUG) Log.d(tag, "room.disconnect() intentionnel")
                                _connectionError.value = false
                                _callState.value = VideoCallState.Disconnected
                            }
                            isServerClosure -> {
                                if (BuildConfig.DEBUG) Log.d(tag, "Déconnexion serveur gérée — pas d'erreur affichée")
                                _connectionError.value = false
                                _callState.value = VideoCallState.Disconnected
                            }
                            //  FIX SESSION 3 — duplicate identity :
                            // quand le même userId reconnecte, LiveKit déconnecte
                            // la session précédente avec "duplicate" dans le message.
                            // On traite ça comme un départ normal (pas une erreur réseau).
                            //  FIX SESSION 3 : utiliser l'enum DisconnectReason.DUPLICATE_IDENTITY
                            // disponible dans ce SDK (livekit-android:2.7.1)
                            // au lieu de parser le message texte de l'erreur
                            event.reason == DisconnectReason.DUPLICATE_IDENTITY -> {
                                Log.w(tag, "Duplicate identity — déconnexion silencieuse")
                                _connectionError.value = false
                                _callState.value = VideoCallState.Disconnected
                            }
                            else -> {
                                _connectionError.value = true
                                _error.value = "Connexion perdue. Vérifiez votre réseau."
                                _callState.value = VideoCallState.Disconnected
                                Log.w(tag, "Déconnexion non-intentionnelle: reason='$reasonText' error='${event.error?.message ?: ""}'")
                            }
                        }

                        if (BuildConfig.DEBUG) Log.d(tag, "intentionalDisconnect=${_intentionalDisconnect.value}")
                        if (BuildConfig.DEBUG) Log.d(tag, "connectionError restera false: ${_intentionalDisconnect.value || isServerClosure || isLeaving}")

                        _participants.value = emptyList()
                        _isRecording.value = false
                        _isRecordingActive.value = false
                        _isStoppingRecording.value = false
                    }
                    is RoomEvent.Reconnecting -> {
                        if (!_intentionalDisconnect.value) {
                            _connectionError.value = true
                        }
                        _callState.value = VideoCallState.Connecting
                    }
                    is RoomEvent.Reconnected -> {
                        onRoomConnected()
                        updateParticipants(room)
                    }
                    else -> Unit
                }
            }
        }
    }

    //  UPDATE PARTICIPANTS 
    private fun updateParticipants(room: Room) {
        try {
            val list = mutableListOf<VideoParticipant>()

            //  Participant local 
            val local       = room.localParticipant
            val localCamPub = local.getTrackPublication(Track.Source.CAMERA)
            val localMicPub = local.getTrackPublication(Track.Source.MICROPHONE)

            val localId   = local.identity?.value ?: ""
            val localName = local.name.orEmpty().ifEmpty { localId.ifEmpty { "Moi" } }
            val localProfilePicture = resolveParticipantProfilePicture(localId, localName)

            // muted=true → caméra désactivée.
            // null (pub pas encore créée) → on considère caméra active (false)
            // pour ne pas bloquer l'affichage pendant l'initialisation.
            val localCamOff = localCamPub?.muted ?: !desiredCameraEnabled
            val localMicOff = localMicPub?.muted ?: !desiredMicEnabled

            _isMuted.value     = localMicOff
            _isCameraOff.value = localCamOff

            // localCamPub.track = LocalVideoTrack publiée par ce participant.
            // Peut être null juste après setCameraEnabled() si la track n'est
            // pas encore initialisée → dans ce cas on passe null (avatar affiché).
            // LocalTrackPublished déclenchera updateParticipants() quand elle sera prête.
            val localVideoTrack: VideoTrack? = if (!localCamOff) {
                localCamPub?.track as? VideoTrack
            } else null

            list.add(
                VideoParticipant(
                    id          = localId,
                    name        = localName,
                    isLocal     = true,
                    isMuted     = localMicOff,
                    isCameraOff = localCamOff,
                    role        = localUserRole,
                    profilePicture = localProfilePicture,
                    videoTrack  = localVideoTrack,
                )
            )

            //  Participants distants 
            for (remote in room.remoteParticipants.values) {
                val camPub = remote.getTrackPublication(Track.Source.CAMERA)
                val micPub = remote.getTrackPublication(Track.Source.MICROPHONE)

                val remoteId   = remote.identity?.value ?: ""
                val remoteName = remote.name.orEmpty().ifEmpty { remoteId.ifEmpty { "Participant" } }
                val remoteProfilePicture = resolveParticipantProfilePicture(remoteId, remoteName)

                //  FIX RÔLE : lire depuis les metadata du participant distant.
                // Le backend encode { role, userId } dans les metadata du token LiveKit.
                // Si absentes ou mal formées → fallback "guest".
                val remoteRole = parseRoleFromMetadata(remote.metadata)

                // État initial réel des tracks distantes:
                // - Pas de track audio publiée => muté
                // - Pas de track vidéo publiée/souscrite => caméra off
                val remoteVideoTrack = camPub?.track as? VideoTrack
                val remoteMicOff = micPub?.muted ?: true
                val remoteCamOff = (camPub?.muted ?: true) || remoteVideoTrack == null

                list.add(
                    VideoParticipant(
                        id          = remoteId,
                        name        = remoteName,
                        isLocal     = false,
                        isMuted     = remoteMicOff,
                        isCameraOff = remoteCamOff,
                        role        = remoteRole,
                        profilePicture = remoteProfilePicture,
                        videoTrack  = if (remoteCamOff) null else remoteVideoTrack,
                    )
                )
            }

            _participants.value = list

        } catch (e: Exception) {
            Log.e(tag, "updateParticipants: ${e.message}")
        }
    }

    //  HELPER : extraire le rôle depuis les metadata JSON du participant 

    private fun parseRoleFromMetadata(metadata: String?): String {
        if (metadata.isNullOrBlank()) return "guest"
        return try {
            val json = JSONObject(metadata)
            if (json.optString("role", "guest").lowercase() == "host") "host" else "guest"
        } catch (e: Exception) {
            Log.w(tag, "parseRoleFromMetadata error: ${e.message}")
            "guest"
        }
    }

    //  TOGGLE MIC 
    fun toggleMute() {
        viewModelScope.launch {
            try {
                val room = currentRoom ?: return@launch
                val currentlyMuted = room.localParticipant
                    .getTrackPublication(Track.Source.MICROPHONE)?.muted ?: !desiredMicEnabled
                val nextMicEnabled = currentlyMuted
                desiredMicEnabled = nextMicEnabled
                room.localParticipant.setMicrophoneEnabled(nextMicEnabled)
                updateParticipants(room)
            } catch (e: Exception) { Log.e(tag, "toggleMute: ${e.message}") }
        }
    }

    //  TOGGLE CAMERA 
    fun toggleCamera() {
        viewModelScope.launch {
            try {
                val room = currentRoom ?: return@launch
                val currentlyOff = room.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)?.muted ?: !desiredCameraEnabled
                val nextCameraEnabled = currentlyOff
                desiredCameraEnabled = nextCameraEnabled
                room.localParticipant.setCameraEnabled(nextCameraEnabled)
                //  FIX : attendre un tick avant de lire l'état de la track
                // pour laisser le temps à setCameraEnabled() de prendre effet
                delay(300)
                updateParticipants(room)
            } catch (e: Exception) { Log.e(tag, "toggleCamera: ${e.message}") }
        }
    }

    //  SWITCH CAMERA 
    fun switchCamera() {
        viewModelScope.launch {
            try {
                val room = currentRoom ?: return@launch
                val videoTrack = room.localParticipant
                    .getTrackPublication(Track.Source.CAMERA)
                    ?.track as? LocalVideoTrack ?: return@launch
                videoTrack.switchCamera()
                if (BuildConfig.DEBUG) Log.d(tag, "Caméra basculée")
            } catch (e: Exception) { Log.w(tag, "switchCamera: ${e.message}") }
        }
    }

    //  LEAVE MEETING 
    fun leaveMeeting() {
        isLeaving = true
        _intentionalDisconnect.value = true
        _connectionError.value = false
        stopHeartbeat()
        currentMeetingId?.let { wsClient.leaveMeetingRoom(it) }

        viewModelScope.launch {
            try {
                eventListenerJob?.cancel()
                currentRoom?.disconnect()
                currentRoom = null
                _room.value = null
            } catch (e: Exception) { Log.w(tag, "room.disconnect() error: ${e.message}") }

            try {
                currentMeetingId?.let { meetingRepository.leaveMeeting(it) }
            } catch (e: Exception) { Log.w(tag, "leaveMeeting API error: ${e.message}") }

            _participants.value = emptyList()
            _isRecording.value = false
            _isRecordingActive.value = false
            _isStoppingRecording.value = false
            _callState.value    = VideoCallState.Disconnected
        }
    }

    fun disconnectAndNavigate() {
        isLeaving = true
        _intentionalDisconnect.value = true
        _connectionError.value = false
        stopHeartbeat()
        currentMeetingId?.let { wsClient.leaveMeetingRoom(it) }

        viewModelScope.launch {
            if (BuildConfig.DEBUG) Log.d(tag, "disconnectAndNavigate() appelé")
            try {
                eventListenerJob?.cancel()
                currentRoom?.disconnect()
            } catch (e: Exception) {
                Log.w(tag, "disconnectAndNavigate: ${e.message}")
            }

            currentRoom = null
            _room.value = null
            _participants.value = emptyList()
            _isRecording.value = false
            _isRecordingActive.value = false
            _isStoppingRecording.value = false
            _callState.value = VideoCallState.Disconnected
        }
    }

    fun stopRecording(meetingId: String) {
        if (!_isHost.value) return
        if (!_isRecordingActive.value) return
        if (_isStoppingRecording.value) return

        _isStoppingRecording.value = true
        viewModelScope.launch {
            val result = meetingRepository.stopRecording(meetingId)
            if (result.isFailure) {
                _isStoppingRecording.value = false
                val msg = result.exceptionOrNull()?.message ?: "Erreur arrêt enregistrement"
                _error.value = msg
                Log.e(tag, "stopRecording API failed: $msg")
                return@launch
            }

            if (BuildConfig.DEBUG) Log.d(tag, "stopRecording API success for meeting=$meetingId")
        }
    }

    //  HEARTBEAT 
    private fun startHeartbeat() {
        heartbeatJob = viewModelScope.launch {
            while (true) {
                delay(30_000L)
                currentMeetingId?.let {
                    try { meetingRepository.sendHeartbeat(it) }
                    catch (e: Exception) { Log.v(tag, "heartbeat error: ${e.message}") }
                }
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopHeartbeat()
        eventListenerJob?.cancel()
        currentMeetingId?.let { wsClient.leaveMeetingRoom(it) }
        try { currentRoom?.disconnect() } catch (_: Exception) {}
        currentRoom = null
        _room.value = null
        _isRecordingActive.value = false
        _isStoppingRecording.value = false
    }
}