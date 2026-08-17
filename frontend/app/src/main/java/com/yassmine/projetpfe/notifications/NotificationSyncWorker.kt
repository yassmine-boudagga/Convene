package com.yassmine.projetpfe.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.yassmine.projetpfe.MainActivity
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.api.ApiService
import com.yassmine.projetpfe.data.local.PreferencesManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

private const val CHANNEL_ID = "meetflow_high_priority_v2"

@HiltWorker
class NotificationSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val apiService: ApiService
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            val prefsManager = PreferencesManager(applicationContext)

            // si rememberMe false pas de sync en background
            val rememberMe = prefsManager.getRememberMe().first()
            if (!rememberMe) {
                return Result.success()
            }

            val token = prefsManager.getToken().first()
            if (token.isNullOrBlank()) {
                return Result.success()
            }

            val rememberMeAfterTokenRead = prefsManager.getRememberMe().first()
            if (!rememberMeAfterTokenRead) {
                return Result.success()
            }
            // Vérifie que le token est bien associé à la session active.
            // Évite qu'un token restant en mémoire soit utilisé après un logout.
            val storedUserId = prefsManager.getUserId().first()
            val tokenUserId = extractUserIdFromJwt(token)
            if (storedUserId.isNullOrBlank() || tokenUserId.isBlank() || storedUserId != tokenUserId) {
                return Result.success()
            }

            // dernier login pour ignorer les notifs antérieures.
            val loginTimestamp = prefsManager.getLoginTimestamp().first()
            if (loginTimestamp <= 0L) {
                return Result.success()
            }

            ensureChannel()

            val response = apiService.getNotifications(unreadOnly = true, limit = 50)
            val notifications = response.data.notifications

            notifications
                .asReversed()
                .forEach { notification ->
                    val id = notification.id

                    if (id.isBlank()) return@forEach

                    if (loginTimestamp > 0L) {
                        val notifCreatedAt = tryParseIsoToMillis(notification.createdAt)
                        if (notifCreatedAt > 0L && notifCreatedAt < loginTimestamp) {
                            NotificationDisplayStore.markDisplayed(applicationContext, id)
                            return@forEach
                        }
                    }

                    if (NotificationDisplayStore.shouldDisplay(applicationContext, id)) {
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
                        }
                        val (localizedTitle, localizedMessage) =
                            NotificationLocalization.buildLocalizedNotification(
                                context = applicationContext,
                                type = notification.type,
                                data = enrichedData
                            )
                        showSystemNotification(
                            notificationId = id,
                            title = localizedTitle,
                            message = localizedMessage,
                            meetingId = notification.data?.meetingId
                        )
                    }
                }

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private fun tryParseIsoToMillis(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0L
        return try {
            java.time.Instant.parse(isoString).toEpochMilli()
        } catch (_: Exception) {
            try {
                val sdf = java.text.SimpleDateFormat(
                    "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                    java.util.Locale.getDefault()
                )
                sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
                sdf.parse(isoString)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }

    private fun showSystemNotification(
        notificationId: String,
        title: String,
        message: String,
        meetingId: String?
    ) {
        if (!hasNotificationPermission()) return

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            meetingId?.let { putExtra("meetingId", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            notificationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val iconRes = android.R.drawable.ic_dialog_info

        val builder = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(iconRes)
            .setContentTitle(title.ifBlank { applicationContext.getString(R.string.notif_default_title) })
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setDefaults(NotificationCompat.DEFAULT_ALL)

        val notifManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(notificationId.hashCode(), builder.build())
    }

    private fun hasNotificationPermission(): Boolean {
        return android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    private fun ensureChannel() {
        val notifManager = applicationContext
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notif_notifications_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = applicationContext.getString(R.string.notif_notifications_channel_description)
            enableLights(true)
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 250, 250, 250)
            setSound(soundUri, audioAttributes)
        }
        notifManager.createNotificationChannel(channel)
    }

    private fun extractUserIdFromJwt(token: String): String {
        return try {
            val parts = token.split(".")
            if (parts.size < 2) return ""
            val padding = (4 - parts[1].length % 4) % 4
            val normalized = parts[1].replace('-', '+').replace('_', '/') + "=".repeat(padding)
            val decoded = android.util.Base64.decode(normalized, android.util.Base64.DEFAULT)
            org.json.JSONObject(String(decoded, Charsets.UTF_8)).optString("id", "")
        } catch (_: Exception) { "" }
    }
}
