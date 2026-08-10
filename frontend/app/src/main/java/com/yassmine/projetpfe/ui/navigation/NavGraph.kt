package com.yassmine.projetpfe.ui.navigation

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.compose.rememberNavController
import android.net.Uri
import com.yassmine.projetpfe.data.api.NotificationDto
import com.yassmine.projetpfe.ui.*
import com.yassmine.projetpfe.ui.auth.*
import com.yassmine.projetpfe.ui.meeting.ArchivedMeetingsScreen
import com.yassmine.projetpfe.ui.notes.NotesScreen
import com.yassmine.projetpfe.ui.video.VideoCallScreen
import com.yassmine.projetpfe.utils.LocaleUtils
import com.yassmine.projetpfe.viewmodel.AppPreferencesViewModel
import com.yassmine.projetpfe.viewmodel.AuthViewModel
import com.yassmine.projetpfe.viewmodel.MeetingViewModel
import com.yassmine.projetpfe.viewmodel.SocialViewModel

// NavGraph.kt
sealed class Screen(val route: String) {
    object Splash         : Screen("splash")
    object Onboarding1    : Screen("onboarding_1")
    object Onboarding2    : Screen("onboarding_2")
    object Onboarding3    : Screen("onboarding_3")
    object Login          : Screen("login")
    object SignUp         : Screen("signup")
    object ForgotPassword : Screen("forgot_password")
    object Home           : Screen("home")
    object ArchivedMeetings: Screen("archived_meetings")
    object MeetingDetail  : Screen("meeting_detail/{meetingId}")
    object EditMeeting    : Screen("edit_meeting/{meetingId}")
    object Tasks          : Screen("tasks")
    object CreateMeeting  : Screen("create_meeting")
    object Alerts         : Screen("alerts")
    object Profile        : Screen("profile")
    object Friends        : Screen("friends")
    object VideoCall      : Screen("video_call/{meetingId}")
    object PreJoin        : Screen("pre_join/{meetingId}/{meetingTitle}")
    object InPersonMeeting: Screen("in_person_meeting/{meetingId}")
    object Notes          : Screen("notes/{meetingId}")
    object Summary        : Screen("summary/{meetingId}/{meetingTitle}")
    object SearchUsers    : Screen("search_users")
    object PublicProfile  : Screen("public_profile/{userId}")
}

@Composable
fun NavGraph(navController: NavHostController = rememberNavController()) {

    val context = LocalContext.current
    val authViewModel: AuthViewModel = hiltViewModel()
    val appPreferencesViewModel: AppPreferencesViewModel = hiltViewModel()

    val isLoggedIn by authViewModel.isLoggedIn.collectAsState()
    val onboardingSeen by appPreferencesViewModel.onboardingSeen.collectAsState()
    val appLanguage by appPreferencesViewModel.appLanguage.collectAsState()
    var splashFinished by remember { mutableStateOf(false) }

    LaunchedEffect(appLanguage) {
        LocaleUtils.applyLanguage(context, appLanguage)
    }

    val onLanguageChange: (String) -> Unit = { languageCode ->
        if (languageCode != appLanguage) {
            appPreferencesViewModel.setLanguage(languageCode)
            LocaleUtils.applyLanguage(context, languageCode)
        }
    }

    val goToSignUp: () -> Unit = {
        appPreferencesViewModel.setOnboardingSeen(true)
        navController.navigate(Screen.SignUp.route) {
            popUpTo(Screen.Splash.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(authViewModel) {
        authViewModel.sessionEvents.collect {
            navController.navigate(Screen.Login.route) {
                popUpTo(navController.graph.id) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(splashFinished, isLoggedIn, onboardingSeen) {
        if (!splashFinished || isLoggedIn == null) return@LaunchedEffect

        val destination = when {
            isLoggedIn == true -> Screen.Home.route
            onboardingSeen -> Screen.Login.route
            else -> Screen.Onboarding1.route
        }

        navController.navigate(destination) {
            popUpTo(Screen.Splash.route) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = Screen.Splash.route) {
        // Splash 
        composable(Screen.Splash.route) {
            SplashScreen(
                onSplashDone = {
                    splashFinished = true
                }
            )
        }

        composable(Screen.Onboarding1.route) {
            OnboardingStep1Screen(
                currentLanguage = appLanguage,
                onLanguageChange = onLanguageChange,
                onSkip = goToSignUp,
                onNext = { navController.navigate(Screen.Onboarding2.route) }
            )
        }

        composable(Screen.Onboarding2.route) {
            OnboardingStep2Screen(
                currentLanguage = appLanguage,
                onLanguageChange = onLanguageChange,
                onSkip = goToSignUp,
                onNext = { navController.navigate(Screen.Onboarding3.route) }
            )
        }

        composable(Screen.Onboarding3.route) {
            OnboardingStep3Screen(
                currentLanguage = appLanguage,
                onLanguageChange = onLanguageChange,
                onSkip = goToSignUp,
                onGetStarted = goToSignUp
            )
        }

        //  Login 
        composable(Screen.Login.route) {
            LoginScreen(
                onSignUpClick         = { navController.navigate(Screen.SignUp.route) },
                onForgotPasswordClick = { navController.navigate(Screen.ForgotPassword.route) },
                onLoginSuccess = {
                    appPreferencesViewModel.setOnboardingSeen(true)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        //  Sign Up 
        composable(Screen.SignUp.route) {
            SignUpScreen(
                onBackToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Onboarding1.route) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onSignUpSuccess = {
                    appPreferencesViewModel.setOnboardingSeen(true)
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        //  Forgot Password 
        composable(Screen.ForgotPassword.route) {
            ForgotPasswordScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.ForgotPassword.route) { inclusive = true }
                    }
                }
            )
        }

        //  Home 
        composable(Screen.Home.route) {
            val meetingViewModel: MeetingViewModel = hiltViewModel()
            HomeScreen(
                onMeetingClick = { meeting ->
                    val realId = meeting.id
                    navController.navigate("meeting_detail/$realId")
                },
                onCreateClick  = { navController.navigate(Screen.CreateMeeting.route) },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onAlertsClick  = { navController.navigate(Screen.Alerts.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                onSearchClick  = { navController.navigate(Screen.SearchUsers.route) },
                onArchivedMeetingsClick = { navController.navigate(Screen.ArchivedMeetings.route) },
                viewModel = meetingViewModel
            )
        }

        composable(Screen.ArchivedMeetings.route) {
            val homeBackStackEntry = remember(navController) {
                navController.getBackStackEntry(Screen.Home.route)
            }
            val meetingViewModel: MeetingViewModel = hiltViewModel(homeBackStackEntry)

            ArchivedMeetingsScreen(
                onBack = { navController.popBackStack() },
                onMeetingClick = { meetingId ->
                    navController.navigate("meeting_detail/$meetingId")
                },
                viewModel = meetingViewModel
            )
        }

        //  Meeting Detail 
        composable(Screen.MeetingDetail.route) { backStackEntry ->
            val meetingIdParam = backStackEntry.arguments?.getString("meetingId") ?: ""
            val meetingRefreshFlag by backStackEntry
                .savedStateHandle
                .getStateFlow("meetingRefresh", false)
                .collectAsState()

            MeetingDetailScreen(
                meetingId     = meetingIdParam,
                onBack        = { navController.popBackStack() },
                onJoinMeeting = { realId, meetingType, meetingTitle ->
                    if (meetingType == "physical") {
                        navController.navigate("in_person_meeting/$realId")
                    } else {
                        navController.navigate("pre_join/$realId/${Uri.encode(meetingTitle)}")
                    }
                },
                onEditMeeting = { id -> navController.navigate("edit_meeting/$id") },
                onSummaryClick = { id, title ->
                    navController.navigate("summary/$id/${Uri.encode(title)}")
                },
                meetingRefreshFlag = meetingRefreshFlag,
                onRefreshHandled = {
                    backStackEntry.savedStateHandle["meetingRefresh"] = false
                },
            )
        }

        composable(
            route = Screen.Summary.route,
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType },
                navArgument("meetingTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            SummaryScreen(
                meetingId = backStackEntry.arguments?.getString("meetingId") ?: "",
                meetingTitle = backStackEntry.arguments?.getString("meetingTitle") ?: "",
                navController = navController
            )
        }

        //  Edit Meeting 
        composable(Screen.EditMeeting.route) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            EditMeetingScreen(
                meetingId     = meetingId,
                onBack        = { navController.popBackStack() },
                onUpdateSuccess = { navController.popBackStack() },
                onHomeClick   = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onAlertsClick  = { navController.navigate(Screen.Alerts.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        //  Video Call 
        // VideoCallScreen reçoit toujours le meetingId
        // Le ViewModel fait lui-même le POST /join pour récupérer token + livekitUrl
        composable(
            route = Screen.PreJoin.route,
            arguments = listOf(
                navArgument("meetingId") { type = NavType.StringType },
                navArgument("meetingTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            val meetingTitle = backStackEntry.arguments?.getString("meetingTitle") ?: ""
            PreJoinScreen(
                meetingId = meetingId,
                meetingTitle = meetingTitle,
                navController = navController
            )
        }

        composable(Screen.VideoCall.route) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            VideoCallScreen(
                meetingId    = meetingId,
                onLeave      = { navController.popBackStack() },
                onForceEndNavigate = { forceMeetingId ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("meetingRefresh", true)

                    navController.navigate("meeting_detail/$forceMeetingId") {
                        popUpTo("video_call/$forceMeetingId") { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNotesClick = { navController.navigate("notes/$meetingId") }
            )
        }

        composable(
            route = Screen.InPersonMeeting.route,
            arguments = listOf(navArgument("meetingId") { type = NavType.StringType })
        ) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: return@composable
            InPersonMeetingScreen(
                meetingId = meetingId,
                navController = navController
            )
        }

        //  Notes 
        composable(Screen.Notes.route) { backStackEntry ->
            val meetingId = backStackEntry.arguments?.getString("meetingId") ?: ""
            NotesScreen(meetingId = meetingId, onBack = { navController.popBackStack() })
        }

        //  Tasks 
        composable(Screen.Tasks.route) {
            TasksScreen(
                onBackClick  = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onCreateTaskClick = {},
                onHomeClick    = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onCreateClick  = { navController.navigate(Screen.CreateMeeting.route) },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onAlertsClick  = { navController.navigate(Screen.Alerts.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        //  Create Meeting 
        composable(Screen.CreateMeeting.route) {
            CreateMeetingScreen(
                onBackClick   = { navController.popBackStack() },
                onCreateClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onHomeClick    = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onAlertsClick  = { navController.navigate(Screen.Alerts.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) }
            )
        }

        //  Alerts 
        composable(
            route = "${Screen.Alerts.route}?targetUserId={targetUserId}&targetNotificationId={targetNotificationId}",
            arguments = listOf(
                navArgument("targetUserId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument("targetNotificationId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val targetUserId = backStackEntry.arguments?.getString("targetUserId")
            val targetNotificationId = backStackEntry.arguments?.getString("targetNotificationId")
            AlertsScreen(
                onHomeClick    = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onCreateClick  = { navController.navigate(Screen.CreateMeeting.route) },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onProfileClick = { navController.navigate(Screen.Profile.route) },
                targetUserId = targetUserId,
                targetNotificationId = targetNotificationId,
                onNotificationClick = { notification: NotificationDto ->
                    if (notification.type == "task_assigned") {
                        navController.navigate(Screen.Tasks.route) {
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else if (
                        notification.type == "friend_request" ||
                        notification.type == "friend_accepted" ||
                        notification.type == "friend_rejected"
                    ) {
                        val fromUserId = notification.data?.fromUserId
                        if (!fromUserId.isNullOrBlank()) {
                            navController.navigate("public_profile/$fromUserId")
                        }
                    } else {
                        val meetingId = notification.data?.meetingId
                        if (meetingId != null) {
                            navController.navigate("meeting_detail/$meetingId")
                        }
                    }
                }
            )
        }

        //  Profile 
        composable(Screen.Profile.route) {
            ProfileScreen(
                onEditProfile  = {},
                onHomeClick = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                },
                onCreateClick  = { navController.navigate(Screen.CreateMeeting.route) },
                onTasksClick   = { navController.navigate(Screen.Tasks.route) },
                onAlertsClick  = { navController.navigate(Screen.Alerts.route) },
                onFriendsClick = { navController.navigate(Screen.Friends.route) },
                currentLanguage = appLanguage,
                onLanguageChange = onLanguageChange,
                onLogout = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Friends.route) {
            FriendsScreen(
                onBack = { navController.popBackStack() },
                navController = navController
            )
        }

        // Search Users
        composable(Screen.SearchUsers.route) {
            val socialViewModel: SocialViewModel = hiltViewModel()
            SearchUsersScreen(
                onBack = { navController.popBackStack() },
                onUserClick = { userId -> navController.navigate("public_profile/$userId") },
                viewModel = socialViewModel
            )
        }

        // Public Profile
        composable(
            route = Screen.PublicProfile.route,
            arguments = listOf(navArgument("userId") { type = NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
            val searchBackStackEntry = remember(navController) {
                runCatching {
                    navController.getBackStackEntry(Screen.SearchUsers.route)
                }.getOrNull()
            }
              
            val socialViewModel: SocialViewModel = if (searchBackStackEntry != null) {
                hiltViewModel(searchBackStackEntry)  // instance partagée avec SearchUsers
            } else {
                hiltViewModel()  // instance indépendante (venant de Friends)
            }
            PublicProfileScreen(
                userId = userId,
                onBack = { navController.popBackStack() },
                viewModel = socialViewModel
            )
        }
    }
}