package com.yassmine.projetpfe

import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.yassmine.projetpfe.data.local.PreferencesManager
import com.yassmine.projetpfe.data.repository.NotificationRepository
import com.yassmine.projetpfe.notifications.NotificationBackgroundScheduler
import com.yassmine.projetpfe.notifications.NotificationWebSocketClient
import com.yassmine.projetpfe.notifications.RealtimeNotificationService
import com.yassmine.projetpfe.ui.navigation.NavGraph
import com.yassmine.projetpfe.ui.theme.ConveneTheme
import com.yassmine.projetpfe.utils.LocaleUtils
import com.yassmine.projetpfe.viewmodel.AuthViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferencesManager: PreferencesManager
    @Inject lateinit var notificationRepository: NotificationRepository
    @Inject lateinit var wsClient: NotificationWebSocketClient

    private val authViewModel: AuthViewModel by viewModels()

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (BuildConfig.DEBUG) Log.d("MainActivity", "POST_NOTIFICATIONS granted=$granted")
    }

    //  On garde une ref au navController pour la navigation depuis onNewIntent
    private var navController: NavHostController? = null

    // meetingId à naviguer (set depuis l'intent, consommé par le composable)
    private var pendingMeetingId: String? = null
    private var pendingTargetRoute: String? = null
    private var pendingNavigateTo: String? = null
    private var pendingUserId: String? = null
    private var pendingNotificationId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        registerNetworkCallback()
        val initialLanguage = runBlocking { preferencesManager.getAppLanguage().first() }
        LocaleUtils.applyLanguage(this, initialLanguage)

        enableEdgeToEdge()
        WindowCompat.setDecorFitsSystemWindows(window, false)
        requestNotificationPermissionIfNeeded()
        handleNotificationNavigation(intent)

        // Connecter le WebSocket si token présent
        CoroutineScope(Dispatchers.IO).launch {
            val token = preferencesManager.jwtTokenFlow.first()
            if (!token.isNullOrBlank() && authViewModel.isLoggedIn.value == true && !authViewModel.isLoggingOut.value) {
                RealtimeNotificationService.start(this@MainActivity)
                NotificationBackgroundScheduler.start(this@MainActivity)
            }
        }

        setContent {
            val appLanguage by preferencesManager.getAppLanguage().collectAsState(initial = initialLanguage)

            LaunchedEffect(appLanguage) {
                LocaleUtils.applyLanguage(this@MainActivity, appLanguage)
            }

            ConveneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val nc = rememberNavController()
                    navController = nc

                    //Navigation vers le meeting depuis notification
                    LaunchedEffect(Unit) {
                        val navigateTo = pendingNavigateTo
                        val userId = pendingUserId
                        val route = pendingTargetRoute
                        val meetingId = pendingMeetingId

                        if (navigateTo == "public_profile" && !userId.isNullOrBlank()) {
                            try {
                                nc.navigate("public_profile/$userId") {
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Nav error: ${e.message}")
                            }
                        } else if (navigateTo == "alerts") {
                            val alertsRoute = buildString {
                                append("alerts")
                                val params = mutableListOf<String>()
                                if (!userId.isNullOrBlank()) {
                                    params.add("targetUserId=${Uri.encode(userId)}")
                                }
                                if (!pendingNotificationId.isNullOrBlank()) {
                                    params.add("targetNotificationId=${Uri.encode(pendingNotificationId)}")
                                }
                                if (params.isNotEmpty()) {
                                    append("?")
                                    append(params.joinToString("&"))
                                }
                            }

                            try {
                                nc.navigate(alertsRoute) {
                                    launchSingleTop = true
                                }
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Nav error: ${e.message}")
                            }
                        } else if (route == "tasks") {
                            try {
                                nc.navigate("tasks") {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            } catch (e: Exception) {
                                Log.w("MainActivity", "Nav error: ${e.message}")
                            }
                        } else {
                            meetingId?.let { id ->
                                if (id.isNotBlank()) {
                                    nc.currentBackStackEntry?.let {
                                        nc.navigate("meeting_detail/$id")
                                    } ?: run {
                                        kotlinx.coroutines.delay(500)
                                        try {
                                            nc.navigate("meeting_detail/$id")
                                        } catch (e: Exception) {
                                            Log.w("MainActivity", "Nav error: ${e.message}")
                                        }
                                    }
                                }
                            }
                        }

                        pendingMeetingId = null
                        pendingTargetRoute = null
                        pendingNavigateTo = null
                        pendingUserId = null
                        pendingNotificationId = null
                    }

                    NavGraph(navController = nc)
                }
            }
        }
    }

    //  CRITIQUE : appelé quand launchMode=singleTop et app déjà ouverte
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationNavigation(intent)

        val navigateTo = pendingNavigateTo
        val userId = pendingUserId
        val route = pendingTargetRoute
        val meetingId = pendingMeetingId

        if (navigateTo == "public_profile" && !userId.isNullOrBlank()) {
            if (BuildConfig.DEBUG) Log.d("MainActivity", "onNewIntent: navigate public_profile/$userId")
            navController?.navigate("public_profile/$userId") {
                launchSingleTop = true
            }
        } else if (navigateTo == "alerts") {
            val alertsRoute = buildString {
                append("alerts")
                val params = mutableListOf<String>()
                if (!userId.isNullOrBlank()) {
                    params.add("targetUserId=${Uri.encode(userId)}")
                }
                if (!pendingNotificationId.isNullOrBlank()) {
                    params.add("targetNotificationId=${Uri.encode(pendingNotificationId)}")
                }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
            if (BuildConfig.DEBUG) Log.d("MainActivity", "onNewIntent: navigate $alertsRoute")
            navController?.navigate(alertsRoute) {
                launchSingleTop = true
            }
        } else if (route == "tasks") {
            if (BuildConfig.DEBUG) Log.d("MainActivity", "onNewIntent: navigate tasks")
            navController?.navigate("tasks") {
                launchSingleTop = true
                restoreState = true
            }
        } else if (!meetingId.isNullOrBlank()) {
            if (BuildConfig.DEBUG) Log.d("MainActivity", "onNewIntent: meetingId=$meetingId")
            navController?.navigate("meeting_detail/$meetingId") {
                launchSingleTop = true
            }
        }

        pendingMeetingId = null
        pendingTargetRoute = null
        pendingNavigateTo = null
        pendingUserId = null
        pendingNotificationId = null
    }

    override fun onDestroy() {
        networkCallback?.let { callback ->
            connectivityManager?.runCatching { unregisterNetworkCallback(callback) }
        }
        networkCallback = null
        connectivityManager = null
        super.onDestroy()
        // Ne pas déconnecter ici à déconnecter seulement au logout
    }

    private fun registerNetworkCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        connectivityManager = cm

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (!wsClient.isConnected.value && !wsClient.isManualDisconnect) {
                    RealtimeNotificationService.start(this@MainActivity)
                }
            }
        }

        networkCallback = callback
        cm.registerNetworkCallback(request, callback)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleNotificationNavigation(intent: Intent?) {
        pendingTargetRoute = intent?.getStringExtra("targetRoute")
        pendingMeetingId = intent?.getStringExtra("meetingId")
        pendingNavigateTo = intent?.getStringExtra("navigate_to")
        pendingUserId = intent?.getStringExtra("userId")
        pendingNotificationId = intent?.getStringExtra("notificationId")

        val notificationId = pendingNotificationId
        if (!notificationId.isNullOrBlank()) {
            CoroutineScope(Dispatchers.IO).launch {
                notificationRepository.markAsRead(notificationId)
            }
        }
    }
}
