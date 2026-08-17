package com.yassmine.projetpfe.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.app.NotificationCompat
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.yassmine.projetpfe.MainActivity
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.NotificationDto
import com.yassmine.projetpfe.data.api.NotificationPayloadDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val CHANNEL_ID   = "meetflow_high_priority_v2"

@Singleton
class NotificationWebSocketClient @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val tag = "WsNotifClient"
    private val _notifications = MutableSharedFlow<NotificationDto>(
        replay = 0,
        extraBufferCapacity = 50,
    )
    val notifications: SharedFlow<NotificationDto> = _notifications.asSharedFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _aiSummaryReadyEvents = MutableSharedFlow<AISummaryReadyEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val aiSummaryReadyEvents: SharedFlow<AISummaryReadyEvent> = _aiSummaryReadyEvents.asSharedFlow()

    private val _aiSummaryEmptyEvents = MutableSharedFlow<AISummaryReadyEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val aiSummaryEmptyEvents: SharedFlow<AISummaryReadyEvent> = _aiSummaryEmptyEvents.asSharedFlow()

    private val _aiSummaryFailedEvents = MutableSharedFlow<AISummaryReadyEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val aiSummaryFailedEvents: SharedFlow<AISummaryReadyEvent> = _aiSummaryFailedEvents.asSharedFlow()

    private val _meetingForceEndEvents = MutableSharedFlow<MeetingForceEndEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val meetingForceEndEvents: SharedFlow<MeetingForceEndEvent> = _meetingForceEndEvents.asSharedFlow()

    private val _recordingStateEvents = MutableSharedFlow<RecordingStateEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val recordingStateEvents: SharedFlow<RecordingStateEvent> = _recordingStateEvents.asSharedFlow()

    private val _recordingAvailableEvents = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val recordingAvailableEvents: SharedFlow<String> =
        _recordingAvailableEvents.asSharedFlow()

    private val _participantPresenceEvents = MutableSharedFlow<ParticipantPresenceEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )
    val participantPresenceEvents: SharedFlow<ParticipantPresenceEvent> = _participantPresenceEvents.asSharedFlow()

    private val _noteAddedEvents = MutableSharedFlow<NoteAddedEvent>(
        replay = 0,
        extraBufferCapacity = 32,
    )
    val noteAddedEvents: SharedFlow<NoteAddedEvent> = _noteAddedEvents.asSharedFlow()

    private val _meetingStatusChangedEvents = MutableSharedFlow<MeetingStatusChangedEvent>(
        replay = 0,
        extraBufferCapacity = 16,
    )
    val meetingStatusChangedEvents: SharedFlow<MeetingStatusChangedEvent> = _meetingStatusChangedEvents.asSharedFlow()

    private val _reconnectedEvent = MutableSharedFlow<Unit>(replay = 0, extraBufferCapacity = 1)
    val reconnectedEvent: SharedFlow<Unit> = _reconnectedEvent.asSharedFlow()

    private val _unreadCount = MutableStateFlow(0)

    private var webSocket: WebSocket? = null
    private val client = OkHttpClient()
    private val joinedMeetingRooms = CopyOnWriteArraySet<String>()
    private var authToken: String? = null
    @Volatile private var lastBaseUrl: String? = null
    @Volatile
    var isManualDisconnect: Boolean = false
        private set

    @Volatile private var lastCloseWasAuthError: Boolean = false
    @Volatile private var reconnectAttempt = 0
    private val maxReconnectDelayMs = 20_000L
    private val baseReconnectDelayMs = 2_000L
    @Volatile private var isReconnecting = false
    private var reconnectJob: Job? = null
    private val reconnectScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    init {
        createNotificationChannel()
    }

    fun connect(baseUrl: String, token: String) {
        lastBaseUrl = baseUrl

        if (isManualDisconnect) {
            Log.w(tag, "connect() ignoré — déconnexion manuelle en cours")
            return
        }

        if (webSocket != null) {
            if (BuildConfig.DEBUG) Log.d(tag, "WebSocket déjà connecté — ignoré")
            return
        }
        // stocker le token pour l'envoyer dans onOpen
        authToken = token
        val wsUrl = baseUrl
            .replace("https://", "wss://")
            .replace("http://", "ws://")

        val request = Request.Builder()
            .url("$wsUrl/ws")
            .build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(ws: WebSocket, response: Response) {
                if (isManualDisconnect) {
                    Log.w(tag, "onOpen ignoré — déconnexion manuelle en cours")
                    ws.close(1000, "Manual disconnect in progress")
                    return
                }

                if (BuildConfig.DEBUG) Log.d(tag, "WebSocket connecté — envoi message auth")
                // envoyer le message d'authentification
                val authMsg = JSONObject().apply {
                    put("event", "auth")
                    put("token", authToken ?: "")
                }.toString()
                ws.send(authMsg)
                if (BuildConfig.DEBUG) Log.d(tag, "Message auth envoyé")
            }

            override fun onMessage(ws: WebSocket, text: String) {
                if (BuildConfig.DEBUG) Log.d(tag, "Message reçu: $text")
                handleIncomingMessage(text)
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.e(tag, "WebSocket failure: ${t.message}")
                _isConnected.value = false
                this@NotificationWebSocketClient.webSocket = null
                isReconnecting = false
                val wasAuthError = lastCloseWasAuthError
                lastCloseWasAuthError = false
                if (!isManualDisconnect && !wasAuthError) {
                    scheduleReconnect()
                }
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                if (BuildConfig.DEBUG) Log.d(tag, "WebSocket fermé: code=$code reason=$reason")
                _isConnected.value = false
                this@NotificationWebSocketClient.webSocket = null
                isReconnecting = false
                val intentionalReasons = setOf("logout", "recently_logged_out", "duplicate_auth", "Auth failed")
                if (!isManualDisconnect && reason !in intentionalReasons && code != 1000) {
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        if (isManualDisconnect) {
            if (BuildConfig.DEBUG) Log.d(tag, "scheduleReconnect ignoré — déconnexion manuelle")
            return
        }
        if (isReconnecting) {
            if (BuildConfig.DEBUG) Log.d(tag, "scheduleReconnect ignoré — reconnexion déjà en cours")
            return
        }
        isReconnecting = true

        reconnectJob?.cancel()
        reconnectJob = reconnectScope.launch {
            val delayMs = minOf(
                baseReconnectDelayMs * (1L shl reconnectAttempt.coerceAtMost(5)),
                maxReconnectDelayMs
            )
            if (BuildConfig.DEBUG) Log.d(tag, "Reconnexion dans ${delayMs}ms (tentative ${reconnectAttempt + 1})")
            delay(delayMs)

            if (isManualDisconnect) return@launch

            reconnectAttempt++
            val token = authToken
            if (token.isNullOrBlank()) {
                isReconnecting = false
                Log.w(tag, "scheduleReconnect: token absent, abandon")
                return@launch
            }

            if (BuildConfig.DEBUG) Log.d(tag, "Tentative de reconnexion WS (attempt=$reconnectAttempt)")
            webSocket = null
            connect(
                baseUrl = lastBaseUrl ?: return@launch,
                token = token
            )
        }
    }

    fun prepareForConnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        isReconnecting = false
        isManualDisconnect = false
        lastCloseWasAuthError = false
        webSocket = null
    }

    fun disconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
        reconnectAttempt = 0
        isReconnecting = false
        isManualDisconnect = true
        webSocket?.close(1000, "Logout")
        webSocket = null
        authToken = null
        _isConnected.value = false
        joinedMeetingRooms.clear()
    }

    fun decrementUnread() {
        if (_unreadCount.value > 0) _unreadCount.value--
    }

    fun resetUnread() {
        _unreadCount.value = 0
    }

    fun joinMeetingRoom(meetingId: String) {
        if (meetingId.isBlank()) return
        joinedMeetingRooms.add(meetingId)
        val msg = JSONObject().apply {
            put("event", "join_meeting")
            put("meetingId", meetingId)
        }.toString()
        webSocket?.send(msg)
    }

    fun leaveMeetingRoom(meetingId: String) {
        if (meetingId.isBlank()) return
        joinedMeetingRooms.remove(meetingId)
        val msg = JSONObject().apply {
            put("event", "leave_meeting")
            put("meetingId", meetingId)
        }.toString()
        webSocket?.send(msg)
    }
 
    private fun handleIncomingMessage(text: String) {
        try {
            val element = JsonParser.parseString(text)
            if (!element.isJsonObject) return
            val json = element.asJsonObject

            val event = safeString(json, "event") ?: ""

            when {
                event == "auth_success" -> {
                    if (BuildConfig.DEBUG) Log.d(tag, "Auth WebSocket réussie: userId=${safeString(json, "userId")}")
                    _isConnected.value = true
                    isReconnecting = false
                    reconnectAttempt = 0

                    joinedMeetingRooms.forEach { meetingId ->
                        val joinMsg = JSONObject().apply {
                            put("event", "join_meeting")
                            put("meetingId", meetingId)
                        }.toString()
                        webSocket?.send(joinMsg)
                    }
                    // Signal de reconnexion pour les ViewModels
                    reconnectScope.launch {
                        _reconnectedEvent.emit(Unit)
                    }
                }

                event == "notification" -> {
                    val notifObj = json.getAsJsonObject("notification") ?: return
                    val notification = parseNotification(notifObj) ?: return
                    _notifications.tryEmit(notification)
                    _unreadCount.value++

                    val meetingId = notification.data?.meetingId
                    if ( !meetingId.isNullOrBlank() && notification.type == "ai_summary_ready") {
                        _aiSummaryReadyEvents.tryEmit(AISummaryReadyEvent(meetingId))
                    }

                    showSystemNotification(notification)
                }

                event == "pending_notifications" -> {
                    val arr = json.getAsJsonArray("notifications") ?: return
                    arr.forEach { el ->
                        if (el.isJsonObject) {
                            val notification = parseNotification(el.asJsonObject)
                            if (notification != null) {
                                _notifications.tryEmit(notification)
                                _unreadCount.value++

                                val meetingId = notification.data?.meetingId
                                if (
                                    !meetingId.isNullOrBlank() &&
                                    (notification.type == "ai_summary_ready")
                                ) {
                                    _aiSummaryReadyEvents.tryEmit(AISummaryReadyEvent(meetingId))
                                }

                                // Déclencher notification pour chaque pending non lue
                                if (!notification.isRead) {
                                    showSystemNotification(notification)
                                }
                            }
                        }
                    }
                }

                event == "pong" -> Unit

                event == "auth_error" -> {
                    val reason = safeString(json, "message") ?: "auth_error"
                    Log.e(tag, "Auth WebSocket échouée: $reason")
                    lastCloseWasAuthError = true
                    if (reason == "recently_logged_out" || reason == "Token invalide") {
                        isManualDisconnect = true
                    }
                    webSocket?.close(1000, "Auth failed")
                    webSocket = null
                    _isConnected.value = false
                }

                event == "meeting_force_end" -> {
                    val dataObj = json.getAsJsonObject("data")
                    val meetingId = safeString(json, "meetingId")
                        ?: dataObj?.let { safeString(it, "meetingId") }
                        ?: return
                    val reason = (
                        safeString(json, "reason")
                            ?: dataObj?.let { safeString(it, "reason") }
                    )
                        ?.trim()
                        ?.lowercase()
                        ?.takeIf { it.isNotBlank() }
                        ?: "auto_finish"
                    val countdown = safeInt(json, "countdown")
                        ?: dataObj?.let { safeInt(it, "countdown") }
                        ?: 5
                    val triggeredBy = safeString(json, "triggeredBy")
                        ?: dataObj?.let { safeString(it, "triggeredBy") }
                    _meetingForceEndEvents.tryEmit(
                        MeetingForceEndEvent(
                            meetingId = meetingId,
                            reason = reason,
                            countdown = countdown,
                            triggeredBy = triggeredBy
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "meeting_force_end reçu meetingId=$meetingId reason=$reason countdown=$countdown triggeredBy=$triggeredBy")
                }

                event == "recording_started" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    val egressId = safeString(json, "egressId")
                    _recordingStateEvents.tryEmit(
                        RecordingStateEvent(
                            meetingId = meetingId,
                            isRecording = true,
                            egressId = egressId,
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "recording_started reçu meetingId=$meetingId egressId=$egressId")
                }

                event == "recording_stopped" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    val egressId = safeString(json, "egressId")
                    _recordingStateEvents.tryEmit(
                        RecordingStateEvent(
                            meetingId = meetingId,
                            isRecording = false,
                            egressId = egressId,
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "recording_stopped reçu meetingId=$meetingId egressId=$egressId")
                }

                event == "participant_joined" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _participantPresenceEvents.tryEmit(
                        ParticipantPresenceEvent(
                            meetingId = meetingId,
                            joined = true,
                            userId = safeString(json, "userId")
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "participant_joined reçu meetingId=$meetingId")
                }

                event == "participant_left" || event == "meeting_participant_left" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _participantPresenceEvents.tryEmit(
                        ParticipantPresenceEvent(
                            meetingId = meetingId,
                            joined = false,
                            userId = safeString(json, "userId")
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "participant_left reçu meetingId=$meetingId")
                }

                event == "note_added" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _noteAddedEvents.tryEmit(
                        NoteAddedEvent(
                            meetingId = meetingId,
                            userId = safeString(json, "userId")
                                ?: json.getAsJsonObject("note")?.let { safeString(it, "userId") }
                        )
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "note_added reçu meetingId=$meetingId")
                }

                event == "meeting_status_changed" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    val status = safeString(json, "status") ?: return
                    _meetingStatusChangedEvents.tryEmit(
                        MeetingStatusChangedEvent(meetingId = meetingId, status = status)
                    )
                    if (BuildConfig.DEBUG) Log.d(tag, "meeting_status_changed reçu meetingId=$meetingId status=$status")
                }
                event == "ai_result_ready" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _aiSummaryReadyEvents.tryEmit(AISummaryReadyEvent(meetingId))
                    if (BuildConfig.DEBUG) Log.d(tag, "ai_result_ready reçu meetingId=$meetingId")
                }

                event == "ai_summary_empty" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _aiSummaryEmptyEvents.tryEmit(AISummaryReadyEvent(meetingId))
                    if (BuildConfig.DEBUG) Log.d(tag, "ai_summary_empty reçu meetingId=$meetingId")
                }

                event == "ai_summary_failed" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _aiSummaryFailedEvents.tryEmit(AISummaryReadyEvent(meetingId))
                    if (BuildConfig.DEBUG) Log.d(tag, "ai_summary_failed reçu meetingId=$meetingId")
                }

                event == "recording_available" -> {
                    val meetingId = safeString(json, "meetingId") ?: return
                    _recordingAvailableEvents.tryEmit(meetingId)
                    if (BuildConfig.DEBUG) Log.d(tag, "recording_available reçu meetingId=$meetingId")
                }

                else -> Log.v(tag, "Événement WS ignoré: event=$event")
            }
        } catch (e: Exception) {
            Log.e(tag, "handleIncomingMessage error: ${e.message}", e)
        }
    }

    // PARSE NOTIFICATION 
    private fun extractId(json: JsonObject): String {
        fun primitiveToId(p: com.google.gson.JsonPrimitive): String = when {
            p.isString -> p.asString
            p.isNumber -> p.asNumber.toString()
            else -> ""
        }

        listOf("id", "_id").forEach { key ->
            val el = json.get(key)?.takeIf { !it.isJsonNull } ?: return@forEach
            if (el.isJsonPrimitive) {
                val v = primitiveToId(el.asJsonPrimitive)
                if (v.isNotBlank()) return v
            }
        }
        return ""
    }

    private fun parseNotification(json: JsonObject): NotificationDto? {
        return try {
            val id = extractId(json).takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()

            val type      = safeString(json, "type")      ?: "info"
            val title     = safeString(json, "title")     ?: context.getString(R.string.notif_default_title)
            val message   = safeString(json, "message")   ?: ""
            val isRead    = json.get("isRead")?.asBoolean ?: false
            val createdAt = safeString(json, "createdAt") ?: ""

            val dataObj    = json.getAsJsonObject("data")
            val payloadObj = json.getAsJsonObject("payload")
            val src        = dataObj ?: payloadObj

            val payloadDto: NotificationPayloadDto? = src?.let { d ->
                NotificationPayloadDto(
                    taskId       = safeString(d, "taskId"),
                    meetingId    = safeString(d, "meetingId"),
                    meetingTitle = safeString(d, "meetingTitle"),
                    startTime    = safeString(d, "startTime"),
                    actionUrl    = safeString(d, "actionUrl"),
                    fromUserId   = safeString(d, "fromUserId"),
                    fromUserName = safeString(d, "fromUserName"),
                    organizerName = safeString(d, "organizerName"),
                )
            }

            NotificationDto(
                id        = id,
                type      = type,
                title     = title,
                message   = message,
                isRead    = isRead,
                createdAt = createdAt,
                data      = payloadDto,
            )
        } catch (e: Exception) {
            Log.e(tag, "parseNotification error: ${e.message}", e)
            null
        }
    }

    private fun safeString(obj: JsonObject, key: String): String? =
        runCatching {
            if (obj.has(key) && !obj.get(key).isJsonNull)
                obj.get(key).asString.takeIf { it.isNotBlank() }
            else null
        }.getOrNull()

    private fun safeInt(obj: JsonObject, key: String): Int? =
        runCatching {
            if (!obj.has(key) || obj.get(key).isJsonNull) return@runCatching null
            val raw = obj.get(key)
            when {
                raw.isJsonPrimitive && raw.asJsonPrimitive.isNumber -> raw.asInt
                raw.isJsonPrimitive && raw.asJsonPrimitive.isString -> raw.asString.toIntOrNull()
                else -> null
            }
        }.getOrNull()

    // SHOW SYSTEM NOTIFICATION
    private fun showSystemNotification(notification: NotificationDto) {
        if (!hasNotificationPermission()) {
            Log.w(tag, "POST_NOTIFICATIONS not granted; skipping popup")
            return
        }

        if (!NotificationDisplayStore.shouldDisplay(context, notification.id)) {
            return
        }

        val meetingId = notification.data?.meetingId
        val taskId = notification.data?.taskId
        val fromUserId = notification.data?.fromUserId
        val targetRoute = if (notification.type == "task_assigned") "tasks" else "meeting_detail"
        val isSocialNotification = notification.type == "friend_request" || notification.type == "friend_accepted" || notification.type == "friend_rejected"

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notificationId", notification.id)
            if (isSocialNotification && !fromUserId.isNullOrBlank()) {
                putExtra("navigate_to", "public_profile")
                putExtra("userId", fromUserId)
            } else {
                putExtra("targetRoute", targetRoute)
                meetingId?.let { putExtra("meetingId", it) }
                taskId?.let { putExtra("taskId", it) }
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notification.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val iconRes = android.R.drawable.ic_dialog_info

        val dataMap = mapOf(
            "meetingTitle" to notification.data?.meetingTitle,
            "organizerName" to notification.data?.organizerName,
            "fromUserName" to notification.data?.fromUserName,
            "minutesBefore" to notification.data?.startTime
        )
        val displayName = (dataMap["organizerName"] as? String)
            ?: (dataMap["fromUserName"] as? String)
        val enrichedData = dataMap.toMutableMap().apply {
            put("organizerName", displayName)
            // pour admin_broadcast
            put("title", notification.title)
            put("message", notification.message)
        }
        val (localizedTitle, localizedMessage) = NotificationLocalization.buildLocalizedNotification(
            context = context,
            type = notification.type,
            data = enrichedData
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(localizedTitle)
            .setContentText(localizedMessage)
            .setStyle(NotificationCompat.BigTextStyle().bigText(localizedMessage))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notifManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(notification.id.hashCode(), builder.build())
    }

    private fun hasNotificationPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    // CREATE CHANNEL
    private fun createNotificationChannel() {
        val notifManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_notifications_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_notifications_channel_description)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            setSound(soundUri, audioAttributes)
        }

        notifManager.createNotificationChannel(channel)
        if (BuildConfig.DEBUG) Log.d(tag, "Canal créé: $CHANNEL_ID (IMPORTANCE_HIGH)")
    }
}

data class MeetingForceEndEvent(
    val meetingId: String,
    val reason: String,
    val countdown: Int,
    val triggeredBy: String? = null,
)

data class RecordingStateEvent(
    val meetingId: String,
    val isRecording: Boolean,
    val egressId: String? = null,
)

data class ParticipantPresenceEvent(
    val meetingId: String,
    val joined: Boolean,
    val userId: String? = null,
)

data class NoteAddedEvent(
    val meetingId: String,
    val userId: String? = null,
)

data class MeetingStatusChangedEvent(
    val meetingId: String,
    val status: String,
)

data class AISummaryReadyEvent(
    val meetingId: String,
)
