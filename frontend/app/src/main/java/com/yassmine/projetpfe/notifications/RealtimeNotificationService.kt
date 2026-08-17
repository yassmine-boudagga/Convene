package com.yassmine.projetpfe.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.yassmine.projetpfe.BuildConfig
import com.yassmine.projetpfe.R
import com.yassmine.projetpfe.data.repository.AuthSessionManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val SERVICE_CHANNEL_ID = "meetflow_ws_service"
private const val SERVICE_NOTIFICATION_ID = 771003

@AndroidEntryPoint
class RealtimeNotificationService : Service() {

    @Inject lateinit var authSessionManager: AuthSessionManager
    @Inject lateinit var wsClient: NotificationWebSocketClient
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ensureServiceChannel()
        startForeground(SERVICE_NOTIFICATION_ID, buildForegroundNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (wsClient.isManualDisconnect) {
            stopSelf()
            return START_NOT_STICKY
        }

        serviceScope.launch {
            val token = authSessionManager.getAccessToken()
            if (token.isNullOrBlank()) {
                stopSelf()
            } else {
                wsClient.prepareForConnect()
                wsClient.connect(baseUrl = BuildConfig.WS_BASE_URL, token = token)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wsClient.disconnect()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureServiceChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            SERVICE_CHANNEL_ID,
            getString(R.string.notif_realtime_service_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notif_realtime_service_description)
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            setSound(null, null)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): android.app.Notification {
        val settingsIntent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            putExtra(Settings.EXTRA_CHANNEL_ID, SERVICE_CHANNEL_ID)
        }
        val settingsPi = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, SERVICE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(getString(R.string.notif_default_title))
            .setContentText(getString(R.string.notif_connection_active))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .addAction(0, getString(R.string.notif_hide), settingsPi)
            .build()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, RealtimeNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
        fun stop(context: Context) {
            context.stopService(Intent(context, RealtimeNotificationService::class.java))
        }
    }
}
